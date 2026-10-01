const BATCH_SIZE = 400;
const MEDIA_BATCH = 100;
const CLOUDINARY_TYPES = ["image", "video", "raw"];

const cloudinaryIds = (urls, cloud) => {
  const groups = { image: [], video: [], raw: [] };
  if (!cloud) return groups;
  urls.forEach(value => {
    try {
      const url = new URL(value);
      if (url.hostname !== "res.cloudinary.com") return;
      const parts = url.pathname.split("/").filter(Boolean);
      const type = parts[1];
      if (parts[0] !== cloud || !CLOUDINARY_TYPES.includes(type) || parts[2] !== "upload") return;
      let rest = parts.slice(3);
      if (rest.length && /^v\d+$/.test(rest[0])) rest = rest.slice(1);
      if (!rest.length) return;
      let id = decodeURIComponent(rest.join("/"));
      if (type !== "raw") id = id.replace(/\.[^./]+$/, "");
      if (id) groups[type].push(id);
    } catch {
      return;
    }
  });
  return groups;
};

const deleteMedia = async (urls, config, fetchImpl) => {
  const { cloud, key, secret } = config || {};
  if (!cloud || !key || !secret || !urls.size) return 0;
  const groups = cloudinaryIds([...urls], cloud);
  const header = "Basic " + Buffer.from(`${key}:${secret}`).toString("base64");
  let requested = 0;
  for (const type of CLOUDINARY_TYPES) {
    const ids = [...new Set(groups[type])];
    for (let i = 0; i < ids.length; i += MEDIA_BATCH) {
      const body = new URLSearchParams();
      ids.slice(i, i + MEDIA_BATCH).forEach(id => body.append("public_ids[]", id));
      body.set("invalidate", "true");
      try {
        await fetchImpl(`https://api.cloudinary.com/v1_1/${cloud}/resources/${type}/upload`, {
          method: "DELETE",
          headers: { Authorization: header, "Content-Type": "application/x-www-form-urlencoded" },
          body,
          signal: AbortSignal.timeout ? AbortSignal.timeout(8000) : undefined
        });
        requested += ids.slice(i, i + MEDIA_BATCH).length;
      } catch {
        continue;
      }
    }
  }
  return requested;
};

const deleteRefs = async (db, refs) => {
  for (let i = 0; i < refs.length; i += BATCH_SIZE) {
    const batch = db.batch();
    refs.slice(i, i + BATCH_SIZE).forEach(ref => batch.delete(ref));
    await batch.commit();
  }
};

const collectUrls = (snap, into) => {
  snap.forEach(doc => {
    const url = doc.get("url");
    if (typeof url === "string" && url) into.add(url);
  });
};

const eraseUser = async ({ db, auth, FieldValue, uid, email, cloudinary, fetchImpl }) => {
  const media = new Set();
  const userRef = db.doc(`users/${uid}`);
  const userSnap = await userRef.get();
  if (userSnap.exists && typeof userSnap.data().photo === "string" && userSnap.data().photo) media.add(userSnap.data().photo);

  const chats = await db.collection("chats").where("members", "array-contains", uid).get();
  for (const chatDoc of chats.docs) {
    const chat = chatDoc.data();
    const messages = chatDoc.ref.collection("messages");
    if (chat.group !== true) {
      collectUrls(await messages.select("url").get(), media);
      await db.recursiveDelete(chatDoc.ref);
      continue;
    }
    const mine = await messages.where("from", "==", uid).select("url").get();
    collectUrls(mine, media);
    await deleteRefs(db, mine.docs.map(d => d.ref));
    const remaining = (chat.members || []).filter(m => m !== uid);
    if (!remaining.length) {
      await db.recursiveDelete(chatDoc.ref);
      continue;
    }
    const patch = {
      members: FieldValue.arrayRemove(uid),
      [`unread.${uid}`]: FieldValue.delete(),
      [`typing.${uid}`]: FieldValue.delete()
    };
    let admins = (chat.admins || []).filter(m => remaining.includes(m));
    const admin = remaining.includes(chat.admin) ? chat.admin : admins[0] || remaining[0];
    if (!admins.includes(admin)) admins = [...admins, admin];
    patch.admin = admin;
    patch.admins = admins;
    if (chat.lastFrom === uid) {
      patch.lastMessage = "[[deleted]]";
      patch.lastFrom = "";
    }
    await chatDoc.ref.update(patch);
  }

  const [asCaller, asCallee] = await Promise.all([
    db.collection("calls").where("caller", "==", uid).get(),
    db.collection("calls").where("callee", "==", uid).get()
  ]);
  for (const snap of [asCaller, asCallee]) {
    for (const callDoc of snap.docs) await db.recursiveDelete(callDoc.ref);
  }

  await db.recursiveDelete(db.doc(`pushTokens/${uid}`));

  const mail = (email || "").toLowerCase();
  if (mail) {
    const lookupRef = db.doc(`emailLookup/${mail}`);
    const lookup = await lookupRef.get();
    if (lookup.exists && lookup.data().uid === uid) await lookupRef.delete();
  }

  await db.recursiveDelete(userRef);
  const requested = await deleteMedia(media, cloudinary, fetchImpl);
  await auth.deleteUser(uid);
  return { media: media.size, mediaRequested: requested };
};

module.exports = { eraseUser, cloudinaryIds, deleteMedia };
