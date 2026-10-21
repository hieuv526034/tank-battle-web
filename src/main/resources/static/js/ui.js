/** Escape mọi dữ liệu người dùng trước khi đưa vào template HTML. */
export function esc(value = '') {
  return String(value ?? '').replace(/[&<>"']/g, char => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  } [char]));
}
/** Dùng sprite SVG đóng gói nội bộ, không cần tải thư viện từ CDN. */
export const icon = (name, extra = '') =>
  `
    <svg class="icon ${extra}" aria-hidden="true">
      <use href="/icons/lucide.svg#${name}"></use>
    </svg>
    `;
/** Lấy chữ cái tên cho avatar và escape trước khi dựng HTML. */
export const initials = name => esc(String(name || '?').trim().split(/\s+/).slice(-2).map(part => [
  ...part
][0]).join('').toUpperCase());
export const avatar = (name, extra = '') =>
  `
    <span class="avatar ${extra}" aria-hidden="true">${initials(name)}</span>
    `;
export const number = value => new Intl.NumberFormat('vi-VN').format(value || 0);
export const date = value => value ? new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit'
}).format(new Date(value)) : '—';
export const difficultyName = value => ({
  EASY: 'Dễ',
  NORMAL: 'Thường',
  HARD: 'Khó'
} [value] || value);
export const statusName = value => ({
  WAITING: 'Đang chờ',
  RUNNING: 'Đang chiến đấu',
  RECOVERING: 'Chờ khôi phục',
  FINISHED: 'Đã kết thúc',
  ABORTED: 'Đã hủy',
  SAVING: 'Đang lưu',
  ENDING: 'Đang kết toán',
  CLOSED: 'Đã đóng'
} [value] || value);
export const outcomeName = value => ({
  WIN: 'Chiến thắng',
  LOSS: 'Thất bại',
  DRAW: 'Hòa'
} [value] || 'Chưa có kết quả');
/** Vùng chờ có role status để trình đọc màn hình nhận thông báo. */
export const loading = text =>
  `
    <div class="loading" role="status">
      <span class="loader"></span>${esc(text || 'Đang tải dữ liệu…')}
    </div>
    `;
/** Dựng trạng thái rỗng; action nhận HTML nội bộ đã escape dữ liệu động. */
export const empty = (title, text, action = '', symbol = 'crosshair') =>
  `
    <div class="empty">${icon(symbol)}<h3>${esc(title)}</h3>
      <p>${esc(text)}</p>${action}
    </div>
    `;
/** Dựng link điều hướng SPA; escape nhãn và đường dẫn trước khi gắn. */
export const button = (label, symbol, path, style = 'primary') =>
  `
    <a class="btn ${style}" data-link href="${esc(path)}">${symbol ? icon(symbol) : ''}${esc(label)}</a>
    `;
/** Hiện thông báo tạm thời có role phù hợp; nội dung API luôn qua escape. */
export function toast(message, error = false) {
  const element = document.createElement('div');
  element.className = `toast${error ? ' error' : ''}`;
  element.setAttribute('role', error ? 'alert' : 'status');
  element.innerHTML =
    `
      ${icon(error ? 'circle-alert' : 'circle-check')}<span>${esc(message)}</span>
      `;
  document.querySelector('#toasts').append(element);
  setTimeout(() => element.remove(), error ? 9000 : 5000);
}
/** Dùng dialog native để giữ focus trong hộp thoại và chặn tương tác nền. */
export function openModal(content) {
  const dialog = document.querySelector('#modal');
  document.querySelector('#modal-content').innerHTML = content;
  if (!dialog.open) dialog.showModal();
  return dialog;
}
export const modalHead = title =>
  `
    <div class="modal-head">
      <h2>${esc(title)}</h2>
      <button class="icon-button" data-action="close-modal" aria-label="Đóng">${icon('x')}</button>
    </div>
    `;
/** Đóng dialog hiện tại; trình duyệt trả focus về phần tử đã mở. */
export function closeModal() {
  document.querySelector('#modal').close();
}
/** Chờ chọn có/không; đóng bằng Escape cũng trả false và gỡ listener. */
export function confirmAction(title, description, label = 'Xác nhận', danger = false) {
  return new Promise(resolve => {
    const dialog = openModal(
      `
        ${modalHead(title)}<p>${esc(description)}</p>
        <div class="actions">
          <button class="btn secondary" data-confirm="no">Quay lại</button>
          <button class="btn ${danger ? 'danger' : 'primary'}" data-confirm="yes">${esc(label)}</button>
        </div>
        `
    );
    const cleanup = answer => {
      dialog.removeEventListener('click', click);
      dialog.removeEventListener('close', cancel);
      closeModal();
      resolve(answer);
    };
    const click = event => {
      const choice = event.target.closest('[data-confirm]');
      if (choice) cleanup(choice.dataset.confirm === 'yes');
    };
    const cancel = () => cleanup(false);
    dialog.addEventListener('click', click);
    dialog.addEventListener('close', cancel, {
      once: true
    });
  });
}
/** Lỗi validation chỉ hiện thông báo đã được API chuẩn hóa, không đọc raw response. */
export function formError(form, error) {
  form.querySelectorAll('.field-error').forEach(element => element.textContent = '');
  form.querySelectorAll('[aria-invalid]').forEach(element => element.removeAttribute(
    'aria-invalid'));
  const box = form.querySelector('.error-box');
  if (box) box.textContent = error.message || 'Chưa thực hiện được. Vui lòng thử lại.';
  for (const [field, violation] of Object.entries(error.errors || {})) {
    const input = form.elements.namedItem(field);
    if (input instanceof HTMLElement) {
      input.setAttribute('aria-invalid', 'true');
      const target = input.closest('.field')?.querySelector('.field-error');
      if (target) target.textContent = violation.message || 'Giá trị chưa hợp lệ.';
    }
  }
  form.querySelector('[aria-invalid=true]')?.focus();
}
