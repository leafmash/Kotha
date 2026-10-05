import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { upload } from "../../core/upload.js";
import { send } from "./send.js";
import { toast } from "../../ui/toast.js";
import { AUDIO_CONSTRAINTS, createVoiceChain, recorderOptions } from "./voice-processor.js";

const MIN_MS = 700;
const BAR_COUNT = 30;
const MIC_PATH = '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>';
const SEND_PATH = '<path d="M12 19V5M5 12l7-7 7 7"/>';

let recorder;
let chain;
let chunks = [];
let recTick;
let waveTick;
let recStart = 0;
let recCancelled = false;
let history = [];

const setMicIcon = recording => {
  $("micBtn").querySelector("svg").innerHTML = recording ? SEND_PATH : MIC_PATH;
};

const buildBars = () => {
  const wrap = $("recWave");
  wrap.replaceChildren();
  history = new Array(BAR_COUNT).fill(0);
  for (let i = 0; i < BAR_COUNT; i++) wrap.append(document.createElement("b"));
};

const paintBars = () => {
  history.push(chain ? chain.level() : 0);
  history.shift();
  [...$("recWave").children].forEach((bar, i) => {
    bar.style.height = Math.max(12, Math.round(history[i] * 100)) + "%";
  });
};

const stopRecUi = () => {
  clearInterval(recTick);
  clearInterval(waveTick);
  $("micBtn").classList.remove("rec");
  setMicIcon(false);
  document.querySelector("#pane footer").classList.remove("recording");
};

const formatTime = ms => {
  const s = Math.floor(ms / 1000);
  return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
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
    let raw;
    try {
      raw = await navigator.mediaDevices.getUserMedia({ audio: AUDIO_CONSTRAINTS });
    } catch {
      toast(t("chat.micPermission"));
      return;
    }
    const target = state.active;
    chunks = [];
    recCancelled = false;
    chain = createVoiceChain(raw);
    const active = chain;
    recorder = new MediaRecorder(active.stream, recorderOptions());
    recorder.ondataavailable = e => {
      if (e.data.size) chunks.push(e.data);
    };
    recorder.onstop = async () => {
      const duration = Date.now() - recStart;
      const { wave } = active.finish();
      active.close();
      raw.getTracks().forEach(track => track.stop());
      chain = null;
      stopRecUi();
      if (recCancelled || duration < MIN_MS) return;
      const blob = new Blob(chunks, { type: recorder.mimeType });
      if (!navigator.onLine) {
        toast(t("chat.offlineMedia"));
        return;
      }
      toast(t("chat.sendingVoice"), true);
      try {
        const res = await upload(new File([blob], "voice", { type: blob.type }));
        await send({ type: "audio", url: res.secure_url, duration: Math.round(duration / 100) / 10, wave }, target);
        toast("");
      } catch (err) {
        toast(err.message);
      }
    };
    recorder.start(250);
    recStart = Date.now();
    buildBars();
    $("recTime").textContent = "0:00";
    recTick = setInterval(() => {
      $("recTime").textContent = formatTime(Date.now() - recStart);
    }, 250);
    waveTick = setInterval(paintBars, 60);
    $("micBtn").classList.add("rec");
    setMicIcon(true);
    document.querySelector("#pane footer").classList.add("recording");
  };
}
