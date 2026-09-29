# কথা (Dusk)

Firebase (Auth + Firestore + Cloud Messaging) ও Cloudinary ভিত্তিক PWA চ্যাট অ্যাপ। ফ্রন্টএন্ড সম্পূর্ণ স্ট্যাটিক, তাই Vercel-এ কোনো বিল্ড ছাড়াই ডিপ্লয় হয়।

## ফাইল কাঠামো

| ফাইল | কাজ |
| --- | --- |
| `index.html`, `css/style.css`, `js/app.js`, `js/call.js` | অ্যাপ (call.js হলো কল সিস্টেম) |
| `api/turn.js` | কলের জন্য TURN ক্রেডেনশিয়াল (Vercel Function) |
| `sw.js` | সার্ভিস ওয়ার্কার (অফলাইন ক্যাশ ও পুশ নোটিফিকেশন) |
| `manifest.json`, `icon*.png/svg` | PWA ইনস্টল |
| `vercel.json`, `.vercelignore` | Vercel কনফিগ (শুধু অ্যাপের ফাইল ডিপ্লয় হয়) |
| `android/`, `tools/inject_android.py`, `.github/workflows/android.yml` | Android APK (বিস্তারিত `android/README.md`-তে) |
| `firestore.rules`, `firebase.json`, `functions/` | Firebase-এ আলাদাভাবে ডিপ্লয় হয়, Vercel-এ নয় |

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
   - `cd functions && npm install`, তারপর প্রজেক্ট রুটে `firebase deploy --only functions`। এর জন্য Blaze প্ল্যান লাগে।
5. **API কী সুরক্ষা (প্রস্তাবিত):** Google Cloud Console → APIs & Services → Credentials-এ Firebase Browser Key-এর HTTP referrer সীমা আপনার ডোমেইনে বেঁধে দিন। Firebase-এর ওয়েব কনফিগ গোপন তথ্য নয়, আসল সুরক্ষা আসে Firestore নিয়ম থেকে।

## ভয়েস ও ভিডিও কল

কল WebRTC দিয়ে সরাসরি দুই ডিভাইসের মধ্যে চলে। সংযোগ-তথ্য আদান-প্রদান হয় Firestore-এর `calls` কালেকশনে। কঠিন নেটওয়ার্কে (মোবাইল ডেটা ইত্যাদি) কল চালাতে TURN সার্ভার লাগে, যার ক্রেডেনশিয়াল দেয় `api/turn.js` (Vercel Function)। এখানে Metered Open Relay ব্যবহার হয়েছে, কার্ড ছাড়াই সাইনআপ করা যায় (মাসে ২০ GB ফ্রি)।

1. https://www.metered.ca/tools/openrelay/ এ গিয়ে ইমেইল দিয়ে ফ্রি অ্যাকাউন্ট খুলুন (কার্ড লাগে না)।
2. ড্যাশবোর্ডে একটা App তৈরি করুন। সেখান থেকে আপনার সাবডোমেইন (যেমন `kotha.metered.live`) ও API Key পাবেন।
3. Vercel → Project → Settings → Environment Variables-এ দিন:
   - `METERED_SUBDOMAIN`: শুধু সাবডোমেইনের প্রথম অংশ, যেমন `kotha` (পুরো URL না)
   - `METERED_API_KEY`: আপনার API key
4. Vercel-এ Redeploy করুন।
5. `firestore.rules` আবার পাবলিশ করুন।
6. অ্যাপ বন্ধ থাকলে কলের নোটিফিকেশনের জন্য `firebase deploy --only functions` (`notifyCall` ফাংশন)।

ভেরিয়েবল না দিলে শুধু Google STUN ব্যবহার হবে, তখন অনেক নেটওয়ার্কে কল সংযোগ নাও হতে পারে। ২০ GB মাসিক কোটা শেষ হলে TURN কাজ করবে না যতক্ষণ না পরের মাস শুরু হয় বা আপগ্রেড করেন।

## আপডেটের পর পুরোনো ভার্সন দেখালে

`sw.js`-এর প্রথম লাইনে `CACHE` নামের ভার্সন সংখ্যা বাড়িয়ে (যেমন `kotha-v9` থেকে `kotha-v10`) commit ও push করুন।

## Android APK

`ANDROID.md` দেখুন।
