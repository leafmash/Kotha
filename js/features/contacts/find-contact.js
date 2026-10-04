import { doc, getDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { emailRe } from "../../core/constants.js";
import { $, el } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { row } from "../chat-list/row.js";
import { openChat } from "../chat/chat-session.js";
import { watchUser } from "./user-watch.js";

let searchTimer;

export function renderFind() {
  const box = $("findResult");
  box.replaceChildren();
  const term = $("findInput").value.trim().toLowerCase();
  const s = state.searchState;
  if (!term) return;
  if (!emailRe.test(term)) {
    box.append(el("p", "hint", t("find.invalid")));
  } else if (s.term !== term || s.status === "idle" || s.status === "loading") {
    box.append(el("p", "hint", t("find.loading")));
  } else if (s.status === "found") {
    box.append(row(s.user, term, "", 0, () => {
      closeFind();
      openChat(s.user);
    }));
  } else if (s.status === "self") {
    box.append(el("p", "hint", t("find.self")));
  } else if (s.status === "error") {
    box.append(el("p", "hint", t("find.error")));
  } else {
    box.append(el("p", "hint", t("find.none")));
  }
}

export function closeFind() {
  $("findSheet").hidden = true;
  $("findInput").value = "";
  $("findResult").replaceChildren();
  clearTimeout(searchTimer);
  state.searchState = { term: "", status: "idle", user: null };
}

async function runSearch(term) {
  state.searchState = { term, status: "loading", user: null };
  renderFind();
  let next = { term, status: "none", user: null };
  try {
    const hit = await getDoc(doc(db, "emailLookup", term));
    if (state.searchState.term !== term) return;
    if (hit.exists()) {
      if (hit.data().uid === auth.currentUser.uid) {
        next.status = "self";
      } else {
        const p = await getDoc(doc(db, "users", hit.data().uid));
        if (state.searchState.term !== term) return;
        if (p.exists()) {
          next = { term, status: "found", user: p.data() };
          state.users.set(p.id, p.data());
          watchUser(p.id);
        }
      }
    }
  } catch {
    next.status = "error";
  }
  state.searchState = next;
  renderFind();
}

export function initFindContact() {
  $("addBtn").onclick = () => {
    $("findInput").value = "";
    $("findResult").replaceChildren();
    $("findSheet").hidden = false;
    $("findInput").focus();
  };
  $("findClose").onclick = closeFind;
  $("findSheet").onclick = e => { if (e.target === $("findSheet")) closeFind(); };
  $("findInput").oninput = () => {
    clearTimeout(searchTimer);
    const term = $("findInput").value.trim().toLowerCase();
    if (emailRe.test(term)) searchTimer = setTimeout(() => runSearch(term), 300);
    else state.searchState = { term, status: "idle", user: null };
    renderFind();
  };
}
