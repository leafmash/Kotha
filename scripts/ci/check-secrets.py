import os
import sys

REQUIRED = {
    "APP_URL": "Settings > Secrets and variables > Actions > Variables",
    "GOOGLE_SERVICES_JSON_BASE64": "Settings > Secrets and variables > Actions > Secrets",
    "ANDROID_KEYSTORE_BASE64": "Settings > Secrets and variables > Actions > Secrets",
    "ANDROID_KEYSTORE_PASSWORD": "Settings > Secrets and variables > Actions > Secrets",
    "ANDROID_KEY_ALIAS": "Settings > Secrets and variables > Actions > Secrets",
    "ANDROID_KEY_PASSWORD": "Settings > Secrets and variables > Actions > Secrets",
}

missing = [name for name in REQUIRED if not os.environ.get(name, "").strip()]

if missing:
    print("Missing required configuration:", file=sys.stderr)
    for name in missing:
        print(f"  {name}  ->  {REQUIRED[name]}", file=sys.stderr)
    sys.exit(1)

print("all required secrets and variables are present")
