import { t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { compressImage } from "../../core/image.js";
import { upload } from "../../core/upload.js";
import { send } from "./send.js";
import { toast } from "../../ui/toast.js";

export function initAttachments() {
  $("attachBtn").onclick = () => $("fileInput").click();
  $("fileInput").onchange = async e => {
    const file = e.target.files[0];
    e.target.value = "";
    if (!file || !state.active) return;
    const target = state.active;
    if (!navigator.onLine) {
      toast(t("chat.offlineMedia"));
      return;
    }
    toast(t("chat.uploading"), true);
    try {
      const prepared = await compressImage(file);
      const res = await upload(prepared);
      const type = res.resource_type === "image" ? "image" : res.resource_type === "video" ? (prepared.type.startsWith("audio") ? "audio" : "video") : "file";
      await send({ type, url: res.secure_url, name: prepared.name, size: prepared.size }, target);
      toast("");
    } catch (err) {
      toast(err.message);
    }
  };
}
