import {
  esc,
  icon
} from './ui.js';
import {
  itemLooks,
  sprite,
  paintTerrain
} from './game-art.js';
export const colors = ['#c4f36b', '#76c9db', '#d3a1f3', '#f0b16a'];
const angles = {
  UP: 0,
  RIGHT: Math.PI / 2,
  DOWN: Math.PI,
  LEFT: -Math.PI / 2
};
/**
 * Lớp hiển thị chỉ đọc snapshot: không ghi HP, điểm, seed hay vị trí vào engine.
 * Tick nghiệp vụ 20 Hz và vòng vẽ theo màn hình tách biệt; nội suy chậm một tick,
 * không dự đoán va chạm. Cache nền/sprite giữ phần vẽ tĩnh ngoài vòng animation.
 */
export class GameRenderer {
  /** Tài nguyên thuộc màn hình trận; dispose thu hồi cả observer/listener/rAF. */
  constructor(canvas, ownId) {
    this.canvas = canvas;
    this.context = canvas.getContext('2d', {
      alpha: false
    });
    this.ownId = String(ownId);
    this.current = null;
    this.previous = null;
    this.receivedAt = 0;
    this.lastDraw = 0;
    this.particles = [];
    this.terrain = document.createElement('canvas');
    this.terrainDirty = true;
    this.motion = matchMedia('(prefers-reduced-motion: reduce)');
    this.controller = new AbortController();
    this.running = true;
    this.draw = this.draw.bind(this);
    this.resize = new ResizeObserver(() => this.resizeCanvas());
    this.resize.observe(canvas);
    this.resizeCanvas();
    document.addEventListener('visibilitychange', () => {
      cancelAnimationFrame(this.frame);
      this.particles.length = 0;
      this.previous = this.current;
      this.lastDraw = 0;
      if (!document.hidden && this.running) this.frame = requestAnimationFrame(this.draw);
    }, {
      signal: this.controller.signal
    });
    if (!document.hidden) this.frame = requestAnimationFrame(this.draw);
  }

  /** Tối đa DPR 3 và 2,4 triệu pixel mỗi lớp: cân bằng độ nét với RAM điện thoại. */
  resizeCanvas() {
    const box = this.canvas.getBoundingClientRect();
    const ratio = Math.min(devicePixelRatio || 1, 3,
      Math.sqrt(2400000 / Math.max(1, box.width * box.width * .75)));
    const width = Math.max(1, Math.round(box.width * ratio));
    const height = Math.max(1, Math.round(box.width * .75 * ratio));
    if (width === this.canvas.width && height === this.canvas.height) return;
    this.canvas.width = width;
    this.canvas.height = height;
    this.terrainDirty = true;
  }

  /**
   * Chỉ chụp trường cần vẽ; catalog/buff definition bất biến được dùng lại.
   * Không clone toàn catalog mỗi tick. Rules được so hình học vì online gửi object mới.
   */
  setState(state) {
    const rulesKey = JSON.stringify(state.rules);
    if (this.rulesKey !== rulesKey) {
      this.rulesKey = rulesKey;
      this.rules = structuredClone(state.rules);
      this.terrainDirty = true;
    }
    const copyGroup = group => Object.fromEntries(Object.entries(group || {}).map(([id,
      entity
    ]) => [id, {
      ...entity,
      buffs: {
        ...entity.buffs
      }
    }]));
    const next = {
      tick: state.tick,
      rules: this.rules,
      players: copyGroup(state.players),
      enemies: copyGroup(state.enemies),
      items: (state.items || []).map(item => ({
        ...item
      })),
      bullets: (state.bullets || []).map(bullet => ({
        ...bullet
      }))
    };
    next.bulletMap = new Map(next.bullets.map(bullet => [bullet.id, bullet]));
    const old = this.current;
    if (old && state.tick !== old.tick && !document.hidden && !this.motion.matches) {
      // Hiệu ứng dựa vào chênh lệch snapshot, hoàn toàn không phát sinh sát thương.
      for (const group of ['players', 'enemies']) {
        for (const [id, entity] of Object.entries(old[group])) {
          const hp = next[group][id]?.hp || 0;
          if (entity.hp > hp) this.burst(entity.x, entity.y,
            group === 'enemies' ? '#f1b76c' : '#d6fba6', hp <= 0 ? 18 : 5);
        }
      }
      for (const bullet of next.bullets) {
        if (old.bulletMap.has(bullet.id)) continue;
        const shooter = next.players[bullet.owner] || next.enemies[bullet.owner];
        if (!shooter) continue;
        const angle = angles[bullet.direction] || 0;
        this.burst(shooter.x + Math.sin(angle) * 24,
          shooter.y - Math.cos(angle) * 24, '#fff2b6', 3, 100);
      }
    }
    this.previous = old;
    this.current = next;
    this.interval = Math.max(this.rules.tickMs,
      Math.min(150, (next.tick - (old?.tick ?? next.tick)) * this.rules.tickMs));
    this.receivedAt = performance.now();
  }

