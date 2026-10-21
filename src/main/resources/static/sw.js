/* Chỉ cache vỏ trang công khai và tài nguyên cho solo. Không cache API, CSRF, phiên hoặc WebSocket. */
const CACHE = 'utc-tank-ui-@UI_REVISION@';
const ASSETS = ['/offline', '/css/app.css', '/js/app.js', '/js/theme.js', '/js/api.js', '/js/ui.js',
  '/js/storage.js', '/js/offline-engine.js', '/js/game-renderer.js', '/js/game-art.js',
  '/js/online.js',
  '/icons/tank.svg', '/icons/mode-online.svg', '/icons/mode-campaign.svg',
  '/icons/battle-scene.svg', '/icons/lucide.svg',
  '/fonts/be-vietnam-pro-regular.ttf', '/fonts/be-vietnam-pro-bold.ttf', '/game-rules.json',
  '/game-content.json', '/campaign-stages.json', '/manifest.webmanifest'
];
/** Cài trọn bộ asset vào cache mới; tải lỗi thì giữ worker cũ tiếp tục phục vụ. */
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache
  .addAll(ASSETS))));
/** Khi đã kích hoạt, bỏ cache UI cũ và nhận điều khiển các trang đang mở. */
self.addEventListener('activate', event => event.waitUntil(Promise.all([caches.keys().then(keys =>
  Promise.all(keys.filter(key => key.startsWith('utc-tank-ui-') && key !== CACHE).map(
    key => caches.delete(key)))), self.clients.claim()])));
/** Trả trạng thái đủ tài nguyên cho nhãn sẵn sàng offline trên sidebar. */
self.addEventListener('message', event => {
  if (event.data === 'OFFLINE_STATUS') event.waitUntil(caches.open(CACHE).then(async cache => {
    const present = await Promise.all(ASSETS.map(asset => cache.match(asset)));
    event.ports[0]?.postMessage({
      ready: present.every(Boolean)
    });
  }));
});
self.addEventListener('message', event => {
  // app.js chỉ gửi sau thao tác cập nhật của người chơi khi đã rời trận.
  if (event.data === 'ACTIVATE_UPDATE') self.skipWaiting();
});
/** Navigation ưu tiên mạng; asset có trong danh sách mới được đọc từ cache. */
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin || url.pathname
    .startsWith('/api/') || url.pathname.startsWith('/auth/') || url.pathname.startsWith(
      '/oauth2/') || url.pathname.startsWith('/login/oauth2/')) return;
  if (event.request.mode === 'navigate') {
    // Vỏ Thymeleaf không chứa dữ liệu người dùng, được dựng từ API no-store sau khi mở trang.
    event.respondWith(fetch(event.request).catch(() => caches.match('/offline')));
    return;
  }
  if (!ASSETS.includes(url.pathname)) return;
  event.respondWith(caches.open(CACHE).then(async cache => (await cache.match(url.pathname)) ||
    fetch(event.request)));
});
