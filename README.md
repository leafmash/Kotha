# Cova (Dusk)

Firebase (Auth + Firestore + Cloud Messaging) ও Cloudinary ভিত্তিক PWA চ্যাট অ্যাপ। ফ্রন্টএন্ড সম্পূর্ণ স্ট্যাটিক, তাই Vercel-এ কোনো বিল্ড ছাড়াই ডিপ্লয় হয়।

## ফাইল কাঠামো

| ফাইল | কাজ |
| --- | --- |
| `index.html`, `css/style.css`, `js/app.js`, `js/call.js` | অ্যাপ (call.js হলো কল সিস্টেম) |
| `api/turn.js` | কলের জন্য TURN ক্রেডেনশিয়াল (Vercel Function) |
| `api/notify.js` | মেসেজ ও কলের পুশ নোটিফিকেশন পাঠায় (Vercel Function, Firebase Admin) |
| `js/i18n.js` | ইংরেজি/বাংলা ভাষা সুইচ (ডিফল্ট ইংরেজি) |
| `api/delete-account.js`, `api/_erase.js`, `api/_firebase.js` | অ্যাকাউন্ট ও সব তথ্য মোছে (Firebase Admin), ফাইল Cloudinary থেকেও সরায় |
| `privacy.html`, `terms.html`, `delete-account.html` | গোপনীয়তা নীতি, শর্তাবলি, অ্যাকাউন্ট মোছার পাতা (Play Store লিংক) |
| `scripts/erase-user.js` | কেউ ইমেইলে অনুরোধ করলে হাতে অ্যাকাউন্ট মোছার স্ক্রিপ্ট |
| `js/push-trigger.js` | মেসেজ/কল লেখার পর `api/notify` ডাকার helper |
| `sw.js` | সার্ভিস ওয়ার্কার (অফলাইন ক্যাশ ও পুশ নোটিফিকেশন) |
| `manifest.json`, `icon*.png/svg` | PWA ইনস্টল |
| `vercel.json`, `.vercelignore` | Vercel কনফিগ (শুধু অ্যাপের ফাইল ডিপ্লয় হয়) |
| `android/`, `tools/inject_android.py`, `.github/workflows/android.yml` | Android APK (বিস্তারিত `android/README.md`-তে) |
| `firestore.rules`, `firebase.json` | Firebase-এ আলাদাভাবে ডিপ্লয় হয়, Vercel-এ নয় |

## GitHub-এ আপলোড

```bash
git init
git add .
git commit -m "first commit"
git branch -M main
git remote add origin https://github.com/<আপনার-ইউজারনেম>/<রিপোজিটরি>.git
git push -u origin main
```

## Vercel-এ ডিপ্লয়

1. vercel.com-এ GitHub দিয়ে লগইন করুন।
2. **Add New → Project** থেকে রিপোজিটরিটি ইমপোর্ট করুন।
3. Framework Preset: **Other**। Build Command, Output Directory ও Install Command খালি রাখুন (`vercel.json` থেকেই আসবে)।
4. **Deploy** চাপুন।

এরপর `main` ব্রাঞ্চে প্রতিটি push-এ নিজে থেকে নতুন ভার্সন ডিপ্লয় হবে। অন্য ব্রাঞ্চের push-এ প্রিভিউ লিংক তৈরি হবে।

## ডিপ্লয়ের পর অবশ্যই করণীয়

1. **Firebase অনুমোদিত ডোমেইন:** Firebase Console → Authentication → Settings → Authorized domains-এ আপনার Vercel ডোমেইন (`<প্রজেক্ট>.vercel.app` এবং নিজস্ব ডোমেইন থাকলে সেটাও) যোগ করুন। না করলে লগইন কাজ করবে না, বিশেষ করে Google দিয়ে সাইন ইন।
2. **Firestore নিয়ম:** `firestore.rules` Firebase Console-এ পাবলিশ করুন, অথবা `firebase deploy --only firestore:rules`।
3. **Cloudinary:** `app.js`-এ `CLOUD_NAME` ও `UPLOAD_PRESET` বসান (আনসাইনড প্রিসেট লাগবে)।
4. **পুশ নোটিফিকেশন (ঐচ্ছিক):**
   - Firebase Console → Project settings → Cloud Messaging → Web Push certificates থেকে কী নিয়ে `app.js`-এর `VAPID_KEY`-তে বসান।
   - Firebase Console → Project settings → Service accounts → Generate new private key থেকে JSON নামিয়ে পুরো লেখাটি Vercel → Settings → Environment Variables-এ `FIREBASE_SERVICE_ACCOUNT` নামে বসান, তারপর Redeploy করুন। Cloud Functions বা Blaze প্ল্যান লাগে না।
