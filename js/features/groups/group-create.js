import { addDoc, collection, doc, getDoc, serverTimestamp } from "../../core/sdk.js";
import { applyStatic, fmtNumber, stored, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { emailRe } from "../../core/constants.js";
import { $, el } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { pic } from "../../core/format.js";
import { icon } from "../../core/icons.js";
import { compressImage } from "../../core/image.js";
import { upload } from "../../core/upload.js";
import { openChat } from "../chat/chat-session.js";
import { watchUser } from "../contacts/user-watch.js";
import { addGroupMembers } from "./group-admin.js";
import { toast } from "../../ui/toast.js";

const GROUP_MAX = 256;

let groupCover = null;
let groupCoverUrl = "";

let groupExtra = new Map();
let groupPicked = new Set();
let groupFindTimer;
let groupFindToken = 0;
let groupSheetMode = "create";
const groupUser = id => state.users.get(id) || groupExtra.get(id);

async function lookupEmail(term) {
  try {
    const hit = await getDoc(doc(db, "emailLookup", term));
    if (!hit.exists()) return { status: "none" };
    if (hit.data().uid === auth.currentUser.uid) return { status: "self" };
    const p = await getDoc(doc(db, "users", hit.data().uid));
    if (!p.exists()) return { status: "none" };
    if (state.blocked.has(p.id)) return { status: "blocked" };
    state.users.set(p.id, p.data());
    watchUser(p.id);
    return { status: "found", user: p.data() };
  } catch {
    return { status: "error" };
  }
}

function groupContacts() {
  const me = auth.currentUser.uid;
  const ids = new Set(state.chats.filter(c => !c.group).map(c => c.members.find(m => m !== me)));
  groupExtra.forEach((u, id) => ids.add(id));
  const existing = groupSheetMode === "add" && state.active?.group ? new Set(state.active.data?.members || []) : null;
  return [...ids].filter(id => !state.blocked.has(id) && !(existing && existing.has(id))).map(id => state.users.get(id) || groupExtra.get(id)).filter(Boolean);
}

let lastPicked = 0;

export function syncGroupUi() {
  const strip = $("picked");
  strip.replaceChildren();
  groupPicked.forEach(id => {
    const u = groupUser(id);
    if (!u) return;
    const pk = el("div", "pk");
    const img = el("img");
    img.src = pic(u);
    img.alt = "";
    const x = el("i");
    x.append(icon("close"));
    pk.append(img, x, el("span", "", u.name || t("common.user")));
    pk.onclick = () => {
      groupPicked.delete(id);
      renderMembers();
    };
    strip.append(pk);
  });
  strip.hidden = !groupPicked.size;
  $("pickedSec").hidden = !groupPicked.size;
  if (groupPicked.size > lastPicked) requestAnimationFrame(() => strip.scrollTo({ left: strip.scrollWidth, behavior: "smooth" }));
  lastPicked = groupPicked.size;
  $("sheetCount").textContent = groupPicked.size ? t("group.selected", { n: fmtNumber(groupPicked.size) }) : t("group.pickMembers");
  $("sheetDone").classList.toggle("off", groupSheetMode === "add" ? !groupPicked.size : !($("groupName").value.trim() && groupPicked.size));
}

function renderMembers() {
  const box = $("members");
  box.replaceChildren();
  const contacts = groupContacts();
  $("membersLabel").hidden = !contacts.length;
  if (!contacts.length) box.append(el("p", "hint", t("group.noContacts")));
  contacts.forEach(u => {
    const row = el("div", "pick" + (groupPicked.has(u.uid) ? " on" : ""));
    const img = el("img");
    img.src = pic(u);
    img.alt = "";
    const tick = el("span", "tickbox");
    tick.append(icon("check"));
    row.append(img, el("span", "n", u.name || t("common.user")), tick);
    row.onclick = () => {
      if (groupPicked.has(u.uid)) groupPicked.delete(u.uid);
      else groupPicked.add(u.uid);
      renderMembers();
    };
    box.append(row);
  });
  syncGroupUi();
}

function renderGroupFind(r) {
  const box = $("groupFindResult");
  box.replaceChildren();
  if (!r) return;
  const notes = {
    invalid: t("find.invalid"),
    loading: t("find.loading"),
    self: t("find.self"),
    error: t("find.error"),
    none: t("find.none"),
    blocked: t("block.inGroup")
  };
  if (r.status !== "found") {
    box.append(el("p", "hint", notes[r.status]));
    return;
  }
  const u = r.user;
  if (groupSheetMode === "add" && state.active?.group && (state.active.data?.members || []).includes(u.uid)) {
    box.append(el("p", "hint", t("group.alreadyIn")));
    return;
  }
  const already = groupPicked.has(u.uid);
  const row = el("div", "pick found");
  const img = el("img");
  img.src = pic(u);
  img.alt = "";
  row.append(img, el("span", "n", u.name || t("common.user")), el("span", "addpill" + (already ? " done" : ""), already ? t("find.added") : t("find.add")));
  row.onclick = () => {
    groupExtra.set(u.uid, u);
    groupPicked.add(u.uid);
    $("groupFind").value = "";
    groupFindToken++;
    renderGroupFind(null);
    renderMembers();
  };
  box.append(row);
}

export const openGroupSheet = mode => {
  groupSheetMode = mode === "add" ? "add" : "create";
  const add = groupSheetMode === "add";
  $("gnameRow").hidden = add;
  $("sheetTitle").dataset.i18n = add ? "group.addTitle" : "menu.newGroup";
  $("sheetDone").dataset.i18n = add ? "group.add" : "group.create";
  applyStatic($("sheet"));
  $("groupName").value = "";
  setGroupCover(null);
  $("groupFind").value = "";
  groupExtra = new Map();
  groupPicked = new Set();
  lastPicked = 0;
  groupFindToken++;
  renderGroupFind(null);
  renderMembers();
  $("sheet").querySelector(".gbody").scrollTop = 0;
  $("sheet").hidden = false;
};
function setGroupCover(file) {
  if (groupCoverUrl) URL.revokeObjectURL(groupCoverUrl);
  groupCover = file;
  groupCoverUrl = file ? URL.createObjectURL(file) : "";
  const img = $("gcoverImg");
  img.hidden = !groupCoverUrl;
  if (groupCoverUrl) img.src = groupCoverUrl;
  else img.removeAttribute("src");
  $("gcoverBtn").classList.toggle("has", !!groupCoverUrl);
}

export function initGroupCreate() {
  $("groupFind").oninput = () => {
    clearTimeout(groupFindTimer);
    const term = $("groupFind").value.trim().toLowerCase();
    const token = ++groupFindToken;
    if (!term) {
      renderGroupFind(null);
      return;
    }
    if (!emailRe.test(term)) {
      renderGroupFind({ status: "invalid" });
      return;
    }
    renderGroupFind({ status: "loading" });
    groupFindTimer = setTimeout(async () => {
      const r = await lookupEmail(term);
      if (token === groupFindToken) renderGroupFind(r);
    }, 300);
  };
  $("gcoverBtn").onclick = () => $("groupCoverInput").click();
  $("groupCoverInput").onchange = e => {
    const file = e.target.files[0];
    e.target.value = "";
    if (file && file.type.startsWith("image/")) setGroupCover(file);
  };
  $("groupName").oninput = syncGroupUi;
  $("sheetClose").onclick = () => { $("sheet").hidden = true; };
  $("sheetDone").onclick = async () => {
    if (groupSheetMode === "add") {
      if (!state.active?.group || !groupPicked.size) return;
      const have = new Set(state.active.data?.members || []);
      const ids = [...groupPicked].filter(id => !have.has(id));
      if (!ids.length) return;
      if (have.size + ids.length > GROUP_MAX) {
        toast(t("group.full"));
        return;
      }
      const names = ids.map(id => groupUser(id)?.name || t("common.user"));
      $("sheet").hidden = true;
      addGroupMembers(state.active.id, ids, names).then(() => toast(t("group.added"))).catch(() => toast(t("group.actionFail")));
      return;
    }
    const uid = auth.currentUser.uid;
    const name = $("groupName").value.trim();
    const picked = [...groupPicked];
    if (!name || !picked.length) {
      toast(t("group.needName"));
      return;
    }
    const members = [uid, ...picked];
    let photo = "";
    if (groupCover) {
      if (!navigator.onLine) {
        toast(t("chat.offlineMedia"));
        return;
      }
      $("sheetDone").classList.add("off");
      toast(t("photo.uploading"), true);
      try {
        photo = (await upload(await compressImage(groupCover, 640, 0.85))).secure_url;
      } catch (err) {
        toast(err.message || t("group.actionFail"));
        syncGroupUi();
        return;
      }
      toast("");
    }
    const data = { group: true, name, admin: uid, admins: [uid], members, lastMessage: stored("groupCreated"), lastFrom: uid, lastAt: serverTimestamp() };
    if (photo) data.photo = photo;
    const ref = await addDoc(collection(db, "chats"), data);
    $("sheet").hidden = true;
    setGroupCover(null);
    openChat(null, { id: ref.id, name, members, photo });
  };
}
