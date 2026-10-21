/** Lưu riêng theo userId; khách không thấy bản lưu cá nhân của tài khoản khác trên cùng máy. */
let connection;

/** Mở IndexedDB một lần, tạo ba object store lần đầu và đóng khi có bản schema mới. */
function database() {
  if (!connection) connection = new Promise((resolve, reject) => {
    const request = indexedDB.open('utc-tank-battle', 1);
    request.onupgradeneeded = () => {
      request.result.createObjectStore('saves', {
        keyPath: 'key'
      });
      request.result.createObjectStore('meta');
      request.result.createObjectStore('results', {
        keyPath: 'key'
      });
    };
    request.onsuccess = () => {
      const db = request.result;
      db.onversionchange = () => {
        db.close();
        connection = null;
      };
      resolve(db);
    };
    request.onerror = () => reject(new Error(
      'Không mở được bộ nhớ trình duyệt. Hãy cho phép lưu dữ liệu để dùng bản lưu offline.'
    ));
    request.onblocked = () => reject(new Error(
      'Đóng các tab trò chơi cũ để cập nhật bộ nhớ offline.'));
  }).catch(error => {
    connection = null;
    throw error;
  });
  return connection;
}
/** Chạy trong một transaction; chỉ resolve sau commit, không resolve ở riêng request con. */
async function operation(stores, mode, action) {
  const db = await database();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(stores, mode);
    let result;
    try {
      result = action(transaction);
    } catch (error) {
      transaction.abort();
      reject(error);
      return;
    }
    transaction.oncomplete = () => resolve(result?.result);
    transaction.onerror = () => reject(new Error(
      'Không lưu được dữ liệu trên máy. Kiểm tra dung lượng và quyền lưu của trình duyệt.'
    ));
    transaction.onabort = () => reject(new Error('Thao tác lưu trên máy chưa hoàn tất.'));
  });
}
const key = (scope, slot) => `${scope}:${slot}`;
/** Đọc một slot theo khóa scope:slot để tách khách và từng tài khoản. */
export const getSave = (scope, slot) => operation(['saves'], 'readonly', tx => tx.objectStore(
  'saves').get(key(scope, slot)));
/** Đọc bản lưu và chọn đúng vùng người chơi, sắp theo vị trí 1–3. */
export async function listSaves(scope) {
  const all = await operation(['saves'], 'readonly', tx => tx.objectStore('saves').getAll());
  return all.filter(save => save.scope === scope).sort((a, b) => a.slot - b.slot);
}
/** Clone snapshot trước khi ghi; giữ revision cloud cũ nếu autosave không truyền revision. */
export function saveGame(scope, slot, state, revision) {
  const snapshot = structuredClone(state);
  return operation(['saves'], 'readwrite', tx => {
    const store = tx.objectStore('saves');
    const existing = store.get(key(scope, slot));
    existing.onsuccess = () => store.put({
      key: key(scope, slot),
      scope,
      slot,
      state: snapshot,
      updatedAt: new Date().toISOString(),
      revision: revision ?? existing.result?.revision ?? 0
    });
  });
}
/** Xóa slot chỉ trong IndexedDB; xóa cloud là luồng API riêng có xác nhận. */
export const deleteSave = (scope, slot) => operation(['saves'], 'readwrite', tx => tx.objectStore(
  'saves').delete(key(scope, slot)));
/** Đọc metadata tiến độ theo khóa trong vùng người chơi. */
export const getMeta = key => operation(['meta'], 'readonly', tx => tx.objectStore('meta').get(
  key));
/** Ghi metadata ngoài snapshot trong transaction riêng. */
export const setMeta = (key, value) => operation(['meta'], 'readwrite', tx => tx.objectStore('meta')
  .put(value, key));
// Chỉ nhớ vùng IndexedDB khi offline; đây không phải thông tin xác thực hoặc quyền gọi API.
let offlineScope = 'guest';
try {
  const saved = localStorage.getItem('utc-tank-offline-scope');
  if (/^user-[1-9][0-9]*$/.test(saved || '')) offlineScope = saved;
} catch {}
/** Nhớ vùng lưu gần nhất cho lúc offline; không dùng giá trị này để xác thực API. */
export function rememberScope(user) {
  offlineScope = user ? `user-${user.id}` : 'guest';
  try {
    localStorage.setItem('utc-tank-offline-scope', offlineScope);
  } catch {}
}
/**
 * Danh tính đã xác thực luôn ưu tiên. Khi không thể hỏi server, mở vùng local gần nhất;
 * không dựa riêng navigator.onLine vì Wi-Fi còn kết nối vẫn có thể mất Internet/server.
 * Đăng xuất chủ động gọi rememberScope(null), nên không tự mở lại vùng tài khoản đã thoát.
 */
export const scopeFor = (user, localOnly = !navigator.onLine) => user ? `user-${user.id}` :
  localOnly ? offlineScope : 'guest';
/** Đọc tối đa các kết quả local đã giữ, lọc đúng vùng và xếp mới nhất trước. */
export async function localHistory(scope) {
  return (await operation(['results'], 'readonly', tx => tx.objectStore('results').getAll()))
    .filter(result => result.scope === scope).sort((a, b) => b.endedAt.localeCompare(a.endedAt));
}
/**
 * Kết toán solo nguyên tử: upsert theo scope:matchId để không nhân đôi kết quả,
 * xóa save dở và mở khóa cửa theo đúng độ khó trong cùng transaction.
 * Khi transaction abort, cả ba thay đổi đều không có hiệu lực; không mất save dở.
 * Dữ liệu local có thể do người chơi sửa nên không dùng làm thành tích online.
 */
export function finishGame(scope, slot, state) {
  return operation(['results', 'saves', 'meta'], 'readwrite', tx => {
    const results = tx.objectStore('results');
    const player = state.players.solo;
    const won = state.mode === 'CAMPAIGN' ? state.endReason === 'CAMPAIGN_CLEARED' : player.hp >
      0 && state.endReason === 'LAST_SURVIVOR';
    results.put({
      key: `${scope}:${state.matchId}`,
      scope,
      id: state.matchId,
      mode: state.mode,
      difficulty: state.difficulty,
      stage: state.stage?.number || 1,
      outcome: won ? 'WIN' : state.endReason === 'TIME_LIMIT' && player.hp > 0 && state
        .mode === 'ARENA' ? 'DRAW' : 'LOSS',
      score: player.score,
      kills: player.kills,
      endedAt: new Date().toISOString()
    });
    tx.objectStore('saves').delete(key(scope, slot));
    const all = results.getAll();
    all.onsuccess = () => all.result.filter(row => row.scope === scope).sort((a, b) => b.endedAt
      .localeCompare(a.endedAt)).slice(20).forEach(row => results.delete(row.key));
    if (won && state.mode === 'CAMPAIGN') {
      const meta = tx.objectStore('meta');
      const progressKey = `${scope}:progress:${state.difficulty}`;
      const progress = meta.get(progressKey);
      progress.onsuccess = () => meta.put([...new Set([...(progress.result || []), state.stage
        .number
      ])], progressKey);
    }
  });
}
