import { t } from "../i18n.js";
import { $, el } from "../core/dom.js";
import { icon } from "../core/icons.js";
import { openGroupSheet } from "../features/groups/group-create.js";
import { openProfile } from "../features/profile/profile.js";
import { openSettings } from "../features/settings/settings.js";
import { toggleTheme } from "./theme.js";

export const moreMenu = $("moreMenu");
export function closeMore() {
  moreMenu.hidden = true;
  $("menuBtn").setAttribute("aria-expanded", "false");
}
export function openMore() {
  const dark = document.documentElement.dataset.theme === "dark";
  const items = [
    { icon: "users", label: t("menu.newGroup"), fn: () => openGroupSheet() },
    { icon: "user", label: t("menu.profile"), fn: () => openProfile() },
    { icon: dark ? "sun" : "moon", label: dark ? t("menu.lightMode") : t("menu.darkMode"), fn: toggleTheme },
    { icon: "sliders", label: t("menu.settings"), fn: () => openSettings() }
  ];
  moreMenu.replaceChildren(...items.map(it => {
    const b = el("button", "mi" + (it.danger ? " danger" : ""));
    b.setAttribute("role", "menuitem");
    b.append(icon(it.icon), el("span", "", it.label));
    b.onclick = () => {
      closeMore();
      it.fn();
    };
    return b;
  }));
  moreMenu.hidden = false;
  $("menuBtn").setAttribute("aria-expanded", "true");
}

export function initMoreMenu() {
  $("menuBtn").onclick = () => moreMenu.hidden ? openMore() : closeMore();
  document.addEventListener("click", e => {
    if (!moreMenu.hidden && !e.target.closest("#moreMenu, #menuBtn")) closeMore();
  });
}
