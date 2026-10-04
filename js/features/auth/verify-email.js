import { doc, getDoc, sendEmailVerification, setDoc } from "../../core/sdk.js";
import { getLang, t } from "../../i18n.js";
import { $ } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { toast } from "../../ui/toast.js";

const isPasswordUser = u => (u?.providerData || []).some(p => p.providerId === "password");

export const registerLookup = async user => {
  const mail = (user.email || "").toLowerCase();
  if (!mail || !user.emailVerified) return;
  const lref = doc(db, "emailLookup", mail);
  const lsnap = await getDoc(lref);
  if (!lsnap.exists() || lsnap.data().uid !== user.uid) await setDoc(lref, { uid: user.uid });
};

export const syncVerifyBar = () => {
  const u = auth.currentUser;
  const show = !!u && isPasswordUser(u) && !u.emailVerified;
  $("verifyBar").hidden = !show;
  if (show) $("verifyText").textContent = t("verify.text", { email: u.email || "" });
};

export async function refreshVerified(announce) {
  const u = auth.currentUser;
  if (!u || u.emailVerified) return;
  try {
    await u.reload();
  } catch (err) {
    if (announce) toast(t("verify.fail"));
    return;
  }
  const fresh = auth.currentUser;
  if (!fresh?.emailVerified) {
    if (announce) toast(t("verify.notYet"));
    return;
  }
  try {
    await fresh.getIdToken(true);
    await registerLookup(fresh);
  } catch (err) {
    toast(t("verify.fail"));
  }
  syncVerifyBar();
  toast(t("verify.verified"));
}

export function initVerifyEmail() {
  $("verifyResend").onclick = async () => {
    const u = auth.currentUser;
    if (!u) return;
    const btn = $("verifyResend");
    btn.disabled = true;
    auth.languageCode = getLang();
    try {
      await sendEmailVerification(u);
      toast(t("verify.sent"));
    } catch (err) {
      toast(t(err?.code === "auth/too-many-requests" ? "verify.busy" : "verify.fail"));
    }
    setTimeout(() => { btn.disabled = false; }, 60000);
  };
  $("verifyCheck").onclick = () => refreshVerified(true);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && !$("verifyBar").hidden) refreshVerified(false);
  });
}
