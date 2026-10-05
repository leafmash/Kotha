import { $, el } from "../core/dom.js";
import { icon } from "../core/icons.js";

const confirmBox = $("confirm");
let confirmDone;
export const closeConfirm = ok => {
  if (confirmBox.hidden) return;
  confirmBox.hidden = true;
  const done = confirmDone;
  confirmDone = null;
  if (done) done(ok);
};
export const askConfirm = ({ title, text, ok, iconName }) => new Promise(resolve => {
  closeConfirm(false);
  $("confirmIcon").replaceChildren(icon(iconName));
  $("confirmTitle").textContent = title;
  $("confirmText").textContent = text;
  $("confirmOk").textContent = ok;
  confirmDone = resolve;
  confirmBox.hidden = false;
  $("confirmCancel").focus();
});
const choiceBox = $("choice");
let choiceDone;
export const closeChoice = value => {
  if (choiceBox.hidden) return;
  choiceBox.hidden = true;
  const done = choiceDone;
  choiceDone = null;
  if (done) done(value);
};
export const askChoice = ({ title, text, iconName, options, neutral }) => new Promise(resolve => {
  closeChoice(null);
  choiceBox.classList.toggle("neutral", !!neutral);
  $("choiceIcon").replaceChildren(icon(iconName));
  $("choiceTitle").textContent = title;
  $("choiceText").textContent = text;
  const btns = options.map(o => {
    const b = el("button", o.kind || "", o.label);
    b.onclick = () => closeChoice(o.value);
    return b;
  });
  $("choiceBtns").replaceChildren(...btns);
  choiceDone = resolve;
  choiceBox.hidden = false;
  btns[btns.length - 1].focus();
});

const editBox = $("editBox");
let editDone;
let editMulti = false;
export function closeEdit(value) {
  if (editBox.hidden) return;
  editBox.hidden = true;
  const done = editDone;
  editDone = null;
  if (done) done(value);
}
export const askEdit = ({ title, value, max, multiline, placeholder }) => new Promise(resolve => {
  closeEdit(null);
  editMulti = !!multiline;
  $("editTitle").textContent = title;
  const inp = $("editInput");
  inp.maxLength = max;
  inp.placeholder = placeholder || "";
  inp.rows = multiline ? 4 : 2;
  inp.value = value;
  editDone = resolve;
  editBox.hidden = false;
  inp.focus();
  inp.setSelectionRange(inp.value.length, inp.value.length);
});

export function initDialogs() {
  choiceBox.onclick = e => { if (e.target === choiceBox) closeChoice(null); };
  $("confirmOk").onclick = () => closeConfirm(true);
  $("confirmCancel").onclick = () => closeConfirm(false);
  confirmBox.onclick = e => { if (e.target === confirmBox) closeConfirm(false); };
  $("editOk").onclick = () => {
    const v = $("editInput").value;
    closeEdit(editMulti ? v.trim() : v.replace(/\s*\n\s*/g, " ").trim());
  };
  $("editCancel").onclick = () => closeEdit(null);
  editBox.onclick = e => { if (e.target === editBox) closeEdit(null); };
}