5. **API কী সুরক্ষা (প্রস্তাবিত):** Google Cloud Console → APIs & Services → Credentials-এ Firebase Browser Key-এর HTTP referrer সীমা আপনার ডোমেইনে বেঁধে দিন। Firebase-এর ওয়েব কনফিগ গোপন তথ্য নয়, আসল সুরক্ষা আসে Firestore নিয়ম থেকে।

## ভয়েস ও ভিডিও কল

কল WebRTC দিয়ে সরাসরি দুই ডিভাইসের মধ্যে চলে। সংযোগ-তথ্য আদান-প্রদান হয় Firestore-এর `calls` কালেকশনে। কঠিন নেটওয়ার্কে (মোবাইল ডেটা ইত্যাদি) কল চালাতে TURN সার্ভার লাগে, যার ক্রেডেনশিয়াল দেয় `api/turn.js` (Vercel Function)। এখানে Metered Open Relay ব্যবহার হয়েছে, কার্ড ছাড়াই সাইনআপ করা যায় (মাসে ২০ GB ফ্রি)।

1. https://www.metered.ca/tools/openrelay/ এ গিয়ে ইমেইল দিয়ে ফ্রি অ্যাকাউন্ট খুলুন (কার্ড লাগে না)।
2. ড্যাশবোর্ডে একটা App তৈরি করুন। সেখান থেকে আপনার সাবডোমেইন (যেমন `cova.metered.live`) ও API Key পাবেন।
3. Vercel → Project → Settings → Environment Variables-এ দিন:
   - `METERED_SUBDOMAIN`: শুধু সাবডোমেইনের প্রথম অংশ, যেমন `cova` (পুরো URL না)
   - `METERED_API_KEY`: আপনার API key
4. Vercel-এ Redeploy করুন।
5. `firestore.rules` আবার পাবলিশ করুন।
6. অ্যাপ বন্ধ থাকলে কলের নোটিফিকেশনের জন্য উপরের `FIREBASE_SERVICE_ACCOUNT` বসানো থাকলেই হবে (`api/notify.js` কাজটি করে)।

ভেরিয়েবল না দিলে শুধু Google STUN ব্যবহার হবে, তখন অনেক নেটওয়ার্কে কল সংযোগ নাও হতে পারে। ২০ GB মাসিক কোটা শেষ হলে TURN কাজ করবে না যতক্ষণ না পরের মাস শুরু হয় বা আপগ্রেড করেন।

## আপডেটের পর পুরোনো ভার্সন দেখালে

`sw.js`-এর প্রথম লাইনে `CACHE` নামের ভার্সন সংখ্যা বাড়িয়ে (যেমন `cova-v9` থেকে `cova-v10`) commit ও push করুন।

## Android APK

`ANDROID.md` দেখুন।

## ব্লক, রিপোর্ট, অ্যাকাউন্ট মোছা ও আইনি পাতা

অ্যাপে যা যোগ হয়েছে:

- **ব্লক:** চ্যাটের ওপরের তিন-ডট মেনু, অথবা গ্রুপে অন্যের মেসেজে লং প্রেস থেকে। ব্লক করা ব্যক্তি মেসেজ, কল ও নোটিফিকেশন পাঠাতে পারে না (Firestore নিয়ম ও `api/notify.js` দুই জায়গায় আটকানো)। তালিকা ও আনব্লক: তিন-ডট মেনু, সেটিংস।
- **রিপোর্ট:** ব্যবহারকারী, মেসেজ ও গ্রুপ রিপোর্ট করা যায়। রিপোর্ট Firestore-এর `reports` কালেকশনে জমা হয়, অ্যাপ থেকে পড়া যায় না।
- **গ্রুপ ছাড়া:** গ্রুপ চ্যাটের মেনুতে।
- **অ্যাকাউন্ট মোছা:** সেটিংসে। পাসওয়ার্ড বা Google দিয়ে আবার পরিচয় নিশ্চিত করে `api/delete-account` ডাকে।
- **শর্ত গ্রহণ:** সাইন আপ পাতায় লিংক আছে। প্রথমবার ঢোকার পর (পুরোনো ব্যবহারকারীদেরও) একবার "আমি সম্মত" চাওয়া হয়। শর্ত বদলালে `js/config.js`-এর `TERMS_VERSION` বদলান, সবার কাছে আবার আসবে।

