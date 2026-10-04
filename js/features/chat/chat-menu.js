import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { icon } from "../../core/icons.js";
import { blockFlow, unblockFlow } from "../block/block.js";
import { isMuted, muteFlow } from "./mute.js";
import { openGroupInfo } from "../groups/group-info.js";
import { leaveGroup } from "../groups/group-leave.js";
import { reportGroup, reportUser } from "../report/report.js";

export const chatMenu = $("chatMenu");

export function closeChatMenu() {
  chatMenu.hidden = true;
  $("chatMenuBtn").setAttribute("aria-expanded", "false");
}

export function openChatMenu() {
  if (!state.active) return;
  const items = [];
  if (state.active.group) {
    items.push({ icon: "users", label: t("ginfo.title"), fn: openGroupInfo });
    items.push({ icon: "flag", label: t("report.group"), fn: reportGroup });
    items.push({ icon: "logout", label: t("group.leave"), fn: leaveGroup, danger: true });
  } else {
    const id = state.active.peer;
    if (state.blocked.has(id)) items.push({ icon: "ban", label: t("block.unblockUser"), fn: () => unblockFlow(id) });
    else items.push({ icon: "ban", label: t("block.user"), fn: () => blockFlow(id), danger: true });
    items.push({ icon: "flag", label: t("report.user"), fn: () => reportUser(id) });
  }
  const silenced = isMuted(state.active.id);
  items.unshift({ icon: silenced ? "bell" : "bellOff", label: t(silenced ? "mute.unmute" : "mute.action"), fn: () => muteFlow(state.active.id) });
  chatMenu.replaceChildren(...items.map(it => {
    const b = el("button", "mi" + (it.danger ? " danger" : ""));
    b.setAttribute("role", "menuitem");
    b.append(icon(it.icon), el("span", "", it.label));
    b.onclick = () => {
      closeChatMenu();
      it.fn();
    };
    return b;
  }));
  chatMenu.hidden = false;
  $("chatMenuBtn").setAttribute("aria-expanded", "true");
}

export function initChatMenu() {
  $("chatMenuBtn").onclick = () => chatMenu.hidden ? openChatMenu() : closeChatMenu();
  document.addEventListener("click", e => {
    if (!chatMenu.hidden && !e.target.closest("#chatMenu, #chatMenuBtn")) closeChatMenu();
  });
}
