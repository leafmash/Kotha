import { doc, updateDoc, updateProfile } from "../../core/sdk.js";
import { fmtNumber, t } from "../../i18n.js";
import { state } from "../../core/state.js";
import { $ } from "../../core/dom.js";
import { auth, db } from "../../core/firebase.js";
import { toast } from "../../ui/toast.js";

const epName = $("epName");
const epBio = $("epBio");
let epInitial = { name: "", bio: "" };
const cleanName = v => v.replace(/\s+/g, " ").trim();
const cleanBio = v => v.replace(/[ \t]+/g, " ").replace(/\s*\n\s*/g, "\n").trim();

function syncEditProfile() {
  $("epBioCount").textContent = fmtNumber(epBio.value.length) + "/" + fmtNumber(epBio.maxLength);
  $("editProfileSave").disabled = !cleanName(epName.value);
}

function openEditProfile() {
  const user = auth.currentUser;
  if (!user) return;
  const me = state.users.get(user.uid);
  epInitial = { name: me?.name || user.displayName || "", bio: me?.bio || "" };
  epName.value = epInitial.name;
  epBio.value = epInitial.bio;
  syncEditProfile();
  $("editProfileSheet").hidden = false;
  $("editProfileSheet").querySelector(".gbody").scrollTop = 0;
  epName.focus();
}

export function closeEditProfile() {
  $("editProfileSheet").hidden = true;
  epName.blur();
  epBio.blur();
}

async function saveEditProfile() {
  const user = auth.currentUser;
  if (!user) return;
  const name = cleanName(epName.value).slice(0, epName.maxLength);
  const bio = cleanBio(epBio.value).slice(0, epBio.maxLength);
  if (!name) {
    toast(t("profile.nameRequired"));
    return;
  }
  if (name === epInitial.name && bio === epInitial.bio) {
    closeEditProfile();
    return;
  }
  if (!navigator.onLine) {
    toast(t("profile.offline"));
    return;
  }
  $("editProfileSave").disabled = true;
  try {
    await updateDoc(doc(db, "users", user.uid), { name, bio });
    if (name !== epInitial.name) updateProfile(user, { displayName: name }).catch(() => {});
    toast(t("profile.saved"));
    closeEditProfile();
  } catch {
    toast(t("profile.saveFail"));
    syncEditProfile();
  }
}

export function initEditProfile() {
  $("profileEdit").onclick = openEditProfile;
  $("editProfileClose").onclick = closeEditProfile;
  $("editProfileSave").onclick = saveEditProfile;
  epName.oninput = syncEditProfile;
  epBio.oninput = syncEditProfile;
  epName.onkeydown = e => {
    if (e.key === "Enter") {
      e.preventDefault();
      saveEditProfile();
    }
  };
}
