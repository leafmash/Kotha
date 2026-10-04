import { doc, updateDoc } from "../../core/sdk.js";
import { fmtNumber, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { pic } from "../../core/format.js";
import { compressImage } from "../../core/image.js";
import { upload } from "../../core/upload.js";
import { openChat } from "../chat/chat-session.js";
import { groupAdminIds, groupBatch, iAmAdmin, memberName, removeGroupMember, runGroup, setGroupAdmin } from "./group-admin.js";
import { openGroupSheet } from "./group-create.js";
import { leaveGroup } from "./group-leave.js";
import { openUserProfile } from "../profile/user-profile.js";
import { reportGroup } from "../report/report.js";
import { askChoice, askConfirm, askEdit } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

export function renderGroupInfo() {
  if (!state.active?.group) return;
  const g = state.active.data || {};
  const uid = auth.currentUser.uid;
  const admin = iAmAdmin(g);
  const admins = groupAdminIds(g);
  const ids = [...(g.members || [])].sort((a, b) => admins.includes(b) - admins.includes(a));
  $("infoImg").src = pic({ name: g.name, photo: g.photo });
  $("infoAvatar").classList.toggle("edit", admin);
  $("infoCam").hidden = !admin;
  $("infoName").textContent = g.name || "";
  $("infoTitleRow").classList.toggle("edit", admin);
  $("infoNameEdit").hidden = !admin;
  $("infoCount").textContent = t("chat.members", { n: fmtNumber(ids.length) });
  const desc = (g.description || "").trim();
  const d = $("infoDesc");
  d.textContent = desc || (admin ? t("ginfo.addDescription") : t("ginfo.noDescription"));
  d.className = "gdesc" + (desc ? "" : admin ? " edit" : " empty");
  $("infoAdminOnly").hidden = !admin;
  $("infoAdminOnlySwitch").classList.toggle("on", g.adminOnly === true);
  $("infoMembersLabel").textContent = t("ginfo.membersLabel", { n: fmtNumber(ids.length) });
  $("infoAdd").hidden = !admin;
  const box = $("infoMembers");
  box.replaceChildren();
  ids.forEach(id => {
    const u = state.users.get(id);
    const row = el("div", "mrow" + (id === uid ? " nohit" : ""));
    const img = el("img");
    img.src = pic(u || { name: "?" });
    img.alt = "";
    const nm = el("div", "mn");
    nm.append(el("span", "", u?.name || t("common.user")));
    if (id === uid) nm.append(el("em", "", "(" + t("common.you") + ")"));
    if (admins.includes(id)) nm.append(el("span", "apill", t("group.admin")));
    row.append(img, nm);
    if (id !== uid) row.onclick = () => memberActions(id);
    box.append(row);
  });
}

export function openGroupInfo() {
  if (!state.active?.group) return;
  renderGroupInfo();
  $("infoSheet").querySelector(".gbody").scrollTop = 0;
  $("infoSheet").hidden = false;
}

async function memberActions(id) {
  if (!state.active?.group) return;
  const chatId = state.active.id;
  const g = state.active.data || {};
  const admin = iAmAdmin(g);
  const isAdminTarget = groupAdminIds(g).includes(id);
  const isOwner = g.admin === id;
  const name = memberName(id);
  const options = [];
  if (state.users.get(id) && !state.blocked.has(id)) options.push({ label: t("member.message", { name }), value: "msg", kind: "dnorm" });
  if (admin && !isOwner) {
    options.push({ label: t(isAdminTarget ? "member.removeAdmin" : "member.makeAdmin"), value: isAdminTarget ? "demote" : "promote", kind: "dnorm" });
    options.push({ label: t("member.remove"), value: "remove", kind: "dok" });
  }
  options.push({ label: t("common.cancel"), value: null, kind: "dcancel" });
  if (options.length === 1) return;
  const choice = await askChoice({ title: name, text: isAdminTarget ? t("group.admin") : "", iconName: "users", options });
  if (!choice || !state.active?.group || state.active.id !== chatId) return;
  if (choice === "msg") {
    $("infoSheet").hidden = true;
    openChat(state.users.get(id));
  } else if (choice === "promote" || choice === "demote") {
    runGroup(() => setGroupAdmin(chatId, id, choice === "promote"));
  } else if (choice === "remove") {
    const ok = await askConfirm({ title: t("member.removeTitle", { name }), text: t("member.removeText"), ok: t("member.remove"), iconName: "logout" });
    if (ok) runGroup(() => removeGroupMember(chatId, id, g));
  }
}

async function editGroupName() {
  if (!state.active?.group || !iAmAdmin(state.active.data)) return;
  const chatId = state.active.id;
  const cur = state.active.data?.name || "";
  const v = await askEdit({ title: t("ginfo.editName"), value: cur, max: 40, multiline: false });
  if (!v || v === cur) return;
  runGroup(() => groupBatch(chatId, { kind: "renamed", name: v }, { name: v }));
}

async function editGroupDesc() {
  if (!state.active?.group || !iAmAdmin(state.active.data)) return;
  const chatId = state.active.id;
  const cur = state.active.data?.description || "";
  const v = await askEdit({ title: t("ginfo.editDesc"), value: cur, max: 300, multiline: true });
  if (v === null || v === cur.trim()) return;
  runGroup(() => groupBatch(chatId, { kind: "desc" }, { description: v }));
}

const openInfoFromHeader = () => {
  if (!state.active) return;
  if (state.active.group) openGroupInfo();
  else if (state.active.peer) openUserProfile(state.active.peer);
};

export function initGroupInfo() {
  $("infoClose").onclick = () => { $("infoSheet").hidden = true; };
  $("infoTitleRow").onclick = editGroupName;
  $("infoDesc").onclick = editGroupDesc;
  $("infoAdd").onclick = () => openGroupSheet("add");
  $("infoLeave").onclick = () => leaveGroup();
  $("infoReport").onclick = () => reportGroup();
  $("infoAdminOnly").onclick = () => {
    if (!state.active?.group || !iAmAdmin(state.active.data)) return;
    runGroup(() => updateDoc(doc(db, "chats", state.active.id), { adminOnly: state.active.data?.adminOnly !== true }));
  };
  $("infoAvatar").onclick = () => {
    if (state.active?.group && iAmAdmin(state.active.data)) $("groupPhotoInput").click();
  };
  $("groupPhotoInput").onchange = async e => {
    const file = e.target.files[0];
    e.target.value = "";
    if (!file || !state.active?.group || !iAmAdmin(state.active.data)) return;
    if (!navigator.onLine) {
      toast(t("chat.offlineMedia"));
      return;
    }
    const chatId = state.active.id;
    toast(t("photo.uploading"), true);
    try {
      const res = await upload(await compressImage(file, 640, 0.85));
      await groupBatch(chatId, { kind: "photo" }, { photo: res.secure_url });
      toast(t("photo.changed"));
    } catch (err) {
      toast(err.message || t("group.actionFail"));
    }
  };
  $("peerAv").onclick = openInfoFromHeader;
  document.querySelector("#pane > header .who").onclick = openInfoFromHeader;
}
