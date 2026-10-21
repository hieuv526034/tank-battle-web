/**
 * ĐIỂM VÀO UI — thứ tự đọc luồng:
 * 1. initializeAuth/api.js khôi phục danh tính; router kiểm routeVersion sau mỗi await.
 * 2. renderRoute dựng trang, cleanups tháo listener/socket/Canvas của màn hình trước.
 * 3. soloGame: khóa slot → restore → tick 50 ms → hàng đợi autosave → kết toán local.
 * 4. onlineGame: frame server → renderer/HUD; chỉ gửi intent, không tự tính điểm online.
 * 5. uploadSave/downloadSave dùng revision và xác nhận xung đột, không tự chọn bản thắng.
 * Xem tài liệu 25–27 trong .agents/docs để theo dõi quyền, nơi ghi DB và cách kiểm thử.
 */
import {
  api,
  initializeAuth,
  authUnavailable,
  login,
  register,
  logout,
  user,
  onAuth,
  updateProfile,
  webSession
} from './api.js';
import {
  esc,
  icon,
  avatar,
  number,
  date,
  difficultyName,
  statusName,
  outcomeName,
  loading,
  empty,
  button,
  toast,
  openModal,
  modalHead,
  closeModal,
  confirmAction,
  formError
} from './ui.js';
import {
  loadCatalog,
  createGame,
  restoreGame,
  step
} from './offline-engine.js';
import {
  GameRenderer,
  GameControls,
  gameMarkup,
  updateHud
} from './game-renderer.js';
import {
  RoomConnection
} from './online.js';
import * as storage from './storage.js';

const main = document.querySelector('#main');
const titles = {
  '/': 'Trạm chỉ huy',
  '/rooms': 'Phòng chơi online',
  '/leaderboard': 'Bảng xếp hạng',
  '/offline': 'Solo offline',
  '/history': 'Lịch sử trận đấu',
  '/saves': 'Bản lưu của tôi',
  '/profile': 'Hồ sơ chỉ huy',
  '/help': 'Hướng dẫn',
  '/login': 'Đăng nhập',
  '/register': 'Tạo tài khoản'
};
let routeVersion = 0,
  cleanups = [],
  connection = null,
  solo = null,
  offlineReady = false,
  workerRegistration = null,
  workerWatching = false,
  updatePending = false;
let roomsFilter = 'all',
  rankMode = 'ARENA',
  rankDifficulty = 'NORMAL',
  rankPage = 0,
  historyTab = 'online',
  historyPage = 0;
let catalogFlight;
const catalog = () => catalogFlight || (catalogFlight = loadCatalog().catch(error => {
  catalogFlight = null;
  throw error;
}));
const scope = () => storage.scopeFor(user, authUnavailable || !navigator.onLine);
const cleanup = fn => cleanups.push(fn);
// Phản hồi API của trang đã rời không được ghi đè DOM trang mới.
const alive = version => version === routeVersion;
const setContent = (version, html) => {
  if (alive(version)) main.innerHTML = html;
};
const currentPath = () => location.pathname.replace(/\/$/, '') || '/';
const label = () => titles[currentPath()] || (currentPath().startsWith('/rooms/') ? 'Phòng chờ' :
  currentPath().startsWith('/play/') ? 'Chiến trường' : 'Trạm chỉ huy');
const heading = (title, description = '', actions = '') =>
  `
    <div class="page-heading">
      <div>
        <h1>${esc(title)}</h1>${
          description ? `
          <p>${esc(description)}</p>
          ` : ''
        }
      </div>${
        actions ? `
        <div class="heading-actions">${actions}</div>
        ` : ''
      }
    </div>
    `;
const field = (name, label, type = 'text', extra = '', hint = '') =>
  `
    <div class="field">
      <label for="${name}">${label}</label>
      <div class="${type === 'password' ? 'password-wrap' : ''}">
        <input id="${name}" name="${name}" type="${type}" ${extra}>${
          type === 'password' ? `
          <button type="button" class="icon-button" data-action="password" data-target="${name}"
            aria-label="Hiện mật khẩu">${icon('eye')}</button>
          ` : ''
        }
      </div>${
        hint ? `
        <small>${hint}</small>
        ` : ''
      }<small class="field-error" id="${name}-error"></small>
    </div>
    `;
const option = (value, label, selected) =>
  `
    <option value="${value}" ${selected === value ? 'selected' : ''}>${label}</option>
    `;
const mobileMenu = matchMedia('(max-width: 760px), (max-width: 1000px) and (max-height: 500px)');

/** Đóng lớp menu mobile, khóa focus sidebar khi nó nằm ngoài màn hình. */
function closeMenu() {
  document.querySelector('#sidebar').classList.remove('open');
  document.querySelector('#sidebar').inert = mobileMenu.matches;
  document.querySelector('#menu-backdrop').hidden = true;
  document.querySelector('[data-action=menu]')?.setAttribute('aria-expanded', 'false');
}
mobileMenu.addEventListener('change', closeMenu);

/** Dựng sidebar/topbar theo route, tài khoản, trạng thái mạng và theme; không dựng lại Canvas. */
function renderChrome() {
  const path = currentPath();
  const links = [
    ['/', 'house', 'Trạm chỉ huy'],
    ['/rooms', 'swords', 'Phòng chơi online'],
    ['/offline', 'gamepad-2', 'Solo offline'],
    ['/leaderboard', 'trophy', 'Bảng xếp hạng']
  ];
  const personal = [
    ['/history', 'history', 'Lịch sử trận đấu'],
    ['/saves', 'cloud-download', 'Bản lưu của tôi'],
    ['/profile', 'user-round', 'Hồ sơ chỉ huy']
  ];
  const nav = items => items.map(([href, symbol, title]) =>
    `
      <a class="nav-link ${(href === '/' ? path === '/' : path.startsWith(href)) ? 'active' : ''}" href="${
        href
      }" data-link ${(href === '/' ? path === '/' : path.startsWith(href)) ? 'aria-current="page"' : ''}>
        ${icon(symbol)}${title}${href === '/offline' ? '<span class="nav-mini">SOLO</span>' : ''}</a>
      `
  ).join('');
  document.querySelector('#sidebar').innerHTML =
    `
      <button class="icon-button sidebar-close" data-action="close-menu" aria-label="Đóng menu">
        ${icon('x')}</button>
      <a href="/" data-link class="brand" aria-label="UTC Tank Battle trang chủ">
        <img src="/icons/tank.svg" alt="">
        <div>
          <strong>TANK BATTLE</strong>
          <span>UTC · BATTLEFIELD</span>
        </div>
      </a>
      <div class="nav-label">CHIẾN TRƯỜNG</div>
      <nav>${nav(links)}</nav>
      <div class="nav-label">CÁ NHÂN</div>
      <nav>${nav(personal)}</nav>
      <div class="sidebar-bottom">
        <a class="nav-link ${path === '/help' ? 'active' : ''}" data-link href="/help">${
          icon('circle-help')
        }Hướng dẫn chơi${icon('arrow-up-right')}</a>
        <div class="offline-note">
          <strong>${icon(offlineReady ? 'circle-check' : 'download')}<span>${
            offlineReady ? 'Đã sẵn sàng offline' : 'Chuẩn bị chơi mọi nơi'
          }</span>
          </strong>
          <p>${
            offlineReady ? 'Tài nguyên đã lưu trên máy. Mở Solo và xuất trận khi không có mạng.' :
              'Mở trò chơi khi có mạng để tải đủ tài nguyên cho chế độ solo.'
          }</p>
        </div>
        <div class="sidebar-user">${avatar(user?.displayName || 'Khách')}<div>
            <strong>${esc(user?.displayName || 'Chỉ huy mới')}</strong>
            <small>${user ? 'Tài khoản đã đăng nhập' : 'Sẵn sàng cho trận đầu?'}</small>
          </div>${
            user ? `
            <button class="icon-button" data-action="logout" aria-label="Đăng xuất">${icon('log-out')}</button>
            ` : `
            <a class="icon-button" href="/login" data-link aria-label="Đăng nhập">${icon('log-in')}</a>
            `
          }</div>
      </div>
      `;
  document.querySelector('#topbar').innerHTML =
    `
      <button class="icon-button mobile-menu" data-action="menu" aria-label="Mở menu"
        aria-expanded="false">${icon('menu')}</button>
      <div class="breadcrumb">
        <span>UTC Tank Battle</span>${icon('chevron-right')}<b>${esc(label())}</b>
      </div>
      <div class="topbar-actions">
        <button class="icon-button theme-toggle" data-action="theme" aria-label="${
          document.documentElement.dataset.theme === 'dark' ? 'Chuyển sang nền sáng' : 'Chuyển sang nền tối'
        }"
          title="${
            document.documentElement.dataset.theme === 'dark' ? 'Chuyển sang nền sáng' : 'Chuyển sang nền tối'
          }">
          <span aria-hidden="true">${document.documentElement.dataset.theme === 'dark' ? '☀' : '☾'}</span>
        </button>${
          updatePending ? `
          <button class="icon-button" data-action="update-ui" aria-label="Cập nhật giao diện"
            title="Cập nhật giao diện">${icon('refresh-cw')}</button>
          ` : ''
        }<span class="connection">
          <i class="dot ${navigator.onLine ? '' : 'offline'}"></i>${
            navigator.onLine ? 'Có kết nối mạng' : 'Đang offline'
          }
        </span>
        <a class="icon-button" href="/help" data-link aria-label="Hướng dẫn điều khiển">${
          icon('keyboard')
        }</a>
        ${
          user ? `
          <a href="/profile" data-link aria-label="Mở hồ sơ">${avatar(user.displayName)}</a>
          ` : button('Đăng nhập', 'log-in', '/login', 'secondary small')
        }
      </div>
      `;
  document.title = `${label()} · UTC Tank Battle`;
  closeMenu();
}
/** Lưu trận solo trước khi rời; cập nhật History API rồi chuyển màn hình. */
async function navigate(path, replace = false) {
  const target = new URL(path, location.origin);
  if (solo?.state.phase === 'RUNNING' && !solo.paused && target.pathname + target.search !==
    location.pathname + location.search) {
    if (!await confirmAction('Lưu trận và rời thao trường?',
        'Trận solo được lưu trên máy. Bạn có thể tiếp tục ở mục Bản lưu của tôi.', 'Lưu và rời'))
      return;
    solo.paused = true;
    await solo.persist().catch(error => toast(error.message, true));
  }
  history[replace ? 'replaceState' : 'pushState']({}, '', target.pathname + target.search);
  document.querySelector('#sidebar').classList.remove('open');
  await renderRoute();
}

