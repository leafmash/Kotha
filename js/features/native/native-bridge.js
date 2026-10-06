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
import { exitMessageSelection, isSelectingMessages } from "../chat/message-selection.js";
import { clearReply } from "../chat/reply.js";
import { closeFind } from "../contacts/find-contact.js";
import { closeEditProfile } from "../profile/edit-profile.js";
import { closeProfile } from "../profile/profile.js";
import { closeUserProfile } from "../profile/user-profile.js";
import { closeReport } from "../report/report.js";
import { closeBlocked } from "../settings/blocked-list.js";
import { closeSettings } from "../settings/settings.js";
import { closeChoice, closeConfirm, closeEdit } from "../../ui/dialogs.js";
import { initEdgeSwipe } from "../../ui/edge-swipe.js";
import { closeMore, moreMenu } from "../../ui/more-menu.js";
import { toast } from "../../ui/toast.js";
import { closeActionSheet } from "../../ui/action-sheet.js";

const openChatFromNative = id => {
  if (id && !openChatById(id)) state.pendingChat = id;
};

const mobileLayout = () => !matchMedia("(min-width:901px) and (pointer:fine)").matches;

const byId = id => () => $(id);

const hideNode = id => () => { $(id).hidden = true; };

const layers = [
  { node: byId("termsGate") },
  { node: byId("deleteBox"), close: closeDelete },
  { node: byId("reportBox"), close: closeReport },
  { node: byId("confirm"), close: () => closeConfirm(false) },
  { node: byId("actionSheet"), close: () => closeActionSheet(null) },
  { node: byId("choice"), close: () => closeChoice(null) },
  { node: byId("editBox"), close: () => closeEdit(null) },
  { node: () => chatMenu, close: closeChatMenu },
  { node: byId("userSheet"), close: closeUserProfile, drag: true },
  { node: byId("editProfileSheet"), close: closeEditProfile, drag: true },
  { node: byId("blockedSheet"), close: closeBlocked, drag: true },
  { node: byId("profileSheet"), close: closeProfile, drag: true },
  { node: byId("settingsSheet"), close: closeSettings, drag: true },
  { node: () => moreMenu, close: closeMore },
  { node: byId("forwardSheet"), close: closeForward, drag: true },
  { node: byId("lightbox"), close: hideNode("lightbox") },
  { node: byId("findSheet"), close: closeFind, drag: true },
  { node: byId("sheet"), close: hideNode("sheet"), drag: true },
  { node: byId("infoSheet"), close: hideNode("infoSheet"), drag: true },
  { node: byId("emojiPanel"), close: hideNode("emojiPanel") }
];

const topLayer = () => layers.find(layer => !layer.node().hidden);

const handleBack = () => {
  const layer = topLayer();
  if (layer) {
    layer.close?.();
    return true;
  }
  const callState = calls.busy();
  if (callState) {
    toast(callState === "in" ? t("nav.answerOrDecline") : t("nav.callInProgress"));
    return true;
  }
  if (!$("replyBar").hidden) { clearReply(); return true; }
  if (isSelectingMessages()) { exitMessageSelection(); return true; }
  if (state.active) { closeChat(); renderList(); return true; }
  return false;
};

const swipeTarget = () => {
  const layer = topLayer();
  if (layer) return layer.drag ? { node: layer.node(), close: layer.close } : null;
  if (!state.active || !mobileLayout() || calls.busy() || !$("replyBar").hidden || isSelectingMessages()) return null;
  return { node: $("chat"), close: () => { closeChat(true); renderList(); } };
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
  initEdgeSwipe(swipeTarget);
}
