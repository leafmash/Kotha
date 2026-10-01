import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { initializeAuth, indexedDBLocalPersistence, browserLocalPersistence, browserPopupRedirectResolver, onAuthStateChanged, createUserWithEmailAndPassword, signInWithEmailAndPassword, signInWithPopup, signInWithCredential, GoogleAuthProvider, EmailAuthProvider, reauthenticateWithCredential, reauthenticateWithPopup, signOut, updateProfile } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { getMessaging, getToken, isSupported } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging.js";
import { createCalls } from "./call.js";
import { createPushTrigger } from "./push-trigger.js";
import { t, getLang, locale, fmtNumber, stored, displayStored, applyStatic, onLangChange, toggleLang } from "./i18n.js";
import { isNative, API_BASE, TERMS_VERSION } from "./config.js";
import { setupNative, hideNativeSplash, applyStatusBar, setActiveChat, syncNativeSession, nativeGoogleIdToken, registerNativePush, initBatteryPrompt, consumePendingChat, setBadgeCount } from "./native.js";
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager, collection, doc, getDoc, getDocs, setDoc, updateDoc, addDoc, onSnapshot, query, orderBy, where, limit, serverTimestamp, arrayUnion, arrayRemove, increment, writeBatch, deleteField, deleteDoc, terminate, clearIndexedDbPersistence, waitForPendingWrites } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyAFMdkcndeYSl2A4MckeEBRG0YlAFlorzA",
  authDomain: "duskchat-e02ba.firebaseapp.com",
  projectId: "duskchat-e02ba",
  storageBucket: "duskchat-e02ba.firebasestorage.app",
  messagingSenderId: "96458608876",
  appId: "1:96458608876:web:52e1a5e66dc79ba94795c9",
  measurementId: "G-591ZFVNG23"
};
const CLOUD_NAME = "YOUR_CLOUDINARY_CLOUD_NAME";
const UPLOAD_PRESET = "YOUR_UNSIGNED_UPLOAD_PRESET";
const VAPID_KEY = "YOUR_WEB_PUSH_VAPID_KEY";
const PAGE = 50;

const app = initializeApp(firebaseConfig);
const auth = initializeAuth(app, { persistence: [indexedDBLocalPersistence, browserLocalPersistence], popupRedirectResolver: browserPopupRedirectResolver });
const triggerPush = createPushTrigger(auth);
const db = initializeFirestore(app, { localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }) });

const $ = id => document.getElementById(id);
const input = $("input");
const el = (tag, cls, txt) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (txt !== undefined) e.textContent = txt;
  return e;
};

const palette = ["#ff8a5c", "#ff4f8b", "#8b5cf6", "#22b8cf", "#f59e0b", "#10b981"];
const initialAvatar = (name = "?") => {
  const c = palette[[...name].reduce((a, ch) => a + ch.charCodeAt(0), 0) % palette.length];
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40'><rect width='40' height='40' fill='${c}'/><text x='20' y='27' font-size='19' text-anchor='middle' fill='white' font-family='sans-serif'>${[...name][0].toUpperCase()}</text></svg>`;
  return "data:image/svg+xml;utf8," + encodeURIComponent(svg);
};
const pic = u => u?.photo || initialAvatar(u?.name || "?");
const clock = ts => ts?.toDate ? ts.toDate().toLocaleTimeString(locale(), { hour: "2-digit", minute: "2-digit" }) : "";
const dayLabel = d => {
  const ref = new Date();
  if (d.toDateString() === ref.toDateString()) return t("common.today");
  ref.setDate(ref.getDate() - 1);
  if (d.toDateString() === ref.toDateString()) return t("common.yesterday");
  return d.toLocaleDateString(locale(), { day: "numeric", month: "long" });
};
const listTime = ts => {
  if (!ts?.toDate) return "";
  const d = ts.toDate();
  return d.toDateString() === new Date().toDateString() ? clock(ts) : d.toLocaleDateString(locale(), { day: "numeric", month: "short" });
};
const preview = m => {
  if (m.deleted) return t("chat.deleted");
  if (m.callLog) return callLogText(m.callLog);
  return { image: t("common.photo"), video: t("common.video"), audio: t("common.voiceMessage"), file: m.name || t("common.file") }[m.type] || m.text;
};
const previewStored = m => {
  if (m.deleted) return stored("deleted");
  if (m.callLog) return callLogText(m.callLog);
  const tokens = { image: stored("image"), video: stored("video"), audio: stored("audio"), file: m.name || stored("file") };
  return tokens[m.type] || m.text;
};
const callLogText = log => {
  const label = t(log.video ? "call.video" : "call.voice");
  const icon = log.video ? "🎥" : "📞";
  if (log.kind === "done") return icon + " " + label + " · " + Math.floor((log.secs || 0) / 60) + ":" + String((log.secs || 0) % 60).padStart(2, "0");
  if (log.kind === "declined") return icon + " " + t("call.logDeclined", { label });
  if (log.kind === "cancelled") return icon + " " + t("call.logCancelled", { label });
  return icon + " " + t("call.logMissed", { label });
};
const callEventKind = (log, mine) => (!mine && log.kind === "cancelled" ? "missed" : log.kind);
const callEventText = (log, mine) => {
  const label = t(log.video ? "call.video" : "call.voice");
  const secs = Number(log.secs) || 0;
  const kind = callEventKind(log, mine);
  if (kind === "done") {
    const base = t(mine ? "call.outgoing" : "call.incoming", { label });
    return secs ? base + " · " + Math.floor(secs / 60) + ":" + String(secs % 60).padStart(2, "0") : base;
  }
  if (kind === "declined") return t("call.logDeclined", { label });
  if (kind === "cancelled") return t("call.logCancelled", { label });
  if (mine) return t("call.outgoing", { label }) + " · " + t("call.noAnswer");
  return t("call.logMissed", { label });
};
const lastText = value => displayStored(value);

const icons = {
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.6 2.7 3.9 5.7 3.9 9s-1.3 6.3-3.9 9c-2.6-2.7-3.9-5.7-3.9-9S9.4 5.7 12 3z"/>',
  ban: '<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6l12.8 12.8"/>',
  clip: '<path d="M20 11.5l-8 8a5 5 0 0 1-7-7l8.5-8.5a3.5 3.5 0 0 1 5 5L10 17.5a2 2 0 0 1-3-3l7.5-7.5"/>',
  reply: '<path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 6 6v3"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12M9 7V4h6v3"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  checks: '<path d="M2 12.5l4.5 4.5L15 8M10 15.5l1.5 1.5L21 7.5"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  logout: '<path d="M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 16l-4-4 4-4M6 12h10"/>',
  moon: '<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14a6 6 0 0 1 3.5 6"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  camera: '<path d="M4 8h3l1.5-2h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/><circle cx="12" cy="13" r="3.5"/>',
  flag: '<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>',
  sliders: '<path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/>',
  shield: '<path d="M12 3l8 3v6c0 4.5-3.2 8-8 9-4.8-1-8-4.5-8-9V6l8-3z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  bell: '<path d="M6 17v-6a6 6 0 0 1 12 0v6l1.5 2h-15L6 17zM10 21.5h4"/>',
  bellOff: '<path d="M6 17v-6a6 6 0 0 1 1.5-4M10.5 5.3A6 6 0 0 1 18 11v4l1.5 2H9M10 21h4M4 4l16 16"/>',
  arrowIn: '<path d="M17 7L7 17M7 9v8h8"/>',
  arrowOut: '<path d="M7 17L17 7M9 7h8v8"/>',
  phone: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"/>',
  video: '<rect x="3" y="6" width="12" height="12" rx="2.5"/><path d="M15 10.5l6-3.5v10l-6-3.5"/>'
};
const icon = (name, cls) => {
  const s = el("span", "ico" + (cls ? " " + cls : ""));
  s.innerHTML = `<svg viewBox="0 0 24 24">${icons[name]}</svg>`;
  return s;
};

const confirmBox = $("confirm");
let confirmDone;
const closeConfirm = ok => {
  if (confirmBox.hidden) return;
  confirmBox.hidden = true;
  const done = confirmDone;
  confirmDone = null;
  if (done) done(ok);
};
const askConfirm = ({ title, text, ok, iconName }) => new Promise(resolve => {
  closeConfirm(false);
  $("confirmIcon").replaceChildren(icon(iconName));
  $("confirmTitle").textContent = title;
  $("confirmText").textContent = text;
  $("confirmOk").textContent = ok;
  confirmDone = resolve;
  confirmBox.hidden = false;
  $("confirmCancel").focus();
});
const choiceBox = $("choice");
let choiceDone;
const closeChoice = value => {
  if (choiceBox.hidden) return;
  choiceBox.hidden = true;
  const done = choiceDone;
  choiceDone = null;
  if (done) done(value);
};
const askChoice = ({ title, text, iconName, options }) => new Promise(resolve => {
  closeChoice(null);
  $("choiceIcon").replaceChildren(icon(iconName));
  $("choiceTitle").textContent = title;
  $("choiceText").textContent = text;
  const btns = options.map(o => {
    const b = el("button", o.kind || "", o.label);
    b.onclick = () => closeChoice(o.value);
    return b;
  });
  $("choiceBtns").replaceChildren(...btns);
  choiceDone = resolve;
  choiceBox.hidden = false;
  btns[btns.length - 1].focus();
});
choiceBox.onclick = e => { if (e.target === choiceBox) closeChoice(null); };

const menuBox = $("msgMenu");
const closeMenu = () => { menuBox.hidden = true; };
menuBox.onclick = e => { if (e.target === menuBox) closeMenu(); };

$("confirmOk").onclick = () => closeConfirm(true);
$("confirmCancel").onclick = () => closeConfirm(false);
confirmBox.onclick = e => { if (e.target === confirmBox) closeConfirm(false); };
addEventListener("keydown", e => {
  if (e.key !== "Escape") return;
  closeConfirm(false);
  closeChoice(null);
  closeMenu();
  closeMore();
  closeChatMenu();
});

let toastTimer;
const toast = (text, sticky) => {
  clearTimeout(toastTimer);
  $("toast").textContent = text;
  $("toast").classList.toggle("show", !!text);
  if (text && !sticky) toastTimer = setTimeout(() => $("toast").classList.remove("show"), 3200);
};

