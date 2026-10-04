import { state } from "../../core/state.js";
import { tsMs } from "../../core/format.js";

export const clearedMs = id => state.cleared.get(id) || 0;

export const isCleared = c => {
  const at = clearedMs(c.id);
  const last = tsMs(c.lastAt);
  return !!at && !!last && last <= at;
};

export const messageCleared = (id, m) => {
  const at = clearedMs(id);
  const sent = tsMs(m.at);
  return !!at && !!sent && sent <= at;
};