  /** Số hạt có trần; RNG trang trí dùng Math.random, không chạm seed mô phỏng. */
  burst(x, y, color, count, duration = 380) {
    for (let i = 0; i < count && this.particles.length < 160; i++) {
      const angle = Math.random() * Math.PI * 2;
      const speed = 25 + Math.random() * 65;
      this.particles.push({
        x,
        y,
        dx: Math.cos(angle) * speed,
        dy: Math.sin(angle) * speed,
        life: duration,
        duration,
        color
      });
    }
  }

  /** Nội suy có chặn [0,1], gồm cả đạn; mục tiêu mới xuất hiện dùng ngay vị trí nhận. */
  position(entity, group, time) {
    const previous = group === 'bullets' ? this.previous?.bulletMap.get(entity.id) :
      this.previous?.[group]?.[entity.id];
    if (!previous || this.motion.matches) return entity;
    const factor = Math.max(0, Math.min(1, (time - this.receivedAt) / this.interval));
    return {
      ...entity,
      x: previous.x + (entity.x - previous.x) * factor,
      y: previous.y + (entity.y - previous.y) * factor
    };
  }

  /** Nền cache cùng độ phân giải Canvas; chỉ xây lại khi đổi luật hoặc resize. */
  rebuildTerrain() {
    this.terrain.width = this.canvas.width;
    this.terrain.height = this.canvas.height;
    const ctx = this.terrain.getContext('2d', {
      alpha: false
    });
    ctx.setTransform(this.canvas.width / this.rules.width, 0, 0,
      this.canvas.height / this.rules.height, 0, 0);
    paintTerrain(ctx, this.rules);
    this.terrainDirty = false;
  }

  /**
   * Thứ tự lớp: nền → item → địch/xe → đạn → hạt. Không tạo path địa hình mỗi frame.
   * Tab ẩn dừng rAF; snapshot đứng yên quá 200 ms giảm vẽ xuống 10 Hz.
   */
  draw(time) {
    if (!this.running || document.hidden) return;
    this.frame = requestAnimationFrame(this.draw);
    const state = this.current;
    if (!state) return;
    if (!this.terrainDirty && !this.particles.length && time - this.receivedAt > 200 &&
      time - this.lastDraw < 100) return;
    const elapsed = this.lastDraw ? Math.min(50, time - this.lastDraw) : 16;
    this.lastDraw = time;
    if (this.terrainDirty) this.rebuildTerrain();
    const ctx = this.context,
      rules = state.rules;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.drawImage(this.terrain, 0, 0);
    ctx.setTransform(this.canvas.width / rules.width, 0, 0,
      this.canvas.height / rules.height, 0, 0);
    for (const item of state.items) this.item(item, time);
    for (const enemy of Object.values(state.enemies)) this.enemy(this.position(enemy, 'enemies',
      time));
    Object.values(state.players).forEach((player, index) => {
      const entity = this.position(player, 'players', time);
      const own = String(player.id) === this.ownId;
      const color = own ? colors[0] : colors[(index + 1) % colors.length];
      this.tank(entity, color, rules.tankHalf);
      if (player.hp <= 0) return;
      ctx.fillStyle = '#06130ee6';
      ctx.fillRect(entity.x - 19, entity.y + 23, 38, 4);
      ctx.fillStyle = color;
      ctx.fillRect(entity.x - 19, entity.y + 23, 38 * Math.max(0,
        Math.min(1, player.hp / rules.hp)), 4);
      ctx.textAlign = 'center';
      ctx.font = '10px "Be Vietnam Pro", sans-serif';
      ctx.fillStyle = '#eff8df';
      ctx.fillText(String(player.name).slice(0, 16), entity.x, entity.y - 30);
      if (own) {
        ctx.fillStyle = colors[0];
        ctx.beginPath();
        ctx.moveTo(entity.x - 3, entity.y - 42);
        ctx.lineTo(entity.x + 3, entity.y - 42);
        ctx.lineTo(entity.x, entity.y - 37);
        ctx.fill();
      }
      if (player.buffs?.SHIELD) {
        ctx.strokeStyle = '#9dd4f4bb';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(entity.x, entity.y, 25, 0, Math.PI * 2);
        ctx.stroke();
      }
    });
    // Vệt và lõi đạn dùng hình đơn giản, tránh shadowBlur cho từng viên đạn.
    ctx.lineCap = 'round';
    for (const source of state.bullets) {
      const bullet = this.position(source, 'bullets', time);
      const angle = angles[bullet.direction] || 0;
      ctx.strokeStyle = bullet.enemy ? '#f1a56966' : '#d4f99466';
      ctx.lineWidth = rules.bulletRadius * 2;
      ctx.beginPath();
      ctx.moveTo(bullet.x - Math.sin(angle) * 9, bullet.y + Math.cos(angle) * 9);
      ctx.lineTo(bullet.x, bullet.y);
      ctx.stroke();
      ctx.fillStyle = bullet.enemy ? '#ffd8a4' : '#f3ffd7';
      ctx.beginPath();
      ctx.arc(bullet.x, bullet.y, rules.bulletRadius, 0, Math.PI * 2);
      ctx.fill();
    }
    if (this.motion.matches) this.particles.length = 0;
    this.particles = this.particles.filter(particle => {
      particle.life -= elapsed;
      particle.x += particle.dx * elapsed / 1000;
      particle.y += particle.dy * elapsed / 1000;
      ctx.globalAlpha = Math.max(0, particle.life / particle.duration);
      ctx.fillStyle = particle.color;
      ctx.fillRect(particle.x - 1.5, particle.y - 1.5, 3, 3);
      return particle.life > 0;
    });
    ctx.globalAlpha = 1;
  }

