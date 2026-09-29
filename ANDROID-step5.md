# ANDROID.md - ধাপ ৫ (নতুন)

আপনার আপলোড করা zip-এ `ANDROID.md` ছিল না, তাই নিজের `ANDROID.md`-এর পুরোনো ধাপ ৫ (`firebase deploy --only functions`) মুছে নিচের লেখাটি বসান।

## ধাপ ৫: পুশ নোটিফিকেশন (Vercel, সম্পূর্ণ ফ্রি)

অ্যাপ বন্ধ বা ব্যাকগ্রাউন্ডে থাকলে মেসেজ ও কলের পুশ এখন Vercel-এর `api/notify.js` পাঠায়। Firebase Cloud Functions ও Blaze প্ল্যান লাগে না।

1. Firebase Console → Project settings → Service accounts → **Generate new private key** চাপুন। একটি JSON ফাইল নামবে।
2. ফাইলটি খুলে পুরো লেখা কপি করুন।
3. Vercel → আপনার প্রজেক্ট → Settings → Environment Variables-এ নতুন ভেরিয়েবল দিন:
   - Name: `FIREBASE_SERVICE_ACCOUNT`
   - Value: কপি করা পুরো JSON
   - Environments: Production, Preview, Development সবগুলো
4. Vercel → Deployments থেকে সর্বশেষ ডিপ্লয়মেন্টে **Redeploy** করুন।
5. GitHub-এ নতুন কোড পুশ করে GitHub Actions থেকে নতুন APK বিল্ড করুন।

সতর্কতা: এই JSON একটি গোপন চাবি। কারও সঙ্গে শেয়ার করবেন না এবং GitHub-এ আপলোড করবেন না। `.gitignore`-এ `*firebase-adminsdk*.json` আগে থেকেই আছে।

`functions/` ফোল্ডার আর ব্যবহার হয় না এবং সরিয়ে দেওয়া হয়েছে।
