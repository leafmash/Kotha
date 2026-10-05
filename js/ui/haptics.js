import { haptic } from "../native.js";

const TAP = "#tabs button, .mreacts button, .mitems .tile, .dbtns button, #micBtn, #voiceCallBtn, #videoCallBtn, #callMute, #callCam, #callFlip, #callSpeaker, #authBtn, #googleBtn";
const PRESS = "#callAccept, #callDecline, #callEnd";

export function initHaptics() {
  document.addEventListener("pointerdown", e => {
    if (e.pointerType === "mouse") return;
    const hit = e.target.closest?.(TAP + ", " + PRESS);
    if (!hit || hit.disabled) return;
    haptic(hit.matches(PRESS) ? "press" : "tap");
  }, true);
}