  /** Xe bị loại chỉ để lại dấu; sprite sống giữ kích cỡ khớp loại hitbox đã có. */
  tank(player, color, half = 13) {
    const ctx = this.context;
    if (player.hp <= 0) {
      ctx.strokeStyle = '#a2b3a277';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(player.x - 7, player.y - 7);
      ctx.lineTo(player.x + 7, player.y + 7);
      ctx.moveTo(player.x + 7, player.y - 7);
      ctx.lineTo(player.x - 7, player.y + 7);
      ctx.stroke();
      return;
    }
    this.drawSprite('tank', player, 64 * half / 13, color);
  }

  /** Biến đổi chỉ áp dụng khi vẽ, không xoay tọa độ hoặc hitbox trong state. */
  drawSprite(kind, entity, size, color) {
    const ctx = this.context;
    ctx.save();
    ctx.translate(entity.x, entity.y);
    ctx.rotate(angles[entity.direction] || 0);
    ctx.drawImage(sprite(kind, color), -size / 2, -size / 2, size, size);
    ctx.restore();
  }

  /** Catalog quyết định silhouette và HP địch; thanh máu giúp phân biệt địch còn sống. */
  enemy(enemy) {
    if (enemy.hp <= 0) return;
    const definition = enemy.definition;
    if (definition.renderKey === 'tank') this.tank(enemy, '#edac68', definition.hitboxRadius);
    else this.drawSprite(definition.renderKey, enemy, 64 * definition.hitboxRadius / 13);
    const ctx = this.context;
    ctx.fillStyle = '#06130ee6';
    ctx.fillRect(enemy.x - 13, enemy.y + 24, 26, 3);
    ctx.fillStyle = '#edac68';
    ctx.fillRect(enemy.x - 13, enemy.y + 24, 26 * Math.min(1, enemy.hp / definition.health), 3);
  }

  /** Nổi nhẹ khi được phép chuyển động; ký hiệu item không phụ thuộc font hệ điều hành. */
  item(item, time) {
    const offset = this.motion.matches ? 0 : Math.sin(time / 330 + item.x) * 1.5;
    this.context.drawImage(sprite(item.definition.effect), item.x - 25,
      item.y - 25 + offset, 50, 50);
  }

  /** Thu hồi toàn bộ tài nguyên theo vòng đời router, tránh rAF/Canvas bị giữ sau rời trận. */
  dispose() {
    this.running = false;
    cancelAnimationFrame(this.frame);
    this.resize.disconnect();
    this.controller.abort();
    this.particles.length = 0;
    this.current = this.previous = null;
    this.terrain.width = this.terrain.height = 1;
  }
}
/**
 * Hợp nhất bàn phím và từng ngón chạm thành ý định di chuyển/bắn.
 * Pointer capture giữ nút bắn khi ngón trượt; d-pad dò vị trí để đổi hướng.
 * Mỗi pointer có trạng thái riêng nên thả một ngón không hủy ngón còn lại.
 */
