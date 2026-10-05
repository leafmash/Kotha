import { deleteField, doc, setDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { auth, db } from "../../core/firebase.js";
import { renderList } from "../chat-list/chat-list.js";
import { askChoice } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";
import { deleteConversation } from "./clear-chat.js";
import { isMuted, muteFlow } from "./mute.js";

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

export async function openRowMenu(id, title) {
  if (!id) return;
  const choice = await askChoice({
    title: title || t("common.chats"),
    text: t("rowmenu.text"),
    iconName: "pin",
    neutral: true,
    options: [
      { label: t(isPinned(id) ? "pin.undo" : "pin.action"), value: "pin", kind: "dplain" },
      { label: t(isArchived(id) ? "archive.undo" : "archive.action"), value: "archive", kind: "dplain" },
      { label: t(isMuted(id) ? "mute.unmute" : "mute.action"), value: "mute", kind: "dplain" },
      { label: t("chat.delete"), value: "delete", kind: "dsoft dplain" },
      { label: t("common.cancel"), value: null, kind: "dcancel" }
    ]
  });
  if (choice === "pin") togglePin(id);
  else if (choice === "archive") toggleArchive(id);
  else if (choice === "mute") muteFlow(id);
  else if (choice === "delete") deleteConversation(id);
}
