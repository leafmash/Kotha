import { doc, getDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { consumePendingChat, setActiveChat, setupNative } from "../../native.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { db } from "../../core/firebase.js";
import { closeDelete } from "../auth/delete-account.js";
import { calls } from "../calls/calls.js";
import { renderList } from "../chat-list/chat-list.js";
import { chatMenu, closeChatMenu } from "../chat/chat-menu.js";
import { closeChat, openChatById } from "../chat/chat-session.js";
import { closeForward } from "../chat/forward.js";
import { closeMenu } from "../chat/message-menu.js";
import { clearReply } from "../chat/reply.js";
import { closeFind } from "../contacts/find-contact.js";
import { closeEditProfile } from "../profile/edit-profile.js";
import { closeProfile } from "../profile/profile.js";
import { closeUserProfile } from "../profile/user-profile.js";
import { closeReport } from "../report/report.js";
import { closeBlocked } from "../settings/blocked-list.js";
import { closeSettings } from "../settings/settings.js";
import { closeChoice, closeConfirm, closeEdit } from "../../ui/dialogs.js";
import { closeMore, moreMenu } from "../../ui/more-menu.js";
import { toast } from "../../ui/toast.js";

const openChatFromNative = id => {
  if (id && !openChatById(id)) state.pendingChat = id;
};

const handleBack = () => {
  if (!$("termsGate").hidden) return true;
  if (!$("deleteBox").hidden) { closeDelete(); return true; }
  if (!$("reportBox").hidden) { closeReport(); return true; }
  if (!$("confirm").hidden) { closeConfirm(false); return true; }
  if (!$("choice").hidden) { closeChoice(null); return true; }
  if (!$("editBox").hidden) { closeEdit(null); return true; }
  if (!chatMenu.hidden) { closeChatMenu(); return true; }
  if (!$("userSheet").hidden) { closeUserProfile(); return true; }
  if (!$("editProfileSheet").hidden) { closeEditProfile(); return true; }
  if (!$("blockedSheet").hidden) { closeBlocked(); return true; }
  if (!$("profileSheet").hidden) { closeProfile(); return true; }
  if (!$("settingsSheet").hidden) { closeSettings(); return true; }
  if (!$("msgMenu").hidden) { closeMenu(); return true; }
  if (!moreMenu.hidden) { closeMore(); return true; }
  if (!$("forwardSheet").hidden) { closeForward(); return true; }
  if (!$("lightbox").hidden) { $("lightbox").hidden = true; return true; }
  if (!$("findSheet").hidden) { closeFind(); return true; }
  if (!$("sheet").hidden) { $("sheet").hidden = true; return true; }
  if (!$("infoSheet").hidden) { $("infoSheet").hidden = true; return true; }
  if (!$("emojiPanel").hidden) { $("emojiPanel").hidden = true; return true; }
  const callState = calls.busy();
  if (callState) {
    toast(callState === "in" ? t("nav.answerOrDecline") : t("nav.callInProgress"));
    return true;
  }
  if (!$("replyBar").hidden) { clearReply(); return true; }
  if (state.active) { closeChat(); renderList(); return true; }
  return false;
};

export function initNativeBridge() {
  setupNative({
    db,
    getDoc,
    doc,
    handleBack,
    openChat: openChatFromNative,
    notify: text => toast(text),
    onResume: () => setActiveChat(state.active?.id || null)
  });
  consumePendingChat().then(openChatFromNative);
}