let users = new Map();
let chats = [];
let active = null;
let unsubs = [];
let chatUnsubs = [];
let pendingName = "";
let signup = false;
let blocked = new Set();
let deleting = false;
let muted = {};
let callDocs = new Map();
let callHistoryOn = false;
const deliverKeys = new Map();
const inflight = new Set();
const OUTBOX = "kotha-outbox";
let flushing = false;
const FOREVER = 4102444800000;
const isMuted = id => (Number(muted[id]) || 0) > Date.now();
const renderAuthTexts = () => {
  $("authBtn").textContent = t(signup ? "auth.signUp" : "auth.signIn");
  $("switchText").textContent = t(signup ? "auth.haveAccount" : "auth.newHere");
  $("switchLink").textContent = t(signup ? "auth.signIn" : "auth.signUp");
  $("langLink").textContent = getLang() === "en" ? "বাংলা" : "English";
};
let isTyping = false;
let typingTimer;
let chatsReady = false;
let msgUnsub = null;
let lastMessageDocs = null;
let pendingChat = new URLSearchParams(location.search).get("chat");
let usersLoaded = false;
const userWatch = new Map();
const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
let searchState = { term: "", status: "idle", user: null };
let searchTimer;
let splashTimer;
let filter = "all";
const drafts = new Map();
const lastAtSeen = new Map();
const goneCache = new Map();
const unreadOf = (c, uid) => active?.id === c.id && !document.hidden ? 0 : (c.unread?.[uid] || 0);

const hadSession = localStorage.getItem("kotha-session") === "1";
const hideSplash = () => {
  clearTimeout(splashTimer);
  $("splash").hidden = true;
  hideNativeSplash();
};
const showSplash = () => {
  $("splash").hidden = false;
  clearTimeout(splashTimer);
  splashTimer = setTimeout(hideSplash, 8000);
};
const checkReady = () => {
  if (!(usersLoaded && chatsReady)) return;
  hideSplash();
  if (pendingChat && openChatById(pendingChat)) {
    pendingChat = null;
    history.replaceState(null, "", location.pathname);
  }
};
if (hadSession) showSplash();

const toggleTheme = () => {
  const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = next;
  localStorage.setItem("theme", next);
  applyStatusBar(next !== "light");
};

const moreMenu = $("moreMenu");
function closeMore() {
  moreMenu.hidden = true;
  $("menuBtn").setAttribute("aria-expanded", "false");
}
function openMore() {
  const dark = document.documentElement.dataset.theme === "dark";
  const items = [
    { icon: "users", label: t("menu.newGroup"), fn: () => openGroupSheet() },
    { icon: "camera", label: t("menu.changeProfilePhoto"), fn: () => $("avatarInput").click() },
    { icon: dark ? "sun" : "moon", label: dark ? t("menu.lightMode") : t("menu.darkMode"), fn: toggleTheme },
    { icon: "globe", label: getLang() === "en" ? "বাংলা" : "English", fn: toggleLang },
    { icon: "sliders", label: t("menu.settings"), fn: () => openSettings() },
    { icon: "logout", label: t("menu.signOut"), fn: () => logout(), danger: true }
  ];
  moreMenu.replaceChildren(...items.map(it => {
    const b = el("button", "mi" + (it.danger ? " danger" : ""));
    b.setAttribute("role", "menuitem");
    b.append(icon(it.icon), el("span", "", it.label));
    b.onclick = () => {
      closeMore();
      it.fn();
    };
    return b;
  }));
  moreMenu.hidden = false;
  $("menuBtn").setAttribute("aria-expanded", "true");
}
$("menuBtn").onclick = () => moreMenu.hidden ? openMore() : closeMore();
document.addEventListener("click", e => {
  if (!moreMenu.hidden && !e.target.closest("#moreMenu, #menuBtn")) closeMore();
});

$("langLink").onclick = e => {
  e.preventDefault();
  toggleLang();
};

onLangChange(() => {
  renderAuthTexts();
  if (auth.currentUser) {
    updateDoc(doc(db, "users", auth.currentUser.uid), { lang: getLang() }).catch(() => {});
    renderList();
    if (active) {
      renderPeer();
      if (lastMessageDocs) renderMessages(lastMessageDocs);
    }
    if (!$("sheet").hidden) syncGroupUi();
    if (!$("findSheet").hidden) renderFind();
    if (!moreMenu.hidden) openMore();
    if (!chatMenu.hidden) openChatMenu();
    if (!$("settingsSheet").hidden) renderBlockedList();
    if (!$("reportBox").hidden) renderReasons();
  }
  setLegalLinks();
});

renderAuthTexts();

$("switchLink").onclick = e => {
  e.preventDefault();
  signup = !signup;
  $("name").hidden = !signup;
  renderAuthTexts();
};

$("authForm").onsubmit = async e => {
  e.preventDefault();
  $("authErr").textContent = "";
  const btn = $("authBtn");
  const label = btn.textContent;
  btn.disabled = true;
  btn.textContent = t("auth.wait");
  const email = $("email").value.trim();
  const password = $("password").value;
  try {
    if (signup) {
      pendingName = $("name").value.trim() || email.split("@")[0];
      const cred = await createUserWithEmailAndPassword(auth, email, password);
      await updateProfile(cred.user, { displayName: pendingName });
    } else {
      await signInWithEmailAndPassword(auth, email, password);
    }
  } catch (err) {
    $("authErr").textContent = err.code.replace("auth/", "").replace(/-/g, " ");
    btn.disabled = false;
    btn.textContent = label;
  }
};

$("googleBtn").onclick = async () => {
  try {
    if (isNative) {
      const idToken = await nativeGoogleIdToken();
      await signInWithCredential(auth, GoogleAuthProvider.credential(idToken));
    } else {
      await signInWithPopup(auth, new GoogleAuthProvider());
    }
  } catch (err) {
    const cancelled = /cancel/i.test(err.message || "");
    $("authErr").textContent = cancelled ? "" : (err.code || t("auth.googleFail")).replace("auth/", "").replace(/-/g, " ");
  }
};

async function logout() {
  const ok = await askConfirm({ title: t("signout.title"), text: t("signout.text"), ok: t("signout.ok"), iconName: "logout" });
  if (!ok) return;
  await unregisterPush();
  await setPresence(false);
  await signOut(auth);
}

const setPresence = on => auth.currentUser && !deleting
  ? updateDoc(doc(db, "users", auth.currentUser.uid), { online: on, lastSeen: serverTimestamp() }).catch(() => {})
  : Promise.resolve();

document.addEventListener("visibilitychange", () => {
  setPresence(!document.hidden);
  markRead();
  renderList();
});

function markRead(force = false) {
  if (!active || document.hidden || !auth.currentUser || deleting) return;
  const uid = auth.currentUser.uid;
  const listed = chats.find(c => c.id === active.id);
  const pending = (active.data?.unread?.[uid] || 0) + (listed?.unread?.[uid] || 0);
  if (!force && !pending) return;
  setDoc(doc(db, "chats", active.id), { unread: { [uid]: 0 } }, { merge: true }).catch(() => {});
}
addEventListener("beforeunload", () => setPresence(false));

