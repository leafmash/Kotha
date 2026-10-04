import { waitForPendingWrites } from "../../core/sdk.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { auth, db, triggerPush } from "../../core/firebase.js";
import { inflight } from "./send.js";

const OUTBOX = "kotha-outbox";
let flushing = false;

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
export const outboxAdd = entry => writeOutbox([...readOutbox(), entry]);
export const outboxDrop = id => writeOutbox(readOutbox().filter(x => x.messageId !== id));

export async function flushOutbox() {
  const user = auth.currentUser;
  if (!user || state.deleting || flushing) return;
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

export function initOutbox() {
  addEventListener("online", () => {
    syncNet();
    flushOutbox();
  });
  addEventListener("offline", syncNet);
  syncNet();
}
