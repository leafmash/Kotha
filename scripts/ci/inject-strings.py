import os
import shutil

variants = ["values", "values-bn"]

for variant in variants:
    source_path = f"resources/android/{variant}/kotha_strings.xml"
    dest_dir = f"android/app/src/main/res/{variant}"
    dest_path = f"{dest_dir}/kotha_strings.xml"
    os.makedirs(dest_dir, exist_ok=True)
    shutil.copyfile(source_path, dest_path)

print("strings injected successfully")
