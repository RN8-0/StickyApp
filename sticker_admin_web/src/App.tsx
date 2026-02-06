import { useState, useEffect, useMemo } from 'react';
import { db, storage, auth } from './firebase';
import {
  collection,
  getDocs,
  deleteDoc,
  doc,
  updateDoc,
  arrayRemove,
  setDoc,
  serverTimestamp,
  onSnapshot,
  getDoc,
  arrayUnion
} from 'firebase/firestore';
import {
  ref,
  deleteObject,
  uploadBytes,
  getDownloadURL,
  listAll,
} from 'firebase/storage';
import {
  signInWithPopup,
  GoogleAuthProvider,
  onAuthStateChanged,
  signOut,
  type User
} from 'firebase/auth';
import {
  Package,
  Trash2,
  RefreshCcw,
  Search,
  Grid,
  Plus,
  Settings,
  LogOut,
  Lock,
  User as UserIcon,
  X,
  Save,
  Info,
  ExternalLink,
  ChevronRight,
  TrendingUp,
  BarChart3,
  DollarSign,
  Globe,

  Mail,
  MessageSquare,
  Lightbulb,
  Check,
  Clock,
  Bell,
  Send,
  Filter,
  ChevronDown,
  CloudLightning,
  Sparkles,
  Users,
  Image as ImageIcon,
  Menu,
  Crown,
  Shield,
  Edit3,
  Wand2
} from 'lucide-react';
import { translateTextAllLanguages, TARGET_LANGUAGES } from './utils/translator';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';
import type { StickerPack, Sticker, ContactMessage, StickerSuggestion, UserData, SubscriptionHistoryItem } from './types';
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { stickerProcessor } from './utils/stickerProcessor';

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

const CREATIVE_PREFIXES = [
  "Super", "Mega", "Ultra", "Best of", "Top", "The Real", "Just", "Simply", "Pure", "Daily",
  "Classic", "Modern", "Retro", "Vintage", "Neon", "Cyber", "Pixel", "Toon", "Chibi", "Kawaii",
  "Official", "Original", "Prime", "Elite", "Pro", "Master", "Ultimate", "Hyper", "Giga", "Turbo"
];

const CREATIVE_ADJECTIVES = [
  "Funny", "Cute", "Sad", "Happy", "Angry", "Crazy", "Sillly", "Weird", "Awkward", "Random",
  "Savage", "Dank", "Spicy", "Wholesome", "Cursed", "Blessed", "Based", "Toxic", "Dark", "Emo",
  "Lovely", "Sweet", "Soft", "Hard", "Loud", "Quiet", "Chill", "Cozy", "Comfy", "Lazy",
  "Hype", "Lit", "Fire", "Icy", "Cool", "Fresh", "Clean", "Messy", "Broken", "Fixed",
  "Golden", "Silver", "Diamond", "Rainbow", "Colorful", "Pastel", "Gothic", "Punk", "Metal", "Pop"
];

const CREATIVE_SUFFIXES = [
  "Pack", "Stickers", "Collection", "Edition", "Box", "Bundle", "Set", "Series", "Vol. 1", "Vol. 2",
  "Vibes", "Mood", "Moments", "Reactions", "Faces", "Expressions", "Emotions", "Feelings", "Thoughts", "Life",
  "Memes", "Jokes", "Humor", "Comedy", "Drama", "Action", "Style", "Art", "Design", "World",
  "Zone", "Club", "Squad", "Gang", "Crew", "Family", "Friends", "Lovers", "Haters", "Fans"
];

const CREATIVE_EMOJIS = [
  "🔥", "✨", "🎉", "🚀", "😂", "🤯", "😍", "🥰", "😎", "🤔", "🙄", "😴", "😭", "💀", "👻", "👽", "💩", "🤡", "👹", "😻",
  "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤",
  "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️",
  "💯", "💢", "💥", "💫", "💦", "💨", "🕳️", "💣", "💬", "👁️‍🗨️", "🗨️", "🗯️", "💭", "💤", "👋", "🤚", "🖐️", "✋", "🖖", "👌",
  "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸", "🪕", "🎻", "🎲", "♟️", "🎯", "🎳", "🎮", "🎰", "🧩", "🧸"
];

const generateCreativeName = (currentName: string) => {
  let cleanName = currentName || "";

  // Remove emojis and specific symbols
  cleanName = cleanName.replace(/[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu, '');

  // Remove known words to extract the core subject
  const allModifiers = [...CREATIVE_PREFIXES, ...CREATIVE_ADJECTIVES, ...CREATIVE_SUFFIXES];
  for (const word of allModifiers) {
    const regex = new RegExp(`\\b${word}\\b`, 'gi');
    cleanName = cleanName.replace(regex, "");
  }

  cleanName = cleanName.trim();
  if (!cleanName || cleanName.length < 2) cleanName = "Stickers";

  // Strategy Selection: 1=Prefix+Base, 2=Adj+Base, 3=Base+Suffix, 4=Adj+Base+Suffix
  const strategy = Math.floor(Math.random() * 4);
  const randomEmoji1 = CREATIVE_EMOJIS[Math.floor(Math.random() * CREATIVE_EMOJIS.length)];
  const randomEmoji2 = Math.random() > 0.5 ? CREATIVE_EMOJIS[Math.floor(Math.random() * CREATIVE_EMOJIS.length)] : "";

  let result = "";

  switch (strategy) {
    case 0: // Prefix + Base (e.g., "Captain Cats")
      const pref = CREATIVE_PREFIXES[Math.floor(Math.random() * CREATIVE_PREFIXES.length)];
      result = `${pref} ${cleanName}`;
      break;
    case 1: // Adj + Base (e.g., "Savage Cats")
      const adj = CREATIVE_ADJECTIVES[Math.floor(Math.random() * CREATIVE_ADJECTIVES.length)];
      result = `${adj} ${cleanName}`;
      break;
    case 2: // Base + Suffix (e.g., "Cats Empire")
      const suf = CREATIVE_SUFFIXES[Math.floor(Math.random() * CREATIVE_SUFFIXES.length)];
      result = `${cleanName} ${suf}`;
      break;
    case 3: // Adj + Base + Suffix (e.g., "Toxic Cats Squad") - Rare but cool
      const adj2 = CREATIVE_ADJECTIVES[Math.floor(Math.random() * CREATIVE_ADJECTIVES.length)];
      const suf2 = CREATIVE_SUFFIXES[Math.floor(Math.random() * CREATIVE_SUFFIXES.length)];
      result = `${adj2} ${cleanName} ${suf2}`;
      break;
  }

  return `${result} ${randomEmoji1}${randomEmoji2}`;
};

const CATEGORIES = [
  { id: 'humor', name: 'Mizah', emoji: '😂' },
  { id: 'love', name: 'Aşk', emoji: '❤️' },
  { id: 'religious', name: 'Dini', emoji: '🕌' },
  { id: 'entertainment', name: 'Eğlence', emoji: '🎉' },
  { id: 'background', name: 'Arka Plan', emoji: '🌅' },
  { id: 'morning', name: 'Günaydın', emoji: '☀️' },
  { id: 'night', name: 'İyi Geceler', emoji: '🌙' },
  { id: 'birthday', name: 'Doğum Günü', emoji: '🎂' },
  { id: 'congrats', name: 'Tebrikler', emoji: '👏' },
  { id: 'animals', name: 'Hayvanlar', emoji: '🐱' },
  { id: 'sports', name: 'Spor', emoji: '⚽' },
  { id: 'gaming', name: 'Oyun', emoji: '🎮' },
  { id: 'movie', name: 'Film & Dizi', emoji: '🎬' },
  { id: 'music', name: 'Müzik', emoji: '🎵' },
  { id: 'food', name: 'Yemek', emoji: '🍔' },
  { id: 'emoji', name: 'Emoji', emoji: '😊' },
  { id: 'cars', name: 'Araba', emoji: '🚗' },
  { id: 'motivation', name: 'Motivasyon', emoji: '⚡' },
  { id: 'cute', name: 'Sevimli', emoji: '🧸' },
  { id: 'text', name: 'Metin/Yazı', emoji: '✍️' },
  { id: 'anime', name: 'Anime', emoji: '⛩️' },
  { id: 'memes', name: 'Memes', emoji: '🎭' },
  { id: 'nature', name: 'Doğa', emoji: '🌿' },
  { id: 'other', name: 'Diğer', emoji: '📂' }
];

// ========== COMPONENTS ==========

const StatCard = ({ label, value, color }: { label: string, value: number, color: 'primary' | 'accent' }) => (
  <div className="bg-card px-5 py-3 rounded-2xl border border-white/5 flex flex-col min-w-[100px]">
    <span className="text-[10px] font-bold text-textSec uppercase tracking-widest mb-1">{label}</span>
    <span className={cn("text-2xl font-black", color === 'primary' ? 'text-primary' : 'text-accent')}>
      {(value || 0).toLocaleString()}
    </span>
  </div>
);

const Modal = ({ show, onClose, title, children }: { show: boolean, onClose: () => void, title: string, children: React.ReactNode }) => {
  if (!show) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-md animate-in fade-in duration-200" onClick={onClose}>
      <div
        onClick={(e) => e.stopPropagation()}
        className="glass w-full max-w-2xl rounded-3xl overflow-hidden animate-in zoom-in-95 duration-200 shadow-[0_0_100px_rgba(0,168,132,0.1)]"
      >
        <div className="px-8 py-6 border-b border-white/5 flex items-center justify-between bg-card/50">
          <h3 className="text-xl font-black tracking-tight text-white">{title}</h3>
          <button onClick={onClose} className="p-2 hover:bg-hover rounded-xl transition-all">
            <X size={20} className="text-textSec" />
          </button>
        </div>
        <div className="p-8 max-h-[80vh] overflow-y-auto custom-scrollbar bg-background/50">
          {children}
        </div>
      </div>
    </div>
  );
};

const Input = ({ label, value, onChange, placeholder, type = "text", helpText }: any) => (
  <div className="space-y-2 w-full">
    <label className="text-[10px] font-black text-textSec uppercase tracking-[0.2em] px-1">{label}</label>
    <div className="relative group">
      <input
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        className="w-full h-14 bg-hover border border-transparent focus:border-primary/50 rounded-xl px-5 text-white font-bold placeholder:text-textSec/30 outline-none transition-all"
      />
      <div className="absolute inset-0 rounded-xl bg-primary/5 opacity-0 group-focus-within:opacity-100 pointer-events-none transition-opacity" />
    </div>
    {helpText && <p className="text-[10px] text-textSec/60 font-medium px-1 italic">{helpText}</p>}
  </div>
);



