const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

const previewOf = m => {
  if (m.deleted) return "";
  const labels = { image: "📷 ছবি", video: "🎬 ভিডিও", audio: "🎤 ভয়েস মেসেজ", file: "📎 ফাইল" };
  return labels[m.type] || m.text || "";
};

exports.notifyMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async event => {
  const message = event.data.data();
  const chatId = event.params.chatId;
  const chatSnap = await db.doc(`chats/${chatId}`).get();
  if (!chatSnap.exists) return;
  const chat = chatSnap.data();
  const senderSnap = await db.doc(`users/${message.from}`).get();
  const senderName = senderSnap.exists ? senderSnap.data().name || "" : "";
  const body = previewOf(message);
  if (!body) return;

  const recipients = (chat.members || []).filter(uid => uid !== message.from);
  await Promise.all(recipients.map(async uid => {
    const ref = db.doc(`pushTokens/${uid}`);
    const snap = await ref.get();
    const tokens = snap.exists ? snap.data().tokens || [] : [];
    if (!tokens.length) return;
    const res = await getMessaging().sendEachForMulticast({
      tokens,
      data: {
        title: chat.group ? chat.name || "গ্রুপ" : senderName,
        body: chat.group ? `${senderName}: ${body}` : body,
        chatId
      },
      webpush: { headers: { Urgency: "high", TTL: "86400" } }
    });
    const dead = [];
    res.responses.forEach((r, i) => {
      const code = r.error && r.error.code;
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") dead.push(tokens[i]);
    });
    if (dead.length) await ref.update({ tokens: FieldValue.arrayRemove(...dead) });
  }));
});

exports.notifyCall = onDocumentCreated("calls/{callId}", async event => {
  const call = event.data.data();
  if (call.status !== "ringing") return;
  const callerSnap = await db.doc(`users/${call.caller}`).get();
  const callerName = callerSnap.exists ? callerSnap.data().name || "" : "";
  const ref = db.doc(`pushTokens/${call.callee}`);
  const snap = await ref.get();
  const tokens = snap.exists ? snap.data().tokens || [] : [];
  if (!tokens.length) return;
  const res = await getMessaging().sendEachForMulticast({
    tokens,
    data: {
      type: "call",
      title: call.video ? "ইনকামিং ভিডিও কল" : "ইনকামিং ভয়েস কল",
      body: callerName,
      chatId: call.chatId || ""
    },
    webpush: { headers: { Urgency: "high", TTL: "60" } }
  });
  const dead = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") dead.push(tokens[i]);
  });
  if (dead.length) await ref.update({ tokens: FieldValue.arrayRemove(...dead) });
});
