import os
import re
import sys

path = "android/app/build.gradle"

run_number = os.environ.get("RUN_NUMBER", "").strip()
base_name = os.environ.get("VERSION_NAME", "").strip() or "1.0"
offset = os.environ.get("VERSION_CODE_OFFSET", "").strip() or "0"

if not run_number.isdigit() or not offset.isdigit():
    print("RUN_NUMBER and VERSION_CODE_OFFSET must be numbers", file=sys.stderr)
    sys.exit(1)

version_code = int(run_number) + int(offset)
version_name = f"{base_name}.{run_number}"

with open(path, "r") as f:
    text = f.read()

text, code_count = re.subn(r"versionCode\s+\d+", f"versionCode {version_code}", text, count=1)
text, name_count = re.subn(r'versionName\s+"[^"]*"', f'versionName "{version_name}"', text, count=1)

if code_count == 0 or name_count == 0:
    print("Could not find versionCode or versionName in build.gradle", file=sys.stderr)
    sys.exit(1)

with open(path, "w") as f:
    f.write(text)

print(f"version set to {version_name} ({version_code})")
