import { deleteField, doc, setDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { auth, db } from "../../core/firebase.js";
import { renderList } from "../chat-list/chat-list.js";
import { toast } from "../../ui/toast.js";

const MAX_PINNED = 3;

export const isPinned = id => !!state.pinned[id];
export const isArchived = id => !!state.archived[id];

const without = (map, id) => {
  const next = { ...map };
  delete next[id];
  return next;
};

async function commit(id, pinned, archived, doneKey, failKey) {
  if (!id || !auth.currentUser) return;
  const before = { pinned: state.pinned, archived: state.archived };
  const now = Date.now();
  state.pinned = pinned ? { ...state.pinned, [id]: now } : without(state.pinned, id);
  state.archived = archived ? { ...state.archived, [id]: now } : without(state.archived, id);
  renderList();
  try {
    await setDoc(doc(db, "pushTokens", auth.currentUser.uid), {
      pinned: { [id]: pinned ? now : deleteField() },
      archived: { [id]: archived ? now : deleteField() }
    }, { merge: true });
    toast(t(doneKey));
  } catch {
    state.pinned = before.pinned;
    state.archived = before.archived;
    renderList();
    toast(t(failKey));
  }
}

export function togglePin(id) {
  if (isPinned(id)) {
    commit(id, false, isArchived(id), "pin.undone", "pin.fail");
    return;
  }
  if (Object.keys(state.pinned).length >= MAX_PINNED) {
    toast(t("pin.max"));
    return;
  }
  commit(id, true, false, "pin.done", "pin.fail");
}

export function toggleArchive(id) {
  if (isArchived(id)) commit(id, isPinned(id), false, "archive.undone", "archive.fail");
  else commit(id, false, true, "archive.done", "archive.fail");
}

async function applyMany(ids, next, doneKey, failKey) {
  if (!ids.length || !auth.currentUser) return false;
  const before = { pinned: state.pinned, archived: state.archived };
  const now = Date.now();
  const pinned = { ...state.pinned };
  const archived = { ...state.archived };
  const pinPatch = {};
  const archivePatch = {};
  ids.forEach(id => {
    const n = next(id);
    if (n.pinned) pinned[id] = now;
    else delete pinned[id];
    if (n.archived) archived[id] = now;
    else delete archived[id];
    pinPatch[id] = n.pinned ? now : deleteField();
    archivePatch[id] = n.archived ? now : deleteField();
  });
  state.pinned = pinned;
  state.archived = archived;
  renderList();
  try {
    await setDoc(doc(db, "pushTokens", auth.currentUser.uid), { pinned: pinPatch, archived: archivePatch }, { merge: true });
    toast(t(doneKey));
    return true;
  } catch {
    state.pinned = before.pinned;
    state.archived = before.archived;
    renderList();
    toast(t(failKey));
    return false;
  }
}

export async function bulkPin(ids) {
  const allPinned = ids.every(isPinned);
  if (!allPinned && Object.keys(state.pinned).length + ids.filter(id => !isPinned(id)).length > MAX_PINNED) {
    toast(t("pin.max"));
    return false;
  }
  return applyMany(ids, id => ({ pinned: !allPinned, archived: allPinned ? isArchived(id) : false }), allPinned ? "pin.undone" : "pin.done", "pin.fail");
}

export async function bulkArchive(ids) {
  const allArchived = ids.every(isArchived);
  return applyMany(ids, id => ({ pinned: allArchived ? isPinned(id) : false, archived: !allArchived }), allArchived ? "archive.undone" : "archive.done", "archive.fail");
}