onAuthStateChanged(auth, async user => {
  unsubs.forEach(u => u());
  unsubs = [];
  if (!user) {
    calls.stop();
    users = new Map();
    chats = [];
    usersLoaded = false;
    chatsReady = false;
    lastAtSeen.clear();
    goneCache.clear();
    blocked = new Set();
    muted = {};
    callDocs = new Map();
    callHistoryOn = false;
    deliverKeys.clear();
    $("termsGate").hidden = true;
    $("settingsSheet").hidden = true;
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
  usersLoaded = false;
  chatsReady = false;
  users = new Map();
  searchState = { term: "", status: "idle", user: null };
  const ref = doc(db, "users", user.uid);
  let needTerms = false;
  try {
    const snap = await getDoc(ref);
    needTerms = !snap.exists() || snap.data().termsVersion !== TERMS_VERSION;
    if (!snap.exists()) {
      await setDoc(ref, { uid: user.uid, name: pendingName || user.displayName || (user.email || "").split("@")[0] || t("common.user"), photo: user.photoURL || "" }, { merge: true });
    } else if ("email" in snap.data()) {
      await updateDoc(ref, { email: deleteField() });
    }
    if (!snap.exists() || snap.data().lang !== getLang()) await setDoc(ref, { lang: getLang() }, { merge: true });
    const mail = (user.email || "").toLowerCase();
    if (mail) {
      const lref = doc(db, "emailLookup", mail);
      const lsnap = await getDoc(lref);
      if (!lsnap.exists() || lsnap.data().uid !== user.uid) await setDoc(lref, { uid: user.uid });
    }
  } catch (err) {
    hideSplash();
    toast(t("list.loadFail"));
  }
  $("app").hidden = false;
  if (needTerms) showTermsGate();
  registerPush(true);
  initBatteryPrompt();
  calls.start(user.uid);
  setPresence(true);
  flushOutbox();
  unsubs.push(() => {
    userWatch.forEach(u => u());
    userWatch.clear();
  });
  watchUser(user.uid);
  unsubs.push(onSnapshot(collection(db, "users", user.uid, "blocked"), s => {
    blocked = new Set(s.docs.map(d => d.id));
    blocked.forEach(watchUser);
    refreshBlockedUi();
  }, () => {}));
  unsubs.push(onSnapshot(doc(db, "pushTokens", user.uid), s => {
    muted = s.exists() ? s.data().muted || {} : {};
    renderList();
    if (!chatMenu.hidden) openChatMenu();
  }, () => {}));
  unsubs.push(onSnapshot(query(collection(db, "chats"), where("members", "array-contains", user.uid)), s => {
    const initial = !chatsReady;
    chatsReady = true;
    s.docChanges().forEach(ch => {
      const c = ch.doc.data();
      const stamp = c.lastAt?.seconds || 0;
      const prev = lastAtSeen.get(ch.doc.id) || 0;
      lastAtSeen.set(ch.doc.id, stamp);
      if (initial || !stamp || stamp <= prev || c.lastFrom === user.uid || blocked.has(c.lastFrom)) return;
      if ((document.hidden || active?.id !== ch.doc.id) && !isMuted(ch.doc.id)) notify(c, ch.doc.id);
    });
    chats = s.docs.map(d => ({ id: d.id, ...d.data() }));
    chats.flatMap(c => c.members || []).forEach(watchUser);
    markDelivered(chats);
    renderList();
    checkReady();
  }, hideSplash));
});

function markDelivered(list) {
  const uid = auth.currentUser?.uid;
  if (!uid || deleting) return;
  list.forEach(c => {
    const n = Number(c.unread?.[uid]) || 0;
    if (!n || c.lastFrom === uid) return;
    if (active?.id === c.id && !document.hidden) return;
    const key = (c.lastAt?.seconds || 0) + ":" + n;
    if (deliverKeys.get(c.id) === key) return;
    deliverKeys.set(c.id, key);
    getDocs(query(collection(db, "chats", c.id, "messages"), orderBy("at", "desc"), limit(Math.min(n, PAGE)))).then(s => {
      const pending = s.docs.filter(d => d.data().from !== uid && d.data().status === "sent");
      if (!pending.length) return null;
      if (active?.id === c.id && !document.hidden) return null;
      const batch = writeBatch(db);
      pending.forEach(d => batch.update(d.ref, { status: "delivered" }));
      return batch.commit();
    }).catch(() => deliverKeys.delete(c.id));
  });
}

function watchUser(id) {
  if (!id || userWatch.has(id)) return;
  userWatch.set(id, onSnapshot(doc(db, "users", id), s => {
    if (s.exists()) users.set(id, s.data());
    else users.delete(id);
    if (auth.currentUser && id === auth.currentUser.uid) {
      usersLoaded = true;
      renderMe();
    }
    renderList();
    renderPeer();
    checkReady();
  }, () => {
    if (auth.currentUser && id === auth.currentUser.uid) {
      usersLoaded = true;
      checkReady();
    }
  }));
}

function renderFind() {
  const box = $("findResult");
  box.replaceChildren();
  const term = $("findInput").value.trim().toLowerCase();
  const s = searchState;
  if (!term) return;
  if (!emailRe.test(term)) {
    box.append(el("p", "hint", t("find.invalid")));
  } else if (s.term !== term || s.status === "idle" || s.status === "loading") {
    box.append(el("p", "hint", t("find.loading")));
  } else if (s.status === "found") {
    box.append(row(s.user, term, "", 0, () => {
      closeFind();
      openChat(s.user);
    }));
  } else if (s.status === "self") {
    box.append(el("p", "hint", t("find.self")));
  } else if (s.status === "error") {
    box.append(el("p", "hint", t("find.error")));
  } else {
    box.append(el("p", "hint", t("find.none")));
  }
}

function closeFind() {
  $("findSheet").hidden = true;
  $("findInput").value = "";
  $("findResult").replaceChildren();
  clearTimeout(searchTimer);
  searchState = { term: "", status: "idle", user: null };
}

$("addBtn").onclick = () => {
  $("findInput").value = "";
  $("findResult").replaceChildren();
  $("findSheet").hidden = false;
  $("findInput").focus();
};
$("findClose").onclick = closeFind;
$("findSheet").onclick = e => { if (e.target === $("findSheet")) closeFind(); };
$("findInput").oninput = () => {
  clearTimeout(searchTimer);
  const term = $("findInput").value.trim().toLowerCase();
  if (emailRe.test(term)) searchTimer = setTimeout(() => runSearch(term), 300);
  else searchState = { term, status: "idle", user: null };
  renderFind();
};

async function runSearch(term) {
  searchState = { term, status: "loading", user: null };
  renderFind();
  let next = { term, status: "none", user: null };
  try {
    const hit = await getDoc(doc(db, "emailLookup", term));
    if (searchState.term !== term) return;
    if (hit.exists()) {
      if (hit.data().uid === auth.currentUser.uid) {
        next.status = "self";
      } else {
        const p = await getDoc(doc(db, "users", hit.data().uid));
        if (searchState.term !== term) return;
        if (p.exists()) {
          next = { term, status: "found", user: p.data() };
          users.set(p.id, p.data());
          watchUser(p.id);
        }
      }
    }
  } catch {
    next.status = "error";
  }
  searchState = next;
  renderFind();
}

function renderMe() {
  const me = users.get(auth.currentUser.uid);
  $("meImg").src = pic(me);
  $("railImg").src = pic(me);
  $("meName").textContent = me?.name || "";
}

$("meBtn").onclick = () => $("avatarInput").click();
$("avatarInput").onchange = async e => {
  const file = e.target.files[0];
  e.target.value = "";
  if (!file) return;
  if (!navigator.onLine) {
    toast(t("chat.offlineMedia"));
    return;
  }
  toast(t("photo.uploading"), true);
  try {
    const res = await upload(await compressImage(file, 640, 0.85));
    await updateDoc(doc(db, "users", auth.currentUser.uid), { photo: res.secure_url });
    toast(t("photo.changed"));
  } catch (err) {
    toast(err.message);
  }
};

function row(u, sub, time, unread, fn, isActive, silent) {
  const r = el("div", "row" + (isActive ? " active" : "") + (unread > 0 ? " unread" : ""));
  const av = el("div", "av" + (u.online ? " online" : ""));
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  av.append(img);
  const body = el("div", "body");
  const top = el("div", "top");
  top.append(el("b", "", u.name || t("common.user")), el("time", "", time));
  const bot = el("div", "bot");
  bot.append(el("span", "sub", sub));
  if (silent) bot.append(icon("bellOff", "muteic"));
  if (unread > 0) bot.append(el("span", "badge" + (silent ? " quiet" : ""), unread > 99 ? "99+" : String(unread)));
  body.append(top, bot);
  r.append(av, body);
  r.onclick = fn;
  return r;
}

function renderList() {
  const list = $("list");
  list.replaceChildren();
  if (!auth.currentUser) return;
  const uid = auth.currentUser.uid;
  const term = $("search").value.trim().toLowerCase();
  const hit = name => !term || (name || "").toLowerCase().includes(term);
  const totalUnread = chats.reduce((n, c) => n + (isMuted(c.id) || (!c.group && blocked.has(peerIdOf(c, uid))) ? 0 : unreadOf(c, uid)), 0);
  setBadgeCount(totalUnread);
  document.title = (totalUnread ? `(${totalUnread}) ` : "") + "কথা";
  $("railBadge").hidden = !totalUnread;
  $("railBadge").textContent = totalUnread > 99 ? "99+" : totalUnread;
  $("tabBadge").hidden = !totalUnread;
  $("tabBadge").textContent = totalUnread > 99 ? "99+" : totalUnread;

  if (filter === "calls") {
    renderCalls(list, uid, hit);
    return;
  }

  if (filter === "group" && !term) {
    const r = el("div", "row newgroup");
    const av = el("div", "av");
    const ic = el("div", "ngicon");
    ic.append(icon("plus"));
    av.append(ic);
    const body = el("div", "body");
    body.append(el("b", "", t("list.newGroupTitle")), el("span", "sub", t("list.newGroupSub")));
    r.append(av, body);
    r.onclick = openGroupSheet;
    list.append(r);
  }

  let count = 0;
  [...chats]
    .filter(c => c.lastMessage)
    .filter(c => filter === "all" || (filter === "group" ? c.group : unreadOf(c, uid) > 0))
    .sort((a, b) => (b.lastAt?.seconds || 0) - (a.lastAt?.seconds || 0))
    .forEach(c => {
      if (c.group) {
        if (!hit(c.name)) return;
        const g = { uid: c.id, name: c.name, photo: c.photo || "" };
        const hiddenLast = blocked.has(c.lastFrom);
        let who = "";
        if (c.lastFrom === uid) who = t("list.youPrefix");
        else if (c.lastFrom && !hiddenLast) who = (users.get(c.lastFrom)?.name || "") + ": ";
        const last = hiddenLast ? t("block.hiddenMessage") : lastText(c.lastMessage);
        list.append(row(g, who + last, listTime(c.lastAt), unreadOf(c, uid), () => openChat(null, c), active?.id === c.id, isMuted(c.id)));
        count++;
        return;
      }
      const peerId = peerIdOf(c, uid);
      const peer = users.get(peerId);
      if (!peer || blocked.has(peerId) || !hit(peer.name)) return;
      const sub = (c.lastFrom === uid ? t("list.youPrefix") : "") + lastText(c.lastMessage);
      list.append(row(peer, sub, listTime(c.lastAt), unreadOf(c, uid), () => openChat(peer), active?.peer === peer.uid, isMuted(c.id)));
      count++;
    });

  if (count) return;
  let msg;
  if (term) msg = t("list.noMatch");
  else if (filter === "group") msg = t("list.noGroups");
  else if (filter === "unread") msg = t("list.noUnread");
  else msg = t("list.noChats");
  list.append(el("p", "hint", msg));
}
$("search").oninput = renderList;
$("chips").onclick = e => {
  const b = e.target.closest("button");
  if (!b) return;
  filter = b.dataset.f;
  syncFilterUi();
  renderList();
};

function syncFilterUi() {
  $("chips").querySelectorAll("button").forEach(x => x.classList.toggle("on", x.dataset.f === filter));
  document.querySelectorAll("#rail [data-f]").forEach(x => x.classList.toggle("on", x.dataset.f === filter));
  document.querySelectorAll("#tabs [data-f]").forEach(x => x.classList.toggle("on", x.dataset.f === (filter === "group" || filter === "calls" ? filter : "all")));
  $("chips").hidden = filter === "group" || filter === "calls";
  document.querySelector(".dtitle").textContent = filter === "group" ? t("common.groups") : filter === "calls" ? t("common.calls") : t("common.chats");
  if (filter === "calls") watchCallHistory();
}

function watchCallHistory() {
  if (callHistoryOn || !auth.currentUser) return;
  callHistoryOn = true;
  const uid = auth.currentUser.uid;
  const take = s => {
    s.docChanges().forEach(ch => {
      if (ch.type === "removed") callDocs.delete(ch.doc.id);
      else callDocs.set(ch.doc.id, { id: ch.doc.id, ...ch.doc.data() });
    });
    if (filter === "calls") renderList();
  };
  const fail = () => { callHistoryOn = false; };
  unsubs.push(onSnapshot(query(collection(db, "calls"), where("caller", "==", uid)), take, fail));
  unsubs.push(onSnapshot(query(collection(db, "calls"), where("callee", "==", uid)), take, fail));
}

const stamp = ms => ({ toDate: () => new Date(ms) });

function callEntry(c, uid) {
  const out = c.caller === uid;
  const peer = out ? c.callee : c.caller;
  const at = c.createdAt?.toMillis?.() || Date.now();
  const label = t(c.video ? "call.video" : "call.voice");
  let kind;
  if (c.answer) kind = "done";
  else if (c.status === "ringing") kind = Date.now() - at < 90000 ? "ringing" : "missed";
  else if (c.status === "declined") kind = out ? "declined" : (c.endReason === "busy" ? "missed" : "declined");
  else if (c.status === "missed") kind = "missed";
  else kind = out ? "cancelled" : "missed";
  const secs = Number(c.secs) || 0;
  let text;
  if (kind === "done") text = secs ? label + " · " + Math.floor(secs / 60) + ":" + String(secs % 60).padStart(2, "0") : label;
  else if (kind === "declined") text = t("call.logDeclined", { label });
  else if (kind === "cancelled") text = t("call.logCancelled", { label });
  else if (out) text = label + " · " + t("call.noAnswer");
  else text = t("call.logMissed", { label });
  return { id: c.id, out, peer, at, video: !!c.video, kind, text, chatId: c.chatId, bad: kind === "missed" && !out };
}

function callRow(e) {
  const u = users.get(e.peer) || { name: "" };
  const r = el("div", "row callrow" + (e.bad ? " missed" : ""));
  const av = el("div", "av");
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  av.append(img);
  const body = el("div", "body");
  const top = el("div", "top");
  top.append(el("b", "", u.name || t("common.user")), el("time", "", listTime(stamp(e.at))));
  const bot = el("div", "bot");
  const sub = el("span", "sub csub");
  sub.append(icon(e.out ? "arrowOut" : "arrowIn", "dir"), el("span", "", e.text));
  const again = el("button", "callagain");
  again.title = t("calls.callBack");
  again.setAttribute("aria-label", t("calls.callBack"));
  again.append(icon(e.video ? "video" : "phone"));
  again.onclick = ev => {
    ev.stopPropagation();
    calls.startCall(e.video, { id: e.chatId, peer: e.peer, group: false });
  };
  bot.append(sub, again);
  body.append(top, bot);
  r.append(av, body);
  r.onclick = () => {
    const peer = users.get(e.peer);
    if (peer) openChat(peer);
  };
  return r;
}

function renderCalls(list, uid, hit) {
  const entries = [...callDocs.values()]
    .map(c => callEntry(c, uid))
    .filter(e => e.kind !== "ringing" && e.peer && !blocked.has(e.peer))
    .sort((a, b) => b.at - a.at)
    .slice(0, 100);
  entries.forEach(e => { if (!users.has(e.peer)) watchUser(e.peer); });
  const shown = entries.filter(e => users.has(e.peer) && hit(users.get(e.peer).name));
  shown.forEach(e => list.append(callRow(e)));
  if (shown.length) return;
  list.append(el("p", "hint", t($("search").value.trim() ? "list.noMatch" : "calls.noCalls")));
}

document.querySelectorAll("#tabs [data-f]").forEach(b => {
  b.onclick = () => {
    filter = b.dataset.f;
    syncFilterUi();
    renderList();
  };
});

document.querySelectorAll("#rail [data-f]").forEach(b => {
  b.onclick = () => {
    filter = filter === b.dataset.f && filter !== "all" ? "all" : b.dataset.f;
    syncFilterUi();
    renderList();
  };
});
$("railMe").onclick = () => $("avatarInput").click();

function closeChat() {
  if (active) drafts.set(active.id, input.value);
  msgUnsub?.();
  msgUnsub = null;
  chatUnsubs.forEach(u => u());
  chatUnsubs = [];
  active = null;
  setActiveChat(null);
  $("pane").hidden = true;
  $("empty").hidden = false;
  $("app").classList.remove("in-chat");
}
$("backBtn").onclick = () => {
  closeChat();
  renderList();
};

async function openChat(peer, group) {
  const uid = auth.currentUser.uid;
  const id = group ? group.id : [uid, peer.uid].sort().join("_");
  if (active) drafts.set(active.id, input.value);
  chatUnsubs.forEach(u => u());
  chatUnsubs = [];
  msgUnsub?.();
  msgUnsub = null;
  const ref = doc(db, "chats", id);
  if (!group && !chats.some(c => c.id === id)) await setDoc(ref, { members: [uid, peer.uid] }, { merge: true });
  goneCache.clear();
  active = { id, peer: group ? null : peer.uid, group: !!group, members: group ? group.members : [uid, peer.uid], reply: null, data: group || null, first: true, lastId: null, limit: PAGE, hasMore: false, olderLoad: false };
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

  chatUnsubs.push(onSnapshot(ref, s => {
    if (!active || active.id !== id) return;
    active.data = s.data();
    renderPeer();
    markRead();
    renderList();
  }));
  listenMessages();
  input.value = drafts.get(id) || "";
  syncComposer();
  input.blur();
}

function listenMessages() {
  const target = active;
  const uid = auth.currentUser.uid;
  msgUnsub?.();
  msgUnsub = onSnapshot(query(collection(db, "chats", target.id, "messages"), orderBy("at", "desc"), limit(target.limit)), { includeMetadataChanges: true }, s => {
    if (active !== target) return;
    target.hasMore = s.docs.length >= target.limit;
    lastMessageDocs = s.docs.slice().reverse();
    renderMessages(lastMessageDocs);
    const unseen = s.docs.filter(d => d.data().from !== uid && d.data().status !== "seen");
    if (unseen.length) {
      const batch = writeBatch(db);
      unseen.forEach(d => batch.update(d.ref, { status: "seen" }));
      batch.commit().catch(() => {});
    }
    markRead(unseen.length > 0);
  });
}

function openChatById(id) {
  const c = chats.find(x => x.id === id);
  if (!c) return false;
  if (c.group) {
    openChat(null, c);
    return true;
  }
  const peer = users.get(c.members.find(m => m !== auth.currentUser.uid));
  if (!peer) return false;
  openChat(peer);
  return true;
}

function renderPeer() {
  if (!active) return;
  const locked = !active.group && blocked.has(active.peer);
  $("composer").hidden = locked;
  $("blockBar").hidden = !locked;
  if (locked) {
    $("emojiPanel").hidden = true;
    clearReply();
    $("blockText").textContent = t("block.bar", { name: nameOf(active.peer) });
  }
  if (active.group) {
    $("hcalls").hidden = true;
    const g = active.data || {};
    const typers = Object.entries(g.typing || {}).filter(([k, v]) => v && k !== auth.currentUser.uid).map(([k]) => users.get(k)?.name).filter(Boolean);
    $("peerImg").src = pic({ name: g.name, photo: g.photo });
    $("peerAv").className = "av";
    $("peerName").textContent = g.name || "";
    $("peerStatus").classList.toggle("live", typers.length > 0);
    $("peerStatus").textContent = typers.length ? t("chat.typingMany", { names: typers.join(", ") }) : t("chat.members", { n: fmtNumber(g.members?.length || 0) });
    return;
  }
  const peer = users.get(active.peer);
  if (!peer) {
    $("hcalls").hidden = locked;
    $("peerAv").className = "av";
    $("peerName").textContent = "";
    $("peerStatus").classList.remove("live");
    $("peerStatus").textContent = "";
    watchUser(active.peer);
    return;
  }
  $("hcalls").hidden = locked;
  $("peerImg").src = pic(peer);
  $("peerAv").className = "av" + (peer.online ? " online" : "");
  $("peerName").textContent = peer.name || "";
  const status = $("peerStatus");
  const typing = active.data?.typing?.[active.peer];
  status.classList.toggle("live", !!typing || !!peer.online);
  if (typing) status.textContent = t("chat.typing");
  else if (peer.online) status.textContent = t("chat.online");
  else status.textContent = peer.lastSeen ? t("chat.lastSeen", { day: dayLabel(peer.lastSeen.toDate()), time: clock(peer.lastSeen) }) : t("chat.offline");
}

function renderMessages(all) {
  const uid = auth.currentUser.uid;
  const docs = all.filter(d => !(d.data().hiddenFor || []).includes(uid));
  const byId = new Map(all.map(d => [d.id, d.data()]));
  if (active.reply?.id && byId.get(active.reply.id)?.deleted) clearReply();
  const box = $("messages");
  const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 140;
  const prevHeight = box.scrollHeight;
  const prevTop = box.scrollTop;
  box.replaceChildren();
  let lastDay = "";
  let prevFrom = null;
  let hiddenRun = false;
  active.lastId = docs.length ? docs[docs.length - 1].id : null;

  docs.forEach(d => {
    const m = d.data({ serverTimestamps: "estimate" });
    const mine = m.from === uid;
    const queued = mine && d.metadata.hasPendingWrites && !d.data().at;
    const date = m.at?.toDate?.();
    if (date && date.toDateString() !== lastDay) {
      lastDay = date.toDateString();
      box.append(el("div", "day", dayLabel(date)));
      prevFrom = null;
    }
    if (active.group && !mine && blocked.has(m.from)) {
      if (!hiddenRun) box.append(el("div", "day hidden-note", t("block.hiddenMessage")));
      hiddenRun = true;
      prevFrom = null;
      return;
    }
    hiddenRun = false;
    if (m.callLog && typeof m.callLog === "object" && !m.deleted) {
      const log = m.callLog;
      const bad = !mine && ["missed", "cancelled"].includes(log.kind);
      const ev = el("div", "callev " + (mine ? "mine" : "theirs") + (bad ? " bad" : ""));
      ev.id = "m-" + d.id;
      ev.append(icon(mine ? "arrowOut" : "arrowIn", "dirico"), icon(log.video ? "video" : "phone"), el("span", "", callEventText(log, mine)), el("time", "", clock(m.at)));
      ev.onclick = () => {
        if (!active || active.group || $("hcalls").hidden) return;
        calls.startCall(!!log.video, active);
      };
      box.append(ev);
      prevFrom = null;
      return;
    }
    const b = el("div", "msg " + (mine ? "mine" : "theirs") + (prevFrom !== m.from ? " first" : ""));
    b.id = "m-" + d.id;
    prevFrom = m.from;
    if (active.group && !mine) {
      const sender = el("div", "sender", users.get(m.from)?.name || "?");
      sender.style.color = palette[[...m.from].reduce((a, ch) => a + ch.charCodeAt(0), 0) % palette.length];
      b.append(sender);
    }
    const rid = m.replyTo?.id;
    const inWindow = rid ? byId.get(rid) : null;
    const quoteGone = rid ? (inWindow ? !!inWindow.deleted : goneCache.get(active.id + "/" + rid) === true) : false;
    if (m.replyTo && !m.deleted && !quoteGone) {
      const q = el("div", "quote", m.replyTo.text);
      if (rid) {
        q.dataset.reply = rid;
        q.onclick = () => {
          const target = $("m-" + rid);
          if (!target) return;
          target.scrollIntoView({ behavior: "smooth", block: "center" });
          target.classList.add("flash");
          setTimeout(() => target.classList.remove("flash"), 1300);
        };
        if (!inWindow && !goneCache.has(active.id + "/" + rid)) checkGone(active.id, rid);
      }
      b.append(q);
    }

    if (m.deleted) {
      const gone = el("em", "gone");
      gone.append(icon("ban"), " " + t("chat.deleted"));
      b.append(gone);
    } else if (m.type === "image") {
      const img = el("img", "media");
      img.src = m.url;
      img.alt = t("common.photo");
      img.loading = "lazy";
      img.onclick = () => { $("lightbox").querySelector("img").src = m.url; $("lightbox").hidden = false; };
      b.append(img);
    } else if (m.type === "video") {
      const v = el("video");
      v.src = m.url;
      v.controls = true;
      v.preload = "metadata";
      b.append(v);
    } else if (m.type === "audio") {
      const a = el("audio");
      a.src = m.url;
      a.controls = true;
      b.append(a);
    } else if (m.type === "file") {
      const a = el("a", "file");
      a.append(icon("clip"), " " + (m.name || t("common.file")));
      a.href = m.url;
      a.target = "_blank";
      a.rel = "noopener";
      b.append(a);
    } else {
      const p = el("p");
      p.append(linkify(m.callLog ? callLogText(m.callLog) : m.text));
      b.append(p);
    }

    const picks = Object.values(m.reactions || {});
    if (picks.length && !m.deleted) {
      const bar = el("div", "reacts" + (m.reactions[uid] ? " own" : ""));
      bar.append(el("span", "", [...new Set(picks)].slice(0, 3).join("")));
      if (picks.length > 1) bar.append(el("small", "", String(picks.length)));
      b.append(bar);
      b.classList.add("hasreact");
    }
    const meta = el("div", "meta");
    meta.append(el("span", "", clock(m.at)));
    if (mine && !m.deleted) {
      const seen = m.status === "seen";
      const reached = seen || m.status === "delivered";
      const tick = el("span", seen ? "seen" : "");
      tick.append(icon(queued ? "clock" : reached ? "checks" : "check", "tick"));
      meta.append(tick);
    }
    b.append(meta);

    attachGestures(b, d, m, mine);
    box.append(b);
  });

  if (active.olderLoad) {
    box.scrollTop = box.scrollHeight - prevHeight + prevTop;
    active.olderLoad = false;
  } else if (nearBottom || active.first) {
    box.scrollTop = box.scrollHeight;
    if (docs.length) active.first = false;
  }
}

async function checkGone(chatId, rid) {
  const key = chatId + "/" + rid;
  goneCache.set(key, false);
  try {
    const s = await getDoc(doc(db, "chats", chatId, "messages", rid));
    const gone = !s.exists() || !!s.data().deleted;
    goneCache.set(key, gone);
    if (gone && active?.id === chatId) document.querySelectorAll(`.quote[data-reply="${rid}"]`).forEach(q => q.remove());
  } catch {
    goneCache.delete(key);
  }
}

async function hideForMe(d) {
  await updateDoc(d.ref, { hiddenFor: arrayUnion(auth.currentUser.uid) });
}

async function removeMessage(d) {
  const uid = auth.currentUser.uid;
  if (d.data().deleted) {
    await hideForMe(d);
    return;
  }
  const mine = d.data().from === uid;
  const options = [];
  if (mine) options.push({ label: t("msg.deleteForAll"), value: "all", kind: "dok" });
  options.push({ label: t("msg.deleteForMe"), value: "me", kind: mine ? "dsoft" : "dok" });
  options.push({ label: t("common.cancel"), value: null, kind: "dcancel" });
  const choice = await askChoice({
    title: t("msg.deleteTitle"),
    text: mine ? t("msg.deleteTextMine") : t("msg.deleteTextOther"),
    iconName: "trash",
    options
  });
  if (!choice) return;
  if (choice === "me") {
    await hideForMe(d);
    return;
  }
  const chatId = active.id;
  const wasLast = d.id === active.lastId;
  await updateDoc(d.ref, { deleted: true, text: "", url: "" });
  if (wasLast) await setDoc(doc(db, "chats", chatId), { lastMessage: stored("deleted") }, { merge: true });
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const area = document.createElement("textarea");
    area.value = text;
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.append(area);
    area.select();
    document.execCommand("copy");
    area.remove();
  }
  toast(t("chat.copied"));
}

