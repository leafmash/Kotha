const STORAGE_KEY = "kotha-lang";
const LANGS = ["en", "bn"];

const DICT = {
  en: {
    "auth.tag": "Sign in with your email to start chatting.",
    "auth.name": "Your name",
    "auth.email": "Email",
    "auth.password": "Password",
    "auth.signIn": "Sign in",
    "auth.signUp": "Create account",
    "auth.google": "Continue with Google",
    "auth.newHere": "New here?",
    "auth.haveAccount": "Already have an account?",
    "auth.wait": "Please wait…",
    "auth.googleFail": "Could not sign in with Google",
    "common.chats": "Chats",
    "common.groups": "Groups",
    "common.cancel": "Cancel",
    "common.back": "Back",
    "common.all": "All",
    "common.unread": "Unread",
    "common.you": "You",
    "common.user": "User",
    "common.file": "File",
    "common.photo": "Photo",
    "common.video": "Video",
    "common.voiceMessage": "Voice message",
    "common.today": "Today",
    "common.yesterday": "Yesterday",
    "common.more": "More",
    "common.changePhoto": "Change photo",
    "common.addContact": "Add contact",
    "menu.newGroup": "New group",
    "menu.changeProfilePhoto": "Change profile photo",
    "menu.lightMode": "Light mode",
    "menu.darkMode": "Dark mode",
    "menu.signOut": "Sign out",
    "signout.title": "Sign out?",
    "signout.text": "You will be signed out of this device. You can sign in again to get back to your chats.",
    "signout.ok": "Sign out",
    "list.search": "Search conversations",
    "list.pick": "Pick a chat from the list on the left",
    "list.loadFail": "Could not load. Check your internet connection.",
    "list.newGroupTitle": "Create a new group",
    "list.newGroupSub": "Start a group with friends from your chats",
    "list.youPrefix": "You: ",
    "list.noMatch": "No conversation found with that name.",
    "list.noGroups": "No groups yet.",
    "list.noUnread": "No unread messages.",
    "list.noChats": "No chats yet. Tap the add contact icon above, find someone by email and start talking.",
    "find.title": "Add contact",
    "find.hint": "Enter the full email address of the person you want to add. People cannot be found by name.",
    "find.invalid": "Enter the full email address, like name@example.com",
    "find.loading": "Searching…",
    "find.self": "That is your own email.",
    "find.error": "Something went wrong while searching. Try again.",
    "find.none": "No one was found with this email.",
    "find.add": "Add",
    "find.added": "Added",
    "photo.uploading": "Uploading photo…",
    "photo.changed": "Profile photo updated",
    "chat.online": "Online",
    "chat.offline": "Offline",
    "chat.typing": "typing…",
    "chat.typingMany": "{names} typing…",
    "chat.lastSeen": "Last seen {day}, {time}",
    "chat.members": "{n} members",
    "chat.deleted": "This message was deleted",
    "chat.replyingTo": "Replying to",
    "chat.messagePlaceholder": "Message",
    "chat.emoji": "Emoji",
    "chat.send": "Send",
    "chat.scrollDown": "Scroll to bottom",
    "chat.voiceCall": "Voice call",
    "chat.videoCall": "Video call",
    "chat.sendFail": "Could not send message",
    "chat.uploadFail": "Upload failed. Try again.",
    "chat.uploading": "Uploading…",
    "chat.sendingVoice": "Sending voice message…",
    "chat.micPermission": "Allow microphone access",
    "chat.copied": "Copied",
    "msg.reply": "Reply",
    "msg.copy": "Copy",
    "msg.delete": "Delete",
    "msg.deleteForMe": "Delete for me",
    "msg.deleteForAll": "Delete for everyone",
    "msg.deleteTitle": "Delete message?",
    "msg.deleteTextMine": "Deleting for everyone removes the message from everyone's chat. Deleting for you only lets others still see it.",
    "msg.deleteTextOther": "The message will be removed from your chat only. Others will still see it.",
    "group.new": "New group",
    "group.pickMembers": "Select members",
    "group.selected": "{n} selected",
    "group.create": "Create",
    "group.name": "Group name",
    "group.findContact": "Find a contact by email",
    "group.yourContacts": "Your contacts",
    "group.noContacts": "Add contacts first by finding them by email in the box above.",
    "group.needName": "Enter a group name and pick at least one person",
    "group.created": "Group created",
    "call.call": "Call",
    "call.decline": "Decline",
    "call.answer": "Answer",
    "call.flip": "Flip camera",
    "call.camera": "Camera",
    "call.speaker": "Speaker",
    "call.mic": "Mic",
    "call.end": "End call",
    "call.ended": "Call ended",
    "call.declined": "Call declined",
    "call.missed": "Missed call",
    "call.busy": "The user is busy",
    "call.voice": "Voice call",
    "call.video": "Video call",
    "call.incomingVoice": "Incoming voice call",
    "call.incomingVideo": "Incoming video call",
    "call.calling": "Calling…",
    "call.ringing": "Ringing…",
    "call.connecting": "Connecting…",
    "call.weak": "Weak connection…",
    "call.dropped": "Connection lost",
    "call.connectFail": "Could not connect",
    "call.setupFail": "Could not connect the call",
    "call.startFail": "Could not start the call",
    "call.answerFail": "Could not answer the call",
    "call.noAnswer": "No answer",
    "call.unsupported": "Calls are not supported on this device",
    "call.alreadyEnded": "The call had already ended",
    "call.answeredElsewhere": "Answered on another device",
    "call.logDeclined": "{label} declined",
    "call.logCancelled": "{label} cancelled",
    "call.logMissed": "Missed {label}",
    "call.micCamPermission": "Allow microphone and camera access",
    "call.micCamMissing": "No microphone or camera found",
    "call.micCamBusy": "Microphone or camera is in use by another app",
    "call.route.speaker": "Speaker",
    "call.route.earpiece": "Earpiece",
    "call.route.bluetooth": "Bluetooth",
    "call.route.headphones": "Headphones",
    "call.route.default": "Default",
    "call.audioTitle": "Audio output",
    "call.audioText": "Choose where to hear the call",
    "call.audioUnsupported": "Audio output cannot be changed on this device",
    "call.audioFail": "Could not change audio output",
    "call.cameraFail": "Could not switch camera",
    "nav.answerOrDecline": "Answer or decline the call",
    "nav.callInProgress": "A call is in progress. Tap the red button to end it",
    "native.exitAgain": "Press back again to exit",
    "update.title": "Update required",
    "update.text": "A new version of Kotha is available. Update the app to keep using it.",
    "update.button": "Update now",
    "update.downloading": "Downloading update…",
    "update.opening": "Opening installer…",
    "update.permission": "Allow Kotha to install apps, then tap the button again.",
    "update.failed": "Could not download the update. Check your internet and try again."
  },
  bn: {
    "auth.tag": "আপনার ইমেইল দিয়ে সাইন ইন করে কথা শুরু করুন।",
    "auth.name": "আপনার নাম",
    "auth.email": "ইমেইল",
    "auth.password": "পাসওয়ার্ড",
    "auth.signIn": "সাইন ইন করুন",
    "auth.signUp": "অ্যাকাউন্ট খুলুন",
    "auth.google": "Google দিয়ে চালিয়ে যান",
    "auth.newHere": "নতুন ব্যবহারকারী?",
    "auth.haveAccount": "আগে থেকেই অ্যাকাউন্ট আছে?",
    "auth.wait": "অপেক্ষা করুন…",
    "auth.googleFail": "Google দিয়ে সাইন ইন করা যায়নি",
    "common.chats": "চ্যাট",
    "common.groups": "গ্রুপ",
    "common.cancel": "বাতিল",
    "common.back": "ফিরে যান",
    "common.all": "সব",
    "common.unread": "অপঠিত",
    "common.you": "আপনি",
    "common.user": "ব্যবহারকারী",
    "common.file": "ফাইল",
    "common.photo": "ছবি",
    "common.video": "ভিডিও",
    "common.voiceMessage": "ভয়েস মেসেজ",
    "common.today": "আজ",
    "common.yesterday": "গতকাল",
    "common.more": "আরও",
    "common.changePhoto": "ছবি বদলান",
    "common.addContact": "কন্টাক্ট যোগ করুন",
    "menu.newGroup": "নতুন গ্রুপ",
    "menu.changeProfilePhoto": "প্রোফাইল ছবি বদলান",
    "menu.lightMode": "লাইট মোড",
    "menu.darkMode": "ডার্ক মোড",
    "menu.signOut": "সাইন আউট",
    "signout.title": "সাইন আউট করবেন?",
    "signout.text": "আপনি এই ডিভাইস থেকে সাইন আউট হয়ে যাবেন। আবার সাইন ইন করে চ্যাটে ফিরতে পারবেন।",
    "signout.ok": "সাইন আউট",
    "list.search": "কথোপকথন খুঁজুন",
    "list.pick": "বামের তালিকা থেকে একটি চ্যাট বেছে নিন",
    "list.loadFail": "লোড করা যায়নি, ইন্টারনেট সংযোগ দেখুন",
    "list.newGroupTitle": "নতুন গ্রুপ তৈরি করুন",
    "list.newGroupSub": "চ্যাটে থাকা বন্ধুদের নিয়ে গ্রুপ খুলুন",
    "list.youPrefix": "আপনি: ",
    "list.noMatch": "এই নামে কোনো কথোপকথন পাওয়া যায়নি।",
    "list.noGroups": "এখনও কোনো গ্রুপ নেই।",
    "list.noUnread": "কোনো অপঠিত মেসেজ নেই।",
    "list.noChats": "এখনও কোনো চ্যাট নেই। উপরের কন্টাক্ট যোগ করার আইকনে চেপে ইমেইল দিয়ে কাউকে খুঁজে কথা শুরু করুন।",
    "find.title": "কন্টাক্ট যোগ করুন",
    "find.hint": "যাকে যোগ করতে চান তার পুরো ইমেইল ঠিকানা লিখুন। নাম দিয়ে কাউকে খোঁজা যায় না।",
    "find.invalid": "পুরো ইমেইল ঠিকানা লিখুন, যেমন name@example.com",
    "find.loading": "খোঁজা হচ্ছে…",
    "find.self": "এটি আপনার নিজের ইমেইল।",
    "find.error": "খুঁজতে সমস্যা হয়েছে, আবার চেষ্টা করুন।",
    "find.none": "এই ইমেইলে কাউকে পাওয়া যায়নি।",
    "find.add": "যোগ করুন",
    "find.added": "যোগ করা হয়েছে",
    "photo.uploading": "ছবি আপলোড হচ্ছে…",
    "photo.changed": "প্রোফাইল ছবি বদলানো হয়েছে",
    "chat.online": "অনলাইন",
    "chat.offline": "অফলাইন",
    "chat.typing": "টাইপ করছে…",
    "chat.typingMany": "{names} টাইপ করছে…",
    "chat.lastSeen": "সর্বশেষ দেখা {day}, {time}",
    "chat.members": "{n} জন সদস্য",
    "chat.deleted": "মেসেজ মুছে ফেলা হয়েছে",
    "chat.replyingTo": "উত্তর দিচ্ছেন",
    "chat.messagePlaceholder": "মেসেজ",
    "chat.emoji": "ইমোজি",
    "chat.send": "পাঠান",
    "chat.scrollDown": "নিচে যান",
    "chat.voiceCall": "ভয়েস কল",
    "chat.videoCall": "ভিডিও কল",
    "chat.sendFail": "মেসেজ পাঠানো যায়নি",
    "chat.uploadFail": "আপলোড ব্যর্থ হয়েছে, আবার চেষ্টা করুন",
    "chat.uploading": "আপলোড হচ্ছে…",
    "chat.sendingVoice": "ভয়েস মেসেজ পাঠানো হচ্ছে…",
    "chat.micPermission": "মাইক্রোফোনের অনুমতি দিন",
    "chat.copied": "কপি করা হয়েছে",
    "msg.reply": "উত্তর দিন",
    "msg.copy": "কপি করুন",
    "msg.delete": "মুছুন",
    "msg.deleteForMe": "আমার জন্য মুছুন",
    "msg.deleteForAll": "সবার জন্য মুছুন",
    "msg.deleteTitle": "মেসেজ মুছবেন?",
    "msg.deleteTextMine": "সবার জন্য মুছলে মেসেজটি সবার চ্যাট থেকে সরে যাবে। শুধু আপনার জন্য মুছলে অন্যরা এটি দেখতে পাবে।",
    "msg.deleteTextOther": "মেসেজটি শুধু আপনার চ্যাট থেকে সরে যাবে, অন্যরা এটি দেখতে পাবে।",
    "group.new": "নতুন গ্রুপ",
    "group.pickMembers": "মেম্বার বেছে নিন",
    "group.selected": "{n} জন নির্বাচিত",
    "group.create": "তৈরি করুন",
    "group.name": "গ্রুপের নাম",
    "group.findContact": "ইমেইল দিয়ে কন্টাক্ট খুঁজুন",
    "group.yourContacts": "আপনার কন্টাক্ট",
    "group.noContacts": "উপরের ঘরে ইমেইল লিখে খুঁজে কন্টাক্ট যোগ করুন।",
    "group.needName": "গ্রুপের নাম দিন ও কমপক্ষে একজনকে বেছে নিন",
    "group.created": "গ্রুপ তৈরি হয়েছে",
    "call.call": "কল",
    "call.decline": "প্রত্যাখ্যান",
    "call.answer": "ধরুন",
    "call.flip": "ক্যামেরা ঘোরান",
    "call.camera": "ক্যামেরা",
    "call.speaker": "স্পিকার",
    "call.mic": "মাইক",
    "call.end": "কল শেষ",
    "call.ended": "কল শেষ হয়েছে",
    "call.declined": "কল প্রত্যাখ্যান করা হয়েছে",
    "call.missed": "মিসড কল",
    "call.busy": "ব্যস্ত আছেন",
    "call.voice": "ভয়েস কল",
    "call.video": "ভিডিও কল",
    "call.incomingVoice": "ইনকামিং ভয়েস কল",
    "call.incomingVideo": "ইনকামিং ভিডিও কল",
    "call.calling": "কল হচ্ছে…",
    "call.ringing": "রিং হচ্ছে…",
    "call.connecting": "সংযোগ হচ্ছে…",
    "call.weak": "সংযোগ দুর্বল…",
    "call.dropped": "সংযোগ বিচ্ছিন্ন হয়েছে",
    "call.connectFail": "সংযোগ করা যায়নি",
    "call.setupFail": "কল সংযোগ করা যায়নি",
    "call.startFail": "কল শুরু করা যায়নি",
    "call.answerFail": "কল ধরা যায়নি",
    "call.noAnswer": "কেউ ধরেনি",
    "call.unsupported": "এই ডিভাইসে কল করা যাবে না",
    "call.alreadyEnded": "কলটি আগেই শেষ হয়ে গেছে",
    "call.answeredElsewhere": "অন্য ডিভাইসে ধরা হয়েছে",
    "call.logDeclined": "{label} প্রত্যাখ্যাত",
    "call.logCancelled": "{label} বাতিল",
    "call.logMissed": "মিসড {label}",
    "call.micCamPermission": "মাইক্রোফোন/ক্যামেরার অনুমতি দিন",
    "call.micCamMissing": "মাইক্রোফোন বা ক্যামেরা পাওয়া যায়নি",
    "call.micCamBusy": "মাইক্রোফোন বা ক্যামেরা অন্য অ্যাপ ব্যবহার করছে",
    "call.route.speaker": "স্পিকার",
    "call.route.earpiece": "ইয়ারপিস",
    "call.route.bluetooth": "ব্লুটুথ",
    "call.route.headphones": "হেডফোন",
    "call.route.default": "ডিফল্ট",
    "call.audioTitle": "অডিও আউটপুট",
    "call.audioText": "কল যেখান থেকে শুনবেন সেটা বেছে নিন",
    "call.audioUnsupported": "এই ডিভাইসে অডিও আউটপুট বদলানো যায় না",
    "call.audioFail": "অডিও আউটপুট বদলানো যায়নি",
    "call.cameraFail": "ক্যামেরা বদলানো যায়নি",
    "nav.answerOrDecline": "কলটি ধরুন বা প্রত্যাখ্যান করুন",
    "nav.callInProgress": "কল চলছে। শেষ করতে লাল বাটন চাপুন",
    "native.exitAgain": "বের হতে আবার ব্যাক চাপুন",
    "update.title": "আপডেট প্রয়োজন",
    "update.text": "কথা-র নতুন ভার্সন এসেছে। ব্যবহার চালিয়ে যেতে অ্যাপটি আপডেট করুন।",
    "update.button": "এখনই আপডেট করুন",
    "update.downloading": "আপডেট নামছে…",
    "update.opening": "ইনস্টলার খুলছে…",
    "update.permission": "কথা-কে অ্যাপ ইনস্টলের অনুমতি দিয়ে আবার বাটনটি চাপুন।",
    "update.failed": "আপডেট নামানো যায়নি। ইন্টারনেট দেখে আবার চেষ্টা করুন।"
  }
};

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
  root.querySelectorAll("[data-i18n-title]").forEach(n => { n.title = t(n.dataset.i18nTitle); });
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
