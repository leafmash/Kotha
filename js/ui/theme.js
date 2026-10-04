import { applyStatusBar } from "../native.js";

let themePoint = null;
let themeBusy = false;

const commitTheme = next => {
  document.documentElement.dataset.theme = next;
  localStorage.setItem("theme", next);
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", next === "light" ? "#ffffff" : "#0d0f1c");
  applyStatusBar(next !== "light");
};

const themeVeil = (x, y, r, color, done) => {
  const veil = document.createElement("div");
  veil.style.cssText = "position:fixed;inset:0;z-index:99999;pointer-events:none;background:" + color;
  document.body.append(veil);
  const start = performance.now();
  const dur = 560;
  const ease = p => p < .5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
  const step = now => {
    const p = Math.min(1, (now - start) / dur);
    const rad = Math.round(r * ease(p));
    const m = "radial-gradient(circle at " + x + "px " + y + "px, transparent " + rad + "px, #000 " + (rad + 1) + "px)";
    veil.style.webkitMaskImage = m;
    veil.style.maskImage = m;
    if (p < 1) requestAnimationFrame(step);
    else {
      veil.remove();
      done();
    }
  };
  requestAnimationFrame(step);
};

export const toggleTheme = () => {
  const root = document.documentElement;
  const next = root.dataset.theme === "dark" ? "light" : "dark";
  const x = Math.round(themePoint?.x ?? innerWidth - 28);
  const y = Math.round(themePoint?.y ?? 28);
  const r = Math.ceil(Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y)));
  if (themeBusy || matchMedia("(prefers-reduced-motion: reduce)").matches) {
    commitTheme(next);
    return;
  }
  themeBusy = true;
  const finish = () => { themeBusy = false; };
  if (typeof document.startViewTransition === "function") {
    root.classList.add("theme-vt");
    const vt = document.startViewTransition(() => commitTheme(next));
    vt.ready.then(() => {
      root.animate(
        { clipPath: ["circle(0px at " + x + "px " + y + "px)", "circle(" + r + "px at " + x + "px " + y + "px)"] },
        { duration: 560, easing: "cubic-bezier(.45,.05,.2,1)", pseudoElement: "::view-transition-new(root)" }
      );
    }).catch(() => {});
    vt.finished.catch(() => {}).finally(() => {
      root.classList.remove("theme-vt");
      finish();
    });
  } else {
    const oldBg = getComputedStyle(document.body).backgroundColor;
    commitTheme(next);
    themeVeil(x, y, r, oldBg, finish);
  }
};

export function initTheme() {
  addEventListener("pointerdown", e => { themePoint = { x: e.clientX, y: e.clientY }; }, true);
  addEventListener("keydown", () => { themePoint = null; }, true);
}
