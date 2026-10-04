import { arrayRemove, arrayUnion, doc, getMessaging, getToken, isSupported, setDoc } from "../../core/sdk.js";
import { isNative } from "../../config.js";
import { registerNativePush } from "../../native.js";
import { app, auth, db } from "../../core/firebase.js";

const VAPID_KEY = "YOUR_WEB_PUSH_VAPID_KEY";

async function savePushToken(token) {
  if (!token || !auth.currentUser) return;
  localStorage.setItem("kotha-push", token);
  await setDoc(doc(db, "pushTokens", auth.currentUser.uid), { tokens: arrayUnion(token) }, { merge: true }).catch(() => {});
}

export async function registerPush(requestPermission = false) {
  if (isNative) {
    const token = await registerNativePush({ requestPermission, onToken: savePushToken }).catch(() => null);
    await savePushToken(token);
    return;
  }
  if (VAPID_KEY.startsWith("YOUR_") || !("Notification" in window) || !("serviceWorker" in navigator)) return;
  if (Notification.permission !== "granted" || !auth.currentUser) return;
  try {
    if (!(await isSupported())) return;
    const reg = await navigator.serviceWorker.ready;
    const token = await getToken(getMessaging(app), { vapidKey: VAPID_KEY, serviceWorkerRegistration: reg });
    await savePushToken(token);
  } catch (err) {
    return;
  }
}

export async function unregisterPush() {
  const token = localStorage.getItem("kotha-push");
  if (!token || !auth.currentUser) return;
  localStorage.removeItem("kotha-push");
  await setDoc(doc(db, "pushTokens", auth.currentUser.uid), { tokens: arrayRemove(token) }, { merge: true }).catch(() => {});
}
