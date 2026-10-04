import { arrayRemove } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { auth } from "../../core/firebase.js";
import { renderList } from "../chat-list/chat-list.js";
import { closeChat } from "../chat/chat-session.js";
import { groupAdminIds, groupBatch } from "./group-admin.js";
import { askConfirm } from "../../ui/dialogs.js";
import { toast } from "../../ui/toast.js";

export async function leaveGroup() {
  if (!state.active?.group) return;
  const ok = await askConfirm({ title: t("group.leaveTitle"), text: t("group.leaveText"), ok: t("group.leave"), iconName: "logout" });
  if (!ok || !state.active?.group) return;
  const uid = auth.currentUser.uid;
  const id = state.active.id;
  const g = state.active.data || {};
  const rest = (g.members || []).filter(m => m !== uid);
  const admins = groupAdminIds(g);
  const patch = { members: arrayRemove(uid) };
  if (admins.includes(uid) && rest.length) {
    const others = admins.filter(m => m !== uid);
    const successor = others[0] || rest[0];
    if (g.admin === uid) patch.admin = successor;
    if (!others.length) patch.admins = [successor];
    else if ((g.admins || []).includes(uid)) patch.admins = (g.admins || []).filter(m => m !== uid);
  }
  closeChat();
  renderList();
  groupBatch(id, { kind: "left" }, patch).catch(() => toast(t("group.leaveFail")));
  toast(t("group.left"));
}
