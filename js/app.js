import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { initializeAuth, indexedDBLocalPersistence, browserLocalPersistence, browserPopupRedirectResolver, onAuthStateChanged, createUserWithEmailAndPassword, signInWithEmailAndPassword, signInWithPopup, GoogleAuthProvider, signOut, updateProfile } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { getMessaging, getToken, isSupported } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging.js";
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager, collection, doc, getDoc, setDoc, updateDoc, addDoc, onSnapshot, query, orderBy, where, limit, serverTimestamp, arrayUnion, arrayRemove, increment, writeBatch, deleteField } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

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
const clock = ts => ts?.toDate ? ts.toDate().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "";
const dayLabel = d => {
  const t = new Date();
  if (d.toDateString() === t.toDateString()) return "আজ";
  t.setDate(t.getDate() - 1);
  if (d.toDateString() === t.toDateString()) return "গতকাল";
  return d.toLocaleDateString("bn-BD", { day: "numeric", month: "long" });
};
const listTime = ts => {
  if (!ts?.toDate) return "";
  const d = ts.toDate();
  return d.toDateString() === new Date().toDateString() ? clock(ts) : d.toLocaleDateString([], { day: "numeric", month: "short" });
};
const preview = m => m.deleted ? "মেসেজ মুছে ফেলা হয়েছে" : ({ image: "ছবি", video: "ভিডিও", audio: "ভয়েস মেসেজ", file: m.name || "ফাইল" }[m.type] || m.text);

