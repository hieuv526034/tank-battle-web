/** Access token chỉ tồn tại trong RAM; refresh nằm trong cookie HttpOnly do server quản lý. */
export class ApiError extends Error {
  constructor(message, code = '', status = 0, errors = {}) {
    super(message);
    this.code = code;
    this.status = status;
    this.errors = errors;
  }
}
let token = null;
let expiresAt = 0;
let apiCsrf = null;
let refreshFlight = null;
let session = null;
let refreshTimer;
export let user = null;
const events = new EventTarget();
const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('utc-tank-auth') :
  null;
const hint = value => {
  try {
    if (value === undefined) return localStorage.getItem('utc-tank-signed-in') === 'yes';
    localStorage.setItem('utc-tank-signed-in', value ? 'yes' : 'no');
  } catch {
    return false;
  }
};

/**
 * Timeout bằng AbortController để Safari iOS không cần AbortSignal.timeout.
 * Giữ hủy từ caller, luôn thu hồi timer/listener; không tự phát lại request.
 */
async function fetchWithTimeout(path, options = {}) {
  const controller = new AbortController();
  const incoming = options.signal;
  const cancel = () => controller.abort();
  if (incoming?.aborted) cancel();
  else incoming?.addEventListener('abort', cancel, {
    once: true
  });
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    cancel();
  }, 15000);
  try {
    return await fetch(path, {
      ...options,
      signal: controller.signal
    });
  } catch (error) {
    if (timedOut) throw new ApiError('Máy chủ phản hồi quá lâu. Vui lòng thử lại.');
    throw error;
  } finally {
    clearTimeout(timer);
    incoming?.removeEventListener('abort', cancel);
  }
}

/** Không cho request bị treo giữ khóa refresh vô hạn hoặc làm kẹt giao diện. */
async function raw(path, options = {}) {
  let response;
  try {
    response = await fetchWithTimeout(path, {
      ...options,
      credentials: 'same-origin',
      cache: 'no-store',
      signal: options.signal,
      headers: {
        'Accept': 'application/json',
        'Accept-Language': 'vi',
        ...options.headers
      }
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ApiError(navigator.onLine ? 'Máy chủ chưa phản hồi. Vui lòng thử lại sau ít giây.' :
      'Bạn đang offline. Chế độ solo và bản lưu trên máy vẫn dùng được.');
  }
  const body = await response.json().catch(() => null);
  if (!response.ok || !body || body.code !== 'SYS_000') {
    throw new ApiError(body?.message || (response.status === 429 ?
        'Bạn thao tác quá nhanh. Vui lòng chờ một phút.' :
        'Không thể thực hiện yêu cầu lúc này.'), body?.code || '', response.status, body
      ?.errors || {});
  }
  return body.data;
}
/** Lấy trạng thái phiên web và CSRF no-store; không lưu thông tin này vào cache offline. */
export async function webSession() {
  session = await raw('/auth/session');
  return session;
}
/** Lấy CSRF cho API dùng refresh cookie; tái sử dụng trong phiên hiện tại. */
async function csrf() {
  if (!apiCsrf) apiCsrf = await raw('/api/auth/csrf');
  return apiCsrf;
}
/** POST auth có CSRF; chỉ thêm Bearer ở thao tác yêu cầu access token. */
async function authPost(path, body, bearer = false) {
  const security = await csrf();
  return raw(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [security.headerName]: security.token,
      ...(bearer && token ? {
        Authorization: `Bearer ${token}`
      } : {})
    },
    body: JSON.stringify(body ?? {})
  });
}

/** Nhận JWT vào RAM và lên lịch refresh trước hạn; phát sự kiện cho socket cập nhật vé. */
function accept(grant) {
  token = grant.accessToken;
  expiresAt = Date.now() + grant.expiresIn * 1000;
  hint(true);
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => refresh().catch(() => {}), Math.max(1000, grant.expiresIn * 1000 -
    35000));
  events.dispatchEvent(new Event('token'));
  return grant;
}

/** Xóa JWT/hồ sơ/timer khỏi RAM và báo các màn hình chuyển về khách. */
function forget() {
  token = null;
  user = null;
  expiresAt = 0;
  hint(false);
  clearTimeout(refreshTimer);
  events.dispatchEvent(new CustomEvent('change', {
    detail: {
      signedOut: true
    }
  }));
}
/** Đổi session web đã xác thực sang JWT bằng CSRF của web session. */
async function exchange() {
  const web = await webSession();
  if (!web.authenticated) throw new ApiError('Đăng nhập để tiếp tục.', 'AUTH_001', 401);
  return accept(await raw('/auth/token', {
    method: 'POST',
    headers: {
      [web.csrfHeaderName]: web.csrfToken
    }
  }));
}
/** Web Locks tuần tự hóa refresh giữa các tab, ngăn reuse thu hồi cả family. */
export async function refresh() {
  if (refreshFlight) return refreshFlight;
  const rotate = async () => {
    try {
      return accept(await authPost('/api/auth/refresh'));
    } catch (error) {
      if (error.status !== 401) throw error;
      try {
        return await exchange();
      } catch (fallback) {
        if (fallback.status === 401) forget();
        throw fallback;
      }
    }
  };
  // Trình duyệt thiếu Web Locks dùng session exchange, tránh rotate đồng thời thiếu khóa.
  refreshFlight = (navigator.locks ? navigator.locks.request('utc-tank-refresh', rotate) :
    exchange()).finally(() => refreshFlight = null);
  return refreshFlight;
}
// navigator.onLine chỉ phản ánh kết nối mạng thiết bị, không chứng minh server truy cập được.
// Cờ này chỉ chọn vùng lưu local, tuyệt đối không cấp quyền gọi API hoặc thay JWT.
export let authUnavailable = !navigator.onLine;

