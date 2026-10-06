import { arrayUnion, doc, serverTimestamp, setDoc, updateDoc, writeBatch } from "../../core/sdk.js";
import { EDIT_MAX, EDIT_WINDOW_MS } from "../../config.js";
import { fmtNumber, stored, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { auth, db } from "../../core/firebase.js";
import { askChoice, askEdit } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

const hidePatch = () => ({ hiddenFor: arrayUnion(auth.currentUser.uid) });
const wipePatch = () => ({ deleted: true, text: "", url: "" });

const commit = (docs, patchOf) => {
  const batch = writeBatch(db);
  docs.forEach(d => batch.update(d.ref, patchOf(d)));
  return batch.commit();
};

export async function removeMessages(docs) {
  const uid = auth.currentUser.uid;
  const many = docs.length > 1;
  const alive = docs.filter(d => !d.data().deleted);
  const everyoneOk = alive.length > 0 && docs.every(d => d.data().from === uid);
  let choice = "me";
  if (alive.length) {
    const options = [];
    if (everyoneOk) options.push({ label: t("msg.deleteForAll"), value: "all", kind: "dok" });
    options.push({ label: t("msg.deleteForMe"), value: "me", kind: everyoneOk ? "dsoft" : "dok" });
    options.push({ label: t("common.cancel"), value: null, kind: "dcancel" });
    choice = await askChoice({
      title: t(many ? "msg.deleteTitleMany" : "msg.deleteTitle", { n: fmtNumber(docs.length) }),
      text: t(everyoneOk ? (many ? "msg.deleteTextMineMany" : "msg.deleteTextMine") : (many ? "msg.deleteTextOtherMany" : "msg.deleteTextOther")),
      iconName: "trash",
      options
    });
    if (!choice) return false;
  }
  const chatId = state.active.id;
  const wasLast = docs.some(d => d.id === state.active.lastId);
  try {
    await commit(docs, d => (choice === "all" && !d.data().deleted ? wipePatch() : hidePatch()));
    if (choice === "all" && wasLast) await setDoc(doc(db, "chats", chatId), { lastMessage: stored("deleted") }, { merge: true });
  } catch {
    toast(t("msg.deleteFail"));
    return false;
  }
  return true;
}

export async function copyText(text) {
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

const isPlain = m => !m.deleted && !m.callLog && !m.sys && m.type !== "system" && m.type !== "call";
export const isTextMsg = m => m.type === "text" || !m.type;
export const copyableOf = m => (isTextMsg(m) ? m.text : m.url);
export const canForward = m => isPlain(m) && (isTextMsg(m) ? !!m.text : !!m.url);
const editLeft = m => {
  const at = m.at?.toDate?.();
  return at ? EDIT_WINDOW_MS - (Date.now() - at.getTime()) : 0;
};
export const canEdit = m => isPlain(m) && isTextMsg(m) && typeof m.text === "string" && m.text.length <= EDIT_MAX && editLeft(m) > 0;

export async function editMessage(d, m) {
  const minutes = fmtNumber(EDIT_WINDOW_MS / 60000);
  if (editLeft(m) <= 0) {
    toast(t("msg.editExpired", { n: minutes }));
    return;
  }
  const next = await askEdit({ title: t("msg.editTitle"), value: m.text, max: EDIT_MAX, multiline: true });
  if (!next || next === m.text) return;
  if (editLeft(m) <= 0) {
    toast(t("msg.editExpired", { n: minutes }));
    return;
  }
  const chatId = state.active.id;
  const wasLast = d.id === state.active.lastId;
  try {
    await updateDoc(d.ref, { text: next, edited: true, editedAt: serverTimestamp() });
    if (wasLast) await setDoc(doc(db, "chats", chatId), { lastMessage: next }, { merge: true });
  } catch (err) {
    toast(err?.code === "permission-denied" ? t("msg.editExpired", { n: minutes }) : t("msg.editFail"));
  }
}
