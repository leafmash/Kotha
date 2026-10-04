import { state } from "../../core/state.js";
import { PAGE } from "../../core/constants.js";
import { $ } from "../../core/dom.js";
import { listenMessages } from "./chat-session.js";

export function initScroll() {
  $("messages").onscroll = () => {
    const box = $("messages");
    $("fab").hidden = box.scrollHeight - box.scrollTop - box.clientHeight < 300;
    if (box.scrollTop < 80 && state.active?.hasMore && !state.active.olderLoad) {
      state.active.olderLoad = true;
      state.active.limit += PAGE;
      listenMessages();
    }
  };
  $("fab").onclick = () => $("messages").scrollTo({ top: $("messages").scrollHeight, behavior: "smooth" });
}