export class GameControls {
  /** Gắn listener theo vòng đời màn hình; AbortController thu hồi khi rời trận. */
  constructor(onPause) {
    this.keys = new Set();
    this.pointers = new Map();
    this.direction = 'NONE';
    this.firing = false;
    this.controller = new AbortController();
    this.mapping = {
      KeyW: 'UP',
      ArrowUp: 'UP',
      KeyS: 'DOWN',
      ArrowDown: 'DOWN',
      KeyA: 'LEFT',
      ArrowLeft: 'LEFT',
      KeyD: 'RIGHT',
      ArrowRight: 'RIGHT'
    };
    const signal = this.controller.signal;
    this.buttons = [...document.querySelectorAll('[data-direction], [data-fire]')];
    window.addEventListener('keydown', event => {
      if (event.target instanceof Element &&
        event.target.closest('input,select,textarea,dialog,[contenteditable=true]')) return;
      if (!this.mapping[event.code] &&
        !['Space', 'KeyJ', 'KeyP', 'Escape'].includes(event.code)) return;
      event.preventDefault();
      this.keys.add(event.code);
      this.reconcile();
      if (!event.repeat && ['KeyP', 'Escape'].includes(event.code)) {
        this.reset();
        onPause?.();
      }
    }, {
      signal
    });
    window.addEventListener('keyup', event => {
      this.keys.delete(event.code);
      this.reconcile();
    }, {
      signal
    });
    window.addEventListener('blur', () => this.reset(), {
      signal
    });
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        this.reset();
        this.onHidden?.();
      }
    }, {
      signal
    });
    for (const element of this.buttons) {
      element.addEventListener('pointerdown', event => {
        if (event.pointerType === 'mouse' && event.button !== 0) return;
        event.preventDefault();
        if (document.querySelector('dialog[open]')) return;
        this.pointers.set(event.pointerId, {
          element,
          dpad: element.closest('.dpad'),
          direction: element.dataset.direction || 'NONE',
          fire: element.hasAttribute('data-fire')
        });
        element.setPointerCapture(event.pointerId);
        this.reconcile();
      }, {
        signal
      });
      element.addEventListener('pointermove', event => {
        const pointer = this.pointers.get(event.pointerId);
        if (!pointer?.dpad) return;
        const target = document.elementFromPoint(event.clientX, event.clientY)
          ?.closest('[data-direction]');
        pointer.direction = target?.closest('.dpad') === pointer.dpad ?
          target.dataset.direction : 'NONE';
        this.reconcile();
      }, {
        signal
      });
      const release = event => {
        this.pointers.delete(event.pointerId);
        this.reconcile();
      };
      element.addEventListener('pointerup', release, {
        signal
      });
      element.addEventListener('pointercancel', release, {
        signal
      });
      element.addEventListener('lostpointercapture', release, {
        signal
      });
    }
    this.reconcile();
  }

  /** Ưu tiên ngón hướng được đặt sau cùng; phím và nút bắn vẫn hoạt động độc lập. */
  reconcile() {
    const pointers = [...this.pointers.values()];
    this.direction = pointers.filter(pointer => pointer.direction !== 'NONE')
      .at(-1)?.direction || [...this.keys].reverse()
      .map(key => this.mapping[key]).find(Boolean) || 'NONE';
    this.firing = pointers.some(pointer => pointer.fire) ||
      this.keys.has('Space') || this.keys.has('KeyJ');
    for (const button of this.buttons) {
      const pressed = button.hasAttribute('data-fire') ? this.firing :
        button.dataset.direction === this.direction;
      button.classList.toggle('pressed', pressed);
      button.setAttribute('aria-pressed', String(pressed));
    }
  }

  /** Không gửi đầu vào cũ khi tab bị ẩn hoặc hộp thoại đang chặn trận. */
  read() {
    if (document.hidden || document.querySelector('dialog[open]')) {
      this.reset();
    }
    return {
      direction: this.direction,
      fire: this.firing
    };
  }

  /** Xóa cả trạng thái ngón lẫn phím sau pause, blur hoặc hủy pointer. */
  reset() {
    this.keys.clear();
    const captured = [...this.pointers.entries()];
    this.pointers.clear();
    for (const [id, pointer] of captured) {
      if (pointer.element.hasPointerCapture(id)) pointer.element.releasePointerCapture(id);
    }
    this.reconcile();
  }

  /** Dừng nhận đầu vào khi router tháo màn hình trận. */
  dispose() {
    this.reset();
    this.controller.abort();
  }
}
/** Dựng khung Canvas, HUD và cụm cảm ứng chung cho solo/online. */
export function gameMarkup(title, offline = false) {
  return `
    <div class="page-heading">
      <div>
        <div class="eyebrow">${offline ? 'SOLO · LƯU TRÊN THIẾT BỊ' : 'TRẬN ĐẤU ONLINE'}</div>
        <h1>${esc(title)}</h1>
      </div>
      <div class="heading-actions">${
        offline ? `
        <button class="btn secondary small" data-action="save-solo">${icon('save')}Lưu trận</button>
        <button class="icon-button" data-action="pause-solo" aria-label="Tạm dừng">${icon('pause')}</button>
        ` : `
        <a class="btn secondary small" data-link href="/rooms">${icon('users')}Sảnh chơi</a>
        `
      }</div>
    </div>
    <div class="game-layout">
      <div class="game-stage">
        <div class="battlefield">
          <div class="game-topline">
            <span class="tag">${icon(offline ? 'gamepad-2' : 'radio')}<span id="game-connection">${
              offline ? 'SOLO' : 'ĐANG KẾT NỐI'
            }</span>
            </span>
            <span id="game-time" class="game-time">00:00</span>
            <span id="game-wave">THAO TRƯỜNG</span>
          </div>
          <canvas id="battle-canvas" width="768" height="576" tabindex="0"
            aria-label="Bản đồ trận đấu. Dùng WASD hoặc phím mũi tên di chuyển, Space để bắn."></canvas>
          <div class="pilot-hud" aria-label="Trạng thái của bạn">
            <div class="pilot-health">${icon('heart')}<div>
                <small>GIÁP XE</small>
                <strong id="pilot-hp">—</strong>
                <div id="pilot-healthbar" class="health-meter" role="progressbar" aria-label="Máu xe"
                  aria-valuemin="0" aria-valuemax="3" aria-valuenow="0">
                  <span></span>
                </div>
              </div>
            </div>
            <div>${icon('trophy')}<div>
                <small>ĐIỂM TRẬN</small>
                <strong id="pilot-score">0</strong>
              </div>
            </div>
            <div>${icon('crosshair')}<div>
                <small>MỤC TIÊU</small>
                <strong id="pilot-kills">0</strong>
              </div>
            </div>
          </div>
          <div id="game-overlay" class="game-overlay hidden" role="status"></div>
        </div>
        <div class="touch-controls" role="group" aria-label="Điều khiển cảm ứng">
          <div class="dpad" role="group" aria-label="Hướng di chuyển">${
            ['UP', 'LEFT', 'DOWN', 'RIGHT'].map((direction, index) => `
            <button type="button" data-direction="${direction}" aria-label="${
              ['Đi lên', 'Sang trái', 'Đi xuống', 'Sang phải'][index]
            }">${icon('chevron-right', 'direction-arrow direction-' + direction.toLowerCase())}</button>
            `).join('')
          }</div>
          <button type="button" class="fire-button" data-fire>${icon('crosshair')}
            <br>BẮN
          </button>
        </div>
        <div class="game-caption">
          <span>${
            offline ? 'Tiến trình tự lưu mỗi 2 giây · Không cộng bảng hạng online' :
              'Máy chủ quyết định vị trí, HP và kết quả'
          }</span>
          <span id="save-status" role="status">${offline ? 'Đang chuẩn bị bản lưu…' : 'Đang đồng bộ…'}</span>
        </div>
      </div>
      <aside class="game-side">
        <div class="panel">
          <h3>ĐỘI HÌNH CHIẾN ĐẤU</h3>
          <div id="game-roster"></div>
          <div id="game-buffs" class="game-buffs"></div>
        </div>
        <div class="panel">
          <h3>ĐIỀU KHIỂN</h3>
          <div class="game-keys">
            <kbd>W A S D</kbd>
            <span>Di chuyển</span>
          </div>
          <div class="game-keys">
            <kbd>Space</kbd>
            <span>Bắn</span>
          </div>${offline ? '<div class="game-keys"><kbd>P</kbd><span>Tạm dừng</span></div>' : ''}<div class="divider">
          </div>
          <p class="muted" style="font-size:10px">${
            offline ? 'Nhặt vật phẩm để hồi máu, tăng tốc, tăng sát thương hoặc nhận khiên.' :
              'Mất mạng? Đăng nhập lại và xác nhận chơi tiếp trước thời hạn phục hồi.'
          }</p>
          <div class="item-legend" aria-label="Vật phẩm trên bản đồ">${
            Object.values(itemLooks).map(look => `
            <span>${icon(look.icon)}${esc(look.label)}</span>
            `).join('')
          }
          </div>
        </div>
      </aside>
    </div>
    `;
}
// Khóa bằng chính element để cache tự thu hồi khi router dựng trận khác.
const hudCache = new WeakMap();

