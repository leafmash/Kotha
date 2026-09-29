import { API_BASE } from "./config.js";

const RETRY_DELAYS = [0, 1500, 5000];
const TIMEOUT_MS = 8000;

const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

export function createPushTrigger(auth) {
  const attempt = async payload => {
    const user = auth.currentUser;
    if (!user) return true;
    const token = await user.getIdToken();
    const res = await fetch(API_BASE + "/api/notify", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + token },
      body: JSON.stringify(payload),
      keepalive: true,
      cache: "no-store",
      signal: AbortSignal.timeout ? AbortSignal.timeout(TIMEOUT_MS) : undefined
    });
    if (res.ok) return true;
    return res.status >= 400 && res.status < 500 && res.status !== 408 && res.status !== 429;
  };

  const run = async payload => {
    for (const delay of RETRY_DELAYS) {
      if (delay) await wait(delay);
      try {
        if (await attempt(payload)) return;
      } catch {
        continue;
      }
    }
  };

  return payload => {
    run(payload).catch(() => {});
  };
}
