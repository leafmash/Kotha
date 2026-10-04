import { doc, onSnapshot, onValue, rtRef } from "../../core/sdk.js";
import { state } from "../../core/state.js";
import { auth, db, rtdb } from "../../core/firebase.js";
import { checkReady } from "../auth/session.js";
import { renderList } from "../chat-list/chat-list.js";
import { renderPeer } from "../chat/header.js";
import { learnClockSkew, rtPresence } from "../presence/presence.js";
import { renderMe } from "../profile/profile.js";
import { renderUserProfile } from "../profile/user-profile.js";

export const userWatch = new Map();

export function watchUser(id) {
  if (!id || userWatch.has(id)) return;
  const stopPresence = rtdb ? onValue(rtRef(rtdb, "presence/" + id), s => {
    const v = s.val();
    if (v && typeof v.at === "number") rtPresence.set(id, { online: v.online === true, at: v.at });
    else rtPresence.delete(id);
    renderList();
    renderPeer();
    renderUserProfile();
  }, () => {}) : null;
  const stopDoc = onSnapshot(doc(db, "users", id), { includeMetadataChanges: id === auth.currentUser?.uid }, s => {
    if (s.exists()) state.users.set(id, { uid: id, ...s.data() });
    else state.users.delete(id);
    if (auth.currentUser && id === auth.currentUser.uid) {
      learnClockSkew(s, id);
      state.usersLoaded = true;
      renderMe();
    }
    renderList();
    renderPeer();
    renderUserProfile();
    checkReady();
  }, () => {
    if (auth.currentUser && id === auth.currentUser.uid) {
      state.usersLoaded = true;
      checkReady();
    }
  });
  userWatch.set(id, () => {
    stopDoc();
    if (stopPresence) stopPresence();
  });
}