/** Không thay innerHTML 20 lần/giây khi HP/điểm/buff chưa đổi. Hàm render chỉ chạy khi cần. */
function hudFragment(element, signature, render) {
  if (!element || hudCache.get(element) === signature) return;
  element.innerHTML = render();
  hudCache.set(element, signature);
}

/** Nhãn nhỏ chỉ ghi DOM khi giá trị đổi; tránh công việc style/layout không cần thiết. */
function hudText(id, value) {
  const element = document.getElementById(id);
  if (element && element.textContent !== String(value)) element.textContent = String(value);
}

/**
 * HUD đọc cùng snapshot với Canvas. Countdown chỉ đổi theo giây; roster chỉ đổi khi
 * HP/kill/score/tên thay đổi. Điểm ở đây là điểm trong trận, chưa phải điểm bảng hạng.
 */
export function updateHud(state, ownId) {
  if (!document.getElementById('game-time')) return;
  const seconds = Math.max(0, Math.ceil(state.remainingTicks * state.rules.tickMs / 1000));
  hudText('game-time',
    `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
  );
  hudText('game-wave', state.mode === 'CAMPAIGN' ?
    `CỬA ${state.stage.number} · ĐỢT ${Math.min(state.wave + 1, state.stage.waves.length)}/${state.stage.waves.length}` :
    'ĐẤU TRƯỜNG ARENA');
  const players = Object.values(state.players);
  const rosterKey = JSON.stringify([String(ownId), state.rules.hp,
    players.map(player => [player.id, player.name, player.hp, player.kills, player.score])
  ]);
  hudFragment(document.getElementById('game-roster'), rosterKey, () => players.map((player,
    index) => {
    const own = String(player.id) === String(ownId);
    const color = own ? colors[0] : colors[(index + 1) % colors.length];
    return `
      <div class="game-player ${player.hp <= 0 ? 'eliminated' : ''}">
        <span class="player-color" style="background:${color}"></span>
        <div>
          <strong>${esc(player.name)}${own ? ' · Bạn' : ''}</strong>
          <small>${player.hp > 0 ? `${player.hp}/${state.rules.hp} HP` : 'Đã bị loại'} ·
            ${player.kills} mục tiêu · ${player.score} điểm</small>
        </div>
      </div>
      `;
  }).join(''));
  const own = state.players[String(ownId)];
  hudText('pilot-hp', own ? `${own.hp}/${state.rules.hp}` : 'Theo dõi');
  hudText('pilot-score', (own?.score || 0).toLocaleString('vi-VN'));
  hudText('pilot-kills', own?.kills || 0);
  const bar = document.getElementById('pilot-healthbar');
  const healthKey = `${own?.hp || 0}/${state.rules.hp}`;
  if (bar && hudCache.get(bar) !== healthKey) {
    const ratio = Math.max(0, Math.min(1, (own?.hp || 0) / state.rules.hp));
    bar.setAttribute('aria-valuemax', String(state.rules.hp));
    bar.setAttribute('aria-valuenow', String(own?.hp || 0));
    bar.firstElementChild.style.width = `${ratio * 100}%`;
    bar.classList.toggle('critical', ratio <= 1 / 3);
    hudCache.set(bar, healthKey);
  }
  const buffs = Object.entries(own?.buffs || {}).map(([type, active]) => [type,
      Math.max(0, Math.ceil((active.expiresAtTick - state.tick) * state.rules.tickMs / 1000))
    ])
    .filter(([, remaining]) => remaining > 0);
  hudFragment(document.getElementById('game-buffs'), JSON.stringify(buffs), () => buffs.map(([type,
    remaining
  ]) => {
    const look = itemLooks[type] || itemLooks.SHIELD;
    return `
      <span class="tag">${icon(look.icon)}${esc(look.label)} ${remaining}s</span>
      `;
  }).join(''));
}
