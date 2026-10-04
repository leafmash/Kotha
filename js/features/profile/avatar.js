import { doc, updateDoc } from "../../core/sdk.js";
import { t } from "../../i18n.js";
import { $ } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { compressImage } from "../../core/image.js";
import { upload } from "../../core/upload.js";
import { toast } from "../../ui/toast.js";

export function initAvatar() {
  $("profileAvatar").onclick = () => $("avatarInput").click();
  $("profileSetPhoto").onclick = () => $("avatarInput").click();
  $("avatarInput").onchange = async e => {
    const file = e.target.files[0];
    e.target.value = "";
    if (!file) return;
    if (!navigator.onLine) {
      toast(t("chat.offlineMedia"));
      return;
    }
    toast(t("photo.uploading"), true);
    try {
      const res = await upload(await compressImage(file, 640, 0.85));
      await updateDoc(doc(db, "users", auth.currentUser.uid), { photo: res.secure_url });
      toast(t("photo.changed"));
    } catch (err) {
      toast(err.message);
    }
  };
}
