/**
 * Hình vẽ thuần hiển thị: không đọc input, không sinh RNG của trận và không sửa state.
 * Vẽ vector một lần vào sprite 3x, sau đó drawImage để giữ nét trên màn hình mật độ cao.
 * Cache có số biến thể cố định; GameRenderer giữ riêng lớp địa hình theo kích thước Canvas.
 */
export const itemLooks = {
  REPAIR: {
    label: 'Hồi máu',
    icon: 'heart',
    color: '#f5a6a0'
  },
  SHIELD: {
    label: 'Khiên',
    icon: 'shield',
    color: '#8cd5f0'
  },
  SPEED: {
    label: 'Tăng tốc',
    icon: 'zap',
    color: '#c4f36b'
  },
  DAMAGE_BOOST: {
    label: 'Sát thương',
    icon: 'target',
    color: '#f1b76c'
  },
  RAPID_FIRE: {
    label: 'Bắn nhanh',
    icon: 'crosshair',
    color: '#d4b3fa'
  }
};

const sprites = new Map();
const palette = new Set(['#c4f36b', '#76c9db', '#d3a1f3', '#f0b16a', '#edac68']);

/** Khối bo góc chung cho thân xe, xích và hộp vật phẩm. */
function box(ctx, x, y, width, height, radius, fill, stroke) {
  ctx.beginPath();
  ctx.roundRect(x, y, width, height, radius);
  ctx.fillStyle = fill;
  ctx.fill();
  if (stroke) {
    ctx.strokeStyle = stroke;
    ctx.lineWidth = 1;
    ctx.stroke();
  }
}

/** Thân xe có hai dải xích, giáp vát, cửa thoát và nòng; màu phân biệt đội. */
function tank(ctx, color) {
  box(ctx, -17, -12, 35, 32, 5, '#05100ac0');
  for (const x of [-16, 8]) {
    box(ctx, x, -15, 8, 32, 3, '#101b16', '#54705b');
    for (let y = -12; y <= 13; y += 5) {
      box(ctx, x + 1, y, 6, 2, 0.5, '#65836b');
    }
  }
  const hull = ctx.createLinearGradient(-11, -13, 11, 16);
  hull.addColorStop(0, '#efffd7');
  hull.addColorStop(0.14, color);
  hull.addColorStop(1, '#426947');
  box(ctx, -10, -14, 20, 30, 4, hull, '#07170b');
  box(ctx, -7, 7, 14, 6, 1, '#213d29');
  ctx.fillStyle = '#9cc481';
  for (let x = -5; x < 6; x += 3) ctx.fillRect(x, 8, 1, 4);
  box(ctx, -8, -10, 16, 17, 5, '#243c2b', '#eaffc188');
  box(ctx, -7, -11, 14, 16, 4, color, '#ecffd766');
  box(ctx, -3, -25, 6, 21, 1, '#243a2b');
  box(ctx, -2, -26, 4, 20, 1, color);
  box(ctx, -3, -26, 6, 4, 1, '#e9fbc4');
  ctx.beginPath();
  ctx.arc(3, -1, 3, 0, Math.PI * 2);
  ctx.fillStyle = '#36513a';
  ctx.fill();
  ctx.strokeStyle = '#ecffd788';
  ctx.stroke();
  ctx.fillStyle = '#e8f5d5';
  ctx.fillRect(-8, -12, 3, 2);
  ctx.fillRect(5, -12, 3, 2);
}

