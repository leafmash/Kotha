# কথা (Dusk)

Firebase (Auth + Firestore + Cloud Messaging) ও Cloudinary ভিত্তিক PWA চ্যাট অ্যাপ। ফ্রন্টএন্ড সম্পূর্ণ স্ট্যাটিক, তাই Vercel-এ কোনো বিল্ড ছাড়াই ডিপ্লয় হয়।

## ফাইল কাঠামো

| ফাইল | কাজ |
| --- | --- |
| `index.html`, `style.css`, `app.js` | অ্যাপ |
| `sw.js` | সার্ভিস ওয়ার্কার (অফলাইন ক্যাশ ও পুশ নোটিফিকেশন) |
| `manifest.json`, `icon*.png/svg` | PWA ইনস্টল |
| `vercel.json`, `.vercelignore` | Vercel কনফিগ (শুধু অ্যাপের ফাইল ডিপ্লয় হয়) |
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

## আপডেটের পর পুরোনো ভার্সন দেখালে

`sw.js`-এর প্রথম লাইনে `CACHE` নামের ভার্সন সংখ্যা বাড়িয়ে (যেমন `kotha-v9` থেকে `kotha-v10`) commit ও push করুন।
