import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth } from "../../core/firebase.js";
import { clock } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { preview } from "../../core/message-format.js";
import { blockFlow } from "../block/block.js";
import { openForward } from "./forward.js";
import { canEdit, canForward, copyText, editMessage, hideForMe, removeMessage } from "./message-actions.js";
import { react } from "./reactions.js";
import { setReply } from "./reply.js";
import { reportMessage } from "../report/report.js";

export const menuBox = $("msgMenu");
export const closeMenu = () => { menuBox.hidden = true; };

export function openMenu(d, m, mine) {
  const uid = auth.currentUser.uid;
  const reacts = $("menuReacts");
  reacts.replaceChildren();
  if (!m.deleted) {
    ["👍", "❤️", "😂", "😮", "🙏"].forEach(em => {
      const r = el("button", m.reactions?.[uid] === em ? "own" : "", em);
      r.onclick = () => { closeMenu(); react(d, em); };
      reacts.append(r);
    });
  }
  reacts.hidden = !!m.deleted;

  const prev = $("menuPreview");
  prev.className = "mbubble " + (mine ? "mine" : "theirs") + (m.deleted ? " gone" : "");
  const body = el("p", "", m.deleted ? t("chat.deleted") : preview(m));
  const time = el("small", "", (mine ? t("common.you") : state.users.get(m.from)?.name || "") + (m.at ? " · " + clock(m.at) : ""));
  prev.replaceChildren(body, time);

  const items = [];
  const tile = (name, label, fn, danger) => {
    const b = el("button", "tile" + (danger ? " danger" : ""));
    const wrap = el("span", "tico");
    wrap.append(icon(name));
    b.append(wrap, el("span", "tlabel", label));
    b.onclick = () => { closeMenu(); fn(); };
    items.push(b);
  };
  if (m.deleted) {
    tile("trash", t("msg.deleteForMe"), () => hideForMe(d), true);
  } else {
    tile("reply", t("msg.reply"), () => setReply(preview(m), d.id));
    const copyable = m.type === "text" || !m.type ? m.text : m.url;
    if (copyable) tile("copy", t("msg.copy"), () => copyText(copyable));
    if (canForward(m)) tile("forward", t("msg.forward"), () => openForward(m));
    if (mine && !d.metadata.hasPendingWrites && canEdit(m)) tile("edit", t("msg.edit"), () => editMessage(d, m));
    tile("trash", t("msg.delete"), () => removeMessage(d), true);
    if (!mine) {
      tile("flag", t("report.message"), () => reportMessage(d, m), true);
      if (state.active?.group) tile("ban", t("block.action"), () => blockFlow(m.from), true);
    }
  }
  const grid = $("menuItems");
  grid.style.setProperty("--n", Math.min(items.length, 4));
  grid.replaceChildren(...items);
  menuBox.hidden = false;
}

export function initMessageMenu() {
  menuBox.onclick = e => { if (e.target === menuBox) closeMenu(); };
}
