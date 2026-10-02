import os
import re
import sys
from urllib.parse import urlparse

path = "www/js/config.js"

url = os.environ.get("APP_URL", "").strip().rstrip("/")
parsed = urlparse(url)

if parsed.scheme != "https" or not parsed.netloc or parsed.path not in ("", "/"):
    print("APP_URL must be a bare https address such as https://cova.vercel.app", file=sys.stderr)
    sys.exit(1)

with open(path, "r", encoding="utf-8") as f:
    text = f.read()

text, count = re.subn(r'API_BASE_NATIVE\s*=\s*"[^"]*"', f'API_BASE_NATIVE = "{url}"', text, count=1)
if count == 0:
    print("Could not find API_BASE_NATIVE in www/js/config.js", file=sys.stderr)
    sys.exit(1)

with open(path, "w", encoding="utf-8") as f:
    f.write(text)

print(f"api base injected: {url}")
