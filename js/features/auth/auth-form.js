import { GoogleAuthProvider, createUserWithEmailAndPassword, sendEmailVerification, sendPasswordResetEmail, signInWithCredential, signInWithEmailAndPassword, signInWithPopup, signOut, updateProfile } from "../../core/sdk.js";
import { isNative } from "../../config.js";
import { getLang, t } from "../../i18n.js";
import { nativeGoogleIdToken } from "../../native.js";
import { state } from "../../core/state.js";
import { emailRe } from "../../core/constants.js";
import { $ } from "../../core/dom.js";
import { auth } from "../../core/firebase.js";
import { unregisterPush } from "../notifications/push.js";
import { leavePresence } from "../presence/presence.js";
import { askConfirm } from "../../ui/dialogs.js";

let signup = false;

const authMessage = (err, fallbackKey) => {
  const key = "auth.err." + String(err?.code || "").replace("auth/", "");
  const text = t(key);
  return text === key ? t(fallbackKey) : text;
};

const isCancelled = err => /cancel|popup-closed/i.test((err?.code || "") + " " + (err?.message || ""));

const setInfo = (text, bad) => {
  $("authInfo").textContent = text;
  $("authInfo").classList.toggle("bad", !!bad);
};
export const renderAuthTexts = () => {
  $("forgotLink").hidden = signup;
  $("authBtn").textContent = t(signup ? "auth.signUp" : "auth.signIn");
  $("switchText").textContent = t(signup ? "auth.haveAccount" : "auth.newHere");
  $("switchLink").textContent = t(signup ? "auth.signIn" : "auth.signUp");
  $("langLink").textContent = getLang() === "en" ? "বাংলা" : "English";
};

export async function logout() {
  const ok = await askConfirm({ title: t("signout.title"), text: t("signout.text"), ok: t("signout.ok"), iconName: "logout" });
  if (!ok) return;
  await unregisterPush();
  await leavePresence();
  await signOut(auth);
}

export function initAuthForm() {
  renderAuthTexts();
  $("switchLink").onclick = e => {
    e.preventDefault();
    signup = !signup;
    $("name").hidden = !signup;
    setInfo("");
    renderAuthTexts();
  };
  $("authForm").onsubmit = async e => {
    e.preventDefault();
    $("authErr").textContent = "";
    setInfo("");
    const btn = $("authBtn");
    const label = btn.textContent;
    btn.disabled = true;
    btn.textContent = t("auth.wait");
    const email = $("email").value.trim();
    const password = $("password").value;
    try {
      if (signup) {
        state.pendingName = $("name").value.trim() || email.split("@")[0];
        const cred = await createUserWithEmailAndPassword(auth, email, password);
        await updateProfile(cred.user, { displayName: state.pendingName });
        auth.languageCode = getLang();
        sendEmailVerification(cred.user).catch(() => {});
      } else {
        await signInWithEmailAndPassword(auth, email, password);
      }
    } catch (err) {
      $("authErr").textContent = authMessage(err, "auth.errGeneric");
      btn.disabled = false;
      btn.textContent = label;
    }
  };
  $("forgotLink").onclick = async e => {
    e.preventDefault();
    $("authErr").textContent = "";
    setInfo("");
    const email = $("email").value.trim();
    if (!emailRe.test(email)) {
      setInfo(t("auth.resetNeedEmail"), true);
      $("email").focus();
      return;
    }
    const link = $("forgotLink");
    link.style.pointerEvents = "none";
    auth.languageCode = getLang();
    try {
      await sendPasswordResetEmail(auth, email);
      setInfo(t("auth.resetSent"));
    } catch (err) {
      const key = err?.code === "auth/too-many-requests" ? "auth.resetBusy" : err?.code === "auth/invalid-email" ? "auth.resetNeedEmail" : "auth.resetFail";
      setInfo(t(key), true);
    }
    link.style.pointerEvents = "";
  };
  $("googleBtn").onclick = async () => {
    setInfo("");
    try {
      if (isNative) {
        const idToken = await nativeGoogleIdToken();
        await signInWithCredential(auth, GoogleAuthProvider.credential(idToken));
      } else {
        await signInWithPopup(auth, new GoogleAuthProvider());
      }
    } catch (err) {
      $("authErr").textContent = isCancelled(err) ? "" : authMessage(err, "auth.googleFail");
    }
  };
}
