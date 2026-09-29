const FIREBASE_API_KEY = process.env.FIREBASE_API_KEY || "AIzaSyAFMdkcndeYSl2A4MckeEBRG0YlAFlorzA";
const FALLBACK = [{ urls: "stun:stun.l.google.com:19302" }];

const verifyUser = async idToken => {
  const r = await fetch("https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + FIREBASE_API_KEY, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken })
  });
  if (!r.ok) return false;
  const j = await r.json();
  return Array.isArray(j.users) && j.users.length > 0;
};

module.exports = async (req, res) => {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (req.method === "OPTIONS") {
    res.status(204).end();
    return;
  }
  if (req.method !== "GET" && req.method !== "POST") {
    res.status(405).json({ error: "method" });
    return;
  }
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) {
    res.status(401).json({ error: "auth" });
    return;
  }
  try {
    if (!(await verifyUser(token))) {
      res.status(401).json({ error: "auth" });
      return;
    }
  } catch (err) {
    res.status(502).json({ error: "verify" });
    return;
  }

  const subdomain = process.env.METERED_SUBDOMAIN;
  const apiKey = process.env.METERED_API_KEY;
  if (!subdomain || !apiKey) {
    res.status(200).json({ iceServers: FALLBACK, relay: false });
    return;
  }
  try {
    const r = await fetch("https://" + subdomain + ".metered.live/api/v1/turn/credentials?apiKey=" + encodeURIComponent(apiKey), {
      cache: "no-store"
    });
    if (!r.ok) throw new Error("metered " + r.status);
    const servers = await r.json();
    if (!Array.isArray(servers) || !servers.length) throw new Error("empty");
    res.status(200).json({ iceServers: servers, relay: true });
  } catch (err) {
    res.status(200).json({ iceServers: FALLBACK, relay: false });
  }
};
