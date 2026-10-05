import { collection, doc, limit, onSnapshot, orderBy, query, setDoc, writeBatch } from "../../core/sdk.js";
import { setActiveChat } from "../../native.js";
import { state } from "../../core/state.js";
import { PAGE } from "../../core/constants.js";
import { $, input } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { renderList } from "../chat-list/chat-list.js";
import { syncComposer } from "./composer.js";
import { renderPeer } from "./header.js";
import { goneCache, renderMessages } from "./message-list.js";
import { markRead } from "./read-state.js";
import { clearReply } from "./reply.js";
import { watchUser } from "../contacts/user-watch.js";
import { closeUserProfile } from "../profile/user-profile.js";

const mobileLayout = () => !matchMedia("(min-width:901px) and (pointer:fine)").matches;

const reducedMotion = () => matchMedia("(prefers-reduced-motion: reduce)").matches;

const PANE_EXIT_MS = 320;

let paneTimer = 0;

const hidePane = () => {
  clearTimeout(paneTimer);
  if (state.active) return;
  $("pane").hidden = true;
  $("empty").hidden = false;
};

let chatUnsubs = [];

let msgUnsub = null;

const drafts = new Map();

export function closeChat(instant = false) {
  if (state.active) drafts.set(state.active.id, input.value);
  msgUnsub?.();
  msgUnsub = null;
  chatUnsubs.forEach(u => u());
  chatUnsubs = [];
  state.active = null;
  setActiveChat(null);
  closeUserProfile();
  $("infoSheet").hidden = true;
  $("editBox").hidden = true;
  $("app").classList.remove("in-chat");
  clearTimeout(paneTimer);
  if (instant || !mobileLayout() || reducedMotion()) {
    hidePane();
    return;
  }
  paneTimer = setTimeout(hidePane, PANE_EXIT_MS);
}

export async function openChat(peer, group) {
  const uid = auth.currentUser.uid;
  const id = group ? group.id : [uid, peer.uid].sort().join("_");
  if (state.active) drafts.set(state.active.id, input.value);
  chatUnsubs.forEach(u => u());
  chatUnsubs = [];
  msgUnsub?.();
  msgUnsub = null;
  const ref = doc(db, "chats", id);
  if (!group && !state.chats.some(c => c.id === id)) await setDoc(ref, { members: [uid, peer.uid] }, { merge: true });
  goneCache.clear();
  state.active = { id, peer: group ? null : peer.uid, group: !!group, members: group ? group.members : [uid, peer.uid], reply: null, data: group || null, first: true, lastId: null, limit: PAGE, hasMore: false, olderLoad: false };
  clearTimeout(paneTimer);
  setActiveChat(id);
  if (!group) watchUser(peer.uid);
  $("empty").hidden = true;
  $("pane").hidden = false;
  $("app").classList.add("in-chat");
  $("messages").replaceChildren();
  $("emojiPanel").hidden = true;
  clearReply();
  renderPeer();
  renderList();
  markRead();

  chatUnsubs.push(onSnapshot(ref, s => {
    if (!state.active || state.active.id !== id) return;
    state.active.data = s.data();
    if (state.active.group && Array.isArray(state.active.data?.members)) state.active.members = state.active.data.members;
    renderPeer();
    markRead();
    renderList();
  }));
  listenMessages();
  input.value = drafts.get(id) || "";
  syncComposer();
  input.blur();
}

export function listenMessages() {
  const target = state.active;
  const uid = auth.currentUser.uid;
  msgUnsub?.();
  msgUnsub = onSnapshot(query(collection(db, "chats", target.id, "messages"), orderBy("at", "desc"), limit(target.limit)), { includeMetadataChanges: true }, s => {
    if (state.active !== target) return;
    target.hasMore = s.docs.length >= target.limit;
    state.lastMessageDocs = s.docs.slice().reverse();
    renderMessages(state.lastMessageDocs);
    const unseen = s.docs.filter(d => d.data().from !== uid && d.data().type !== "system" && d.data().status !== "seen");
    if (unseen.length) {
      const batch = writeBatch(db);
      unseen.forEach(d => batch.update(d.ref, { status: "seen" }));
      batch.commit().catch(() => {});
    }
    markRead(unseen.length > 0);
  });
}

export function openChatById(id) {
  const c = state.chats.find(x => x.id === id);
  if (!c) return false;
  if (c.group) {
    openChat(null, c);
    return true;
  }
  const peer = state.users.get(c.members.find(m => m !== auth.currentUser.uid));
  if (!peer) return false;
  openChat(peer);
  return true;
}

export function initChatSession() {
  $("backBtn").onclick = () => {
    closeChat();
    renderList();
  };
}
