import { isNative } from "./config.js";

const CapApp = isNative ? window.Capacitor.Plugins?.App : null;
const AppUpdater = isNative ? window.Capacitor.Plugins?.AppUpdater : null;

const STYLE = `
.update-overlay{position:fixed;inset:0;z-index:100000;display:flex;align-items:center;justify-content:center;padding:24px;background:var(--bg,#0d0f1c)}
.update-card{width:100%;max-width:360px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:14px;color:var(--text,#eef0fb)}
.update-icon{width:72px;height:72px;border-radius:50%;display:grid;place-items:center;background:var(--accent,#8b7dff);color:#fff}
.update-icon svg{width:34px;height:34px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}
.update-card h2{font-size:22px;font-weight:700}
.update-card p{color:var(--muted,#8d93b5);font-size:15px;line-height:1.5}
.update-notes{background:var(--fill,#181b2e);border-radius:14px;padding:12px 14px;width:100%;white-space:pre-wrap;text-align:left}
.update-track{width:100%;height:6px;border-radius:3px;background:var(--fill,#181b2e);overflow:hidden;display:none}
.update-track.show{display:block}
.update-bar{display:block;height:100%;width:0;background:var(--accent,#8b7dff);transition:width .2s}
.update-btn{width:100%;border:0;border-radius:14px;padding:14px;font:inherit;font-weight:600;color:#fff;background:var(--accent,#8b7dff)}
.update-btn:disabled{opacity:.6}
`;

const ICON = '<svg viewBox="0 0 24 24"><path d="M12 4v11M7 11l5 5 5-5M5 20h14"/></svg>';

function buildOverlay(config) {
  const style = document.createElement("style");
  style.textContent = STYLE;
  document.head.append(style);

  const overlay = document.createElement("div");
  overlay.className = "update-overlay";

  const card = document.createElement("div");
  card.className = "update-card";

  const icon = document.createElement("div");
  icon.className = "update-icon";
  icon.innerHTML = ICON;

  const title = document.createElement("h2");
  title.textContent = "আপডেট প্রয়োজন";

  const text = document.createElement("p");
  text.textContent = "কথা-র নতুন ভার্সন এসেছে। ব্যবহার চালিয়ে যেতে অ্যাপটি আপডেট করুন।";

  card.append(icon, title, text);

  if (config.changelog) {
    const notes = document.createElement("p");
    notes.className = "update-notes";
    notes.textContent = config.changelog;
    card.append(notes);
  }

  const track = document.createElement("div");
  track.className = "update-track";
  const bar = document.createElement("span");
  bar.className = "update-bar";
  track.append(bar);

  const status = document.createElement("p");

  const button = document.createElement("button");
  button.type = "button";
  button.className = "update-btn";
  button.textContent = "এখনই আপডেট করুন";

  card.append(track, status, button);
  overlay.append(card);
  document.body.append(overlay);
  return { button, track, bar, status };
}

async function startDownload(apkUrl, els) {
  els.button.disabled = true;
  if (!AppUpdater) {
    window.open(apkUrl, "_system");
    els.button.disabled = false;
    return;
  }
  els.track.classList.add("show");
  els.status.textContent = "আপডেট নামছে…";
  const listener = await AppUpdater.addListener("downloadProgress", ({ percent }) => {
    els.bar.style.width = percent + "%";
    els.status.textContent = "আপডেট নামছে… " + percent + "%";
  });
  try {
    await AppUpdater.downloadAndInstall({ url: apkUrl });
    els.status.textContent = "ইনস্টলার খুলছে…";
  } catch (err) {
    els.status.textContent = err?.message === "install-permission-required"
      ? "কথা-কে অ্যাপ ইনস্টলের অনুমতি দিয়ে আবার বাটনটি চাপুন।"
      : "আপডেট নামানো যায়নি। ইন্টারনেট দেখে আবার চেষ্টা করুন।";
    els.button.disabled = false;
  } finally {
    listener.remove();
  }
}

export async function checkForcedUpdate({ db, getDoc, doc }) {
  if (!CapApp) return false;
  try {
    const info = await CapApp.getInfo();
    const build = parseInt(info.build, 10);
    if (!build) return false;
    const snap = await getDoc(doc(db, "config", "appVersion"));
    if (!snap.exists()) return false;
    const config = snap.data();
    const minimum = Number(config.minVersionCode) || 0;
    if (build >= minimum || !config.apkUrl) return false;
    const els = buildOverlay(config);
    els.button.addEventListener("click", () => startDownload(config.apkUrl, els));
    return true;
  } catch {
    return false;
  }
}
