import { addDoc, collection, serverTimestamp, setDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { blockedRef } from "../block/block.js";
import { nameOf } from "../chat/peer.js";
import { toast } from "../../ui/toast.js";

const REASONS = ["spam", "harassment", "hate", "sexual", "violence", "scam", "other"];
let reportCtx = null;
let reportReason = "";

export function renderReasons() {
  $("reportReasons").replaceChildren(...REASONS.map(key => {
    const b = el("button", "reason" + (reportReason === key ? " on" : ""));
    b.type = "button";
    b.setAttribute("role", "radio");
    b.setAttribute("aria-checked", String(reportReason === key));
    b.append(el("span", "radio"), el("span", "", t("report.reason." + key)));
    b.onclick = () => {
      reportReason = key;
      renderReasons();
      $("reportSubmit").disabled = false;
    };
    return b;
  }));
}

export function openReport(ctx) {
  reportCtx = ctx;
  reportReason = "";
  $("reportTitle").textContent = ctx.title;
  $("reportNote").value = "";
  $("reportBlock").checked = false;
  $("reportBlockRow").hidden = !ctx.targetUid || state.blocked.has(ctx.targetUid);
  $("reportSubmit").disabled = true;
  renderReasons();
  $("reportBox").hidden = false;
  $("reportBox").querySelector(".dialog").scrollTop = 0;
}

export function closeReport() {
  $("reportBox").hidden = true;
  reportCtx = null;
}

export function reportUser(id) {
  if (!state.active) return;
  openReport({ type: "user", chatId: state.active.id, targetUid: id, title: t("report.titleUser", { name: nameOf(id) }) });
}

export function reportGroup() {
  if (!state.active) return;
  openReport({ type: "group", chatId: state.active.id, title: t("report.titleGroup") });
}

export function reportMessage(d, m) {
  if (!state.active) return;
  const media = m.type && m.type !== "text";
  openReport({
    type: "message",
    chatId: state.active.id,
    messageId: d.id,
    targetUid: m.from,
    content: media ? m.url || "" : m.text || "",
    contentType: m.type || "text",
    title: t("report.titleMessage")
  });
}

export function initReport() {
  $("reportCancel").onclick = closeReport;
  $("reportBox").onclick = e => { if (e.target === $("reportBox")) closeReport(); };
  $("reportSubmit").onclick = () => {
    const ctx = reportCtx;
    if (!ctx || !reportReason || !auth.currentUser) return;
    const data = { reporter: auth.currentUser.uid, type: ctx.type, reason: reportReason, chatId: ctx.chatId, createdAt: serverTimestamp() };
    const note = $("reportNote").value.trim().slice(0, 500);
    if (note) data.note = note;
    if (ctx.targetUid) data.reportedUid = ctx.targetUid;
    if (ctx.messageId) data.messageId = ctx.messageId;
    if (ctx.content) data.content = String(ctx.content).slice(0, 1500);
    if (ctx.contentType) data.contentType = ctx.contentType;
    const alsoBlock = !$("reportBlockRow").hidden && $("reportBlock").checked && ctx.targetUid;
    closeReport();
    addDoc(collection(db, "reports"), data).catch(() => toast(t("report.fail")));
    if (alsoBlock) setDoc(blockedRef(ctx.targetUid), { at: serverTimestamp() }).catch(() => {});
    toast(t("report.sent"));
  };
}
