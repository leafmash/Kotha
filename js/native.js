import { isNative, API_BASE } from "./config.js";
import { checkForcedUpdate } from "./app-update.js";
import { t } from "./i18n.js";

const plugins = isNative ? window.Capacitor.Plugins || {} : {};

const CapApp = plugins.App;
const StatusBar = plugins.StatusBar;
const SplashScreen = plugins.SplashScreen;
const Haptics = plugins.Haptics;
const FirebaseMessaging = plugins.FirebaseMessaging;
const FirebaseAuthentication = plugins.FirebaseAuthentication;
const KothaDeepLink = plugins.KothaDeepLink;
const KothaSession = plugins.KothaSession;
const BatteryOptimization = plugins.BatteryOptimization;

const EXIT_WINDOW_MS = 2000;
const SPLASH_LIMIT_MS = 6000;
const UPDATE_WAIT_MS = 1500;
const BATTERY_KEY = "kotha-battery-prompted";

let exitArmed = false;
let exitTimer = null;
let splashHidden = false;
let updateCheck = Promise.resolve(false);
let pushWired = false;

const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

export async function hideNativeSplash() {
  if (!SplashScreen || splashHidden) return;
  splashHidden = true;
  await Promise.race([updateCheck, wait(UPDATE_WAIT_MS)]);
  SplashScreen.hide({ fadeOutDuration: 300 }).catch(() => {});
}

export function applyStatusBar(dark) {
  if (!StatusBar) return;
  const color = getComputedStyle(document.documentElement).getPropertyValue("--bg").trim() || "#0d0f1c";
  StatusBar.setOverlaysWebView({ overlay: false }).catch(() => {});
  StatusBar.setBackgroundColor({ color }).catch(() => {});
  StatusBar.setStyle({ style: dark ? "DARK" : "LIGHT" }).catch(() => {});
}

export function setActiveChat(chatId) {
  if (!KothaDeepLink) return;
  if (chatId) {
    KothaDeepLink.setActiveChat({ chatId }).catch(() => {});
    KothaDeepLink.clearChatNotification({ chatId }).catch(() => {});
  } else {
    KothaDeepLink.clearActiveChat().catch(() => {});
  }
}

export function syncNativeSession(user, config) {
  if (!KothaSession) return;
  if (!user) {
    KothaSession.clearSession().catch(() => {});
    return;
  }
  KothaSession.setSession({
    apiKey: config.apiKey,
    projectId: config.projectId,
    uid: user.uid,
    refreshToken: user.refreshToken,
    apiBase: API_BASE
  }).catch(() => {});
}

export async function nativeGoogleIdToken() {
  const result = await FirebaseAuthentication.signInWithGoogle();
  const idToken = result.credential?.idToken;
  if (!idToken) {
    const err = new Error("no-id-token");
    err.code = "auth/no-google-credential";
    throw err;
  }
  return idToken;
}

export async function registerNativePush({ requestPermission, onToken }) {
  if (!FirebaseMessaging) return null;
  if (!pushWired) {
    pushWired = true;
    FirebaseMessaging.addListener("tokenReceived", event => {
      if (event.token) onToken(event.token);
    });
  }
  let status = await FirebaseMessaging.checkPermissions();
  if (status.receive === "prompt" || status.receive === "prompt-with-rationale") {
    if (!requestPermission) return null;
    status = await FirebaseMessaging.requestPermissions();
  }
  if (status.receive !== "granted") return null;
  const { token } = await FirebaseMessaging.getToken();
  return token || null;
}

export async function initBatteryPrompt() {
  if (!BatteryOptimization || localStorage.getItem(BATTERY_KEY) === "1") return;
  localStorage.setItem(BATTERY_KEY, "1");
  const { ignoring } = await BatteryOptimization.isIgnoringBatteryOptimizations().catch(() => ({ ignoring: true }));
  if (!ignoring) await BatteryOptimization.requestExemption().catch(() => {});
  const { available } = await BatteryOptimization.hasAutostartSettings().catch(() => ({ available: false }));
  if (available) await BatteryOptimization.openAutostartSettings().catch(() => {});
}

export async function consumePendingChat() {
  if (!KothaDeepLink) return null;
  const { chatId } = await KothaDeepLink.getPending().catch(() => ({}));
  return chatId || null;
}

export function setupNative({ db, getDoc, doc, handleBack, openChat, notify, onResume }) {
  if (!isNative) return;

  applyStatusBar(document.documentElement.dataset.theme !== "light");
  setTimeout(hideNativeSplash, SPLASH_LIMIT_MS);
  updateCheck = checkForcedUpdate({ db, getDoc, doc });

  window.addEventListener("kothaNotificationTap", event => {
    if (event.detail) openChat(event.detail);
  });

  if (!CapApp) return;

  CapApp.addListener("backButton", () => {
    if (handleBack()) return;
    if (exitArmed) {
      CapApp.exitApp();
      return;
    }
    exitArmed = true;
    if (Haptics) Haptics.notification({ type: "WARNING" }).catch(() => {});
    notify(t("native.exitAgain"));
    clearTimeout(exitTimer);
    exitTimer = setTimeout(() => {
      exitArmed = false;
    }, EXIT_WINDOW_MS);
  });

  CapApp.addListener("appStateChange", ({ isActive }) => {
    if (isActive) onResume();
  });
}
