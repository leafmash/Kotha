import { doc, onDisconnect, onValue, rtRef, rtRemove, rtServerTimestamp, rtSet, serverTimestamp, updateDoc } from "../../core/sdk.js";
import { fmtNumber, locale, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { el } from "../../core/dom.js";
import { auth, db, rtdb } from "../../core/firebase.js";
import { tsMs } from "../../core/format.js";
import { renderList } from "../chat-list/chat-list.js";
import { renderPeer } from "../chat/header.js";
import { markRead } from "../chat/read-state.js";
import { renderUserProfile } from "../profile/user-profile.js";

const PRESENCE_BEAT_MS = 90000;
const PRESENCE_STALE_MS = 240000;
const RT_GRACE_MS = 180000;

export const rtPresence = new Map();
let rtConnected = false;
let rtSkew = false;
let rtStarted = false;
let clockSkew = 0;
let beatSentAt = 0;
let skewArmed = false;
export const serverNow = () => Date.now() - clockSkew;

const presenceOf = u => {
  const fsMs = tsMs(u?.lastSeen);
  const p = u && rtPresence.get(u.uid);
  if (p && p.at && p.at >= fsMs - 15000) return { online: p.online || serverNow() - p.at < RT_GRACE_MS, ms: p.at };
  return { online: !!u && u.online === true && fsMs > 0 && serverNow() - fsMs < PRESENCE_STALE_MS, ms: fsMs };
};
export const isOnline = u => presenceOf(u).online;

export const presenceText = u => {
  if (isOnline(u)) return t("presence.now");
  const ms = presenceOf(u).ms;
  if (!ms) return t("chat.offline");
  const mins = Math.floor(Math.max(0, serverNow() - ms) / 60000);
  if (mins < 1) return t("presence.justNow");
  if (mins < 60) return t("presence.min", { n: fmtNumber(mins) });
  const hours = Math.floor(mins / 60);
  if (hours < 24) return t("presence.hour", { n: fmtNumber(hours) });
  const d = new Date(ms);
  const ref = new Date();
  ref.setDate(ref.getDate() - 1);
  if (hours < 48 && d.toDateString() === ref.toDateString()) return t("presence.yesterday");
  const days = Math.floor(hours / 24);
  if (days < 7) return t("presence.day", { n: fmtNumber(days) });
  return t("presence.on", { date: d.toLocaleDateString(locale(), { day: "numeric", month: "short" }) });
};

const presenceShort = u => {
  if (!u || isOnline(u)) return "";
  const ms = presenceOf(u).ms;
  if (!ms) return "";
  const mins = Math.max(1, Math.floor(Math.max(0, serverNow() - ms) / 60000));
  if (mins < 60) return t("presence.short.m", { n: fmtNumber(mins) });
  const hours = Math.floor(mins / 60);
  if (hours < 24) return t("presence.short.h", { n: fmtNumber(hours) });
  const days = Math.floor(hours / 24);
  return days < 7 ? t("presence.short.d", { n: fmtNumber(days) }) : "";
};

export const setPresenceBadge = (av, u) => {
  av.querySelector(".pb")?.remove();
  const online = isOnline(u);
  const text = online ? "" : presenceShort(u);
  if (!online && !text) return;
  av.append(el("span", "pb " + (online ? "dot" : "time"), text));
};

export const setPresence = (on, mode) => {
  const user = auth.currentUser;
  if (!user || state.deleting) return Promise.resolve();
  const viaRt = !!rtdb && rtConnected;
  if (viaRt && mode === "beat") return Promise.resolve();
  const jobs = [];
  if (viaRt) jobs.push(rtSet(rtRef(rtdb, "presence/" + user.uid), { online: on, at: rtServerTimestamp() }).catch(() => {}));
  if (!viaRt || !mode) {
    beatSentAt = Date.now();
    skewArmed = true;
    jobs.push(updateDoc(doc(db, "users", user.uid), { online: on, lastSeen: serverTimestamp() }).catch(() => {}));
  }
  return Promise.all(jobs);
};

const presenceOn = () => (rtdb && rtConnected ? !document.hidden : true);

export function armRtPresence() {
  const uid = auth.currentUser?.uid;
  if (!rtdb || !uid || state.deleting || !rtConnected) return;
  const me = rtRef(rtdb, "presence/" + uid);
  onDisconnect(me).set({ online: false, at: rtServerTimestamp() }).then(() => {
    if (state.deleting || auth.currentUser?.uid !== uid) return null;
    return rtSet(me, { online: !document.hidden, at: rtServerTimestamp() });
  }).catch(() => {});
}

export function startRtPresence() {
  if (!rtdb || rtStarted) return;
  rtStarted = true;
  onValue(rtRef(rtdb, ".info/connected"), s => {
    rtConnected = s.val() === true;
    if (rtConnected) armRtPresence();
  });
  onValue(rtRef(rtdb, ".info/serverTimeOffset"), s => {
    const off = Number(s.val());
    if (!Number.isFinite(off)) return;
    clockSkew = -off;
    rtSkew = true;
  });
}

export async function leavePresence() {
  const uid = auth.currentUser?.uid;
  await setPresence(false);
  if (rtdb && uid) onDisconnect(rtRef(rtdb, "presence/" + uid)).cancel().catch(() => {});
}

export function dropRtPresence(uid) {
  if (!rtdb || !uid) return;
  const me = rtRef(rtdb, "presence/" + uid);
  onDisconnect(me).cancel().catch(() => {});
  rtRemove(me).catch(() => {});
}

export const learnClockSkew = (s, uid) => {
  if (rtSkew) return;
  if (!skewArmed || s.metadata.hasPendingWrites || s.metadata.fromCache) return;
  const ls = tsMs(s.data()?.lastSeen);
  const now = Date.now();
  if (!ls || now - beatSentAt > 15000) return;
  skewArmed = false;
  const skew = (beatSentAt + now) / 2 - ls;
  if (Math.abs(skew) < 86400000) clockSkew = skew;
};

let presenceSig = "";
const presenceSignature = () => {
  let sig = Math.floor(serverNow() / 60000) + ":";
  state.users.forEach((u, id) => { if (isOnline(u)) sig += id + ","; });
  return sig;
};
const presenceTick = () => {
  if (!auth.currentUser || document.hidden) return;
  if (state.active) renderPeer();
  renderUserProfile();
  const sig = presenceSignature();
  if (sig !== presenceSig) {
    presenceSig = sig;
    renderList();
  }
};

export function initPresence() {
  setInterval(() => {
    if (auth.currentUser && !document.hidden && !state.deleting) setPresence(true, "beat");
  }, PRESENCE_BEAT_MS);
  setInterval(presenceTick, 20000);
  addEventListener("focus", () => { if (!document.hidden) setPresence(true, "soft"); });
  addEventListener("pageshow", () => { if (!document.hidden) setPresence(true, "soft"); });
  addEventListener("online", () => { if (!document.hidden) setPresence(true, "soft"); });
  document.addEventListener("visibilitychange", () => {
    setPresence(presenceOn(), "soft");
    markRead();
    renderList();
    if (!document.hidden) renderPeer();
  });
  addEventListener("pagehide", () => setPresence(!(rtdb && rtConnected), "soft"));
}
