import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";

export function setReply(text, id) {
  state.active.reply = { text, id };
  $("replyText").textContent = text;
  $("replyBar").hidden = false;
  $("input").focus();
}
export function clearReply() {
  if (state.active) state.active.reply = null;
  $("replyBar").hidden = true;
}

export function initReply() {
  $("replyCancel").onclick = clearReply;
}
