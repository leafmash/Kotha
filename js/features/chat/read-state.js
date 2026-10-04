import { collection, doc, getDocs, limit, orderBy, query, serverTimestamp, setDoc, writeBatch } from "../../core/sdk.js";
import { state } from "../../core/state.js";
import { PAGE } from "../../core/constants.js";
import { auth, db } from "../../core/firebase.js";
import { tsMs } from "../../core/format.js";
import { peerIdOf } from "./peer.js";

export const deliverKeys = new Map();

export const readLocal = new Map();
export const readSent = new Map();

export const unreadOf = (c, uid) => {
  if (state.active?.id === c.id && !document.hidden) return 0;
  if (!c.group && state.blocked.has(peerIdOf(c, uid))) return 0;
  const n = Math.max(0, Math.floor(Number(c.unread?.[uid]) || 0));
  if (!n || c.lastFrom === uid) return 0;
  const last = tsMs(c.lastAt);
  const cleared = readLocal.get(c.id);
  if (cleared !== undefined && last <= cleared) return 0;
  const readAt = tsMs(c.readAt?.[uid]);
  if (readAt && last && readAt >= last) return 0;
  return n;
};

export function markRead(force = false) {
  if (!state.active || document.hidden || !auth.currentUser || state.deleting) return;
  const uid = auth.currentUser.uid;
  const chatId = state.active.id;
  const listed = state.chats.find(c => c.id === chatId);
  const pending = Math.max(Number(state.active.data?.unread?.[uid]) || 0, Number(listed?.unread?.[uid]) || 0);
  const last = Math.max(tsMs(state.active.data?.lastAt), tsMs(listed?.lastAt));
  if (!force && !pending) return;
  readLocal.set(chatId, Math.max(readLocal.get(chatId) || 0, last));
  const key = pending + ":" + last;
  if (!force && readSent.get(chatId) === key) return;
  readSent.set(chatId, key);
  const write = attempt => setDoc(doc(db, "chats", chatId), { unread: { [uid]: 0 }, readAt: { [uid]: serverTimestamp() } }, { merge: true }).catch(() => {
    if (attempt >= 4) {
      readSent.delete(chatId);
      return;
    }
    setTimeout(() => {
      if (auth.currentUser?.uid === uid && !state.deleting) write(attempt + 1);
    }, 1500 * (attempt + 1));
  });
  write(0);
}

const blockClearSent = new Map();

export function clearBlockedUnread() {
  const uid = auth.currentUser?.uid;
  if (!uid || state.deleting || !state.blockedReady) return;
  state.chats.forEach(c => {
    if (c.group || !Array.isArray(c.members) || c.members.length !== 2) return;
    if (!state.blocked.has(peerIdOf(c, uid))) return;
    const n = Number(c.unread?.[uid]) || 0;
    if (!n) return;
    const key = n + ":" + tsMs(c.lastAt);
    if (blockClearSent.get(c.id) === key) return;
    blockClearSent.set(c.id, key);
    setDoc(doc(db, "chats", c.id), { unread: { [uid]: 0 }, readAt: { [uid]: serverTimestamp() } }, { merge: true }).catch(() => blockClearSent.delete(c.id));
  });
}

export function markDelivered(list) {
  const uid = auth.currentUser?.uid;
  if (!uid || state.deleting) return;
  list.forEach(c => {
    const n = Number(c.unread?.[uid]) || 0;
    if (!n || c.lastFrom === uid) return;
    if (state.active?.id === c.id && !document.hidden) return;
    const key = (c.lastAt?.seconds || 0) + ":" + n;
    if (deliverKeys.get(c.id) === key) return;
    deliverKeys.set(c.id, key);
    getDocs(query(collection(db, "chats", c.id, "messages"), orderBy("at", "desc"), limit(Math.min(n, PAGE)))).then(s => {
      const pending = s.docs.filter(d => d.data().from !== uid && d.data().status === "sent");
      if (!pending.length) return null;
      if (state.active?.id === c.id && !document.hidden) return null;
      const batch = writeBatch(db);
      pending.forEach(d => batch.update(d.ref, { status: "delivered" }));
      return batch.commit();
    }).catch(() => deliverKeys.delete(c.id));
  });
}

export function initReadState() {
  addEventListener("focus", () => markRead());
}
