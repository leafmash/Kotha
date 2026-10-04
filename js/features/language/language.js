import { doc, updateDoc } from "../../core/sdk.js";
import { getLang, onLangChange, toggleLang } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { renderAuthTexts } from "../auth/auth-form.js";
import { setLegalLinks } from "../auth/terms.js";
import { syncVerifyBar } from "../auth/verify-email.js";
import { renderList } from "../chat-list/chat-list.js";
import { chatMenu, openChatMenu } from "../chat/chat-menu.js";
import { renderForward } from "../chat/forward.js";
import { renderPeer } from "../chat/header.js";
import { renderMessages } from "../chat/message-list.js";
import { renderFind } from "../contacts/find-contact.js";
import { syncGroupUi } from "../groups/group-create.js";
import { renderGroupInfo } from "../groups/group-info.js";
import { renderProfile } from "../profile/profile.js";
import { renderUserProfile } from "../profile/user-profile.js";
import { renderReasons } from "../report/report.js";
import { renderBlockedList } from "../settings/blocked-list.js";
import { moreMenu, openMore } from "../../ui/more-menu.js";

export function initLanguage() {
  $("langLink").onclick = e => {
    e.preventDefault();
    toggleLang();
  };
  onLangChange(() => {
    renderAuthTexts();
    if (auth.currentUser) {
      updateDoc(doc(db, "users", auth.currentUser.uid), { lang: getLang() }).catch(() => {});
      renderList();
      if (state.active) {
        renderPeer();
        if (state.lastMessageDocs) renderMessages(state.lastMessageDocs);
      }
      if (!$("sheet").hidden) syncGroupUi();
      if (!$("infoSheet").hidden) renderGroupInfo();
      if (!$("findSheet").hidden) renderFind();
      if (!moreMenu.hidden) openMore();
      if (!chatMenu.hidden) openChatMenu();
      renderBlockedList();
      renderProfile();
      renderUserProfile();
      if (!$("reportBox").hidden) renderReasons();
      if (!$("forwardSheet").hidden) renderForward();
      syncVerifyBar();
    }
    setLegalLinks();
  });
}
