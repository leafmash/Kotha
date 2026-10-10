# Cova - Play Store readiness

## Blockers to resolve before publishing
- Target API: Play requires targetSdk 36 for new apps and updates since 31 Aug 2026. Current project targets 35 (AGP 8.7.3). Upgrade AGP to 8.9.1 or newer, Gradle to 8.11.1 or newer, set compileSdk and targetSdk to 36 in a separate tested step.
- Upload format: Play requires an Android App Bundle. Run `./gradlew bundleRelease` in CI and upload `app/build/outputs/bundle/release/app-release.aab`.
- Signing: enrol in Play App Signing; keep the current keystore as the upload key.

## Permission declarations (Play Console > App content)
- Foreground service types: phoneCall, microphone, camera. Purpose: ongoing one-to-one voice and video call that must keep running when the app is in the background.
- USE_FULL_SCREEN_INTENT: incoming call screen over the lock screen. Core function is calling.
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: needed so incoming messages and calls are delivered reliably. If review rejects it, remove the permission; the Settings row keeps working through the system settings screen.
- CAMERA, RECORD_AUDIO: video calls, voice messages, photo and video capture.
- POST_NOTIFICATIONS: message and call notifications.
- BLUETOOTH_CONNECT: route call audio to Bluetooth headsets.

## Data safety form (suggested answers, verify against your backend)
- Data collected: email address, name, profile photo, user IDs, messages, photos and videos, audio files and voice messages, device or other IDs (FCM token), approximate call metadata.
- Purpose: app functionality, account management.
- Shared with third parties: media storage provider (Cloudinary) and Firebase as processors. Not sold.
- Encrypted in transit: yes. Users can request deletion: yes, in-app account deletion exists.

## Listing text
Short description (80): Fast, clean chat with voice and video calls.
Full description: Cova is a private messenger built natively for Android. Send messages, photos, videos, files and voice notes, create groups, and make clear one-to-one voice and video calls. Light and dark themes, Bengali and English, and notifications that reply from the shade.

## Release checklist
- Privacy policy URL and account deletion URL filled in Play Console.
- Content rating questionnaire completed (user generated content: yes, report and block tools exist).
- Test on a low-end device, Android 8 to 15, TalkBack on, largest font size, reduced animations.
- Verify calls on mobile data and Wi-Fi, lock screen ringing, Bluetooth routing.