/** Hiện lời mời đăng nhập cho trang cần tài khoản; vẫn cho mở solo công khai. */
function needLogin() {
  return heading('Sẵn sàng gia nhập đội hình?',
    'Đăng nhập để chơi cùng bạn bè, lưu thành tích và đồng bộ tiến trình.') + empty(
    'Đăng nhập để tiếp tục',
    'Bạn vẫn có thể chơi solo offline và xem bảng xếp hạng mà không cần tài khoản.',
    `
      <div class="actions" style="justify-content:center">${button('Đăng nhập', 'log-in', '/login')}${
        button('Chơi solo', 'gamepad-2', '/offline', 'secondary')
      }</div>
      `,
    'shield');
}
/** Tăng phiên route, thu hồi listener/timer cũ và bỏ qua response của màn hình đã rời. */
async function renderRoute() {
  const version = ++routeVersion;
  await Promise.all(cleanups.splice(0).map(dispose => Promise.resolve(dispose()).catch(
    () => {})));
  if (!alive(version)) return;
  solo = null;
  closeModal();
  renderChrome();
  const path = currentPath();
  main.classList.toggle('game-page', path.startsWith('/play/') ||
    (path === '/offline' && new URLSearchParams(location.search).get('play') === '1'));
  const privatePage = ['/rooms', '/profile', '/play'].some(prefix => path === prefix || path
    .startsWith(prefix + '/'));
  // Vùng IndexedDB phụ thuộc danh tính: đợi auth một lần khi tải trang trực tiếp.
  // Nếu không đợi, URL chơi tiếp có thể mở nhầm slot guest trước khi biết userId.
  const localPage = ['/offline', '/saves', '/history'].includes(path);
  if ((privatePage || localPage) && !user) {
    main.innerHTML = loading('Đang xác định phiên và vùng lưu…');
    await authReady;
    if (!alive(version)) return;
    if (privatePage && !user) {
      main.innerHTML = needLogin();
      return;
    }
  }
  if (!navigator.onLine && privatePage && path !== '/saves' && !(path === '/history' &&
      historyTab === 'offline')) {
    main.innerHTML = heading('Bạn đang offline', 'Kết nối mạng để truy cập tính năng này.') +
      empty('Thao trường solo vẫn đang mở',
        'Bản lưu trên máy và chế độ solo hoạt động khi không có mạng.', button('Mở solo',
          'gamepad-2', '/offline'), 'wifi-off');
    return;
  }
  try {
    if (path === '/') await home(version);
    else if (path === '/login' || path === '/register') await authPage(version, path ===
      '/register');
    else if (path === '/rooms') await roomsPage(version);
    else if (/^\/rooms\/[A-Z2-9]{8}$/.test(path)) await lobbyPage(version, path.split('/')[2]);
    else if (/^\/play\/[a-f0-9-]{36}$/.test(path)) await onlineGame(version, path.split('/')[2]);
    else if (path === '/leaderboard') await leaderboardPage(version);
    else if (path === '/history') await historyPageView(version);
    else if (path === '/profile') await profilePage(version);
    else if (path === '/saves') await savesPage(version);
    else if (path === '/offline') {
      const query = new URLSearchParams(location.search);
      if (query.get('play') === '1') await soloGame(version, Number(query.get('slot') || 1));
      else await offlinePage(version);
    } else if (path === '/help') helpPage(version);
    else setContent(version, heading('Không tìm thấy màn hình') + empty(
      'Có vẻ bạn đã đi lạc thao trường', 'Quay lại trạm chỉ huy để chọn nhiệm vụ tiếp theo.',
      button('Về trang chủ', 'house', '/')));
  } catch (error) {
    if (alive(version)) {
      main.innerHTML = heading(label()) +
        `
          <div class="error-box" role="alert">${icon('circle-alert')}<span>${esc(error.message)}</span>
          </div>
          <button class="btn secondary" data-action="retry">${icon('refresh-cw')}Thử lại</button>
          `;
    }
  }
  if (alive(version)) main.focus({
    preventScroll: true
  });
  window.scrollTo(0, 0);
}

/** Dựng trang chủ ngay; bảng hạng tải sau và chỉ gắn khi route còn hiệu lực. */
function home(version) {
  setContent(version,
    `
      <div class="page-heading">
        <div>
          <p class="greeting">${
            user ? `Chào trở lại, ${esc(user.displayName)} 👋` : 'Chào mừng đến với UTC Tank Battle'
          }</p>
          <h1>Trạm chỉ huy</h1>
        </div>
        <span class="tag neutral">${icon('crosshair')}SẴN SÀNG XUẤT TRẬN</span>
      </div>
      <section class="hero">
        <div class="hero-copy">
          <span class="tag">${icon('flag')}CHỌN ĐỒNG ĐỘI. VIẾT CHIẾN THẮNG.</span>
          <h2>CHIẾN TRƯỜNG
            <br>
            <span>ĐANG CHỜ BẠN.</span>
          </h2>
          <p>Lên xe, tìm đồng đội và làm chủ thao trường.
            <br>Mỗi đường đạn, một quyết định.
          </p>
          <div class="actions">${button('Vào chiến trường', 'swords', user ? '/rooms' : '/login')}${
            button('Thử sức solo', 'arrow-up-right', '/offline', 'secondary')
          }</div>
        </div>
        <div class="hero-art">
          <img src="/icons/battle-scene.svg" alt="Xe tăng xanh trên bản đồ chiến thuật">
          <span class="hero-coordinate">21°02′N · 105°48′E /
            UTC BASE</span>
        </div>
      </section>
      <div class="stats">${
        [
          ['swords', '02', 'Chế độ chiến đấu'],
          ['flag', '03', 'Cửa chiến dịch'],
          ['users', '2–4', 'Người chơi / phòng'],
          ['wifi-off', '∞', 'Chơi solo mọi lúc']
        ].map(([symbol, value, text]) => `
        <div class="stat">
          <div class="stat-icon">${icon(symbol)}</div>
          <div>
            <strong>${value}</strong>
            <small>${text}</small>
          </div>
        </div>
        `).join('')
      }</div>
      <div class="section-heading">
        <h2>Chọn cách bạn chiến đấu</h2>
        <span class="muted">Một thao trường. Nhiều chiến thuật.</span>
      </div>
      <div class="game-modes">
        <a class="mode-card" data-link href="${user ? '/rooms' : '/login'}">
          <div class="mode-copy">
            <div class="eyebrow">MULTIPLAYER · 2–4 NGƯỜI</div>
            <h3>Không đi một mình.</h3>
            <p>Đối đầu Arena hoặc hợp tác vượt Campaign. Gọi đội hình, lên chiến thuật.</p>
            <span class="btn small secondary">Tìm phòng chơi ${icon('arrow-right')}</span>
          </div>
          <div class="mode-art">
            <img src="/icons/mode-online.svg" width="240" height="220" alt="" loading="lazy">
          </div>
        </a>
        <a class="mode-card solo" href="/offline" data-link>
          <div class="mode-copy">
            <div class="eyebrow">SOLO · KHÔNG CẦN MẠNG</div>
            <h3>Một mình. Vẫn chiến.</h3>
            <p>Luyện tay với bot, chinh phục cửa ải. Lưu lại và tiếp tục bất cứ lúc nào.</p>
            <span class="btn small secondary">Vào thao trường ${icon('arrow-right')}</span>
          </div>
          <div class="mode-art">
            <img src="/icons/mode-campaign.svg" width="240" height="220" alt="" loading="lazy">
          </div>
        </a>
      </div>
      <div class="home-bottom">
        <section class="panel">
          <div class="panel-title">
            <h2>Chỉ huy dẫn đầu</h2>
            <a class="btn text small" href="/leaderboard" data-link>Xem bảng hạng
              ${icon('arrow-up-right')}</a>
          </div>
          <div id="home-ranking">${loading('Đang tải bảng hạng…')}</div>
        </section>
        <section class="panel">
          <div class="panel-title">
            <h2>Trận đầu? Đơn giản thôi.</h2>${icon('rocket')}
          </div>${
            [
              ['01', 'Chọn chiến trường', 'Tìm phòng online hoặc thử sức với solo.'],
              ['02', 'Sẵn sàng đội hình', 'WASD để di chuyển. Space để khai hỏa.'],
              ['03', 'Làm chủ trận đấu', 'Tận dụng địa hình, nhặt vật phẩm và chiến thắng.']
            ].map(([n, title, text]) => `
            <div class="mini-guide">
              <span class="step-number">${n}</span>
              <div>
                <h3>${title}</h3>
                <p>${text}</p>
              </div>
            </div>
            `).join('')
          }
        </section>
      </div>
      `
  );
  api('/api/rankings?size=3', {
    auth: false
  }).then(rows => {
    if (!alive(version)) return;
    document.querySelector('#home-ranking').innerHTML = rows.length ? rows.map(row =>
        `
          <div class="rank-mini">
            <span class="place">#${row.rank}</span>${avatar(row.displayName)}<strong>${esc(row.displayName)}</strong>
            <small>${number(row.points)}
              điểm</small>
          </div>
          `
      ).join('') :
      `
        <div class="empty compact">${icon('trophy')}<h3>Bảng hạng đang chờ tên bạn</h3>
          <p>Hoàn thành trận Arena online đầu tiên để ghi dấu trên bảng hạng.</p>
        </div>
        `;
  }).catch(error => {
    if (alive(version)) document.querySelector('#home-ranking').innerHTML =
      `
        <div class="empty compact">
          <p>${esc(error.message)}</p>
          <a class="btn text" href="/leaderboard" data-link>Mở bảng xếp hạng
            ${icon('arrow-right')}</a>
        </div>
        `;
  });
}
/** Dựng form đăng nhập/đăng ký; chỉ hiện Google khi server bật nhà cung cấp. */
async function authPage(version, isRegister) {
  if (user) {
    setContent(version, heading('Bạn đã đăng nhập',
      `Chào ${user.displayName}, đội hình đang chờ bạn.`) + empty('Sẵn sàng xuất trận?',
      'Mở phòng chơi để bắt đầu trận mới.', button('Đến sảnh chơi', 'swords', '/rooms')));
    return;
  }
  setContent(version,
    `
      <div class="auth-layout">
        <div class="auth-story">
          <div class="eyebrow">UTC TANK BATTLE / CHÀO MỪNG CHỈ HUY</div>
          <h1>Cùng đội hình.
            <br>
            <span>Vượt chiến trường.</span>
          </h1>
          <p>Những trận đấu ngắn. Những chiến thuật dài hơi.
            <br>Ngồi vào vị trí và viết nên chiến thắng
            của bạn.
          </p>
          <img src="/icons/battle-scene.svg" alt="Xe tăng trên thao trường">
          <div class="actions">
            <span class="tag neutral">${icon('shield')}Chơi ngay trên trình
              duyệt</span>
            <span class="tag neutral">${icon('gamepad-2')}Solo & Online</span>
          </div>
        </div>
        <section class="auth-card">
          <h2>${isRegister ? 'Gia nhập đội hình' : 'Chào trở lại, chỉ huy.'}</h2>
          <p>${
            isRegister ? 'Tạo tài khoản để lưu thành tích và chơi cùng bạn bè.' :
              'Đăng nhập để tiếp tục hành trình của bạn.'
          }</p>
          <form id="auth-form" class="form-stack" data-form="${isRegister ? 'register' : 'login'}">
            <div class="error-box" role="alert"></div>
            ${
              field('username', 'Tên đăng nhập', 'text',
                'required minlength="4" maxlength="24" pattern="[A-Za-z0-9_]{4,24}" autocomplete="username" placeholder="Tên đăng nhập của bạn" autocapitalize="none" spellcheck="false" aria-describedby="username-error"',
                isRegister ? '4–24 chữ cái, chữ số hoặc dấu gạch dưới.' : '')
            }${
              isRegister ? field('displayName', 'Tên hiển thị', 'text',
                'required minlength="2" maxlength="30" autocomplete="nickname" placeholder="Mọi người sẽ gọi bạn là…" aria-describedby="displayName-error"'
              ) : ''
            }${
              field('password', 'Mật khẩu', 'password',
                `required minlength="12" maxlength="128" autocomplete="${isRegister ? 'new-password' : 'current-password'}" placeholder="${isRegister ? 'Từ 12 đến 64 ký tự' : 'Nhập mật khẩu của bạn'}" aria-describedby="password-error"`,
                isRegister ? 'Mật khẩu được giữ nguyên, kể cả dấu cách.' : '')
            }${
              isRegister ? field('confirmPassword', 'Xác nhận mật khẩu', 'password',
                'required autocomplete="new-password" placeholder="Nhập lại mật khẩu" aria-describedby="confirmPassword-error"'
              ) : ''
            }<button class="btn primary wide"
              type="submit">${isRegister ? 'Tạo tài khoản' : 'Đăng nhập'}${icon('arrow-right')}</button>
          </form>
          <div id="google-login"></div>
          <p class="auth-foot">${
            isRegister ? 'Đã có tài khoản? <a href="/login" data-link>Đăng nhập</a>' :
              'Chưa có tài khoản? <a href="/register" data-link>Gia nhập ngay</a>'
          }</p>
          <div class="divider"></div>
          <a class="btn text wide" href="/offline" data-link>${icon('gamepad-2')}Chơi
            solo không cần tài khoản</a>
        </section>
      </div>
      `
  );
  if (navigator.onLine) webSession().then(web => {
    if (alive(version) && web.googleEnabled) document.querySelector('#google-login')
      .innerHTML =
      `
        <div class="auth-divider">hoặc tiếp tục với</div>
        <a class="btn google wide" href="/oauth2/authorization/google">
          <b aria-hidden="true">G</b>Đăng nhập với Google
        </a>
        `;
  }).catch(() => {});
}

