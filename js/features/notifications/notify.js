import { isNative } from "../../config.js";
import { state } from "../../core/state.js";
import { lastText } from "../../core/message-format.js";
import { registerPush } from "./push.js";

export function notify(c, id) {
  if (!("Notification" in window) || Notification.permission !== "granted") return;
  const sender = state.users.get(c.lastFrom)?.name || "";
  const n = new Notification(c.group ? c.name : sender, { body: (c.group ? sender + ": " : "") + lastText(c.lastMessage), icon: "icon.svg", tag: id });
  n.onclick = () => {
    window.focus();
    n.close();
  };
}

export function initNotify() {
  document.addEventListener("click", () => {
    if (!isNative && "Notification" in window && Notification.permission === "default") {
      Notification.requestPermission().then(p => {
        if (p === "granted") registerPush();
      });
    }
  }, { once: true });
}