function openMenu(d, m, mine) {
  const uid = auth.currentUser.uid;
  const reacts = $("menuReacts");
  reacts.replaceChildren();
  if (!m.deleted) {
    ["👍", "❤️", "😂", "😮", "🙏"].forEach(em => {
      const r = el("button", m.reactions?.[uid] === em ? "own" : "", em);
      r.onclick = () => { closeMenu(); react(d, em); };
      reacts.append(r);
    });
  }
  reacts.hidden = !!m.deleted;

  const prev = $("menuPreview");
  prev.className = "mbubble " + (mine ? "mine" : "theirs") + (m.deleted ? " gone" : "");
  const body = el("p", "", m.deleted ? t("chat.deleted") : preview(m));
  const time = el("small", "", (mine ? t("common.you") : users.get(m.from)?.name || "") + (m.at ? " · " + clock(m.at) : ""));
  prev.replaceChildren(body, time);

  const items = [];
  const tile = (name, label, fn, danger) => {
    const b = el("button", "tile" + (danger ? " danger" : ""));
    const wrap = el("span", "tico");
    wrap.append(icon(name));
    b.append(wrap, el("span", "tlabel", label));
    b.onclick = () => { closeMenu(); fn(); };
    items.push(b);
  };
  if (m.deleted) {
    tile("trash", t("msg.deleteForMe"), () => hideForMe(d), true);
  } else {
    tile("reply", t("msg.reply"), () => setReply(preview(m), d.id));
    const copyable = m.type === "text" || !m.type ? m.text : m.url;
    if (copyable) tile("copy", t("msg.copy"), () => copyText(copyable));
    tile("trash", t("msg.delete"), () => removeMessage(d), true);
    if (!mine) {
      tile("flag", t("report.message"), () => reportMessage(d, m), true);
      if (active?.group) tile("ban", t("block.action"), () => blockFlow(m.from), true);
    }
  }
  const grid = $("menuItems");
  grid.style.setProperty("--n", items.length);
  grid.replaceChildren(...items);
  menuBox.hidden = false;
}

