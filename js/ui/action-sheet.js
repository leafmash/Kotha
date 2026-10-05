import { el } from "../core/dom.js";
import { icon } from "../core/icons.js";

const CLOSE_MS = 200;
const DISMISS_PX = 90;

const root = el("div");
root.id = "actionSheet";
root.hidden = true;
root.setAttribute("role", "dialog");
root.setAttribute("aria-modal", "true");
const scrim = el("div", "as-scrim");
const panel = el("div", "as-panel");
const grab = el("div", "as-grab");
grab.append(el("i"));
const head = el("div", "as-head");
const list = el("div", "as-list");
panel.append(grab, head, list);
root.append(scrim, panel);
document.body.append(root);

let done = null;
let closing = false;
let timer = 0;

const settle = value => {
  const fn = done;
  done = null;
  if (fn) fn(value);
};

export function closeActionSheet(value = null) {
  if (root.hidden || closing) return;
  closing = true;
  settle(value);
  root.classList.add("leaving");
  clearTimeout(timer);
  timer = setTimeout(() => {
    root.hidden = true;
    root.classList.remove("leaving");
    panel.style.transform = "";
    scrim.style.opacity = "";
    closing = false;
  }, CLOSE_MS);
}

scrim.onclick = () => closeActionSheet(null);

let startY = 0;
let startAt = 0;
let dragging = false;
let offset = 0;

const onDown = e => {
  if (e.target.closest(".as-list") && list.scrollTop > 0) return;
  dragging = true;
  startY = e.clientY;
  startAt = performance.now();
  offset = 0;
  panel.style.transition = "none";
};

const onMove = e => {
  if (!dragging) return;
  offset = Math.max(0, e.clientY - startY);
  panel.style.transform = `translateY(${offset}px)`;
  scrim.style.opacity = String(Math.max(0, 1 - offset / 320));
};

const onUp = () => {
  if (!dragging) return;
  dragging = false;
  panel.style.transition = "";
  const speed = offset / Math.max(1, performance.now() - startAt);
  if (offset > DISMISS_PX || speed > 0.6) {
    closeActionSheet(null);
    return;
  }
  panel.style.transform = "";
  scrim.style.opacity = "";
};

[grab, head].forEach(node => {
  node.addEventListener("pointerdown", onDown);
});
addEventListener("pointermove", onMove);
addEventListener("pointerup", onUp);
addEventListener("pointercancel", onUp);

export const actionSheetOpen = () => !root.hidden && !closing;

export function openActionSheet({ title, subtitle, avatar, badge, actions }) {
  if (!root.hidden) {
    clearTimeout(timer);
    closing = false;
    root.classList.remove("leaving");
    settle(null);
  }
  head.replaceChildren();
  if (avatar) {
    const img = el("img", "as-av");
    img.src = avatar;
    img.alt = "";
    head.append(img);
  } else if (badge !== undefined) {
    head.append(el("span", "as-badge", String(badge)));
  }
  const text = el("div", "as-title");
  text.append(el("b", "", title));
  if (subtitle) text.append(el("small", "", subtitle));
  head.append(text);
  const rows = [];
  actions.forEach((a, i) => {
    if (a.separator && i > 0) rows.push(el("div", "as-sep"));
    const b = el("button", "as-item" + (a.danger ? " danger" : ""));
    b.type = "button";
    b.setAttribute("role", "menuitem");
    const ic = el("span", "as-ic");
    ic.append(icon(a.icon));
    b.append(ic, el("span", "as-label", a.label));
    b.onclick = () => closeActionSheet(a.value);
    rows.push(b);
  });
  list.replaceChildren(...rows);
  list.scrollTop = 0;
  root.hidden = false;
  return new Promise(resolve => {
    done = resolve;
  });
}
