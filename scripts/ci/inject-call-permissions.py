import re
import sys

manifest_path = "android/app/src/main/AndroidManifest.xml"

PERMISSIONS = [
    "android.permission.RECORD_AUDIO",
    "android.permission.CAMERA",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.USE_FULL_SCREEN_INTENT",
    "android.permission.WAKE_LOCK",
    "android.permission.VIBRATE",
]

FEATURES = [
    "android.hardware.camera",
    "android.hardware.camera.front",
    "android.hardware.microphone",
]

with open(manifest_path, "r") as f:
    manifest = f.read()

lines = ""
for name in PERMISSIONS:
    if name not in manifest:
        lines += f'    <uses-permission android:name="{name}" />\n'
for name in FEATURES:
    if f'android:name="{name}"' not in manifest:
        lines += f'    <uses-feature android:name="{name}" android:required="false" />\n'

if not lines:
    print("call permissions already injected")
    sys.exit(0)

manifest, count = re.subn(r"(<application[^>]*>)", lines + r"\1", manifest, count=1)
if count == 0:
    print("Could not find '<application' tag in AndroidManifest.xml", file=sys.stderr)
    sys.exit(1)

with open(manifest_path, "w") as f:
    f.write(manifest)

print("call permissions injected successfully")