function attachGestures(b, d, m, mine) {
  const ico = el("div", "swipe-ico");
  ico.append(icon("reply"));
  b.append(ico);
  let sx = 0, sy = 0, dx = 0, timer = null, swiping = false, tracking = false, fired = false;
  const cancelPress = () => { clearTimeout(timer); timer = null; };
  const reset = () => {
    b.style.transition = "transform .2s";
    b.style.transform = "";
    ico.style.opacity = 0;
    setTimeout(() => { b.style.transition = ""; }, 220);
  };
  const show = () => {
    cancelPress();
    fired = true;
    if (navigator.vibrate) navigator.vibrate(12);
    openMenu(d, m, mine);
  };
  b.addEventListener("pointerdown", e => {
    if (e.pointerType === "mouse") return;
    tracking = true;
    swiping = false;
    fired = false;
    sx = e.clientX;
    sy = e.clientY;
    dx = 0;
    cancelPress();
    timer = setTimeout(show, 450);
  });
  b.addEventListener("pointermove", e => {
    if (!tracking || fired) return;
    const mx = e.clientX - sx;
    const my = e.clientY - sy;
    if (!swiping && (Math.abs(mx) > 10 || Math.abs(my) > 10)) cancelPress();
    const dir = mine ? -1 : 1;
    if (!swiping && mx * dir > 12 && Math.abs(mx) > Math.abs(my) * 1.4 && !m.deleted) swiping = true;
    if (!swiping) return;
    dx = Math.max(0, Math.min(mx * dir, 90));
    b.style.transform = `translateX(${dx * dir}px)`;
    ico.style.opacity = Math.min(1, dx / 60);
    ico.classList.toggle("ready", dx >= 60);
  });
  const end = () => {
    if (!tracking) return;
    tracking = false;
    cancelPress();
    if (swiping) {
      if (dx >= 60) {
        if (navigator.vibrate) navigator.vibrate(10);
        setReply(preview(m), d.id);
      }
      swiping = false;
      ico.classList.remove("ready");
      reset();
    }
  };
  b.addEventListener("pointerup", end);
  b.addEventListener("pointercancel", end);
  b.addEventListener("contextmenu", e => {
    e.preventDefault();
    if (menuBox.hidden) show();
  });
  b.addEventListener("click", e => {
    if (fired || dx >= 12) {
      e.preventDefault();
      e.stopPropagation();
      fired = false;
      dx = 0;
    }
  }, true);
}

function setReply(text, id) {
  active.reply = { text, id };
  $("replyText").textContent = text;
  $("replyBar").hidden = false;
  $("input").focus();
}
function clearReply() {
  if (active) active.reply = null;
  $("replyBar").hidden = true;
}
$("replyCancel").onclick = clearReply;

async function send(payload, target = active) {
  if (!target) return;
  const uid = auth.currentUser.uid;
  const msg = { from: uid, type: "text", text: "", at: serverTimestamp(), status: "sent", ...payload };
  if (target.reply) msg.replyTo = { text: target.reply.text, id: target.reply.id };
  if (target === active) clearReply();
  const msgRef = doc(collection(db, "chats", target.id, "messages"));
  const batch = writeBatch(db);
  batch.set(msgRef, msg);
  batch.set(doc(db, "chats", target.id), {
    lastMessage: previewStored(msg),
    lastFrom: uid,
    lastAt: serverTimestamp(),
    typing: { [uid]: false },
    unread: Object.fromEntries(target.members.filter(m => m !== uid).map(m => [m, increment(1)]))
  }, { merge: true });
  inflight.add(msgRef.id);
  outboxAdd({ uid, chatId: target.id, messageId: msgRef.id });
  try {
    await batch.commit();
  } catch (err) {
    inflight.delete(msgRef.id);
    outboxDrop(msgRef.id);
    throw err;
  }
  triggerPush({ type: "message", chatId: target.id, messageId: msgRef.id });
  inflight.delete(msgRef.id);
  outboxDrop(msgRef.id);
}

