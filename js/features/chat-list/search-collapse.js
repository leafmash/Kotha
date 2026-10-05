import { $ } from "../../core/dom.js";

const FULL_H = 48;
const MINI = 44;
const ICON = 24;
const SHAPE_END = 0.8;

const clamp = (v, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v));
const smooth = v => v * v * (3 - 2 * v);
const lerp = (a, b, k) => a + (b - a) * k;

export function initSearchCollapse() {
  const sidebar = $("sidebar");
  const scroller = $("listScroll");
  const box = $("sbox");
  const input = $("search");
  const icon = box.querySelector(".sico");
  const add = $("addBtn");
  const list = $("list");
  const wide = matchMedia("(min-width:901px) and (pointer:fine)");

  let geo = null;
  let frame = 0;
  let lastTop = 0;

  const measure = () => {
    const sb = sidebar.getBoundingClientRect();
    const sc = scroller.getBoundingClientRect();
    const ad = add.getBoundingClientRect();
    if (!sb.width || !ad.width) return;
    const side = wide.matches ? 14 : 16;
    const fullTop = sc.top - sb.top + 6;
    const miniTop = ad.top - sb.top + (ad.height - MINI) / 2;
    geo = {
      side,
      fullW: sb.width - side * 2,
      fullTop,
      miniLeft: ad.left - sb.left - MINI,
      miniTop,
      range: Math.max(1, fullTop - miniTop)
    };
    list.style.minHeight = Math.max(0, scroller.clientHeight + geo.range - list.offsetTop) + "px";
    apply();
  };

  const apply = () => {
    frame = 0;
    if (!geo) return;
    const st = scroller.scrollTop;
    const p = clamp(st / geo.range);
    const s = smooth(clamp(p / SHAPE_END));
    const top = Math.max(geo.miniTop, geo.fullTop - st);
    const left = lerp(geo.side, geo.miniLeft, s);
    box.style.transform = `translate3d(${left}px,${top}px,0)`;
    box.style.width = lerp(geo.fullW, MINI, s) + "px";
    box.style.height = lerp(FULL_H, MINI, s) + "px";
    icon.style.left = lerp(16, (MINI - ICON) / 2, s) + "px";
    box.style.setProperty("--bgo", clamp(1 - s * 1.6));
    input.style.opacity = clamp(1 - s * 2.4);
    const mini = s > 0.98;
    box.classList.toggle("mini", mini);
    if (mini && st > lastTop && document.activeElement === input) input.blur();
    lastTop = st;
  };

  const schedule = () => {
    if (!frame) frame = requestAnimationFrame(apply);
  };

  scroller.addEventListener("scroll", schedule, { passive: true });
  input.addEventListener("focus", () => {
    if (scroller.scrollTop > 0) scroller.scrollTo({ top: 0, behavior: "smooth" });
  });
  box.addEventListener("click", () => {
    if (box.classList.contains("mini")) input.focus({ preventScroll: true });
    else if (document.activeElement !== input) input.focus();
  });
  window.addEventListener("resize", measure);
  wide.addEventListener("change", measure);

  const observer = new ResizeObserver(measure);
  [sidebar, scroller, $("activeStrip"), $("chips"), $("searchSpacer")].forEach(n => observer.observe(n));
  measure();
}
