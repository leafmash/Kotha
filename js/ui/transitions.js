const SHEETS = ["sheet", "findSheet", "settingsSheet", "infoSheet", "forwardSheet", "profileSheet", "blockedSheet", "editProfileSheet", "userSheet"];
const EXIT_MS = 220;
const GRACE_MS = 80;

const canAnimate = () => document.visibilityState === "visible" && !matchMedia("(prefers-reduced-motion: reduce)").matches;

function watch(node) {
  let leaving = false;
  let timer = 0;
  let known = node.hidden;

  const force = value => {
    node.hidden = value;
    observer.takeRecords();
  };

  const finish = () => {
    clearTimeout(timer);
    leaving = false;
    node.classList.remove("leaving");
    force(true);
    known = true;
  };

  const observer = new MutationObserver(() => {
    const hidden = node.hidden;
    if (leaving) {
      if (hidden) force(false);
      return;
    }
    if (hidden === known) return;
    known = hidden;
    if (!hidden) return;
    if (node.dataset.skipExit) {
      delete node.dataset.skipExit;
      return;
    }
    if (!canAnimate()) return;
    leaving = true;
    force(false);
    node.classList.add("leaving");
    timer = setTimeout(finish, EXIT_MS + GRACE_MS);
  });

  node.addEventListener("animationend", e => {
    if (leaving && e.target === node && e.animationName === "pageOut") finish();
  });

  observer.observe(node, { attributes: true, attributeFilter: ["hidden"] });
}

export function initTransitions() {
  SHEETS.forEach(id => {
    const node = document.getElementById(id);
    if (node) watch(node);
  });
}