const readOutbox = () => {
  try {
    const list = JSON.parse(localStorage.getItem(OUTBOX) || "[]");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
};
const writeOutbox = list => {
  try {
    localStorage.setItem(OUTBOX, JSON.stringify(list.slice(-200)));
  } catch {
    return;
  }
};
const outboxAdd = entry => writeOutbox([...readOutbox(), entry]);
const outboxDrop = id => writeOutbox(readOutbox().filter(x => x.messageId !== id));

async function flushOutbox() {
  const user = auth.currentUser;
  if (!user || deleting || flushing) return;
  flushing = true;
  try {
    await waitForPendingWrites(db);
    const all = readOutbox();
    const mine = all.filter(x => x.uid === user.uid && !inflight.has(x.messageId));
    writeOutbox(all.filter(x => x.uid === user.uid && inflight.has(x.messageId)));
    mine.forEach(x => triggerPush({ type: "message", chatId: x.chatId, messageId: x.messageId }));
  } catch {
    return;
  } finally {
    flushing = false;
  }
}

const syncNet = () => { $("offlineBar").hidden = navigator.onLine; };
addEventListener("online", () => {
  syncNet();
  flushOutbox();
});
addEventListener("offline", syncNet);
syncNet();

const imageSize = img => ({ w: img.naturalWidth || img.width, h: img.naturalHeight || img.height });

async function loadImage(file) {
  if (window.createImageBitmap) {
    try {
      return await createImageBitmap(file, { imageOrientation: "from-image" });
    } catch {
      return loadImageTag(file);
    }
  }
  return loadImageTag(file);
}

const loadImageTag = file => new Promise((resolve, reject) => {
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => {
    URL.revokeObjectURL(url);
    resolve(img);
  };
  img.onerror = () => {
    URL.revokeObjectURL(url);
    reject(new Error("image"));
  };
  img.src = url;
});

async function compressImage(file, maxSide = 1600, quality = 0.8) {
  if (!file.type.startsWith("image/") || /gif|svg/.test(file.type) || file.size < 150 * 1024) return file;
  try {
    const img = await loadImage(file);
    const { w: iw, h: ih } = imageSize(img);
    const scale = Math.min(1, maxSide / Math.max(iw, ih));
    const w = Math.max(1, Math.round(iw * scale));
    const h = Math.max(1, Math.round(ih * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, w, h);
    ctx.drawImage(img, 0, 0, w, h);
    if (img.close) img.close();
    const blob = await new Promise(resolve => canvas.toBlob(resolve, "image/jpeg", quality));
    if (!blob || blob.size >= file.size) return file;
    const base = (file.name || "photo").replace(/\.[^.]+$/, "");
    return new File([blob], base + ".jpg", { type: "image/jpeg" });
  } catch {
    return file;
  }
}

const syncComposer = () => {
  input.style.height = "auto";
  input.style.height = Math.min(input.scrollHeight, 130) + "px";
  const has = input.value.trim().length > 0;
  $("sendBtn").hidden = !has;
  $("micBtn").hidden = has;
};

const setTyping = value => {
  if (!active || isTyping === value) return;
  isTyping = value;
  setDoc(doc(db, "chats", active.id), { typing: { [auth.currentUser.uid]: value } }, { merge: true }).catch(() => {});
};

input.oninput = () => {
  syncComposer();
  setTyping(true);
  clearTimeout(typingTimer);
  typingTimer = setTimeout(() => setTyping(false), 1600);
};

async function submitText() {
  const text = input.value.trim();
  if (!text || !active) return;
  input.value = "";
  syncComposer();
  clearTimeout(typingTimer);
  isTyping = false;
  try {
    await send({ text });
  } catch (err) {
    toast(t(err?.code === "permission-denied" ? "chat.cannotSend" : "chat.sendFail"));
  }
}
input.onkeydown = e => {
  if (e.key === "Enter" && !e.shiftKey && !matchMedia("(pointer:coarse)").matches) {
    e.preventDefault();
    submitText();
  }
};
$("sendBtn").onclick = submitText;

const emojis = "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😜 🤪 😎 🤩 🥳 😏 😌 😴 🤔 🤗 🤭 🙄 😬 😢 😭 😤 😡 🥺 😱 🤯 😳 🤒 👍 👎 👏 🙌 🙏 💪 👋 🤝 ✌️ 🤞 👌 🔥 ✨ 🎉 💯 ❤️ 🧡 💛 💚 💙 💜 🖤 💔 💕 🎂 🎁 ☕ 🍕 🍔 🌹 🌙 ☀️ ⭐ ⚡".split(" ");
emojis.forEach(ch => {
  const b = el("button", "", ch);
  b.onclick = () => {
    input.value += ch;
    syncComposer();
    input.focus();
  };
  $("emojiPanel").append(b);
});
$("emojiBtn").onclick = () => { $("emojiPanel").hidden = !$("emojiPanel").hidden; };

async function upload(file) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("upload_preset", UPLOAD_PRESET);
  const res = await fetch(`https://api.cloudinary.com/v1_1/${CLOUD_NAME}/auto/upload`, { method: "POST", body: fd });
  if (!res.ok) throw new Error(t("chat.uploadFail"));
  return res.json();
}

$("attachBtn").onclick = () => $("fileInput").click();
$("fileInput").onchange = async e => {
  const file = e.target.files[0];
  e.target.value = "";
  if (!file || !active) return;
  const target = active;
  if (!navigator.onLine) {
    toast(t("chat.offlineMedia"));
    return;
  }
  toast(t("chat.uploading"), true);
  try {
    const prepared = await compressImage(file);
    const res = await upload(prepared);
    const type = res.resource_type === "image" ? "image" : res.resource_type === "video" ? (prepared.type.startsWith("audio") ? "audio" : "video") : "file";
    await send({ type, url: res.secure_url, name: prepared.name, size: prepared.size }, target);
    toast("");
  } catch (err) {
    toast(err.message);
  }
};

let recorder;
let chunks = [];
let recTick;
let recStart = 0;
let recCancelled = false;
const stopRecUi = () => {
  clearInterval(recTick);
  $("micBtn").classList.remove("rec");
  document.querySelector("#pane footer").classList.remove("recording");
};
$("recCancel").onclick = () => {
  recCancelled = true;
  recorder?.stop();
};
$("micBtn").onclick = async () => {
  if (recorder?.state === "recording") {
    recorder.stop();
    return;
  }
  if (!active) return;
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const target = active;
    chunks = [];
    recCancelled = false;
    recorder = new MediaRecorder(stream);
    recorder.ondataavailable = e => chunks.push(e.data);
    recorder.onstop = async () => {
      stream.getTracks().forEach(track => track.stop());
      stopRecUi();
      if (recCancelled) return;
      const blob = new Blob(chunks, { type: recorder.mimeType });
      if (!navigator.onLine) {
        toast(t("chat.offlineMedia"));
        return;
      }
      toast(t("chat.sendingVoice"), true);
      try {
        const res = await upload(new File([blob], "voice", { type: blob.type }));
        await send({ type: "audio", url: res.secure_url }, target);
        toast("");
      } catch (err) {
        toast(err.message);
      }
    };
    recorder.start();
    recStart = Date.now();
    $("recTime").textContent = "0:00";
    recTick = setInterval(() => {
      const s = Math.floor((Date.now() - recStart) / 1000);
      $("recTime").textContent = Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
    }, 500);
    $("micBtn").classList.add("rec");
    document.querySelector("#pane footer").classList.add("recording");
  } catch (err) {
    toast(t("chat.micPermission"));
  }
};

$("lightbox").onclick = () => { $("lightbox").hidden = true; };
syncComposer();

function linkify(text) {
  const frag = document.createDocumentFragment();
  text.split(/(https?:\/\/[^\s]+)/g).forEach((part, i) => {
    if (i % 2) {
      const a = el("a", "", part);
      a.href = part;
      a.target = "_blank";
      a.rel = "noopener";
      frag.append(a);
    } else {
      frag.append(part);
    }
  });
  return frag;
}

async function react(d, emoji) {
  const uid = auth.currentUser.uid;
  const current = d.data().reactions?.[uid];
  await updateDoc(d.ref, { [`reactions.${uid}`]: current === emoji ? deleteField() : emoji });
}

function notify(c, id) {
  if (!("Notification" in window) || Notification.permission !== "granted") return;
  const sender = users.get(c.lastFrom)?.name || "";
  const n = new Notification(c.group ? c.name : sender, { body: (c.group ? sender + ": " : "") + lastText(c.lastMessage), icon: "icon.svg", tag: id });
  n.onclick = () => {
    window.focus();
    n.close();
  };
}
document.addEventListener("click", () => {
  if (!isNative && "Notification" in window && Notification.permission === "default") {
    Notification.requestPermission().then(p => {
      if (p === "granted") registerPush();
    });
  }
}, { once: true });

async function savePushToken(token) {
  if (!token || !auth.currentUser) return;
  localStorage.setItem("kotha-push", token);
  await setDoc(doc(db, "pushTokens", auth.currentUser.uid), { tokens: arrayUnion(token) }, { merge: true }).catch(() => {});
}

async function registerPush(requestPermission = false) {
  if (isNative) {
    const token = await registerNativePush({ requestPermission, onToken: savePushToken }).catch(() => null);
    await savePushToken(token);
    return;
  }
  if (VAPID_KEY.startsWith("YOUR_") || !("Notification" in window) || !("serviceWorker" in navigator)) return;
  if (Notification.permission !== "granted" || !auth.currentUser) return;
  try {
    if (!(await isSupported())) return;
    const reg = await navigator.serviceWorker.ready;
    const token = await getToken(getMessaging(app), { vapidKey: VAPID_KEY, serviceWorkerRegistration: reg });
    await savePushToken(token);
  } catch (err) {
    return;
  }
}

async function unregisterPush() {
  const token = localStorage.getItem("kotha-push");
  if (!token || !auth.currentUser) return;
  localStorage.removeItem("kotha-push");
  await setDoc(doc(db, "pushTokens", auth.currentUser.uid), { tokens: arrayRemove(token) }, { merge: true }).catch(() => {});
}

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.addEventListener("message", e => {
    const id = e.data?.chatId;
    if (id && !openChatById(id)) pendingChat = id;
  });
}

$("messages").onscroll = () => {
  const box = $("messages");
  $("fab").hidden = box.scrollHeight - box.scrollTop - box.clientHeight < 300;
  if (box.scrollTop < 80 && active?.hasMore && !active.olderLoad) {
    active.olderLoad = true;
    active.limit += PAGE;
    listenMessages();
  }
};
$("fab").onclick = () => $("messages").scrollTo({ top: $("messages").scrollHeight, behavior: "smooth" });

let groupExtra = new Map();
let groupPicked = new Set();
let groupFindTimer;
let groupFindToken = 0;
const groupUser = id => users.get(id) || groupExtra.get(id);

async function lookupEmail(term) {
  try {
    const hit = await getDoc(doc(db, "emailLookup", term));
    if (!hit.exists()) return { status: "none" };
    if (hit.data().uid === auth.currentUser.uid) return { status: "self" };
    const p = await getDoc(doc(db, "users", hit.data().uid));
    if (!p.exists()) return { status: "none" };
    if (blocked.has(p.id)) return { status: "blocked" };
    users.set(p.id, p.data());
    watchUser(p.id);
    return { status: "found", user: p.data() };
  } catch {
    return { status: "error" };
  }
}

