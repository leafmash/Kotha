import { $ } from "../core/dom.js";

let toastTimer;
export const toast = (text, sticky) => {
  clearTimeout(toastTimer);
  $("toast").textContent = text;
  $("toast").classList.toggle("show", !!text);
  if (text && !sticky) toastTimer = setTimeout(() => $("toast").classList.remove("show"), 3200);
};
