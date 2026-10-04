import { collection, doc, increment, serverTimestamp, writeBatch } from "../../core/sdk.js";
import { state } from "../../core/state.js";
import { auth, db, triggerPush } from "../../core/firebase.js";
import { previewStored } from "../../core/message-format.js";
import { outboxAdd, outboxDrop } from "./outbox.js";
import { clearReply } from "./reply.js";

export const inflight = new Set();

export async function send(payload, target = state.active) {
  if (!target) return;
  const uid = auth.currentUser.uid;
  const msg = { from: uid, type: "text", text: "", at: serverTimestamp(), status: "sent", ...payload };
  if (target.reply) msg.replyTo = { text: target.reply.text, id: target.reply.id };
  if (target === state.active) clearReply();
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
