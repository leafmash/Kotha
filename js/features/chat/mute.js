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

async function setMute(id, until) {
  const previous = state.muted;
  state.muted = { ...state.muted };
  if (until) state.muted[id] = until;
  else delete state.muted[id];
  renderList();
  try {
    await setDoc(pushRef(), { muted: { [id]: until || deleteField() } }, { merge: true });
    toast(t(until ? "mute.done" : "mute.undone"));
  } catch {
    state.muted = previous;
    renderList();
    toast(t("mute.fail"));
  }
}

export async function muteFlow(id) {
  if (!id || !auth.currentUser) return;
  if (isMuted(id)) {
    setMute(id, 0);
    return;
  }
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
  if (!choice) return;
  const span = spans[choice - 1];
  setMute(id, span ? Date.now() + span : FOREVER);
}
