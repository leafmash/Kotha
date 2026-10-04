import { doc, getDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { clock, dayLabel, palette } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { linkify } from "../../core/linkify.js";
import { callEventParts, callLogText } from "../../core/message-format.js";
import { calls } from "../calls/calls.js";
import { messageCleared } from "./cleared.js";
import { attachGestures } from "./gestures.js";
import { clearReply } from "./reply.js";
import { sysText } from "../groups/group-admin.js";
import { openUserProfile } from "../profile/user-profile.js";

export const goneCache = new Map();

export function renderMessages(all) {
  const uid = auth.currentUser.uid;
  const wiped = all.some(d => messageCleared(state.active.id, d.data()));
  if (wiped) state.active.hasMore = false;
  const docs = all.filter(d => !(d.data().hiddenFor || []).includes(uid) && !messageCleared(state.active.id, d.data()));
  const byId = new Map(all.map(d => [d.id, d.data()]));
  if (state.active.reply?.id && byId.get(state.active.reply.id)?.deleted) clearReply();
  const box = $("messages");
  const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 140;
  const prevHeight = box.scrollHeight;
  const prevTop = box.scrollTop;
  box.replaceChildren();
  let lastDay = "";
  let prevFrom = null;
  let hiddenRun = false;
  state.active.lastId = docs.length ? docs[docs.length - 1].id : null;

  docs.forEach(d => {
    const m = d.data({ serverTimestamps: "estimate" });
    const mine = m.from === uid;
    const queued = mine && d.metadata.hasPendingWrites && !d.data().at;
    const date = m.at?.toDate?.();
    if (date && date.toDateString() !== lastDay) {
      lastDay = date.toDateString();
      box.append(el("div", "day", dayLabel(date)));
      prevFrom = null;
    }
    if (m.type === "system" && m.sys && typeof m.sys === "object" && !m.deleted) {
      const ev = el("div", "sysev", sysText(m.sys, m.from));
      ev.id = "m-" + d.id;
      box.append(ev);
      prevFrom = null;
      return;
    }
    if (state.active.group && !mine && state.blocked.has(m.from)) {
      if (!hiddenRun) box.append(el("div", "day hidden-note", t("block.hiddenMessage")));
      hiddenRun = true;
      prevFrom = null;
      return;
    }
    hiddenRun = false;
    if (m.callLog && typeof m.callLog === "object" && !m.deleted) {
      const log = m.callLog;
      const bad = !mine && ["missed", "cancelled"].includes(log.kind);
      const ev = el("div", "callev " + (mine ? "mine" : "theirs") + (bad ? " bad" : ""));
      ev.id = "m-" + d.id;
      const parts = callEventParts(log, mine);
      const cic = el("span", "cic");
      cic.append(icon(log.video ? "video" : "phone"));
      const cbody = el("span", "cbody");
      const cmeta = el("span", "cmeta");
      cmeta.append(icon(mine ? "arrowOut" : "arrowIn", "dir"), el("span", "", clock(m.at) + (parts.sub ? " · " + parts.sub : "")));
      cbody.append(el("b", "", parts.title), cmeta);
      ev.append(cbody, cic);
      ev.onclick = () => {
        if (!state.active || state.active.group || $("hcalls").hidden) return;
        calls.startCall(!!log.video, state.active);
      };
      box.append(ev);
      prevFrom = null;
      return;
    }
    const b = el("div", "msg " + (mine ? "mine" : "theirs") + (prevFrom !== m.from ? " first" : ""));
    b.id = "m-" + d.id;
    prevFrom = m.from;
    if (state.active.group && !mine) {
      const sender = el("div", "sender", state.users.get(m.from)?.name || "?");
      sender.style.color = palette[[...m.from].reduce((a, ch) => a + ch.charCodeAt(0), 0) % palette.length];
      sender.onclick = e => {
        e.stopPropagation();
        openUserProfile(m.from);
      };
      b.append(sender);
    }
    const rid = m.replyTo?.id;
    const inWindow = rid ? byId.get(rid) : null;
    const quoteGone = rid ? (inWindow ? !!inWindow.deleted : goneCache.get(state.active.id + "/" + rid) === true) : false;
    if (m.replyTo && !m.deleted && !quoteGone) {
      const q = el("div", "quote", m.replyTo.text);
      if (rid) {
        q.dataset.reply = rid;
        q.onclick = () => {
          const target = $("m-" + rid);
          if (!target) return;
          target.scrollIntoView({ behavior: "smooth", block: "center" });
          target.classList.add("flash");
          setTimeout(() => target.classList.remove("flash"), 1300);
        };
        if (!inWindow && !goneCache.has(state.active.id + "/" + rid)) checkGone(state.active.id, rid);
      }
      b.append(q);
    }

    if (m.forwarded && !m.deleted) {
      const f = el("div", "fwd");
      f.append(icon("forward"), " " + t("msg.forwarded"));
      b.append(f);
    }
    if (m.deleted) {
      const gone = el("em", "gone");
      gone.append(icon("ban"), " " + t("chat.deleted"));
      b.append(gone);
    } else if (m.type === "image") {
      const img = el("img", "media");
      img.src = m.url;
      img.alt = t("common.photo");
      img.loading = "lazy";
      img.onclick = () => { $("lightbox").querySelector("img").src = m.url; $("lightbox").hidden = false; };
      b.append(img);
    } else if (m.type === "video") {
      const v = el("video");
      v.src = m.url;
      v.controls = true;
      v.preload = "metadata";
      b.append(v);
    } else if (m.type === "audio") {
      const a = el("audio");
      a.src = m.url;
      a.controls = true;
      b.append(a);
    } else if (m.type === "file") {
      const a = el("a", "file");
      a.append(icon("clip"), " " + (m.name || t("common.file")));
      a.href = m.url;
      a.target = "_blank";
      a.rel = "noopener";
      b.append(a);
    } else {
      const p = el("p");
      p.append(linkify(m.callLog ? callLogText(m.callLog) : m.text));
      b.append(p);
    }

    const picks = Object.values(m.reactions || {});
    if (picks.length && !m.deleted) {
      const bar = el("div", "reacts" + (m.reactions[uid] ? " own" : ""));
      bar.append(el("span", "", [...new Set(picks)].slice(0, 3).join("")));
      if (picks.length > 1) bar.append(el("small", "", String(picks.length)));
      b.append(bar);
      b.classList.add("hasreact");
    }
    const meta = el("div", "meta");
    if (m.edited && !m.deleted) meta.append(el("span", "edited", t("msg.edited")));
    meta.append(el("span", "", clock(m.at)));
    if (mine && !m.deleted) {
      const seen = m.status === "seen";
      const reached = seen || m.status === "delivered";
      const tick = el("span", seen ? "seen" : "");
      tick.append(icon(queued ? "clock" : reached ? "checks" : "check", "tick"));
      meta.append(tick);
    }
    b.append(meta);

    attachGestures(b, d, m, mine);
    box.append(b);
  });

  if (state.active.olderLoad) {
    box.scrollTop = box.scrollHeight - prevHeight + prevTop;
    state.active.olderLoad = false;
  } else if (nearBottom || state.active.first) {
    box.scrollTop = box.scrollHeight;
    if (docs.length) state.active.first = false;
  }
}

async function checkGone(chatId, rid) {
  const key = chatId + "/" + rid;
  goneCache.set(key, false);
  try {
    const s = await getDoc(doc(db, "chats", chatId, "messages", rid));
    const gone = !s.exists() || !!s.data().deleted;
    goneCache.set(key, gone);
    if (gone && state.active?.id === chatId) document.querySelectorAll(`.quote[data-reply="${rid}"]`).forEach(q => q.remove());
  } catch {
    goneCache.delete(key);
  }
}
