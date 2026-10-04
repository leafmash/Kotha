import { fmtNumber, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { auth } from "../../core/firebase.js";
import { pic } from "../../core/format.js";
import { nameOf } from "./peer.js";
import { clearReply } from "./reply.js";
import { watchUser } from "../contacts/user-watch.js";
import { iAmAdmin } from "../groups/group-admin.js";
import { renderGroupInfo } from "../groups/group-info.js";
import { isOnline, presenceText, setPresenceBadge } from "../presence/presence.js";

export function renderPeer() {
  if (!state.active) return;
  const locked = !state.active.group && state.blocked.has(state.active.peer);
  $("composer").hidden = locked;
  $("blockBar").hidden = !locked;
  $("roBar").hidden = true;
  $("pane").classList.toggle("ingroup", !!state.active.group);
  if (locked) {
    $("emojiPanel").hidden = true;
    clearReply();
    $("blockText").textContent = t("block.bar", { name: nameOf(state.active.peer) });
  }
  if (state.active.group) {
    $("hcalls").hidden = true;
    const g = state.active.data || {};
    const typers = Object.entries(g.typing || {}).filter(([k, v]) => v && k !== auth.currentUser.uid).map(([k]) => state.users.get(k)?.name).filter(Boolean);
    $("peerImg").src = pic({ name: g.name, photo: g.photo });
    $("peerAv").className = "av";
    $("peerAv").querySelector(".pb")?.remove();
    $("peerName").textContent = g.name || "";
    $("peerStatus").classList.toggle("live", typers.length > 0);
    $("peerStatus").textContent = typers.length ? t("chat.typingMany", { names: typers.join(", ") }) : t("chat.members", { n: fmtNumber(g.members?.length || 0) });
    const readOnly = g.adminOnly === true && !iAmAdmin(g);
    $("composer").hidden = readOnly;
    $("roBar").hidden = !readOnly;
    if (readOnly) {
      $("emojiPanel").hidden = true;
      clearReply();
    }
    if (!$("infoSheet").hidden) renderGroupInfo();
    return;
  }
  const peer = state.users.get(state.active.peer);
  if (!peer) {
    $("hcalls").hidden = locked;
    $("peerAv").className = "av";
    $("peerAv").querySelector(".pb")?.remove();
    $("peerName").textContent = "";
    $("peerStatus").classList.remove("live");
    $("peerStatus").textContent = "";
    watchUser(state.active.peer);
    return;
  }
  $("hcalls").hidden = locked;
  $("peerImg").src = pic(peer);
  $("peerAv").className = "av";
  $("peerAv").querySelector(".pb")?.remove();
  if (isOnline(peer)) setPresenceBadge($("peerAv"), peer);
  $("peerName").textContent = peer.name || "";
  const status = $("peerStatus");
  const typing = state.active.data?.typing?.[state.active.peer];
  status.classList.toggle("live", !!typing || isOnline(peer));
  status.textContent = typing ? t("chat.typing") : presenceText(peer);
}
