import { t } from "../i18n.js";

const CLOUD_NAME = "xreqa1wz";
const UPLOAD_PRESET = "CovaMsg";

export async function upload(file) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("upload_preset", UPLOAD_PRESET);
  const res = await fetch(`https://api.cloudinary.com/v1_1/${CLOUD_NAME}/auto/upload`, { method: "POST", body: fd });
  if (!res.ok) throw new Error(t("chat.uploadFail"));
  return res.json();
}
