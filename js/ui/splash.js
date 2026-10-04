import { hideNativeSplash } from "../native.js";
import { $ } from "../core/dom.js";

let splashTimer;

const hadSession = localStorage.getItem("kotha-session") === "1";
export const hideSplash = () => {
  clearTimeout(splashTimer);
  $("splash").hidden = true;
  hideNativeSplash();
};
export const showSplash = () => {
  $("splash").hidden = false;
  clearTimeout(splashTimer);
  splashTimer = setTimeout(hideSplash, 8000);
};

export function initSplash() {
  if (hadSession) showSplash();
}
