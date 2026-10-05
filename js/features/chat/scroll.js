import { state } from "../../core/state.js";
import { PAGE } from "../../core/constants.js";
import { $ } from "../../core/dom.js";
import { listenMessages } from "./chat-session.js";

const FAB_GAP = 12;

function placeFab() {
  const pane = $("pane");
  const box = $("messages");
  if (pane.hidden) return;
  const gap = pane.getBoundingClientRect().bottom - box.getBoundingClientRect().bottom;
  $("fab").style.bottom = Math.max(0, gap) + FAB_GAP + "px";
}

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
  new ResizeObserver(placeFab).observe($("messages"));
  new ResizeObserver(placeFab).observe($("pane"));
  placeFab();
}
