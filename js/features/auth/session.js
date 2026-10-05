import { collection, deleteField, doc, getDoc, onAuthStateChanged, onSnapshot, query, setDoc, updateDoc, where } from "../../core/sdk.js";
import { TERMS_VERSION } from "../../config.js";
import { getLang, t } from "../../i18n.js";
import { initBatteryPrompt, syncNativeSession } from "../../native.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { auth, db, firebaseConfig } from "../../core/firebase.js";
import { renderAuthTexts } from "./auth-form.js";
import { showTermsGate } from "./terms.js";
import { refreshVerified, registerLookup, syncVerifyBar } from "./verify-email.js";
import { refreshBlockedUi } from "../block/block.js";
import { calls } from "../calls/calls.js";
import { watchCleared } from "../chat/clear-chat.js";
import { renderList } from "../chat-list/chat-list.js";
import { chatMenu, openChatMenu } from "../chat/chat-menu.js";
import { closeChat, openChatById } from "../chat/chat-session.js";
import { goneCache } from "../chat/message-list.js";
import { isMuted } from "../chat/mute.js";
import { flushOutbox } from "../chat/outbox.js";
import { clearBlockedUnread, deliverKeys, markDelivered, readLocal, readSent } from "../chat/read-state.js";
import { userWatch, watchUser } from "../contacts/user-watch.js";
import { notify } from "../notifications/notify.js";
import { registerPush } from "../notifications/push.js";
import { armRtPresence, rtPresence, setPresence, startRtPresence } from "../presence/presence.js";
import { closeUserProfile } from "../profile/user-profile.js";
import { closeMore } from "../../ui/more-menu.js";
import { hideSplash, showSplash } from "../../ui/splash.js";
import { toast } from "../../ui/toast.js";

const lastAtSeen = new Map();

export const checkReady = () => {
  if (!(state.usersLoaded && state.chatsReady && state.clearedReady)) return;
  hideSplash();
  if (state.pendingChat && openChatById(state.pendingChat)) {
    state.pendingChat = null;
    history.replaceState(null, "", location.pathname);
  }
};

export function initSession() {
  onAuthStateChanged(auth, async user => {
    state.unsubs.forEach(u => u());
    state.unsubs = [];
    if (!user) {
      calls.stop();
      state.users = new Map();
      rtPresence.clear();
      state.chats = [];
      state.usersLoaded = false;
      state.chatsReady = false;
      state.cleared = new Map();
      state.clearedReady = false;
      lastAtSeen.clear();
      goneCache.clear();
      state.blocked = new Set();
      state.blockedReady = false;
      state.muted = {};
      state.callDocs = new Map();
      state.callHistoryOn = false;
      deliverKeys.clear();
      $("termsGate").hidden = true;
      readLocal.clear();
      readSent.clear();
      $("settingsSheet").hidden = true;
      closeUserProfile();
      $("editProfileSheet").hidden = true;
      $("blockedSheet").hidden = true;
      $("profileSheet").hidden = true;
      $("forwardSheet").hidden = true;
      $("verifyBar").hidden = true;
      closeMore();
      closeChat();
      $("app").hidden = true;
      $("auth").hidden = false;
      $("authBtn").disabled = false;
      renderAuthTexts();
      localStorage.removeItem("kotha-session");
      syncNativeSession(null, firebaseConfig);
      hideSplash();
      return;
    }
    localStorage.setItem("kotha-session", "1");
    syncNativeSession(user, firebaseConfig);
    showSplash();
    $("auth").hidden = true;
    state.usersLoaded = false;
    state.chatsReady = false;
    state.cleared = new Map();
    state.clearedReady = false;
    state.users = new Map();
    state.searchState = { term: "", status: "idle", user: null };
    const ref = doc(db, "users", user.uid);
    let needTerms = false;
    try {
      const snap = await getDoc(ref);
      needTerms = !snap.exists() || snap.data().termsVersion !== TERMS_VERSION;
      if (!snap.exists()) {
        await setDoc(ref, { uid: user.uid, name: state.pendingName || user.displayName || (user.email || "").split("@")[0] || t("common.user"), photo: user.photoURL || "" }, { merge: true });
      } else if ("email" in snap.data()) {
        await updateDoc(ref, { email: deleteField() });
      }
      if (!snap.exists() || snap.data().lang !== getLang()) await setDoc(ref, { lang: getLang() }, { merge: true });
      await registerLookup(user);
    } catch (err) {
      hideSplash();
      toast(t("list.loadFail"));
    }
    $("app").hidden = false;
    syncVerifyBar();
    refreshVerified(false);
    if (needTerms) showTermsGate();
    registerPush(true);
    initBatteryPrompt();
    calls.start(user.uid);
    startRtPresence();
    armRtPresence();
    setPresence(true);
    flushOutbox();
    state.unsubs.push(() => {
      userWatch.forEach(u => u());
      userWatch.clear();
    });
    watchUser(user.uid);
    state.unsubs.push(onSnapshot(collection(db, "users", user.uid, "blocked"), s => {
      state.blocked = new Set(s.docs.map(d => d.id));
      state.blockedReady = true;
      state.blocked.forEach(watchUser);
      clearBlockedUnread();
      refreshBlockedUi();
    }, () => {
      state.blockedReady = true;
      renderList();
    }));
    state.unsubs.push(watchCleared(user.uid, () => {
      renderList();
      checkReady();
    }));
    state.unsubs.push(onSnapshot(doc(db, "pushTokens", user.uid), s => {
      state.muted = s.exists() ? s.data().muted || {} : {};
      state.pinned = s.exists() ? s.data().pinned || {} : {};
      state.archived = s.exists() ? s.data().archived || {} : {};
      renderList();
      if (!chatMenu.hidden) openChatMenu();
    }, () => {}));
    state.unsubs.push(onSnapshot(query(collection(db, "chats"), where("members", "array-contains", user.uid)), s => {
      const initial = !state.chatsReady;
      state.chatsReady = true;
      s.docChanges().forEach(ch => {
        const c = ch.doc.data();
        const stamp = c.lastAt?.seconds || 0;
        const prev = lastAtSeen.get(ch.doc.id) || 0;
        lastAtSeen.set(ch.doc.id, stamp);
        if (ch.type === "removed" && state.active?.group && state.active.id === ch.doc.id) {
          closeChat();
          toast(t("group.removedYou"));
          return;
        }
        if (initial || !stamp || stamp <= prev || c.lastFrom === user.uid || state.blocked.has(c.lastFrom)) return;
        if ((document.hidden || state.active?.id !== ch.doc.id) && !isMuted(ch.doc.id)) notify(c, ch.doc.id);
      });
      state.chats = s.docs.map(d => ({ id: d.id, ...d.data() }));
      state.chats.flatMap(c => c.members || []).forEach(watchUser);
      clearBlockedUnread();
      markDelivered(state.chats);
      renderList();
      checkReady();
    }, hideSplash));
  });
}
