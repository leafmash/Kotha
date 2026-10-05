import { haptic } from "../native.js";

const EDGE_PX = 26;
const COMMIT_RATIO = 0.35;
const COMMIT_VELOCITY = 0.55;
const MIN_FLING_PX = 36;
const START_SLOP_PX = 10;
const SETTLE_MS = 220;
const EASING = "cubic-bezier(.22,.8,.2,1)";

const clearStyles = node => {
  node.style.transition = "";
  node.style.transform = "";
  node.style.willChange = "";
};

function settle(node, to, done) {
  let finished = false;
  const end = () => {
    if (finished) return;
    finished = true;
    node.removeEventListener("transitionend", onEnd);
    done();
  };
  const onEnd = e => {
    if (e.target === node && e.propertyName === "transform") end();
  };
  node.addEventListener("transitionend", onEnd);
  node.style.transition = "transform " + SETTLE_MS + "ms " + EASING;
  node.style.transform = "translateX(" + to + "px)";
  setTimeout(end, SETTLE_MS + 80);
}

export function initEdgeSwipe(getTarget) {
  let target = null;
  let tracking = false;
  let dragging = false;
  let crossed = false;
  let sx = 0;
  let sy = 0;
  let lastX = 0;
  let lastT = 0;
  let velocity = 0;
  let offset = 0;
  let width = 0;

  const reset = () => {
    tracking = false;
    dragging = false;
    crossed = false;
    target = null;
    offset = 0;
    velocity = 0;
  };

  const commit = t => {
    settle(t.node, width, () => {
      t.node.style.transition = "none";
      t.node.dataset.skipExit = "1";
      t.close();
      t.node.style.transform = "";
      requestAnimationFrame(() => {
        delete t.node.dataset.skipExit;
        t.node.style.transition = "";
        t.node.style.willChange = "";
      });
      haptic("tap");
    });
  };

  const cancel = t => settle(t.node, 0, () => clearStyles(t.node));

  document.addEventListener("touchstart", e => {
    if (tracking || e.touches.length !== 1) return;
    const touch = e.touches[0];
    if (touch.clientX > EDGE_PX) return;
    const found = getTarget();
    if (!found) return;
    target = found;
    tracking = true;
    sx = lastX = touch.clientX;
    sy = touch.clientY;
    lastT = e.timeStamp;
  }, { passive: true });

  document.addEventListener("touchmove", e => {
    if (!tracking) return;
    const touch = e.touches[0];
    const dx = touch.clientX - sx;
    const dy = touch.clientY - sy;
    if (!dragging) {
      if (Math.abs(dy) > START_SLOP_PX && Math.abs(dy) > Math.abs(dx)) {
        reset();
        return;
      }
      if (dx < START_SLOP_PX || dx < Math.abs(dy) * 1.2) return;
      dragging = true;
      width = target.node.offsetWidth || innerWidth;
      target.node.style.transition = "none";
      target.node.style.willChange = "transform";
    }
    if (e.cancelable) e.preventDefault();
    offset = Math.min(width, Math.max(0, dx));
    const dt = e.timeStamp - lastT;
    if (dt > 0) velocity = (touch.clientX - lastX) / dt;
    lastX = touch.clientX;
    lastT = e.timeStamp;
    target.node.style.transform = "translateX(" + offset + "px)";
    const passed = offset / width >= COMMIT_RATIO;
    if (passed !== crossed) {
      crossed = passed;
      haptic("select");
    }
  }, { passive: false });

  const finish = e => {
    if (!tracking) return;
    const t = target;
    const wasDragging = dragging;
    const fling = velocity > COMMIT_VELOCITY && offset > MIN_FLING_PX;
    const pass = offset / (width || 1) >= COMMIT_RATIO;
    const cancelled = e.type === "touchcancel";
    reset();
    if (!wasDragging) return;
    if (!cancelled && (pass || fling)) commit(t);
    else cancel(t);
  };

  document.addEventListener("touchend", finish, { passive: true });
  document.addEventListener("touchcancel", finish, { passive: true });
}