/** Chuyển dữ liệu phòng thành thẻ; mọi tên do người dùng nhập đều qua esc. */
function roomCard(room, current) {
  return `
    <article class="room-card" data-room-card data-name="${
      esc(room.name.toLowerCase() + ' ' + room.code.toLowerCase())
    }" data-status="${esc(room.status)}">
      <div class="room-top">
        <span class="tag ${room.status === 'WAITING' ? '' : 'orange'}">${
          room.status === 'WAITING' ? '<i class="dot"></i>' : ''
        }${esc(statusName(room.status))}</span>${icon('users')}
      </div>
      <h3>${esc(room.name)}</h3>
      <div class="room-code">PHÒNG / ${esc(room.code)}</div>
      <div class="avatars">${
        Array.from({
            length: room.capacity
          }, (_, index) => room.members[index] ? avatar(room.members[index].displayName) :
          '<span class="seat">+</span>').join('')
      }<span class="count">${room.members.length}/${room.capacity} người</span>
      </div>
      ${
        current?.code === room.code ? button('Quay lại phòng', 'arrow-right', `/rooms/${room.code}`,
          'primary wide') : `
        <button class="btn ${
          room.status === 'WAITING' && room.members.length < room.capacity ? 'secondary' : 'secondary'
        } wide" data-action="join-room" data-code="${esc(room.code)}" ${
          room.status !== 'WAITING' || room.members.length >= room.capacity || current ? 'disabled' : ''
        }>
          ${
            room.members.length >= room.capacity ? 'Phòng đã đầy' : room.status !== 'WAITING' ?
              'Trận đang diễn ra' : 'Tham gia phòng'
          } ${icon('arrow-right')}</button>
        `
      }
    </article>
    `;
}
/** Nạp danh sách và phòng đang tham gia; bộ lọc chỉ thay vùng danh sách. */
async function roomsPage(version) {
  setContent(version, loading());
  const [rooms, current] = await Promise.all([api('/api/rooms'), api('/api/rooms/current')]);
  if (!alive(version)) return;
  setContent(version, heading('Tìm đội hình của bạn',
      'Tham gia một phòng, gọi bạn bè và cùng xuất trận.',
      `
        <button class="icon-button" data-action="retry" aria-label="Làm mới danh sách">${
          icon('refresh-cw')
        }</button>
        <button class="btn primary" data-action="create-room" ${current ? 'disabled' : ''}>${icon('plus')}Tạo phòng</button>
        `
    ) + (current ?
      `
        <div class="notice info">${icon('radio')}<div>Bạn đang ở <b>${esc(current.name)}</b>. <a class="lime" data-link
              href="/rooms/${esc(current.code)}">Quay lại phòng ${icon('arrow-right')}</a>
          </div>
        </div>
        ` :
      '') +
    `
      <div class="filters">
        <div class="tabs" aria-label="Lọc phòng">
          <button class="tab ${roomsFilter === 'all' ? 'active' : ''}" data-action="filter-rooms" data-filter="all">Tất cả phòng
          </button>
          <button class="tab ${roomsFilter === 'WAITING' ? 'active' : ''}" data-action="filter-rooms" data-filter="WAITING">Đang chờ
          </button>
        </div>
        <div class="search-box">${icon('search')}
          <input id="room-search" type="search" placeholder="Tìm tên hoặc mã phòng…"
            aria-label="Tìm phòng">
        </div>
      </div>
      <div class="room-grid" id="room-grid">${rooms.map(room => roomCard(room, current)).join('')}</div>
      <div id="room-empty">${
        rooms.length ? '' : empty('Chưa có phòng đang mở',
          'Tạo phòng đầu tiên, chọn số ghế và mời bạn bè tham gia.',
          '<button class="btn primary" data-action="create-room">' + icon('plus') +
          'Tạo phòng đầu tiên</button>', 'users')
      }</div>
      <div class="join-panel">${icon('crosshair')}<div>
          <strong>Có mã phòng từ đồng đội?</strong>
          <p>Nhập mã 8 ký tự để vào đúng đội hình.</p>
        </div>
        <form data-form="join-room">
          <input name="code" required minlength="8" maxlength="8" pattern="[A-HJ-NP-Za-hj-np-z2-9]{8}"
            aria-label="Mã phòng" placeholder="ABCD2345" autocomplete="off" autocapitalize="characters">
          <button class="btn secondary small" ${current ? 'disabled' : ''}>Vào phòng ${
            icon('arrow-right')
          }</button>
        </form>
      </div>
      `
  );
  applyRoomFilter();
}

/** Lọc thẻ phòng đang hiển thị theo từ khóa và loại phòng, không gọi thêm API. */
function applyRoomFilter() {
  const search = document.querySelector('#room-search')?.value.trim().toLowerCase() || '';
  const cards = [...document.querySelectorAll('[data-room-card]')];
  if (!cards.length) return;
  cards.forEach(card => card.hidden = !(card.dataset.name.includes(search) && (roomsFilter ===
    'all' || card.dataset.status === roomsFilter)));
  const box = document.querySelector('#room-empty');
  if (box) box.innerHTML = cards.every(card => card.hidden) ? empty('Không tìm thấy phòng phù hợp',
    'Thử tìm bằng mã khác hoặc đổi bộ lọc.', '', 'search') : '';
}