function App() {
  const [user, setUser] = useState<User | null>(null);
  console.log("STICKY ADMIN V3 LOADING...");

  const [packs, setPacks] = useState<StickerPack[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedPack, setSelectedPack] = useState<StickerPack | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState<'dashboard' | 'stats' | 'messages' | 'notifications' | 'users'>('dashboard');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'passive' | 'premium' | 'normal' | 'animated' | 'static' | 'new'>('all');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [statsFilter, setStatsFilter] = useState<'all' | 'active' | 'passive' | 'premium' | 'normal'>('all');
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);
  const [showMobileMenu, setShowMobileMenu] = useState(false);

  // Mail System States
  const [messages, setMessages] = useState<ContactMessage[]>([]);
  const [suggestions, setSuggestions] = useState<StickerSuggestion[]>([]);
  const [messagesSubTab, setMessagesSubTab] = useState<'messages' | 'suggestions'>('messages');

  // Modals
  const [showNewPackModal, setShowNewPackModal] = useState(false);
  const [showEditPackModal, setShowEditPackModal] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [isTranslating, setIsTranslating] = useState(false);
  const [langSearch, setLangSearch] = useState('');

  // Form States
  const [newPackData, setNewPackData] = useState<any>({
    name: '',
    name_tr: '',
    name_zh: '',
    name_es: '',
    name_ar: '',
    name_hi: '',
    name_pt: '',
    publisher: '',
    publisher_email: '',
    privacy_policy_website: '',
    license_agreement_website: '',
    category: 'humor',
    is_premium: false,
    is_active: true,
    is_animated: true
  });
  const [editFormData, setEditFormData] = useState<Partial<StickerPack>>({});
  const [previewSticker, setPreviewSticker] = useState<{ url: string, title?: string } | null>(null);
  const [uploadProgress, setUploadProgress] = useState<{ current: number, total: number, message?: string } | null>(null);

  // Fake Download Base Range
  const [fakeBaseMin, setFakeBaseMin] = useState(3000);
  const [fakeBaseMax, setFakeBaseMax] = useState(10000);

  // Notification States
  const [notifTitle, setNotifTitle] = useState('Sticky');
  const [notifBody, setNotifBody] = useState('');
  const [notifImageUrl, setNotifImageUrl] = useState('');
  const [isSendingNotif, setIsSendingNotif] = useState(false);

  // Silme progress state
  const [deleteProgress, setDeleteProgress] = useState<{ deleting: boolean, message: string, current: number, total: number } | null>(null);

  // Users State
  const [usersData, setUsersData] = useState<UserData[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [userSearch, setUserSearch] = useState('');
  const [userFilter, setUserFilter] = useState<'all' | 'premium' | 'free' | 'subscription'>('all');
  const [selectedUser, setSelectedUser] = useState<UserData | null>(null);
  const [editingSubscription, setEditingSubscription] = useState(false);
  const [subPlan, setSubPlan] = useState('none');

  const [adminName, setAdminName] = useState('');
  const [showWelcome, setShowWelcome] = useState(false);
  const [welcomeExit, setWelcomeExit] = useState(false);
  const [showVideoBgModal, setShowVideoBgModal] = useState(false);
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (u) => {
      if (u && u.email) {
        setLoading(true);
        try {
          console.log("Admin kontrolü yapılıyor:", u.email);
          const adminDoc = await getDoc(doc(db, 'admins', u.email));
          if (!adminDoc.exists()) {
            console.warn("YETKESİZ GİRİŞ DENEMESİ:", u.email);
            await signOut(auth);
            setUser(null);
            alert("Yetkisiz Erişim: " + u.email + " yönetici listesinde bulunamadı.");
            setLoading(false);
            return;
          }

          const adminData = adminDoc.data();
          // Hem 'name' hem 'admin' alanına bak, yoksa e-posta ismini al
          const name = adminData?.name || adminData?.admin || u.email.split('@')[0];
          setAdminName(name);

          // Eğer yeni giriş yapılıyorsa hoş geldin ekranını göster
          if (!user && !showWelcome) {
            setShowWelcome(true);
            setWelcomeExit(false);
            // 2.9 saniyede fırlamaya başla, 3.5 saniyede bitir
            setTimeout(() => setWelcomeExit(true), 2900);
            setTimeout(() => {
              setShowWelcome(false);
              setLoading(false);
            }, 3500);
          } else {
            setLoading(false);
          }

          console.log("Admin girişi başarılı, isim:", name);
        } catch (error: any) {
          console.error("Admin yetkisi kontrol edilirken hata:", error);
          alert("Giriş Hatası: " + (error?.message || "Yetki kontrolü yapılamadı."));
          await signOut(auth);
          setUser(null);
          setLoading(false);
          return;
        }
      }
      setUser(u);
      if (u) fetchPacks();
      else setLoading(false);
    });
    return unsubscribe;
  }, []);

  useEffect(() => {
    if (activeTab !== 'messages') return;

    // Realtime listener for messages
    const unsubMessages = onSnapshot(collection(db, 'messages'), (snapshot) => {
      const msgs: ContactMessage[] = snapshot.docs.map(d => ({
        id: d.id,
        ...d.data()
      } as ContactMessage));
      setMessages(msgs.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0)));
    }, (e) => console.error("Mesajlar yüklenirken hata:", e));

    // Realtime listener for suggestions
    const unsubSuggestions = onSnapshot(collection(db, 'suggestions'), (snapshot) => {
      const suggs: StickerSuggestion[] = snapshot.docs.map(d => ({
        id: d.id,
        ...d.data()
      } as StickerSuggestion));
      setSuggestions(suggs.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0)));
    }, (e) => console.error("Öneriler yüklenirken hata:", e));

    return () => {
      unsubMessages();
      unsubSuggestions();
    };
  }, [activeTab]);

  const markMessageAsRead = async (messageId: string) => {
    try {
      await updateDoc(doc(db, 'messages', messageId), { status: 'read' });
      setMessages(messages.map(m => m.id === messageId ? { ...m, status: 'read' } : m));
    } catch (e) {
      console.error("Mesaj okundu işaretlenemedi:", e);
    }
  };

  const deleteMessage = async (messageId: string) => {
    if (!window.confirm("Bu mesajı silmek istediğinize emin misiniz?")) return;
    try {
      await deleteDoc(doc(db, 'messages', messageId));
      setMessages(messages.filter(m => m.id !== messageId));
    } catch (e) {
      console.error("Mesaj silinemedi:", e);
    }
  };

  const deleteSuggestion = async (suggestionId: string) => {
    if (!window.confirm("Bu öneriyi silmek istediğinize emin misiniz?")) return;
    try {
      await deleteDoc(doc(db, 'suggestions', suggestionId));
      setSuggestions(suggestions.filter(s => s.id !== suggestionId));
    } catch (e) {
      console.error("Öneri silinemedi:", e);
    }
  };

  const clearAllMessages = async () => {
    if (!window.confirm("TÜM mesajları silmek istediğinize emin misiniz?")) return;
    try {
      setDeleteProgress({ deleting: true, message: 'Mesajlar siliniyor...', current: 0, total: messages.length });
      const snapshot = await getDocs(collection(db, 'messages'));
      for (let i = 0; i < snapshot.docs.length; i++) {
        await deleteDoc(snapshot.docs[i].ref);
        setDeleteProgress({ deleting: true, message: 'Mesajlar siliniyor...', current: i + 1, total: snapshot.docs.length });
      }
      setDeleteProgress(null);
    } catch (e) {
      setDeleteProgress(null);
      console.error("Mesajlar silinemedi:", e);
    }
  };

  const clearAllSuggestions = async () => {
    if (!window.confirm("TÜM önerileri silmek istediğinize emin misiniz?")) return;
    try {
      setDeleteProgress({ deleting: true, message: 'Öneriler siliniyor...', current: 0, total: suggestions.length });
      const snapshot = await getDocs(collection(db, 'suggestions'));
      for (let i = 0; i < snapshot.docs.length; i++) {
        await deleteDoc(snapshot.docs[i].ref);
        setDeleteProgress({ deleting: true, message: 'Öneriler siliniyor...', current: i + 1, total: snapshot.docs.length });
      }
      setDeleteProgress(null);
    } catch (e) {
      setDeleteProgress(null);
      console.error("Öneriler silinemedi:", e);
    }
  };

  const handleSendNotification = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!notifBody) return alert("Lütfen bildirim mesajını girin.");
    if (!window.confirm("Bu bildirimi tüm kullanıcılara göndermek istediğinize emin misiniz?")) return;

    setIsSendingNotif(true);
    try {
      const notifRef = collection(db, 'notifications');
      await setDoc(doc(notifRef), {
        title: notifTitle || 'Sticky',
        body: notifBody,
        imageUrl: notifImageUrl || '',
        timestamp: serverTimestamp()
      });
      alert(" Bildirim kuyruğa alındı! Birkaç saniye içinde tüm cihazlara ulaşacak.");
      setNotifBody('');
      setNotifImageUrl('');
    } catch (e: any) {
      console.error("Bildirim gönderme hatası:", e);
      alert("Hata: " + e.message);
    } finally {
      setIsSendingNotif(false);
    }
  };



  const handleGoogleLogin = async () => {
    setLoading(true);
    try {
      const provider = new GoogleAuthProvider();
      await signInWithPopup(auth, provider);
    } catch (error: any) {
      console.error("Google login error:", error);
      alert("Google ile giriş yapılamadı: " + error.message);
    } finally {
      setLoading(false);
    }
  };

  const fetchPacks = async () => {
    setLoading(true);
    try {
      const normalPacks = await getDocs(collection(db, 'stickers'));
      const premiumPacks = await getDocs(collection(db, 'premium_stickers'));

      const allPacks: StickerPack[] = [
        ...normalPacks.docs.map(d => {
          const data = d.data();
          const p = {
            id: d.id,
            ...data,
            is_premium: false,
            is_animated: data.is_animated ?? data.animated ?? false,
            download_count: Number(data.download_count || data.downloadCount || data.downloads || 0),
            fake_download_base: Number(data.fake_download_base || 0),
            view_count: Number(data.view_count || data.viewCount || data.views || 0),
            favorite_count: Number(data.favorite_count || data.favoriteCount || data.favorites || 0),
            sticker_count: Number(data.sticker_count || data.stickers?.length || 0),

          } as StickerPack;
          return p;
        }),
        ...premiumPacks.docs.map(d => {
          const data = d.data();
          const p = {
            id: d.id,
            ...data,
            is_premium: true,
            is_animated: data.is_animated ?? data.animated ?? false,
            download_count: Number(data.download_count || data.downloadCount || data.downloads || 0),
            fake_download_base: Number(data.fake_download_base || 0),
            view_count: Number(data.view_count || data.viewCount || data.views || 0),
            favorite_count: Number(data.favorite_count || data.favoriteCount || data.favorites || 0),
            sticker_count: Number(data.sticker_count || data.stickers?.length || 0),

          } as StickerPack;
          return p;
        })
      ];

      // Fallback: Eğer hiç paket bulunamadıysa eski koleksiyonu (sticker_packs) kontrol et
      if (allPacks.length === 0) {
        console.warn("Standart koleksiyonlar boş, 'sticker_packs' kontrol ediliyor...");
        try {
          const oldPacks = await getDocs(collection(db, 'sticker_packs'));
          const oldPacksData = oldPacks.docs.map(d => {
            const data = d.data();
            return {
              id: d.id,
              ...data,
              is_premium: false,
              is_animated: data.is_animated ?? data.animated ?? false,
              download_count: Number(data.download_count || data.downloadCount || data.downloads || 0),
              fake_download_base: Number(data.fake_download_base || 0),
              view_count: Number(data.view_count || data.viewCount || data.views || 0),
              favorite_count: Number(data.favorite_count || data.favoriteCount || data.favorites || 0),
              sticker_count: Number(data.sticker_count || data.stickers?.length || 0),
            } as StickerPack;
          });
          allPacks.push(...oldPacksData);
        } catch (e) {
          console.warn("Eski koleksiyon (sticker_packs) okunurken hata:", e);
        }
      }

      console.log("FETCHED PACKS DATA:");
      console.table(allPacks.map(p => ({ name: p.name, dl: p.download_count, views: p.view_count })));
      // Güvenli sıralama (name undefined olabilir)
      setPacks(allPacks.sort((a, b) => (a.name || '').localeCompare(b.name || '')));
    } catch (error: any) {
      console.error("Fetch error:", error);
      alert("Firebase Veri Çekme Hatası: " + (error?.message || "Bilinmeyen hata"));
    } finally {
      setLoading(false);
    }
  };

  // ========== KULLANICI YÖNETİM FONKSİYONLARI ==========

  const fetchUsers = async () => {
    setUsersLoading(true);
    try {
      const snapshot = await getDocs(collection(db, 'users'));
      const usersList: UserData[] = snapshot.docs.map(d => ({
        id: d.id,
        email: d.data().email || '',
        is_premium: d.data().is_premium || false,
        premium_type: d.data().premium_type || 'none',
        premium_expiry: d.data().premium_expiry || 0,
        favorite_packs: d.data().favorite_packs || [],
        last_sync: d.data().last_sync || null,
        cancelled_at: d.data().cancelled_at || null,
        cancelled_reason: d.data().cancelled_reason || '',
      }));
      setUsersData(usersList);
    } catch (error) {
      console.error("Users fetch error:", error);
    } finally {
      setUsersLoading(false);
    }
  };

  const filteredUsers = useMemo(() => {
    let filtered = usersData;
    if (userSearch) {
      const q = userSearch.toLowerCase();
      filtered = filtered.filter(u => u.email.toLowerCase().includes(q));
    }
    if (userFilter === 'premium') filtered = filtered.filter(u => u.is_premium);
    else if (userFilter === 'free') filtered = filtered.filter(u => !u.is_premium);
    else if (userFilter === 'subscription') filtered = filtered.filter(u => u.premium_type === 'subscription');

    return filtered;
  }, [usersData, userSearch, userFilter]);

  const userStats = useMemo(() => {
    const total = usersData.length;
    const premium = usersData.filter(u => u.is_premium).length;
    const subscription = usersData.filter(u => u.premium_type === 'subscription').length;
    const free = total - premium;
    return { total, premium, subscription, free };
  }, [usersData]);

  const handleUpdateSubscription = async (userId: string, plan: string) => {
    try {
      const user = usersData.find(u => u.id === userId);
      // Prevent changing Google Play subscriptions via Admin
      if (user?.subscription_source === 'google_play' && plan !== 'none') {
        alert("Google Play abonelikleri admin panelinden değiştirilemez.");
        return;
      }

      let isPremium = false;
      let type = 'none';
      let expiry = 0;
      let details = '';

      if (plan === 'monthly') {
        isPremium = true;
        type = 'subscription';
        const d = new Date();
        d.setMonth(d.getMonth() + 1);
        expiry = d.getTime();
        details = 'Admin tarafindan 1 Aylik eklendi';
      } else if (plan === 'yearly') {
        isPremium = true;
        type = 'subscription';
        const d = new Date();
        d.setFullYear(d.getFullYear() + 1);
        expiry = d.getTime();
        details = 'Admin tarafindan 1 Yillik eklendi';
      }

      // History item
      const historyItem: SubscriptionHistoryItem = {
        id: crypto.randomUUID(),
        type: isPremium ? 'start' : 'cancel',
        plan: plan === 'none' ? 'none' : (plan as 'monthly' | 'yearly'),
        source: 'admin',
        timestamp: Date.now(),
        date_str: new Date().toLocaleDateString('tr-TR'),
        details: details
      };

      const updateData: any = {
        is_premium: isPremium,
        premium_type: type,
        premium_expiry: expiry,
        last_sync: serverTimestamp(),
        subscription_source: isPremium ? 'admin' : 'none',
        subscription_history: arrayUnion(historyItem)
      };

      await updateDoc(doc(db, 'users', userId), updateData);

      // Optimistic Update
      const updatedUser: UserData = {
        ...user!,
        is_premium: isPremium,
        premium_type: type,
        premium_expiry: expiry,
        subscription_source: (isPremium ? 'admin' : 'none') as 'admin' | 'none',
        subscription_history: [...(user?.subscription_history || []), historyItem]
      };

      setUsersData(prev => prev.map(u => u.id === userId ? updatedUser : u));
      setEditingSubscription(false);
      setSelectedUser(updatedUser);
    } catch (error) {
      console.error("Update subscription error:", error);
      alert("Abonelik güncellenirken hata oluştu.");
    }
  };

  const handleRevokeSubscription = async (userId: string) => {
    const user = usersData.find(u => u.id === userId);
    if (!user) return;

    if (user.subscription_source === 'google_play') {
      alert("Google Play üzerinden alınan abonelikler buradan iptal edilemez. Kullanıcının Play Store üzerinden iptal etmesi gerekir.");
      return;
    }

    if (!window.confirm("Bu kullanıcının aboneliğini iptal etmek istediğinize emin misiniz?")) return;

    try {
      const historyItem: SubscriptionHistoryItem = {
        id: crypto.randomUUID(),
        type: 'cancel',
        plan: 'none',
        source: 'admin',
        timestamp: Date.now(),
        date_str: new Date().toLocaleDateString('tr-TR'),
        details: 'Admin tarafindan iptal edildi'
      };

      await updateDoc(doc(db, 'users', userId), {
        is_premium: false,
        premium_type: 'none',
        premium_expiry: 0,
        last_sync: serverTimestamp(),
        subscription_source: 'none',
        subscription_history: arrayUnion(historyItem)
      });

      const updatedUser: UserData = {
        ...user,
        is_premium: false,
        premium_type: 'none',
        premium_expiry: 0,
        subscription_source: 'none',
        subscription_history: [...(user.subscription_history || []), historyItem]
      };

      setUsersData(prev => prev.map(u => u.id === userId ? updatedUser : u));
      setSelectedUser(updatedUser);
    } catch (error) {
      console.error("Revoke subscription error:", error);
      alert("Abonelik iptal edilirken hata oluştu.");
    }
  };

  // Users sekmesine geçince otomatik yükle
  useEffect(() => {
    if (activeTab === 'users' && usersData.length === 0) {
      fetchUsers();
    }
  }, [activeTab]);

  // ========== KULLANICI YÖNETİM FONKSİYONLARI SON ==========

  const handleCreatePack = async () => {
    if (!newPackData.name || !newPackData.publisher) return alert("Lütfen isim ve yayıncı alanlarını doldurun.");
    setIsProcessing(true);
    try {
      const packId = newPackData.name.toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, '');
      const collectionName = newPackData.is_premium ? 'premium_stickers' : 'stickers';

      const packData: any = {
        name: newPackData.name,
        publisher: newPackData.publisher,
        publisher_email: "contact@sticly.com",
        category: newPackData.category,
        is_premium: newPackData.is_premium,
        is_animated: true,
        download_count: 0,
        fake_download_base: Math.floor(Math.random() * 7001) + 3000,
        view_count: 0,
        favorite_count: 0,
        sticker_count: 0,
        image_data_version: "1",
        is_active: newPackData.is_active,
        ...(newPackData.is_premium ? { price_try: "4,99 TL", price_usd: "$0.99", price_eur: "€0.99" } : {}),
        stickers: [],
        tray_url: "",
        created_at: serverTimestamp()
      };

      // Tüm name_ ile başlayan alanları kopyala (Çeviriler)
      Object.keys(newPackData).forEach(key => {
        if (key.startsWith('name_')) {
          packData[key] = (newPackData as any)[key] || '';
        }
      });

      await setDoc(doc(db, collectionName, packId), packData);

      const createdPack = { id: packId, ...packData } as StickerPack;
      setPacks([createdPack, ...packs]);
      setSelectedPack(createdPack);
      setShowNewPackModal(false);
      setNewPackData({
        name: '',
        name_tr: '',
        name_zh: '',
        name_es: '',
        name_ar: '',
        name_hi: '',
        name_pt: '',
        publisher: '',
        publisher_email: '',
        privacy_policy_website: '',
        license_agreement_website: '',
        category: 'humor',
        is_premium: false,
        is_active: true,
        is_animated: true,

      });
      alert("Yeni hareketli paket oluşturuldu. Şimdi video/gif ekleyebilirsiniz.");
    } catch (e) {
      alert("Hata: " + e);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleAutoTranslate = async (isEdit: boolean) => {
    const textToTranslate = isEdit ? editFormData.name : newPackData.name;
    if (!textToTranslate) {
      alert("Lütfen önce bir ana isim (İngilizce) girin.");
      return;
    }

    setIsTranslating(true);
    try {
      const translations = await translateTextAllLanguages(textToTranslate);
      if (isEdit) {
        setEditFormData((prev: any) => ({ ...prev, ...translations }));
      } else {
        setNewPackData((prev: any) => ({ ...prev, ...translations }));
      }
      alert("✅ Gemini tüm dilleri başarıyla çevirdi!");
    } catch (error) {
      console.error("Gemini Error:", error);
      alert("⚠️ Çeviri sırasında bir hata oluştu. Lütfen tekrar deneyin.");
    } finally {
      setIsTranslating(false);
    }
  };

  const handleUpdatePack = async () => {
    if (!selectedPack || !editFormData) return;
    setIsProcessing(true);

    try {
      const updatedData: any = { ...editFormData };
      updatedData.image_data_version = Date.now().toString();

      // Premium'a çevriliyorsa ve fiyat yoksa default fiyat ata
      if (updatedData.is_premium && !selectedPack.is_premium) {
        if (!updatedData.price_try) updatedData.price_try = "69,99 TL";
        if (!updatedData.price_usd) updatedData.price_usd = "$4.99";
        if (!updatedData.price_eur) updatedData.price_eur = "€4.49";
      }

      const oldCollection = selectedPack.is_premium ? 'premium_stickers' : 'stickers';
      const newCollection = updatedData.is_premium ? 'premium_stickers' : 'stickers';

      if (oldCollection !== newCollection) {
        // Koleksiyonlar arası geçiş - sadece database taşıma
        console.log(`[UPDATE] ${oldCollection} -> ${newCollection} için ${selectedPack.id} paketi taşınıyor...`);

        const fullData = {
          ...selectedPack,
          ...updatedData
        };

        // Yeni koleksiyona ekle
        await setDoc(doc(db, newCollection, selectedPack.id), fullData);
        console.log(`[UPDATE] Yeni döküman oluşturuldu: ${newCollection}/${selectedPack.id}`);

        // Eski koleksiyondan sil
        await deleteDoc(doc(db, oldCollection, selectedPack.id));
        console.log(`[UPDATE] Eski döküman silindi: ${oldCollection}/${selectedPack.id}`);

        const updated = fullData as StickerPack;
        setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
        setSelectedPack(updated);
        setShowEditPackModal(false);

        alert(`Paket başarıyla ${updatedData.is_premium ? 'Premium' : 'Normal'} olarak güncellendi.`);
      } else {
        // Sadece bilgi güncelleme (tip değişikliği yok)
        await updateDoc(doc(db, oldCollection, selectedPack.id), updatedData);
        const updated = { ...selectedPack, ...updatedData } as StickerPack;
        setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
        setSelectedPack(updated);
        setShowEditPackModal(false);
        alert("Paket bilgileri başarıyla güncellendi.");
      }

    } catch (e: any) {
      console.error('[UPDATE] Hata:', e);
      alert("Hata: " + e.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const uploadStickersBatch = async (files: File[], removeBgForVideos: boolean = false) => {
    if (!selectedPack) return;

    const uploadCount = files.length;
    setIsProcessing(true);
    setUploadProgress({ current: 0, total: uploadCount, message: 'İşlem başlıyor...' });

    try {
      const collectionName = selectedPack.is_premium ? 'premium_stickers' : 'stickers';
      const packRef = doc(db, collectionName, selectedPack.id);
      const newStickers: Sticker[] = [];
      const processedBlobs: Blob[] = [];

      for (let i = 0; i < uploadCount; i++) {
        const file = files[i];
        const isAnimatedFile = file.type.includes('video') || file.type.includes('gif');


        // Karışık paket kontrolü (WhatsApp kısıtlaması)
        if (selectedPack.is_animated && !isAnimatedFile && !file.name.endsWith('.webp')) {
          alert(`Hata: Bu paket hareketli bir pakettir. "${file.name}" gibi statik görseller eklenemez.`);
          continue;
        }
        if (!selectedPack.is_animated && isAnimatedFile) {
          alert(`Hata: Bu paket statik bir pakettir. "${file.name}" gibi hareketli dosyalar eklenemez.`);
          continue;
        }

        setUploadProgress({
          current: i + 1,
          total: uploadCount,
          message: `${file.name} işleniyor...`
        });

        let processedBlob: Blob;
        const isAnimatedPack = selectedPack.is_animated ?? false;
        const isWebP = file.type === 'image/webp' || file.name.toLowerCase().endsWith('.webp');

        try {
          // WebP dosyalarını da işle (WhatsApp koşullarına uygun hale getir)
          if (isWebP) {
            // Animasyonlu WebP mi kontrol et
            const isAnimatedWebP = await stickerProcessor.isAnimatedWebP(file);

            if (isAnimatedPack && isAnimatedWebP) {
              // Animasyonlu paket + Animasyonlu WebP
              processedBlob = await stickerProcessor.processAnimatedWebP(file, (p) => {
                setUploadProgress(prev => prev ? { ...prev, message: `${file.name}: ${p.message}` } : null);
              });
            } else if (isAnimatedPack && !isAnimatedWebP) {
              // Animasyonlu pakete statik WebP eklenemez
              alert(`Hata: Bu paket hareketli bir pakettir. "${file.name}" statik bir WebP dosyasıdır.`);
              continue;
            } else if (!isAnimatedPack && isAnimatedWebP) {
              // Statik pakete animasyonlu WebP eklenemez
              alert(`Hata: Bu paket statik bir pakettir. "${file.name}" animasyonlu bir WebP dosyasıdır.`);
              continue;
            } else {
              // Statik paket + Statik WebP
              processedBlob = await stickerProcessor.processStaticWebP(file, (p) => {
                setUploadProgress(prev => prev ? { ...prev, message: `${file.name}: ${p.message}` } : null);
              });
            }
          } else if (isAnimatedPack) {
            // Video için removeBgForVideos parametresini geçir
            processedBlob = await stickerProcessor.processAnimated(file, (p) => {
              setUploadProgress(prev => prev ? { ...prev, message: `${file.name}: ${p.message}` } : null);
            }, removeBgForVideos);
          } else {
            processedBlob = await stickerProcessor.processStatic(file, (p) => {
              setUploadProgress(prev => prev ? { ...prev, message: `${file.name}: ${p.message}` } : null);
            });
          }
        } catch (processingError: any) {
          // Dosya işleme hatası - kullanıcıya bildir ve bu dosyayı atla
          console.error(`Dosya işleme hatası (${file.name}):`, processingError);
          alert(`Hata: "${file.name}" işlenemedi.\n\n${processingError.message || 'Bilinmeyen hata'}`);
          continue;
        }

        processedBlobs.push(processedBlob);

        const fileName = `${Date.now()}_${i}.webp`;
        const storagePath = `stickers/${selectedPack.id}/${fileName}`;
        const storageRef = ref(storage, storagePath);

        await uploadBytes(storageRef, processedBlob);
        const url = await getDownloadURL(storageRef);

        newStickers.push({
          image_file: fileName,
          url: url,
          emojis: ["😀"]  // WhatsApp requires at least one valid emoji
        });
      }

      if (newStickers.length === 0) {
        setIsProcessing(false);
        setUploadProgress(null);
        return;
      }

      // AUTO TRAY: Randomly select one from the new batch to be the tray
      let newTrayUrl = selectedPack.tray_url;
      let newTrayFile = selectedPack.tray_image_file;

      if (processedBlobs.length > 0) {
        setUploadProgress({ current: uploadCount, total: uploadCount, message: 'Kapak resmi otomatik seçiliyor...' });

        try {
          // Eski kapak resmini sil (varsa)
          if (selectedPack.tray_image_file) {
            const oldTrayPath = `stickers/${selectedPack.id}/${selectedPack.tray_image_file}`;
            try { await deleteObject(ref(storage, oldTrayPath)); } catch (e) { console.warn("Old tray delete fail", e); }
          }

          // Random seçim
          const randomIdx = Math.floor(Math.random() * processedBlobs.length);
          const chosenBlob = processedBlobs[randomIdx];

          // Blob -> File dönüşümü (stickerProcessor.processTray için)
          const tempFile = new File([chosenBlob], "auto_tray.webp", { type: 'image/webp' });

          const trayProcessedBlob = await stickerProcessor.processTray(tempFile, (p) => {
            setUploadProgress(prev => prev ? { ...prev, message: `Kapak: ${p.message}` } : null);
          });

          const trayFileName = `tray_${Date.now()}.png`;
          const trayStorageRef = ref(storage, `stickers/${selectedPack.id}/${trayFileName}`);

          await uploadBytes(trayStorageRef, trayProcessedBlob);
          newTrayUrl = await getDownloadURL(trayStorageRef);
          newTrayFile = trayFileName;

        } catch (err) {
          console.error("Auto tray failed:", err);
          // Hata olsa bile stickerlar eklendi, devam et
        }
      }

      const newVersion = Date.now().toString();
      const updatedData = {
        stickers: [...(selectedPack.stickers || []), ...newStickers],
        sticker_count: Math.max(0, (selectedPack.sticker_count || 0) + newStickers.length),
        image_data_version: newVersion,
        tray_url: newTrayUrl,
        tray_image_file: newTrayFile
      };

      await updateDoc(packRef, updatedData);

      const updated = {
        ...selectedPack,
        ...updatedData
      };

      setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
      setSelectedPack(updated);
      alert(`${newStickers.length} sticker başarıyla işlendi ve eklendi. Kapak resmi güncellendi.`);
    } catch (error: any) {
      console.error(error);
      alert("Yükleme hatası: " + error.message);
    } finally {
      setIsProcessing(false);
      setUploadProgress(null);
    }
  };

  const handleVideoProcessingChoice = (removeBg: boolean) => {
    setShowVideoBgModal(false);
    if (pendingFiles.length > 0) {
      uploadStickersBatch(pendingFiles, removeBg);
      setPendingFiles([]);
    }
  };

  const handleAddSticker = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const filesList = e.target.files;
    if (!filesList || !selectedPack) return;

    const currentCount = selectedPack.sticker_count || 0;
    const uploadCount = filesList.length;
    const totalCount = currentCount + uploadCount;

    // WhatsApp Paket Standartları Kontrolü (Min 3, Max 30 Toplam)
    if (totalCount > 30) {
      alert(`Hata: Bir pakette en fazla 30 sticker olabilir. (Mevcut: ${currentCount}, Yeni: ${uploadCount}, Toplam: ${totalCount})`);
      e.target.value = '';
      return;
    }

    if (totalCount < 3) {
      alert(`Hata: Bir pakette en az 3 sticker olmalıdır. (Mevcut: ${currentCount}, Yeni: ${uploadCount}, Toplam: ${totalCount}). En az ${3 - currentCount} adet daha eklemelisiniz.`);
      e.target.value = '';
      return;
    }

    const files = Array.from(filesList);

    // Video (MP4) veya GIF kontrolü
    const hasAnimatedWithPrompt = files.some(f =>
      f.type.startsWith('video/mp4') || f.name.endsWith('.mp4') ||
      f.type === 'image/gif' || f.name.endsWith('.gif')
    );

    if (hasAnimatedWithPrompt) {
      setPendingFiles(files);
      setShowVideoBgModal(true);
      e.target.value = '';
      return;
    }

    await uploadStickersBatch(files, false);
    e.target.value = '';
  };



  const deletePack = async (pack: StickerPack) => {
    if (!window.confirm(`"${pack.name}" paketini TAMAMEN silmek istediğinize emin misiniz?\n\nBu işlem geri alınamaz ve tüm dosyalar silinecek!`)) return;

    try {
      setDeleteProgress({ deleting: true, message: 'Dosyalar listeleniyor...', current: 0, total: 0 });

      const folderRef = ref(storage, `stickers/${pack.id}`);

      // 1. Storage klasöründeki TÜM dosyaları listele ve sil
      try {
        const fileList = await listAll(folderRef);
        const total = fileList.items.length;
        setDeleteProgress({ deleting: true, message: `Storage'dan siliniyor...`, current: 0, total });

        for (let i = 0; i < fileList.items.length; i++) {
          await deleteObject(fileList.items[i]);
          setDeleteProgress({ deleting: true, message: `Storage'dan siliniyor...`, current: i + 1, total });
        }
      } catch (e) { console.log('Storage silme hatası:', e); }

      // 2. Firestore dokümanını sil
      setDeleteProgress({ deleting: true, message: 'Veritabanından siliniyor...', current: 0, total: 1 });
      const collectionName = pack.is_premium ? 'premium_stickers' : 'stickers';
      await deleteDoc(doc(db, collectionName, pack.id));

      setPacks(packs.filter(p => p.id !== pack.id));
      if (selectedPack?.id === pack.id) setSelectedPack(null);
      setDeleteProgress(null);
    } catch (error) {
      setDeleteProgress(null);
      alert("Silme hatası: " + error);
    }
  };

  const deleteSticker = async (pack: StickerPack, sticker: Sticker) => {
    if (!window.confirm("Bu çıkartmayı silmek istediğinize emin misiniz?")) return;

    try {
      const collectionName = pack.is_premium ? 'premium_stickers' : 'stickers';
      const packRef = doc(db, collectionName, pack.id);

      const newVersion = Date.now().toString();
      await updateDoc(packRef, {
        stickers: arrayRemove(sticker),
        image_data_version: newVersion
      });

      // Storage'dan sil (tek klasör: stickers)
      const storagePath = `stickers/${pack.id}/${sticker.image_file}`;
      console.log('[DELETE] Storage path:', storagePath);
      try {
        await deleteObject(ref(storage, storagePath));
        console.log('[DELETE] ✅ Storage dosyası silindi:', storagePath);
      } catch (storageErr: any) {
        console.error('[DELETE] ❌ Storage silme hatası:', storageErr.code, storageErr.message);
      }

      const updatedPack = {
        ...pack,
        stickers: pack.stickers.filter(s => s.image_file !== sticker.image_file),
        sticker_count: Math.max(0, pack.sticker_count - 1),
        image_data_version: newVersion
      };

      setPacks(packs.map(p => p.id === pack.id ? updatedPack : p));
      setSelectedPack(updatedPack);
    } catch (error) {
      alert("Çıkartma silme hatası: " + error);
    }
  };

  const resetStats = async (pack: StickerPack) => {
    if (!window.confirm("İstatistikleri sıfırlamak istiyor musunuz?")) return;
    try {
      const collectionName = pack.is_premium ? 'premium_stickers' : 'stickers';
      await updateDoc(doc(db, collectionName, pack.id), {
        download_count: 0,
        view_count: 0
      });
      const updated = { ...pack, download_count: 0, view_count: 0 };
      setPacks(packs.map(p => p.id === pack.id ? updated : p));
      setSelectedPack(updated);
      alert("İstatistikler sıfırlandı.");
    } catch (e) { alert("Hata: " + e); }
  };

  // Tüm paketleri fake_download_base ve premium fiyatlarıyla güncelle
  const updateAllPacksWithFakeBase = async (forceUpdate: boolean = false) => {
    const range = fakeBaseMax - fakeBaseMin;
    if (range <= 0) {
      alert("Geçersiz aralık! Max değer Min'den büyük olmalı.");
      return;
    }
    if (!window.confirm(`Tüm paketlere fake download base (${fakeBaseMin.toLocaleString()} - ${fakeBaseMax.toLocaleString()}) ${forceUpdate ? 'ZORLA ' : ''}eklenecek ve premium paket fiyatları güncellenecek. Devam?`)) return;
    setIsProcessing(true);
    try {
      const collections = ['stickers', 'premium_stickers'];
      let updated = 0;

      for (const collectionName of collections) {
        const snapshot = await getDocs(collection(db, collectionName));
        for (const docSnap of snapshot.docs) {
          const data = docSnap.data();
          const updates: any = {};

          // fake_download_base yoksa, 0 ise veya forceUpdate ise ekle
          if (forceUpdate || !data.fake_download_base || data.fake_download_base === 0) {
            updates.fake_download_base = Math.floor(Math.random() * (range + 1)) + fakeBaseMin;
          }

          // Premium paketlere doğru fiyatları yaz
          if (collectionName === 'premium_stickers') {
            updates.price_try = '4,99 TL';
            updates.price_usd = '$0.99';
            updates.price_eur = '€0.99';
          }

          if (Object.keys(updates).length > 0) {
            await updateDoc(doc(db, collectionName, docSnap.id), updates);
            updated++;
          }
        }
      }

      alert(`${updated} paket güncellendi! Listeyi yenilemek için bekleyin...`);
      await fetchPacks();
    } catch (e) {
      alert("Hata: " + e);
    } finally {
      setIsProcessing(false);
    }
  };

  if (showWelcome) {
    return (
      <div className={cn(
        "fixed inset-0 z-[9999] bg-[#08090A] flex flex-col items-center justify-center transition-all duration-700 ease-out-expo",
        welcomeExit ? "scale-[2] blur-3xl opacity-0 -translate-y-full" : "scale-100 opacity-100"
      )}>
        {/* Techy Grid Background */}
        <div className="absolute inset-0 opacity-[0.03] pointer-events-none"
          style={{ backgroundImage: 'radial-gradient(#10B981 1px, transparent 0)', backgroundSize: '40px 40px' }} />

        {/* Scanning Line */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="w-full h-[2px] bg-gradient-to-r from-transparent via-primary to-transparent animate-scan shadow-[0_0_20px_#10B981]" />
        </div>

        <div className="relative z-10 text-center space-y-12 max-w-4xl px-4">
          {/* Hexagon/Circle Container */}
          <div className="relative mx-auto w-32 h-32 animate-in zoom-in duration-700">
            <div className="absolute inset-0 bg-primary/20 rounded-3xl rotate-12 animate-pulse" />
            <div className="absolute inset-0 bg-primary/20 rounded-3xl -rotate-12 animate-pulse delay-75" />
            <div className="relative bg-[#0F1112] border-2 border-primary/50 w-full h-full rounded-3xl flex items-center justify-center shadow-[0_0_50px_rgba(16,185,129,0.3)]">
              <Sparkles className="text-primary animate-bounce-subtle" size={56} />
            </div>
            {/* Spinning Rings */}
            <div className="absolute -inset-4 border border-primary/10 rounded-full border-t-primary/40 animate-spin-slow" />
            <div className="absolute -inset-8 border border-primary/5 rounded-full border-b-primary/20 animate-reverse-spin" />
          </div>

          <div className="space-y-6">
            <div className="inline-flex items-center gap-2 px-4 py-1 rounded-full bg-primary/10 border border-primary/20 text-primary text-xs font-mono tracking-widest animate-in fade-in slide-in-from-top-4 duration-500">
              <CloudLightning size={14} className="animate-pulse" />
              ERİŞİM ONAYLANDI • SİSTEM GÜVENLİ
            </div>

            <h1 className="text-7xl font-black tracking-tight leading-none italic animate-in slide-in-from-bottom-8 duration-700">
              <span className="block text-textSec text-2xl font-mono uppercase tracking-[0.5em] mb-4 opacity-40">HOŞ GELDİN</span>
              <span className="text-transparent bg-clip-text bg-gradient-to-br from-white via-primary to-emerald-500 drop-shadow-[0_0_30px_rgba(16,185,129,0.3)]">
                {adminName.toLowerCase()}
              </span>
            </h1>

            <div className="font-mono text-primary/40 text-sm tracking-widest flex justify-center gap-8 pt-4">
              <div className="flex flex-col gap-1">
                <span className="text-[10px] uppercase opacity-50">KİMLİK</span>
                <span className="text-white/80">DOĞRULANDI</span>
              </div>
              <div className="w-px h-8 bg-white/10" />
              <div className="flex flex-col gap-1">
                <span className="text-[10px] uppercase opacity-50">PROTOKOL</span>
                <span className="text-white/80">ŞİFRELENDİ</span>
              </div>
              <div className="w-px h-8 bg-white/10" />
              <div className="flex flex-col gap-1">
                <span className="text-[10px] uppercase opacity-50">DURUM</span>
                <span className="text-primary font-bold">AKTİF</span>
              </div>
            </div>
          </div>
        </div>

        {/* CSS for custom animations */}
        <style dangerouslySetInnerHTML={{
          __html: `
          @keyframes scan {
            0% { transform: translateY(-100%); opacity: 0; }
            50% { opacity: 1; }
            100% { transform: translateY(100vh); opacity: 0; }
          }
          .animate-scan { animation: scan 3s linear infinite; }
          .animate-spin-slow { animation: spin 8s linear infinite; }
          .animate-reverse-spin { animation: spin 12s linear reverse infinite; }
          .animate-bounce-subtle { animation: bounceSubtle 2s ease-in-out infinite; }
          @keyframes bounceSubtle {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-8px); }
          }
          .ease-out-expo { transition-timing-function: cubic-bezier(0.19, 1, 0.22, 1); }
        ` }} />
      </div>
    );
  }

  if (loading && !user) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <RefreshCcw className="text-primary animate-spin" size={40} />
      </div>
    );
  }

  if (!user) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="glass w-full max-w-md p-8 rounded-3xl space-y-8 animate-in fade-in zoom-in duration-300">
          <div className="text-center space-y-2">
            <div className="bg-primary w-16 h-16 rounded-2xl flex items-center justify-center mx-auto shadow-lg shadow-primary/20">
              <Lock className="text-white" size={32} />
            </div>
            <h1 className="text-2xl font-bold">Sticky Admin Girişi</h1>
            <p className="text-textSec text-sm">Yönetim paneline erişmek için giriş yapın</p>
          </div>


          <button
            onClick={handleGoogleLogin}
            disabled={loading}
            className="w-full bg-white hover:bg-gray-100 py-3 rounded-xl font-bold text-black transition-all flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
              <path
                fill="currentColor"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="currentColor"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="currentColor"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z"
              />
              <path
                fill="currentColor"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.66l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 12-4.53z"
              />
            </svg>
            Google ile Giriş Yap
          </button>
        </div>
      </div>
    );
  }

  const isNew = (pack: StickerPack) => {
    if (!pack.created_at) return false;
    try {
      const created = pack.created_at.toDate ? pack.created_at.toDate() : new Date(pack.created_at);
      const diff = Date.now() - created.getTime();
      return diff < 7 * 24 * 60 * 60 * 1000;
    } catch (e) {
      return false;
    }
  };

  const filteredPacks = packs.filter(p => {
    const matchesSearch = p.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.name_tr?.toLowerCase().includes(searchTerm.toLowerCase());

    if (!matchesSearch) return false;

    // Kategori Filtresi
    if (categoryFilter !== 'all' && p.category !== categoryFilter) return false;

    if (statusFilter === 'all') return true;
    if (statusFilter === 'active') return p.is_active !== false;
    if (statusFilter === 'passive') return p.is_active === false;
    if (statusFilter === 'premium') return p.is_premium === true;
    if (statusFilter === 'normal') return p.is_premium === false;
    if (statusFilter === 'animated') return p.is_animated === true;
    if (statusFilter === 'static') return p.is_animated !== true;
    if (statusFilter === 'new') return isNew(p);
    return true;
  });

  return (
    <div className="min-h-screen bg-background text-textMain flex flex-col font-sans overflow-x-hidden">
      {/* Delete Progress Overlay */}
      {deleteProgress && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center">
          <div className="bg-card rounded-3xl p-8 w-96 shadow-2xl">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 bg-danger/20 rounded-xl flex items-center justify-center">
                <Trash2 className="text-danger animate-pulse" size={20} />
              </div>
              <h3 className="text-xl font-bold text-white">Siliniyor...</h3>
            </div>
            <p className="text-textSec mb-4">{deleteProgress.message}</p>
            {deleteProgress.total > 0 && (
              <>
                <div className="w-full bg-hover rounded-full h-3 mb-2 overflow-hidden">
                  <div
                    className="bg-gradient-to-r from-danger to-orange-500 h-full rounded-full transition-all duration-300"
                    style={{ width: `${(deleteProgress.current / deleteProgress.total) * 100}%` }}
                  />
                </div>
                <p className="text-sm text-textSec text-center">{deleteProgress.current} / {deleteProgress.total}</p>
              </>
            )}
          </div>
        </div>
      )}

      {/* Header */}
      <header className="glass sticky top-0 z-40 px-4 md:px-8 py-4 flex items-center justify-between border-b border-white/5">
        <div className="flex items-center gap-4">
          {/* Mobile Menu Toggle */}
          <button
            onClick={() => setShowMobileMenu(true)}
            className="md:hidden p-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-textSec active:scale-95 transition-all"
          >
            <Menu size={20} />
          </button>
          <div className="flex items-center gap-4">
            <div className="w-10 h-10 bg-primary rounded-xl flex items-center justify-center shadow-lg shadow-primary/20">
              <div className="w-3 h-3 bg-white rounded-full" />
            </div>
            <div className="flex flex-col">
              <h1 className="text-xl font-black tracking-tight text-white flex items-center gap-1.5 leading-none mb-0.5">
                Sticky <span className="text-primary/70">Admin</span>
              </h1>
              <div className="flex items-center gap-1.5">
                <div className="w-1 h-1 bg-primary rounded-full animate-pulse shadow-[0_0_8px_rgba(0,168,132,0.8)]" />
                <span className="text-[9px] text-textSec font-black uppercase tracking-widest opacity-80">Aktif</span>
              </div>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 md:gap-4">
          <div className="relative group hidden sm:block">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-textSec/50 group-focus-within:text-primary transition-colors" size={16} />
            <input
              type="text"
              placeholder="Hızlı arama..."
              className="bg-white/5 border border-white/5 rounded-full pl-10 pr-4 py-2 focus:ring-2 focus:ring-primary/20 focus:border-primary/50 outline-none text-sm w-48 md:w-64 transition-all text-white placeholder:text-textSec/30"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <button
            onClick={fetchPacks}
            className="p-2.5 hover:bg-white/10 rounded-xl transition-all active:scale-90 group relative"
            title="Sistemi Yenile"
          >
            <RefreshCcw size={18} className={cn("text-textSec group-hover:text-primary transition-colors", loading && 'animate-spin text-primary')} />
          </button>

          <div className="w-px h-6 bg-white/10 mx-1 hidden md:block" />

          <button
            onClick={() => {
              if (window.confirm("Güvenli çıkış yapmak istiyor musunuz?")) signOut(auth);
            }}
            className="hidden md:flex items-center gap-2.5 px-4 py-2 bg-danger/5 hover:bg-danger/10 text-textSec hover:text-danger rounded-xl transition-all border border-transparent hover:border-danger/20"
          >
            <LogOut size={16} />
            <span className="text-xs font-black uppercase tracking-wider">Çıkış</span>
          </button>
        </div>
      </header>

      {/* Mobile Menu Overlay */}
      {showMobileMenu && (
        <div className="fixed inset-0 z-50 md:hidden bg-background/95 backdrop-blur-xl animate-in fade-in duration-300">
          <div className="flex flex-col h-full p-8">
            <div className="flex items-center justify-between mb-12">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-primary rounded-xl flex items-center justify-center">
                  <div className="w-3 h-3 bg-white rounded-full" />
                </div>
                <span className="text-xl font-black text-white">Sticky Admin</span>
              </div>
              <button
                onClick={() => setShowMobileMenu(false)}
                className="p-3 bg-white/5 rounded-2xl text-textSec"
              >
                <X size={24} />
              </button>
            </div>

            <div className="flex-1 space-y-4">
              {[
                { id: 'dashboard', label: 'Dashboard', icon: Grid },
                { id: 'stats', label: 'İstatistikler', icon: BarChart3 },
                { id: 'messages', label: 'Mesajlar', icon: Mail, count: messages.filter(m => m.status === 'unread').length },
                { id: 'notifications', label: 'Bildirimler', icon: Bell },
                { id: 'users', label: 'Kullanicilar', icon: Users }
              ].map((item) => (
                <button
                  key={item.id}
                  onClick={() => {
                    setActiveTab(item.id as any);
                    setShowMobileMenu(false);
                  }}
                  className={cn(
                    "w-full flex items-center gap-5 p-5 rounded-3xl transition-all border",
                    activeTab === item.id
                      ? "bg-primary border-primary/20 text-white shadow-xl shadow-primary/20"
                      : "bg-white/5 border-transparent text-textSec hover:bg-white/10"
                  )}
                >
                  <item.icon size={24} />
                  <span className="text-lg font-black uppercase tracking-widest">{item.label}</span>
                  {item.count ? (
                    <span className="ml-auto bg-white/20 text-white text-xs font-black min-w-[24px] h-6 flex items-center justify-center rounded-full px-2">
                      {item.count}
                    </span>
                  ) : null}
                </button>
              ))}
            </div>

            <div className="pt-8 border-t border-white/5">
              <button
                onClick={() => {
                  if (window.confirm("Çıkış yapmak istediğinize emin misiniz?")) signOut(auth);
                }}
                className="w-full flex items-center justify-center gap-4 p-5 bg-danger/10 text-danger rounded-3xl font-black uppercase tracking-widest"
              >
                <LogOut size={24} />
                Güvenli Çıkış
              </button>
            </div>
          </div>
        </div>
      )}

      <main className="flex-1 overflow-hidden flex flex-col md:flex-row relative">
        {/* Navigation Sidebar (Vertical Center on Desktop) */}
        <div className="hidden md:flex fixed left-0 top-0 bottom-0 w-24 flex-col items-center justify-center z-30 pointer-events-none">
          <div className="bg-card/40 backdrop-blur-2xl border border-white/5 rounded-[2.5rem] p-4 space-y-6 pointer-events-auto shadow-2xl shadow-black/40">
            {[
              { id: 'dashboard', icon: Grid, label: 'Panel' },
              { id: 'stats', icon: BarChart3, label: 'Veriler' },
              { id: 'messages', icon: Mail, label: 'Mesajlar', count: messages.filter(m => m.status === 'unread').length },
              { id: 'notifications', icon: Bell, label: 'Bildirim' },
              { id: 'users', icon: Users, label: 'Kullanicilar' }
            ].map(item => (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id as any)}
                className={cn(
                  "relative w-14 h-14 flex items-center justify-center rounded-2xl transition-all duration-300 group",
                  activeTab === item.id
                    ? "bg-primary text-white shadow-xl shadow-primary/20 scale-105"
                    : "text-textSec hover:bg-white/10 hover:scale-110"
                )}
                title={item.label}
              >
                <item.icon size={26} />
                {item.count ? (
                  <span className="absolute -top-1 -right-1 bg-danger text-white text-[9px] font-black w-5 h-5 rounded-full flex items-center justify-center border-2 border-background ring-2 ring-danger/20">
                    {item.count}
                  </span>
                ) : null}

                {/* Tooltip-like label */}
                <div className="absolute left-full ml-6 px-3 py-1.5 bg-card border border-white/10 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap z-50 shadow-2xl">
                  <span className="text-[10px] font-black uppercase tracking-widest text-white">{item.label}</span>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Desktop Sidebar Spacer */}
        <div className="hidden md:block w-24 shrink-0" />

        {activeTab === 'dashboard' ? (
          <>
            {/* Sidebar / Pack List */}
            <div className={cn(
              "border-r border-white/5 flex-col bg-card/30 transition-all duration-500",
              selectedPack ? "md:w-[400px] w-full hidden md:flex" : "w-full flex"
            )}>
              <div className="p-5 space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex flex-col">
                    <span className="text-[10px] font-bold uppercase tracking-widest text-textSec">Sticker Paketleri</span>
                    <span className="text-lg font-bold">{filteredPacks.length} Paket</span>
                  </div>

                  {/* Filter Dropdown */}
                  <div className="flex items-center gap-2">
                    {/* Status Filter */}
                    <div className="relative">
                      <button
                        onClick={() => {
                          setShowFilterDropdown(!showFilterDropdown);
                          setShowCategoryDropdown(false);
                        }}
                        className={cn(
                          "flex items-center gap-2 px-3 py-2 rounded-xl text-[10px] font-black uppercase tracking-wider transition-all border",
                          statusFilter !== 'all'
                            ? "bg-primary/20 border-primary text-primary"
                            : "bg-white/5 border-white/5 text-textSec hover:bg-hover"
                        )}
                      >
                        <Filter size={14} />
                        {statusFilter === 'all' ? 'Filtrele' : statusFilter.toUpperCase()}
                        <ChevronDown size={14} className={cn("transition-transform", showFilterDropdown && "rotate-180")} />
                      </button>

                      {showFilterDropdown && (
                        <>
                          <div className="fixed inset-0 z-30" onClick={() => setShowFilterDropdown(false)} />
                          <div className="absolute right-0 top-full mt-2 w-48 glass rounded-2xl border border-white/10 shadow-2xl py-2 z-40 animate-in fade-in zoom-in-95 duration-200">
                            {[
                              { id: 'all', label: 'Tümü', icon: Grid },
                              { id: 'active', label: 'Aktif Paketler', icon: Check },
                              { id: 'passive', label: 'Pasif Paketler', icon: X },
                              { id: 'premium', label: 'Premium Paketler', icon: DollarSign },
                              { id: 'normal', label: 'Normal Paketler', icon: Package },
                              { id: 'animated', label: 'Hareketli Paketler', icon: RefreshCcw },
                              { id: 'static', label: 'Statik Paketler', icon: ImageIcon },
                              { id: 'new', label: 'Yeni Eklenenler', icon: Clock }
                            ].map(f => (
                              <button
                                key={f.id}
                                onClick={() => {
                                  setStatusFilter(f.id as any);
                                  setShowFilterDropdown(false);
                                }}
                                className={cn(
                                  "w-full flex items-center gap-3 px-4 py-3 text-[11px] font-bold transition-all hover:bg-hover",
                                  statusFilter === f.id ? "text-primary bg-primary/5" : "text-textSec hover:text-white"
                                )}
                              >
                                <f.icon size={14} className={statusFilter === f.id ? "text-primary" : "text-textSec"} />
                                {f.label}
                              </button>
                            ))}
                          </div>
                        </>
                      )}
                    </div>

                    {/* Category Filter */}
                    <div className="relative">
                      <button
                        onClick={() => {
                          setShowCategoryDropdown(!showCategoryDropdown);
                          setShowFilterDropdown(false);
                        }}
                        className={cn(
                          "flex items-center gap-2 px-3 py-2 rounded-xl text-[10px] font-black uppercase tracking-wider transition-all border",
                          categoryFilter !== 'all'
                            ? "bg-accent/20 border-accent text-accent"
                            : "bg-white/5 border-white/5 text-textSec hover:bg-hover"
                        )}
                      >
                        <Grid size={14} />
                        {categoryFilter === 'all' ? 'Kategori' : CATEGORIES.find(c => c.id === categoryFilter)?.name.toUpperCase()}
                        <ChevronDown size={14} className={cn("transition-transform", showCategoryDropdown && "rotate-180")} />
                      </button>

                      {showCategoryDropdown && (
                        <>
                          <div className="fixed inset-0 z-30" onClick={() => setShowCategoryDropdown(false)} />
                          <div className="absolute right-0 top-full mt-2 w-56 glass rounded-2xl border border-white/10 shadow-2xl py-2 z-40 animate-in fade-in zoom-in-95 duration-200 max-h-[400px] overflow-y-auto custom-scrollbar">
                            <button
                              onClick={() => {
                                setCategoryFilter('all');
                                setShowCategoryDropdown(false);
                              }}
                              className={cn(
                                "w-full flex items-center gap-3 px-4 py-3 text-[11px] font-bold transition-all hover:bg-hover",
                                categoryFilter === 'all' ? "text-accent bg-accent/5" : "text-textSec hover:text-white"
                              )}
                            >
                              <div className="w-5 h-5 flex items-center justify-center bg-white/5 rounded-lg text-xs">✨</div>
                              Tümü (Hepsi)
                            </button>
                            {CATEGORIES.map(cat => (
                              <button
                                key={cat.id}
                                onClick={() => {
                                  setCategoryFilter(cat.id);
                                  setShowCategoryDropdown(false);
                                }}
                                className={cn(
                                  "w-full flex items-center gap-3 px-4 py-3 text-[11px] font-bold transition-all hover:bg-hover",
                                  categoryFilter === cat.id ? "text-accent bg-accent/5" : "text-textSec hover:text-white"
                                )}
                              >
                                <div className="w-5 h-5 flex items-center justify-center bg-white/5 rounded-lg text-xs">{cat.emoji}</div>
                                {cat.name}
                              </button>
                            ))}
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex-1 overflow-y-auto px-4 pb-20 space-y-2.5 flex flex-col custom-scrollbar">
                <button
                  onClick={() => setShowNewPackModal(true)}
                  className="w-full flex items-center justify-center gap-2 p-4 border-2 border-dashed border-white/10 hover:border-primary/50 hover:bg-primary/5 rounded-2xl group transition-all mb-4"
                >
                  <Plus className="text-textSec group-hover:text-primary transition-colors" size={20} />
                  <span className="text-sm font-bold text-textSec group-hover:text-primary">Yeni Paket Oluştur</span>
                </button>

                {filteredPacks.map(pack => (
                  <div
                    key={pack.id}
                    onClick={() => setSelectedPack(pack)}
                    className={cn(
                      "group relative cursor-pointer p-3.5 rounded-2xl transition-all duration-300 border",
                      selectedPack?.id === pack.id
                        ? 'bg-primary/10 border-primary/50 shadow-xl shadow-primary/5 scale-[1.02]'
                        : 'bg-card border-white/5 hover:border-white/20 hover:bg-hover active:scale-[0.98]'
                    )}
                  >
                    <div className="flex items-center gap-4">
                      <div className="relative w-14 h-14 bg-hover rounded-2xl overflow-hidden glass flex-shrink-0 flex items-center justify-center group-hover:scale-105 transition-transform">
                        {pack.tray_url ? (
                          <img src={pack.tray_url} alt="" className="w-10 h-10 object-contain" />
                        ) : (
                          <Package className="w-6 h-6 text-textSec" />
                        )}
                        {pack.is_premium && (
                          <div className="absolute top-0 right-0 w-4 h-4 bg-warning flex items-center justify-center rounded-bl-xl shadow-sm">
                            <span className="text-[8px] text-background font-black">P</span>
                          </div>
                        )}
                        {pack.is_active === false && (
                          <div className="absolute bottom-0 left-0 right-0 bg-danger/80 py-0.5 flex items-center justify-center">
                            <span className="text-[7px] text-white font-black tracking-widest">PASİF</span>
                          </div>
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <h3 className="font-bold text-sm truncate text-white">
                          {pack.name}
                          {pack.name_tr && pack.name_tr !== pack.name && (
                            <span className="text-textSec font-normal ml-2">({pack.name_tr})</span>
                          )}
                        </h3>
                        <div className="flex items-center gap-2 mt-1.5">
                          <span className="text-[10px] bg-white/5 px-2 py-0.5 rounded-full text-textSec font-semibold">
                            {pack.sticker_count} Sticker
                          </span>
                          {pack.category && (
                            <span className="text-[10px] text-primary font-bold uppercase tracking-tighter">{pack.category}</span>
                          )}
                          {isNew(pack) && (
                            <span className="px-1.5 py-0.5 bg-accent/20 text-accent text-[8px] font-black rounded-md animate-pulse">YENİ</span>
                          )}
                        </div>
                      </div>
                      <div className="flex flex-col items-end opacity-0 group-hover:opacity-100 transition-opacity">
                        <button
                          onClick={(e) => { e.stopPropagation(); deletePack(pack); }}
                          className="p-2 hover:text-danger hover:bg-danger/10 rounded-xl transition-all active:scale-90"
                        >
                          <Trash2 size={16} />
                        </button>
                        <ChevronRight size={16} className="text-textSec mr-1" />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Details Panel */}
            {selectedPack ? (
              <div className="flex-1 flex flex-col bg-background/80 overflow-hidden animate-in fade-in slide-in-from-right-10 duration-500">
                {/* Detail Header */}
                <div className="p-4 md:p-8 border-b border-white/5 bg-card/20 backdrop-blur-xl">
                  <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-start justify-between gap-6">
                    {/* Mobile Back Button */}
                    <button
                      onClick={() => setSelectedPack(null)}
                      className="md:hidden flex items-center gap-2 text-textSec hover:text-white mb-2"
                    >
                      <ChevronRight className="rotate-180" size={20} />
                      <span className="text-sm font-bold">Listeye Dön</span>
                    </button>
                    <div className="flex flex-col md:flex-row gap-4 md:gap-8 items-center md:items-start text-center md:text-left w-full md:w-auto">
                      <div className="w-32 h-32 bg-card rounded-3xl overflow-hidden glass flex items-center justify-center p-4 shadow-2xl relative group">
                        {selectedPack.tray_url ? (
                          <img src={selectedPack.tray_url} alt="" className="w-full h-full object-contain group-hover:scale-110 transition-transform duration-500" />
                        ) : (
                          <Package className="w-16 h-16 text-textSec/20" />
                        )}

                      </div>
                      <div className="space-y-4">
                        <div className="space-y-1">
                          <div className="flex items-center gap-3">
                            <h2 className="text-4xl font-black tracking-tight text-white">{selectedPack.name}</h2>
                            <span className={cn(
                              "px-4 py-1.5 rounded-full text-[10px] font-black uppercase tracking-widest shadow-lg",
                              selectedPack.is_premium ? 'bg-warning text-background' : 'bg-primary text-white'
                            )}>
                              {selectedPack.is_premium ? 'Premium Pack' : 'Standard Pack'}
                            </span>
                          </div>
                          <p className="text-textSec text-lg flex items-center gap-2">
                            <UserIcon size={16} />
                            {selectedPack.publisher}
                            <span className="w-1 h-1 bg-white/20 rounded-full" />
                            <span className="text-primary font-bold uppercase tracking-tighter">{selectedPack.category}</span>
                          </p>
                        </div>

                        <div className="flex items-center gap-4">
                          <StatCard label="İndirme" value={selectedPack.download_count} color="primary" />
                          <StatCard label="Görüntülenme" value={selectedPack.view_count} color="accent" />
                          <button
                            onClick={() => resetStats(selectedPack)}
                            className="p-3 bg-white/5 hover:bg-white/10 rounded-2xl transition-all text-textSec hover:text-white"
                            title="İstatistikleri Sıfırla"
                          >
                            <RefreshCcw size={20} />
                          </button>
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-col gap-3">
                      <label className="relative flex items-center gap-2 px-6 py-3.5 bg-primary hover:bg-primary/90 text-white rounded-2xl text-sm font-black shadow-xl shadow-primary/20 transition-all hover:translate-y-[-2px] active:translate-y-0 cursor-pointer">
                        <Plus size={20} className="stroke-[3]" /> Sticker Ekle
                        <input type="file" multiple className="hidden" onChange={handleAddSticker} disabled={isProcessing} />
                      </label>
                      <button
                        onClick={() => {
                          setEditFormData({
                            ...selectedPack
                          });
                          setShowEditPackModal(true);
                        }}
                        className="flex items-center gap-2 px-6 py-3.5 bg-card hover:bg-hover border border-white/5 rounded-2xl text-sm font-bold transition-all text-textSec hover:text-textMain"
                      >
                        <Settings size={20} /> Paket Bilgileri
                      </button>
                    </div>
                  </div>
                </div>

                {/* Sticker Grid */}
                <div className="flex-1 overflow-y-auto p-4 md:p-8 custom-scrollbar pb-24 md:pb-8">
                  <div className="max-w-7xl mx-auto">
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
                      <h3 className="text-xl font-bold flex items-center gap-3">
                        <Grid className="text-primary" size={24} />
                        Paket İçeriği
                        <span className="bg-white/5 px-2.5 py-1 rounded-lg text-xs font-mono ml-2">{selectedPack.sticker_count} DOSYA</span>
                      </h3>
                      <div className="flex items-center gap-4 text-xs text-textSec font-bold uppercase tracking-widest">
                        <span className="flex items-center gap-1.5"><Info size={14} /> Anlık Bulut Önizleme</span>
                      </div>
                    </div>

                    <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-7 xl:grid-cols-8 gap-3 md:gap-5">
                      {selectedPack.stickers?.map((sticker, idx) => (
                        <div
                          key={idx}
                          className="group relative aspect-square bg-card/50 rounded-2xl glass p-4 hover:ring-2 hover:ring-primary/50 transition-all duration-300 shadow-lg hover:shadow-2xl hover:shadow-primary/5 cursor-zoom-in"
                          onClick={() => setPreviewSticker({ url: sticker.url, title: sticker.image_file })}
                        >
                          <div className="w-full h-full flex items-center justify-center pointer-events-none">
                            <img
                              src={sticker.url}
                              alt=""
                              className="w-full h-full object-contain group-hover:scale-110 transition-transform duration-500"
                            />
                          </div>
                          <div className="absolute inset-0 bg-background/60 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2 rounded-2xl backdrop-blur-[2px]" onClick={(e) => e.stopPropagation()}>
                            <button
                              onClick={() => deleteSticker(selectedPack, sticker)}
                              className="p-2.5 bg-danger hover:bg-danger/80 text-white rounded-xl shadow-lg transition-all hover:scale-110"
                            >
                              <Trash2 size={18} />
                            </button>
                            <a
                              href={sticker.url}
                              target="_blank"
                              rel="noreferrer"
                              className="p-2.5 bg-primary hover:bg-primary/80 text-white rounded-xl shadow-lg transition-all hover:scale-110"
                            >
                              <ExternalLink size={18} />
                            </a>
                          </div>
                          <div className="absolute bottom-2 left-3 text-[9px] font-black text-textSec group-hover:text-primary transition-colors opacity-0 group-hover:opacity-100 tracking-tighter">
                            {sticker.image_file.toUpperCase()}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex-1 flex flex-col items-center justify-center">
                {/* Boş Durum - Temizlendi */}
              </div>
            )}
          </>
        ) : activeTab === 'stats' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-12 custom-scrollbar bg-background">
            <div className="max-w-6xl mx-auto space-y-8 md:space-y-12 animate-in fade-in duration-500">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div>
                  <div className="flex items-center gap-3 mb-1">
                    <BarChart3 className="text-primary" size={24} />
                    <h2 className="text-2xl md:text-4xl font-black text-white uppercase tracking-tighter">Performans Analizi</h2>
                  </div>
                  <p className="text-textSec text-xs md:text-base font-medium">Uygulama genelindeki etkileşim ve verimlilik raporu</p>
                </div>

                <div className="flex items-center gap-4">
                  {/* Stats Filter */}
                  <div className="relative group">
                    <button
                      onClick={() => setShowFilterDropdown(!showFilterDropdown)}
                      className={cn(
                        "flex items-center gap-3 px-6 py-3 rounded-2xl text-xs font-black uppercase tracking-widest transition-all border shadow-xl",
                        statsFilter !== 'all'
                          ? "bg-primary/20 border-primary text-primary shadow-primary/10"
                          : "bg-white/5 border-white/10 text-textSec hover:bg-hover active:scale-95"
                      )}
                    >
                      <Filter size={16} />
                      {statsFilter === 'all' ? 'Veri Filtrele' : `${statsFilter.toUpperCase()} VERİLER`}
                      <ChevronDown size={16} className={cn("transition-transform duration-300", showFilterDropdown && "rotate-180")} />
                    </button>

                    {showFilterDropdown && (
                      <>
                        <div className="fixed inset-0 z-30" onClick={() => setShowFilterDropdown(false)} />
                        <div className="absolute right-0 top-full mt-3 w-56 glass rounded-[2rem] border border-white/10 shadow-2xl py-3 z-40 animate-in fade-in zoom-in-95 duration-300 ring-1 ring-white/5">
                          <div className="px-5 py-2 mb-2 border-b border-white/5">
                            <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Görünüm Ayarı</span>
                          </div>
                          {[
                            { id: 'all', label: 'Tüm Paketler', icon: Grid, color: 'text-white' },
                            { id: 'active', label: 'Aktif Olanlar', icon: Check, color: 'text-primary' },
                            { id: 'passive', label: 'Pasif Olanlar', icon: X, color: 'text-danger' },
                            { id: 'premium', label: 'Sadece Premium', icon: DollarSign, color: 'text-warning' },
                            { id: 'normal', label: 'Sadece Normal', icon: Package, color: 'text-accent' }
                          ].map(f => (
                            <button
                              key={f.id}
                              onClick={() => {
                                setStatsFilter(f.id as any);
                                setShowFilterDropdown(false);
                              }}
                              className={cn(
                                "w-full flex items-center gap-4 px-5 py-3.5 text-xs font-bold transition-all",
                                statsFilter === f.id ? "bg-white/10 text-white" : "text-textSec hover:text-white hover:bg-white/5"
                              )}
                            >
                              <f.icon size={16} className={f.color} />
                              {f.label}
                            </button>
                          ))}
                        </div>
                      </>
                    )}
                  </div>

                  <button
                    onClick={fetchPacks}
                    className="p-3.5 bg-white/5 hover:bg-white/10 border border-white/10 rounded-2xl text-textSec transition-all active:scale-90"
                    title="Verileri Güncelle"
                  >
                    <RefreshCcw size={20} className={loading ? 'animate-spin text-primary' : ''} />
                  </button>
                </div>
              </div>

              {/* Fake Base Controls */}
              <div className="glass rounded-[2rem] p-4 md:p-6 mb-6 border border-white/10">
                <div className="flex flex-wrap items-center gap-4">
                  <span className="text-xs font-black text-textSec uppercase tracking-widest shrink-0">Fake İndirme Aralığı:</span>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      value={fakeBaseMin}
                      onChange={(e) => setFakeBaseMin(Number(e.target.value))}
                      className="w-24 md:w-28 px-4 py-2 bg-white/5 border border-white/10 rounded-xl text-white text-sm outline-none focus:border-primary/50 transition-all"
                      placeholder="Min"
                    />
                    <span className="text-textSec">-</span>
                    <input
                      type="number"
                      value={fakeBaseMax}
                      onChange={(e) => setFakeBaseMax(Number(e.target.value))}
                      className="w-24 md:w-28 px-4 py-2 bg-white/5 border border-white/10 rounded-xl text-white text-sm outline-none focus:border-primary/50 transition-all"
                      placeholder="Max"
                    />
                  </div>
                  <div className="flex items-center gap-2 flex-1 md:flex-none justify-end md:justify-start">
                    <button
                      onClick={() => updateAllPacksWithFakeBase(false)}
                      disabled={isProcessing}
                      className="flex-1 md:flex-none px-4 py-2 bg-primary/20 hover:bg-primary/30 border border-primary/30 rounded-xl text-primary text-[10px] font-black uppercase tracking-tight transition-all active:scale-90 disabled:opacity-50"
                    >
                      {isProcessing ? '..' : 'Eksiklere'}
                    </button>
                    <button
                      onClick={() => updateAllPacksWithFakeBase(true)}
                      disabled={isProcessing}
                      className="flex-1 md:flex-none px-4 py-2 bg-warning/20 hover:bg-warning/30 border border-warning/30 rounded-xl text-warning text-[10px] font-black uppercase tracking-tight transition-all active:scale-90 disabled:opacity-50"
                    >
                      {isProcessing ? '..' : 'Tümünü'}
                    </button>
                  </div>
                </div>
              </div>

              {/* Advanced Metrics Grid */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                {((): any => {
                  const sPacks = packs.filter(p => {
                    if (statsFilter === 'all') return true;
                    if (statsFilter === 'active') return p.is_active !== false;
                    if (statsFilter === 'passive') return p.is_active === false;
                    if (statsFilter === 'premium') return p.is_premium === true;
                    if (statsFilter === 'normal') return p.is_premium === false;
                    return true;
                  });

                  const totalDL = sPacks.reduce((acc, p) => acc + (p.download_count || 0), 0);
                  const totalViews = sPacks.reduce((acc, p) => acc + (p.view_count || 0), 0);
                  const totalStickers = sPacks.reduce((acc, p) => acc + (p.sticker_count || 0), 0);
                  const avgCVR = totalViews > 0 ? (totalDL / totalViews) * 100 : 0;

                  return (
                    <>
                      <div className="glass p-8 rounded-[2.5rem] bg-gradient-to-br from-primary/10 to-transparent border border-white/5 shadow-xl group hover:scale-[1.02] transition-all duration-500">
                        <div className="flex items-center justify-between mb-6">
                          <div className="bg-primary/20 p-3 rounded-2xl text-primary transform group-hover:rotate-12 transition-transform">
                            <TrendingUp size={24} />
                          </div>
                          <span className="text-[10px] font-black text-primary/60 bg-primary/5 px-2 py-1 rounded-lg">ETKİLEŞİM</span>
                        </div>
                        <div className="space-y-1">
                          <h4 className="text-xs font-bold text-textSec uppercase tracking-widest">Toplam İndirme</h4>
                          <div className="text-4xl font-black text-white">{totalDL.toLocaleString()}</div>
                        </div>
                      </div>

                      <div className="glass p-8 rounded-[2.5rem] bg-gradient-to-br from-accent/10 to-transparent border border-white/5 shadow-xl group hover:scale-[1.02] transition-all duration-500">
                        <div className="flex items-center justify-between mb-6">
                          <div className="bg-accent/20 p-3 rounded-2xl text-accent transform group-hover:rotate-12 transition-transform">
                            <BarChart3 size={24} />
                          </div>
                          <span className="text-[10px] font-black text-accent/60 bg-accent/5 px-2 py-1 rounded-lg">ERİŞİM</span>
                        </div>
                        <div className="space-y-1">
                          <h4 className="text-xs font-bold text-textSec uppercase tracking-widest">Görüntülenme</h4>
                          <div className="text-4xl font-black text-white">{totalViews.toLocaleString()}</div>
                        </div>
                      </div>

                      <div className="glass p-8 rounded-[2.5rem] bg-gradient-to-br from-warning/10 to-transparent border border-white/5 shadow-xl group hover:scale-[1.02] transition-all duration-500">
                        <div className="flex items-center justify-between mb-6">
                          <div className="bg-warning/20 p-3 rounded-2xl text-warning transform group-hover:rotate-12 transition-transform">
                            <Lightbulb size={24} />
                          </div>
                          <span className="text-[10px] font-black text-warning/60 bg-warning/5 px-2 py-1 rounded-lg">VERİMLİLİK</span>
                        </div>
                        <div className="space-y-1">
                          <h4 className="text-xs font-bold text-textSec uppercase tracking-widest">Dönüşüm (CVR)</h4>
                          <div className="text-4xl font-black text-white">%{avgCVR.toFixed(1)}</div>
                          <div className="w-full h-1 bg-white/5 rounded-full overflow-hidden mt-2">
                            <div className="h-full bg-warning" style={{ width: `${Math.min(100, avgCVR)}%` }} />
                          </div>
                        </div>
                      </div>

                      <div className="glass p-8 rounded-[2.5rem] bg-gradient-to-br from-purple-500/10 to-transparent border border-white/5 shadow-xl group hover:scale-[1.02] transition-all duration-500">
                        <div className="flex items-center justify-between mb-6">
                          <div className="bg-purple-500/20 p-3 rounded-2xl text-purple-400 transform group-hover:rotate-12 transition-transform">
                            <Grid size={24} />
                          </div>
                          <span className="text-[10px] font-black text-purple-400/60 bg-purple-500/5 px-2 py-1 rounded-lg">KÜTÜPHANE</span>
                        </div>
                        <div className="space-y-1">
                          <h4 className="text-xs font-bold text-textSec uppercase tracking-widest">Toplam Sticker</h4>
                          <div className="text-4xl font-black text-white">{totalStickers.toLocaleString()}</div>
                          <p className="text-[10px] font-bold text-textSec">{sPacks.length} Paket İçerisinde</p>
                        </div>
                      </div>
                    </>
                  );
                })()}
              </div>

              {/* Chart & Ranking Section */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* Visual Analysis */}
                <div className="lg:col-span-2 glass p-10 rounded-[3rem] border border-white/5 bg-card/20 shadow-2xl relative overflow-hidden">
                  <div className="flex items-center justify-between mb-10 relative z-10">
                    <div className="space-y-1">
                      <h3 className="text-xl md:text-2xl font-black text-white tracking-tight">Eğilim Analizi</h3>
                      <p className="text-textSec text-[10px] md:text-sm">En popüler 10 paketin performans karşılaştırması</p>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="flex items-center gap-2">
                        <div className="w-3 h-3 rounded-full bg-primary shadow-[0_0_10px_rgba(0,168,132,0.4)]" />
                        <span className="text-[10px] font-black text-textSec uppercase tracking-tighter">İndirme</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <div className="w-3 h-3 rounded-full bg-accent shadow-[0_0_10px_rgba(52,183,241,0.4)]" />
                        <span className="text-[10px] font-black text-textSec uppercase tracking-tighter">Görüntüleme</span>
                      </div>
                    </div>
                  </div>

                  <div className="h-[400px] w-full relative z-10">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={packs
                          .filter(p => {
                            if (statsFilter === 'all') return true;
                            if (statsFilter === 'active') return p.is_active !== false;
                            if (statsFilter === 'passive') return p.is_active === false;
                            if (statsFilter === 'premium') return p.is_premium === true;
                            if (statsFilter === 'normal') return p.is_premium === false;
                            return true;
                          })
                          .sort((a, b) => (b.download_count || 0) - (a.download_count || 0))
                          .slice(0, 10)
                          .map(p => {
                            const pName = p.name || 'İsimsiz Paket';
                            return {
                              name: pName.length > 10 ? pName.substring(0, 8) + '..' : pName,
                              downloads: p.download_count || 0,
                              views: p.view_count || 0
                            };
                          })}
                        margin={{ top: 10, right: 10, left: 0, bottom: 20 }}
                        barGap={12}
                      >
                        <defs>
                          <linearGradient id="gPrimary" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="#00A884" stopOpacity={1} />
                            <stop offset="100%" stopColor="#00A884" stopOpacity={0.4} />
                          </linearGradient>
                          <linearGradient id="gAccent" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="#34B7F1" stopOpacity={1} />
                            <stop offset="100%" stopColor="#34B7F1" stopOpacity={0.4} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="5 5" stroke="rgba(255,255,255,0.03)" vertical={false} />
                        <XAxis dataKey="name" stroke="rgba(255,255,255,0.3)" fontSize={11} fontWeight="800" axisLine={false} tickLine={false} dy={15} />
                        <YAxis stroke="rgba(255,255,255,0.3)" fontSize={11} fontWeight="800" axisLine={false} tickLine={false} tickFormatter={(v) => v >= 1000 ? `${v / 1000}k` : v} />
                        <Tooltip
                          contentStyle={{ backgroundColor: '#1A1D21', border: 'none', borderRadius: '16px', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)', padding: '16px' }}
                          cursor={{ fill: 'rgba(255,255,255,0.02)' }}
                          itemStyle={{ fontWeight: '900', fontSize: '13px' }}
                        />
                        <Bar dataKey="downloads" fill="url(#gPrimary)" radius={[8, 8, 2, 2]} name="İndirme" barSize={24} />
                        <Bar dataKey="views" fill="url(#gAccent)" radius={[8, 8, 2, 2]} name="Görüntüleme" barSize={24} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                {/* Best Performers Mini Table */}
                <div className="glass p-8 rounded-[3rem] border border-white/5 bg-card/20 shadow-2xl flex flex-col">
                  <div className="mb-6 md:mb-8">
                    <h3 className="text-lg md:text-xl font-black text-white mb-1 uppercase tracking-tighter">🏆 Lider Tablosu</h3>
                    <p className="text-[9px] md:text-[10px] font-bold text-textSec uppercase tracking-widest">En çok indirilen ilk 5</p>
                  </div>
                  <div className="flex-1 space-y-4">
                    {packs
                      .filter(p => {
                        if (statsFilter === 'all') return true;
                        if (statsFilter === 'active') return p.is_active !== false;
                        if (statsFilter === 'passive') return p.is_active === false;
                        if (statsFilter === 'premium') return p.is_premium === true;
                        if (statsFilter === 'normal') return p.is_premium === false;
                        return true;
                      })
                      .sort((a, b) => (b.download_count || 0) - (a.download_count || 0))
                      .slice(0, 5)
                      .map((p, i) => (
                        <div key={p.id} className="flex items-center gap-4 p-3.5 rounded-2xl bg-white/2 border border-white/5 hover:bg-white/5 transition-all group">
                          <div className={cn(
                            "w-8 h-8 rounded-xl flex items-center justify-center font-black text-xs shrink-0 shadow-lg",
                            i === 0 ? "bg-amber-400 text-black scale-110" :
                              i === 1 ? "bg-slate-300 text-black" :
                                i === 2 ? "bg-amber-700 text-white" : "bg-card text-textSec"
                          )}>
                            {i + 1}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="text-xs font-black text-white truncate group-hover:text-primary transition-colors">{p.name || 'İsimsiz Paket'}</div>
                            <div className="text-[9px] font-bold text-textSec uppercase tracking-tighter">{p.category}</div>
                          </div>
                          <div className="text-right">
                            <div className="text-xs font-black text-primary">{(p.download_count || 0).toLocaleString()}</div>
                            <div className="text-[8px] font-bold text-textSec uppercase">İndirme</div>
                          </div>
                        </div>
                      ))}
                  </div>
                  <div className="mt-8 pt-6 border-t border-white/5">
                    <div className="flex items-center justify-between text-xs font-black">
                      <span className="text-textSec uppercase tracking-widest">Kapsam:</span>
                      <span className="text-white bg-primary/20 px-2.5 py-1 rounded-lg border border-primary/20">{statsFilter.toUpperCase()}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Full Performance List */}
              <div className="glass rounded-[3rem] border border-white/5 overflow-hidden shadow-2xl">
                <div className="px-4 md:px-10 py-6 md:py-8 border-b border-white/5 bg-white/2 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="bg-primary p-2 rounded-xl">
                      <Grid className="text-white" size={18} />
                    </div>
                    <h3 className="text-lg md:text-2xl font-black text-white tracking-tighter uppercase">Detaylı Performans Listesi</h3>
                  </div>
                </div>
                <div className="overflow-x-auto custom-scrollbar">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-card">
                        <th className="px-4 md:px-10 py-4 md:py-5 text-[9px] md:text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5">Paket Bilgisi</th>
                        <th className="px-4 md:px-10 py-4 md:py-5 text-[9px] md:text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5 text-center">İndirme</th>
                        <th className="hidden sm:table-cell px-4 md:px-10 py-4 md:py-5 text-[9px] md:text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5 text-center">Görüntülenme</th>
                        <th className="hidden lg:table-cell px-4 md:px-10 py-4 md:py-5 text-[9px] md:text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5 text-center">Favori</th>
                        <th className="hidden md:table-cell px-4 md:px-10 py-4 md:py-5 text-[9px] md:text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5 text-right w-64">Dönüşüm Oranı (CVR)</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/2">
                      {packs
                        .filter(p => {
                          if (statsFilter === 'all') return true;
                          if (statsFilter === 'active') return p.is_active !== false;
                          if (statsFilter === 'passive') return p.is_active === false;
                          if (statsFilter === 'premium') return p.is_premium === true;
                          if (statsFilter === 'normal') return p.is_premium === false;
                          return true;
                        })
                        .sort((a, b) => (b.download_count || 0) - (a.download_count || 0))
                        .map((p) => (
                          <tr key={p.id} className="hover:bg-white/3 transition-all group">
                            <td className="px-4 md:px-10 py-4 md:py-6">
                              <div className="flex items-center gap-3 md:gap-5">
                                <div className="w-10 h-10 md:w-12 md:h-12 rounded-2xl bg-card border border-white/5 p-1 relative overflow-hidden group-hover:scale-110 transition-transform">
                                  <img src={p.tray_url} className="w-full h-full object-contain" />
                                  {p.is_premium && <div className="absolute top-0 right-0 w-3 h-3 bg-warning rounded-bl-lg" />}
                                </div>
                                <div className="min-w-0">
                                  <div className="font-black text-white text-sm md:text-base group-hover:text-primary transition-colors truncate">{p.name || 'İsimsiz Paket'}</div>
                                  <div className="flex items-center gap-2 mt-0.5">
                                    <span className="text-[8px] md:text-[10px] font-bold text-textSec uppercase tracking-widest transition-all truncate">{p.category}</span>
                                    <span className={cn(
                                      "px-1.5 py-0.5 rounded-md text-[7px] md:text-[8px] font-black tracking-widest uppercase",
                                      p.is_active !== false ? "bg-primary/20 text-primary" : "bg-danger/20 text-danger"
                                    )}>
                                      {p.is_active !== false ? 'AKTİF' : 'PASİF'}
                                    </span>
                                  </div>
                                </div>
                              </div>
                            </td>
                            <td className="px-4 md:px-10 py-4 md:py-6 text-center">
                              <span className="text-sm md:text-lg font-black text-primary">{(p.download_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="hidden sm:table-cell px-4 md:px-10 py-4 md:py-6 text-center">
                              <span className="text-sm md:text-lg font-black text-accent">{(p.view_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="hidden lg:table-cell px-4 md:px-10 py-4 md:py-6 text-center">
                              <span className="text-sm md:text-lg font-black text-warning">{(p.favorite_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="hidden md:table-cell px-4 md:px-10 py-4 md:py-6">
                              <div className="flex items-center justify-end gap-5">
                                <div className="flex-1 max-w-[120px] h-2 bg-white/5 rounded-full overflow-hidden shadow-inner">
                                  <div
                                    className={cn(
                                      "h-full rounded-full shadow-[0_0_10px_rgba(0,0,0,0.5)] transition-all duration-1000 delay-300",
                                      ((p.download_count || 0) / (p.view_count || 1)) * 100 > 25 ? "bg-primary" : "bg-warning"
                                    )}
                                    style={{ width: `${Math.min(100, ((p.download_count || 0) / (p.view_count || 1)) * 100)}%` }}
                                  />
                                </div>
                                <span className="text-sm font-black text-white tabular-nums w-12 text-right">
                                  {Math.round(((p.download_count || 0) / (p.view_count || 1)) * 100)}%
                                </span>
                              </div>
                            </td>
                          </tr>
                        ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>

        ) : activeTab === 'notifications' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-12 custom-scrollbar bg-background">
            <div className="max-w-3xl mx-auto space-y-12 animate-in fade-in duration-500">
              <div>
                <div className="flex flex-col md:flex-row md:items-center gap-4 mb-2">
                  <Bell className="text-primary w-6 h-6 md:w-8 md:h-8" />
                  <h2 className="text-2xl md:text-4xl font-black text-white uppercase tracking-tighter">BİLDİRİM GÖNDER</h2>
                </div>
                <p className="text-textSec text-xs md:text-lg">Sticky uygulamasını kullanan tüm cihazlara anlık bildirim gönderin.</p>
              </div>

              <form onSubmit={handleSendNotification} className="space-y-6 md:space-y-8">
                <div className="glass p-5 md:p-10 rounded-[2rem] md:rounded-[2.5rem] bg-gradient-to-br from-primary/5 to-transparent border border-white/5 shadow-2xl space-y-6 md:space-y-8">
                  <div className="space-y-3">
                    <label className="text-xs font-bold text-textSec uppercase tracking-widest flex items-center gap-2">
                      <Info size={14} className="text-primary" /> BİLDİRİM BAŞLIĞI
                    </label>
                    <input
                      type="text"
                      value={notifTitle}
                      onChange={(e) => setNotifTitle(e.target.value)}
                      placeholder="Sticky"
                      className="w-full bg-card/60 border border-white/10 rounded-xl md:rounded-2xl px-4 md:px-5 py-3 md:py-4 text-white text-sm md:text-base font-bold outline-none focus:ring-2 focus:ring-primary focus:bg-background transition-all"
                    />
                    <p className="text-[9px] md:text-[10px] text-textSec font-medium pl-1">Bildirimde görünecek kalın başlık. Boş bırakılırsa "Sticky" yazısı görünecektir.</p>
                  </div>

                  <div className="space-y-4">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                      <label className="text-xs font-bold text-textSec uppercase tracking-widest flex items-center gap-2">
                        <MessageSquare size={14} className="text-accent" /> BİLDİRİM MESAJI
                      </label>

                      {/* Emojis Grid */}
                      <div className="flex flex-wrap gap-1 md:gap-1.5 max-w-full sm:max-w-[400px] justify-start sm:justify-end">
                        {['😊', '😂', '❤️', '🔥', '✨', '🚀', '🎉', '🌟', '💫', '🎁', '💎', '📱', '🌈', '🎭', '🐱', '🧿', '👑', '⚡', '🔔', '💯'].map(emoji => (
                          <button
                            key={emoji}
                            type="button"
                            onClick={() => setNotifBody(prev => prev + emoji)}
                            className="w-7 h-7 md:w-8 md:h-8 flex items-center justify-center bg-card/40 hover:bg-accent/20 border border-white/5 rounded-lg text-xs md:text-sm transition-all hover:scale-110 active:scale-95"
                          >
                            {emoji}
                          </button>
                        ))}
                      </div>
                    </div>

                    <textarea
                      required
                      value={notifBody}
                      onChange={(e) => setNotifBody(e.target.value)}
                      placeholder="Sana özel harika yeni çıkartmalar geldi! Hemen göz at..."
                      rows={4}
                      className="w-full bg-card/60 border border-white/10 rounded-2xl px-5 py-4 text-white font-medium outline-none focus:ring-2 focus:ring-accent focus:bg-background transition-all resize-none shadow-inner"
                    />
                    <div className="flex items-center justify-between px-1">
                      <p className="text-[10px] text-textSec font-medium">Kullanıcıların göreceği ana mesaj metni.</p>
                      <p className="text-[10px] font-mono text-accent/60 font-bold">{notifBody.length} karakter</p>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <label className="text-xs font-bold text-textSec uppercase tracking-widest flex items-center gap-2">
                      <Package size={14} className="text-warning" /> RESİM URL (OPSİYONEL)
                    </label>
                    <input
                      type="url"
                      value={notifImageUrl}
                      onChange={(e) => setNotifImageUrl(e.target.value)}
                      placeholder="https://example.com/image.webp"
                      className="w-full bg-card/60 border border-white/10 rounded-2xl px-5 py-4 text-white font-mono text-sm outline-none focus:ring-2 focus:ring-warning focus:bg-background transition-all"
                    />
                    <p className="text-[10px] text-textSec font-medium pl-1">Bildirimde görünecek büyük görsel bağlantısı.</p>
                  </div>

                  <div className="pt-4">
                    <button
                      type="submit"
                      disabled={isSendingNotif || !notifBody}
                      className={cn(
                        "w-full py-5 rounded-2xl font-black shadow-xl transition-all flex items-center justify-center gap-3 disabled:opacity-50 text-white",
                        isSendingNotif ? "bg-hover" : "bg-primary shadow-primary/20 hover:scale-[1.02] active:scale-[0.98]"
                      )}
                    >
                      <Send size={22} className={cn(isSendingNotif && "animate-pulse")} />
                      {isSendingNotif ? 'GÖNDERİLİYOR...' : 'ŞİMDİ GÖNDER'}
                    </button>
                  </div>
                </div>

                <div className="bg-warning/5 border border-warning/20 p-8 rounded-[2rem] flex items-start gap-5">
                  <div className="bg-warning/20 p-4 rounded-xl">
                    <Info className="text-warning" size={24} />
                  </div>
                  <div>
                    <h4 className="text-sm font-black text-warning uppercase tracking-tight">Dikkat & İpucu</h4>
                    <ul className="text-xs text-textSec leading-relaxed mt-2 space-y-1 list-disc pl-4">
                      <li>Bu bildirim uygulamada "stickers" kanalına abone olan tüm kullanıcılara gider.</li>
                      <li>Sistemi gereksiz yere meşgul etmemek için günde en fazla 2-3 kez bildirim göndermeniz önerilir.</li>
                      <li>Mesajın sonuna ilgi çekici emojiler eklemek kullanıcı etkileşimini %20 artırır! 🚀</li>
                    </ul>
                  </div>
                </div>
              </form>
            </div>
          </div>
        ) : activeTab === 'messages' ? (
          <div className="flex-1 overflow-y-auto p-12 custom-scrollbar bg-background">
            <div className="max-w-5xl mx-auto space-y-8 animate-in fade-in duration-500">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 md:gap-0">
                <div>
                  <h2 className="text-2xl md:text-4xl font-black text-white uppercase tracking-tighter">Mesajlar ve Öneriler</h2>
                  <p className="text-textSec text-xs md:text-base">Uygulama kullanıcılarından gelen iletişim talepleri</p>
                </div>
                <div className="flex flex-wrap items-center gap-2 md:gap-3">
                  <span className="text-[10px] text-green-500 font-black uppercase tracking-widest flex items-center gap-1.5 bg-green-500/5 px-2 py-1 rounded-lg border border-green-500/10">
                    <span className="w-1.5 h-1.5 bg-green-500 rounded-full animate-pulse"></span>
                    Canlı
                  </span>
                  <button
                    onClick={clearAllMessages}
                    className="px-3 py-1.5 bg-danger/10 hover:bg-danger/20 text-danger text-[10px] font-black uppercase tracking-widest rounded-xl transition-all border border-danger/10"
                  >
                    Temizle (Mesaj)
                  </button>
                  <button
                    onClick={clearAllSuggestions}
                    className="px-3 py-1.5 bg-warning/10 hover:bg-warning/20 text-warning text-[10px] font-black uppercase tracking-widest rounded-xl transition-all border border-warning/10"
                  >
                    Temizle (Öneri)
                  </button>
                </div>
              </div>

              {/* Sub Tabs */}
              <div className="flex bg-hover/50 rounded-2xl p-1 gap-1">
                <button
                  onClick={() => setMessagesSubTab('messages')}
                  className={cn(
                    "flex-1 py-2.5 md:py-3 px-3 md:px-6 rounded-xl text-xs md:text-sm font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2",
                    messagesSubTab === 'messages' ? "bg-primary text-white shadow-lg" : "text-textSec hover:text-white hover:bg-white/5"
                  )}
                >
                  <MessageSquare size={16} />
                  Mesajlar
                  {messages.length > 0 && (
                    <span className="bg-white/20 px-2 py-0.5 rounded-full text-[10px]">{messages.length}</span>
                  )}
                </button>
                <button
                  onClick={() => setMessagesSubTab('suggestions')}
                  className={cn(
                    "flex-1 py-2.5 md:py-3 px-3 md:px-6 rounded-xl text-xs md:text-sm font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2",
                    messagesSubTab === 'suggestions' ? "bg-warning text-background shadow-lg" : "text-textSec hover:text-white hover:bg-white/5"
                  )}
                >
                  <Lightbulb size={16} />
                  Öneriler
                  {suggestions.length > 0 && (
                    <span className="bg-white/20 px-2 py-0.5 rounded-full text-[10px]">{suggestions.length}</span>
                  )}
                </button>
              </div>

              {/* Messages Content */}
              {messagesSubTab === 'messages' ? (
                <div className="space-y-4">
                  {messages.length === 0 ? (
                    <div className="glass rounded-[2rem] p-8 md:p-12 text-center border border-white/5 bg-white/2">
                      <MessageSquare className="mx-auto text-textSec mb-4 opacity-20" size={32} />
                      <h3 className="text-lg font-black text-white uppercase tracking-tighter">Henüz mesaj yok</h3>
                      <p className="text-textSec text-xs mt-2">Kullanıcılar uygulamadan mesaj gönderdiğinde burada görünecek.</p>
                    </div>
                  ) : (
                    messages.map(msg => (
                      <div
                        key={msg.id}
                        className={cn(
                          "glass rounded-2xl p-6 border transition-all",
                          msg.status === 'unread' ? "border-primary/50 bg-primary/5" : "border-white/5"
                        )}
                      >
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex-1 space-y-3">
                            <div className="flex items-center gap-3">
                              {msg.status === 'unread' && (
                                <span className="bg-primary text-white text-[10px] font-black px-2 py-1 rounded-full uppercase">Yeni</span>
                              )}
                              <h4 className="text-lg font-bold text-white">{msg.subject || 'Konu belirtilmemiş'}</h4>
                            </div>
                            <div className="flex items-center gap-4 text-sm text-textSec">
                              <span className="font-semibold">{msg.name}</span>
                              <span>•</span>
                              <a href={`mailto:${msg.email}`} className="text-primary hover:underline">{msg.email}</a>
                              <span>•</span>
                              <span className="flex items-center gap-1">
                                <Clock size={14} />
                                {msg.date} {msg.time}
                              </span>
                            </div>
                            <p className="text-sm text-textMain leading-relaxed bg-hover/50 rounded-xl p-4">{msg.message}</p>
                          </div>
                          <div className="flex flex-col gap-2">
                            {msg.status === 'unread' && (
                              <button
                                onClick={() => markMessageAsRead(msg.id)}
                                className="p-2.5 hover:bg-primary/20 text-primary rounded-xl transition-all"
                                title="Okundu olarak işaretle"
                              >
                                <Check size={18} />
                              </button>
                            )}
                            <button
                              onClick={() => deleteMessage(msg.id)}
                              className="p-2.5 hover:bg-danger/20 text-danger rounded-xl transition-all"
                              title="Sil"
                            >
                              <Trash2 size={18} />
                            </button>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              ) : (
                <div className="space-y-4">
                  {suggestions.length === 0 ? (
                    <div className="glass rounded-[2rem] p-12 text-center">
                      <Lightbulb className="mx-auto text-textSec mb-4" size={48} />
                      <h3 className="text-xl font-bold text-white">Henüz öneri yok</h3>
                      <p className="text-textSec mt-2">Kullanıcılar sticker önerisi gönderdiğinde burada görünecek.</p>
                    </div>
                  ) : (
                    suggestions.map(sugg => (
                      <div
                        key={sugg.id}
                        className="glass rounded-2xl p-6 border border-white/5 transition-all hover:border-warning/30"
                      >
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex-1 space-y-2">
                            <div className="flex items-center gap-3">
                              <div className="bg-warning/20 p-2 rounded-xl">
                                <Lightbulb className="text-warning" size={20} />
                              </div>
                              <span className="text-sm text-textSec flex items-center gap-1">
                                <Clock size={14} />
                                {sugg.date} {sugg.time}
                              </span>
                            </div>
                            <p className="text-base text-white font-medium leading-relaxed bg-hover/50 rounded-xl p-4">{sugg.suggestion}</p>
                          </div>
                          <button
                            onClick={() => deleteSuggestion(sugg.id)}
                            className="p-2.5 hover:bg-danger/20 text-danger rounded-xl transition-all"
                            title="Sil"
                          >
                            <Trash2 size={18} />
                          </button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}
            </div>
          </div>
        ) : activeTab === 'users' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-12 custom-scrollbar bg-background">
            <div className="max-w-7xl mx-auto space-y-8 md:space-y-12">
              {/* Users Header */}
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 px-4">
                <div className="flex items-center gap-5">
                  <div className="w-16 h-16 bg-gradient-to-br from-primary/20 to-accent/20 rounded-2xl flex items-center justify-center shadow-inner">
                    <Users size={36} className="text-primary" />
                  </div>
                  <div>
                    <h1 className="text-3xl font-black text-white tracking-tight">Kullanicilar</h1>
                    <p className="text-textSec text-sm font-medium mt-1">Firebase kullanici yonetimi</p>
                  </div>
                </div>
                <button
                  onClick={fetchUsers}
                  disabled={usersLoading}
                  className="flex items-center gap-3 px-6 py-3 bg-primary/10 hover:bg-primary/20 text-primary border border-primary/20 rounded-2xl text-xs font-black uppercase tracking-widest transition-all"
                >
                  <RefreshCcw size={16} className={usersLoading ? "animate-spin" : ""} />
                  Yenile
                </button>
              </div>

              {/* Stats Cards */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 px-4">
                <div className="bg-card/60 backdrop-blur-sm px-5 py-4 rounded-2xl border border-white/5">
                  <span className="text-[10px] font-bold text-textSec uppercase tracking-widest">Toplam</span>
                  <p className="text-2xl font-black text-white mt-1">{userStats.total.toLocaleString()}</p>
                </div>
                <div className="bg-card/60 backdrop-blur-sm px-5 py-4 rounded-2xl border border-primary/10">
                  <span className="text-[10px] font-bold text-primary uppercase tracking-widest">Premium</span>
                  <p className="text-2xl font-black text-primary mt-1">{userStats.premium.toLocaleString()}</p>
                </div>
                <div className="bg-card/60 backdrop-blur-sm px-5 py-4 rounded-2xl border border-accent/10">
                  <span className="text-[10px] font-bold text-accent uppercase tracking-widest">Abonelik</span>
                  <p className="text-2xl font-black text-accent mt-1">{userStats.subscription.toLocaleString()}</p>
                </div>

              </div>

              {/* Search & Filter Bar */}
              <div className="flex flex-col sm:flex-row gap-4 px-4">
                <div className="flex-1 relative">
                  <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-textSec" size={20} />
                  <input
                    type="text"
                    value={userSearch}
                    onChange={(e) => setUserSearch(e.target.value)}
                    placeholder="E-posta ara..."
                    className="w-full h-14 bg-hover border border-white/5 rounded-xl pl-12 pr-5 text-white font-bold placeholder:text-textSec/30 outline-none focus:border-primary/50 transition-all"
                  />
                </div>
                <select
                  value={userFilter}
                  onChange={(e) => setUserFilter(e.target.value as any)}
                  className="h-14 bg-hover border border-white/5 rounded-xl px-4 text-white font-bold outline-none focus:border-primary/50 transition-all appearance-none min-w-[160px]"
                >
                  <option value="all">Tumunu Goster</option>
                  <option value="premium">Premium</option>
                  <option value="free">Ucretsiz</option>
                  <option value="subscription">Abonelik</option>
                </select>
              </div>

              {/* Loading State */}
              {usersLoading && (
                <div className="flex flex-col items-center justify-center py-20">
                  <RefreshCcw size={48} className="animate-spin text-primary mb-4" />
                  <p className="text-white font-black uppercase tracking-widest text-sm">Kullanicilar yukleniyor...</p>
                </div>
              )}

              {/* Users Content */}
              {!usersLoading && (
                <div className="flex flex-col lg:flex-row gap-6 px-4">
                  {/* Users Table */}
                  <div className="flex-1 bg-card/40 backdrop-blur-sm rounded-[32px] border border-white/5 overflow-hidden">
                    {/* Table Header */}
                    <div className="hidden md:grid grid-cols-12 gap-4 px-6 py-4 border-b border-white/5 bg-white/[0.02]">
                      <span className="col-span-5 text-[10px] font-black text-textSec uppercase tracking-widest">E-posta</span>
                      <span className="col-span-2 text-[10px] font-black text-textSec uppercase tracking-widest">Durum</span>
                      <span className="col-span-2 text-[10px] font-black text-textSec uppercase tracking-widest">Tur</span>
                      <span className="col-span-3 text-[10px] font-black text-textSec uppercase tracking-widest">Bitis</span>
                    </div>

                    {/* Table Body */}
                    <div className="max-h-[600px] overflow-y-auto custom-scrollbar divide-y divide-white/5">
                      {filteredUsers.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-20 text-center space-y-4">
                          <div className="w-20 h-20 bg-white/5 rounded-full flex items-center justify-center text-textSec/20">
                            <Users size={40} />
                          </div>
                          <p className="text-textSec text-sm font-bold">Kullanici bulunamadi</p>
                        </div>
                      ) : (
                        filteredUsers.map((u) => (
                          <button
                            key={u.id}
                            onClick={() => {
                              setSelectedUser(u);
                              setEditingSubscription(false);
                            }}
                            className={cn(
                              "w-full grid grid-cols-1 md:grid-cols-12 gap-2 md:gap-4 px-6 py-4 text-left transition-all hover:bg-white/[0.03]",
                              selectedUser?.id === u.id && "bg-primary/5 border-l-2 border-primary"
                            )}
                          >
                            <div className="col-span-5 flex items-center gap-3 min-w-0">
                              <div className={cn(
                                "w-8 h-8 rounded-lg flex items-center justify-center shrink-0",
                                u.is_premium ? "bg-primary/20 text-primary" : "bg-white/5 text-textSec"
                              )}>
                                <UserIcon size={16} />
                              </div>
                              <span className="text-sm font-bold text-white truncate">{u.email || u.id}</span>
                            </div>
                            <div className="col-span-2 flex items-center">
                              {u.is_premium ? (
                                <span className="flex items-center gap-1.5 text-xs font-black text-primary">
                                  <Crown size={14} />
                                  Premium
                                </span>
                              ) : (
                                <span className="text-xs font-bold text-textSec">Free</span>
                              )}
                            </div>
                            <div className="col-span-2 flex items-center">
                              <span className={cn(
                                "text-xs font-bold px-2 py-1 rounded-lg",
                                u.premium_type === 'subscription' ? "bg-accent/10 text-accent" : "text-textSec"
                              )}>
                                {u.premium_type === 'subscription' ? 'Abonelik' : '-'}
                              </span>
                            </div>
                            <div className="col-span-3 flex items-center">
                              <span className="text-xs font-bold text-textSec">
                                {u.premium_expiry ? new Date(u.premium_expiry).toLocaleDateString('tr-TR') : '-'}
                              </span>
                            </div>
                          </button>
                        ))
                      )}
                    </div>

                    {/* Table Footer */}
                    <div className="px-6 py-3 border-t border-white/5 bg-white/[0.02]">
                      <span className="text-[10px] font-black text-textSec uppercase tracking-widest">
                        {filteredUsers.length} / {usersData.length} kullanici
                      </span>
                    </div>
                  </div>

                  {/* User Detail Panel */}
                  {selectedUser && (
                    <div className="lg:w-[420px] bg-card/40 backdrop-blur-sm rounded-[32px] border border-white/5 overflow-hidden shrink-0">
                      {/* Detail Header */}
                      <div className="p-6 border-b border-white/5 flex items-center justify-between">
                        <h3 className="text-lg font-black text-white tracking-tight">Kullanici Detay</h3>
                        <button
                          onClick={() => setSelectedUser(null)}
                          className="p-2 hover:bg-white/10 rounded-xl transition-all text-textSec"
                        >
                          <X size={18} />
                        </button>
                      </div>

                      <div className="p-6 space-y-5">
                        {/* Email */}
                        <div className="space-y-1">
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">E-posta</span>
                          <p className="text-sm font-bold text-white break-all">{selectedUser.email || selectedUser.id}</p>
                        </div>

                        {/* UID */}
                        <div className="space-y-1">
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">UID</span>
                          <p className="text-xs font-mono text-textSec break-all">{selectedUser.id}</p>
                        </div>

                        {/* Premium Status */}
                        <div className="space-y-1">
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Premium</span>
                          <div className="flex items-center gap-2">
                            {selectedUser.is_premium ? (
                              <span className="flex items-center gap-2 px-3 py-1.5 bg-primary/10 text-primary rounded-lg text-xs font-black">
                                <Crown size={14} /> Evet
                              </span>
                            ) : (
                              <span className="flex items-center gap-2 px-3 py-1.5 bg-white/5 text-textSec rounded-lg text-xs font-bold">
                                <Shield size={14} /> Hayir
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Type */}
                        <div className="space-y-1">
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Tur</span>
                          <p className={cn(
                            "text-sm font-black",
                            selectedUser.premium_type === 'subscription' ? "text-accent" : "text-textSec"
                          )}>
                            {selectedUser.premium_type === 'subscription' ? 'Abonelik' : 'Yok'}
                          </p>
                        </div>

                        {/* Expiry */}
                        <div className="space-y-1">
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Bitis Tarihi</span>
                          <p className="text-sm font-bold text-white">
                            {selectedUser.premium_expiry ? new Date(selectedUser.premium_expiry).toLocaleDateString('tr-TR', { year: 'numeric', month: 'long', day: 'numeric' }) : '-'}
                          </p>
                        </div>

                        {/* Last Sync */}
                        <div className="space-y-1">
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Son Senkronizasyon</span>
                          <p className="text-sm font-bold text-textSec">
                            {selectedUser.last_sync?.toDate ? selectedUser.last_sync.toDate().toLocaleString('tr-TR') : selectedUser.last_sync ? new Date(selectedUser.last_sync).toLocaleString('tr-TR') : '-'}
                          </p>
                        </div>

                        {/* Favourite Packs */}
                        <div className="space-y-1">
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Favori Paketler</span>
                          <div className="flex flex-wrap gap-1.5">
                            {selectedUser.favorite_packs && selectedUser.favorite_packs.length > 0 ? (
                              selectedUser.favorite_packs.map((p, i) => (
                                <span key={i} className="px-2 py-1 bg-white/5 text-textSec text-[10px] font-bold rounded-lg">{p}</span>
                              ))
                            ) : (
                              <span className="text-textSec text-xs font-bold">-</span>
                            )}
                          </div>
                        </div>

                        {/* Cancelled Info */}
                        {selectedUser.cancelled_at && (
                          <div className="space-y-1 bg-danger/5 border border-danger/10 rounded-xl p-3">
                            <span className="text-[10px] font-black text-danger uppercase tracking-widest">Iptal Edildi</span>
                            <p className="text-xs font-bold text-textSec">{selectedUser.cancelled_reason || '-'}</p>
                          </div>
                        )}

                        <div className="border-t border-white/5 pt-5 space-y-3">

                          {/* Subscription History */}
                          {selectedUser.subscription_history && selectedUser.subscription_history.length > 0 && (
                            <div className="space-y-2 mb-4">
                              <label className="text-[10px] font-black text-textSec uppercase tracking-widest">Abonelik Gecmisi</label>
                              <div className="bg-black/20 rounded-xl max-h-32 overflow-y-auto">
                                {selectedUser.subscription_history.slice().reverse().map((item: any) => (
                                  <div key={item.id} className="p-2 border-b border-white/5 last:border-0 flex items-center justify-between">
                                    <div className="flex flex-col">
                                      <span className="text-[10px] font-bold text-white capitalize">{item.type === 'start' ? 'Baslangic' : item.type === 'cancel' ? 'Iptal' : item.type}</span>
                                      <span className="text-[9px] text-textSec">{item.date_str} • {item.source}</span>
                                    </div>
                                    <span className="text-[9px] font-medium text-textSec">{item.plan}</span>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* Edit Subscription */}
                          {!editingSubscription ? (
                            <div className="flex gap-3">
                              {/* Admin tarafindan eklenen aboneliklerde düzenleme aktif, Google Play ise pasif */}
                              <button
                                disabled={selectedUser.subscription_source !== 'admin'}
                                onClick={() => {
                                  if (selectedUser.subscription_source !== 'admin') return;
                                  setEditingSubscription(true);
                                  setSubPlan(selectedUser.premium_type === 'subscription' ? 'monthly' : 'none');
                                }}
                                className={cn(
                                  "flex-1 h-12 rounded-xl font-black text-xs uppercase tracking-widest flex items-center justify-center gap-2 transition-all",
                                  selectedUser.subscription_source !== 'admin'
                                    ? "bg-white/5 text-textSec cursor-not-allowed opacity-50"
                                    : "bg-primary/10 hover:bg-primary/20 text-primary"
                                )}
                              >
                                {selectedUser.subscription_source === 'admin' ? (
                                  <>
                                    <Edit3 size={14} />
                                    Abonelik Duzenle
                                  </>
                                ) : (
                                  <span className="text-[8px]">
                                    {selectedUser.subscription_source === 'google_play' ? 'Play Store' : 'Uygulama İçi'}
                                  </span>
                                )}
                              </button>

                              {selectedUser.is_premium && (
                                <button
                                  disabled={selectedUser.subscription_source !== 'admin'}
                                  onClick={() => handleRevokeSubscription(selectedUser.id)}
                                  className={cn(
                                    "h-12 px-4 rounded-xl font-black text-xs uppercase tracking-widest flex items-center justify-center gap-2 transition-all",
                                    selectedUser.subscription_source !== 'admin'
                                      ? "bg-white/5 text-textSec cursor-not-allowed opacity-50"
                                      : "bg-danger/10 hover:bg-danger/20 text-danger"
                                  )}
                                  title={selectedUser.subscription_source !== 'admin' ? "Sadece Admin tarafından verilen abonelikler iptal edilebilir" : "Iptal Et"}
                                >
                                  <X size={14} />
                                  {selectedUser.subscription_source !== 'admin' ? 'Kilitli' : 'Iptal Et'}
                                </button>
                              )}
                            </div>
                          ) : (
                            <div className="space-y-4 bg-white/[0.02] border border-white/5 rounded-2xl p-4">
                              <div className="space-y-2">
                                <label className="text-[10px] font-black text-textSec uppercase tracking-widest">Abonelik Plani</label>
                                <select
                                  value={subPlan}
                                  onChange={(e) => setSubPlan(e.target.value)}
                                  className="w-full h-12 bg-hover border border-white/5 rounded-xl px-4 text-white font-bold outline-none focus:border-primary/50 transition-all appearance-none"
                                >
                                  <option value="none">Yok (Free)</option>
                                  <option value="monthly">Aylik (Monthly)</option>
                                  <option value="yearly">Yillik (Yearly)</option>
                                </select>
                              </div>

                              <div className="flex gap-3">
                                <button
                                  onClick={() => handleUpdateSubscription(selectedUser.id, subPlan)}
                                  className="flex-1 h-10 bg-primary hover:bg-primary/80 text-white rounded-xl font-black text-xs uppercase tracking-widest flex items-center justify-center gap-2 transition-all"
                                >
                                  <Save size={14} />
                                  Kaydet
                                </button>
                                <button
                                  onClick={() => setEditingSubscription(false)}
                                  className="h-10 px-4 bg-white/5 hover:bg-white/10 text-textSec rounded-xl font-black text-xs uppercase tracking-widest transition-all"
                                >
                                  Vazgec
                                </button>
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div >
        ) : null
        }
      </main >

      <footer className="hidden md:flex glass h-8 px-6 items-center justify-between text-[10px] font-bold text-textSec uppercase tracking-widest border-t border-white/5 fixed bottom-0 left-0 right-0 z-30">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <div className="w-1.5 h-1.5 bg-primary rounded-full shadow-sm shadow-primary/50" />
            Firebase Bağlı: {selectedPack ? selectedPack.id : 'Hazır'}
          </div>
          <div className="flex items-center gap-2">
            <div className="w-1.5 h-1.5 bg-accent rounded-full" />
            Storage: Veri Akışı Aktif
          </div>
        </div>
        <div>
          v2.0 PRO • {new Date().toLocaleTimeString()}
        </div>
      </footer>

      {/* New Pack Modal */}
      <Modal show={showNewPackModal} onClose={() => setShowNewPackModal(false)} title="Yeni Paket Oluştur">
        <div className="space-y-6">
          <p className="text-sm text-textSec">StickyApp veritabanına doğrudan el ile yeni paket ekleyin.</p>

          {/* Çoklu Dil Desteği */}
          <div className="bg-gradient-to-r from-primary/10 to-transparent border border-primary/20 rounded-2xl p-4 space-y-4">
            <div className="flex items-center gap-2 mb-2">
              <Globe className="text-primary" size={20} />
              <span className="text-sm font-bold text-white">Çoklu Dil Desteği</span>
              <span className="text-xs text-textSec ml-auto">Uygulamada seçilen dile göre görünür</span>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2">
                {/* Header */}
                <div className="flex items-center justify-between mb-3">
                  <label className="flex items-center gap-2 text-primary font-bold text-sm">
                    <Globe size={18} />
                    Çoklu Dil Desteği ({TARGET_LANGUAGES.length} dil)
                  </label>
                  <button
                    onClick={() => handleAutoTranslate(false)}
                    disabled={isTranslating || !newPackData.name}
                    type="button"
                    className="px-4 py-2 bg-gradient-to-r from-primary to-accent text-white rounded-xl flex items-center gap-2 hover:opacity-90 transition-all font-bold text-sm disabled:opacity-40 disabled:cursor-not-allowed shadow-lg"
                  >
                    {isTranslating ? <RefreshCcw size={16} className="animate-spin" /> : <Sparkles size={16} />}
                    {isTranslating ? 'AI Çeviriyor...' : '✨ Otomatik Çevir'}
                  </button>
                </div>

                {/* Ana İsim (İngilizce) */}
                <div className="mb-3">
                  <label className="flex items-center gap-2 text-xs font-bold text-white/80 mb-1.5">
                    🇬🇧 İngilizce (Ana İsim) <span className="text-red-400">*</span>
                  </label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      placeholder="Örn: Funny Cats, Love Stickers..."
                      className="flex-1 bg-bgSecondary border-2 border-primary/50 rounded-xl p-3 text-white placeholder:text-white/30 text-base focus:border-primary outline-none transition-all"
                      value={newPackData.name}
                      onChange={(e) => setNewPackData({ ...newPackData, name: e.target.value, name_en: e.target.value })}
                    />
                    <button
                      type="button"
                      onClick={() => {
                        const newName = generateCreativeName(newPackData.name);
                        setNewPackData({ ...newPackData, name: newName, name_en: newName });
                      }}
                      className="px-4 bg-accent/10 border-2 border-accent/20 hover:bg-accent/20 hover:border-accent/50 text-accent rounded-xl transition-all flex items-center justify-center active:scale-95 group"
                      title="Yaratıcı İsim Öner"
                    >
                      <Wand2 size={24} className="group-hover:rotate-12 transition-transform" />
                    </button>
                  </div>
                  <p className="text-xs text-textSec mt-1">İngilizce ismi girin, ardından "Otomatik Çevir" butonuna tıklayın</p>
                </div>

                {/* Arama */}
                <div className="relative mb-3">
                  <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-textSec" />
                  <input
                    type="text"
                    placeholder="Dil ara... (Turkish, German, Japanese...)"
                    className="w-full bg-bgSecondary border border-white/10 rounded-xl py-2.5 pl-10 pr-4 text-sm text-white placeholder:text-white/30"
                    value={langSearch}
                    onChange={(e) => setLangSearch(e.target.value)}
                  />
                </div>

                {/* Doluluk Durumu */}
                <div className="flex items-center gap-4 mb-3 text-xs">
                  <span className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-green-500"></span>
                    Dolu: {TARGET_LANGUAGES.filter(l => (newPackData as any)[`name_${l.code}`]).length}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-white/20"></span>
                    Boş: {TARGET_LANGUAGES.filter(l => !(newPackData as any)[`name_${l.code}`]).length}
                  </span>
                </div>

                {/* Dil Listesi */}
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 max-h-[280px] overflow-y-auto pr-2 p-3 bg-black/20 rounded-xl border border-white/5">
                  {TARGET_LANGUAGES
                    .filter(lang => lang.code !== 'en') // İngilizce zaten yukarıda
                    .filter(lang =>
                      langSearch === '' ||
                      lang.name.toLowerCase().includes(langSearch.toLowerCase()) ||
                      lang.code.toLowerCase().includes(langSearch.toLowerCase())
                    )
                    .map((lang) => {
                      const value = (newPackData as any)[`name_${lang.code}`] || '';
                      const isFilled = value.length > 0;
                      return (
                        <div key={lang.code} className="space-y-1">
                          <label className={`flex items-center gap-1.5 text-xs font-medium ${isFilled ? 'text-green-400' : 'text-textSec'}`}>
                            <span>{lang.flag}</span> {lang.name}
                            {isFilled && <Check size={12} className="text-green-400" />}
                          </label>
                          <input
                            type="text"
                            placeholder={`${lang.name}...`}
                            className={`w-full bg-bgSecondary border rounded-lg p-2 text-sm text-white placeholder:text-white/20 ${isFilled ? 'border-green-500/30' : 'border-white/10'}`}
                            value={value}
                            onChange={(e) => setNewPackData({ ...newPackData, [`name_${lang.code}`]: e.target.value })}
                          />
                        </div>
                      );
                    })}
                </div>
              </div>
            </div>
          </div>
          <Input
            label="Yayıncı"
            placeholder="Sticky"
            value={newPackData.publisher}
            onChange={(e: any) => setNewPackData({ ...newPackData, publisher: e.target.value })}
          />
          <Input
            label="Yayıncı E-posta"
            value={newPackData.publisher_email}
            onChange={(e: any) => setNewPackData({ ...newPackData, publisher_email: e.target.value })}
          />
          <div>
            <label className="text-xs font-bold text-textSec uppercase mb-2 block">Kategori</label>
            <select
              className="w-full bg-hover rounded-xl px-4 py-2.5 text-sm outline-none border-none text-white cursor-pointer"
              value={newPackData.category}
              onChange={(e) => setNewPackData({ ...newPackData, category: e.target.value })}
            >
              {CATEGORIES.map(cat => (
                <option key={cat.id} value={cat.id}>{cat.emoji} {cat.name}</option>
              ))}
            </select>
          </div>
          <div className="flex bg-hover rounded-xl p-1 gap-1">
            <button
              onClick={() => setNewPackData({ ...newPackData, is_premium: false })}
              className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", !newPackData.is_premium ? "bg-primary text-white" : "text-textSec")}
            >NORMAL PAKET</button>
            <button
              onClick={() => setNewPackData({ ...newPackData, is_premium: true })}
              className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", newPackData.is_premium ? "bg-warning text-background" : "text-textSec")}
            >PREMIUM PAKET</button>
          </div>



          <div className="flex bg-hover rounded-xl p-1 gap-1">
            <button
              onClick={() => setNewPackData({ ...newPackData, is_animated: false })}
              className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", !newPackData.is_animated ? "bg-blue-500 text-white" : "text-textSec")}
            >STATİK PAKET</button>
            <button
              onClick={() => setNewPackData({ ...newPackData, is_animated: true })}
              className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", newPackData.is_animated ? "bg-purple-500 text-white" : "text-textSec")}
            >HAREKETLİ PAKET</button>
          </div>
          <div className="bg-primary/5 border border-primary/20 p-4 rounded-xl flex items-center gap-3">
            <Info className="text-primary" size={20} />
            <span className="text-xs text-textMain/70 uppercase font-bold">
              {newPackData.is_animated
                ? "Hareketli paket: GIF, Video ve Animasyonlu WebP destekler"
                : "Statik paket: PNG, JPG ve Statik WebP destekler"}
            </span>
          </div>
          <button
            onClick={handleCreatePack}
            disabled={isProcessing}
            className="w-full bg-primary py-4 rounded-2xl font-black shadow-lg shadow-primary/20 hover:scale-[1.01] active:scale-95 transition-all disabled:opacity-50 text-white"
          >
            {isProcessing ? 'PAKET OLUŞTURULUYOR...' : 'OLUŞTUR VE BAŞLA'}
          </button>
        </div>
      </Modal>

      {/* Edit Pack Modal */}
      <Modal show={showEditPackModal} onClose={() => setShowEditPackModal(false)} title="Uygulama Bilgilerini Düzenle">
        {selectedPack && (
          <div className="space-y-6">
            {/* Çoklu Dil Desteği */}
            <div className="bg-gradient-to-r from-primary/10 to-transparent border border-primary/20 rounded-2xl p-4 space-y-4">
              <div className="flex items-center gap-2 mb-2">
                <Globe className="text-primary" size={20} />
                <span className="text-sm font-bold text-white">Çoklu Dil Desteği</span>
                <span className="text-xs text-textSec ml-auto">Uygulamada seçilen dile göre görünür</span>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  {/* Header */}
                  <div className="flex items-center justify-between mb-3">
                    <label className="flex items-center gap-2 text-primary font-bold text-sm">
                      <Globe size={18} />
                      Çoklu Dil Desteği ({TARGET_LANGUAGES.length} dil)
                    </label>
                    <button
                      onClick={() => handleAutoTranslate(true)}
                      disabled={isTranslating || !editFormData.name}
                      type="button"
                      className="px-4 py-2 bg-gradient-to-r from-primary to-accent text-white rounded-xl flex items-center gap-2 hover:opacity-90 transition-all font-bold text-sm disabled:opacity-40 disabled:cursor-not-allowed shadow-lg"
                    >
                      {isTranslating ? <RefreshCcw size={16} className="animate-spin" /> : <Sparkles size={16} />}
                      {isTranslating ? 'AI Çeviriyor...' : '✨ Otomatik Çevir'}
                    </button>
                  </div>

                  {/* Ana İsim (İngilizce) */}
                  <div className="mb-3">
                    <label className="flex items-center gap-2 text-xs font-bold text-white/80 mb-1.5">
                      🇬🇧 İngilizce (Ana İsim) <span className="text-red-400">*</span>
                    </label>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        placeholder="Örn: Funny Cats, Love Stickers..."
                        className="flex-1 bg-bgSecondary border-2 border-primary/50 rounded-xl p-3 text-white placeholder:text-white/30 text-base focus:border-primary outline-none transition-all"
                        value={editFormData.name || ''}
                        onChange={(e) => setEditFormData({ ...editFormData, name: e.target.value, name_en: e.target.value })}
                      />
                      <button
                        type="button"
                        onClick={() => {
                          const newName = generateCreativeName(editFormData.name || "");
                          setEditFormData({ ...editFormData, name: newName, name_en: newName });
                        }}
                        className="px-4 bg-accent/10 border-2 border-accent/20 hover:bg-accent/20 hover:border-accent/50 text-accent rounded-xl transition-all flex items-center justify-center active:scale-95 group"
                        title="Yaratıcı İsim Öner"
                      >
                        <Wand2 size={24} className="group-hover:rotate-12 transition-transform" />
                      </button>
                    </div>
                  </div>

                  {/* Arama */}
                  <div className="relative mb-3">
                    <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-textSec" />
                    <input
                      type="text"
                      placeholder="Dil ara... (Turkish, German, Japanese...)"
                      className="w-full bg-bgSecondary border border-white/10 rounded-xl py-2.5 pl-10 pr-4 text-sm text-white placeholder:text-white/30"
                      value={langSearch}
                      onChange={(e) => setLangSearch(e.target.value)}
                    />
                  </div>

                  {/* Doluluk Durumu */}
                  <div className="flex items-center gap-4 mb-3 text-xs">
                    <span className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-green-500"></span>
                      Dolu: {TARGET_LANGUAGES.filter(l => (editFormData as any)[`name_${l.code}`]).length}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-white/20"></span>
                      Boş: {TARGET_LANGUAGES.filter(l => !(editFormData as any)[`name_${l.code}`]).length}
                    </span>
                  </div>

                  {/* Dil Listesi */}
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 max-h-[280px] overflow-y-auto pr-2 p-3 bg-black/20 rounded-xl border border-white/5">
                    {TARGET_LANGUAGES
                      .filter(lang => lang.code !== 'en')
                      .filter(lang =>
                        langSearch === '' ||
                        lang.name.toLowerCase().includes(langSearch.toLowerCase()) ||
                        lang.code.toLowerCase().includes(langSearch.toLowerCase())
                      )
                      .map((lang) => {
                        const value = (editFormData as any)[`name_${lang.code}`] || '';
                        const isFilled = value.length > 0;
                        return (
                          <div key={lang.code} className="space-y-1">
                            <label className={`flex items-center gap-1.5 text-xs font-medium ${isFilled ? 'text-green-400' : 'text-textSec'}`}>
                              <span>{lang.flag}</span> {lang.name}
                              {isFilled && <Check size={12} className="text-green-400" />}
                            </label>
                            <input
                              type="text"
                              placeholder={`${lang.name}...`}
                              className={`w-full bg-bgSecondary border rounded-lg p-2 text-sm text-white placeholder:text-white/20 ${isFilled ? 'border-green-500/30' : 'border-white/10'}`}
                              value={value}
                              onChange={(e) => setEditFormData({ ...editFormData, [`name_${lang.code}`]: e.target.value })}
                            />
                          </div>
                        );
                      })}
                  </div>
                </div>
              </div>
            </div>
            <Input
              label="Yayıncı"
              value={editFormData.publisher}
              onChange={(e: any) => setEditFormData({ ...editFormData, publisher: e.target.value })}
            />
            <Input
              label="Yayıncı E-posta"
              value={editFormData.publisher_email}
              onChange={(e: any) => setEditFormData({ ...editFormData, publisher_email: e.target.value })}
            />
            <div className="grid grid-cols-2 gap-4">
              <Input
                label="Gizlilik Politikası Link"
                value={editFormData.privacy_policy_website}
                onChange={(e: any) => setEditFormData({ ...editFormData, privacy_policy_website: e.target.value })}
              />
              <Input
                label="Lisans Sözleşmesi Link"
                value={editFormData.license_agreement_website}
                onChange={(e: any) => setEditFormData({ ...editFormData, license_agreement_website: e.target.value })}
              />
            </div>
            <div className="flex items-center gap-4">
              <div className="flex-1">
                <label className="text-xs font-bold text-textSec uppercase mb-2 block">Kategori</label>
                <select
                  className="w-full bg-hover rounded-xl px-4 py-2.5 text-sm outline-none border-none text-white cursor-pointer"
                  value={editFormData.category}
                  onChange={(e) => setEditFormData({ ...editFormData, category: e.target.value })}
                >
                  {CATEGORIES.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.emoji} {cat.name}</option>
                  ))}
                </select>
              </div>
              <div className="flex-1">
                <label className="text-xs font-bold text-textSec uppercase mb-2 block">Paket Tipi</label>
                <div className="flex bg-hover rounded-xl p-1 gap-1">
                  <button
                    onClick={() => setEditFormData({ ...editFormData, is_premium: false })}
                    className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", !editFormData.is_premium ? "bg-primary text-white" : "text-textSec")}
                  >NORMAL</button>
                  <button
                    onClick={() => setEditFormData({ ...editFormData, is_premium: true })}
                    className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", editFormData.is_premium ? "bg-warning text-background" : "text-textSec")}
                  >PREMIUM</button>
                </div>
              </div>
            </div>

            {/* Product ID alanı kaldırıldı - Artık tekli satın alım yok, premium abonelik tüm premium paketleri açıyor */}

            <div className="flex items-center gap-4">
              <div className="flex-1">
                <label className="text-xs font-bold text-textSec uppercase mb-2 block">Paket Türü</label>
                <div className="bg-primary/10 border border-primary/20 p-2.5 rounded-xl flex items-center justify-center gap-2">
                  <RefreshCcw className="text-primary animate-spin" size={14} />
                  <span className="text-[10px] text-primary font-black uppercase">HAREKETLİ (ZORUNLU)</span>
                </div>
              </div>
              <div className="flex-1">
                <label className="text-xs font-bold text-textSec uppercase mb-2 block">Durum (Görünürlük)</label>
                <div className="flex bg-hover rounded-xl p-1 gap-1">
                  <button
                    onClick={() => setEditFormData({ ...editFormData, is_active: true })}
                    className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", editFormData.is_active !== false ? "bg-primary text-white" : "text-textSec")}
                  >AKTİF</button>
                  <button
                    onClick={() => setEditFormData({ ...editFormData, is_active: false })}
                    className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", editFormData.is_active === false ? "bg-danger text-white" : "text-textSec")}
                  >PASİF (GİZLİ)</button>
                </div>
              </div>
            </div>
            <button
              onClick={handleUpdatePack}
              disabled={isProcessing}
              className="w-full bg-primary py-4 rounded-2xl font-black shadow-lg shadow-primary/20 flex items-center justify-center gap-2 hover:scale-[1.01] active:scale-95 transition-all disabled:opacity-50 text-white"
            >
              {isProcessing ? 'KAYDEDİLİYOR...' : (
                <>
                  <Save size={20} /> DEĞİŞİKLİKLERİ KAYDET
                </>
              )}
            </button>
          </div>
        )}
      </Modal>

      {/* Processing Overlay */}
      {
        isProcessing && uploadProgress && (
          <div className="fixed inset-0 z-[100] bg-background/90 backdrop-blur-xl flex flex-col items-center justify-center space-y-8 animate-in fade-in duration-300 p-6">
            <div className="relative group">
              <div className="absolute inset-0 bg-primary/20 blur-[60px] rounded-full animate-pulse" />
              <div className="relative bg-card/60 p-8 rounded-[3rem] border border-white/10 shadow-2xl backdrop-blur-3xl">
                <RefreshCcw className="text-primary animate-spin" size={60} />
              </div>
            </div>

            <div className="text-center space-y-4 max-w-lg">
              <h3 className="text-3xl font-black text-white tracking-tight uppercase">Çıkartmalar İşleniyor</h3>
              <div className="bg-white/5 px-6 py-2 rounded-2xl border border-white/5 inline-block">
                <p className="text-primary font-black uppercase tracking-[0.2em] text-[10px]">
                  {uploadProgress.message || 'Medyalar WhatsApp formatına dönüştürülüyor...'}
                </p>
              </div>
            </div>

            <div className="w-full max-w-md space-y-4">
              <div className="flex items-center justify-between text-[10px] font-black text-textSec uppercase tracking-widest px-1">
                <span>İşlem: {uploadProgress.current} / {uploadProgress.total}</span>
                <span className="text-primary">{Math.round((uploadProgress.current / uploadProgress.total) * 100)}%</span>
              </div>
              <div className="w-full bg-white/5 h-3 rounded-full overflow-hidden border border-white/5 p-1">
                <div
                  className="h-full bg-gradient-to-r from-primary via-accent to-primary shadow-[0_0_20px_rgba(0,168,132,0.6)] transition-all duration-700 ease-out rounded-full"
                  style={{ width: `${(uploadProgress.current / uploadProgress.total) * 100}%` }}
                />
              </div>
            </div>

            <div className="bg-primary/5 border border-primary/20 p-5 rounded-3xl flex items-center gap-4 max-w-sm">
              <div className="bg-primary/20 p-2 rounded-xl text-primary">
                <Info size={20} />
              </div>
              <p className="text-[10px] text-textMain/70 font-bold uppercase leading-relaxed text-left">
                Video ve GIF işlemleri işlemci gücü gerektirir. Lütfen işlemi bölmeyin.
              </p>
            </div>
          </div>
        )
      }

      {/* Automation/Delete Progress Overlay */}
      {
        isProcessing && deleteProgress && (
          <div className="fixed inset-0 z-[100] bg-background/95 backdrop-blur-2xl flex flex-col items-center justify-center space-y-10 animate-in fade-in duration-500 p-8 text-center">
            <div className="relative scale-110">
              <div className="absolute inset-0 bg-primary/30 blur-[100px] rounded-full animate-pulse" />
              <div className="relative bg-card p-10 rounded-[3.5rem] border border-white/10 shadow-3xl">
                <CloudLightning className="text-primary animate-bounce" size={72} />
              </div>
            </div>

            <div className="space-y-6 max-w-xl">
              <div className="space-y-2">
                <h3 className="text-4xl font-black text-white tracking-tighter uppercase italic underline decoration-primary/50 underline-offset-8">
                  Sistem Aktarımı
                </h3>
                <p className="text-textSec font-bold text-sm tracking-wide">
                  Taslak paket ana sunucuya taşınıyor...
                </p>
              </div>

              <div className="bg-primary/10 border border-primary/20 px-8 py-3 rounded-full inline-flex items-center gap-3">
                <span className="w-2 h-2 bg-primary rounded-full animate-ping" />
                <p className="text-primary font-black uppercase tracking-widest text-xs">
                  {deleteProgress.message}
                </p>
              </div>
            </div>

            <div className="w-full max-w-lg space-y-5 bg-card/40 p-8 rounded-[2.5rem] border border-white/5 shadow-inner">
              <div className="flex items-center justify-between text-[11px] font-black text-white uppercase tracking-[0.2em] px-2">
                <div className="flex items-center gap-2">
                  <div className="w-1.5 h-1.5 bg-primary rounded-full" />
                  <span>TAŞINAN: {deleteProgress.current} Çıkartma</span>
                </div>
                <div className="bg-white/10 px-3 py-1 rounded-lg">
                  <span className="text-primary">{deleteProgress.total > 0 ? Math.round((deleteProgress.current / deleteProgress.total) * 100) : 0}%</span>
                </div>
              </div>

              <div className="w-full bg-white/5 h-4 rounded-full overflow-hidden border border-white/10 p-1.5">
                <div
                  className="h-full bg-gradient-to-r from-primary via-emerald-400 to-primary shadow-[0_0_30px_rgba(0,168,132,0.5)] transition-all duration-500 ease-out rounded-full"
                  style={{ width: `${deleteProgress.total > 0 ? (deleteProgress.current / deleteProgress.total) * 100 : 0}%` }}
                />
              </div>

              <div className="pt-2">
                <p className="text-[10px] text-textSec font-bold uppercase italic opacity-40">
                  Bu işlem tamamlandığında paket uygulama içerisinde yayınlanacaktır.
                </p>
              </div>
            </div>
          </div>
        )
      }

      {/* Sticker Preview Modal */}
      {
        previewSticker && (
          <div
            className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/90 backdrop-blur-xl animate-in fade-in duration-300"
            onClick={() => setPreviewSticker(null)}
          >
            <div className="absolute top-6 right-6 flex items-center gap-4">
              <span className="text-white/40 font-mono text-xs uppercase tracking-[0.3em] font-black">{previewSticker.title}</span>
              <button className="p-3 bg-white/10 hover:bg-white/20 text-white rounded-full transition-all">
                <X size={24} />
              </button>
            </div>
            <div className="relative group max-w-[90vw] max-h-[90vh]">
              <img
                src={previewSticker.url}
                alt=""
                className="max-w-full max-h-[80vh] object-contain drop-shadow-[0_0_50px_rgba(0,168,132,0.3)] animate-in zoom-in-90 duration-300"
                onClick={(e) => e.stopPropagation()}
              />
              <div className="absolute -bottom-12 left-1/2 -translate-x-1/2 text-primary font-black text-sm tracking-widest uppercase opacity-0 group-hover:opacity-100 transition-opacity">
                STICKER ÖNİZLEME
              </div>
            </div>
          </div>
        )
      }

      {/* Video/GIF Background Removal Modal */}
      <Modal show={showVideoBgModal} onClose={() => setShowVideoBgModal(false)} title="Hareketli Medya İşleme">
        <div className="space-y-6">
          <div className="bg-primary/10 border border-primary/20 p-6 rounded-2xl flex items-center gap-4">
            <div className="bg-primary/20 p-3 rounded-xl animate-pulse">
              <CloudLightning className="text-primary" size={28} />
            </div>
            <div>
              <h3 className="text-lg font-black text-white">Arka Plan Temizlensin mi?</h3>
              <p className="text-textSec text-xs mt-1">Yüklediğiniz video veya GIF'in arka planı yapay zeka ile otomatik olarak silinebilir.</p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <button
              onClick={() => handleVideoProcessingChoice(false)}
              className="bg-card hover:bg-hover border-2 border-white/5 rounded-2xl p-6 transition-all group flex flex-col items-center gap-3 active:scale-95"
            >
              <div className="w-12 h-12 rounded-full bg-white/5 flex items-center justify-center group-hover:bg-white/10 transition-colors">
                <ImageIcon size={24} className="text-textSec group-hover:text-white" />
              </div>
              <div className="text-center">
                <span className="block font-black text-sm text-white group-hover:text-textMain">HAYIR</span>
                <span className="text-[10px] text-textSec">Olduğu gibi kalsın</span>
              </div>
            </button>

            <button
              onClick={() => handleVideoProcessingChoice(true)}
              className="bg-primary hover:bg-primary/90 rounded-2xl p-6 transition-all group flex flex-col items-center gap-3 shadow-xl shadow-primary/20 hover:shadow-primary/40 active:scale-95 border-2 border-transparent"
            >
              <div className="w-12 h-12 rounded-full bg-white/20 flex items-center justify-center animate-bounce">
                <Wand2 size={24} className="text-white" />
              </div>
              <div className="text-center">
                <span className="block font-black text-sm text-white">EVET, TEMİZLE</span>
                <span className="text-[10px] text-white/70">Yapay Zeka ile Sil</span>
              </div>
            </button>
          </div>

          <p className="text-[10px] text-center text-textSec opacity-60">
            Not: Arka plan silme işlemi dosyanın uzunluğuna göre biraz zaman alabilir.
          </p>
        </div>
      </Modal>

    </div >
  );
}

export default App;
