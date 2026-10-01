const CACHE = "kotha-v27";
const SHELL = ["./", "index.html", "css/style.css", "js/app.js", "js/call.js", "js/i18n.js", "js/push-trigger.js", "icon.svg", "icon-192.png"];

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
      const tag = isCall ? "call-" + (d.chatId || "") : d.chatId || "kotha";
      return self.registration.showNotification(d.title || "কথা", {
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
