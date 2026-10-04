import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth } from "../../core/firebase.js";
import { pic } from "../../core/format.js";
import { blockFlow, unblockFlow } from "../block/block.js";
import { openChat } from "../chat/chat-session.js";
import { nameOf } from "../chat/peer.js";
import { watchUser } from "../contacts/user-watch.js";
import { isOnline, presenceText } from "../presence/presence.js";
import { openProfile } from "./profile.js";
import { openReport } from "../report/report.js";

let userSheetId = null;

export function renderUserProfile() {
  if ($("userSheet").hidden || !userSheetId) return;
  const id = userSheetId;
  const u = state.users.get(id) || {};
  const name = u.name || t("common.user");
  $("userImg").src = pic(u.name ? u : { ...u, name });
  $("userName").textContent = name;
  const status = $("userStatus");
  const online = isOnline(u);
  status.textContent = presenceText(u);
  status.classList.toggle("live", online);
  const pav = $("userImg").parentElement;
  pav.querySelector(".pdot")?.remove();
  if (online) pav.append(el("span", "pdot"));
  const isBlocked = state.blocked.has(id);
  $("userBlockText").textContent = t(isBlocked ? "user.unblock" : "user.block");
  $("userBlock").classList.toggle("danger", !isBlocked);
  $("userReport").hidden = !state.active;
  const bio = (u.bio || "").trim();
  $("userAboutWrap").hidden = !bio;
  const aboutRow = el("div", "irow");
  aboutRow.append(el("b", "", bio));
  $("userAbout").replaceChildren(...(bio ? [aboutRow] : []));
}

export function openUserProfile(id) {
  if (!id || !auth.currentUser) return;
  if (id === auth.currentUser.uid) {
    openProfile();
    return;
  }
  userSheetId = id;
  watchUser(id);
  $("userSheet").hidden = false;
  $("userSheet").querySelector(".gbody").scrollTop = 0;
  renderUserProfile();
}

export function closeUserProfile() {
  $("userSheet").hidden = true;
  userSheetId = null;
}

export function initUserProfile() {
  $("userClose").onclick = closeUserProfile;
  $("userMessage").onclick = () => {
    const id = userSheetId;
    const peer = state.users.get(id);
    closeUserProfile();
    if (!id || !peer) return;
    if (state.active && !state.active.group && state.active.peer === id) return;
    $("infoSheet").hidden = true;
    openChat({ ...peer, uid: id });
  };
  $("userBlock").onclick = () => {
    const id = userSheetId;
    if (!id) return;
    if (state.blocked.has(id)) unblockFlow(id);
    else blockFlow(id);
  };
  $("userReport").onclick = () => {
    const id = userSheetId;
    if (!id || !state.active) return;
    openReport({ type: "user", chatId: state.active.id, targetUid: id, title: t("report.titleUser", { name: nameOf(id) }) });
  };
}
