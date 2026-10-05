import { t } from "../../i18n.js";
import { el } from "../../core/dom.js";
import { pic } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { setPresenceBadge } from "../presence/presence.js";

export function row(u, sub, time, unread, fn, isActive, silent, onMenu, extra = {}) {
  const r = el("div", "row" + (isActive ? " active" : "") + (unread > 0 ? " unread" : ""));
  const av = el("div", "av");
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  av.append(img);
  setPresenceBadge(av, u);
  const body = el("div", "body");
  const top = el("div", "top");
  top.append(el("b", "", u.name || t("common.user")), el("time", "", time));
  const bot = el("div", "bot");
  const subEl = el("span", "sub");
  if (extra.draft) subEl.append(el("span", "dtag", t("list.draftPrefix")), document.createTextNode(sub));
  else subEl.textContent = sub;
  bot.append(subEl);
  if (silent) bot.append(icon("bellOff", "muteic"));
  if (extra.pinned) bot.append(icon("pin", "pinic"));
  if (unread > 0) bot.append(el("span", "badge" + (silent ? " quiet" : ""), unread > 99 ? "99+" : String(unread)));
  body.append(top, bot);
  r.append(av, body);
  r.onclick = fn;
  r.setAttribute("role", "button");
  r.tabIndex = 0;
  r.setAttribute("aria-label", [u.name || t("common.user"), extra.pinned ? t("list.pinnedLabel") : "", unread > 0 ? unread + " " + t("common.unread") : "", (extra.draft ? t("list.draftPrefix") : "") + sub].filter(Boolean).join(", "));
  r.onkeydown = e => {
    if (e.target !== r || (e.key !== "Enter" && e.key !== " ")) return;
    e.preventDefault();
    fn();
  };
  if (onMenu) {
    r.oncontextmenu = e => {
      e.preventDefault();
      onMenu();
    };
  }
  return r;
}
