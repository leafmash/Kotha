import { deleteDoc, doc, serverTimestamp, setDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { renderList } from "../chat-list/chat-list.js";
import { renderPeer } from "../chat/header.js";
import { renderMessages } from "../chat/message-list.js";
import { nameOf } from "../chat/peer.js";
import { renderUserProfile } from "../profile/user-profile.js";
import { renderBlockedList } from "../settings/blocked-list.js";
import { askConfirm } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

export const blockedRef = id => doc(db, "users", auth.currentUser.uid, "blocked", id);

export function refreshBlockedUi() {
  renderList();
  renderUserProfile();
  if (state.active) {
    renderPeer();
    if (state.lastMessageDocs) renderMessages(state.lastMessageDocs);
  }
  renderBlockedList();
}

export async function blockFlow(id) {
  if (!id || id === auth.currentUser.uid || state.blocked.has(id)) return;
  const name = nameOf(id);
  const ok = await askConfirm({ title: t("block.confirmTitle", { name }), text: t("block.confirmText"), ok: t("block.action"), iconName: "ban" });
  if (!ok) return;
  setDoc(blockedRef(id), { at: serverTimestamp() }).catch(() => toast(t("block.fail")));
  toast(t("block.done", { name }));
}

export function unblockFlow(id) {
  deleteDoc(blockedRef(id)).catch(() => toast(t("block.unblockFail")));
  toast(t("block.unblocked", { name: nameOf(id) }));
}

export function initBlock() {
  $("unblockBtn").onclick = () => {
    if (state.active && !state.active.group) unblockFlow(state.active.peer);
  };
}
