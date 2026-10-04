import { t } from "../../i18n.js";
import { el } from "../../core/dom.js";
import { pic } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { setPresenceBadge } from "../presence/presence.js";

export function row(u, sub, time, unread, fn, isActive, silent, onMenu) {
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
  bot.append(el("span", "sub", sub));
  if (silent) bot.append(icon("bellOff", "muteic"));
  if (unread > 0) bot.append(el("span", "badge" + (silent ? " quiet" : ""), unread > 99 ? "99+" : String(unread)));
  body.append(top, bot);
  r.append(av, body);
  r.onclick = fn;
  if (onMenu) {
    r.oncontextmenu = e => {
      e.preventDefault();
      onMenu();
    };
  }
  return r;
}
