import { collection, doc, onSnapshot, serverTimestamp, setDoc } from "../../core/sdk.js";
import { fmtNumber, t } from "../../i18n.js";
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

export async function deleteConversations(ids) {
  const uid = auth.currentUser?.uid;
  if (!uid || !ids.length || state.deleting) return false;
  const many = ids.length > 1;
  const ok = await askConfirm({
    title: t(many ? "chat.deleteManyTitle" : "chat.deleteTitle", { n: fmtNumber(ids.length) }),
    text: t(many ? "chat.deleteManyText" : "chat.deleteText"),
    ok: t("chat.delete"),
    iconName: "trash"
  });
  if (!ok) return false;
  if (state.active && ids.includes(state.active.id)) closeChat();
  const results = await Promise.allSettled(ids.map(id => setDoc(doc(db, "users", uid, "clears", id), { at: serverTimestamp() })));
  ids.forEach((id, i) => {
    if (results[i].status !== "fulfilled") return;
    const chat = state.chats.find(c => c.id === id);
    if (chat && unreadOf(chat, uid) > 0) setDoc(doc(db, "chats", id), { unread: { [uid]: 0 }, readAt: { [uid]: serverTimestamp() } }, { merge: true }).catch(() => {});
  });
  const failed = results.filter(r => r.status === "rejected").length;
  toast(t(failed ? "chat.deleteFail" : many ? "chat.manyDeleted" : "chat.convDeleted", { n: fmtNumber(ids.length) }));
  renderList();
  return failed < ids.length;
}

export const deleteConversation = id => deleteConversations([id]);
