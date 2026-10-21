/** Engine solo chạy tick cố định 50 ms, dùng schema/catalog giống Java; không gửi điểm online. */
const directions = ['UP', 'DOWN', 'LEFT', 'RIGHT'];
const vectors = {
  UP: [0, -1],
  DOWN: [0, 1],
  LEFT: [-1, 0],
  RIGHT: [1, 0],
  NONE: [0, 0]
};
/** Thứ tự ID ổn định giúp tick và việc phục hồi không lệ thuộc thứ tự duyệt object. */
const ordered = objects => Object.keys(objects).sort().map(key => objects[key]);
/** RNG có seed nằm trong snapshot để trạng thái ngẫu nhiên tiếp tục sau khi phục hồi. */
const random = state => {
  state.seed = (Math.imul(state.seed, 1664525) + 1013904223) >>> 0;
  return state.seed / 4294967296;
};
const randomDirection = state => directions[Math.floor(random(state) * 4)];
const buff = (player, type) => player.buffs[type]?.amount || 0;

/** Tải luật, nội dung và cửa song song; Service Worker cung cấp khi offline đã sẵn sàng. */
export async function loadCatalog() {
  const resources = await Promise.all(['/game-rules.json', '/game-content.json',
    '/campaign-stages.json'
  ].map(async path => {
    const response = await fetch(path);
    if (!response.ok) throw new Error(
      'Chưa tải đủ dữ liệu trò chơi. Kết nối mạng và mở lại chế độ solo.');
    return response.json();
  }));
  return {
    rules: resources[0],
    content: resources[1],
    stages: resources[2]
  };
}
/** Clone catalog vào snapshot schema 2 để lần thay catalog sau không đổi trận đã lưu. */
export function createGame(catalog, {
  mode = 'CAMPAIGN',
  difficulty = 'NORMAL',
  stage = 1,
  bots = 1,
  name = 'Chỉ huy'
} = {}) {
  const rules = structuredClone(catalog.rules);
  const selectedStage = mode === 'CAMPAIGN' ? structuredClone(catalog.stages.find(row => row
    .number === stage)) : null;
  const state = {
    schemaVersion: 2,
    mode,
    difficulty,
    content: structuredClone(catalog.content),
    stage: selectedStage,
    wave: 0,
    nextEnemyId: 1,
    nextItemId: 1,
    enemies: {},
    items: [],
    matchId: crypto.randomUUID(),
    rules,
    tick: 0,
    seed: 20261020,
    remainingTicks: selectedStage?.durationTicks || rules.durationTicks,
    nextBulletId: 1,
    phase: 'RUNNING',
    endReason: null,
    players: {},
    bullets: []
  };
  const roster = [{
    id: 'solo',
    name
  }, ...Array.from({
    length: mode === 'ARENA' ? Math.max(1, Math.min(3, bots)) : 0
  }, (_, index) => ({
    id: `bot-${index + 1}`,
    name: `Bot ${index + 1}`,
    bot: true
  }))];
  roster.forEach((entry, index) => {
    state.players[entry.id] = {
      id: entry.id,
      name: entry.name,
      direction: 'UP',
      botDirection: 'NONE',
      x: rules.spawns[index].x,
      y: rules.spawns[index].y,
      hp: rules.hp,
      cooldown: 0,
      botThinkAt: 0,
      bot: !!entry.bot,
      forfeited: false,
      eliminatedAtTick: -1,
      kills: 0,
      score: 0,
      buffs: {}
    };
  });
  if (mode === 'CAMPAIGN') spawnWave(state);
  return state;
}
/** Kiểm tra payload từ IndexedDB/cloud trước khi dùng; lỗi không xóa bản lưu gốc. */
export function restoreGame(payload) {
  const state = structuredClone(payload);
  if (!state || state.schemaVersion !== 2 || state.rules?.version !== 1 || state.rules.tickMs !==
    50 || !['ARENA', 'CAMPAIGN'].includes(state.mode) || !['EASY', 'NORMAL', 'HARD'].includes(state
      .difficulty) || !state.players?.solo || !['RUNNING', 'ENDING', 'FINISHED'].includes(state
      .phase) || !Number.isSafeInteger(state.tick) || state.tick < 0 || !Number.isFinite(state
      .remainingTicks) || state.remainingTicks < 0 || !Array.isArray(state.bullets) || state.bullets
    .length > 64 || !state.enemies || Object.keys(state.enemies).length > 16 || !Array.isArray(state
      .items) || state.items.length > 32 || (state.mode === 'CAMPAIGN' && (!state.stage?.waves || !
      state.content?.enemies))) throw new Error(
    'Bản lưu không tương thích với phiên bản hiện tại. Bản gốc vẫn được giữ trên máy.');
  for (const player of Object.values(state.players)) {
    if (![player.x, player.y, player.hp].every(Number.isFinite) || player.hp < 0 || player.hp >
      state.rules.hp || !directions.includes(player.direction) || !player.buffs) throw new Error(
      'Dữ liệu xe tăng trong bản lưu chưa hợp lệ.');
  }
  return state;
}

