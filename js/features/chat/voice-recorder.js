import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { upload } from "../../core/upload.js";
import { send } from "./send.js";
import { toast } from "../../ui/toast.js";

let recorder;
let chunks = [];
let recTick;
let recStart = 0;
let recCancelled = false;
const stopRecUi = () => {
  clearInterval(recTick);
  $("micBtn").classList.remove("rec");
  document.querySelector("#pane footer").classList.remove("recording");
};

export function initVoiceRecorder() {
  $("recCancel").onclick = () => {
    recCancelled = true;
    recorder?.stop();
  };
  $("micBtn").onclick = async () => {
    if (recorder?.state === "recording") {
      recorder.stop();
      return;
    }
    if (!state.active) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const target = state.active;
      chunks = [];
      recCancelled = false;
      recorder = new MediaRecorder(stream);
      recorder.ondataavailable = e => chunks.push(e.data);
      recorder.onstop = async () => {
        stream.getTracks().forEach(track => track.stop());
        stopRecUi();
        if (recCancelled) return;
        const blob = new Blob(chunks, { type: recorder.mimeType });
        if (!navigator.onLine) {
          toast(t("chat.offlineMedia"));
          return;
        }
        toast(t("chat.sendingVoice"), true);
        try {
          const res = await upload(new File([blob], "voice", { type: blob.type }));
          await send({ type: "audio", url: res.secure_url }, target);
          toast("");
        } catch (err) {
          toast(err.message);
        }
      };
      recorder.start();
      recStart = Date.now();
      $("recTime").textContent = "0:00";
      recTick = setInterval(() => {
        const s = Math.floor((Date.now() - recStart) / 1000);
        $("recTime").textContent = Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
      }, 500);
      $("micBtn").classList.add("rec");
      document.querySelector("#pane footer").classList.add("recording");
    } catch (err) {
      toast(t("chat.micPermission"));
    }
  };
}
