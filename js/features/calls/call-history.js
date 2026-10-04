import { collection, onSnapshot, query, where } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { listTime, pic } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { calls } from "./calls.js";
import { renderList } from "../chat-list/chat-list.js";
import { openChat } from "../chat/chat-session.js";
import { watchUser } from "../contacts/user-watch.js";

export function watchCallHistory() {
  if (state.callHistoryOn || !auth.currentUser) return;
  state.callHistoryOn = true;
  const uid = auth.currentUser.uid;
  const take = s => {
    s.docChanges().forEach(ch => {
      if (ch.type === "removed") state.callDocs.delete(ch.doc.id);
      else state.callDocs.set(ch.doc.id, { id: ch.doc.id, ...ch.doc.data() });
    });
    if (state.filter === "calls") renderList();
  };
  const fail = () => { state.callHistoryOn = false; };
  state.unsubs.push(onSnapshot(query(collection(db, "calls"), where("caller", "==", uid)), take, fail));
  state.unsubs.push(onSnapshot(query(collection(db, "calls"), where("callee", "==", uid)), take, fail));
}

const stamp = ms => ({ toDate: () => new Date(ms) });

function callEntry(c, uid) {
  const out = c.caller === uid;
  const peer = out ? c.callee : c.caller;
  const at = c.createdAt?.toMillis?.() || Date.now();
  const label = t(c.video ? "call.video" : "call.voice");
  let kind;
  if (c.answer) kind = "done";
  else if (c.status === "ringing") kind = Date.now() - at < 90000 ? "ringing" : "missed";
  else if (c.status === "declined") kind = out ? "declined" : (c.endReason === "busy" ? "missed" : "declined");
  else if (c.status === "missed") kind = "missed";
  else kind = out ? "cancelled" : "missed";
  const secs = Number(c.secs) || 0;
  let text;
  if (kind === "done") text = secs ? label + " · " + Math.floor(secs / 60) + ":" + String(secs % 60).padStart(2, "0") : label;
  else if (kind === "declined") text = t("call.logDeclined", { label });
  else if (kind === "cancelled") text = t("call.logCancelled", { label });
  else if (out) text = label + " · " + t("call.noAnswer");
  else text = t("call.logMissed", { label });
  return { id: c.id, out, peer, at, video: !!c.video, kind, text, chatId: c.chatId, bad: kind === "missed" && !out };
}

function callRow(e) {
  const u = state.users.get(e.peer) || { name: "" };
  const r = el("div", "row callrow" + (e.bad ? " missed" : ""));
  const av = el("div", "av");
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  av.append(img);
  const body = el("div", "body");
  const top = el("div", "top");
  top.append(el("b", "", u.name || t("common.user")), el("time", "", listTime(stamp(e.at))));
  const bot = el("div", "bot");
  const sub = el("span", "sub csub");
  sub.append(icon(e.out ? "arrowOut" : "arrowIn", "dir"), el("span", "", e.text));
  const again = el("button", "callagain");
  again.title = t("calls.callBack");
  again.setAttribute("aria-label", t("calls.callBack"));
  again.append(icon(e.video ? "video" : "phone"));
  again.onclick = ev => {
    ev.stopPropagation();
    calls.startCall(e.video, { id: e.chatId, peer: e.peer, group: false });
  };
  bot.append(sub, again);
  body.append(top, bot);
  r.append(av, body);
  r.onclick = () => {
    const peer = state.users.get(e.peer);
    if (peer) openChat(peer);
  };
  return r;
}

export function renderCalls(list, uid, hit) {
  const entries = [...state.callDocs.values()]
    .map(c => callEntry(c, uid))
    .filter(e => e.kind !== "ringing" && e.peer && !state.blocked.has(e.peer))
    .sort((a, b) => b.at - a.at)
    .slice(0, 100);
  entries.forEach(e => { if (!state.users.has(e.peer)) watchUser(e.peer); });
  const shown = entries.filter(e => state.users.has(e.peer) && hit(state.users.get(e.peer).name));
  shown.forEach(e => list.append(callRow(e)));
  if (shown.length) return;
  list.append(el("p", "hint", t($("search").value.trim() ? "list.noMatch" : "calls.noCalls")));
}
