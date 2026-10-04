import { arrayUnion, doc, serverTimestamp, setDoc, updateDoc } from "../../core/sdk.js";
import { EDIT_MAX, EDIT_WINDOW_MS } from "../../config.js";
import { fmtNumber, stored, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { auth, db } from "../../core/firebase.js";
import { askChoice, askEdit } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

export async function hideForMe(d) {
  await updateDoc(d.ref, { hiddenFor: arrayUnion(auth.currentUser.uid) });
}

export async function removeMessage(d) {
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
  const chatId = state.active.id;
  const wasLast = d.id === state.active.lastId;
  await updateDoc(d.ref, { deleted: true, text: "", url: "" });
  if (wasLast) await setDoc(doc(db, "chats", chatId), { lastMessage: stored("deleted") }, { merge: true });
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