/** Kiểm biên bản đồ/tường theo hitbox; đơn vị bay được bỏ qua tường. */
function clear(state, x, y, half, air = false) {
  const rules = state.rules;
  return x >= half && y >= half && x <= rules.width - half && y <= rules.height - half && (air || !
    rules.walls.some(wall => x + half > wall.x && x - half < wall.x + wall.w && y + half > wall
      .y && y - half < wall.y + wall.h));
}
/** Slab intersection trên toàn đoạn bay, tránh đạn xuyên tường khi tốc độ cao. */
function hit(x, y, nx, ny, rx, ry, width, height) {
  let low = 0,
    high = 1;
  const start = [x, y],
    delta = [nx - x, ny - y],
    min = [rx, ry],
    max = [rx + width, ry + height];
  for (let axis = 0; axis < 2; axis++) {
    if (Math.abs(delta[axis]) < 1e-9) {
      if (start[axis] < min[axis] || start[axis] > max[axis]) return Infinity;
    } else {
      const a = (min[axis] - start[axis]) / delta[axis],
        b = (max[axis] - start[axis]) / delta[axis];
      low = Math.max(low, Math.min(a, b));
      high = Math.min(high, Math.max(a, b));
      if (low > high) return Infinity;
    }
  }
  return low;
}

/** Sinh viên đạn ở đầu nòng, giới hạn tổng số đạn và giữ chủ sở hữu để cộng điểm. */
function fire(state, owner, x, y, direction, radius, damage, enemy = false) {
  if (state.bullets.length >= 64) return;
  const [dx, dy] = vectors[direction];
  state.bullets.push({
    id: state.nextBulletId++,
    life: state.rules.bulletLifeTicks,
    owner,
    direction,
    x: x + dx * (radius + 4),
    y: y + dy * (radius + 4),
    damage,
    enemy
  });
}

/** Áp dụng khiên trước sát thương và ghi tick bị loại đúng một lần. */
function damage(state, player, amount) {
  const previous = player.hp;
  player.hp = Math.max(0, player.hp - Math.max(0, amount - buff(player, 'SHIELD')));
  if (previous > 0 && player.hp === 0) player.eliminatedAtTick = state.tick;
}

/** Bot Arena định kỳ chọn hướng, ưu tiên mục tiêu thẳng hàng và giữ bắn. */
function botInput(state, bot) {
  if (state.tick >= bot.botThinkAt) {
    bot.botThinkAt = state.tick + 12;
    bot.botDirection = randomDirection(state);
    for (const target of ordered(state.players)) {
      if (target.hp <= 0 || target.id === bot.id) continue;
      if (Math.abs(bot.x - target.x) < state.rules.tankHalf) {
        bot.botDirection = target.y < bot.y ? 'UP' : 'DOWN';
        break;
      }
      if (Math.abs(bot.y - target.y) < state.rules.tankHalf) {
        bot.botDirection = target.x < bot.x ? 'LEFT' : 'RIGHT';
        break;
      }
    }
  }
  return {
    direction: bot.botDirection,
    fire: true
  };
}

/** Tìm va chạm đầu tiên trên đoạn bay rồi gom sát thương/điểm theo mục tiêu. */
function moveBullets(state) {
  const damages = {},
    credits = {},
    surviving = [];
  for (const bullet of state.bullets) {
    const [dx, dy] = vectors[bullet.direction];
    const nx = bullet.x + dx * state.rules.bulletSpeed * state.rules.tickMs / 1000,
      ny = bullet.y + dy * state.rules.bulletSpeed * state.rules.tickMs / 1000;
    let first = Infinity,
      target = null;
    const radius = state.rules.bulletRadius;
    for (const wall of state.rules.walls) first = Math.min(first, hit(bullet.x, bullet.y, nx, ny,
      wall.x - radius, wall.y - radius, wall.w + 2 * radius, wall.h + 2 * radius));
    if (bullet.enemy || state.mode === 'ARENA') {
      for (const player of ordered(state.players)) {
        if (player.hp <= 0 || player.id === bullet.owner) continue;
        const half = state.rules.tankHalf + radius;
        const at = hit(bullet.x, bullet.y, nx, ny, player.x - half, player.y - half, 2 * half, 2 *
          half);
        if (at < first) {
          first = at;
          target = player.id;
        }
      }
    }
    if (!bullet.enemy) {
      for (const enemy of ordered(state.enemies)) {
        const half = enemy.definition.hitboxRadius + radius;
        const at = hit(bullet.x, bullet.y, nx, ny, enemy.x - half, enemy.y - half, 2 * half, 2 *
          half);
        if (at < first) {
          first = at;
          target = enemy.id;
        }
      }
    }
    if (first !== Infinity) {
      if (target) {
        damages[target] = (damages[target] || 0) + bullet.damage;
        credits[target] = bullet.owner;
      }
      continue;
    }
    bullet.x = nx;
    bullet.y = ny;
    bullet.life--;
    if (bullet.life > 0 && nx >= 0 && ny >= 0 && nx <= state.rules.width && ny <= state.rules
      .height) surviving.push(bullet);
  }
  state.bullets = surviving;
  for (const id of Object.keys(damages).sort()) {
    const player = state.players[id],
      killer = state.players[credits[id]];
    if (player) {
      const before = player.hp;
      damage(state, player, damages[id]);
      if (before > 0 && player.hp === 0 && killer) {
        killer.kills++;
        killer.score += 100;
      }
    } else {
      const enemy = state.enemies[id];
      if (enemy) {
        enemy.hp = Math.max(0, enemy.hp - damages[id]);
        if (enemy.hp === 0) {
          delete state.enemies[id];
          if (killer) {
            killer.kills++;
            killer.score += enemy.definition.killScore;
          }
          if (state.content.items.length && random(state) < .5) drop(state, state.content.items[Math
            .floor(random(state) * state.content.items.length)], enemy.x, enemy.y);
        }
      }
    }
  }
}

