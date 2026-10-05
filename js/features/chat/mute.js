import { deleteField, doc, setDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { auth, db } from "../../core/firebase.js";
import { renderList } from "../chat-list/chat-list.js";
import { askChoice } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

const FOREVER = 4102444800000;
export const isMuted = id => (Number(state.muted[id]) || 0) > Date.now();

const pushRef = () => doc(db, "pushTokens", auth.currentUser.uid);

async function setMuteMany(ids, until) {
  const previous = state.muted;
  const next = { ...state.muted };
  const patch = {};
  ids.forEach(id => {
    if (until) next[id] = until;
    else delete next[id];
    patch[id] = until || deleteField();
  });
  state.muted = next;
  renderList();
  try {
    await setDoc(pushRef(), { muted: patch }, { merge: true });
    toast(t(until ? "mute.done" : "mute.undone"));
    return true;
  } catch {
    state.muted = previous;
    renderList();
    toast(t("mute.fail"));
    return false;
  }
}

export async function muteMany(ids) {
  if (!ids.length || !auth.currentUser) return false;
  if (ids.every(isMuted)) return setMuteMany(ids, 0);
  const spans = [8 * 3600000, 7 * 24 * 3600000, 0];
  const choice = await askChoice({
    title: t("mute.title"),
    text: t("mute.text"),
    iconName: "bellOff",
    options: [
      { label: t("mute.8h"), value: 1, kind: "dsoft" },
      { label: t("mute.1w"), value: 2, kind: "dsoft" },
      { label: t("mute.always"), value: 3, kind: "dsoft" },
      { label: t("common.cancel"), value: null, kind: "dcancel" }
    ]
  });
  if (!choice) return false;
  const span = spans[choice - 1];
  return setMuteMany(ids, span ? Date.now() + span : FOREVER);
}

export const muteFlow = id => muteMany([id]);
