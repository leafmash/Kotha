import { browserLocalPersistence, browserPopupRedirectResolver, getDatabase, indexedDBLocalPersistence, initializeApp, initializeAuth, initializeFirestore, persistentLocalCache, persistentMultipleTabManager } from "./sdk.js";
import { createPushTrigger } from "../push-trigger.js";

export const firebaseConfig = {
  apiKey: "AIzaSyAFMdkcndeYSl2A4MckeEBRG0YlAFlorzA",
  authDomain: "duskchat-e02ba.firebaseapp.com",
  projectId: "duskchat-e02ba",
  storageBucket: "duskchat-e02ba.firebasestorage.app",
  messagingSenderId: "96458608876",
  appId: "1:96458608876:web:52e1a5e66dc79ba94795c9",
  measurementId: "G-591ZFVNG23"
};
const RTDB_URL = "https://duskchat-e02ba-default-rtdb.asia-southeast1.firebasedatabase.app";

export const app = initializeApp(firebaseConfig);
export const auth = initializeAuth(app, { persistence: [indexedDBLocalPersistence, browserLocalPersistence], popupRedirectResolver: browserPopupRedirectResolver });
export const triggerPush = createPushTrigger(auth);
export const db = initializeFirestore(app, { localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }) });

export const rtdb = RTDB_URL ? getDatabase(app, RTDB_URL) : null;
