import { doc, setDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, input } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { send } from "./send.js";
import { toast } from "../../ui/toast.js";

let isTyping = false;
let typingTimer;

export const syncComposer = () => {
  input.style.height = "auto";
  input.style.height = Math.min(input.scrollHeight, 130) + "px";
  const has = input.value.trim().length > 0;
  $("sendBtn").hidden = !has;
  $("micBtn").hidden = has;
};

const setTyping = value => {
  if (!state.active || isTyping === value) return;
  isTyping = value;
  setDoc(doc(db, "chats", state.active.id), { typing: { [auth.currentUser.uid]: value } }, { merge: true }).catch(() => {});
};

async function submitText() {
  const text = input.value.trim();
  if (!text || !state.active) return;
  input.value = "";
  syncComposer();
  clearTimeout(typingTimer);
  isTyping = false;
  try {
    await send({ text });
  } catch (err) {
    toast(t(err?.code === "permission-denied" ? "chat.cannotSend" : "chat.sendFail"));
  }
}

export function initComposer() {
  input.oninput = () => {
    syncComposer();
    setTyping(true);
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => setTyping(false), 1600);
  };
  input.onkeydown = e => {
    if (e.key === "Enter" && !e.shiftKey && !matchMedia("(pointer:coarse)").matches) {
      e.preventDefault();
      submitText();
    }
  };
  $("sendBtn").onclick = submitText;
  syncComposer();
}
