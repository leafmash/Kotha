const WAVE_POINTS = 44;
const TICK_MS = 25;

export const AUDIO_CONSTRAINTS = {
  channelCount: 1,
  sampleRate: 48000,
  echoCancellation: true,
  noiseSuppression: true,
  autoGainControl: true,
  voiceIsolation: true
};

const pickMime = () => {
  if (typeof MediaRecorder === "undefined") return "";
  return ["audio/webm;codecs=opus", "audio/ogg;codecs=opus", "audio/mp4"].find(m => MediaRecorder.isTypeSupported(m)) || "";
};

export const recorderOptions = () => {
  const mimeType = pickMime();
  return mimeType ? { mimeType, audioBitsPerSecond: 96000 } : { audioBitsPerSecond: 96000 };
};

const buildContext = () => {
  const Ctx = window.AudioContext || window.webkitAudioContext;
  if (!Ctx) return null;
  try {
    return new Ctx({ sampleRate: 48000, latencyHint: "interactive" });
  } catch {
    return new Ctx();
  }
};

export function createVoiceChain(stream) {
  const ctx = buildContext();
  if (!ctx) return { stream, level: () => 0, finish: () => ({ wave: [] }), close() {} };

  const source = ctx.createMediaStreamSource(stream);

  const highpass = ctx.createBiquadFilter();
  highpass.type = "highpass";
  highpass.frequency.value = 90;
  highpass.Q.value = 0.707;

  const hum = ctx.createBiquadFilter();
  hum.type = "notch";
  hum.frequency.value = 50;
  hum.Q.value = 12;

  const hum2 = ctx.createBiquadFilter();
  hum2.type = "notch";
  hum2.frequency.value = 60;
  hum2.Q.value = 12;

  const lowpass = ctx.createBiquadFilter();
  lowpass.type = "lowpass";
  lowpass.frequency.value = 10000;
  lowpass.Q.value = 0.707;

  const presence = ctx.createBiquadFilter();
  presence.type = "peaking";
  presence.frequency.value = 3200;
  presence.Q.value = 0.9;
  presence.gain.value = 2.5;

  const compressor = ctx.createDynamicsCompressor();
  compressor.threshold.value = -32;
  compressor.knee.value = 24;
  compressor.ratio.value = 3.5;
  compressor.attack.value = 0.004;
  compressor.release.value = 0.22;

  const gate = ctx.createGain();
  const makeup = ctx.createGain();
  makeup.gain.value = 1.35;

  const limiter = ctx.createDynamicsCompressor();
  limiter.threshold.value = -3;
  limiter.knee.value = 0;
  limiter.ratio.value = 20;
  limiter.attack.value = 0.001;
  limiter.release.value = 0.08;

  const destination = ctx.createMediaStreamDestination();
  const analyser = ctx.createAnalyser();
  analyser.fftSize = 1024;

  source.connect(highpass);
  highpass.connect(hum);
  hum.connect(hum2);
  hum2.connect(lowpass);
  lowpass.connect(presence);
  presence.connect(analyser);
  presence.connect(compressor);
  compressor.connect(gate);
  gate.connect(makeup);
  makeup.connect(limiter);
  limiter.connect(destination);

  const buffer = new Float32Array(analyser.fftSize);
  const samples = [];
  let floor = 0.012;
  let open = true;
  let holdUntil = performance.now() + 450;
  let current = 0;

  const tick = () => {
    analyser.getFloatTimeDomainData(buffer);
    let sum = 0;
    for (let i = 0; i < buffer.length; i++) sum += buffer[i] * buffer[i];
    const rms = Math.sqrt(sum / buffer.length);
    const now = performance.now();
    if (rms < floor) floor = rms * 0.6 + floor * 0.4;
    else floor = Math.min(0.03, floor + (rms - floor) * 0.0004);
    const openAt = Math.max(0.011, floor * 3.2);
    const closeAt = Math.max(0.007, floor * 2.2);
    if (rms > openAt) {
      open = true;
      holdUntil = now + 260;
    } else if (open && rms < closeAt && now > holdUntil) {
      open = false;
    }
    const now2 = ctx.currentTime;
    gate.gain.cancelScheduledValues(now2);
    gate.gain.setTargetAtTime(open ? 1 : 0.1, now2, open ? 0.012 : 0.1);
    current = open ? rms : rms * 0.15;
    samples.push(current);
  };

  const timer = setInterval(tick, TICK_MS);
  if (ctx.state === "suspended") ctx.resume().catch(() => {});

  const finish = () => {
    clearInterval(timer);
    if (!samples.length) return { wave: [] };
    const size = samples.length / WAVE_POINTS;
    const bars = [];
    for (let i = 0; i < WAVE_POINTS; i++) {
      const from = Math.floor(i * size);
      const to = Math.max(from + 1, Math.floor((i + 1) * size));
      let peak = 0;
      for (let j = from; j < to && j < samples.length; j++) peak = Math.max(peak, samples[j]);
      bars.push(peak);
    }
    const top = Math.max(...bars, 0.001);
    return { wave: bars.map(v => Math.round(Math.pow(v / top, 0.7) * 100)) };
  };

  return {
    stream: destination.stream,
    level: () => Math.min(1, Math.pow(current * 6, 0.65)),
    finish,
    close() {
      clearInterval(timer);
      source.disconnect();
      ctx.close().catch(() => {});
    }
  };
}
