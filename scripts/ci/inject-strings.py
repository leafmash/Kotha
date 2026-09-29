import os
import shutil

source_path = "resources/android/values/kotha_strings.xml"
dest_dir = "android/app/src/main/res/values"
dest_path = f"{dest_dir}/kotha_strings.xml"

os.makedirs(dest_dir, exist_ok=True)
shutil.copyfile(source_path, dest_path)

print("strings injected successfully")