/** Dùng một RoomConnection cho phòng hiện tại; dừng socket khi đổi phòng. */
function ensureConnection(code) {
  if (connection?.code === code && !connection.closed) return connection;
  connection?.stop();
  connection = new RoomConnection(code);
  let lastError = 0;
  connection.addEventListener('ERROR', event => {
    if (Date.now() - lastError > 8000) {
      toast(event.detail.message || 'Kết nối chưa sẵn sàng.', true);
      lastError = Date.now();
    }
  });
  connection.connect();
  return connection;
}
/** Nạp lobby rồi nghe sự kiện phòng/trận; đăng ký cleanup trước khi rời route. */
async function lobbyPage(version, code) {
  setContent(version, loading('Đang vào phòng chờ…'));
  const initial = await api(`/api/rooms/${code}`);
  if (!alive(version)) return;
  const socket = ensureConnection(code);
  socket.lobby = initial;
  setContent(version, heading(initial.room.name,
      'Kiểm tra đội hình, chọn chiến dịch và sẵn sàng xuất trận.',
      `
        <button class="btn secondary small" data-action="copy-code" data-code="${code}">
          ${icon('copy')}${code}</button>
        <button class="btn danger small" data-action="leave-room" data-code="${code}">Rời phòng
        </button>
        `
    ) +
    `
      <div class="two-column">
        <section class="panel">
          <div class="panel-title">
            <h2>Đội hình phòng chờ</h2>
            <span class="tag neutral" id="lobby-count"></span>
          </div>
          <div id="lobby-members"></div>
          <div class="divider"></div>
          <div id="lobby-connection" class="muted" style="font-size:11px">Đang kết nối chiến trường…</div>
          <div id="lobby-recovery"></div>
        </section>
        <section class="panel">
          <div class="panel-title">
            <h2>Thiết lập trận đấu</h2>${icon('settings')}
          </div>
          <form data-form="start-match" data-code="${code}" class="form-stack">
            <div class="error-box" role="alert"></div>
            <div class="field">
              <label for="match-mode">Chế độ chiến đấu</label>
              <select id="match-mode" name="mode">
                <option value="ARENA">Arena · Đối đầu 2–4 người</option>
                <option value="CAMPAIGN">Campaign · Hợp tác 1–4 người</option>
              </select>
            </div>
            <div class="field">
              <label for="match-difficulty">Độ khó Campaign</label>
              <select id="match-difficulty" name="difficulty">${
                ['EASY', 'NORMAL', 'HARD'].map(value => option(value, difficultyName(value), 'NORMAL')).join('')
              }</select>
              <small>Arena dùng cùng luật
                đối kháng ở mọi độ khó.</small>
            </div>
            <div class="field" id="online-stage-field" hidden>
              <label for="match-stage">Cửa chiến
                dịch</label>
              <select id="match-stage" name="stage">
                <option value="1">Cửa 1 · Làm quen thao trường</option>
                <option value="2">Cửa 2 · Bảo vệ khu rừng</option>
                <option value="3">Cửa 3 · Thử thách cuối</option>
              </select>
              <small>Mọi thành viên cần thắng cửa trước ở cùng độ khó.</small>
            </div>
            <button type="button" id="ready-button" class="btn secondary wide" data-action="toggle-ready"
              data-code="${code}">${icon('check')}Sẵn sàng</button>
            <button id="start-button" class="btn primary wide" type="submit" disabled>${icon('play')}Bắt đầu
              trận đấu</button>
            <small id="start-hint" class="muted">Chờ tất cả thành viên sẵn sàng và có kết nối.</small>
          </form>
        </section>
      </div>
      `
  );
  const update = data => {
    if (!alive(version)) return;
    socket.lobby = data;
    const room = data.room;
    document.querySelector('#lobby-count').textContent =
      `${room.members.length}/${room.capacity} CHỈ HUY`;
    document.querySelector('#lobby-members').innerHTML = Array.from({
      length: room.capacity
    }, (_, index) => {
      const member = room.members.find(row => row.seat === index);
      return member ?
        `
          <div class="member">${avatar(member.displayName)}<div class="member-info">
              <strong>${esc(member.displayName)}${member.userId === room.hostUserId ? icon('crown') : ''}${
                member.userId === user.id ? '<span class="muted">· Bạn</span>' : ''
              }</strong>
              <small>${
                data.connectedUserIds.includes(member.userId) ? 'Đã kết nối chiến trường' : 'Chưa kết nối'
              }</small>
            </div>
            <span class="tag ${member.ready ? '' : 'neutral'}">${
              member.ready ? 'Sẵn sàng' : 'Chưa sẵn sàng'
            }</span>
          </div>
          ` :
        `
          <div class="member">
            <span class="seat">+</span>
            <div class="member-info">
              <strong class="muted">Chờ đồng đội</strong>
              <small>Chia sẻ mã ${code}
                để mời tham gia.</small>
            </div>
          </div>
          `;
    }).join('');
    const me = room.members.find(row => row.userId === user.id);
    if (!me) {
      socket.stop();
      navigate('/rooms', true);
      return;
    }
    const ready = document.querySelector('#ready-button');
    ready.innerHTML = icon(me.ready ? 'check-check' : 'check') + (me.ready ? 'Hủy sẵn sàng' :
      'Tôi đã sẵn sàng');
    ready.classList.toggle('primary', me.ready);
    ready.disabled = room.status !== 'WAITING';
    const host = room.hostUserId === user.id;
    const form = document.querySelector('[data-form=start-match]');
    form.querySelectorAll('select').forEach(select => select.disabled = !host || room.status !==
      'WAITING');
    const enough = room.members.length >= (form.elements.mode.value === 'CAMPAIGN' ? 1 : 2);
    document.querySelector('#start-button').disabled = !host || room.status !== 'WAITING' || !
      enough || !room.members.every(row => row.ready && data.connectedUserIds.includes(row
        .userId));
    document.querySelector('#start-hint').textContent = !host ?
      'Chủ phòng sẽ chọn chế độ và bắt đầu trận.' : !enough ?
      'Arena cần ít nhất 2 người. Chọn Campaign để chơi một mình.' :
      'Tất cả thành viên cần sẵn sàng và kết nối trước khi bắt đầu.';
    if (data.matchId && ['RUNNING', 'RECOVERING'].includes(room.status)) navigate(
      `/play/${data.matchId}`, true);
  };
  const lobby = event => update(event.detail),
    state = event => {
      if (!alive(version)) return;
      document.querySelector('#lobby-connection').innerHTML = event.detail === 'connected' ?
        `${icon('wifi')} Đã kết nối. Chia sẻ mã phòng để gọi đồng đội.` :
        `
          ${icon('wifi-off')} Kết nối đang gián đoạn. <button class="btn text small" data-action="reconnect">Kết nối
            lại</button>
          `;
    };
  socket.addEventListener('LOBBY', lobby);
  socket.addEventListener('connection', state);
  cleanup(() => {
    socket.removeEventListener('LOBBY', lobby);
    socket.removeEventListener('connection', state);
  });
  update(initial);
  state({
    detail: socket.socket?.readyState === WebSocket.OPEN ? 'connected' : 'connecting'
  });
  const refresh = () => {
    if (alive(version)) update(socket.lobby);
  };
  document.querySelector('#match-mode')?.addEventListener('change', event => {
    document.querySelector('#online-stage-field').hidden = event.target.value !== 'CAMPAIGN';
    refresh();
  });
}
/** Nạp hạng online theo chế độ/trang; kết quả solo không nhập vào bảng này. */
async function leaderboardPage(version) {
  setContent(version, loading());
  const campaign = rankMode === 'CAMPAIGN';
  const query = `page=${rankPage}&size=20${campaign ? '&difficulty=' + rankDifficulty : ''}`;
  const [rows, mine] = await Promise.all([api(
    `/api/rankings${campaign ? '/campaign' : ''}?${query}`, {
      auth: false
    }), user ? api(
    `/api/rankings${campaign ? '/campaign' : ''}/me${campaign ? '?difficulty=' + rankDifficulty : ''}`
  ).catch(() => null) : null]);
  if (!alive(version)) return;
  const rank = row => campaign ? row.place : row.rank;
  const points = row => campaign ? row.score : row.points;
  setContent(version, heading('Những chỉ huy dẫn đầu',
      'Thành tích từ các trận online đã hoàn tất. Mỗi chiến thắng đều có ý nghĩa.') +
    `
      <div class="filters">
        <div class="tabs">
          <button class="tab ${campaign ? '' : 'active'}" data-action="rank-mode" data-mode="ARENA">${
            icon('swords')
          } Arena
          </button>
          <button class="tab ${campaign ? 'active' : ''}" data-action="rank-mode" data-mode="CAMPAIGN">${
            icon('flag')
          }
            Campaign</button>
        </div>${
          campaign ? `
          <select class="inline-select" id="rank-difficulty" aria-label="Độ khó xếp hạng">${
            ['EASY', 'NORMAL', 'HARD'].map(value => option(value, difficultyName(value), rankDifficulty)).join(
              '')
          }
          </select>
          ` : '<span class="muted" style="font-size:11px">Thắng 3 điểm · Hòa 1 điểm · Thua 0 điểm</span>'
        }
      </div>${
        mine ? `
        <div class="notice info">${icon('trophy')}<span>Thành tích của bạn: <b class="lime">#${rank(mine)}</b> ·
            ${number(points(mine))} điểm${campaign ? ' · ' + mine.clearedStages + ' cửa đã thắng' : ''}</span>
        </div>
        ` : ''
      }${
        rankPage === 0 && rows.length ? `
        <div class="podium">${
          rows.slice(0, 3).map(row => `
          <div class="podium-card">
            <span class="eyebrow">HẠNG ${rank(row)}</span>
            <div>${avatar(row.displayName)}</div>
            <strong>${esc(row.displayName)}</strong>
            <p>${number(points(row))} điểm</p>
            <small>${campaign ? row.clearedStages + ' cửa chinh phục' : row.wins + ' trận thắng'}</small>
          </div>
          `).join('')
        }</div>
        ` : ''
      }${
        rows.length ? `
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Thứ hạng</th>
                <th>Chỉ huy</th>${
                  campaign ? '<th>Cửa đã thắng</th><th>Điểm cao nhất</th>' :
                    '<th>Trận</th><th>Thắng</th><th>Hòa</th><th>Thua</th><th>Tỷ lệ thắng</th><th>Điểm</th>'
                }
              </tr>
            </thead>
            <tbody>${
              rows.map(row => `
              <tr>
                <td>
                  <span class="place-medal">${rank(row)}</span>
                </td>
                <td>
                  <div class="player-cell">${avatar(row.displayName)}${esc(row.displayName)}${
                    row.userId === user?.id ? '<span class="tag">Bạn</span>' : ''
                  }</div>
                </td>${
                  campaign ? `
                  <td>${row.clearedStages}/3</td>
                  <td class="lime">${number(row.score)}</td>
                  ` : `
                  <td>${row.played}</td>
                  <td>${row.wins}</td>
                  <td>${row.draws}</td>
                  <td>${row.losses}</td>
                  <td>${row.winRate}%</td>
                  <td class="lime">
                    <b>${row.points}</b>
                  </td>
                  `
                }
              </tr>
              `).join('')
            }</tbody>
          </table>
        </div>
        ` : empty('Vị trí dẫn đầu còn trống',
          'Hoàn thành trận online để ghi thành tích đầu tiên lên bảng hạng.', button('Tìm phòng chơi',
            'swords', '/rooms'), 'trophy')
      }<div class="pagination">
        <button class="icon-button" data-action="rank-page" data-page="${rankPage - 1}" ${
          rankPage === 0 ? 'disabled' : ''
        }
          aria-label="Trang trước">${icon('chevron-left')}</button>
        <span>Trang ${rankPage + 1}</span>
        <button class="icon-button" data-action="rank-page" data-page="${rankPage + 1}" ${
          rows.length < 20 ? 'disabled' : ''
        }
          aria-label="Trang tiếp">${icon('chevron-right')}</button>
      </div>
      `
  );
}
/** Tách lịch sử online từ API với lịch sử solo trong vùng IndexedDB hiện tại. */
async function historyPageView(version) {
  setContent(version, loading());
  const offline = historyTab === 'offline';
  if (!offline && !user) await authReady;
  if (!offline && !user) {
    setContent(version, heading('Mỗi trận đấu, một dấu ấn',
        'Lịch sử solo trên máy luôn có sẵn, kể cả khi chưa đăng nhập.') +
      `
        <div class="filters">
          <div class="tabs">
            <button class="tab active" data-action="history-tab" data-tab="online">Trận
              online</button>
            <button class="tab" data-action="history-tab" data-tab="offline">Solo trên
              máy</button>
          </div>
        </div>
        ` +
      empty('Đăng nhập để xem trận online', 'Lịch sử online được lưu riêng theo tài khoản.',
        button('Đăng nhập', 'log-in', '/login'), 'shield'));
    return;
  }
  const rows = offline ? await storage.localHistory(scope()) : await api(
    `/api/matches?page=${historyPage}&size=15`);
  if (!alive(version)) return;
  setContent(version, heading('Mỗi trận đấu, một dấu ấn',
      'Xem lại hành trình và những kết quả bạn đã đạt được.') +
    `
      <div class="filters">
        <div class="tabs">
          <button class="tab ${offline ? '' : 'active'}" data-action="history-tab" data-tab="online">Trận online
          </button>
          <button class="tab ${offline ? 'active' : ''}" data-action="history-tab" data-tab="offline">Solo trên máy
          </button>
        </div>
        <span class="muted" style="font-size:10px">${
          offline ? 'Lưu tối đa 20 kết quả · Không cộng bảng hạng' : 'Chỉ hiển thị trận bạn đã tham gia'
        }</span>
      </div>${
        rows.length ? rows.map(row => `
        <article class="history-item">
          <div class="history-icon">${icon(row.mode === 'CAMPAIGN' ? 'flag' : 'swords')}</div>
          <div>
            <h3>${
              offline ? (row.mode === 'CAMPAIGN' ? 'Campaign · Cửa ' + row.stage : 'Arena · Đấu bot') : esc(row
                .roomName)
            }</h3>
            <p>${row.mode === 'CAMPAIGN' ? 'Campaign · Cửa ' + row.stage : 'Arena'} · ${
              difficultyName(row.difficulty)
            } · ${date(row.endedAt || row.startedAt)}${
              offline ? ' · ' + row.kills + ' mục tiêu · ' + row.score + ' điểm' : ''
            }</p>
          </div>
          <span class="tag ${
            offline ? (row.outcome === 'WIN' ? '' : row.outcome === 'LOSS' ? 'red' : 'orange') : row.status ===
              'FINISHED' ? 'neutral' : 'orange'
          }">${esc(offline ? outcomeName(row.outcome) : statusName(row.status))}</span>${
            offline ? '' : `
            <button class="icon-button" data-action="match-detail" data-id="${row.id}"
              aria-label="Xem chi tiết trận">${icon('arrow-up-right')}</button>
            `
          }
        </article>
        `).join('') : empty('Chưa có trận nào ở đây',
          'Một trận đấu mới có thể là khởi đầu cho chuỗi chiến thắng của bạn.', button(offline ?
            'Chơi solo' : 'Vào phòng chơi', offline ? 'gamepad-2' : 'swords', offline ? '/offline' :
            '/rooms'), 'history')
      }${
        offline ? '' : `
        <div class="pagination">
          <button class="icon-button" data-action="history-page" data-page="${historyPage - 1}" ${
            historyPage === 0 ? 'disabled' : ''
          }
            aria-label="Trang trước">${icon('chevron-left')}</button>
          <span>Trang
            ${historyPage + 1}</span>
          <button class="icon-button" data-action="history-page" data-page="${historyPage + 1}" ${
            rows.length < 15 ? 'disabled' : ''
          }
            aria-label="Trang tiếp">${icon('chevron-right')}</button>
        </div>
        `
      }
      `
  );
}
/** Lấy hồ sơ và thống kê từ API riêng; form cập nhật dùng submitForm chung. */
async function profilePage(version) {
  setContent(version, loading());
  const rank = await api('/api/rankings/me');
  if (!alive(version)) return;
  setContent(version, heading('Hồ sơ chỉ huy', 'Tên tuổi của bạn trên mọi chiến trường.') +
    `
      <div class="two-column">
        <section class="panel profile-card">${avatar(user.displayName)}<h2>${esc(user.displayName)}</h2>
          <p>@${esc(user.username)}</p>
          <span class="tag">${icon('shield')}CHỈ HUY UTC</span>
          <div class="divider"></div>
          <div class="stats" style="grid-template-columns:repeat(2,1fr);margin-bottom:0">${
            [
              [rank?.played || 0, 'Trận Arena'],
              [rank?.wins || 0, 'Chiến thắng'],
              [rank ? rank.winRate + '%' : '—', 'Tỷ lệ thắng'],
              [rank ? '#' + rank.rank : '—', 'Xếp hạng']
            ].map(([value, text]) => `
            <div class="stat">
              <div>
                <strong>${value}</strong>
                <small>${text}</small>
              </div>
            </div>
            `).join('')
          }
          </div>
        </section>
        <div>
          <section class="panel">
            <div class="panel-title">
              <h2>Thông tin cá nhân</h2>${icon('user-round')}
            </div>
            <form class="form-stack" data-form="profile">
              <div class="error-box" role="alert"></div>${
                field('displayName', 'Tên hiển thị', 'text',
                  `required minlength="2" maxlength="30" value="${esc(user.displayName)}" autocomplete="nickname" aria-describedby="displayName-error"`,
                  'Tên sẽ xuất hiện trong phòng chơi và trên bảng xếp hạng.')
              }<div class="field">
                <label for="profile-username">Tên đăng nhập</label>
                <input id="profile-username" value="${esc(user.username)}" readonly>
                <small>Tên đăng nhập được giữ cố định.</small>
              </div>
              <button class="btn primary" type="submit">${icon('check')}Lưu thay đổi</button>
            </form>
          </section>
          <section class="panel">
            <h2>Quản lý phiên đăng nhập</h2>
            <p class="muted" style="font-size:11px;margin:12px 0 18px">Đăng xuất mọi phiên API để ngắt các
              kết nối chơi online của tài khoản. Phiên web trên thiết bị khác vẫn được quản lý riêng.</p>
            <button class="btn danger small" data-action="logout-all">${icon('log-out')}Đăng xuất mọi phiên
              API</button>
          </section>
        </div>
      </div>
      `
  );
}
/** Nạp catalog và tiến độ local để chọn độ khó/cửa; chưa tạo trận ở bước xem trang. */
async function offlinePage(version) {
  setContent(version, loading('Đang chuẩn bị thao trường…'));
  const [gameCatalog, saves, progress] = await Promise.all([catalog(), storage.listSaves(scope()),
    storage.getMeta(`${scope()}:progress:NORMAL`)
  ]);
  if (!alive(version)) return;
  setContent(version, heading('Một mình. Vẫn chiến.',
      'Tập luyện cùng bot hoặc chinh phục chiến dịch. Chơi ngay cả khi không có mạng.',
      `
        <span id="offline-status" class="tag ${offlineReady ? '' : 'orange'}" role="status">${
          icon(offlineReady ? 'circle-check' : 'download')
        }${offlineReady ? 'ĐÃ TẢI ĐỦ OFFLINE' : 'ĐANG CHUẨN BỊ OFFLINE'}</span>
        `
    ) +
    `
      <div class="offline-layout">
        <section class="panel">
          <div class="panel-title">
            <h2>Chọn nhiệm vụ</h2>${icon('crosshair')}
          </div>
          <form class="form-stack" data-form="solo-start">
            <div class="error-box" role="alert"></div>
            <div class="field">
              <span class="field-label">Chế độ</span>
              <div class="segmented">${
                [
                  ['CAMPAIGN', 'Chiến dịch'],
                  ['ARENA', 'Đấu bot Arena']
                ].map(([value, text]) => `
                <label>
                  <input type="radio" name="mode" value="${value}" ${value === 'CAMPAIGN' ? 'checked' : ''}>
                  <span>${text}</span>
                </label>
                `).join('')
              }</div>
            </div>
            <div class="field">
              <label for="solo-difficulty">Độ khó</label>
              <select name="difficulty" id="solo-difficulty">${
                ['EASY', 'NORMAL', 'HARD'].map(value => option(value, difficultyName(value), 'NORMAL')).join('')
              }</select>
            </div>
            <div class="field" id="solo-stages">
              <span class="field-label">Cửa chiến dịch</span>
              <div class="stage-list" id="solo-stage-list">${stageOptions(gameCatalog, progress || [])}</div>
            </div>
            <div class="field" id="solo-bots" hidden>
              <label for="bot-count">Số bot đối thủ</label>
              <select name="bots" id="bot-count">
                <option value="1">1 bot · Đấu tay đôi</option>
                <option value="2">2 bot · Tăng thử thách</option>
                <option value="3">3 bot · Toàn đội hình</option>
              </select>
            </div>
            <div class="field">
              <label for="solo-slot">Vị trí lưu tiến trình</label>
              <select name="slot" id="solo-slot">${
                [1, 2, 3].map(slot => `
                <option value="${slot}">Bản lưu ${slot} · ${
                  saves.some(save => save.slot === slot) ? 'Đã có dữ liệu' : 'Còn trống'
                }</option>
                `).join('')
              }</select>
            </div>
            <button class="btn primary wide" type="submit">${icon('play')}Xuất trận</button>
          </form>
        </section>
        <div>
          <section class="panel">
            <div class="panel-title">
              <h2>Chiến dịch đang chờ</h2>${icon('flag')}
            </div>
            <img src="/icons/battle-scene.svg" alt="Bản đồ chiến dịch"
              style="width:100%;height:230px;object-fit:contain">
            <p class="muted" style="font-size:11px">Vượt từng đợt địch để mở cửa tiếp theo. Chim trinh sát
              và bướm bay qua tường; xe tăng và bọ giáp bị địa hình cản lại.</p>
            <div class="divider"></div>
            <div class="actions">
              <span class="tag neutral">${icon('save')}Tự lưu mỗi 2 giây</span>
              <span class="tag neutral">${icon('wifi-off')}Không cần tài khoản</span>
            </div>
          </section>${
            saves.length ? `
            <section class="panel">
              <div class="panel-title">
                <h2>Tiếp tục trận dở</h2>${icon('history')}
              </div>${
                saves.map(save => `
                <div class="member">
                  <div class="member-info">
                    <strong>Bản lưu ${save.slot} · ${
                      save.state.mode === 'CAMPAIGN' ? 'Cửa ' + save.state.stage.number : 'Arena'
                    }</strong>
                    <small>${date(save.updatedAt)} ·
                      ${difficultyName(save.state.difficulty)}</small>
                  </div>
                  <a class="btn secondary small" href="/offline?slot=${save.slot}&play=1" data-link>Chơi tiếp
                    ${icon('arrow-right')}</a>
                </div>
                `).join('')
              }
            </section>
            ` : ''
          }
        </div>
      </div>
      `
  );
}

