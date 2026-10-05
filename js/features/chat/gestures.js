import { el } from "../../core/dom.js";
import { icon } from "../../core/icons.js";
import { preview } from "../../core/message-format.js";
import { haptic } from "../../native.js";
import { menuBox, openMenu } from "./message-menu.js";
import { setReply } from "./reply.js";

export function attachGestures(b, d, m, mine) {
  const ico = el("div", "swipe-ico");
  ico.append(icon("reply"));
  b.append(ico);
  let sx = 0, sy = 0, dx = 0, timer = null, swiping = false, tracking = false, fired = false, armed = false;
  const cancelPress = () => { clearTimeout(timer); timer = null; };
  const reset = () => {
    b.style.transition = "transform .2s";
    b.style.transform = "";
    ico.style.opacity = 0;
    setTimeout(() => { b.style.transition = ""; }, 220);
  };
  const show = () => {
    cancelPress();
    fired = true;
    haptic("press");
    openMenu(d, m, mine);
  };
  b.addEventListener("pointerdown", e => {
    if (e.pointerType === "mouse") return;
    tracking = true;
    swiping = false;
    fired = false;
    armed = false;
    sx = e.clientX;
    sy = e.clientY;
    dx = 0;
    cancelPress();
    timer = setTimeout(show, 450);
  });
  b.addEventListener("pointermove", e => {
    if (!tracking || fired) return;
    const mx = e.clientX - sx;
    const my = e.clientY - sy;
    if (!swiping && (Math.abs(mx) > 10 || Math.abs(my) > 10)) cancelPress();
    const dir = mine ? -1 : 1;
    if (!swiping && mx * dir > 12 && Math.abs(mx) > Math.abs(my) * 1.4 && !m.deleted) swiping = true;
    if (!swiping) return;
    dx = Math.max(0, Math.min(mx * dir, 90));
    b.style.transform = `translateX(${dx * dir}px)`;
    ico.style.opacity = Math.min(1, dx / 60);
    ico.classList.toggle("ready", dx >= 60);
    if ((dx >= 60) !== armed) {
      armed = dx >= 60;
      haptic("select");
    }
  });
  const end = () => {
    if (!tracking) return;
    tracking = false;
    cancelPress();
    if (swiping) {
      if (dx >= 60) {
        haptic("tap");
        setReply(preview(m), d.id);
      }
      swiping = false;
      ico.classList.remove("ready");
      reset();
    }
  };
  b.addEventListener("pointerup", end);
  b.addEventListener("pointercancel", end);
  b.addEventListener("contextmenu", e => {
    e.preventDefault();
    if (menuBox.hidden) show();
  });
  b.addEventListener("click", e => {
    if (fired || dx >= 12) {
      e.preventDefault();
      e.stopPropagation();
      fired = false;
      dx = 0;
    }
  }, true);
}