/** Khôi phục tài khoản; phân biệt server không truy cập được với phiên thật sự hết hạn. */
export async function initializeAuth() {
  authUnavailable = !navigator.onLine;
  if (authUnavailable) return null;
  try {
    const web = await webSession();
    if (web.authenticated || hint()) {
      await refresh();
      user = await api('/api/auth/me');
    }
  } catch (error) {
    authUnavailable = !error.status || error.status >= 500;
    if (error.status === 401) forget();
  }
  events.dispatchEvent(new Event('change'));
  return user;
}
/** Đăng nhập form với CSRF, đổi session lấy JWT rồi đọc hồ sơ; thông báo các tab. */
export async function login(username, password) {
  const perform = async () => {
    const web = await webSession();
    const response = await fetchWithTimeout('/login', {
      method: 'POST',
      credentials: 'same-origin',
      cache: 'no-store',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        [web.csrfHeaderName]: web.csrfToken,
        'Accept-Language': 'vi'
      },
      body: new URLSearchParams({
        username: username.trim().toLowerCase(),
        password
      })
    });
    if (!response.ok) {
      const error = await response.json().catch(() => null);
      throw new ApiError(error?.message || 'Đăng nhập chưa thành công.', error?.code, response
        .status, error?.errors);
    }
    apiCsrf = null;
    await exchange();
    user = await api('/api/auth/me');
    authUnavailable = false;
    events.dispatchEvent(new Event('change'));
    channel?.postMessage('signed-in');
    return user;
  };
  return navigator.locks ? navigator.locks.request('utc-tank-refresh', perform) : perform();
}
/** Gửi đăng ký qua API auth; không tự sửa hoặc cắt khoảng trắng mật khẩu. */
export async function register(data) {
  return authPost('/api/auth/register', data);
}
/** Thu hồi refresh qua API rồi đóng session web trước khi xóa trạng thái local. */
export async function logout(all = false) {
  const perform = async () => {
    if (!token || expiresAt < Date.now() + 1000) await exchange();
    await authPost(all ? '/api/auth/logout-all' : '/api/auth/logout', null, true);
    const web = await webSession();
    if (web.authenticated) {
      const response = await fetchWithTimeout('/logout', {
        method: 'POST',
        credentials: 'same-origin',
        cache: 'no-store',
        headers: {
          [web.csrfHeaderName]: web.csrfToken
        }
      });
      if (!response.ok) throw new ApiError(
        'Phiên web chưa được đóng. Vui lòng thử đăng xuất lại.');
    }
    session = null;
    apiCsrf = null;
    forget();
    channel?.postMessage('signed-out');
  };
  return navigator.locks ? navigator.locks.request('utc-tank-refresh', perform) : perform();
}
/** Chỉ retry auth khi server trả 401; không phát lại mutation do lỗi mạng không rõ commit. */
export async function api(path, options = {}, retry = true) {
  const authenticated = options.auth !== false;
  if (authenticated && (!token || expiresAt < Date.now() + 15000)) await refresh();
  const headers = {
    ...options.headers
  };
  if (authenticated && token) headers.Authorization = `Bearer ${token}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (path.startsWith('/api/auth/') && options.method && options.method !== 'GET') {
    const security = await csrf();
    headers[security.headerName] = security.token;
  }
  try {
    return await raw(path, {
      ...options,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
  } catch (error) {
    if (authenticated && retry && error.status === 401) {
      await refresh();
      return api(path, options, false);
    }
    throw error;
  }
}
/** Cập nhật tên hiển thị từ response chuẩn hóa của server và báo UI dựng lại. */
export async function updateProfile(displayName) {
  user = await api('/api/profile', {
    method: 'PUT',
    body: {
      displayName
    }
  });
  events.dispatchEvent(new Event('change'));
  return user;
}
/** Đăng ký theo dõi thay đổi danh tính trong vòng đời ứng dụng. */
export const onAuth = listener => events.addEventListener('change', listener);
/** Đăng ký theo dõi JWT mới và trả hàm gỡ listener cho vòng đời socket. */
export const onToken = listener => {
  events.addEventListener('token', listener);
  return () => events.removeEventListener('token', listener);
};
channel?.addEventListener('message', event => {
  if (event.data === 'signed-out') forget();
  else if (event.data === 'signed-in' && !user) initializeAuth().then(() => events
    .dispatchEvent(new Event('change')));
});
