import { el } from "../../core/dom.js";

const BARS = 38;
const SPEEDS = [1, 1.5, 2];
const PLAY = '<path d="M8 5.2v13.6a.6.6 0 0 0 .9.5l10.8-6.8a.6.6 0 0 0 0-1L8.9 4.7a.6.6 0 0 0-.9.5z"/>';
const PAUSE = '<rect x="6.5" y="5" width="3.8" height="14" rx="1.2"/><rect x="13.7" y="5" width="3.8" height="14" rx="1.2"/>';

const views = new Map();
let audio = null;
let currentId = null;
let speedIndex = 0;
let frame = 0;

const formatTime = seconds => {
  const s = Math.max(0, Math.floor(Number.isFinite(seconds) ? seconds : 0));
  return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
};

const seededWave = seed => {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) h = Math.imul(h ^ seed.charCodeAt(i), 16777619) >>> 0;
  let prev = 50;
  return Array.from({ length: BARS }, (_, i) => {
    h = Math.imul(h ^ (i + 1), 16777619) >>> 0;
    prev = prev * 0.45 + (22 + (h % 72)) * 0.55;
    return prev;
  });
};

const fitWave = raw => {
  const clean = raw.map(v => Math.min(100, Math.max(0, Number(v) || 0)));
  return Array.from({ length: BARS }, (_, i) => {
    const from = Math.floor((i * clean.length) / BARS);
    const to = Math.max(from + 1, Math.floor(((i + 1) * clean.length) / BARS));
    let peak = 0;
    for (let j = from; j < to && j < clean.length; j++) peak = Math.max(peak, clean[j]);
    return peak;
  });
};

const resolveWave = m => {
  if (Array.isArray(m.wave) && m.wave.length >= 4 && m.wave.length <= 256) return fitWave(m.wave);
  return seededWave(m.url || "voice");
};

const getAudio = () => {
  if (audio) return audio;
  audio = new Audio();
  audio.preload = "auto";
  const refresh = () => views.get(currentId)?.sync();
  const loop = () => {
    refresh();
    frame = audio && !audio.paused ? requestAnimationFrame(loop) : 0;
  };
  audio.addEventListener("play", () => {
    cancelAnimationFrame(frame);
    loop();
  });
  audio.addEventListener("pause", refresh);
  audio.addEventListener("loadedmetadata", refresh);
  audio.addEventListener("ended", () => {
    audio.currentTime = 0;
    refresh();
  });
  return audio;
};

export function stopVoice() {
  if (!audio) return;
  audio.pause();
  audio.currentTime = 0;
  const prev = views.get(currentId);
  currentId = null;
  prev?.sync();
}

export function createVoicePlayer(id, m) {
  const wave = resolveWave(m);
  const root = el("div", "vp");
  const btn = el("button", "vp-btn");
  btn.type = "button";
  btn.setAttribute("aria-label", "Play");
  const glyph = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  glyph.setAttribute("viewBox", "0 0 24 24");
  glyph.innerHTML = PLAY;
  btn.append(glyph);

  const main = el("div", "vp-main");
  const track = el("div", "vp-wave");
  const bars = wave.map(v => {
    const bar = document.createElement("i");
    bar.style.height = Math.max(14, v) + "%";
    track.append(bar);
    return bar;
  });
  const foot = el("div", "vp-foot");
  const time = el("span", "vp-time", formatTime(m.duration));
  const speed = el("button", "vp-speed");
  speed.type = "button";
  speed.hidden = true;
  foot.append(time, speed);
  main.append(track, foot);
  root.append(btn, main);

  let shownPlaying = null;

  const totalSeconds = () => {
    const live = currentId === id && audio && Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : 0;
    return live || Number(m.duration) || 0;
  };

  const view = {
    sync() {
      const isCurrent = currentId === id && audio;
      const playing = !!isCurrent && !audio.paused;
      const total = totalSeconds();
      const at = isCurrent ? audio.currentTime : 0;
      const progress = total > 0 ? Math.min(1, at / total) : 0;
      if (shownPlaying !== playing) {
        glyph.innerHTML = playing ? PAUSE : PLAY;
        btn.setAttribute("aria-label", playing ? "Pause" : "Play");
        root.classList.toggle("playing", playing);
        shownPlaying = playing;
      }
      const filled = progress * BARS;
      bars.forEach((bar, i) => bar.classList.toggle("on", i < filled));
      time.textContent = formatTime(isCurrent && at > 0 ? at : total);
      speed.hidden = !(isCurrent && (playing || at > 0));
      speed.textContent = SPEEDS[speedIndex] + "x";
    }
  };

  const start = ratio => {
    const player = getAudio();
    if (currentId !== id) {
      const prev = views.get(currentId);
      currentId = id;
      player.src = m.url;
      player.playbackRate = SPEEDS[speedIndex];
      prev?.sync();
      if (ratio !== undefined) {
        const seek = () => {
          const total = Number.isFinite(player.duration) && player.duration > 0 ? player.duration : Number(m.duration) || 0;
          if (total > 0) player.currentTime = total * ratio;
        };
        if (player.readyState >= 1) seek();
        else player.addEventListener("loadedmetadata", seek, { once: true });
      }
      player.play().catch(() => view.sync());
      return;
    }
    if (ratio !== undefined) {
      const total = totalSeconds();
      if (total > 0) player.currentTime = total * ratio;
    }
    if (player.paused) player.play().catch(() => view.sync());
    view.sync();
  };

  btn.onclick = e => {
    e.stopPropagation();
    if (currentId === id && audio && !audio.paused) audio.pause();
    else start();
  };

  track.onclick = e => {
    e.stopPropagation();
    const box = track.getBoundingClientRect();
    const ratio = Math.min(1, Math.max(0, (e.clientX - box.left) / box.width));
    start(ratio);
  };

  speed.onclick = e => {
    e.stopPropagation();
    speedIndex = (speedIndex + 1) % SPEEDS.length;
    if (audio) audio.playbackRate = SPEEDS[speedIndex];
    view.sync();
  };

  views.set(id, view);
  view.sync();
  return root;
}
