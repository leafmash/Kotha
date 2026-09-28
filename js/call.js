const RING_MS = 45000;
const STALE_MS = 90000;
const DROP_MS = 12000;
const CAND_DELAY = 250;
const FALLBACK_ICE = [{ urls: "stun:stun.l.google.com:19302" }];
const END_TEXT = { ended: "কল শেষ হয়েছে", declined: "কল প্রত্যাখ্যান করা হয়েছে", missed: "মিসড কল" };

const fmt = s => Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");

const mediaError = err => {
  const n = err && err.name;
  if (n === "NotAllowedError" || n === "SecurityError") return "মাইক্রোফোন/ক্যামেরার অনুমতি দিন";
  if (n === "NotFoundError" || n === "OverconstrainedError") return "মাইক্রোফোন বা ক্যামেরা পাওয়া যায়নি";
  if (n === "NotReadableError") return "মাইক্রোফোন বা ক্যামেরা অন্য অ্যাপ ব্যবহার করছে";
  return "কল শুরু করা যায়নি";
};

export function createCalls({ auth, db, fs, $, toast, pic, getActive, getUsers, send }) {
  const { collection, doc, getDoc, setDoc, updateDoc, addDoc, onSnapshot, query, where, serverTimestamp } = fs;

  let call = null;
  let watchUnsub = null;
  let iceCache = null;
  let audioCtx = null;
  let toneTimer = null;

  const myUid = () => auth.currentUser?.uid || "";

  const newCall = base => ({
    ...base,
    id: "",
    ref: null,
    pc: null,
    local: null,
    remote: null,
    data: null,
    created: false,
    accepted: false,
    connected: false,
    weak: false,
    ended: false,
    canSend: false,
    peerCamOff: false,
    facing: "user",
    out: [],
    pending: [],
    unsubs: [],
    startedAt: 0,
    tick: null,
    ringTimer: null,
    dropTimer: null,
    outTimer: null,
    wake: null,
    notif: null
  });

  const setStatus = text => {
    $("callStatus").textContent = text;
  };

  const stopTone = () => {
    if (toneTimer) clearInterval(toneTimer);
    toneTimer = null;
    if (navigator.vibrate) navigator.vibrate(0);
  };

  const beep = (freq, at, dur, vol) => {
    const o = audioCtx.createOscillator();
    const g = audioCtx.createGain();
    o.type = "sine";
    o.frequency.value = freq;
    o.connect(g);
    g.connect(audioCtx.destination);
    g.gain.setValueAtTime(0, at);
    g.gain.linearRampToValueAtTime(vol, at + 0.03);
    g.gain.linearRampToValueAtTime(0, at + dur);
    o.start(at);
    o.stop(at + dur + 0.05);
  };

  const startTone = kind => {
    stopTone();
    try {
      audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      if (audioCtx.resume) audioCtx.resume();
    } catch {
      return;
    }
    const play = () => {
      try {
        const t = audioCtx.currentTime + 0.05;
        if (kind === "in") {
          beep(880, t, 0.35, 0.2);
          beep(660, t + 0.4, 0.35, 0.2);
          beep(880, t + 1.0, 0.35, 0.2);
          beep(660, t + 1.4, 0.35, 0.2);
          if (navigator.vibrate) navigator.vibrate([400, 200, 400]);
        } else {
          beep(440, t, 1.0, 0.12);
        }
      } catch {
        return;
      }
    };
    play();
    toneTimer = setInterval(play, 3000);
  };

  async function getIce() {
    if (iceCache && iceCache.exp > Date.now()) return iceCache.servers;
    try {
      const token = await auth.currentUser.getIdToken();
      const r = await fetch("/api/turn", {
        headers: { Authorization: "Bearer " + token },
        cache: "no-store",
        signal: AbortSignal.timeout ? AbortSignal.timeout(6000) : undefined
      });
      if (r.ok) {
        const j = await r.json();
        if (Array.isArray(j.iceServers) && j.iceServers.length) {
          if (j.relay) iceCache = { servers: j.iceServers, exp: Date.now() + 30 * 60000 };
          return j.iceServers;
        }
      }
    } catch {
      return FALLBACK_ICE;
    }
    return FALLBACK_ICE;
  }

  const getMedia = video => navigator.mediaDevices.getUserMedia({
    audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    video: video ? { facingMode: "user", width: { ideal: 640 }, height: { ideal: 480 } } : false
  });

  const paintPeer = u => {
    $("callImg").src = pic(u || { name: "?" });
    $("callName").textContent = (u && u.name) || "";
  };

  function layout(c) {
    const rv = !!c.remote && c.remote.getVideoTracks().length > 0 && !c.peerCamOff;
    $("call").classList.toggle("vid", rv && c.connected);
    $("localVideo").hidden = !(c.video && c.local && c.local.getVideoTracks().length);
  }

  function showCall(c, text) {
    const u = getUsers().get(c.peerUid);
    paintPeer(u);
    if (!u) {
      getDoc(doc(db, "users", c.peerUid)).then(s => {
        if (s.exists() && call === c) paintPeer(s.data());
      }).catch(() => {});
    }
    const box = $("call");
    box.className = "ring";
    setStatus(text);
    $("callIn").hidden = c.dir !== "in";
    $("callBar").hidden = c.dir === "in";
    $("callCam").hidden = !c.video;
    $("callFlip").hidden = true;
    $("callMute").classList.remove("off");
    $("callCam").classList.remove("off");
    $("localVideo").classList.remove("off", "back");
    $("localVideo").srcObject = null;
    $("remoteVideo").srcObject = null;
    $("localVideo").hidden = true;
    box.hidden = false;
  }

  const hideCall = () => {
    $("call").hidden = true;
    $("localVideo").srcObject = null;
    $("remoteVideo").srcObject = null;
  };

  async function refreshFlip(c) {
    if (!c.video || !navigator.mediaDevices.enumerateDevices) return;
    try {
      const list = await navigator.mediaDevices.enumerateDevices();
      if (call === c && list.filter(d => d.kind === "videoinput").length > 1) $("callFlip").hidden = false;
    } catch {
      return;
    }
  }

  function queueCand(c, cand) {
    c.out.push(cand);
    if (!c.canSend) return;
    clearTimeout(c.outTimer);
    c.outTimer = setTimeout(() => flushOut(c), CAND_DELAY);
  }

  function flushOut(c) {
    if (!c.canSend || c.ended || !c.out.length) return;
    const cs = c.out;
    c.out = [];
    addDoc(collection(db, "calls", c.id, "candidates"), { from: myUid(), cs }).catch(() => {});
  }

  async function applyCand(c, cand) {
    if (c.ended || !c.pc) return;
    if (!c.pc.remoteDescription) {
      c.pending.push(cand);
      return;
    }
    try {
      await c.pc.addIceCandidate(cand);
    } catch {
      return;
    }
  }

  async function flushPending(c) {
    const list = c.pending;
    c.pending = [];
    for (const x of list) {
      try {
        await c.pc.addIceCandidate(x);
      } catch {
        continue;
      }
    }
  }

  function watchCandidates(c) {
    const uid = myUid();
    c.unsubs.push(onSnapshot(collection(db, "calls", c.id, "candidates"), s => {
      s.docChanges().forEach(ch => {
        if (ch.type !== "added") return;
        const d = ch.doc.data();
        if (d.from === uid) return;
        (d.cs || []).forEach(x => applyCand(c, x));
      });
    }));
  }

  function onConnected(c) {
    if (c.ended) return;
    c.weak = false;
    clearTimeout(c.dropTimer);
    if (c.connected) return;
    c.connected = true;
    c.startedAt = Date.now();
    clearTimeout(c.ringTimer);
    stopTone();
    $("call").classList.remove("ring");
    setStatus("0:00");
    c.tick = setInterval(() => {
      if (!c.weak) setStatus(fmt(Math.floor((Date.now() - c.startedAt) / 1000)));
    }, 1000);
    layout(c);
  }

  function makePeer(c, stream, ice) {
    const pc = new RTCPeerConnection({ iceServers: ice });
    c.pc = pc;
    c.local = stream;
    c.remote = null;
    stream.getTracks().forEach(t => pc.addTrack(t, stream));
    $("localVideo").srcObject = c.video ? stream : null;
    pc.ontrack = e => {
      c.remote = e.streams[0] || c.remote || new MediaStream([e.track]);
      const rv = $("remoteVideo");
      if (rv.srcObject !== c.remote) rv.srcObject = c.remote;
      const p = rv.play();
      if (p && p.catch) p.catch(() => {});
      layout(c);
    };
    pc.onicecandidate = e => {
      if (e.candidate) {
        queueCand(c, e.candidate.toJSON());
        return;
      }
      clearTimeout(c.outTimer);
      flushOut(c);
    };
    pc.onconnectionstatechange = () => {
      if (c.ended) return;
      const st = pc.connectionState;
      if (st === "connected") {
        onConnected(c);
      } else if (st === "disconnected") {
        c.weak = true;
        setStatus("সংযোগ দুর্বল…");
        clearTimeout(c.dropTimer);
        c.dropTimer = setTimeout(() => {
          if (!c.ended && pc.connectionState !== "connected") finish(c, "ended", "সংযোগ বিচ্ছিন্ন হয়েছে");
        }, DROP_MS);
      } else if (st === "failed") {
        finish(c, "ended", "সংযোগ করা যায়নি");
      }
    };
    if (navigator.wakeLock) {
      navigator.wakeLock.request("screen").then(l => {
        if (c.ended) l.release().catch(() => {});
        else c.wake = l;
      }).catch(() => {});
    }
    layout(c);
    refreshFlip(c);
    return pc;
  }

  function watchCall(c) {
    c.unsubs.push(onSnapshot(c.ref, snap => {
      if (c.ended || !snap.exists()) return;
      const d = snap.data();
      c.data = d;
      if (c.dir === "out" && d.answer && c.pc && !c.pc.remoteDescription) {
        stopTone();
        setStatus("সংযোগ হচ্ছে…");
        c.pc.setRemoteDescription(d.answer).then(() => flushPending(c)).catch(() => finish(c, "ended", "কল সংযোগ করা যায়নি"));
      }
      const off = d.cam ? d.cam[c.peerUid] === false : false;
      if (off !== c.peerCamOff) {
        c.peerCamOff = off;
        layout(c);
      }
      if (END_TEXT[d.status]) {
        const text = d.endReason === "busy" ? "ব্যস্ত আছেন" : (c.dir === "out" && d.status === "missed" ? "" : END_TEXT[d.status]);
        finish(c, d.status, text, true);
      }
    }));
  }

  function logCall(c, reason) {
    const icon = c.video ? "🎥" : "📞";
    const label = c.video ? "ভিডিও কল" : "ভয়েস কল";
    let text;
    if (c.connected) text = icon + " " + label + " · " + fmt(Math.floor((Date.now() - c.startedAt) / 1000));
    else if (reason === "declined") text = icon + " " + label + " প্রত্যাখ্যাত";
    else if (reason === "ended") text = icon + " " + label + " বাতিল";
    else text = icon + " মিসড " + label;
    send({ text }, { id: c.chatId, members: [myUid(), c.peerUid], reply: null }).catch(() => {});
  }

  function finish(c, reason, text, remote) {
    if (c.ended) return;
    c.ended = true;
    clearTimeout(c.ringTimer);
    clearTimeout(c.dropTimer);
    clearTimeout(c.outTimer);
    clearInterval(c.tick);
    c.unsubs.forEach(u => u());
    c.unsubs = [];
    stopTone();
    if (c.notif) c.notif.close();
    if (c.wake) c.wake.release().catch(() => {});
    if (c.pc) {
      c.pc.onconnectionstatechange = null;
      c.pc.onicecandidate = null;
      c.pc.ontrack = null;
      c.pc.close();
    }
    if (c.local) c.local.getTracks().forEach(t => t.stop());
    if (c.created && !remote) {
      const status = reason === "declined" || reason === "missed" ? reason : "ended";
      updateDoc(c.ref, { status, endedAt: serverTimestamp(), endReason: reason }).catch(() => {});
    }
    if (c.dir === "out" && c.created) logCall(c, reason);
    hideCall();
    if (call === c) call = null;
    if (text) toast(text);
  }

  const hangup = reason => {
    if (call) finish(call, reason, "");
  };

  async function startCall(video) {
    const active = getActive();
    const me = auth.currentUser;
    if (call || !me || !active || active.group || !active.peer) return;
    if (!window.RTCPeerConnection || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      toast("এই ডিভাইসে কল করা যাবে না");
      return;
    }
    const c = newCall({ dir: "out", video, peerUid: active.peer, chatId: active.id });
    call = c;
    showCall(c, "কল হচ্ছে…");
    startTone("out");
    let stream;
    let ice;
    try {
      [stream, ice] = await Promise.all([getMedia(video), getIce()]);
    } catch (err) {
      finish(c, "ended", mediaError(err));
      return;
    }
    if (c.ended) {
      stream.getTracks().forEach(t => t.stop());
      return;
    }
    try {
      const pc = makePeer(c, stream, ice);
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      if (c.ended) return;
      c.ref = doc(collection(db, "calls"));
      c.id = c.ref.id;
      await setDoc(c.ref, {
        caller: me.uid,
        callee: c.peerUid,
        chatId: c.chatId,
        video,
        status: "ringing",
        offer: { type: offer.type, sdp: offer.sdp },
        createdAt: serverTimestamp()
      });
      c.created = true;
      if (c.ended) {
        updateDoc(c.ref, { status: "ended", endedAt: serverTimestamp(), endReason: "ended" }).catch(() => {});
        return;
      }
      setStatus("রিং হচ্ছে…");
      watchCall(c);
      watchCandidates(c);
      c.canSend = true;
      flushOut(c);
      c.ringTimer = setTimeout(() => {
        if (!c.connected && !c.ended) finish(c, "missed", "কেউ ধরেনি");
      }, RING_MS);
    } catch (err) {
      finish(c, "ended", "কল শুরু করা যায়নি");
    }
  }

  function incoming(id, ref, d) {
    const c = newCall({ dir: "in", video: !!d.video, peerUid: d.caller, chatId: d.chatId });
    c.id = id;
    c.ref = ref;
    c.data = d;
    c.created = true;
    call = c;
    showCall(c, d.video ? "ইনকামিং ভিডিও কল" : "ইনকামিং ভয়েস কল");
    startTone("in");
    if (document.hidden && "Notification" in window && Notification.permission === "granted") {
      try {
        const name = (getUsers().get(d.caller) || {}).name || "";
        c.notif = new Notification(d.video ? "ইনকামিং ভিডিও কল" : "ইনকামিং ভয়েস কল", { body: name, icon: "icon-192.png", tag: "call-" + id, requireInteraction: true });
        c.notif.onclick = () => {
          window.focus();
          c.notif.close();
        };
      } catch {
        c.notif = null;
      }
    }
    c.ringTimer = setTimeout(() => {
      if (!c.accepted && !c.ended) finish(c, "missed", "মিসড কল", true);
    }, RING_MS + 5000);
  }

  async function accept() {
    const c = call;
    if (!c || c.dir !== "in" || c.accepted || c.ended) return;
    c.accepted = true;
    stopTone();
    clearTimeout(c.ringTimer);
    if (c.notif) c.notif.close();
    $("callIn").hidden = true;
    $("callBar").hidden = false;
    setStatus("সংযোগ হচ্ছে…");
    if (!window.RTCPeerConnection || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      finish(c, "declined", "এই ডিভাইসে কল করা যাবে না");
      return;
    }
    let stream;
    let ice;
    try {
      [stream, ice] = await Promise.all([getMedia(c.video), getIce()]);
    } catch (err) {
      finish(c, "declined", mediaError(err));
      return;
    }
    if (c.ended) {
      stream.getTracks().forEach(t => t.stop());
      return;
    }
    try {
      const pc = makePeer(c, stream, ice);
      await pc.setRemoteDescription(c.data.offer);
      watchCandidates(c);
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      if (c.ended) return;
      const fresh = await getDoc(c.ref);
      if (!fresh.exists() || fresh.data().status !== "ringing") {
        finish(c, "missed", "কলটি আগেই শেষ হয়ে গেছে", true);
        return;
      }
      await updateDoc(c.ref, { answer: { type: answer.type, sdp: answer.sdp }, status: "active" });
      c.canSend = true;
      flushOut(c);
      watchCall(c);
    } catch (err) {
      finish(c, "ended", "কল ধরা যায়নি");
    }
  }

  function toggleMute() {
    const c = call;
    if (!c || !c.local) return;
    const tracks = c.local.getAudioTracks();
    if (!tracks.length) return;
    const on = !tracks[0].enabled;
    tracks.forEach(t => { t.enabled = on; });
    $("callMute").classList.toggle("off", !on);
  }

  function toggleCam() {
    const c = call;
    if (!c || !c.local) return;
    const tracks = c.local.getVideoTracks();
    if (!tracks.length) return;
    const on = !tracks[0].enabled;
    tracks.forEach(t => { t.enabled = on; });
    $("callCam").classList.toggle("off", !on);
    $("localVideo").classList.toggle("off", !on);
    if (c.created && c.ref) updateDoc(c.ref, { ["cam." + myUid()]: on }).catch(() => {});
  }

  async function flipCam() {
    const c = call;
    if (!c || !c.local || !c.pc) return;
    const old = c.local.getVideoTracks()[0];
    if (!old) return;
    const prev = c.facing;
    const next = prev === "user" ? "environment" : "user";
    const open = facing => navigator.mediaDevices.getUserMedia({ video: { facingMode: facing, width: { ideal: 640 }, height: { ideal: 480 } } });
    const enabled = old.enabled;
    old.stop();
    let s;
    let used = next;
    try {
      s = await open(next);
    } catch {
      try {
        s = await open(prev);
        used = prev;
      } catch {
        toast("ক্যামেরা বদলানো যায়নি");
        return;
      }
    }
    if (c.ended) {
      s.getTracks().forEach(t => t.stop());
      return;
    }
    const track = s.getVideoTracks()[0];
    track.enabled = enabled;
    const sender = c.pc.getSenders().find(x => x.track && x.track.kind === "video");
    try {
      if (sender) await sender.replaceTrack(track);
    } catch {
      track.stop();
      toast("ক্যামেরা বদলানো যায়নি");
      return;
    }
    c.local.removeTrack(old);
    c.local.addTrack(track);
    c.facing = used;
    $("localVideo").srcObject = c.local;
    $("localVideo").classList.toggle("back", used === "environment");
  }

  function start(uid) {
    stop();
    const q = query(collection(db, "calls"), where("callee", "==", uid), where("status", "==", "ringing"));
    watchUnsub = onSnapshot(q, s => {
      s.docChanges().forEach(ch => {
        const d = ch.doc.data();
        if (ch.type === "removed") {
          const c = call;
          if (!c || c.dir !== "in" || c.id !== ch.doc.id || c.accepted || c.ended) return;
          const text = d.status === "active" ? "অন্য ডিভাইসে ধরা হয়েছে" : "মিসড কল";
          finish(c, "missed", text, true);
          return;
        }
        if (ch.type !== "added") return;
        const created = d.createdAt && d.createdAt.toMillis ? d.createdAt.toMillis() : 0;
        if (!created || Date.now() - created > STALE_MS) return;
        if (call) {
          if (call.id !== ch.doc.id) updateDoc(ch.doc.ref, { status: "declined", endedAt: serverTimestamp(), endReason: "busy" }).catch(() => {});
          return;
        }
        incoming(ch.doc.id, ch.doc.ref, d);
      });
    }, () => {});
  }

  function stop() {
    if (watchUnsub) watchUnsub();
    watchUnsub = null;
    if (call) finish(call, "ended", "");
  }

  $("voiceCallBtn").onclick = () => startCall(false);
  $("videoCallBtn").onclick = () => startCall(true);
  $("callAccept").onclick = accept;
  $("callDecline").onclick = () => hangup("declined");
  $("callEnd").onclick = () => hangup("ended");
  $("callMute").onclick = toggleMute;
  $("callCam").onclick = toggleCam;
  $("callFlip").onclick = flipCam;
  addEventListener("pagehide", () => {
    if (call && call.created && !call.ended) hangup("ended");
  });

  return { start, stop };
}