/** Dựng lựa chọn cửa theo tiến độ ở độ khó hiện tại, khóa cửa chưa mở. */
function stageOptions(gameCatalog, progress) {
  return gameCatalog.stages.map((stage, index) => {
    const unlocked = stage.number === 1 || progress.includes(stage.number - 1);
    return `
      <label class="stage-option">
        <input name="stage" type="radio" value="${stage.number}" ${index === 0 ? 'checked' : ''} ${
          unlocked ? '' : 'disabled'
        }>
        <span class="stage-body">
          <span class="stage-number">0${stage.number}</span>
          <span>
            <strong>${esc(stage.name)}</strong>
            <small>${stage.waves.length}
              đợt địch · ${Math.round(stage.durationTicks * .05 / 60)} phút${
                progress.includes(stage.number) ? ' · Đã chinh phục' : ''
              }</small>
          </span>${icon(unlocked ? 'chevron-right' : 'lock')}
        </span>
      </label>
      `;
  }).join('');
}
/** Đối chiếu ba slot local/cloud và revision; chưa ghi đè bản nào khi chỉ xem. */
async function savesPage(version) {
  setContent(version, loading());
  const local = await storage.listSaves(scope());
  let cloud = [],
    cloudError = '';
  if (navigator.onLine && user) try {
    cloud = await api('/api/offline-saves');
  } catch (error) {
    cloudError = error.message;
  }
  if (!alive(version)) return;
  setContent(version, heading('Tiến trình luôn ở đây',
      'Ba vị trí lưu cho hành trình solo. Chọn chơi tiếp trên máy hoặc đồng bộ đám mây.') + (!
      user ?
      `
        <div class="notice info">${icon('log-in')}<span>Bản lưu trên máy vẫn dùng được. <a class="lime"
              href="/login" data-link>Đăng nhập</a> để đồng bộ đám mây.${
                !navigator.onLine && scope() !== 'guest' ?
                  ' Đang mở vùng lưu của tài khoản gần nhất trên thiết bị.' : ''
              }</span>
        </div>
        ` :
      '') + (cloudError ?
      `
        <div class="notice">${icon('cloud-download')}<span>Chưa đọc được bản lưu đám mây: ${
          esc(cloudError)
        }</span>
        </div>
        ` :
      '') + (!navigator.onLine ?
      `
        <div class="notice">${icon('wifi-off')}Chỉ hiển thị bản lưu trên thiết bị. Kết nối mạng để đồng bộ.</div>
        ` :
      '') +
    `
      <div class="saves-grid">${
        [1, 2, 3].map(slot => {
          const device = local.find(row => row.slot === slot),
            remote = cloud.find(row => row.slot === slot);
          return `
        <section class="save-card">
          <h3>Bản lưu 0${slot}<span class="tag ${device ? '' : 'neutral'}">${
            device ? 'Trên máy' : 'Máy trống'
          }</span>
          </h3>
          <div class="save-art">${icon(device ? 'gamepad-2' : 'save')}</div>${
            device ? `
            <p>
              <b>${
                device.state.mode === 'CAMPAIGN' ? 'Campaign · Cửa ' + device.state.stage.number : 'Arena · Đấu bot'
              }</b>
            </p>
            <p>${difficultyName(device.state.difficulty)} · ${date(device.updatedAt)}</p>
            <p>${device.state.players.solo?.kills || 0} mục tiêu · ${device.state.players.solo?.score || 0} điểm</p>
            ` : '<p>Chưa có trận được lưu trên thiết bị này.</p><p>Vào solo để bắt đầu hành trình mới.</p>'
          }<div class="divider"></div>
          <p>${icon('cloud-download')} ${
            remote ? 'Đám mây: ' + date(remote.updatedAt) + ' · v' + remote.revision : 'Chưa có bản đám mây'
          }</p>
          <div class="actions">${
            device ? button('Chơi tiếp', 'play', `/offline?slot=${slot}&play=1`, 'primary small') : button(
              'Chơi mới', 'plus', '/offline', 'secondary small')
          }${
            device ? `
            <button class="icon-button" data-action="delete-local" data-slot="${slot}"
              aria-label="Xóa bản lưu trên máy ${slot}">${icon('trash-2')}</button>
            ` : ''
          }</div>
          <div class="actions">
            <button class="btn secondary small" data-action="upload-save" data-slot="${slot}"
              ${!user || !device || !navigator.onLine || cloudError ? 'disabled' : ''}>${icon('upload')}Lưu lên mây</button>
            <button class="btn secondary small" data-action="download-save" data-slot="${slot}"
              ${!remote || !navigator.onLine ? 'disabled' : ''}>${icon('download')}Tải về máy</button>
          </div>${
            remote ? `
            <button class="btn text small red" data-action="delete-cloud" data-slot="${slot}"
              data-revision="${remote.revision}">Xóa bản đám mây</button>
            ` : ''
          }
        </section>
        `;
        }).join('')
      }</div>
      <div class="notice info" style="margin-top:24px">${icon('shield')}Bản lưu solo không ảnh hưởng thành tích
        online. Khi có xung đột, bạn chọn bản muốn giữ trước khi ghi đè.</div>
      `
  );
}

/** Dựng hướng dẫn và bộ chọn theme; đổi nền được lưu riêng với dữ liệu trò chơi. */
function helpPage(version) {
  setContent(version, heading('Làm chủ xe tăng của bạn',
      'Nắm điều khiển, hiểu chiến trường và sẵn sàng cho trận đầu tiên.') +
    `
      <section class="panel theme-panel">
        <h2>Giao diện của bạn</h2>
        <p class="muted">Chọn nền sáng, nền tối hoặc tự đổi theo thiết bị. Lựa chọn được nhớ trên trình
          duyệt này.</p>
        <div class="actions" aria-label="Chọn nền giao diện">${
          [
            ['system', 'Theo thiết bị'],
            ['light', 'Nền sáng'],
            ['dark', 'Nền tối']
          ].map(([value, title]) => `
          <button class="btn secondary" data-action="theme" data-theme="${value}"
            aria-pressed="${document.documentElement.dataset.themePreference === value}">${title}</button>
          `).join('')
        }</div>
      </section>
      <div class="help-grid">
        <section class="panel">
          <h2>Điều khiển</h2>
          <div class="key-row">
            <kbd>W</kbd>
            <kbd>A</kbd>
            <kbd>S</kbd>
            <kbd>D</kbd>
            <span>hoặc phím mũi tên để
              di chuyển</span>
          </div>
          <div class="key-row">
            <kbd>Space</kbd>
            <span>hoặc J để giữ bắn</span>
          </div>
          <div class="key-row">
            <kbd>P</kbd>
            <span>hoặc Escape để tạm dừng solo</span>
          </div>
          <div class="divider"></div>
          <p class="muted" style="font-size:12px">Trên Android và iPhone/iPad, giữ một ngón ở cụm hướng và
            một ngón ở nút bắn để vừa chạy vừa bắn. Có thể trượt ngón trong cụm hướng để đổi hướng. Xoay
            ngang nếu muốn thấy bản đồ rộng hơn. Trận online tiếp tục chạy khi bạn mở trang khác.</p>
        </section>
        <section class="panel">
          <h2>Vật phẩm hỗ trợ</h2>${
            [
              ['heart', 'Hộp cứu thương', 'Hồi máu, tối đa bằng HP ban đầu.'],
              ['shield', 'Khiên', 'Giảm sát thương nhận vào.'],
              ['zap', 'Tăng tốc & bắn nhanh', 'Di chuyển nhanh hơn hoặc rút ngắn thời gian bắn.'],
              ['crosshair', 'Tăng sát thương', 'Tăng sức mạnh cho những viên đạn mới.']
            ].map(([symbol, title, text]) => `
            <div class="mini-guide">${icon(symbol)}<div>
                <h3>${title}</h3>
                <p>${text}</p>
              </div>
            </div>
            `).join('')
          }
        </section>
      </div>
      <section class="panel" style="margin-top:20px">
        <h2>Trước khi xuất trận</h2>${
          [
            ['Làm sao chơi cùng bạn bè?',
              'Đăng nhập, tạo phòng 2–4 ghế và gửi mã 8 ký tự cho đồng đội. Tất cả thành viên nhấn sẵn sàng; chủ phòng chọn chế độ rồi bắt đầu. Arena cần từ 2 người, Campaign có thể bắt đầu với 1 người.'
            ],
            ['Chơi offline cần chuẩn bị gì?',
              'Mở ứng dụng khi có mạng và đợi dòng “Đã sẵn sàng offline”. Sau đó có thể mở lại /offline khi mất Internet. Bản lưu tự động nằm trong trình duyệt; xóa dữ liệu website cũng xóa bản lưu trên máy.'
            ],
            ['Mất kết nối hoặc máy chủ khởi động lại?',
              'Mở lại phòng hiện tại. Trận dở được phục hồi từ checkpoint; người chơi còn sống phải xác nhận chơi tiếp trước deadline hiển thị. Hủy phục hồi không được cộng điểm.'
            ],
            ['Cách tính bảng xếp hạng?',
              'Arena tính 3 điểm mỗi trận thắng, 1 điểm hòa và 0 điểm thua. Campaign xếp theo số cửa thắng rồi tổng điểm cao nhất mỗi cửa theo từng độ khó. Chỉ trận online FINISHED được tính.'
            ],
            ['Bản lưu trên máy và đám mây khác nhau thế nào?',
              'Bản trên máy dùng được khi offline. Đám mây giúp chuyển tiến trình sang thiết bị khác sau đăng nhập. Đồng bộ được thực hiện khi bạn chọn tải lên hoặc tải về; hệ thống hỏi trước khi ghi đè bản xung đột.'
            ]
          ].map(([title, text]) => `
          <details>
            <summary>${title}</summary>
            <p>${text}</p>
          </details>
          `).join('')
        }<div class="actions" style="margin-top:20px">
          ${button('Thử sức solo', 'play', '/offline')}<button class="btn secondary" data-action="prepare-offline">${
            icon('download')
          }Kiểm tra tài
            nguyên offline</button>
        </div>
      </section>
      `
  );
}

