/**
 * Kết quả solo dùng chung cho màn hình kết thúc và lịch sử IndexedDB.
 * Giữ cùng quy tắc MatchService của Java: người chết ở tick trước vẫn thua;
 * chỉ người cùng bị loại ở tick kết thúc mới hòa khi MUTUAL_ELIMINATION.
 * Đây là kết quả local, không có quyền ghi bảng xếp hạng online.
 */
export function soloOutcome(state) {
  const player = state.players.solo;
  if (player.forfeited) return 'LOSS';
  if (state.mode === 'CAMPAIGN') {
    return state.endReason === 'CAMPAIGN_CLEARED' && player.hp > 0 ? 'WIN' : 'LOSS';
  }
  if (state.endReason === 'MUTUAL_ELIMINATION') {
    return player.eliminatedAtTick === state.tick ? 'DRAW' : 'LOSS';
  }
  if (player.hp <= 0) return 'LOSS';
  if (state.endReason === 'LAST_SURVIVOR') return 'WIN';
  return state.endReason === 'TIME_LIMIT' ? 'DRAW' : 'LOSS';
}