/** Thả vật phẩm có hạn tồn tại và giới hạn số lượng trong snapshot. */
function drop(state, definition, x, y) {
  if (state.items.length < 32) state.items.push({
    id: state.nextItemId++,
    definition,
    x,
    y,
    expiresAtTick: state.tick + 600
  });
}

/** Nhân thông số theo độ khó, chọn điểm sinh có kiểm tường/khoảng cách và thêm vật phẩm. */
function spawnWave(state) {
  const health = state.difficulty === 'EASY' ? .75 : state.difficulty === 'HARD' ? 1.5 : 1;
  const speed = state.difficulty === 'EASY' ? .8 : state.difficulty === 'HARD' ? 1.15 : 1;
  for (const code of state.stage.waves[state.wave]) {
    const original = state.content.enemies.find(row => row.code === code);
    const definition = {
      ...original,
      health: Math.max(1, Math.ceil(original.health * health)),
      speed: original.speed * speed,
      fireCooldownTicks: original.fireCooldownTicks === 0 ? 0 : Math.max(1, Math.trunc(original
        .fireCooldownTicks / speed)),
      killScore: original.killScore * (state.difficulty === 'HARD' ? 2 : 1)
    };
    const enemy = {
      id: `E${state.nextEnemyId++}`,
      definition,
      x: 0,
      y: 0,
      direction: 'DOWN',
      hp: definition.health,
      cooldown: 0,
      contactCooldown: 0,
      thinkAt: 0
    };
    for (let attempt = 0; attempt < 100; attempt++) {
      enemy.x = 32 + random(state) * (state.rules.width - 64);
      enemy.y = 32 + random(state) * (state.rules.height - 64);
      if (clear(state, enemy.x, enemy.y, definition.hitboxRadius, definition.movement === 'AIR') &&
        !ordered(state.players).some(p => Math.hypot(p.x - enemy.x, p.y - enemy.y) < 100) && !
        ordered(state.enemies).some(e => Math.hypot(e.x - enemy.x, e.y - enemy.y) < 40)) break;
    }
    state.enemies[enemy.id] = enemy;
  }
  if (state.content.items.length) {
    const spawn = state.rules.spawns[0];
    drop(state, state.content.items[state.wave % state.content.items.length], spawn.x + 40, spawn
      .y);
  }
}

