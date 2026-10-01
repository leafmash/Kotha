const { initializeApp, getApps, cert } = require("firebase-admin/app");

let cachedApp = null;

const getApp = () => {
  if (cachedApp) return cachedApp;
  if (getApps().length) {
    cachedApp = getApps()[0];
    return cachedApp;
  }
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error("config");
  const account = JSON.parse(raw);
  if (account.private_key) account.private_key = account.private_key.replace(/\\n/g, "\n");
  cachedApp = initializeApp({ credential: cert(account) });
  return cachedApp;
};

module.exports = { getApp };
