import { arrayRemove, arrayUnion, collection, deleteField, doc, serverTimestamp, writeBatch } from "../../core/sdk.js";
import { stored, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { auth, db } from "../../core/firebase.js";
import { toast } from "../../ui/toast.js";

export const groupAdminIds = g => [...new Set([g?.admin, ...(g?.admins || [])].filter(id => id && (g.members || []).includes(id)))];
export const iAmAdmin = g => !!auth.currentUser && groupAdminIds(g).includes(auth.currentUser.uid);
export const memberName = id => state.users.get(id)?.name || t("common.user");
export const runGroup = fn => fn().catch(() => toast(t("group.actionFail")));

export function groupBatch(chatId, sys, patch) {
  const uid = auth.currentUser.uid;
  const batch = writeBatch(db);
  if (sys) {
    batch.set(doc(collection(db, "chats", chatId, "messages")), {
      from: uid,
      type: "system",
      text: "",
      at: serverTimestamp(),
      sys: { by: state.users.get(uid)?.name || "", ...sys }
    });
  }
  batch.update(doc(db, "chats", chatId), patch);
  return batch.commit();
}

export function addGroupMembers(chatId, ids, names) {
  return groupBatch(chatId, { kind: "added", target: ids, names }, {
    members: arrayUnion(...ids),
    lastMessage: stored("membersAdded"),
    lastFrom: auth.currentUser.uid,
    lastAt: serverTimestamp()
  });
}

export function removeGroupMember(chatId, id, g) {
  const patch = { members: arrayRemove(id), ["unread." + id]: deleteField(), ["typing." + id]: deleteField() };
  if ((g.admins || []).includes(id)) patch.admins = arrayRemove(id);
  return groupBatch(chatId, { kind: "removed", target: [id], names: [memberName(id)] }, patch);
}

export function setGroupAdmin(chatId, id, make) {
  return groupBatch(chatId, { kind: make ? "promoted" : "demoted", target: [id], names: [memberName(id)] }, { admins: make ? arrayUnion(id) : arrayRemove(id) });
}

export function sysText(sys, from) {
  const uid = auth.currentUser.uid;
  const by = from === uid ? t("common.you") : state.users.get(from)?.name || sys.by || t("common.user");
  const targets = Array.isArray(sys.target) ? sys.target : [];
  const list = targets.map((id, i) => (id === uid ? t("sys.youObj") : state.users.get(id)?.name || (sys.names || [])[i] || t("common.user")));
  const names = targets.length === 1 && targets[0] === uid ? t("sys.youFull") : t("sys.obj", { name: list.join(", ") });
  return t("sys." + sys.kind, { by, names, name: sys.name || "" });
}
