const { initializeApp, getApps, cert } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

const TEXT = {
  en: {
    image: "📷 Photo",
    video: "🎬 Video",
    audio: "🎤 Voice message",
    file: "📎 File",
    group: "Group",
    callVideo: "Incoming video call",
    callVoice: "Incoming voice call",
    voiceCall: "Voice call",
    videoCall: "Video call",
    logDeclined: "{label} declined",
    logCancelled: "{label} cancelled",
    logMissed: "Missed {label}"
  },
  bn: {
    image: "📷 ছবি",
    video: "🎬 ভিডিও",
    audio: "🎤 ভয়েস মেসেজ",
    file: "📎 ফাইল",
    group: "গ্রুপ",
    callVideo: "ইনকামিং ভিডিও কল",
    callVoice: "ইনকামিং ভয়েস কল",
    voiceCall: "ভয়েস কল",
    videoCall: "ভিডিও কল",
    logDeclined: "{label} প্রত্যাখ্যাত",
    logCancelled: "{label} বাতিল",
    logMissed: "মিসড {label}"
  }
};

const DEAD = ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"];
const ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;

let cachedApp = null;

const getApp = () => {
  if (cachedApp) return cachedApp;
  if (getApps().length) {
    cachedApp = getApps()[0];
    return cachedApp;
  }
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error("config");
  const account = JSON.parse(raw);
  if (account.private_key) account.private_key = account.private_key.replace(/\\n/g, "\n");
  cachedApp = initializeApp({ credential: cert(account) });
  return cachedApp;
};

const parseBody = req => {
  if (req.body && typeof req.body === "object") return req.body;
  try {
    return JSON.parse(req.body || "{}");
  } catch {
    return {};
  }
};

const langOf = snap => (snap.exists && snap.data().lang === "bn" ? "bn" : "en");

const callLogPreview = (log, text) => {
  const label = log.video ? text.videoCall : text.voiceCall;
  const icon = log.video ? "🎥" : "📞";
  if (log.kind === "done") {
    const secs = Number(log.secs) || 0;
    return `${icon} ${label} · ${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, "0")}`;
  }
  const template = { declined: text.logDeclined, cancelled: text.logCancelled }[log.kind] || text.logMissed;
  return `${icon} ${template.replace("{label}", label)}`;
};

const previewOf = (m, text) => {
  if (m.deleted) return "";
  if (m.callLog && typeof m.callLog === "object") return callLogPreview(m.callLog, text);
  const labels = { image: text.image, video: text.video, audio: text.audio, file: text.file };
  return labels[m.type] || m.text || "";
};

const mutedUntil = (muted, chatId) => {
  const until = muted && muted[chatId];
  return typeof until === "number" && until > Date.now();
};

const totalUnread = async (db, uid, muted) => {
  const [chatsSnap, blockedSnap] = await Promise.all([
    db.collection("chats").where("members", "array-contains", uid).get(),
    db.collection(`users/${uid}/blocked`).get()
  ]);
  const blocked = new Set(blockedSnap.docs.map(d => d.id));
  let total = 0;
  chatsSnap.forEach(d => {
    if (mutedUntil(muted, d.id)) return;
    const chat = d.data();
    if (chat.group !== true && (chat.members || []).some(m => blocked.has(m))) return;
    const count = Number((chat.unread || {})[uid]) || 0;
    const lastAt = chat.lastAt && chat.lastAt.toMillis ? chat.lastAt.toMillis() : 0;
    const readAt = chat.readAt && chat.readAt[uid] && chat.readAt[uid].toMillis ? chat.readAt[uid].toMillis() : 0;
    if (chat.lastFrom === uid || (readAt && lastAt && readAt >= lastAt)) return;
    total += count;
  });
  return total;
};

const sendTo = async (db, uid, build, ttlMs, opts = {}) => {
  const ref = db.doc(`pushTokens/${uid}`);
  const snap = await ref.get();
  const stored = snap.exists ? snap.data() : {};
  const tokens = stored.tokens || [];
  if (!tokens.length) return 0;
  const muted = stored.muted || {};
  const data = { ...build.data };
  if (opts.chatId) data.muted = mutedUntil(muted, opts.chatId) ? "1" : "0";
  if (opts.badge) {
    try {
      data.badge = String(await totalUnread(db, uid, muted));
    } catch {
      delete data.badge;
    }
  }
  const res = await getMessaging(getApp()).sendEachForMulticast({
    tokens,
    data,
    webpush: { headers: { Urgency: "high", TTL: String(Math.floor(ttlMs / 1000)) } },
    android: { priority: "high", ttl: ttlMs }
  });
  const dead = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (DEAD.includes(code)) dead.push(tokens[i]);
  });
  if (dead.length) await ref.update({ tokens: FieldValue.arrayRemove(...dead) });
  return res.successCount;
};