const icons = {
  ban: '<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6l12.8 12.8"/>',
  clip: '<path d="M20 11.5l-8 8a5 5 0 0 1-7-7l8.5-8.5a3.5 3.5 0 0 1 5 5L10 17.5a2 2 0 0 1-3-3l7.5-7.5"/>',
  reply: '<path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 6 6v3"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12M9 7V4h6v3"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  checks: '<path d="M2 12.5l4.5 4.5L15 8M10 15.5l1.5 1.5L21 7.5"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
  logout: '<path d="M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 16l-4-4 4-4M6 12h10"/>'
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
let isTyping = false;
let typingTimer;
let chatsReady = false;
let msgUnsub = null;
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

const hadSession = localStorage.getItem("kotha-session") === "1";
const hideSplash = () => {
  clearTimeout(splashTimer);
  $("splash").hidden = true;
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

$("themeBtn").onclick = () => {
  const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = next;
  localStorage.setItem("theme", next);
};

$("switchLink").onclick = e => {
  e.preventDefault();
  signup = !signup;
  $("name").hidden = !signup;
  $("authBtn").textContent = signup ? "অ্যাকাউন্ট খুলুন" : "সাইন ইন করুন";
  $("switchText").textContent = signup ? "আগে থেকেই অ্যাকাউন্ট আছে?" : "নতুন ব্যবহারকারী?";
  $("switchLink").textContent = signup ? "সাইন ইন করুন" : "অ্যাকাউন্ট খুলুন";
};

$("authForm").onsubmit = async e => {
  e.preventDefault();
  $("authErr").textContent = "";
  const btn = $("authBtn");
  const label = btn.textContent;
  btn.disabled = true;
  btn.textContent = "অপেক্ষা করুন…";
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
    await signInWithPopup(auth, new GoogleAuthProvider());
  } catch (err) {
    $("authErr").textContent = err.code.replace("auth/", "").replace(/-/g, " ");
  }
};

$("logoutBtn").onclick = async () => {
  const ok = await askConfirm({ title: "সাইন আউট করবেন?", text: "আপনি এই ডিভাইস থেকে সাইন আউট হয়ে যাবেন। আবার সাইন ইন করে চ্যাটে ফিরতে পারবেন।", ok: "সাইন আউট", iconName: "logout" });
  if (!ok) return;
  await unregisterPush();
  await setPresence(false);
  await signOut(auth);
};

const setPresence = on => auth.currentUser
  ? updateDoc(doc(db, "users", auth.currentUser.uid), { online: on, lastSeen: serverTimestamp() }).catch(() => {})
  : Promise.resolve();

document.addEventListener("visibilitychange", () => setPresence(!document.hidden));
addEventListener("beforeunload", () => setPresence(false));

onAuthStateChanged(auth, async user => {
  unsubs.forEach(u => u());
  unsubs = [];
  if (!user) {
    users = new Map();
    chats = [];
    usersLoaded = false;
    chatsReady = false;
    lastAtSeen.clear();
    closeChat();
    $("app").hidden = true;
    $("auth").hidden = false;
    $("authBtn").disabled = false;
    $("authBtn").textContent = signup ? "অ্যাকাউন্ট খুলুন" : "সাইন ইন করুন";
    localStorage.removeItem("kotha-session");
    hideSplash();
    return;
  }
  localStorage.setItem("kotha-session", "1");
  showSplash();
  $("auth").hidden = true;
  usersLoaded = false;
  chatsReady = false;
  users = new Map();
  searchState = { term: "", status: "idle", user: null };
  const ref = doc(db, "users", user.uid);
  try {
    const snap = await getDoc(ref);
    if (!snap.exists()) {
      await setDoc(ref, { uid: user.uid, name: pendingName || user.displayName || (user.email || "").split("@")[0] || "ব্যবহারকারী", photo: user.photoURL || "" }, { merge: true });
    } else if ("email" in snap.data()) {
      await updateDoc(ref, { email: deleteField() });
    }
    const mail = (user.email || "").toLowerCase();
    if (mail) {
      const lref = doc(db, "emailLookup", mail);
      const lsnap = await getDoc(lref);
      if (!lsnap.exists() || lsnap.data().uid !== user.uid) await setDoc(lref, { uid: user.uid });
    }
  } catch (err) {
    hideSplash();
    toast("লোড করা যায়নি, ইন্টারনেট সংযোগ দেখুন");
  }
  $("app").hidden = false;
  registerPush();
  setPresence(true);
  unsubs.push(() => {
    userWatch.forEach(u => u());
    userWatch.clear();
  });
  watchUser(user.uid);
  unsubs.push(onSnapshot(query(collection(db, "chats"), where("members", "array-contains", user.uid)), s => {
    const initial = !chatsReady;
    chatsReady = true;
    s.docChanges().forEach(ch => {
      const c = ch.doc.data();
      const t = c.lastAt?.seconds || 0;
      const prev = lastAtSeen.get(ch.doc.id) || 0;
      lastAtSeen.set(ch.doc.id, t);
      if (initial || !t || t <= prev || c.lastFrom === user.uid) return;
      if (document.hidden || active?.id !== ch.doc.id) notify(c, ch.doc.id);
    });
    chats = s.docs.map(d => ({ id: d.id, ...d.data() }));
    chats.flatMap(c => c.members || []).forEach(watchUser);
    renderList();
    checkReady();
  }, hideSplash));
});

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

async function runSearch(term) {
  searchState = { term, status: "loading", user: null };
  renderList();
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
  renderList();
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
  toast("ছবি আপলোড হচ্ছে…", true);
  try {
    const res = await upload(file);
    await updateDoc(doc(db, "users", auth.currentUser.uid), { photo: res.secure_url });
    toast("প্রোফাইল ছবি বদলানো হয়েছে");
  } catch (err) {
    toast(err.message);
  }
};

function row(u, sub, time, unread, fn, isActive) {
  const r = el("div", "row" + (isActive ? " active" : "") + (unread > 0 ? " unread" : ""));
  const av = el("div", "av" + (u.online ? " online" : ""));
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  av.append(img);
  const body = el("div", "body");
  const top = el("div", "top");
  top.append(el("b", "", u.name || "ব্যবহারকারী"), el("time", "", time));
  const bot = el("div", "bot");
  bot.append(el("span", "sub", sub));
  if (unread > 0) bot.append(el("span", "badge", unread > 99 ? "99+" : String(unread)));
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
  const totalUnread = chats.reduce((n, c) => n + (c.unread?.[uid] || 0), 0);
  document.title = (totalUnread ? `(${totalUnread}) ` : "") + "কথা";
  $("railBadge").hidden = !totalUnread;
  $("railBadge").textContent = totalUnread > 99 ? "99+" : totalUnread;

  if (term) {
    const s = searchState;
    if (!emailRe.test(term)) {
      list.append(el("p", "hint", "কাউকে খুঁজতে তার পুরো ইমেইল ঠিকানা লিখুন, যেমন name@example.com"));
    } else if (s.term !== term || s.status === "idle" || s.status === "loading") {
      list.append(el("p", "hint", "খোঁজা হচ্ছে…"));
    } else if (s.status === "found") {
      list.append(row(s.user, term, "", 0, () => {
        $("search").value = "";
        searchState = { term: "", status: "idle", user: null };
        openChat(s.user);
      }));
    } else if (s.status === "self") {
      list.append(el("p", "hint", "এটি আপনার নিজের ইমেইল।"));
    } else if (s.status === "error") {
      list.append(el("p", "hint", "খুঁজতে সমস্যা হয়েছে, আবার চেষ্টা করুন।"));
    } else {
      list.append(el("p", "hint", "এই ইমেইলে কাউকে পাওয়া যায়নি।"));
    }
    return;
  }

  const talked = new Set();
  [...chats]
    .filter(c => c.lastMessage)
    .filter(c => filter === "all" || (filter === "group" ? c.group : (c.unread?.[uid] || 0) > 0))
    .sort((a, b) => (b.lastAt?.seconds || 0) - (a.lastAt?.seconds || 0))
    .forEach(c => {
      if (c.group) {
        const g = { uid: c.id, name: c.name, photo: c.photo || "" };
        const who = c.lastFrom === uid ? "আপনি: " : (users.get(c.lastFrom)?.name || "") + ": ";
        list.append(row(g, who + c.lastMessage, listTime(c.lastAt), c.unread?.[uid] || 0, () => openChat(null, c), active?.id === c.id));
        return;
      }
      const peer = users.get(c.members.find(m => m !== uid));
      if (!peer) return;
      talked.add(peer.uid);
      const sub = (c.lastFrom === uid ? "আপনি: " : "") + c.lastMessage;
      list.append(row(peer, sub, listTime(c.lastAt), c.unread?.[uid] || 0, () => openChat(peer), active?.peer === peer.uid));
    });

  if (!list.children.length) list.append(el("p", "hint", filter === "all" ? "কথা শুরু করতে উপরের সার্চ বক্সে বন্ধুর পুরো ইমেইল ঠিকানা লিখুন।" : "কোনো চ্যাট নেই।"));
}
$("search").oninput = () => {
  clearTimeout(searchTimer);
  const term = $("search").value.trim().toLowerCase();
  if (emailRe.test(term)) searchTimer = setTimeout(() => runSearch(term), 300);
  else searchState = { term, status: "idle", user: null };
  renderList();
};
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
}

