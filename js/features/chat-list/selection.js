import { t, fmtNumber } from "../../i18n.js";
import { haptic } from "../../native.js";
import { $ } from "../../core/dom.js";
import { icon } from "../../core/icons.js";
import { pic } from "../../core/format.js";
import { openActionSheet } from "../../ui/action-sheet.js";
import { deleteConversations } from "../chat/clear-chat.js";
import { muteMany, isMuted } from "../chat/mute.js";
import { bulkArchive, bulkPin, isArchived, isPinned } from "../chat/pin-archive.js";

const selected = new Set();
let visible = [];

const button = (id, onClick) => {
  const b = document.createElement("button");
  b.id = id;
  b.type = "button";
  b.onclick = onClick;
  return b;
};

const setIcon = (btn, name, label) => {
  btn.replaceChildren(icon(name));
  btn.title = label;
  btn.setAttribute("aria-label", label);
};

export const isSelecting = () => selected.size > 0;
export const isSelected = id => selected.has(id);

function syncBar() {
  const ids = [...selected];
  const on = ids.length > 0;
  $("sidebar").classList.toggle("selecting", on);
  $("selBar").hidden = !on;
  document.querySelectorAll("#list .row[data-id]").forEach(r => r.classList.toggle("selected", selected.has(r.dataset.id)));
  if (!on) return;
  $("selCount").textContent = t("sel.count", { n: fmtNumber(ids.length) });
  setIcon($("selMore"), "more", t("common.more"));
  setIcon($("selDelete"), "trash", t("chat.delete"));
  setIcon($("selAll"), "checks", t("sel.all"));
  setIcon($("selClose"), "close", t("sel.close"));
}

export function exitSelect() {
  if (!selected.size) return;
  selected.clear();
  syncBar();
}

export function startSelect(id) {
  if (!id || selected.has(id)) return;
  haptic("tap");
  selected.add(id);
  syncBar();
}

export function toggleSelect(id) {
  if (!id) return;
  haptic("tap");
  if (selected.has(id)) selected.delete(id);
  else selected.add(id);
  syncBar();
}

export function reconcileSelection(ids) {
  visible = ids;
  if (!selected.size) return;
  const live = new Set(ids);
  let changed = false;
  [...selected].forEach(id => {
    if (!live.has(id)) {
      selected.delete(id);
      changed = true;
    }
  });
  if (changed) syncBar();
}

export async function openChatActions(ids, who) {
  if (!ids.length) return;
  const many = ids.length > 1;
  const allPinned = ids.every(isPinned);
  const allArchived = ids.every(isArchived);
  const allMuted = ids.every(isMuted);
  const actions = [];
  if (!isSelecting()) actions.push({ icon: "select", label: t("sel.select"), value: "select" });
  actions.push(
    { icon: "pin", label: t(allPinned ? "pin.undo" : "pin.action"), value: "pin" },
    { icon: allMuted ? "bell" : "bellOff", label: t(allMuted ? "mute.unmute" : "mute.action"), value: "mute" },
    { icon: allArchived ? "unarchive" : "archive", label: t(allArchived ? "archive.undo" : "archive.action"), value: "archive" },
    { icon: "trash", label: t("chat.delete"), value: "delete", danger: true, separator: true }
  );
  const choice = await openActionSheet({
    title: many ? t("sel.count", { n: fmtNumber(ids.length) }) : who?.name || t("common.chats"),
    avatar: many || !who ? undefined : pic(who),
    badge: many ? fmtNumber(ids.length) : undefined,
    actions
  });
  if (!choice) return;
  if (choice === "select") {
    startSelect(ids[0]);
    return;
  }
  const action = { pin: bulkPin, mute: muteMany, archive: bulkArchive, delete: deleteConversations }[choice];
  const result = await action(ids);
  if (result !== false && isSelecting()) exitSelect();
}

async function run(action, keepOnFail = true) {
  const ids = [...selected];
  if (!ids.length) return;
  const result = await action(ids);
  if (result === false && keepOnFail) return;
  exitSelect();
}

export function initSelection() {
  const bar = $("selBar");
  const actions = document.createElement("div");
  actions.className = "selactions";
  const count = document.createElement("span");
  count.id = "selCount";
  actions.append(
    button("selAll", () => {
      visible.forEach(id => selected.add(id));
      syncBar();
    }),
    button("selDelete", () => run(deleteConversations)),
    button("selMore", () => openChatActions([...selected]))
  );
  bar.append(button("selClose", exitSelect), count, actions);
}
