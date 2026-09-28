const CACHE = "kotha-v9";
const SHELL = ["./", "index.html", "style.css", "app.js", "icon.svg", "icon-192.png"];

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
      return self.registration.showNotification(d.title || "কথা", {
        body: d.body || "",
        icon: "icon-192.png",
        badge: "icon-192.png",
        tag: d.chatId || "kotha",
        renotify: true,
        data: { chatId: d.chatId || "" }
      });
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