/** Ba silhouette riêng cho địch Campaign; chỉ hình dạng, không thay hitbox catalog. */
function creature(ctx, kind) {
  ctx.lineWidth = 1.2;
  ctx.strokeStyle = '#301b18';
  if (kind === 'bird') {
    ctx.fillStyle = '#8fd8df';
    ctx.beginPath();
    ctx.moveTo(0, -15);
    ctx.lineTo(18, 9);
    ctx.lineTo(6, 5);
    ctx.lineTo(0, 14);
    ctx.lineTo(-6, 5);
    ctx.lineTo(-18, 9);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = '#e2f8f0';
    ctx.beginPath();
    ctx.moveTo(0, -15);
    ctx.lineTo(5, 7);
    ctx.lineTo(0, 3);
    ctx.lineTo(-5, 7);
    ctx.closePath();
    ctx.fill();
    box(ctx, -2, -6, 4, 7, 2, '#285462');
  } else if (kind === 'butterfly') {
    for (const side of [-1, 1]) {
      ctx.fillStyle = '#d5b1f1';
      ctx.beginPath();
      ctx.ellipse(side * 8, -4, 8, 10, side * 0.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = '#8e69bd';
      ctx.beginPath();
      ctx.ellipse(side * 6, 7, 6, 7, -side * 0.4, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = '#f7e1ff';
      ctx.beginPath();
      ctx.arc(side * 9, -5, 3, 0, Math.PI * 2);
      ctx.fill();
    }
    box(ctx, -2, -11, 4, 25, 2, '#50395f');
  } else {
    ctx.strokeStyle = '#d59b60';
    ctx.lineWidth = 2;
    for (const y of [-8, 0, 8]) {
      ctx.beginPath();
      ctx.moveTo(-17, y + 5);
      ctx.lineTo(-10, y);
      ctx.moveTo(17, y + 5);
      ctx.lineTo(10, y);
      ctx.stroke();
    }
    const shell = ctx.createLinearGradient(-11, -13, 11, 14);
    shell.addColorStop(0, '#ffd59b');
    shell.addColorStop(1, '#a36540');
    ctx.fillStyle = shell;
    ctx.strokeStyle = '#563b29';
    ctx.beginPath();
    ctx.ellipse(0, 0, 12, 16, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(0, -14);
    ctx.lineTo(0, 15);
    ctx.moveTo(-10, -5);
    ctx.lineTo(0, 0);
    ctx.lineTo(10, -5);
    ctx.stroke();
    box(ctx, -6, -16, 12, 6, 3, '#563b29');
  }
}

/** Biểu tượng item là nét vector, tránh phụ thuộc glyph/emoji khác nhau trên iOS/Android. */
function pickup(ctx, effect) {
  const color = (itemLooks[effect] || itemLooks.REPAIR).color;
  box(ctx, -14, -12, 28, 28, 6, '#05100a88');
  box(ctx, -14, -15, 28, 28, 6, '#1a2b22', color);
  box(ctx, -11, -12, 22, 22, 4, color + '22');
  ctx.lineWidth = 2.5;
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.beginPath();
  if (effect === 'SHIELD') {
    ctx.moveTo(0, -8);
    ctx.lineTo(7, -5);
    ctx.lineTo(6, 2);
    ctx.quadraticCurveTo(4, 6, 0, 8);
    ctx.quadraticCurveTo(-4, 6, -6, 2);
    ctx.lineTo(-7, -5);
    ctx.closePath();
    ctx.stroke();
  } else if (effect === 'SPEED' || effect === 'RAPID_FIRE') {
    for (const x of effect === 'SPEED' ? [-5, 3] : [-1]) {
      ctx.moveTo(x, -8);
      ctx.lineTo(x - 4, 1);
      ctx.lineTo(x + 2, 1);
      ctx.lineTo(x - 1, 8);
    }
    ctx.stroke();
  } else if (effect === 'DAMAGE_BOOST') {
    ctx.arc(0, 0, 6, 0, Math.PI * 2);
    ctx.moveTo(-9, 0);
    ctx.lineTo(9, 0);
    ctx.moveTo(0, -9);
    ctx.lineTo(0, 9);
    ctx.stroke();
  } else {
    box(ctx, -2, -8, 4, 16, 1, color);
    box(ctx, -8, -2, 16, 4, 1, color);
  }
}

/** Chỉ chấp nhận biến thể nội bộ để dữ liệu server không làm cache tăng vô hạn. */
export function sprite(kind, requestedColor = '#c4f36b') {
  const color = palette.has(requestedColor) ? requestedColor : '#c4f36b';
  const safeKind = ['tank', 'bird', 'butterfly', 'beetle', ...Object.keys(itemLooks)]
    .includes(kind) ? kind : 'tank';
  const key = safeKind + (safeKind === 'tank' ? color : '');
  if (sprites.has(key)) return sprites.get(key);
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 192;
  const ctx = canvas.getContext('2d');
  ctx.scale(3, 3);
  ctx.translate(32, 32);
  if (safeKind === 'tank') tank(ctx, color);
  else if (itemLooks[safeKind]) pickup(ctx, safeKind);
  else creature(ctx, safeKind);
  sprites.set(key, canvas);
  return canvas;
}

/** Địa hình chỉ vẽ khi đổi map/resize. Trang trí không thêm vật cản ngoài rules.walls. */
export function paintTerrain(ctx, rules) {
  const ground = ctx.createLinearGradient(0, 0, rules.width, rules.height);
  ground.addColorStop(0, '#26392c');
  ground.addColorStop(0.5, '#1c3028');
  ground.addColorStop(1, '#263a2a');
  ctx.fillStyle = ground;
  ctx.fillRect(0, 0, rules.width, rules.height);
  ctx.fillStyle = '#b5c69a05';
  ctx.fillRect(0, rules.height / 2 - 26, rules.width, 52);
  ctx.fillRect(rules.width / 2 - 26, 0, 52, rules.height);
  // Phân bố cố định cho cỏ/đất; không tiêu thụ seed của mô phỏng.
  for (let i = 0; i < 650; i++) {
    const x = (i * 179 + 31) % rules.width;
    const y = (i * 107 + 81) % rules.height;
    ctx.fillStyle = i % 3 ? '#b9d0990b' : '#071b1455';
    ctx.fillRect(x, y, i % 4 + 1, 2);
  }
  ctx.strokeStyle = '#d0edb608';
  ctx.lineWidth = 1;
  for (let x = 0; x <= rules.width; x += rules.tile) {
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, rules.height);
    ctx.stroke();
  }
  for (let y = 0; y <= rules.height; y += rules.tile) {
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(rules.width, y);
    ctx.stroke();
  }
  rules.spawns.forEach((spawn, index) => {
    ctx.strokeStyle = '#b4cf8155';
    ctx.setLineDash([4, 5]);
    ctx.strokeRect(spawn.x - 20, spawn.y - 20, 40, 40);
    ctx.setLineDash([]);
    ctx.fillStyle = '#cce3b475';
    ctx.textAlign = 'center';
    ctx.font = '9px monospace';
    ctx.fillText('0' + (index + 1), spawn.x, spawn.y + 4);
  });
  for (const wall of rules.walls) {
    box(ctx, wall.x + 4, wall.y + 6, wall.w, wall.h, 2, '#06140e88');
    const stone = ctx.createLinearGradient(wall.x, wall.y, wall.x + wall.w, wall.y + wall.h);
    stone.addColorStop(0, '#77856a');
    stone.addColorStop(1, '#435c49');
    box(ctx, wall.x, wall.y, wall.w, wall.h, 2, stone, '#253b2c');
    ctx.fillStyle = '#c6d4a94d';
    ctx.fillRect(wall.x + 1, wall.y + 1, wall.w - 2, 3);
    ctx.strokeStyle = '#253b2c99';
    ctx.lineWidth = 1;
    for (let y = wall.y + 16; y < wall.y + wall.h; y += 16) {
      ctx.beginPath();
      ctx.moveTo(wall.x, y);
      ctx.lineTo(wall.x + wall.w, y);
      ctx.stroke();
    }
    for (let x = wall.x + 16; x < wall.x + wall.w; x += 32) {
      ctx.beginPath();
      ctx.moveTo(x, wall.y);
      ctx.lineTo(x, wall.y + wall.h);
      ctx.stroke();
    }
  }
  ctx.strokeStyle = '#a7c28655';
  ctx.strokeRect(1, 1, rules.width - 2, rules.height - 2);
}
