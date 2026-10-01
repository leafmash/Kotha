import os
import shutil
import sys

source_dir = "resources/android/raw"
dest_dir = "android/app/src/main/res/raw"
extensions = ("wav", "mp3", "ogg")
sounds = (("kotha_message", True), ("kotha_call", False))

os.makedirs(dest_dir, exist_ok=True)

for name, required in sounds:
    found = [e for e in extensions if os.path.isfile(f"{source_dir}/{name}.{e}")]
    if not found:
        if required:
            print(f"missing required sound: {name}")
            sys.exit(1)
        print(f"optional sound not provided: {name}")
        continue
    ext = found[0]
    for old in extensions:
        stale = f"{dest_dir}/{name}.{old}"
        if os.path.isfile(stale):
            os.remove(stale)
    shutil.copyfile(f"{source_dir}/{name}.{ext}", f"{dest_dir}/{name}.{ext}")
    print(f"sound injected: {name}.{ext}")
