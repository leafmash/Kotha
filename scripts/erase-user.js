const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getApp } = require("../api/_firebase");
const { eraseUser } = require("../api/_erase");

const target = process.argv[2];

if (!target || !process.env.FIREBASE_SERVICE_ACCOUNT) {
  console.error("Usage: FIREBASE_SERVICE_ACCOUNT='<service account json>' node scripts/erase-user.js <email-or-uid>");
  process.exit(1);
}

const run = async () => {
  const app = getApp();
  const auth = getAuth(app);
  const record = target.includes("@") ? await auth.getUserByEmail(target) : await auth.getUser(target);
  const result = await eraseUser({
    db: getFirestore(app),
    auth,
    FieldValue,
    uid: record.uid,
    email: record.email || "",
    cloudinary: {
      cloud: process.env.CLOUDINARY_CLOUD_NAME,
      key: process.env.CLOUDINARY_API_KEY,
      secret: process.env.CLOUDINARY_API_SECRET
    },
    fetchImpl: fetch
  });
  console.log("erased", record.uid, result);
};

run().catch(err => {
  console.error(err);
  process.exit(1);
});
