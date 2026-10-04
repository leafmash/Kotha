import { t } from "../i18n.js";

const CLOUD_NAME = "YOUR_CLOUDINARY_CLOUD_NAME";
const UPLOAD_PRESET = "YOUR_UNSIGNED_UPLOAD_PRESET";

export async function upload(file) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("upload_preset", UPLOAD_PRESET);
  const res = await fetch(`https://api.cloudinary.com/v1_1/${CLOUD_NAME}/auto/upload`, { method: "POST", body: fd });
  if (!res.ok) throw new Error(t("chat.uploadFail"));
  return res.json();
}
