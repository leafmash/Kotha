import { EmailAuthProvider, GoogleAuthProvider, clearIndexedDbPersistence, reauthenticateWithCredential, reauthenticateWithPopup, signOut, terminate } from "../../core/sdk.js";
import { API_BASE, isNative } from "../../config.js";
import { fmtNumber, t } from "../../i18n.js";
import { nativeGoogleIdToken } from "../../native.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { icon } from "../../core/icons.js";
import { calls } from "../calls/calls.js";
import { closeChat } from "../chat/chat-session.js";
import { registerPush, unregisterPush } from "../notifications/push.js";
import { armRtPresence, dropRtPresence, setPresence } from "../presence/presence.js";
import { toast } from "../../ui/toast.js";

const providerIds = () => (auth.currentUser?.providerData || []).map(p => p.providerId);
let deleteBusy = false;
let deleteMethod = "password";

let deleteLeft = 0;
let deleteTimer;

function renderDeleteOk() {
  const locked = deleteLeft > 0;
  $("deleteOk").disabled = deleteBusy || locked;
  $("deleteGoogle").disabled = deleteBusy || locked;
  const base = t(deleteMethod === "password" ? "delete.confirm" : "delete.confirmGoogle");
  $("deleteOk").textContent = deleteBusy ? t("delete.working") : base + (locked ? " (" + fmtNumber(deleteLeft) + ")" : "");
}

function setDeleteBusy(on) {
  deleteBusy = on;
  ["deleteCancel", "deletePassword"].forEach(id => { $(id).disabled = on; });
  renderDeleteOk();
}

function startDeleteLock() {
  clearInterval(deleteTimer);
  deleteLeft = 3;
  renderDeleteOk();
  deleteTimer = setInterval(() => {
    deleteLeft -= 1;
    if (deleteLeft <= 0) {
      deleteLeft = 0;
      clearInterval(deleteTimer);
    }
    renderDeleteOk();
  }, 1000);
}

export function openDelete() {
  const ids = providerIds();
  const usesPassword = ids.includes("password");
  const usesGoogle = ids.includes("google.com");
  deleteMethod = usesPassword || !usesGoogle ? "password" : "google";
  const showPassword = deleteMethod === "password";
  $("deleteText").textContent = t("delete.text") + " " + t(showPassword ? "delete.textPassword" : "delete.textGoogle");
  $("deletePassword").hidden = !showPassword;
  $("deletePassword").value = "";
  $("deleteGoogle").hidden = !(showPassword && usesGoogle);
  $("deleteErr").textContent = "";
  setDeleteBusy(false);
  startDeleteLock();
  $("deleteBox").hidden = false;
  (showPassword ? $("deletePassword") : $("deleteCancel")).focus();
}

export function closeDelete() {
  if (deleteBusy) return;
  clearInterval(deleteTimer);
  deleteLeft = 0;
  $("deleteBox").hidden = true;
  $("deletePassword").value = "";
}

async function reauthenticate(method, password) {
  const user = auth.currentUser;
  if (method === "password") {
    await reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, password));
  } else if (isNative) {
    const idToken = await nativeGoogleIdToken();
    await reauthenticateWithCredential(user, GoogleAuthProvider.credential(idToken));
  } else {
    await reauthenticateWithPopup(user, new GoogleAuthProvider());
  }
}

async function runDelete(method) {
  if (deleteBusy || deleteLeft > 0 || !auth.currentUser) return;
  const password = $("deletePassword").value;
  if (method === "password" && !password) {
    $("deleteErr").textContent = t("delete.needPassword");
    return;
  }
  $("deleteErr").textContent = "";
  setDeleteBusy(true);
  try {
    await reauthenticate(method, password);
  } catch (err) {
    setDeleteBusy(false);
    const code = err?.code || "";
    if (/wrong-password|invalid-credential|invalid-login/.test(code)) $("deleteErr").textContent = t("delete.wrongPassword");
    else if (!/cancel|closed|popup/i.test(code + " " + (err?.message || ""))) $("deleteErr").textContent = t("delete.failed");
    return;
  }
  state.deleting = true;
  const uid = auth.currentUser.uid;
  dropRtPresence(uid);
  closeChat();
  calls.stop();
  try {
    await unregisterPush();
    const token = await auth.currentUser.getIdToken(true);
    const res = await fetch(API_BASE + "/api/delete-account", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + token },
      body: "{}",
      cache: "no-store"
    });
    if (!res.ok) {
      const j = await res.json().catch(() => ({}));
      throw new Error(j.error || "server");
    }
  } catch (err) {
    state.deleting = false;
    setDeleteBusy(false);
    if (auth.currentUser) {
      calls.start(uid);
      registerPush();
      armRtPresence();
      setPresence(true);
    }
    $("deleteErr").textContent = t(err.message === "reauth" ? "delete.reauth" : "delete.failed");
    return;
  }
  try {
    sessionStorage.setItem("kotha-deleted", "1");
  } catch {
    localStorage.removeItem("kotha-deleted");
  }
  localStorage.removeItem("kotha-session");
  localStorage.removeItem("kotha-push");
  await signOut(auth).catch(() => {});
  try {
    await terminate(db);
    await clearIndexedDbPersistence(db);
  } catch {
    localStorage.removeItem("kotha-deleted");
  }
  location.reload();
}

export function initDeleteAccount() {
  $("deleteOk").onclick = () => runDelete(deleteMethod);
  $("deleteGoogle").onclick = () => runDelete("google");
  $("deleteCancel").onclick = closeDelete;
  $("deleteBox").onclick = e => { if (e.target === $("deleteBox")) closeDelete(); };
  $("deletePassword").onkeydown = e => {
    if (e.key === "Enter") {
      e.preventDefault();
      runDelete("password");
    }
  };
  $("deleteIcon").replaceChildren(icon("trash"));
  try {
    if (sessionStorage.getItem("kotha-deleted") === "1") {
      sessionStorage.removeItem("kotha-deleted");
      toast(t("delete.done"));
    }
  } catch {
    localStorage.removeItem("kotha-deleted");
  }
}
