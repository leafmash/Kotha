import { en } from "./locales/en.js";
import { bn } from "./locales/bn.js";

const STORAGE_KEY = "kotha-lang";
const LANGS = ["en", "bn"];

const DICT = { en, bn };

const LEGACY = {
  "মেসেজ মুছে ফেলা হয়েছে": "deleted",
  "গ্রুপ তৈরি হয়েছে": "groupCreated",
  "ছবি": "image",
  "ভিডিও": "video",
  "ভয়েস মেসেজ": "audio",
  "ফাইল": "file"
};

const TOKENS = {
  deleted: "chat.deleted",
  groupCreated: "group.created",
  membersAdded: "group.membersAdded",
  image: "common.photo",
  video: "common.video",
  audio: "common.voiceMessage",
  file: "common.file"
};

const listeners = new Set();

const readStored = () => {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return LANGS.includes(v) ? v : "en";
  } catch {
    return "en";
  }
};

let lang = readStored();

export const getLang = () => lang;

export const locale = () => (lang === "bn" ? "bn-BD" : "en-US");

export const t = (key, params) => {
  let s = DICT[lang][key] ?? DICT.en[key] ?? key;
  if (params) for (const k of Object.keys(params)) s = s.split("{" + k + "}").join(params[k]);
  return s;
};

export const fmtNumber = n => Number(n).toLocaleString(locale());

export const stored = token => "[[" + token + "]]";

export const displayStored = value => {
  if (typeof value !== "string") return "";
  const m = /^\[\[(\w+)\]\]$/.exec(value);
  const token = m ? m[1] : LEGACY[value];
  return token && TOKENS[token] ? t(TOKENS[token]) : value;
};

export const applyStatic = (root = document) => {
  root.querySelectorAll("[data-i18n]").forEach(n => { n.textContent = t(n.dataset.i18n); });
  root.querySelectorAll("[data-i18n-title]").forEach(n => {
    n.title = t(n.dataset.i18nTitle);
    if (!n.textContent.trim() && !n.dataset.i18nAria) n.setAttribute("aria-label", n.title);
  });
  root.querySelectorAll("[data-i18n-placeholder]").forEach(n => { n.placeholder = t(n.dataset.i18nPlaceholder); });
  root.querySelectorAll("[data-i18n-aria]").forEach(n => { n.setAttribute("aria-label", t(n.dataset.i18nAria)); });
  document.documentElement.lang = lang;
};

export const onLangChange = fn => {
  listeners.add(fn);
  return () => listeners.delete(fn);
};

export const setLang = next => {
  if (!LANGS.includes(next) || next === lang) return;
  lang = next;
  try {
    localStorage.setItem(STORAGE_KEY, next);
  } catch {
    lang = next;
  }
  applyStatic();
  listeners.forEach(fn => fn(next));
};

export const toggleLang = () => setLang(lang === "en" ? "bn" : "en");

applyStatic();
