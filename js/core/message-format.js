import { displayStored, stored, t } from "../i18n.js";

export const preview = m => {
  if (m.deleted) return t("chat.deleted");
  if (m.callLog) return callLogText(m.callLog);
  return { image: t("common.photo"), video: t("common.video"), audio: t("common.voiceMessage"), file: m.name || t("common.file") }[m.type] || m.text;
};
export const previewStored = m => {
  if (m.deleted) return stored("deleted");
  if (m.callLog) return callLogText(m.callLog);
  const tokens = { image: stored("image"), video: stored("video"), audio: stored("audio"), file: m.name || stored("file") };
  return tokens[m.type] || m.text;
};
export const callLogText = log => {
  const label = t(log.video ? "call.video" : "call.voice");
  const icon = log.video ? "🎥" : "📞";
  if (log.kind === "done") return icon + " " + label + " · " + Math.floor((log.secs || 0) / 60) + ":" + String((log.secs || 0) % 60).padStart(2, "0");
  if (log.kind === "declined") return icon + " " + t("call.logDeclined", { label });
  if (log.kind === "cancelled") return icon + " " + t("call.logCancelled", { label });
  return icon + " " + t("call.logMissed", { label });
};
const callEventKind = (log, mine) => (!mine && log.kind === "cancelled" ? "missed" : log.kind);
export const callEventParts = (log, mine) => {
  const label = t(log.video ? "call.shortVideo" : "call.shortVoice");
  const secs = Number(log.secs) || 0;
  const kind = callEventKind(log, mine);
  if (kind === "done") return { title: t(mine ? "call.evOutgoing" : "call.evIncoming", { label }), sub: secs ? Math.floor(secs / 60) + ":" + String(secs % 60).padStart(2, "0") : "" };
  if (kind === "declined") return { title: t("call.evDeclined", { label }), sub: "" };
  if (kind === "cancelled") return { title: t("call.evCancelled", { label }), sub: "" };
  if (mine) return { title: t("call.evOutgoing", { label }), sub: t("call.noAnswer") };
  return { title: t("call.evMissed", { label }), sub: "" };
};
export const lastText = value => displayStored(value);
