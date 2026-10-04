import { t } from "../../i18n.js";
import { $ } from "../../core/dom.js";
import { pic } from "../../core/format.js";

const box = $("noteView");
let onMessage = null;

export function closeNoteView() {
  box.hidden = true;
  onMessage = null;
}

export function openNoteView({ user, name, text, message }) {
  $("noteViewAvatar").src = pic(user);
  $("noteViewName").textContent = name;
  $("noteViewText").textContent = text;
  $("noteViewMessage").textContent = t("user.message");
  $("noteViewClose").textContent = t("note.close");
  onMessage = message;
  box.hidden = false;
  $("noteViewClose").focus();
}

export function initNoteView() {
  box.addEventListener("click", e => {
    if (e.target === box) closeNoteView();
  });
  $("noteViewClose").onclick = closeNoteView;
  $("noteViewMessage").onclick = () => {
    const go = onMessage;
    closeNoteView();
    if (go) go();
  };
  addEventListener("keydown", e => {
    if (e.key === "Escape" && !box.hidden) closeNoteView();
  });
}