function groupContacts() {
  const me = auth.currentUser.uid;
  const ids = new Set(chats.filter(c => !c.group).map(c => c.members.find(m => m !== me)));
  groupExtra.forEach((u, id) => ids.add(id));
  return [...ids].filter(id => !blocked.has(id)).map(id => users.get(id) || groupExtra.get(id)).filter(Boolean);
}

let lastPicked = 0;

function syncGroupUi() {
  const strip = $("picked");
  strip.replaceChildren();
  groupPicked.forEach(id => {
    const u = groupUser(id);
    if (!u) return;
    const pk = el("div", "pk");
    const img = el("img");
    img.src = pic(u);
    img.alt = "";
    const x = el("i");
    x.append(icon("close"));
    pk.append(img, x, el("span", "", u.name || t("common.user")));
    pk.onclick = () => {
      groupPicked.delete(id);
      renderMembers();
    };
    strip.append(pk);
  });
  strip.hidden = !groupPicked.size;
  $("pickedSec").hidden = !groupPicked.size;
  if (groupPicked.size > lastPicked) requestAnimationFrame(() => strip.scrollTo({ left: strip.scrollWidth, behavior: "smooth" }));
  lastPicked = groupPicked.size;
  $("sheetCount").textContent = groupPicked.size ? t("group.selected", { n: fmtNumber(groupPicked.size) }) : t("group.pickMembers");
  $("sheetDone").classList.toggle("off", !($("groupName").value.trim() && groupPicked.size));
}

function renderMembers() {
  const box = $("members");
  box.replaceChildren();
  const contacts = groupContacts();
  $("membersLabel").hidden = !contacts.length;
  if (!contacts.length) box.append(el("p", "hint", t("group.noContacts")));
  contacts.forEach(u => {
    const row = el("div", "pick" + (groupPicked.has(u.uid) ? " on" : ""));
    const img = el("img");
    img.src = pic(u);
    img.alt = "";
    const tick = el("span", "tickbox");
    tick.append(icon("check"));
    row.append(img, el("span", "n", u.name || t("common.user")), tick);
    row.onclick = () => {
      if (groupPicked.has(u.uid)) groupPicked.delete(u.uid);
      else groupPicked.add(u.uid);
      renderMembers();
    };
    box.append(row);
  });
  syncGroupUi();
}

function renderGroupFind(r) {
  const box = $("groupFindResult");
  box.replaceChildren();
  if (!r) return;
  const notes = {
    invalid: t("find.invalid"),
    loading: t("find.loading"),
    self: t("find.self"),
    error: t("find.error"),
    none: t("find.none"),
    blocked: t("block.inGroup")
  };
  if (r.status !== "found") {
    box.append(el("p", "hint", notes[r.status]));
    return;
  }
  const u = r.user;
  const already = groupPicked.has(u.uid);
  const row = el("div", "pick found");
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  row.append(img, el("span", "n", u.name || t("common.user")), el("span", "addpill" + (already ? " done" : ""), already ? t("find.added") : t("find.add")));
  row.onclick = () => {
    groupExtra.set(u.uid, u);
    groupPicked.add(u.uid);
    $("groupFind").value = "";
    groupFindToken++;
    renderGroupFind(null);
    renderMembers();
  };
  box.append(row);
}

$("groupFind").oninput = () => {
  clearTimeout(groupFindTimer);
  const term = $("groupFind").value.trim().toLowerCase();
  const token = ++groupFindToken;
  if (!term) {
    renderGroupFind(null);
    return;
  }
  if (!emailRe.test(term)) {
    renderGroupFind({ status: "invalid" });
    return;
  }
  renderGroupFind({ status: "loading" });
  groupFindTimer = setTimeout(async () => {
    const r = await lookupEmail(term);
    if (token === groupFindToken) renderGroupFind(r);
  }, 300);
};

const openGroupSheet = () => {
  $("groupName").value = "";
  $("groupFind").value = "";
  groupExtra = new Map();
  groupPicked = new Set();
  lastPicked = 0;
  groupFindToken++;
  renderGroupFind(null);
  renderMembers();
  $("sheet").querySelector(".gbody").scrollTop = 0;
  $("sheet").hidden = false;
};
$("groupName").oninput = syncGroupUi;
$("sheetClose").onclick = () => { $("sheet").hidden = true; };
$("sheetDone").onclick = async () => {
  const uid = auth.currentUser.uid;
  const name = $("groupName").value.trim();
  const picked = [...groupPicked];
  if (!name || !picked.length) {
    toast(t("group.needName"));
    return;
  }
  const members = [uid, ...picked];
  const ref = await addDoc(collection(db, "chats"), { group: true, name, admin: uid, members, lastMessage: stored("groupCreated"), lastFrom: uid, lastAt: serverTimestamp() });
  $("sheet").hidden = true;
  openChat(null, { id: ref.id, name, members });
};

const peerIdOf = (c, uid) => c.members.find(m => m !== uid);
const nameOf = id => users.get(id)?.name || t("common.user");
const blockedRef = id => doc(db, "users", auth.currentUser.uid, "blocked", id);

function refreshBlockedUi() {
  renderList();
  if (active) {
    renderPeer();
    if (lastMessageDocs) renderMessages(lastMessageDocs);
  }
  if (!$("settingsSheet").hidden) renderBlockedList();
}

async function blockFlow(id) {
  if (!id || id === auth.currentUser.uid || blocked.has(id)) return;
  const name = nameOf(id);
  const ok = await askConfirm({ title: t("block.confirmTitle", { name }), text: t("block.confirmText"), ok: t("block.action"), iconName: "ban" });
  if (!ok) return;
  setDoc(blockedRef(id), { at: serverTimestamp() }).catch(() => toast(t("block.fail")));
  toast(t("block.done", { name }));
}

function unblockFlow(id) {
  deleteDoc(blockedRef(id)).catch(() => toast(t("block.unblockFail")));
  toast(t("block.unblocked", { name: nameOf(id) }));
}

$("unblockBtn").onclick = () => {
  if (active && !active.group) unblockFlow(active.peer);
};

const REASONS = ["spam", "harassment", "hate", "sexual", "violence", "scam", "other"];
let reportCtx = null;
let reportReason = "";

function renderReasons() {
  $("reportReasons").replaceChildren(...REASONS.map(key => {
    const b = el("button", "reason" + (reportReason === key ? " on" : ""));
    b.type = "button";
    b.setAttribute("role", "radio");
    b.setAttribute("aria-checked", String(reportReason === key));
    b.append(el("span", "radio"), el("span", "", t("report.reason." + key)));
    b.onclick = () => {
      reportReason = key;
      renderReasons();
      $("reportSubmit").disabled = false;
    };
    return b;
  }));
}

function openReport(ctx) {
  reportCtx = ctx;
  reportReason = "";
  $("reportTitle").textContent = ctx.title;
  $("reportNote").value = "";
  $("reportBlock").checked = false;
  $("reportBlockRow").hidden = !ctx.targetUid || blocked.has(ctx.targetUid);
  $("reportSubmit").disabled = true;
  renderReasons();
  $("reportBox").hidden = false;
  $("reportBox").querySelector(".dialog").scrollTop = 0;
}

function closeReport() {
  $("reportBox").hidden = true;
  reportCtx = null;
}

$("reportCancel").onclick = closeReport;
$("reportBox").onclick = e => { if (e.target === $("reportBox")) closeReport(); };
$("reportSubmit").onclick = () => {
  const ctx = reportCtx;
  if (!ctx || !reportReason || !auth.currentUser) return;
  const data = { reporter: auth.currentUser.uid, type: ctx.type, reason: reportReason, chatId: ctx.chatId, createdAt: serverTimestamp() };
  const note = $("reportNote").value.trim().slice(0, 500);
  if (note) data.note = note;
  if (ctx.targetUid) data.reportedUid = ctx.targetUid;
  if (ctx.messageId) data.messageId = ctx.messageId;
  if (ctx.content) data.content = String(ctx.content).slice(0, 1500);
  if (ctx.contentType) data.contentType = ctx.contentType;
  const alsoBlock = !$("reportBlockRow").hidden && $("reportBlock").checked && ctx.targetUid;
  closeReport();
  addDoc(collection(db, "reports"), data).catch(() => toast(t("report.fail")));
  if (alsoBlock) setDoc(blockedRef(ctx.targetUid), { at: serverTimestamp() }).catch(() => {});
  toast(t("report.sent"));
};

function reportUser(id) {
  if (!active) return;
  openReport({ type: "user", chatId: active.id, targetUid: id, title: t("report.titleUser", { name: nameOf(id) }) });
}

function reportGroup() {
  if (!active) return;
  openReport({ type: "group", chatId: active.id, title: t("report.titleGroup") });
}

function reportMessage(d, m) {
  if (!active) return;
  const media = m.type && m.type !== "text";
  openReport({
    type: "message",
    chatId: active.id,
    messageId: d.id,
    targetUid: m.from,
    content: media ? m.url || "" : m.text || "",
    contentType: m.type || "text",
    title: t("report.titleMessage")
  });
}

async function leaveGroup() {
  if (!active?.group) return;
  const ok = await askConfirm({ title: t("group.leaveTitle"), text: t("group.leaveText"), ok: t("group.leave"), iconName: "logout" });
  if (!ok || !active?.group) return;
  const id = active.id;
  closeChat();
  renderList();
  updateDoc(doc(db, "chats", id), { members: arrayRemove(auth.currentUser.uid) }).catch(() => toast(t("group.leaveFail")));
  toast(t("group.left"));
}

const chatMenu = $("chatMenu");

const pushRef = () => doc(db, "pushTokens", auth.currentUser.uid);

async function setMute(id, until) {
  const previous = muted;
  muted = { ...muted };
  if (until) muted[id] = until;
  else delete muted[id];
  renderList();
  try {
    await setDoc(pushRef(), { muted: { [id]: until || deleteField() } }, { merge: true });
    toast(t(until ? "mute.done" : "mute.undone"));
  } catch {
    muted = previous;
    renderList();
    toast(t("mute.fail"));
  }
}