const claim = (db, ref) =>
  db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    if (!snap.exists) return null;
    const data = snap.data();
    if (data.pushedAt) return false;
    tx.update(ref, { pushedAt: FieldValue.serverTimestamp() });
    return data;
  });

const release = ref => ref.update({ pushedAt: FieldValue.delete() }).catch(() => {});

const handleMessage = async (db, uid, body) => {
  const { chatId, messageId } = body;
  if (!ID_PATTERN.test(chatId || "") || !ID_PATTERN.test(messageId || "")) return { status: 400, error: "params" };
  const chatRef = db.doc(`chats/${chatId}`);
  const msgRef = db.doc(`chats/${chatId}/messages/${messageId}`);
  const [chatSnap, msgSnap] = await Promise.all([chatRef.get(), msgRef.get()]);
  if (!chatSnap.exists || !msgSnap.exists) return { status: 404, error: "missing" };
  const chat = chatSnap.data();
  const preClaim = msgSnap.data();
  if (preClaim.from !== uid) return { status: 403, error: "forbidden" };
  const members = chat.members || [];
  if (!members.includes(uid)) return { status: 403, error: "forbidden" };
  const message = await claim(db, msgRef);
  if (message === false) return { status: 200, ok: true, sent: 0, duplicate: true };
  if (!message) return { status: 404, error: "missing" };
  try {
    const senderSnap = await db.doc(`users/${uid}`).get();
    const sender = senderSnap.exists ? senderSnap.data() : {};
    const senderName = sender.name || "";
    const recipients = members.filter(m => m !== uid);
    const counts = await Promise.all(recipients.map(async rid => {
      const [userSnap, blockSnap] = await Promise.all([db.doc(`users/${rid}`).get(), db.doc(`users/${rid}/blocked/${uid}`).get()]);
      if (blockSnap.exists) return 0;
      const text = TEXT[langOf(userSnap)];
      const preview = previewOf(message, text);
      if (!preview) return 0;
      return sendTo(db, rid, {
        data: {
          type: "message",
          messageId,
          title: chat.group ? chat.name || text.group : senderName,
          body: chat.group ? `${senderName}: ${preview}` : preview,
          chatId,
          senderUid: uid,
          senderName,
          senderPhoto: sender.photo || "",
          text: preview,
          group: chat.group ? "1" : "0",
          chatName: chat.group ? chat.name || "" : ""
        }
      }, 86400000, { chatId, badge: true });
    }));
    return { status: 200, ok: true, sent: counts.reduce((a, b) => a + b, 0) };
  } catch (err) {
    await release(msgRef);
    throw err;
  }
};

const handleCall = async (db, uid, body) => {
  const { callId } = body;
  if (!ID_PATTERN.test(callId || "")) return { status: 400, error: "params" };
  const callRef = db.doc(`calls/${callId}`);
  const snap = await callRef.get();
  if (!snap.exists) return { status: 404, error: "missing" };
  const peek = snap.data();
  if (peek.caller !== uid) return { status: 403, error: "forbidden" };
  if (peek.status !== "ringing") return { status: 200, ok: true, sent: 0, skipped: true };
  const blockSnap = await db.doc(`users/${peek.callee}/blocked/${uid}`).get();
  if (blockSnap.exists) return { status: 200, ok: true, sent: 0, skipped: true };
  const call = await claim(db, callRef);
  if (call === false) return { status: 200, ok: true, sent: 0, duplicate: true };
  if (!call) return { status: 404, error: "missing" };
  try {
    const [callerSnap, calleeSnap] = await Promise.all([db.doc(`users/${uid}`).get(), db.doc(`users/${call.callee}`).get()]);
    const text = TEXT[langOf(calleeSnap)];
    const sent = await sendTo(db, call.callee, {
      data: {
        type: "call",
        title: call.video ? text.callVideo : text.callVoice,
        body: callerSnap.exists ? callerSnap.data().name || "" : "",
        chatId: call.chatId || ""
      }
    }, 60000);
    return { status: 200, ok: true, sent };
  } catch (err) {
    await release(callRef);
    throw err;
  }
};

module.exports = async (req, res) => {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (req.method === "OPTIONS") {
    res.status(204).end();
    return;
  }
  if (req.method !== "POST") {
    res.status(405).json({ error: "method" });
    return;
  }
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) {
    res.status(401).json({ error: "auth" });
    return;
  }
  let app;
  try {
    app = getApp();
  } catch {
    res.status(500).json({ error: "config" });
    return;
  }
  let uid;
  try {
    uid = (await getAuth(app).verifyIdToken(token)).uid;
  } catch {
    res.status(401).json({ error: "auth" });
    return;
  }
  const body = parseBody(req);
  const db = getFirestore(app);
  try {
    let out;
    if (body.type === "message") out = await handleMessage(db, uid, body);
    else if (body.type === "call") out = await handleCall(db, uid, body);
    else out = { status: 400, error: "type" };
    const { status, ...json } = out;
    res.status(status).json(json);
  } catch {
    res.status(500).json({ error: "server" });
  }
};
