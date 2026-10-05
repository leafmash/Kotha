import { t } from "../../i18n.js";
import { setBadgeCount } from "../../native.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth } from "../../core/firebase.js";
import { listTime } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { lastText } from "../../core/message-format.js";
import { renderCalls, watchCallHistory } from "../calls/call-history.js";
import { isCleared } from "../chat/cleared.js";
import { renderStrip } from "./notes.js";
import { initSearchCollapse } from "./search-collapse.js";
import { row } from "./row.js";
import { draftOf, openChat } from "../chat/chat-session.js";
import { isArchived, isPinned, openRowMenu } from "../chat/pin-archive.js";
import { isMuted } from "../chat/mute.js";
import { peerIdOf } from "../chat/peer.js";
import { unreadOf } from "../chat/read-state.js";
import { openGroupSheet } from "../groups/group-create.js";
import { openProfile } from "../profile/profile.js";

export function renderList() {
  const list = $("list");
  list.replaceChildren();
  if (!auth.currentUser) return;
  renderStrip();
  const uid = auth.currentUser.uid;
  const term = $("search").value.trim().toLowerCase();
  const hit = name => !term || (name || "").toLowerCase().includes(term);
  const totalUnread = state.chats.reduce((n, c) => n + (isMuted(c.id) || isCleared(c) || (!c.group && state.blocked.has(peerIdOf(c, uid))) ? 0 : unreadOf(c, uid)), 0);
  if (state.blockedReady) setBadgeCount(totalUnread);
  document.title = (totalUnread ? `(${totalUnread}) ` : "") + "Cova";
  $("railBadge").hidden = !totalUnread;
  $("railBadge").textContent = totalUnread > 99 ? "99+" : totalUnread;
  $("tabBadge").hidden = !totalUnread;
  $("tabBadge").textContent = totalUnread > 99 ? "99+" : totalUnread;

  if (state.filter === "calls") {
    renderCalls(list, uid, hit);
    return;
  }

  if (state.filter === "group" && !term) {
    const r = el("div", "row newgroup");
    const av = el("div", "av");
    const ic = el("div", "ngicon");
    ic.append(icon("plus"));
    av.append(ic);
    const body = el("div", "body");
    body.append(el("b", "", t("list.newGroupTitle")), el("span", "sub", t("list.newGroupSub")));
    r.append(av, body);
    r.onclick = () => openGroupSheet();
    list.append(r);
  }

  let count = 0;
  const archivedView = state.filter === "archived";
  const pinFirst = !term && !archivedView;
  const draftFor = c => (state.active?.id === c.id ? "" : draftOf(c.id));
  const archivedUnread = state.chats.some(c => c.lastMessage && !isCleared(c) && isArchived(c.id) && !isMuted(c.id) && unreadOf(c, uid) > 0);
  $("chips").querySelector('[data-f="archived"]').classList.toggle("has", archivedUnread);
  [...state.chats]
    .filter(c => c.lastMessage && !isCleared(c))
    .filter(c => term || (archivedView ? isArchived(c.id) : !isArchived(c.id)))
    .filter(c => state.filter === "all" || archivedView || (state.filter === "group" ? c.group : unreadOf(c, uid) > 0))
    .sort((a, b) => (pinFirst ? Number(isPinned(b.id)) - Number(isPinned(a.id)) : 0) || (b.lastAt?.seconds || 0) - (a.lastAt?.seconds || 0))
    .forEach(c => {
      if (c.group) {
        if (!hit(c.name)) return;
        const g = { uid: c.id, name: c.name, photo: c.photo || "" };
        const hiddenLast = state.blocked.has(c.lastFrom);
        let who = "";
        if (c.lastFrom === uid) who = t("list.youPrefix");
        else if (c.lastFrom && !hiddenLast) who = (state.users.get(c.lastFrom)?.name || "") + ": ";
        const last = hiddenLast ? t("block.hiddenMessage") : lastText(c.lastMessage);
        const draft = draftFor(c);
        list.append(row(g, draft || who + last, listTime(c.lastAt), unreadOf(c, uid), () => openChat(null, c), state.active?.id === c.id, isMuted(c.id), () => openRowMenu(c.id, c.name), { pinned: isPinned(c.id), draft: !!draft }));
        count++;
        return;
      }
      const peerId = peerIdOf(c, uid);
      const peer = state.users.get(peerId);
      if (!peer || state.blocked.has(peerId) || !hit(peer.name)) return;
      const sub = (c.lastFrom === uid ? t("list.youPrefix") : "") + lastText(c.lastMessage);
      const draft = draftFor(c);
      list.append(row(peer, draft || sub, listTime(c.lastAt), unreadOf(c, uid), () => openChat(peer), state.active?.peer === peer.uid, isMuted(c.id), () => openRowMenu(c.id, peer.name), { pinned: isPinned(c.id), draft: !!draft }));
      count++;
    });

  if (count) return;
  let msg;
  if (term) msg = t("list.noMatch");
  else if (state.filter === "group") msg = t("list.noGroups");
  else if (archivedView) msg = t("list.noArchived");
  else if (state.filter === "unread") msg = t("list.noUnread");
  else msg = t("list.noChats");
  list.append(el("p", "hint", msg));
}

function syncFilterUi() {
  $("chips").querySelectorAll("button").forEach(x => {
    const on = x.dataset.f === state.filter;
    x.classList.toggle("on", on);
    x.setAttribute("aria-pressed", String(on));
  });
  document.querySelectorAll("#rail [data-f]").forEach(x => x.classList.toggle("on", x.dataset.f === state.filter));
  document.querySelectorAll("#tabs [data-f]").forEach(x => {
    const on = x.dataset.f === (state.filter === "group" || state.filter === "calls" ? state.filter : "all");
    x.classList.toggle("on", on);
    if (on) x.setAttribute("aria-current", "page");
    else x.removeAttribute("aria-current");
  });
  $("chips").hidden = state.filter === "group" || state.filter === "calls";
  if (state.filter === "calls") watchCallHistory();
}

export function initChatList() {
  initSearchCollapse();
  $("search").oninput = renderList;
  $("chips").onclick = e => {
    const b = e.target.closest("button");
    if (!b) return;
    state.filter = b.dataset.f;
    syncFilterUi();
    renderList();
  };
  document.querySelectorAll("#tabs [data-f]").forEach(b => {
    b.onclick = () => {
      state.filter = b.dataset.f;
      syncFilterUi();
      renderList();
    };
  });
  document.querySelectorAll("#rail [data-f]").forEach(b => {
    b.onclick = () => {
      state.filter = state.filter === b.dataset.f && state.filter !== "all" ? "all" : b.dataset.f;
      syncFilterUi();
      renderList();
    };
  });
  $("railMe").onclick = () => openProfile();
}
