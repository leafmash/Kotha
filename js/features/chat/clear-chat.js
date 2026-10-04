import { collection, doc, onSnapshot, serverTimestamp, setDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { auth, db } from "../../core/firebase.js";
import { renderList } from "../chat-list/chat-list.js";
import { closeChat } from "./chat-session.js";
import { renderMessages } from "./message-list.js";
import { unreadOf } from "./read-state.js";
import { askConfirm } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

export function watchCleared(uid, onChange) {
  return onSnapshot(collection(db, "users", uid, "clears"), s => {
    const next = new Map();
    s.docs.forEach(d => {
      const at = d.data({ serverTimestamps: "estimate" }).at;
      if (at && typeof at.toMillis === "function") next.set(d.id, at.toMillis());
    });
    state.cleared = next;
    state.clearedReady = true;
    if (state.active && state.lastMessageDocs) renderMessages(state.lastMessageDocs);
    onChange();
  }, () => {
    state.clearedReady = true;
    onChange();
  });
}

export async function deleteConversation(id) {
  const uid = auth.currentUser?.uid;
  if (!uid || !id || state.deleting) return;
  const chat = state.chats.find(c => c.id === id);
  const ok = await askConfirm({ title: t("chat.deleteTitle"), text: t("chat.deleteText"), ok: t("chat.delete"), iconName: "trash" });
  if (!ok) return;
  const hadUnread = chat ? unreadOf(chat, uid) > 0 : false;
  if (state.active?.id === id) closeChat();
  try {
    await setDoc(doc(db, "users", uid, "clears", id), { at: serverTimestamp() });
    if (hadUnread) setDoc(doc(db, "chats", id), { unread: { [uid]: 0 }, readAt: { [uid]: serverTimestamp() } }, { merge: true }).catch(() => {});
    toast(t("chat.convDeleted"));
  } catch {
    toast(t("chat.deleteFail"));
  }
  renderList();
}
