import { t } from "../../i18n.js";
import { state } from "../../core/state.js";

export function peerIdOf(c, uid) {
  return (c.members || []).find(m => m !== uid);
}
export const nameOf = id => state.users.get(id)?.name || t("common.user");
