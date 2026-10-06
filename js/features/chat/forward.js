import { FORWARD_MAX } from "../../config.js";
import { fmtNumber, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth } from "../../core/firebase.js";
import { pic } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { isTextMsg } from "./message-actions.js";
import { peerIdOf } from "./peer.js";
import { send } from "./send.js";
import { toast } from "../../ui/toast.js";

const fwd = { picked: new Set(), payloads: [], busy: false };

const forwardPayload = m => {
  if (isTextMsg(m)) return { type: "text", text: m.text, forwarded: true };
  const p = { type: m.type, url: m.url, forwarded: true };
  if (m.name) p.name = m.name;
  if (m.size) p.size = m.size;
  return p;
};

function forwardTargets() {
  const uid = auth.currentUser.uid;
  const term = $("forwardFind").value.trim().toLowerCase();
  return [...state.chats]
    .filter(c => Array.isArray(c.members) && c.members.includes(uid))
    .sort((a, b) => (b.lastAt?.seconds || 0) - (a.lastAt?.seconds || 0))
    .map(c => {
      if (c.group) {
        const locked = c.adminOnly === true && c.admin !== uid && !(c.admins || []).includes(uid);
        return locked ? null : { id: c.id, name: c.name || "", photo: c.photo || "" };
      }
      const peerId = peerIdOf(c, uid);
      const peer = state.users.get(peerId);
      if (!peer || state.blocked.has(peerId)) return null;
      return { id: c.id, name: peer.name || "", photo: peer.photo || "" };
    })
    .filter(x => x && (!term || x.name.toLowerCase().includes(term)));
}

export function renderForward() {
  const box = $("forwardList");
  box.replaceChildren();
  const list = forwardTargets();
  if (!list.length) box.append(el("p", "hint", t("fwd.none")));
  list.forEach(x => {
    const row = el("div", "pick" + (fwd.picked.has(x.id) ? " on" : ""));
    const img = el("img");
    img.src = pic(x);
    img.alt = "";
    const tick = el("span", "tickbox");
    tick.append(icon("check"));
    row.append(img, el("span", "n", x.name || t("common.user")), tick);
    row.onclick = () => {
      if (fwd.busy) return;
      if (fwd.picked.has(x.id)) {
        fwd.picked.delete(x.id);
      } else if (fwd.picked.size >= FORWARD_MAX) {
        toast(t("fwd.max", { n: fmtNumber(FORWARD_MAX) }));
        return;
      } else {
        fwd.picked.add(x.id);
      }
      renderForward();
    };
    box.append(row);
  });
  $("forwardCount").textContent = fwd.picked.size ? t("group.selected", { n: fmtNumber(fwd.picked.size) }) : t("fwd.pick");
  $("forwardSend").classList.toggle("off", !fwd.picked.size || fwd.busy);
}

export function openForward(messages) {
  fwd.payloads = messages.map(forwardPayload);
  fwd.picked = new Set();
  fwd.busy = false;
  $("forwardFind").value = "";
  renderForward();
  $("forwardSheet").hidden = false;
  $("forwardSheet").querySelector(".gbody").scrollTop = 0;
}

export function closeForward() {
  if ($("forwardSheet").hidden) return;
  $("forwardSheet").hidden = true;
  fwd.payloads = [];
  fwd.picked = new Set();
  fwd.busy = false;
}

export function initForward() {
  $("forwardClose").onclick = closeForward;
  $("forwardFind").oninput = renderForward;
  $("forwardSend").onclick = async () => {
    if (fwd.busy || !fwd.picked.size || !fwd.payloads.length) return;
    if (!navigator.onLine) {
      toast(t("fwd.offline"));
      return;
    }
    fwd.busy = true;
    renderForward();
    const targets = [...fwd.picked].map(id => state.chats.find(c => c.id === id)).filter(Boolean);
    const results = await Promise.allSettled(targets.map(async c => {
      for (const payload of fwd.payloads) await send({ ...payload }, { id: c.id, members: c.members, reply: null });
    }));
    const failed = results.filter(r => r.status === "rejected").length;
    closeForward();
    toast(failed ? t("fwd.partial", { n: fmtNumber(failed) }) : t("fwd.done"));
  };
}
