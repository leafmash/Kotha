import { t } from "../../i18n.js";
import { el } from "../../core/dom.js";
import { pic } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { setPresenceBadge } from "../presence/presence.js";
import { haptic } from "../../native.js";
import { openChatActions } from "./chat-actions.js";

const HOLD_MS = 420;
const MOVE_LIMIT = 10;

function bindActions(r, id, open, who) {
  let timer = 0;
  let held = false;
  let sx = 0;
  let sy = 0;
  const cancel = () => clearTimeout(timer);
  r.addEventListener("pointerdown", e => {
    if (e.pointerType === "mouse" && e.button !== 0) return;
    held = false;
    sx = e.clientX;
    sy = e.clientY;
    cancel();
    timer = setTimeout(() => {
      held = true;
      haptic("tap");
      openChatActions(id, who);
    }, HOLD_MS);
  });
  r.addEventListener("pointermove", e => {
    if (Math.abs(e.clientX - sx) > MOVE_LIMIT || Math.abs(e.clientY - sy) > MOVE_LIMIT) cancel();
  });
  ["pointerup", "pointercancel", "pointerleave"].forEach(ev => r.addEventListener(ev, cancel));
  r.oncontextmenu = e => {
    e.preventDefault();
    openChatActions(id, who);
  };
  r.onclick = () => {
    if (held) {
      held = false;
      return;
    }
    open();
  };
  r.onkeydown = e => {
    if (e.target !== r) return;
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      open();
    } else if (e.key === "Delete" || e.key === "Backspace") {
      e.preventDefault();
      openChatActions(id, who);
    }
  };
}

export function row(u, sub, time, unread, fn, isActive, silent, extra = {}) {
  const r = el("div", "row" + (isActive ? " active" : "") + (unread > 0 ? " unread" : ""));
  const av = el("div", "av");
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  av.append(img);
  setPresenceBadge(av, u);
  if (extra.id) r.dataset.id = extra.id;
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
  r.setAttribute("role", "button");
  r.tabIndex = 0;
  r.setAttribute("aria-label", [u.name || t("common.user"), extra.pinned ? t("list.pinnedLabel") : "", unread > 0 ? unread + " " + t("common.unread") : "", (extra.draft ? t("list.draftPrefix") : "") + sub].filter(Boolean).join(", "));
  if (extra.id) {
    bindActions(r, extra.id, fn, u);
    return r;
  }
  r.onclick = fn;
  r.onkeydown = e => {
    if (e.target !== r || (e.key !== "Enter" && e.key !== " ")) return;
    e.preventDefault();
    fn();
  };
  return r;
}
