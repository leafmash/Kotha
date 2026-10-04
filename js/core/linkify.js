import { el } from "./dom.js";

export function linkify(text) {
  const frag = document.createDocumentFragment();
  text.split(/(https?:\/\/[^\s]+)/g).forEach((part, i) => {
    if (i % 2) {
      const a = el("a", "", part);
      a.href = part;
      a.target = "_blank";
      a.rel = "noopener";
      frag.append(a);
    } else {
      frag.append(part);
    }
  });
  return frag;
}
