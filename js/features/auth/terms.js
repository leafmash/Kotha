import { doc, serverTimestamp, setDoc, signOut } from "../../core/sdk.js";
import { API_BASE, TERMS_VERSION, isNative } from "../../config.js";
import { getLang } from "../../i18n.js";
import { $ } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { icon } from "../../core/icons.js";
import { unregisterPush } from "../notifications/push.js";
import { leavePresence } from "../presence/presence.js";

const legalHref = page => (isNative ? API_BASE + "/" : "") + page + ".html?lang=" + getLang();

export function setLegalLinks() {
  document.querySelectorAll("[data-legal]").forEach(a => { a.href = legalHref(a.dataset.legal); });
}

export function showTermsGate() {
  setLegalLinks();
  $("termsGate").hidden = false;
  $("termsAgree").focus();
}

export function initTerms() {
  $("termsAgree").onclick = () => {
    if (!auth.currentUser) return;
    $("termsGate").hidden = true;
    setDoc(doc(db, "users", auth.currentUser.uid), { termsVersion: TERMS_VERSION, termsAt: serverTimestamp() }, { merge: true }).catch(() => showTermsGate());
  };
  $("termsDecline").onclick = async () => {
    $("termsGate").hidden = true;
    await unregisterPush();
    await leavePresence();
    await signOut(auth);
  };
  $("termsIcon").replaceChildren(icon("shield"));
  setLegalLinks();
}
