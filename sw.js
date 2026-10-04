const CACHE = "cova-v50";
const SHELL = [
  "./",
  "index.html",
  "css/style.css",
  "js/app.js",
  "js/call.js",
  "js/i18n.js",
  "js/locales/en.js",
  "js/locales/bn.js",
  "js/push-trigger.js",
  "js/core/constants.js",
  "js/core/dom.js",
  "js/core/firebase.js",
  "js/core/format.js",
  "js/core/icons.js",
  "js/core/image.js",
  "js/core/linkify.js",
  "js/core/message-format.js",
  "js/core/sdk.js",
  "js/core/state.js",
  "js/core/upload.js",
  "js/features/auth/auth-form.js",
  "js/features/auth/delete-account.js",
  "js/features/auth/session.js",
  "js/features/auth/terms.js",
  "js/features/auth/verify-email.js",
  "js/features/block/block.js",
  "js/features/calls/call-history.js",
  "js/features/calls/calls.js",
  "js/features/chat-list/chat-list.js",
  "js/features/chat-list/note-view.js",
  "js/features/chat-list/notes.js",
  "js/features/chat-list/row.js",
  "js/features/chat/attachments.js",
  "js/features/chat/chat-menu.js",
  "js/features/chat/clear-chat.js",
  "js/features/chat/cleared.js",
  "js/features/chat/chat-session.js",
  "js/features/chat/composer.js",
  "js/features/chat/emoji.js",
  "js/features/chat/forward.js",
  "js/features/chat/gestures.js",
  "js/features/chat/header.js",
  "js/features/chat/media-viewer.js",
  "js/features/chat/message-actions.js",
  "js/features/chat/message-list.js",
  "js/features/chat/message-menu.js",
  "js/features/chat/mute.js",
  "js/features/chat/outbox.js",
  "js/features/chat/peer.js",
  "js/features/chat/reactions.js",
  "js/features/chat/read-state.js",
  "js/features/chat/reply.js",
  "js/features/chat/scroll.js",
  "js/features/chat/send.js",
  "js/features/chat/voice-recorder.js",
  "js/features/contacts/find-contact.js",
  "js/features/contacts/user-watch.js",
  "js/features/groups/group-admin.js",
  "js/features/groups/group-create.js",
  "js/features/groups/group-info.js",
  "js/features/groups/group-leave.js",
  "js/features/language/language.js",
  "js/features/native/native-bridge.js",
  "js/features/notifications/notify.js",
  "js/features/notifications/push.js",
  "js/features/notifications/service-worker.js",
  "js/features/presence/presence.js",
  "js/features/profile/avatar.js",
  "js/features/profile/edit-profile.js",
  "js/features/profile/profile.js",
  "js/features/profile/user-profile.js",
  "js/features/report/report.js",
  "js/features/settings/blocked-list.js",
  "js/features/settings/settings.js",
  "js/ui/dialogs.js",
  "js/ui/keyboard.js",
  "js/ui/more-menu.js",
  "js/ui/splash.js",
  "js/ui/theme.js",
  "js/ui/toast.js",
  "icon.svg",
  "icon-192.png"
];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", e => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin) return;
  e.respondWith(
    fetch(e.request)
      .then(res => {
        const copy = res.clone();
        caches.open(CACHE).then(c => c.put(e.request, copy));
        return res;
      })
      .catch(() => caches.match(e.request))
  );
});

self.addEventListener("push", e => {
  let payload = {};
  try {
    payload = e.data.json();
  } catch (err) {
    payload = {};
  }
  const d = payload.data || payload.notification || {};
  e.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then(list => {
      if (list.some(c => c.visibilityState === "visible" && c.focused)) return;
      const isCall = d.type === "call";
      const quiet = !isCall && d.muted === "1";
      const tag = isCall ? "call-" + (d.chatId || "") : d.chatId || "cova";
      return self.registration.showNotification(d.title || "Cova", {
        body: d.body || "",
        icon: "icon-192.png",
        badge: "icon-192.png",
        tag,
        renotify: !quiet,
        silent: quiet,
        requireInteraction: isCall,
        vibrate: isCall ? [400, 200, 400, 200, 400] : undefined,
        data: { chatId: d.chatId || "" }
      }).then(() => quiet ? self.registration.getNotifications({ tag }).then(list => list.forEach(n => n.close())) : null);
    })
  );
});

self.addEventListener("notificationclick", e => {
  e.notification.close();
  const chatId = e.notification.data && e.notification.data.chatId || "";
  e.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then(list => {
      if (list.length) {
        list[0].postMessage({ chatId });
        return list[0].focus();
      }
      return self.clients.openWindow("./index.html" + (chatId ? "?chat=" + encodeURIComponent(chatId) : ""));
    })
  );
});
