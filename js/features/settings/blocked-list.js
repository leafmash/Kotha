import { fmtNumber, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { pic } from "../../core/format.js";
import { unblockFlow } from "../block/block.js";

export function renderBlockedList() {
  const box = $("blockedList");
  box.replaceChildren();
  $("blockedLabel").textContent = t("settings.blockedCount", { n: fmtNumber(state.blocked.size) });
  $("blockedCount").textContent = state.blocked.size ? fmtNumber(state.blocked.size) : "";
  if (!state.blocked.size) {
    box.append(el("p", "hint", t("settings.noBlocked")));
    return;
  }
  [...state.blocked].forEach(id => {
    const u = state.users.get(id);
    const r = el("div", "pick found blockedrow");
    const img = el("img");
    img.src = pic(u || { name: t("common.user") });
    img.alt = "";
    const btn = el("button", "addpill", t("block.unblock"));
    btn.onclick = () => unblockFlow(id);
    r.append(img, el("span", "n", u?.name || t("common.user")), btn);
    box.append(r);
  });
}

function openBlocked() {
  renderBlockedList();
  $("blockedSheet").hidden = false;
  $("blockedSheet").querySelector(".gbody").scrollTop = 0;
}
export function closeBlocked() {
  $("blockedSheet").hidden = true;
}

export function initBlockedList() {
  $("blockedRow").onclick = openBlocked;
  $("blockedClose").onclick = closeBlocked;
}
