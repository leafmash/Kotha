import { t } from "../../i18n.js";
import { pic } from "../../core/format.js";
import { openActionSheet } from "../../ui/action-sheet.js";
import { deleteConversations } from "../chat/clear-chat.js";
import { muteMany, isMuted } from "../chat/mute.js";
import { bulkArchive, bulkPin, isArchived, isPinned } from "../chat/pin-archive.js";

const HANDLERS = { pin: bulkPin, mute: muteMany, archive: bulkArchive, delete: deleteConversations };

export async function openChatActions(id, who) {
  const pinned = isPinned(id);
  const archived = isArchived(id);
  const muted = isMuted(id);
  const choice = await openActionSheet({
    title: who?.name || t("common.chats"),
    avatar: who ? pic(who) : undefined,
    actions: [
      { icon: "pin", label: t(pinned ? "pin.undo" : "pin.action"), value: "pin" },
      { icon: muted ? "bell" : "bellOff", label: t(muted ? "mute.unmute" : "mute.action"), value: "mute" },
      { icon: archived ? "unarchive" : "archive", label: t(archived ? "archive.undo" : "archive.action"), value: "archive" },
      { icon: "trash", label: t("chat.delete"), value: "delete", danger: true, separator: true }
    ]
  });
  if (choice) await HANDLERS[choice]([id]);
}