async function muteFlow(id) {
  if (!id || !auth.currentUser) return;
  if (isMuted(id)) {
    setMute(id, 0);
    return;
  }
  const spans = [8 * 3600000, 7 * 24 * 3600000, 0];
  const choice = await askChoice({
    title: t("mute.title"),
    text: t("mute.text"),
    iconName: "bellOff",
    options: [
      { label: t("mute.8h"), value: 1, kind: "dsoft" },
      { label: t("mute.1w"), value: 2, kind: "dsoft" },
      { label: t("mute.always"), value: 3, kind: "dsoft" },
      { label: t("common.cancel"), value: null, kind: "dcancel" }
    ]
  });
  if (!choice) return;
  const span = spans[choice - 1];
  setMute(id, span ? Date.now() + span : FOREVER);
}

function closeChatMenu() {
  chatMenu.hidden = true;
  $("chatMenuBtn").setAttribute("aria-expanded", "false");
}

function openChatMenu() {
  if (!active) return;
  const items = [];
  if (active.group) {
    items.push({ icon: "flag", label: t("report.group"), fn: reportGroup });
    items.push({ icon: "logout", label: t("group.leave"), fn: leaveGroup, danger: true });
  } else {
    const id = active.peer;
    if (blocked.has(id)) items.push({ icon: "ban", label: t("block.unblockUser"), fn: () => unblockFlow(id) });
    else items.push({ icon: "ban", label: t("block.user"), fn: () => blockFlow(id), danger: true });
    items.push({ icon: "flag", label: t("report.user"), fn: () => reportUser(id) });
  }
  const silenced = isMuted(active.id);
  items.unshift({ icon: silenced ? "bell" : "bellOff", label: t(silenced ? "mute.unmute" : "mute.action"), fn: () => muteFlow(active.id) });
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

$("chatMenuBtn").onclick = () => chatMenu.hidden ? openChatMenu() : closeChatMenu();
document.addEventListener("click", e => {
  if (!chatMenu.hidden && !e.target.closest("#chatMenu, #chatMenuBtn")) closeChatMenu();
});

const legalHref = page => (isNative ? API_BASE + "/" : "") + page + ".html?lang=" + getLang();

function setLegalLinks() {
  document.querySelectorAll("[data-legal]").forEach(a => { a.href = legalHref(a.dataset.legal); });
}

function renderBlockedList() {
  const box = $("blockedList");
  box.replaceChildren();
  if (!blocked.size) {
    box.append(el("p", "hint", t("settings.noBlocked")));
    return;
  }
  [...blocked].forEach(id => {
    const u = users.get(id);
    const r = el("div", "pick found blockedrow");
    const img = el("img");
    img.src = pic(u || { name: t("common.user") });
    img.alt = "";
    const btn = el("button", "addpill", t("block.unblock"));
    btn.onclick = () => unblockFlow(id);
    r.append(img, el("span", "n", u?.name || t("common.user")), btn);
    box.append(r);
  });
}

function openSettings() {
  renderBlockedList();
  setLegalLinks();
  $("settingsSheet").hidden = false;
  $("settingsSheet").querySelector(".gbody").scrollTop = 0;
}

function closeSettings() {
  $("settingsSheet").hidden = true;
}

$("settingsClose").onclick = closeSettings;
$("deleteAccountBtn").onclick = () => openDelete();

const providerIds = () => (auth.currentUser?.providerData || []).map(p => p.providerId);
let deleteBusy = false;
let deleteMethod = "password";

function setDeleteBusy(on) {
  deleteBusy = on;
  ["deleteOk", "deleteCancel", "deleteGoogle", "deletePassword"].forEach(id => { $(id).disabled = on; });
  $("deleteOk").textContent = on ? t("delete.working") : t(deleteMethod === "password" ? "delete.confirm" : "delete.confirmGoogle");
}

function openDelete() {
  const ids = providerIds();
  const usesPassword = ids.includes("password");
  const usesGoogle = ids.includes("google.com");
  deleteMethod = usesPassword || !usesGoogle ? "password" : "google";
  const showPassword = deleteMethod === "password";
  $("deleteText").textContent = t("delete.text") + " " + t(showPassword ? "delete.textPassword" : "delete.textGoogle");
  $("deletePassword").hidden = !showPassword;
  $("deletePassword").value = "";
  $("deleteGoogle").hidden = !(showPassword && usesGoogle);
  $("deleteErr").textContent = "";
  setDeleteBusy(false);
  $("deleteBox").hidden = false;
  (showPassword ? $("deletePassword") : $("deleteCancel")).focus();
}

function closeDelete() {
  if (deleteBusy) return;
  $("deleteBox").hidden = true;
  $("deletePassword").value = "";
}

async function reauthenticate(method, password) {
  const user = auth.currentUser;
  if (method === "password") {
    await reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, password));
  } else if (isNative) {
    const idToken = await nativeGoogleIdToken();
    await reauthenticateWithCredential(user, GoogleAuthProvider.credential(idToken));
  } else {
    await reauthenticateWithPopup(user, new GoogleAuthProvider());
  }
}

async function runDelete(method) {
  if (deleteBusy || !auth.currentUser) return;
  const password = $("deletePassword").value;
  if (method === "password" && !password) {
    $("deleteErr").textContent = t("delete.needPassword");
    return;
  }
  $("deleteErr").textContent = "";
  setDeleteBusy(true);
  try {
    await reauthenticate(method, password);
  } catch (err) {
    setDeleteBusy(false);
    const code = err?.code || "";
    if (/wrong-password|invalid-credential|invalid-login/.test(code)) $("deleteErr").textContent = t("delete.wrongPassword");
    else if (!/cancel|closed|popup/i.test(code + " " + (err?.message || ""))) $("deleteErr").textContent = t("delete.failed");
    return;
  }
  deleting = true;
  const uid = auth.currentUser.uid;
  closeChat();
  calls.stop();
  try {
    await unregisterPush();
    const token = await auth.currentUser.getIdToken(true);
    const res = await fetch(API_BASE + "/api/delete-account", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + token },
      body: "{}",
      cache: "no-store"
    });
    if (!res.ok) {
      const j = await res.json().catch(() => ({}));
      throw new Error(j.error || "server");
    }
  } catch (err) {
    deleting = false;
    setDeleteBusy(false);
    if (auth.currentUser) {
      calls.start(uid);
      registerPush();
      setPresence(true);
    }
    $("deleteErr").textContent = t(err.message === "reauth" ? "delete.reauth" : "delete.failed");
    return;
  }
  try {
    sessionStorage.setItem("kotha-deleted", "1");
  } catch {
    localStorage.removeItem("kotha-deleted");
  }
  localStorage.removeItem("kotha-session");
  localStorage.removeItem("kotha-push");
  await signOut(auth).catch(() => {});
  try {
    await terminate(db);
    await clearIndexedDbPersistence(db);
  } catch {
    localStorage.removeItem("kotha-deleted");
  }
  location.reload();
}

$("deleteOk").onclick = () => runDelete(deleteMethod);
$("deleteGoogle").onclick = () => runDelete("google");
$("deleteCancel").onclick = closeDelete;
$("deleteBox").onclick = e => { if (e.target === $("deleteBox")) closeDelete(); };
$("deletePassword").onkeydown = e => {
  if (e.key === "Enter") {
    e.preventDefault();
    runDelete("password");
  }
};

function showTermsGate() {
  setLegalLinks();
  $("termsGate").hidden = false;
  $("termsAgree").focus();
}

$("termsAgree").onclick = () => {
  if (!auth.currentUser) return;
  $("termsGate").hidden = true;
  setDoc(doc(db, "users", auth.currentUser.uid), { termsVersion: TERMS_VERSION, termsAt: serverTimestamp() }, { merge: true }).catch(() => showTermsGate());
};

$("termsDecline").onclick = async () => {
  $("termsGate").hidden = true;
  await unregisterPush();
  await setPresence(false);
  await signOut(auth);
};

$("deleteIcon").replaceChildren(icon("trash"));
$("termsIcon").replaceChildren(icon("shield"));
setLegalLinks();

try {
  if (sessionStorage.getItem("kotha-deleted") === "1") {
    sessionStorage.removeItem("kotha-deleted");
    toast(t("delete.done"));
  }
} catch {
  localStorage.removeItem("kotha-deleted");
}

const calls = createCalls({
  auth,
  db,
  push: triggerPush,
  fs: { collection, doc, getDoc, setDoc, updateDoc, addDoc, onSnapshot, query, where, serverTimestamp },
  $,
  toast,
  pic,
  getActive: () => active,
  getUsers: () => users,
  send,
  askChoice
});

if (!isNative && "serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});

document.addEventListener("keydown", e => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    closeSearchFocus();
    return;
  }
  if (e.key !== "Escape") return;
  if (!$("lightbox").hidden) $("lightbox").hidden = true;
  else if (!$("deleteBox").hidden) closeDelete();
  else if (!$("reportBox").hidden) closeReport();
  else if (!$("settingsSheet").hidden) closeSettings();
  else if (!$("findSheet").hidden) closeFind();
  else if (!$("sheet").hidden) $("sheet").hidden = true;
  else if (!$("emojiPanel").hidden) $("emojiPanel").hidden = true;
  else if (active) closeChat();
});

const openChatFromNative = id => {
  if (id && !openChatById(id)) pendingChat = id;
};

const handleBack = () => {
  if (!$("termsGate").hidden) return true;
  if (!$("deleteBox").hidden) { closeDelete(); return true; }
  if (!$("reportBox").hidden) { closeReport(); return true; }
  if (!$("confirm").hidden) { closeConfirm(false); return true; }
  if (!$("choice").hidden) { closeChoice(null); return true; }
  if (!chatMenu.hidden) { closeChatMenu(); return true; }
  if (!$("settingsSheet").hidden) { closeSettings(); return true; }
  if (!$("msgMenu").hidden) { closeMenu(); return true; }
  if (!moreMenu.hidden) { closeMore(); return true; }
  if (!$("lightbox").hidden) { $("lightbox").hidden = true; return true; }
  if (!$("findSheet").hidden) { closeFind(); return true; }
  if (!$("sheet").hidden) { $("sheet").hidden = true; return true; }
  if (!$("emojiPanel").hidden) { $("emojiPanel").hidden = true; return true; }
  const callState = calls.busy();
  if (callState) {
    toast(callState === "in" ? t("nav.answerOrDecline") : t("nav.callInProgress"));
    return true;
  }
  if (!$("replyBar").hidden) { clearReply(); return true; }
  if (active) { closeChat(); renderList(); return true; }
  return false;
};

setupNative({
  db,
  getDoc,
  doc,
  handleBack,
  openChat: openChatFromNative,
  notify: text => toast(text),
  onResume: () => setActiveChat(active?.id || null)
});

consumePendingChat().then(openChatFromNative);

function closeSearchFocus() {
  $("search").focus();
  $("search").select();
}
