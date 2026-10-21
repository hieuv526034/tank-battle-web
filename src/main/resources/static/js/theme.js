/**
 * Đọc sở thích sáng/tối trước CSS để tránh chớp nền khi mở trang.
 * app.js gửi theme-preference; file này áp dụng màu rồi phát themechange.
 * Chỉ lưu sở thích hiển thị, không đọc danh tính, token hoặc bản lưu.
 */
(() => {
  const storageKey = 'utc-tank-theme';
  const systemTheme = window.matchMedia('(prefers-color-scheme: dark)');
  const choices = new Set(['system', 'light', 'dark']);
  let preference = 'system';

  try {
    const saved = localStorage.getItem(storageKey);
    if (choices.has(saved)) preference = saved;
  } catch {
    // Khi bị chặn lưu dữ liệu, vẫn đổi nền được trong phiên hiện tại.
  }

  /** Đồng bộ màu trang, thanh trình duyệt và trạng thái nút chọn giao diện. */
  function applyTheme() {
    const resolved = preference === 'system' ? (systemTheme.matches ? 'dark' : 'light') :
      preference;
    document.documentElement.dataset.theme = resolved;
    document.documentElement.dataset.themePreference = preference;
    document.querySelector('meta[name="theme-color"]')?.setAttribute(
      'content', resolved === 'dark' ? '#111411' : '#f3f5ee'
    );
    window.dispatchEvent(new Event('themechange'));
  }

  /** Lựa chọn tường minh ưu tiên hơn hệ điều hành và được giữ cho lần mở sau. */
  window.addEventListener('theme-preference', event => {
    if (!choices.has(event.detail)) return;
    preference = event.detail;
    try {
      localStorage.setItem(storageKey, preference);
    } catch {
      // Safari riêng tư hoặc quyền lưu bị chặn không làm hỏng giao diện.
    }
    applyTheme();
  });

  /** Theo Android/iOS chỉ khi người chơi chọn nền tự động. */
  systemTheme.addEventListener('change', () => {
    if (preference === 'system') applyTheme();
  });

  /** Đồng bộ nền giữa các tab cùng hồ sơ trình duyệt, không tải lại trận. */
  window.addEventListener('storage', event => {
    if (event.key !== storageKey) return;
    preference = choices.has(event.newValue) ? event.newValue : 'system';
    applyTheme();
  });

  applyTheme();
})();
