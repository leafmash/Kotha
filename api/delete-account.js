const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getApp } = require("./_firebase");
const { eraseUser } = require("./_erase");

const FRESH_LOGIN_SECONDS = 600;

module.exports = async (req, res) => {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (req.method === "OPTIONS") {
    res.status(204).end();
    return;
  }
  if (req.method !== "POST") {
    res.status(405).json({ error: "method" });
    return;
  }
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) {
    res.status(401).json({ error: "auth" });
    return;
  }
  let app;
  try {
    app = getApp();
  } catch {
    res.status(500).json({ error: "config" });
    return;
  }
  const auth = getAuth(app);
  let decoded;
  try {
    decoded = await auth.verifyIdToken(token, true);
  } catch {
    res.status(401).json({ error: "auth" });
    return;
  }
  const signedInAt = Number(decoded.auth_time) || 0;
  if (Date.now() / 1000 - signedInAt > FRESH_LOGIN_SECONDS) {
    res.status(401).json({ error: "reauth" });
    return;
  }
  try {
    const record = await auth.getUser(decoded.uid);
    await eraseUser({
      db: getFirestore(app),
      auth,
      FieldValue,
      uid: decoded.uid,
      email: record.email || decoded.email || "",
      cloudinary: {
        cloud: process.env.CLOUDINARY_CLOUD_NAME,
        key: process.env.CLOUDINARY_API_KEY,
        secret: process.env.CLOUDINARY_API_SECRET
      },
      fetchImpl: fetch
    });
    res.status(200).json({ ok: true });
  } catch {
    res.status(500).json({ error: "server" });
  }
};
