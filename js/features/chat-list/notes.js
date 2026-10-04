import { deleteField, doc, serverTimestamp, updateDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { pic, tsMs } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { openChat } from "../chat/chat-session.js";
import { peerIdOf } from "../chat/peer.js";
import { isOnline, serverNow } from "../presence/presence.js";
import { openNoteView } from "./note-view.js";
import { askEdit } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

const NOTE_MAX = 60;
const NOTE_TTL_MS = 86400000;

const noteOf = u => {
  const text = (u?.note || "").trim();
  if (!text) return "";
  const at = tsMs(u.noteAt) || serverNow();
  return serverNow() - at < NOTE_TTL_MS ? text : "";
};

let stripSig = "";

function stripItem(id, u, mine) {
  const note = noteOf(u);
  const item = el("button", "sitem");
  item.type = "button";
  const av = el("div", "av");
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  av.append(img);
  if (mine) av.append(el("span", "splus"));
  else if (isOnline(u)) av.append(el("span", "pb dot"));
  if (mine) av.querySelector(".splus").append(icon("plus"));
  const first = (u?.name || "").trim().split(/\s+/)[0] || t("common.user");
  const parts = [];
  if (note || mine) {
    const bubble = el("span", "snote" + (note ? "" : " add"));
    bubble.append(el("span", "stext", note || t("note.add")));
    parts.push(bubble);
  }
  item.append(...parts, av, el("span", "sname", mine ? t("common.you") : first));
  item.onclick = e => {
    if (mine) editMyNote();
    else if (note && e.target.closest(".snote")) openNoteView({ user: u, name: (u?.name || "").trim() || t("common.user"), text: note, message: () => openChat(u) });
    else openChat(u);
  };
  return item;
}

export function renderStrip() {
  const strip = $("activeStrip");
  const uid = auth.currentUser?.uid;
  const show = !!uid && (state.filter === "all" || state.filter === "unread") && !$("search").value.trim();
  strip.hidden = !show;
  if (!show) return;
  const seen = new Set();
  const peers = [];
  state.chats.forEach(c => {
    if (c.group || !Array.isArray(c.members) || c.members.length !== 2) return;
    const id = peerIdOf(c, uid);
    if (!id || seen.has(id) || state.blocked.has(id)) return;
    const u = state.users.get(id);
    if (!u) return;
    seen.add(id);
    const online = isOnline(u);
    if (online || noteOf(u)) peers.push({ id, u, online });
  });
  peers.sort((a, b) => Number(b.online) - Number(a.online) || tsMs(b.u.noteAt) - tsMs(a.u.noteAt));
  const entries = [{ id: uid, u: state.users.get(uid), mine: true }, ...peers.map(p => ({ id: p.id, u: p.u, mine: false }))];
  const sig = [t("common.you"), t("note.add"), ...entries.map(e => [e.id, e.u?.name || "", pic(e.u), noteOf(e.u), e.mine ? "" : isOnline(e.u) ? 1 : 0].join("\u0001"))].join("\u0002");
  if (sig === stripSig && strip.childElementCount) return;
  stripSig = sig;
  const left = strip.scrollLeft;
  strip.replaceChildren(...entries.map(e => stripItem(e.id, e.u, e.mine)));
  strip.scrollLeft = left;
}

async function editMyNote() {
  const uid = auth.currentUser?.uid;
  if (!uid || state.deleting) return;
  const cur = noteOf(state.users.get(uid));
  const v = await askEdit({ title: t("note.title"), value: cur, max: NOTE_MAX, multiline: false, placeholder: t("note.placeholder") });
  if (v === null || v === cur) return;
  const patch = v ? { note: v, noteAt: serverTimestamp() } : { note: deleteField(), noteAt: deleteField() };
  updateDoc(doc(db, "users", uid), patch).then(() => toast(t(v ? "note.saved" : "note.removed"))).catch(() => toast(t("note.fail")));
}