/** AI Campaign chọn mục tiêu, di chuyển/bắn/va chạm theo cooldown trong state. */
function stepEnemies(state) {
  if (state.mode !== 'CAMPAIGN') return;
  const dt = state.rules.tickMs / 1000;
  for (const enemy of ordered(state.enemies)) {
    enemy.cooldown = Math.max(0, enemy.cooldown - 1);
    enemy.contactCooldown = Math.max(0, enemy.contactCooldown - 1);
    const target = ordered(state.players).filter(p => p.hp > 0).sort((a, b) => Math.hypot(a.x -
      enemy.x, a.y - enemy.y) - Math.hypot(b.x - enemy.x, b.y - enemy.y))[0];
    if (!target) continue;
    if (state.tick >= enemy.thinkAt) {
      enemy.thinkAt = state.tick + (state.difficulty === 'HARD' ? 8 : 16);
      enemy.direction = enemy.definition.behavior === 'PATROL' ? randomDirection(state) : Math.abs(
        target.x - enemy.x) > Math.abs(target.y - enemy.y) ? (target.x < enemy.x ? 'LEFT' :
        'RIGHT') : (target.y < enemy.y ? 'UP' : 'DOWN');
    }
    const [dx, dy] = vectors[enemy.direction], x = enemy.x + dx * enemy.definition.speed * dt, y =
      enemy.y + dy * enemy.definition.speed * dt;
    if (clear(state, x, y, enemy.definition.hitboxRadius, enemy.definition.movement === 'AIR')) {
      enemy.x = x;
      enemy.y = y;
    } else {
      enemy.direction = randomDirection(state);
      enemy.thinkAt = state.tick + 10;
    }
    if (enemy.definition.projectileDamage > 0 && enemy.cooldown === 0) {
      fire(state, enemy.id, enemy.x, enemy.y, enemy.direction, enemy.definition.hitboxRadius, enemy
        .definition.projectileDamage, true);
      enemy.cooldown = enemy.definition.fireCooldownTicks;
    }
    if (enemy.definition.contactDamage > 0 && enemy.contactCooldown === 0 && Math.hypot(target.x -
        enemy.x, target.y - enemy.y) < state.rules.tankHalf + enemy.definition.hitboxRadius) {
      damage(state, target, enemy.definition.contactDamage);
      enemy.contactCooldown = 20;
    }
  }
}

/** Xóa vật phẩm hết hạn hoặc đã nhặt; hồi máu ngay, buff có tick hết hiệu lực. */
function collect(state) {
  state.items = state.items.filter(item => {
    if (item.expiresAtTick <= state.tick) return false;
    const player = ordered(state.players).find(p => p.hp > 0 && Math.hypot(p.x - item.x, p.y -
      item.y) <= state.rules.tankHalf + 8);
    if (!player) return true;
    const definition = item.definition;
    if (definition.effect === 'REPAIR') player.hp = Math.min(state.rules.hp, player.hp +
      definition.amount);
    else player.buffs[definition.effect] = {
      amount: definition.amount,
      expiresAtTick: state.tick + definition.durationTicks
    };
    return false;
  });
}
/** Một tick: hết buff → người chơi → địch → đạn → vật phẩm → chuyển wave/kết thúc. */
export function step(state, input = {
  direction: 'NONE',
  fire: false
}) {
  if (state.phase !== 'RUNNING') return;
  state.tick++;
  state.remainingTicks = Math.max(0, state.remainingTicks - 1);
  for (const player of ordered(state.players)) {
    for (const [type, active] of Object.entries(player.buffs))
      if (active.expiresAtTick <= state.tick) delete player.buffs[type];
    if (player.hp <= 0) continue;
    player.cooldown = Math.max(0, player.cooldown - 1);
    const intent = player.bot ? botInput(state, player) : input;
    if (intent.direction && intent.direction !== 'NONE' && vectors[intent.direction]) {
      player.direction = intent.direction;
      const [dx, dy] = vectors[intent.direction], speed = state.rules.tankSpeed * (1 + buff(player,
          'SPEED') / 100), x = player.x + dx * speed * state.rules.tickMs / 1000, y = player.y +
        dy * speed * state.rules.tickMs / 1000;
      if (clear(state, x, y, state.rules.tankHalf) && !ordered(state.players).some(other => other
          .hp > 0 && other.id !== player.id && Math.abs(x - other.x) < 2 * state.rules.tankHalf &&
          Math.abs(y - other.y) < 2 * state.rules.tankHalf)) {
        player.x = x;
        player.y = y;
      }
    }
    if (intent.fire && player.cooldown === 0) {
      fire(state, player.id, player.x, player.y, player.direction, state.rules.tankHalf, 1 + buff(
        player, 'DAMAGE_BOOST'));
      player.cooldown = Math.max(1, Math.ceil(state.rules.fireCooldownTicks * Math.max(.1, 1 - buff(
        player, 'RAPID_FIRE') / 100)));
    }
  }
  stepEnemies(state);
  moveBullets(state);
  collect(state);
  const alive = ordered(state.players).filter(p => p.hp > 0).length;
  if (state.mode === 'CAMPAIGN') {
    if (!alive) end(state, 'TEAM_ELIMINATED');
    else if (!Object.keys(state.enemies).length) {
      if (++state.wave >= state.stage.waves.length) end(state, 'CAMPAIGN_CLEARED');
      else spawnWave(state);
    } else if (state.remainingTicks === 0) end(state, 'TIME_LIMIT');
  } else if (alive <= 1 || state.remainingTicks === 0) end(state, alive === 1 ? 'LAST_SURVIVOR' :
    alive === 0 ? 'MUTUAL_ELIMINATION' : 'TIME_LIMIT');
}
/** Chuyển sang ENDING; app.js chịu trách nhiệm lưu kết quả local một lần. */
const end = (state, reason) => {
  state.phase = 'ENDING';
  state.endReason = reason;
};