### ডিপ্লয়ের আগে অবশ্যই করণীয়

1. `privacy.html`, `terms.html`, `delete-account.html`-এ `in.with.imran@gmail.com` খুঁজে আপনার আসল ইমেইল বসান।
2. `firestore.rules` আবার পাবলিশ করুন (`firebase deploy --only firestore:rules`)। নতুন নিয়ম ছাড়া ব্লক ও রিপোর্ট কাজ করবে না।
3. Vercel → Environment Variables-এ অ্যাকাউন্ট মোছার সময় Cloudinary-র ফাইল সরাতে এই তিনটি দিন (না দিলে ডাটা মুছবে, কিন্তু Cloudinary-র ফাইল থেকে যাবে):
   - `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
4. Vercel-এ Redeploy করুন। `FIREBASE_SERVICE_ACCOUNT` আগে থেকেই থাকতে হবে।
5. Google Play Console → App content → Privacy policy-তে `https://<আপনার-ডোমেইন>/privacy.html` এবং Data safety → Account deletion-এ `https://<আপনার-ডোমেইন>/delete-account.html` দিন।
6. রিপোর্ট পর্যালোচনা: Firebase Console → Firestore → `reports`। শর্তে ২৪ ঘণ্টার মধ্যে ব্যবস্থার কথা আছে, তাই নিয়মিত দেখতে হবে।
7. ইমেইলে মোছার অনুরোধ এলে: `FIREBASE_SERVICE_ACCOUNT='...' node scripts/erase-user.js user@example.com`

নোট: গোপনীয়তা নীতি ও শর্তাবলি সাধারণ টেমপ্লেট। প্রকাশের আগে নিজের অ্যাপের সঙ্গে মিলিয়ে দেখে নিন।

## পাসওয়ার্ড রিসেট ও ইমেইল যাচাই

- লগইন পাতায় "পাসওয়ার্ড ভুলে গেছেন?" লিংক। ইমেইল লিখে চাপলে Firebase রিসেট লিংক পাঠায়। অ্যাকাউন্ট আছে কি না সেটা জানানো হয় না।
- ইমেইল/পাসওয়ার্ডে সাইন আপ করলে নিজে থেকে যাচাইয়ের ইমেইল যায়। যাচাই না হওয়া পর্যন্ত সাইডবারে একটি বার দেখায় ("আবার পাঠান", "যাচাই করেছি")। যাচাই ছাড়াও চ্যাট করা যায়।
- `emailLookup` (ইমেইল দিয়ে খোঁজা) এখন শুধু যাচাই করা ইমেইলের জন্য লেখা যায়, তাই যাচাই না করা পর্যন্ত কেউ ইমেইল দিয়ে ওই ব্যবহারকারীকে খুঁজে পাবে না। Google দিয়ে ঢুকলে ইমেইল আগে থেকেই যাচাই করা।
- ইমেইলের ভাষা অ্যাপের ভাষা অনুযায়ী যায়। চেহারা বদলাতে Firebase Console → Authentication → Templates।
- এই কাজের পর `firestore.rules` আবার পাবলিশ করতে হবে।

## মেসেজ এডিট ও ফরওয়ার্ড

- নিজের টেক্সট মেসেজ পাঠানোর ১৫ মিনিটের মধ্যে এডিট করা যায়। মেসেজের মেনুতে "এডিট করুন"। এডিট করা মেসেজে "এডিট করা" লেখা দেখায়।
- সময়সীমা বদলাতে `js/config.js`-এর `EDIT_WINDOW_MS` এবং `firestore.rules`-এর `duration.value(15, 'm')` দুটোই একসঙ্গে বদলান। দুটো না মিললে সার্ভার এডিট আটকে দেবে।
- মেসেজের মেনু থেকে "ফরওয়ার্ড" দিয়ে সর্বোচ্চ ৫টি চ্যাটে একসঙ্গে পাঠানো যায় (`FORWARD_MAX`)। টেক্সট, ছবি, ভিডিও, ভয়েস ও ফাইল ফরওয়ার্ড হয়। কল ও সিস্টেম মেসেজ হয় না। ব্লক করা চ্যাট এবং যেসব গ্রুপে শুধু অ্যাডমিন লিখতে পারে সেগুলো তালিকায় আসে না।
- ফরওয়ার্ড করা মিডিয়া একই Cloudinary ফাইল ব্যবহার করে। তাই `api/_erase.js` ফরওয়ার্ড করা মেসেজের ফাইল Cloudinary থেকে মোছে না।
