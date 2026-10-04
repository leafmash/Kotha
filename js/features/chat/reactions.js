import { deleteField, updateDoc } from "../../core/sdk.js";
import { auth } from "../../core/firebase.js";

export async function react(d, emoji) {
  const uid = auth.currentUser.uid;
  const current = d.data().reactions?.[uid];
  await updateDoc(d.ref, { [`reactions.${uid}`]: current === emoji ? deleteField() : emoji });
}
