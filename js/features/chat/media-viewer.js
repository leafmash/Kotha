import { $ } from "../../core/dom.js";

export function initMediaViewer() {
  $("lightbox").onclick = () => { $("lightbox").hidden = true; };
}