document.querySelectorAll("#rail [data-f]").forEach(b => {
  b.onclick = () => {
    filter = filter === b.dataset.f && filter !== "all" ? "all" : b.dataset.f;
    syncFilterUi();
    renderList();
  };
});
$("railTheme").onclick = () => $("themeBtn").click();
$("railLogout").onclick = () => $("logoutBtn").click();
$("railMe").onclick = () => $("avatarInput").click();

function closeChat() {
  if (active) drafts.set(active.id, input.value);
  msgUnsub?.();
  msgUnsub = null;
  chatUnsubs.forEach(u => u());
  chatUnsubs = [];
  active = null;
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
  active = { id, peer: group ? null : peer.uid, group: !!group, members: group ? group.members : [uid, peer.uid], reply: null, data: group || null, first: true, lastId: null, limit: PAGE, hasMore: false, olderLoad: false };
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
  }));
  listenMessages();
  input.value = drafts.get(id) || "";
  syncComposer();
  input.blur();
}

function listenMessages() {
  const target = active;
  const uid = auth.currentUser.uid;
  const ref = doc(db, "chats", target.id);
  msgUnsub?.();
  msgUnsub = onSnapshot(query(collection(db, "chats", target.id, "messages"), orderBy("at", "desc"), limit(target.limit)), s => {
    if (active !== target) return;
    target.hasMore = s.docs.length >= target.limit;
    renderMessages(s.docs.slice().reverse());
    const unseen = s.docs.filter(d => d.data().from !== uid && d.data().status !== "seen");
    if (unseen.length) {
      const batch = writeBatch(db);
      unseen.forEach(d => batch.update(d.ref, { status: "seen" }));
      batch.commit().catch(() => {});
    }
    if (target.data?.unread?.[uid]) setDoc(ref, { unread: { [uid]: 0 } }, { merge: true });
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
  if (active.group) {
    const g = active.data || {};
    const typers = Object.entries(g.typing || {}).filter(([k, v]) => v && k !== auth.currentUser.uid).map(([k]) => users.get(k)?.name).filter(Boolean);
    $("peerImg").src = pic({ name: g.name, photo: g.photo });
    $("peerAv").className = "av";
    $("peerName").textContent = g.name || "";
    $("peerStatus").classList.toggle("live", typers.length > 0);
    $("peerStatus").textContent = typers.length ? typers.join(", ") + " টাইপ করছে…" : (g.members?.length || 0) + " জন সদস্য";
    return;
  }
  const peer = users.get(active.peer);
  if (!peer) return;
  $("peerImg").src = pic(peer);
  $("peerAv").className = "av" + (peer.online ? " online" : "");
  $("peerName").textContent = peer.name || "";
  const status = $("peerStatus");
  const typing = active.data?.typing?.[active.peer];
  status.classList.toggle("live", !!typing || !!peer.online);
  if (typing) status.textContent = "টাইপ করছে…";
  else if (peer.online) status.textContent = "অনলাইন";
  else status.textContent = peer.lastSeen ? "সর্বশেষ দেখা " + dayLabel(peer.lastSeen.toDate()) + ", " + clock(peer.lastSeen) : "অফলাইন";
}

function renderMessages(all) {
  const uid = auth.currentUser.uid;
  const docs = all.filter(d => !(d.data().hiddenFor || []).includes(uid));
  const box = $("messages");
  const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 140;
  const prevHeight = box.scrollHeight;
  const prevTop = box.scrollTop;
  box.replaceChildren();
  let lastDay = "";
  let prevFrom = null;
  active.lastId = docs.length ? docs[docs.length - 1].id : null;

  docs.forEach(d => {
    const m = d.data();
    const mine = m.from === uid;
    const date = m.at?.toDate?.();
    if (date && date.toDateString() !== lastDay) {
      lastDay = date.toDateString();
      box.append(el("div", "day", dayLabel(date)));
      prevFrom = null;
    }
    const b = el("div", "msg " + (mine ? "mine" : "theirs") + (prevFrom !== m.from ? " first" : ""));
    b.id = "m-" + d.id;
    prevFrom = m.from;
    if (active.group && !mine) {
      const sender = el("div", "sender", users.get(m.from)?.name || "?");
      sender.style.color = palette[[...m.from].reduce((a, ch) => a + ch.charCodeAt(0), 0) % palette.length];
      b.append(sender);
    }
    if (m.replyTo && !m.deleted) {
      const q = el("div", "quote", m.replyTo.text);
      if (m.replyTo.id) q.onclick = () => {
        const t = $("m-" + m.replyTo.id);
        if (!t) return;
        t.scrollIntoView({ behavior: "smooth", block: "center" });
        t.classList.add("flash");
        setTimeout(() => t.classList.remove("flash"), 1300);
      };
      b.append(q);
    }

    if (m.deleted) {
      const gone = el("em", "gone");
      gone.append(icon("ban"), " মেসেজ মুছে ফেলা হয়েছে");
      b.append(gone);
    } else if (m.type === "image") {
      const img = el("img", "media");
      img.src = m.url;
      img.alt = "ছবি";
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
      a.append(icon("clip"), " " + (m.name || "ফাইল"));
      a.href = m.url;
      a.target = "_blank";
      a.rel = "noopener";
      b.append(a);
    } else {
      const p = el("p");
      p.append(linkify(m.text));
      b.append(p);
    }

    const picks = Object.values(m.reactions || {});
    if (picks.length && !m.deleted) {
      const bar = el("div", "reacts");
      [...new Set(picks)].forEach(em => {
        const n = picks.filter(x => x === em).length;
        bar.append(el("span", m.reactions[uid] === em ? "own" : "", n > 1 ? em + " " + n : em));
      });
      b.append(bar);
    }
    const meta = el("div", "meta");
    meta.append(el("span", "", clock(m.at)));
    if (mine && !m.deleted) {
      const seen = m.status === "seen";
      const tick = el("span", seen ? "seen" : "");
      tick.append(icon(seen ? "checks" : "check", "tick"));
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
  if (mine) options.push({ label: "সবার জন্য মুছুন", value: "all", kind: "dok" });
  options.push({ label: "আমার জন্য মুছুন", value: "me", kind: mine ? "dsoft" : "dok" });
  options.push({ label: "বাতিল", value: null, kind: "dcancel" });
  const choice = await askChoice({
    title: "মেসেজ মুছবেন?",
    text: mine ? "সবার জন্য মুছলে মেসেজটি সবার চ্যাট থেকে সরে যাবে। শুধু আপনার জন্য মুছলে অন্যরা এটি দেখতে পাবে।" : "মেসেজটি শুধু আপনার চ্যাট থেকে সরে যাবে, অন্যরা এটি দেখতে পাবে।",
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
  if (wasLast) await setDoc(doc(db, "chats", chatId), { lastMessage: "মেসেজ মুছে ফেলা হয়েছে" }, { merge: true });
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const t = document.createElement("textarea");
    t.value = text;
    t.style.position = "fixed";
    t.style.opacity = "0";
    document.body.append(t);
    t.select();
    document.execCommand("copy");
    t.remove();
  }
  toast("কপি করা হয়েছে");
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
  const body = el("p", "", m.deleted ? "মেসেজ মুছে ফেলা হয়েছে" : preview(m));
  const time = el("small", "", (mine ? "আপনি" : users.get(m.from)?.name || "") + (m.at ? " · " + clock(m.at) : ""));
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
    tile("trash", "আমার জন্য মুছুন", () => hideForMe(d), true);
  } else {
    tile("reply", "উত্তর দিন", () => setReply(preview(m), d.id));
    const copyable = m.type === "text" || !m.type ? m.text : m.url;
    if (copyable) tile("copy", "কপি করুন", () => copyText(copyable));
    tile("trash", "মুছুন", () => removeMessage(d), true);
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
  await addDoc(collection(db, "chats", target.id, "messages"), msg);
  await setDoc(doc(db, "chats", target.id), {
    lastMessage: preview(msg),
    lastFrom: uid,
    lastAt: serverTimestamp(),
    typing: { [uid]: false },
    unread: Object.fromEntries(target.members.filter(m => m !== uid).map(m => [m, increment(1)]))
  }, { merge: true });
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
    toast("মেসেজ পাঠানো যায়নি");
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
  if (!res.ok) throw new Error("আপলোড ব্যর্থ হয়েছে, আবার চেষ্টা করুন");
  return res.json();
}

$("attachBtn").onclick = () => $("fileInput").click();
$("fileInput").onchange = async e => {
  const file = e.target.files[0];
  e.target.value = "";
  if (!file || !active) return;
  const target = active;
  toast("আপলোড হচ্ছে…", true);
  try {
    const res = await upload(file);
    const type = res.resource_type === "image" ? "image" : res.resource_type === "video" ? (file.type.startsWith("audio") ? "audio" : "video") : "file";
    await send({ type, url: res.secure_url, name: file.name, size: file.size }, target);
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
      stream.getTracks().forEach(t => t.stop());
      stopRecUi();
      if (recCancelled) return;
      const blob = new Blob(chunks, { type: recorder.mimeType });
      toast("ভয়েস মেসেজ পাঠানো হচ্ছে…", true);
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
    toast("মাইক্রোফোনের অনুমতি দিন");
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
  const n = new Notification(c.group ? c.name : sender, { body: (c.group ? sender + ": " : "") + c.lastMessage, icon: "icon.svg", tag: id });
  n.onclick = () => {
    window.focus();
    n.close();
  };
}
document.addEventListener("click", () => {
  if ("Notification" in window && Notification.permission === "default") {
    Notification.requestPermission().then(p => {
      if (p === "granted") registerPush();
    });
  }
}, { once: true });

async function registerPush() {
  if (VAPID_KEY.startsWith("YOUR_") || !("Notification" in window) || !("serviceWorker" in navigator)) return;
  if (Notification.permission !== "granted" || !auth.currentUser) return;
  try {
    if (!(await isSupported())) return;
    const reg = await navigator.serviceWorker.ready;
    const token = await getToken(getMessaging(app), { vapidKey: VAPID_KEY, serviceWorkerRegistration: reg });
    if (!token) return;
    localStorage.setItem("kotha-push", token);
    await setDoc(doc(db, "pushTokens", auth.currentUser.uid), { tokens: arrayUnion(token) }, { merge: true });
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

$("groupBtn").onclick = () => {
  $("groupName").value = "";
  const box = $("members");
  box.replaceChildren();
  const me = auth.currentUser.uid;
  const contacts = [...new Set(chats.filter(c => !c.group && c.lastMessage).map(c => c.members.find(m => m !== me)))]
    .map(id => users.get(id))
    .filter(Boolean);
  if (!contacts.length) box.append(el("p", "hint", "গ্রুপে যোগ করতে আগে ইমেইল দিয়ে খুঁজে তাদের সাথে চ্যাট শুরু করুন।"));
  contacts.forEach(u => {
    const label = el("label", "pick");
    const img = el("img");
    img.src = pic(u);
    img.alt = "";
    const cb = el("input");
    cb.type = "checkbox";
    cb.value = u.uid;
    label.append(img, el("span", "", u.name || "ব্যবহারকারী"), cb);
    box.append(label);
  });
  $("sheet").hidden = false;
};
$("sheetClose").onclick = () => { $("sheet").hidden = true; };
$("sheetDone").onclick = async () => {
  const uid = auth.currentUser.uid;
  const name = $("groupName").value.trim();
  const picked = [...$("members").querySelectorAll("input:checked")].map(i => i.value);
  if (!name || !picked.length) {
    toast("গ্রুপের নাম দিন ও কমপক্ষে একজনকে বেছে নিন");
    return;
  }
  const members = [uid, ...picked];
  const ref = await addDoc(collection(db, "chats"), { group: true, name, admin: uid, members, lastMessage: "গ্রুপ তৈরি হয়েছে", lastFrom: uid, lastAt: serverTimestamp() });
  $("sheet").hidden = true;
  openChat(null, { id: ref.id, name, members });
};

if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});

document.addEventListener("keydown", e => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    closeSearchFocus();
    return;
  }
  if (e.key !== "Escape") return;
  if (!$("lightbox").hidden) $("lightbox").hidden = true;
  else if (!$("sheet").hidden) $("sheet").hidden = true;
  else if (!$("emojiPanel").hidden) $("emojiPanel").hidden = true;
  else if (active) closeChat();
});

function closeSearchFocus() {
  $("search").focus();
  $("search").select();
}
