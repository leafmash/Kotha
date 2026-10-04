import { state } from "../core/state.js";
import { $ } from "../core/dom.js";
import { closeDelete } from "../features/auth/delete-account.js";
import { closeChatMenu } from "../features/chat/chat-menu.js";
import { closeChat } from "../features/chat/chat-session.js";
import { closeForward } from "../features/chat/forward.js";
import { closeMenu } from "../features/chat/message-menu.js";
import { closeFind } from "../features/contacts/find-contact.js";
import { closeEditProfile } from "../features/profile/edit-profile.js";
import { closeProfile } from "../features/profile/profile.js";
import { closeUserProfile } from "../features/profile/user-profile.js";
import { closeReport } from "../features/report/report.js";
import { closeBlocked } from "../features/settings/blocked-list.js";
import { closeSettings } from "../features/settings/settings.js";
import { closeChoice, closeConfirm, closeEdit } from "./dialogs.js";
import { closeMore } from "./more-menu.js";

function closeSearchFocus() {
  $("search").focus();
  $("search").select();
}

export function initKeyboard() {
  addEventListener("keydown", e => {
    if (e.key !== "Escape") return;
    closeConfirm(false);
    closeChoice(null);
    closeEdit(null);
    closeMenu();
    closeMore();
    closeChatMenu();
    closeForward();
  });
  document.addEventListener("keydown", e => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
      e.preventDefault();
      closeSearchFocus();
      return;
    }
    if (e.key !== "Escape") return;
    if (!$("lightbox").hidden) $("lightbox").hidden = true;
    else if (!$("deleteBox").hidden) closeDelete();
    else if (!$("reportBox").hidden) closeReport();
    else if (!$("userSheet").hidden) closeUserProfile();
    else if (!$("editProfileSheet").hidden) closeEditProfile();
    else if (!$("blockedSheet").hidden) closeBlocked();
    else if (!$("profileSheet").hidden) closeProfile();
    else if (!$("settingsSheet").hidden) closeSettings();
    else if (!$("findSheet").hidden) closeFind();
    else if (!$("sheet").hidden) $("sheet").hidden = true;
    else if (!$("infoSheet").hidden) $("infoSheet").hidden = true;
    else if (!$("emojiPanel").hidden) $("emojiPanel").hidden = true;
    else if (state.active) closeChat();
  });
}
