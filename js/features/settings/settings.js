import { getLang, setLang, t } from "../../i18n.js";
import { $ } from "../../core/dom.js";
import { logout } from "../auth/auth-form.js";
import { openDelete } from "../auth/delete-account.js";
import { setLegalLinks } from "../auth/terms.js";
import { openProfile } from "../profile/profile.js";
import { renderBlockedList } from "./blocked-list.js";
import { askChoice } from "../../ui/dialogs.js";

export function openSettings() {
  renderBlockedList();
  setLegalLinks();
  $("settingsSheet").hidden = false;
  $("settingsSheet").querySelector(".gbody").scrollTop = 0;
}

export function closeSettings() {
  $("settingsSheet").hidden = true;
}

export function initSettings() {
  $("settingsMe").onclick = () => openProfile();
  $("signOutBtn").onclick = () => logout();
  $("langRow").onclick = async () => {
    const cur = getLang();
    const pick = await askChoice({
      title: t("settings.language"),
      text: t("settings.languageText"),
      iconName: "globe",
      options: [
        { label: (cur === "en" ? "✓  " : "") + "English", value: "en", kind: "dnorm" },
        { label: (cur === "bn" ? "✓  " : "") + "বাংলা", value: "bn", kind: "dnorm" },
        { label: t("common.cancel"), value: null, kind: "dcancel" }
      ]
    });
    if (pick && pick !== getLang()) setLang(pick);
  };
  $("settingsClose").onclick = closeSettings;
  $("deleteAccountBtn").onclick = () => openDelete();
}
