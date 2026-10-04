import { locale, t } from "../i18n.js";

export const palette = ["#ff8a5c", "#ff4f8b", "#8b5cf6", "#22b8cf", "#f59e0b", "#10b981"];
const initialAvatar = (name = "?") => {
  const c = palette[[...name].reduce((a, ch) => a + ch.charCodeAt(0), 0) % palette.length];
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40'><rect width='40' height='40' fill='${c}'/><text x='20' y='27' font-size='19' text-anchor='middle' fill='white' font-family='sans-serif'>${[...name][0].toUpperCase()}</text></svg>`;
  return "data:image/svg+xml;utf8," + encodeURIComponent(svg);
};
export const pic = u => u?.photo || initialAvatar(u?.name || "?");
export const clock = ts => ts?.toDate ? ts.toDate().toLocaleTimeString(locale(), { hour: "2-digit", minute: "2-digit" }) : "";
export const dayLabel = d => {
  const ref = new Date();
  if (d.toDateString() === ref.toDateString()) return t("common.today");
  ref.setDate(ref.getDate() - 1);
  if (d.toDateString() === ref.toDateString()) return t("common.yesterday");
  return d.toLocaleDateString(locale(), { day: "numeric", month: "long" });
};
export const listTime = ts => {
  if (!ts?.toDate) return "";
  const d = ts.toDate();
  return d.toDateString() === new Date().toDateString() ? clock(ts) : d.toLocaleDateString(locale(), { day: "numeric", month: "short" });
};

export const tsMs = ts => (ts && typeof ts.toMillis === "function" ? ts.toMillis() : 0);
