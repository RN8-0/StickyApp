import { initializeApp } from 'firebase/app';
import { getFirestore } from 'firebase/firestore';
import { getStorage } from 'firebase/storage';
import { getAuth } from 'firebase/auth';

const firebaseConfig = {
    apiKey: "AIzaSyBQYpPuPxhBJcPFLCMCTE3F2PqlzeFWkzM",
    authDomain: "sticky-dcd20.firebaseapp.com",
    projectId: "sticky-dcd20",
    storageBucket: "sticky-dcd20.firebasestorage.app",
    messagingSenderId: "643314832062",
    appId: "1:643314832062:web:68a0146becd797934d04ef"
};

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
export const storage = getStorage(app);
export const auth = getAuth(app);
