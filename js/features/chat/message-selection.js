import { fmtNumber, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth } from "../../core/firebase.js";
import { icon } from "../../core/icons.js";
import { preview } from "../../core/message-format.js";
import { haptic } from "../../native.js";
import { openActionSheet } from "../../ui/action-sheet.js";
import { blockFlow } from "../block/block.js";
import { reportMessage } from "../report/report.js";
import { openForward } from "./forward.js";
import { canEdit, canForward, copyableOf, copyText, editMessage, removeMessages } from "./message-actions.js";
import { react } from "./reactions.js";
import { setReply } from "./reply.js";

const REACTIONS = ["👍", "❤️", "😂", "😮", "🙏"];

const picked = new Map();
let order = [];

const sorted = () => order.filter(id => picked.has(id)).map(id => picked.get(id));

const button = (name, label, onClick, danger) => {
  const b = el("button", danger ? "danger" : "");
  b.type = "button";
  b.title = label;
  b.setAttribute("aria-label", label);
  b.append(icon(name));
  b.onclick = onClick;
  return b;
};

const editable = ({ d, m, mine }) => mine && !d.metadata.hasPendingWrites && canEdit(m);

export const isSelectingMessages = () => picked.size > 0;

export function exitMessageSelection() {
  if (!picked.size) return;
  picked.clear();
  syncBar();
}

async function openMore(entry) {
  const { d, m, mine } = entry;
  const actions = [];
  if (editable(entry)) actions.push({ icon: "edit", label: t("msg.edit"), value: "edit" });
  if (!mine) {
    actions.push({ icon: "flag", label: t("report.message"), value: "report", danger: true });
    if (state.active?.group) actions.push({ icon: "ban", label: t("block.action"), value: "block", danger: true });
  }
  const choice = await openActionSheet({ title: mine ? t("common.you") : state.users.get(m.from)?.name || t("common.user"), actions });
  if (!choice) return;
  exitMessageSelection();
  if (choice === "edit") editMessage(d, m);
  else if (choice === "report") reportMessage(d, m);
  else blockFlow(m.from);
}

function buildActions(items) {
  const single = items.length === 1 ? items[0] : null;
  const alive = items.every(({ m }) => !m.deleted);
  const actions = [];
  if (single && alive) {
    actions.push(button("reply", t("msg.reply"), () => {
      exitMessageSelection();
      setReply(preview(single.m), single.d.id);
    }));
  }
  if (alive && items.every(({ m }) => copyableOf(m))) {
    actions.push(button("copy", t("msg.copy"), () => {
      const text = items.map(({ m }) => copyableOf(m)).join("\n");
      exitMessageSelection();
      copyText(text);
    }));
  }
  if (items.every(({ m }) => canForward(m))) {
    actions.push(button("forward", t("msg.forward"), () => {
      const messages = items.map(({ m }) => m);
      exitMessageSelection();
      openForward(messages);
    }));
  }
  actions.push(button("trash", t("msg.delete"), async () => {
    if (await removeMessages(items.map(({ d }) => d))) exitMessageSelection();
  }, true));
  if (single && alive && (!single.mine || editable(single))) {
    actions.push(button("more", t("common.more"), () => openMore(single)));
  }
  return actions;
}

function buildReactions(items) {
  const box = $("msgSelReacts");
  const single = items.length === 1 ? items[0] : null;
  box.hidden = !single || !!single.m.deleted;
  if (box.hidden) return;
  const mineNow = single.m.reactions?.[auth.currentUser.uid];
  box.replaceChildren(...REACTIONS.map(emoji => {
    const b = el("button", mineNow === emoji ? "own" : "", emoji);
    b.type = "button";
    b.onclick = () => {
      exitMessageSelection();
      react(single.d, emoji);
    };
    return b;
  }));
}

function syncBar() {
  const items = sorted();
  const on = items.length > 0;
  $("pane").classList.toggle("selecting", on);
  document.querySelectorAll("#messages .msg").forEach(b => b.classList.toggle("selected", picked.has(b.id.slice(2))));
  if (!on) return;
  $("msgSelClose").title = t("sel.close");
  $("msgSelClose").setAttribute("aria-label", t("sel.close"));
  $("msgSelCount").textContent = t("sel.count", { n: fmtNumber(items.length) });
  $("msgSelActions").replaceChildren(...buildActions(items));
  buildReactions(items);
}

export function toggleMessage(d, m, mine) {
  haptic("tap");
  if (picked.has(d.id)) picked.delete(d.id);
  else picked.set(d.id, { d, m, mine });
  syncBar();
}

export function syncMessageSelection(rendered) {
  order = rendered.map(entry => entry.d.id);
  if (!picked.size) return;
  const live = new Map(rendered.map(entry => [entry.d.id, entry]));
  [...picked.keys()].forEach(id => {
    if (live.has(id)) picked.set(id, live.get(id));
    else picked.delete(id);
  });
  syncBar();
}

export function initMessageSelection() {
  const close = button("close", t("sel.close"), exitMessageSelection);
  close.id = "msgSelClose";
  const count = el("span");
  count.id = "msgSelCount";
  const actions = el("div");
  actions.id = "msgSelActions";
  const reacts = el("div", "selreacts");
  reacts.id = "msgSelReacts";
  reacts.hidden = true;
  $("msgSelBar").append(close, count, actions, reacts);
}