/** Giữ khóa cho một slot đến lúc dispose để hai tab không tự ghi đè cùng trận solo. */
async function lockSolo(owner, slot) {
  if (!navigator.locks) return () => {};
  return new Promise((resolve, reject) => {
    navigator.locks.request(`utc-tank-solo:${owner}:${slot}`, {
      ifAvailable: true
    }, lock => {
      if (!lock) {
        reject(new Error(
          'Bản lưu này đang được chơi ở tab khác. Đóng trận ở tab đó hoặc chọn vị trí lưu khác.'
        ));
        return;
      }
      return new Promise(release => resolve(release));
    }).catch(reject);
  });
}
/** Giữ khóa slot, phục hồi snapshot rồi chạy tick 50 ms; gom ghi vào một hàng đợi. */
async function soloGame(version, slot) {
  if (![1, 2, 3].includes(slot)) throw new Error('Vị trí lưu phải từ 1 đến 3.');
  setContent(version, loading('Đang nạp trận solo…'));
  const owner = scope(),
    release = await lockSolo(owner, slot);
  if (!alive(version)) {
    release();
    return;
  }
  let saved, state;
  try {
    saved = await storage.getSave(owner, slot);
    if (!saved) throw new Error('Chưa có trận ở vị trí này. Mở Solo để bắt đầu trận mới.');
    state = restoreGame(saved.state);
  } catch (error) {
    release();
    throw error;
  }
  if (!alive(version)) {
    release();
    return;
  }
  setContent(version, gameMarkup(state.mode === 'CAMPAIGN' ?
    `Chiến dịch · Cửa ${state.stage.number}` : 'Arena · Đấu bot', true));
  const renderer = new GameRenderer(document.querySelector('#battle-canvas'), 'solo');
  let stopped = false,
    finished = false,
    saveQueue = Promise.resolve(),
    saveErrorShown = false,
    last = performance.now(),
    accumulator = 0,
    frameId;
  /**
   * Chụp state ngay lúc yêu cầu lưu, sau đó ghi tuần tự. Bản autosave cũ không được
   * ghi đè bản mới nếu IndexedDB chậm; lỗi trước không chặn lần lưu tiếp theo.
   */
  const persist = () => {
    if (finished) return saveQueue;
    const snapshot = structuredClone(state);
    saveQueue = saveQueue.catch(() => {}).then(() => storage.saveGame(owner, slot, snapshot))
      .then(() => {
        if (alive(version)) {
          document.querySelector('#save-status').textContent =
            `Đã lưu · ${date(new Date().toISOString())}`;
        }
        saveErrorShown = false;
      }).catch(error => {
        if (!saveErrorShown) {
          toast(error.message, true);
          saveErrorShown = true;
        }
        if (alive(version)) document.querySelector('#save-status').textContent =
          'Chưa lưu được trên máy';
        throw error;
      });
    return saveQueue;
  };
  solo = {
    state,
    slot,
    owner,
    paused: false,
    persist,
    toggle: () => pause(!solo.paused)
  };
  const overlay = document.querySelector('#game-overlay');

  /** Dừng tick, xóa đầu vào và lưu snapshot; tiếp tục đặt lại mốc thời gian. */
  function pause(value) {
    if (finished || stopped) return;
    solo.paused = value;
    controls.reset();
    last = performance.now();
    accumulator = 0;
    overlay.classList.toggle('hidden', !value);
    if (value) overlay.innerHTML =
      `
        <div>${icon('pause', 'large')}<h2>Thao trường tạm nghỉ</h2>
          <p>Đồng hồ đã dừng. Tiến trình được lưu trên thiết bị.</p>
          <div class="actions">
            <button class="btn primary" data-action="pause-solo">${icon('play')}Tiếp
              tục</button>
            <a class="btn secondary" href="/offline" data-link>Về thao trường</a>
          </div>
        </div>
        `;
    if (value) persist().catch(() => {});
  }
  const controls = new GameControls(() => pause(!solo.paused));
  controls.onHidden = () => pause(true);
  renderer.setState(state);
  updateHud(state, 'solo');
  /**
   * Cờ finished chặn kết toán lặp. Chờ autosave trước khi ghi kết quả + mở cửa + xóa
   * save dở trong một transaction IndexedDB. Nếu lỗi, giữ snapshot để có thể thử lại;
   * kết quả này chỉ thuộc thiết bị, tuyệt đối không gọi API cộng bảng hạng online.
   */
  async function finish() {
    if (finished || stopped) return;
    finished = true;
    controls.reset();
    const won = state.mode === 'CAMPAIGN' ? state.endReason === 'CAMPAIGN_CLEARED' : state
      .players.solo.hp > 0 && state.endReason === 'LAST_SURVIVOR';
    const draw = state.mode === 'ARENA' && state.players.solo.hp > 0 && state.endReason ===
      'TIME_LIMIT';
    let savedResult = true;
    saveQueue = saveQueue.catch(() => {}).then(() => storage.finishGame(owner, slot, state))
      .catch(async error => {
        savedResult = false;
        toast(error.message, true);
        await storage.saveGame(owner, slot, state).catch(() => {});
      });
    await saveQueue;
    if (!alive(version)) return;
    document.querySelector('#save-status').textContent = savedResult ?
      'Đã lưu kết quả trên máy' : 'Kết quả chưa lưu; bản trận vẫn được giữ';
    overlay.classList.remove('hidden');
    overlay.innerHTML =
      `
        <div>${icon(won ? 'trophy' : draw ? 'shield' : 'flag', 'large')}<h2>${
          won ? 'Chiến thắng thuộc về bạn!' : draw ? 'Một trận hòa đáng nhớ.' :
            'Rút kinh nghiệm. Tái xuất trận.'
        }</h2>
          <p>${
            won && state.mode === 'CAMPAIGN' ? 'Bạn đã hoàn thành chiến dịch. Cửa tiếp theo đã sẵn sàng.' :
              'Mỗi trận đấu là một cơ hội để trở thành chỉ huy tốt hơn.'
          }</p>
          <div class="result-score">${number(state.players.solo.score)}</div>
          <p>${state.players.solo.kills} mục tiêu · ${difficultyName(state.difficulty)} · Solo</p>
          <div class="actions">${button('Trận tiếp theo', 'play', '/offline')}${
            button('Xem lịch sử', 'history', '/history?tab=offline', 'secondary')
          }</div>
        </div>
        `;
  }
  const loop = time => {
    if (stopped) return;
    const elapsed = Math.min(250, Math.max(0, time - last));
    last = time;
    if (!solo.paused && !finished) {
      accumulator += elapsed;
      let advanced = false;
      // Bắt kịp tối đa 250 ms khi frame chậm; engine vẫn chạy đúng từng tick 50 ms.
      // Chỉ xuất snapshot/HUD một lần sau vòng bắt kịp, tránh clone/DOM lặp trong cùng frame.
      while (accumulator >= 50 && state.phase === 'RUNNING') {
        step(state, controls.read());
        accumulator -= 50;
        advanced = true;
        if (state.tick % 40 === 0) persist().catch(() => {});
      }
      if (advanced) {
        renderer.setState(state);
        updateHud(state, 'solo');
      }
      if (state.phase !== 'RUNNING') finish();
    }
    frameId = requestAnimationFrame(loop);
  };
  frameId = requestAnimationFrame(loop);
  cleanup(async () => {
    stopped = true;
    cancelAnimationFrame(frameId);
    controls.dispose();
    renderer.dispose();
    try {
      if (!finished) await persist();
      else await saveQueue.catch(() => {});
    } finally {
      release();
    }
  });
  persist().catch(() => {});
  if (state.phase !== 'RUNNING') finish();
}
/** Canvas đọc state server; client chỉ gửi ý định, xử lý sync và thời hạn phục hồi. */
async function onlineGame(version, id) {
  setContent(version, loading('Đang đồng bộ chiến trường…'));
  const [metadata, initial, current] = await Promise.all([api(`/api/matches/${id}`), api(
    `/api/matches/${id}/state`), api('/api/rooms/current')]);
  if (!alive(version)) return;
  setContent(version, gameMarkup(metadata.mode === 'CAMPAIGN' ?
    `${metadata.roomName} · Cửa ${metadata.stage}` : metadata.roomName));
  const renderer = new GameRenderer(document.querySelector('#battle-canvas'), user.id);
  const controls = new GameControls();
  const socket = current?.code === metadata.roomCode && !['FINISHED', 'ABORTED'].includes(metadata
    .status) ? ensureConnection(metadata.roomCode) : null;
  let activeFrame = initial,
    resultShown = false,
    lastConnection = 'connected';
  const overlay = document.querySelector('#game-overlay');
  const showResult = async () => {
    if (resultShown || !alive(version)) return;
    resultShown = true;
    try {
      const rows = await api(`/api/matches/${id}/results`);
      if (!alive(version)) return;
      const me = rows.find(row => row.userId === user.id);
      overlay.classList.remove('hidden');
      overlay.innerHTML =
        `
          <div>${
            icon(activeFrame.mode === 'ABORTED' ? 'circle-x' : me?.outcome === 'WIN' ? 'trophy' : 'flag',
              'large')
          }<h2>${activeFrame.mode === 'ABORTED' ? 'Trận đấu đã hủy' : outcomeName(me?.outcome)}</h2>
            <p>${
              activeFrame.mode === 'ABORTED' ? 'Trận hủy không được cộng vào bảng xếp hạng.' :
                'Kết quả chính thức đã được máy chủ lưu.'
            }</p>
            <div class="result-score">${number(me?.score)}</div>
            <p>${me?.kills || 0} mục tiêu · ${difficultyName(metadata.difficulty)}</p>
            <div class="actions">${
              current ? button('Về phòng chờ', 'users', `/rooms/${current.code}`) : button('Tìm phòng mới',
                'users', '/rooms')
            }${button('Lịch sử trận', 'history', '/history', 'secondary')}</div>
          </div>
          `;
      document.querySelector('#save-status').textContent = 'Kết quả đã xác nhận';
    } catch (error) {
      resultShown = false;
      toast(error.message, true);
      overlay.classList.remove('hidden');
      overlay.innerHTML =
        `
          <div>
            <h2>Đang tải kết quả</h2>
            <p>${esc(error.message)}</p>
            <button class="btn primary" data-action="retry">Thử tải lại</button>
          </div>
          `;
    }
  };
  /**
   * Server là nguồn duy nhất của HP/vị trí/kết quả online. Frame thuộc trận cũ
   * bị bỏ qua; RECOVERING yêu cầu xác nhận, SAVING/ENDING khóa điều khiển.
   */
  const showFrame = frame => {
    if (!alive(version) || frame.state.matchId !== id) return;
    activeFrame = frame;
    renderer.setState(frame.state);
    updateHud(frame.state, user.id);
    document.querySelector('#save-status').textContent = frame.mode === 'RUNNING' ?
      'Đang đồng bộ với máy chủ' : statusName(frame.mode);
    if (['FINISHED', 'ABORTED'].includes(frame.mode)) {
      showResult();
      return;
    }
    if (frame.mode === 'RECOVERING') {
      controls.reset();
      overlay.classList.remove('hidden');
      const ready = frame.resumeReady.includes(String(user.id)),
        canResume = frame.state.players[String(user.id)]?.hp > 0;
      overlay.innerHTML =
        `
          <div>${icon('history', 'large')}<h2>Tiếp tục trận đang dở</h2>
            <p>Đồng hồ đang dừng. Tất cả người chơi còn sống cần xác nhận trước <b>${
              date(frame.recoveryDeadline)
            }</b>.</p>
            <p>${frame.resumeReady.length} người đã sẵn sàng phục hồi</p>
            <div class="actions">
              <button class="btn primary" data-action="resume-match" data-id="${id}" ${
                ready || !canResume ? 'disabled' : ''
              }>
                ${ready ? 'Đã xác nhận · Chờ đồng đội' : 'Tôi đã sẵn sàng'}</button>
              <button class="btn danger" data-action="cancel-recovery" data-id="${id}">Hủy trận
              </button>
            </div>
          </div>
          `;
    } else if (['SAVING', 'ENDING'].includes(frame.mode)) {
      controls.reset();
      overlay.classList.remove('hidden');
      overlay.innerHTML =
        `
          <div>
            <span class="loader"></span>
            <h2 style="margin-top:15px">${
              frame.mode === 'SAVING' ? 'Đang lưu tiến trình' : 'Đang xác nhận kết quả'
            }</h2>
            <p>Vui lòng chờ máy chủ hoàn tất. Trận đấu được tạm dừng trong lúc lưu.</p>
          </div>
          `;
    } else if (lastConnection === 'connected') overlay.classList.add('hidden');
  };
  const connectionStatus = status => {
    if (!alive(version)) return;
    lastConnection = status;
    document.querySelector('#game-connection').textContent = status === 'connected' ?
      'ĐÃ KẾT NỐI' : status === 'connecting' ? 'ĐANG KẾT NỐI' : 'MẤT KẾT NỐI';
    if (status === 'connected') showFrame(activeFrame);
    else if (activeFrame.mode === 'RUNNING') {
      controls.reset();
      overlay.classList.remove('hidden');
      overlay.innerHTML =
        `
          <div>${icon('wifi-off', 'large')}<h2>${
            status === 'closed' ? 'Kết nối đã đóng' : 'Đang kết nối lại…'
          }</h2>
            <p>${
              status === 'closed' ?
                'Bạn có thể đã mở trận ở tab khác hoặc phiên kết nối đã hết hạn. Kết nối lại để điều khiển tại đây.' :
                'Giữ trang này mở. Trò chơi sẽ tải trạng thái thật từ máy chủ khi kết nối trở lại.'
            }</p>
            <button class="btn primary" data-action="reconnect">${icon('refresh-cw')}Kết nối
              lại</button>
          </div>
          `;
    }
  };
  const game = event => showFrame(event.detail),
    finished = event => {
      if (event.detail.matchId === id) socket.sync(id).catch(error => toast(error.message, true));
    },
    status = event => connectionStatus(event.detail);
  socket?.addEventListener('GAME', game);
  socket?.addEventListener('FINISHED', finished);
  socket?.addEventListener('connection', status);
  const inputs = setInterval(() => {
    if (socket && activeFrame.mode === 'RUNNING') socket.input(id, controls.read());
  }, 50);
  cleanup(() => {
    clearInterval(inputs);
    controls.dispose();
    socket?.input(id, {
      direction: 'NONE',
      fire: false
    });
    renderer.dispose();
    socket?.removeEventListener('GAME', game);
    socket?.removeEventListener('FINISHED', finished);
    socket?.removeEventListener('connection', status);
  });
  showFrame(initial);
  if (socket) connectionStatus(socket.socket?.readyState === WebSocket.OPEN ? 'connected' :
    'connecting');
}
/** Mở hộp thoại chi tiết trận; API tiếp tục kiểm quyền sở hữu ở server. */
async function matchDetail(id) {
  openModal(`${modalHead('Chi tiết trận đấu')}${loading()}`);
  const [metadata, results] = await Promise.all([api(`/api/matches/${id}`), api(
    `/api/matches/${id}/results`)]);
  openModal(
    `
      ${modalHead(metadata.roomName)}<p>${
        metadata.mode === 'CAMPAIGN' ? 'Campaign · Cửa ' + metadata.stage : 'Arena'
      } · ${difficultyName(metadata.difficulty)}
        <br>${date(metadata.startedAt)} → ${date(metadata.endedAt)} · ${statusName(metadata.status)}
      </p>
      ${
        results.map(row => `
        <div class="result-row">
          <span>${esc(row.displayName)}${row.userId === user.id ? ' · Bạn' : ''}
            <br>
            <small class="muted">${row.kills} mục tiêu
              · ${row.score} điểm</small>
          </span>
          <span class="${row.outcome === 'WIN' ? 'lime' : 'muted'}">${outcomeName(row.outcome)}</span>
        </div>
        `).join('')
      }<div class="actions">${
        ['RUNNING', 'RECOVERING'].includes(metadata.status) ? button('Mở trận đấu', 'play', `/play/${id}`) :
          ''
      }<button class="btn secondary"
          data-action="close-modal">Đóng</button>
      </div>
      `
  );
}
/** Đẩy snapshot kèm revision; xung đột yêu cầu chọn bản giữ lại trước khi ghi đè. */
async function uploadSave(slot, force = false) {
  if (!user) {
    toast('Đăng nhập để đồng bộ đám mây.', true);
    return;
  }
  const owner = scope(),
    local = await storage.getSave(owner, slot);
  if (!local) throw new Error('Vị trí này chưa có bản lưu trên máy.');
  const metadata = await api('/api/offline-saves');
  const remoteMetadata = metadata.find(row => row.slot === slot);
  let revision = remoteMetadata ? (local.revision || 0) : 0;
  if (force) revision = remoteMetadata?.revision || 0;
  try {
    const remote = await api(`/api/offline-saves/${slot}`, {
      method: 'PUT',
      body: {
        expectedRevision: revision,
        state: restoreGame(local.state)
      }
    });
    await storage.saveGame(owner, slot, local.state, remote.revision);
    toast('Đã lưu tiến trình lên đám mây.');
    await renderRoute();
  } catch (error) {
    if (error.code !== 'SAVE_002') throw error;
    const latest = await api('/api/offline-saves');
    const remote = latest.find(row => row.slot === slot);
    if (!remote) {
      toast('Bản đám mây vừa được xóa ở thiết bị khác. Bấm lưu lên mây để tạo lại.', true);
      return;
    }
    openModal(
      `
        ${modalHead('Chọn bản lưu cần giữ')}<p>Đám mây có bản mới hơn revision mà thiết bị này đang giữ. Chọn rõ bản cần giữ để
          tránh mất tiến trình.</p>
        <div class="result-row">
          <span>Bản trên máy</span>
          <span>${date(local.updatedAt)}</span>
        </div>
        <div class="result-row">
          <span>Bản đám mây · v${remote.revision}</span>
          <span>${date(remote.updatedAt)}</span>
        </div>
        <div class="actions">
          <button class="btn secondary" data-action="download-save" data-slot="${slot}">Giữ bản đám mây
          </button>
          <button class="btn primary" data-action="force-upload" data-slot="${slot}">Giữ bản trên máy
          </button>
        </div>
        `
    );
  }
}
/** Kiểm snapshot cloud tương thích trước khi ghi slot local theo lựa chọn người chơi. */
async function downloadSave(slot) {
  const remote = await api(`/api/offline-saves/${slot}`),
    state = restoreGame(remote.state),
    owner = scope(),
    local = await storage.getSave(owner, slot);
  if (local && !await confirmAction('Thay bản lưu trên máy?',
      `Bản đám mây được lưu lúc ${date(remote.updatedAt)} sẽ thay tiến trình hiện có ở vị trí ${slot}.`,
      'Tải về máy')) return;
  await storage.saveGame(owner, slot, state, remote.revision);
  toast('Đã tải bản lưu về thiết bị.');
  closeModal();
  await renderRoute();
}
/** Điểm vào cho form tài khoản/phòng/solo; lỗi trả về đúng trường qua formError. */
async function submitForm(form) {
  const type = form.dataset.form;
  const values = Object.fromEntries(new FormData(form));
  if (type === 'login') {
    await login(values.username, values.password);
    form.reset();
    toast('Đăng nhập thành công. Chào mừng chỉ huy!');
    await navigate('/rooms');
  } else if (type === 'register') {
    if (values.password !== values.confirmPassword) throw new Error(
      'Hai mật khẩu chưa khớp nhau.');
    if ([...values.password].length > 64) throw new Error('Mật khẩu cần từ 12 đến 64 ký tự.');
    await register(values);
    form.reset();
    toast('Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.');
    await navigate('/login');
  } else if (type === 'profile') {
    await updateProfile(values.displayName);
    toast('Đã cập nhật tên hiển thị.');
    await renderRoute();
  } else if (type === 'create-room') {
    const room = await api('/api/rooms', {
      method: 'POST',
      body: {
        name: values.name,
        capacity: Number(values.capacity)
      }
    });
    closeModal();
    await navigate(`/rooms/${room.code}`);
  } else if (type === 'join-room') {
    const code = values.code.trim().toUpperCase();
    await api(`/api/rooms/${encodeURIComponent(code)}/join`, {
      method: 'POST'
    });
    await navigate(`/rooms/${code}`);
  } else if (type === 'start-match') {
    const result = await api(`/api/rooms/${form.dataset.code}/start`, {
      method: 'POST',
      body: {
        mode: values.mode,
        difficulty: values.difficulty,
        stage: Number(values.stage || 1)
      }
    });
    await navigate(`/play/${result.matchId}`);
  } else if (type === 'solo-start') {
    const slot = Number(values.slot),
      owner = scope();
    const release = await lockSolo(owner, slot);
    try {
      const previous = await storage.getSave(owner, slot);
      if (previous && !await confirmAction('Bắt đầu trận mới?',
          `Tiến trình cũ ở bản lưu ${slot} sẽ được thay bằng trận mới.`, 'Bắt đầu trận mới'))
        return;
      const progress = await storage.getMeta(`${owner}:progress:${values.difficulty}`) || [];
      if (values.mode === 'CAMPAIGN' && Number(values.stage) > 1 && !progress.includes(Number(
          values.stage) - 1)) throw new Error(
        'Hoàn thành cửa trước ở cùng độ khó để mở nhiệm vụ này.');
      const state = createGame(await catalog(), {
        ...values,
        stage: Number(values.stage || 1),
        bots: Number(values.bots),
        name: user?.displayName || 'Chỉ huy'
      });
      await storage.saveGame(owner, slot, state, previous?.revision || 0);
    } finally {
      release();
    }
    await navigate(`/offline?slot=${slot}&play=1`);
  }
}
/** Điểm vào nút theo data-action: điều hướng, theme, phòng, lưu và cập nhật worker. */
async function handleAction(element) {
  const action = element.dataset.action,
    slot = Number(element.dataset.slot);
  if (action === 'theme') {
    const preference = element.dataset.theme ||
      (document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');
    window.dispatchEvent(new CustomEvent('theme-preference', {
      detail: preference
    }));
    return;
  }
  if (action === 'close-modal') {
    closeModal();
    return;
  }
  if (action === 'menu') {
    const open = document.querySelector('#sidebar').classList.toggle('open');
    document.querySelector('#sidebar').inert = !open && mobileMenu.matches;
    document.querySelector('#menu-backdrop').hidden = !open;
    element.setAttribute('aria-expanded', String(open));
    if (open) document.querySelector('.sidebar-close')?.focus();
    return;
  }
  if (action === 'close-menu') {
    closeMenu();
    document.querySelector('[data-action=menu]')?.focus();
    return;
  }
  if (action === 'password') {
    const input = document.getElementById(element.dataset.target),
      visible = input.type === 'password';
    input.type = visible ? 'text' : 'password';
    element.innerHTML = icon(visible ? 'eye-off' : 'eye');
    element.setAttribute('aria-label', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
    return;
  }
  if (action === 'retry') {
    await renderRoute();
    return;
  }
  if (action === 'logout' || action === 'logout-all') {
    if (!await confirmAction(action === 'logout-all' ? 'Đăng xuất mọi phiên API?' :
        'Rời trạm chỉ huy?', action === 'logout-all' ?
        'Các kết nối chơi online của tài khoản sẽ bị ngắt. Phiên web trên thiết bị khác vẫn tồn tại.' :
        'Bạn cần đăng nhập lại để chơi online và đồng bộ bản lưu.', 'Đăng xuất')) return;
    await logout(action === 'logout-all');
    connection?.stop();
    connection = null;
    toast('Đã đăng xuất.');
    await navigate('/');
    return;
  }
  if (action === 'create-room') {
    openModal(
      `
        ${modalHead('Lập đội hình mới')}<p>Tạo phòng, gửi mã cho đồng đội và cùng xuất trận.</p>
        <form class="form-stack" data-form="create-room">
          <div class="error-box" role="alert"></div>${
            field('name', 'Tên phòng', 'text',
              'required minlength="3" maxlength="40" placeholder="Ví dụ: Biệt đội UTC" aria-describedby="name-error"'
            )
          }<div class="field">
            <label for="capacity">Số
              ghế tối đa</label>
            <select id="capacity" name="capacity">
              <option value="2">2 người</option>
              <option value="3">3 người</option>
              <option value="4" selected>4 người</option>
            </select>
          </div>
          <button class="btn primary wide" type="submit">${icon('plus')}Tạo phòng</button>
        </form>
        `
    );
    return;
  }
  if (action === 'join-room') {
    await api(`/api/rooms/${element.dataset.code}/join`, {
      method: 'POST'
    });
    await navigate(`/rooms/${element.dataset.code}`);
    return;
  }
  if (action === 'filter-rooms') {
    roomsFilter = element.dataset.filter;
    document.querySelectorAll('[data-action=filter-rooms]').forEach(tab => tab.classList.toggle(
      'active', tab === element));
    applyRoomFilter();
    return;
  }
  if (action === 'copy-code') {
    try {
      await navigator.clipboard.writeText(element.dataset.code);
      toast('Đã sao chép mã phòng.');
    } catch {
      openModal(
        `
          ${modalHead('Mã mời đồng đội')}<p>Chọn và sao chép mã phòng bên dưới.</p>
          <input readonly value="${element.dataset.code}" aria-label="Mã phòng">
          `
      );
    }
    return;
  }
  if (action === 'leave-room') {
    const active = connection?.lobby?.matchId;
    if (!await confirmAction(active ? 'Bỏ cuộc và rời phòng?' : 'Rời đội hình?', active ?
        'Rời khi trận đang chạy sẽ được ghi nhận là bỏ cuộc. Bạn vẫn xem được trận trong lịch sử.' :
        'Bạn có thể tham gia phòng khác sau khi rời.', 'Rời phòng', !!active)) return;
    await api(`/api/rooms/${element.dataset.code}/members/me`, {
      method: 'DELETE'
    });
    connection?.stop();
    connection = null;
    await navigate('/rooms');
    return;
  }
  if (action === 'toggle-ready') {
    const me = connection?.lobby?.room.members.find(member => member.userId === user.id);
    await api(`/api/rooms/${element.dataset.code}/ready`, {
      method: 'PUT',
      body: {
        ready: !me?.ready
      }
    });
    return;
  }
  if (action === 'reconnect') {
    await connection?.connect();
    return;
  }
  if (action === 'rank-mode') {
    rankMode = element.dataset.mode;
    rankPage = 0;
    await renderRoute();
    return;
  }
  if (action === 'rank-page') {
    rankPage = Math.max(0, Number(element.dataset.page));
    await renderRoute();
    return;
  }
  if (action === 'history-tab') {
    historyTab = element.dataset.tab;
    historyPage = 0;
    await renderRoute();
    return;
  }
  if (action === 'history-page') {
    historyPage = Math.max(0, Number(element.dataset.page));
    await renderRoute();
    return;
  }
  if (action === 'match-detail') {
    await matchDetail(element.dataset.id);
    return;
  }
  if (action === 'delete-local') {
    const owner = scope(),
      release = await lockSolo(owner, slot);
    try {
      if (await confirmAction('Xóa bản lưu trên máy?',
          `Xóa tiến trình tại vị trí ${slot} trên thiết bị này. Bản đám mây được giữ riêng.`,
          'Xóa bản trên máy', true)) {
        await storage.deleteSave(owner, slot);
        toast('Đã xóa bản lưu trên máy.');
        await renderRoute();
      }
    } finally {
      release();
    }
    return;
  }
  if (action === 'upload-save' || action === 'force-upload') {
    const release = await lockSolo(scope(), slot);
    try {
      await uploadSave(slot, action === 'force-upload');
    } finally {
      release();
    }
    return;
  }
  if (action === 'download-save') {
    const owner = scope(),
      release = await lockSolo(owner, slot);
    try {
      await downloadSave(slot);
    } finally {
      release();
    }
    return;
  }
  if (action === 'delete-cloud') {
    const owner = scope(),
      release = await lockSolo(owner, slot);
    try {
      if (await confirmAction('Xóa bản lưu đám mây?',
          `Xóa bản đám mây tại vị trí ${slot}. Bản trên máy vẫn được giữ.`, 'Xóa bản đám mây',
          true)) {
        await api(`/api/offline-saves/${slot}?revision=${element.dataset.revision}`, {
          method: 'DELETE'
        });
        const local = await storage.getSave(scope(), slot);
        if (local) await storage.saveGame(scope(), slot, local.state, 0);
        toast('Đã xóa bản đám mây.');
        await renderRoute();
      }
    } finally {
      release();
    }
    return;
  }
  if (action === 'pause-solo') {
    solo?.toggle();
    return;
  }
  if (action === 'save-solo') {
    await solo?.persist();
    toast('Đã lưu tiến trình trên thiết bị.');
    return;
  }
  if (action === 'resume-match') {
    await api(`/api/matches/${element.dataset.id}/resume-ready`, {
      method: 'POST'
    });
    await connection?.sync(element.dataset.id);
    return;
  }
  if (action === 'cancel-recovery') {
    if (await confirmAction('Hủy trận đang phục hồi?',
        'Trận sẽ chuyển sang đã hủy và không cộng điểm xếp hạng.', 'Hủy trận', true)) {
      await api(`/api/matches/${element.dataset.id}/cancel-recovery`, {
        method: 'POST'
      });
      await connection?.sync(element.dataset.id);
    }
    return;
  }
  if (action === 'prepare-offline') {
    await prepareOffline();
    toast(offlineReady ? 'Tài nguyên đã tải đủ. Bạn có thể chơi solo khi mất mạng.' :
      'Đang tải tài nguyên. Chờ biểu tượng sẵn sàng offline xuất hiện.');
    return;
  }
  if (action === 'update-ui') {
    if (solo || currentPath().startsWith('/play/')) {
      toast('Kết thúc trận và về trạm chỉ huy trước khi cập nhật giao diện.', true);
      return;
    }
    if (!workerRegistration?.waiting) {
      updatePending = false;
      renderChrome();
      return;
    }
    navigator.serviceWorker.addEventListener('controllerchange', () => location.reload(), {
      once: true
    });
    workerRegistration.waiting.postMessage('ACTIVATE_UPDATE');
    return;
  }
}
/** Event delegation dùng chung cho nút/link của các trang được dựng động. */
document.addEventListener('click', event => {
  const link = event.target.closest('a[data-link]');
  if (link && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey && event
    .button === 0) {
    event.preventDefault();
    navigate(link.href).catch(error => toast(error.message, true));
    return;
  }
  const element = event.target.closest('[data-action]');
  if (!element || element.disabled) return;
  const mayDisable = !['password', 'menu', 'close-menu', 'filter-rooms', 'pause-solo',
    'close-modal'
  ].includes(element.dataset.action);
  const originallyDisabled = element.disabled;
  if (mayDisable) element.disabled = true;
  Promise.resolve(handleAction(element)).catch(error => toast(error.message, true)).finally(
    () => {
      if (mayDisable && element.isConnected) element.disabled = originallyDisabled;
    });
});
/** Chặn submit mặc định, khóa nút khi gửi và hiện lỗi mà không reload vỏ trang. */
document.addEventListener('submit', event => {
  const form = event.target.closest('[data-form]');
  if (!form) return;
  event.preventDefault();
  if (form.dataset.busy) return;
  form.dataset.busy = 'yes';
  const submit = form.querySelector('[type=submit]') || form.querySelector('button');
  const previous = submit?.innerHTML;
  if (submit) {
    submit.disabled = true;
    submit.innerHTML = '<span class="loader"></span> Đang xử lý…';
  }
  submitForm(form).catch(error => {
    if (form.querySelector('.error-box')) formError(form, error);
    else toast(error.message, true);
  }).finally(() => {
    delete form.dataset.busy;
    if (submit?.isConnected) {
      submit.disabled = false;
      submit.innerHTML = previous;
    }
  });
});
document.addEventListener('input', event => {
  if (event.target.id === 'room-search') applyRoomFilter();
});
document.addEventListener('change', event => {
  if (event.target.id === 'rank-difficulty') {
    rankDifficulty = event.target.value;
    rankPage = 0;
    renderRoute();
  }
  const form = event.target.closest('[data-form=solo-start]');
  if (!form) return;
  if (event.target.name === 'mode') {
    document.querySelector('#solo-stages').hidden = form.elements.mode.value !== 'CAMPAIGN';
    document.querySelector('#solo-bots').hidden = form.elements.mode.value !== 'ARENA';
  }
  if (event.target.name === 'difficulty') {
    const version = routeVersion;
    Promise.all([catalog(), storage.getMeta(
      `${scope()}:progress:${form.elements.difficulty.value}`)]).then(([gameCatalog,
      progress
    ]) => {
      if (alive(version)) document.querySelector('#solo-stage-list').innerHTML =
        stageOptions(gameCatalog, progress || []);
    }).catch(error => toast(error.message, true));
  }
});
window.addEventListener('popstate', () => {
  if (solo) solo.paused = true;
  renderRoute();
});
// Đổi nền chỉ vẽ lại phần điều hướng và lựa chọn hiển thị, giữ nguyên trận đang chạy.
/** Chỉ cập nhật thanh công cụ và nút theme; giữ nguyên DOM và vòng lặp trận. */
window.addEventListener('themechange', () => {
  renderChrome();
  document.querySelectorAll('[data-action="theme"][data-theme]').forEach(element => {
    element.setAttribute('aria-pressed', String(
      element.dataset.theme === document.documentElement.dataset.themePreference
    ));
  });
});
window.addEventListener('keydown', event => {
  if (event.key === 'Escape' && document.querySelector('#sidebar').classList.contains('open'))
    closeMenu();
});
window.addEventListener('online', () => {
  renderChrome();
  if (!solo) initializeAuth().then(() => {
    renderChrome();
    if (!user && currentPath() === '/') return;
    renderRoute();
  });
});
window.addEventListener('offline', () => {
  renderChrome();
  if (solo) toast('Mất mạng. Trận solo và bản lưu trên máy vẫn hoạt động.');
});
let lastScope = scope();
/** Đổi danh tính thì đổi vùng lưu; dừng socket cũ và rời solo trước khi mở vùng khác. */
onAuth(event => {
  if (user || event.detail?.signedOut) storage.rememberScope(user);
  const changed = lastScope !== scope();
  lastScope = scope();
  renderChrome();
  if (!user) {
    connection?.stop();
    connection = null;
  }
  if (changed) {
    if (solo) {
      solo.paused = true;
      navigate('/offline', true);
    } else if (['/offline', '/saves', '/history'].includes(currentPath())) renderRoute();
  }
});
/** Đồng bộ nhãn sau khi worker trả lời; giữ lựa chọn đang nhập trong form solo. */
function renderOfflineStatus() {
  const status = document.querySelector('#offline-status');
  if (!status) return;
  status.classList.toggle('orange', !offlineReady);
  status.innerHTML =
    `${icon(offlineReady ? 'circle-check' : 'download')}${offlineReady ? 'ĐÃ TẢI ĐỦ OFFLINE' : 'ĐANG CHUẨN BỊ OFFLINE'}`;
}

/** Đăng ký worker, hỏi đủ asset hay chưa và báo bản cập nhật đang chờ; không ép reload trận. */
async function prepareOffline() {
  if (!('serviceWorker' in navigator) || !isSecureContext) return;
  workerRegistration = await navigator.serviceWorker.register('/sw.js');
  await navigator.serviceWorker.ready;
  const active = workerRegistration.active;
  if (active) {
    const channel = new MessageChannel();
    const complete = new Promise(resolve => {
      channel.port1.onmessage = event => resolve(event.data?.ready === true);
      setTimeout(() => resolve(false), 5000);
    });
    active.postMessage('OFFLINE_STATUS', [channel.port2]);
    offlineReady = await complete;
    renderChrome();
    renderOfflineStatus();
  }
  const showUpdate = () => {
    if (workerRegistration.waiting) {
      const first = !updatePending;
      updatePending = true;
      renderChrome();
      if (first) toast('Có giao diện mới. Chọn nút cập nhật sau khi kết thúc trận.');
    }
  };
  showUpdate();
  if (!workerWatching) {
    workerWatching = true;
    workerRegistration.addEventListener('updatefound', () => {
      const worker = workerRegistration.installing;
      worker?.addEventListener('statechange', () => {
        if (worker.state === 'installed') showUpdate();
      });
    });
  }
}
/** Khởi động song song phiên đăng nhập và vỏ UI; route riêng chờ authReady. */
const authReady = initializeAuth().then(() => {
  renderChrome();
  if (currentPath() === '/') home(routeVersion);
});
if (new URLSearchParams(location.search).get('tab') === 'offline') historyTab = 'offline';
renderChrome();
renderRoute();
prepareOffline().catch(() => {});
