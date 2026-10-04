import { isNative } from "../../config.js";
import { state } from "../../core/state.js";
import { openChatById } from "../chat/chat-session.js";

export function initServiceWorker() {
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.addEventListener("message", e => {
      const id = e.data?.chatId;
      if (id && !openChatById(id)) state.pendingChat = id;
    });
  }
  if (!isNative && "serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
}
