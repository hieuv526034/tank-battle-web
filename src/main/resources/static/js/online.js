import {
  api,
  onToken,
  user
} from './api.js';
/**
 * Một kết nối điều khiển/phòng: JWT → vé một lần qua API → WebSocket cùng origin.
 * Reconnect luôn xin vé mới, reset sequence và sync state từ server; không phát lại
 * ý định bắn/di chuyển lúc mất mạng. Kết quả chính thức chỉ do server kết toán.
 */
export class RoomConnection extends EventTarget {
  /** Khởi tạo kết nối theo mã phòng; theo dõi đổi JWT và sự kiện có mạng trở lại. */
  constructor(code) {
    super();
    this.code = code;
    this.socket = null;
    this.closed = false;
    this.sequence = 0;
    this.retry = 0;
    this.lobby = null;
    this.frame = null;
    this.timer = null;
    this.generation = 0;
    this.removeToken = onToken(() => {
      if (this.socket?.readyState === WebSocket.OPEN) this.connect();
    });
    this.online = () => {
      if (!this.closed) this.connect();
    };
    window.addEventListener('online', this.online);
  }
  /** Phát sự kiện dữ liệu để lobby và màn hình trận dùng cùng một kết nối. */
  publish(type, data) {
    this.dispatchEvent(new CustomEvent(type, {
      detail: data
    }));
  }
  /** Xin vé socket mới qua Bearer API, thay kết nối cũ và bỏ phản hồi thuộc thế hệ cũ. */
  async connect() {
    if (this.closed || this.connecting) return;
    this.connecting = true;
    const generation = ++this.generation;
    clearTimeout(this.timer);
    this.publish('connection', 'connecting');
    try {
      const ticket = await api('/api/auth/socket-ticket', {
        method: 'POST',
        body: {}
      });
      if (this.closed || generation !== this.generation) return;
      const previous = this.socket;
      const socket = new WebSocket(
        `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/game?ticket=${encodeURIComponent(ticket.ticket)}`
      );
      this.socket = socket;
      this.sequence = 0;
      socket.onopen = () => {
        if (this.socket !== socket) return;
        this.retry = 0;
        this.publish('connection', 'connected');
        this.send({
          type: 'subscribe',
          code: this.code
        });
        if (this.frame?.state?.matchId) this.send({
          type: 'sync',
          matchId: this.frame.state.matchId
        });
      };
      socket.onmessage = event => {
        if (this.socket !== socket) return;
        let message;
        try {
          message = JSON.parse(event.data);
        } catch {
          return;
        }
        if (message.type === 'LOBBY') this.lobby = message.data;
        if (message.type === 'GAME') this.frame = message.data;
        this.publish(message.type, message.data);
      };
      socket.onclose = event => {
        if (this.socket !== socket || this.closed) return;
        // SocketHub đóng NORMAL khi tab khác thay kết nối: không tự tranh quyền liên tục.
        if (event.code === 1000 || event.code === 1008) {
          this.publish('connection', 'closed');
          return;
        }
        this.publish('connection', 'disconnected');
        this.timer = setTimeout(() => this.connect(), Math.min(12000, 1000 * 2 ** this
          .retry++));
      };
      socket.onerror = () => {};
      if (previous) {
        previous.onclose = null;
        previous.close(1000, 'renew');
      }
    } catch (error) {
      if (!this.closed) {
        this.publish('ERROR', {
          message: error.message,
          code: error.code
        });
        this.publish('connection', 'disconnected');
        this.timer = setTimeout(() => this.connect(), Math.min(15000, 1500 * 2 ** this.retry++));
      }
    } finally {
      this.connecting = false;
    }
  }
  /** Gửi khi socket OPEN; không giữ hàng đợi ý định cũ để phát lại sau reconnect. */
  send(message) {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
      return true;
    }
    return false;
  }
  /** Gắn sequence tăng dần cho ý định; không gửi vị trí, HP hoặc điểm tự khai. */
  input(matchId, intent) {
    return this.send({
      type: 'input',
      matchId,
      sequence: ++this.sequence,
      ...intent
    });
  }
  /** Đọc frame server để lấy lại trạng thái đầy đủ sau reconnect hoặc yêu cầu sync. */
  async sync(matchId) {
    const frame = await api(`/api/matches/${encodeURIComponent(matchId)}/state`);
    this.frame = frame;
    this.publish('GAME', frame);
    return frame;
  }
  /** Hủy retry/listener, tăng thế hệ và đóng socket khi rời phòng hoặc đăng xuất. */
  stop() {
    this.closed = true;
    this.generation++;
    clearTimeout(this.timer);
    this.removeToken();
    window.removeEventListener('online', this.online);
    if (this.socket) {
      this.socket.onclose = null;
      this.socket.close(1000, 'leave');
    }
  }
}
