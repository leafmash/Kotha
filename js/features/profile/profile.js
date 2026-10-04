import { getLang, locale, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $, el } from "../../core/dom.js";
import { auth } from "../../core/firebase.js";
import { pic } from "../../core/format.js";

export function renderMe() {
  const me = state.users.get(auth.currentUser.uid);
  $("railImg").src = pic(me);
  renderProfile();
}

function infoRow(value, label) {
  const r = el("div", "irow");
  r.append(el("b", "", value), el("small", "", label));
  return r;
}

export function renderProfile() {
  const user = auth.currentUser;
  if (!user) return;
  const me = state.users.get(user.uid);
  const name = me?.name || user.displayName || t("common.user");
  const photo = pic(me || { name });
  $("profileImg").src = photo;
  $("profileName").textContent = name;
  $("settingsImg").src = photo;
  $("settingsName").textContent = name;
  $("settingsEmail").textContent = user.email || "";
  $("langValue").textContent = getLang() === "en" ? "English" : "বাংলা";
  const bio = (me?.bio || "").trim();
  const rows = [infoRow(name, t("profile.name"))];
  if (bio) rows.push(infoRow(bio, t("profile.about")));
  if (user.email) {
    rows.push(infoRow(user.email, t("profile.email")));
    rows.push(infoRow(t(user.emailVerified ? "profile.verified" : "profile.notVerified"), t("profile.emailStatus")));
  }
  const methods = (user.providerData || []).map(p => p.providerId === "password" ? t("profile.methodPassword") : p.providerId === "google.com" ? "Google" : p.providerId);
  if (methods.length) rows.push(infoRow(methods.join(" · "), t("profile.method")));
  const created = user.metadata?.creationTime ? new Date(user.metadata.creationTime) : null;
  if (created && !isNaN(created)) rows.push(infoRow(created.toLocaleDateString(locale(), { year: "numeric", month: "long", day: "numeric" }), t("profile.joined")));
  $("profileInfo").replaceChildren(...rows);
}

export function openProfile() {
  renderProfile();
  $("profileSheet").hidden = false;
  $("profileSheet").querySelector(".gbody").scrollTop = 0;
}

export function closeProfile() {
  $("profileSheet").hidden = true;
}

export function initProfile() {
  $("profileClose").onclick = closeProfile;
}
