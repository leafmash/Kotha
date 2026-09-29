import os
import shutil

source_path = "resources/android/raw/kotha_message.wav"
dest_dir = "android/app/src/main/res/raw"
dest_path = f"{dest_dir}/kotha_message.wav"

os.makedirs(dest_dir, exist_ok=True)
shutil.copyfile(source_path, dest_path)

print("notification sound injected successfully")
