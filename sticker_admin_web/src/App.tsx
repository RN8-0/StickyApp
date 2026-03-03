import { useState, useEffect, useMemo, useRef } from 'react';
import { createPortal } from 'react-dom';
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
  ChevronRight,
  TrendingUp,
  BarChart3,
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
  Wand2,
  Calendar,
  Smartphone,
  Zap,
  List,
  Star,
  AlertTriangle
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
import { importStickers, type StickerImportProgress } from './utils/stickerImporter';
import { generateBatchPacks, getCategoryStats, type BatchProgress, type BatchSource } from './utils/batchPackGenerator';
import { deepseekService } from './utils/deepseekService';
import { importTelegramPacks, validateBotToken } from './utils/telegramImporter';

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

// Alakalı kelime önerileri - her tema için uygun kelimeler
const RELEVANT_SUGGESTIONS: Record<string, { words: string[], emojis: string[] }> = {
  // Hayvanlar
  cat: { words: ["Cute", "Funny", "Meow", "Kitty", "Paws"], emojis: ["🐱", "😻", "🐾", "😸", "😹"] },
  dog: { words: ["Woof", "Puppy", "Cute", "Funny", "Happy"], emojis: ["🐶", "🐕", "🦮", "🐾", "🐩"] },
  bird: { words: ["Tweet", "Flying", "Cute", "Chirp", "Happy"], emojis: ["🐦", "🐤", "🦜", "🦅", "🐧"] },
  animal: { words: ["Wild", "Cute", "Funny", "Safari", "Nature"], emojis: ["🦁", "🐻", "🦊", "🐼", "🐨"] },
  bear: { words: ["Cute", "Cozy", "Fluffy", "Hugs", "Soft"], emojis: ["🐻", "🧸", "🐻‍❄️", "🤗", "🐾"] },
  bunny: { words: ["Cute", "Fluffy", "Hop", "Sweet", "Soft"], emojis: ["🐰", "🐇", "💕", "🥕", "✨"] },
  rabbit: { words: ["Cute", "Fluffy", "Hop", "Sweet", "Soft"], emojis: ["🐰", "🐇", "💕", "🥕", "✨"] },
  fox: { words: ["Sly", "Cute", "Wild", "Clever", "Orange"], emojis: ["🦊", "🧡", "🍂", "✨", "🌲"] },

  // Duygular
  love: { words: ["Sweet", "Hearts", "Forever", "Romance", "Kiss"], emojis: ["❤️", "💕", "💖", "😍", "💘"] },
  happy: { words: ["Joy", "Smile", "Fun", "Cheer", "Bright"], emojis: ["😊", "🎉", "✨", "🌟", "😁"] },
  sad: { words: ["Tears", "Blue", "Cry", "Moody", "Feels"], emojis: ["😢", "💔", "😭", "🥺", "💙"] },
  angry: { words: ["Rage", "Fire", "Mad", "Fury", "Hot"], emojis: ["😠", "🔥", "💢", "😤", "⚡"] },
  funny: { words: ["Lol", "Haha", "Comedy", "Jokes", "Memes"], emojis: ["😂", "🤣", "😆", "😜", "🤡"] },
  cute: { words: ["Kawaii", "Sweet", "Lovely", "Adorable", "Tiny"], emojis: ["🥰", "💕", "✨", "🎀", "💖"] },

  // Aktiviteler
  game: { words: ["Play", "Win", "Epic", "Pro", "Level"], emojis: ["🎮", "🕹️", "👾", "🏆", "⚡"] },
  gaming: { words: ["Play", "Win", "Epic", "Pro", "Level"], emojis: ["🎮", "🕹️", "👾", "🏆", "⚡"] },
  music: { words: ["Beat", "Vibes", "Dance", "Groove", "Sound"], emojis: ["🎵", "🎶", "🎧", "🎤", "🎸"] },
  food: { words: ["Yummy", "Tasty", "Delish", "Hungry", "Chef"], emojis: ["🍔", "🍕", "🍩", "😋", "🍳"] },
  sport: { words: ["Win", "Play", "Goal", "Team", "Champ"], emojis: ["⚽", "🏀", "🏆", "💪", "🎯"] },
  sports: { words: ["Win", "Play", "Goal", "Team", "Champ"], emojis: ["⚽", "🏀", "🏆", "💪", "🎯"] },
  movie: { words: ["Cinema", "Scene", "Star", "Film", "Show"], emojis: ["🎬", "🎥", "🍿", "⭐", "🎭"] },

  // Zaman/Günler
  morning: { words: ["Rise", "Fresh", "Sunny", "Early", "Coffee"], emojis: ["☀️", "🌅", "☕", "🌤️", "🌻"] },
  night: { words: ["Sleep", "Dream", "Stars", "Moon", "Rest"], emojis: ["🌙", "⭐", "💤", "🌟", "🌛"] },
  birthday: { words: ["Party", "Cake", "Wish", "Gift", "Cheers"], emojis: ["🎂", "🎉", "🎁", "🎈", "🥳"] },

  // Genel temalar
  meme: { words: ["Dank", "Epic", "Lol", "Based", "Viral"], emojis: ["😂", "🔥", "💀", "🤣", "👌"] },
  memes: { words: ["Dank", "Epic", "Lol", "Based", "Viral"], emojis: ["😂", "🔥", "💀", "🤣", "👌"] },
  emoji: { words: ["Mood", "Vibes", "React", "Express", "Face"], emojis: ["😊", "🤔", "😎", "🥳", "😍"] },
  reaction: { words: ["Mood", "Vibes", "Express", "Reply", "React"], emojis: ["😮", "👀", "🙌", "👏", "😱"] },
  text: { words: ["Words", "Quote", "Say", "Write", "Chat"], emojis: ["✍️", "💬", "📝", "💭", "🗨️"] },

  // Özel günler/temalar
  christmas: { words: ["Santa", "Joy", "Merry", "Snow", "Gift"], emojis: ["🎄", "🎅", "🎁", "❄️", "⛄"] },
  halloween: { words: ["Spooky", "Boo", "Scary", "Trick", "Treat"], emojis: ["🎃", "👻", "🦇", "💀", "🕷️"] },
  valentine: { words: ["Hearts", "Kiss", "Love", "Sweet", "Rose"], emojis: ["💕", "💘", "🌹", "💝", "😘"] },

  // Varsayılan
  default: { words: ["Cool", "Epic", "Vibes", "Fun", "Best"], emojis: ["✨", "🔥", "💯", "⭐", "🎉"] }
};

const generateCreativeNameLocal = (currentName: string) => {
  let cleanName = currentName || "";

  // Remove emojis and specific symbols
  cleanName = cleanName.replace(/[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu, '');
  cleanName = cleanName.trim();

  if (!cleanName || cleanName.length < 2) cleanName = "Stickers";

  // İlk kelimeyi al (max 2 kelime kuralı için)
  const words = cleanName.split(/\s+/);
  const baseWord = words[0];

  // Alakalı öneri bul
  const lowerName = cleanName.toLowerCase();
  let matchedKey = "default";

  for (const key of Object.keys(RELEVANT_SUGGESTIONS)) {
    if (key !== "default" && lowerName.includes(key)) {
      matchedKey = key;
      break;
    }
  }

  const suggestions = RELEVANT_SUGGESTIONS[matchedKey];

  // Rastgele bir öneri kelimesi seç
  const suggestionWord = suggestions.words[Math.floor(Math.random() * suggestions.words.length)];

  // İki farklı emoji seç
  const shuffledEmojis = [...suggestions.emojis].sort(() => Math.random() - 0.5);
  const emoji1 = shuffledEmojis[0];
  const emoji2 = shuffledEmojis[1] || shuffledEmojis[0];

  // Format: "BaseWord Suggestion 🎉✨"
  return `${baseWord} ${suggestionWord} ${emoji1}${emoji2}`;
};

// DeepSeek destekli akıllı isim üretici (AI varsa AI kullanır, yoksa local fallback)
const generateCreativeName = async (currentName: string): Promise<string> => {
  if (deepseekService.isConfigured()) {
    try {
      const names = await deepseekService.generatePackNames(currentName || 'stickers', 1);
      if (names.length > 0) {
        return `${names[0].name} ${names[0].emoji}`;
      }
    } catch (e) {
      console.warn('[AI Name] DeepSeek hatası, local fallback kullanılıyor:', e);
    }
  }
  return generateCreativeNameLocal(currentName);
};

const CATEGORIES = [
  { id: 'humor', name: 'Humor', emoji: '😂' },
  { id: 'love', name: 'Love', emoji: '❤️' },
  { id: 'religious', name: 'Religious', emoji: '🕌' },
  { id: 'entertainment', name: 'Entertainment', emoji: '🎉' },
  { id: 'background', name: 'Background', emoji: '🌅' },
  { id: 'morning', name: 'Good Morning', emoji: '☀️' },
  { id: 'night', name: 'Good Night', emoji: '🌙' },
  { id: 'birthday', name: 'Birthday', emoji: '🎂' },
  { id: 'congrats', name: 'Congratulations', emoji: '👏' },
  { id: 'animals', name: 'Animals', emoji: '🐱' },
  { id: 'sports', name: 'Sports', emoji: '⚽' },
  { id: 'gaming', name: 'Gaming', emoji: '🎮' },
  { id: 'movie', name: 'Movies & TV', emoji: '🎬' },
  { id: 'music', name: 'Music', emoji: '🎵' },
  { id: 'food', name: 'Food', emoji: '🍔' },
  { id: 'emoji', name: 'Emoji', emoji: '😊' },
  { id: 'cars', name: 'Cars', emoji: '🚗' },
  { id: 'motivation', name: 'Motivation', emoji: '⚡' },
  { id: 'cute', name: 'Cute', emoji: '🧸' },
  { id: 'text', name: 'Text', emoji: '✍️' },
  { id: 'anime', name: 'Anime', emoji: '⛩️' },
  { id: 'memes', name: 'Memes', emoji: '🎭' },
  { id: 'nature', name: 'Nature', emoji: '🌿' },
  { id: 'other', name: 'Other', emoji: '📂' }
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
        className="glass w-full max-w-2xl rounded-3xl overflow-hidden animate-in zoom-in-95 duration-200 shadow-[0_0_100px_rgba(108,92,231,0.1)]"
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
  const [selectedStickerIds, setSelectedStickerIds] = useState<string[]>([]);
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [panelDragIdx, setPanelDragIdx] = useState<number | null>(null);
  const [panelDragOverIdx, setPanelDragOverIdx] = useState<number | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState<'dashboard' | 'packs' | 'stats' | 'messages' | 'notifications' | 'users' | 'batch'>('dashboard');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'passive' | 'animated' | 'static' | 'premium' | 'new'>('all');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [statsFilter, setStatsFilter] = useState<'all' | 'active' | 'passive' | 'premium' | 'normal' | 'popular'>('all');
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);
  const [showMobileMenu, setShowMobileMenu] = useState(false);
  const statusFilterRef = useRef<HTMLButtonElement>(null);
  const categoryFilterRef = useRef<HTMLButtonElement>(null);
  const statsFilterRef = useRef<HTMLButtonElement>(null);

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
    publisher: 'Sticky',
    publisher_email: 'contact@arain.digital',
    privacy_policy_website: '',
    license_agreement_website: '',
    category: 'humor',
    is_premium: false,
    is_active: true,
    is_animated: true,
    is_popular: false
  });
  const [editFormData, setEditFormData] = useState<Partial<StickerPack>>({});
  const [previewSticker, setPreviewSticker] = useState<{ url: string, title?: string } | null>(null);
  const [uploadProgress, setUploadProgress] = useState<{ current: number, total: number, message?: string } | null>(null);

  // Sticker boyut kontrolü (WhatsApp 500KB limiti)
  const [stickerSizes, setStickerSizes] = useState<Record<string, number>>({});
  const [checkingSizes, setCheckingSizes] = useState(false);

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
  const [userFilter, setUserFilter] = useState<'all' | 'premium' | 'free' | 'monthly' | 'yearly'>('all');
  const [selectedUser, setSelectedUser] = useState<UserData | null>(null);
  const [editingSubscription, setEditingSubscription] = useState(false);
  const [subPlan, setSubPlan] = useState('none');

  const [showVideoBgModal, setShowVideoBgModal] = useState(false);
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);

  const [showImportModal, setShowImportModal] = useState(false);

  // Batch Generator States
  const [batchTermsInput, setBatchTermsInput] = useState('');
  const [batchContentType, setBatchContentType] = useState<'gifs' | 'stickers'>('stickers');
  const [batchStickersPerPack, setBatchStickersPerPack] = useState(20);
  const [batchMaxPacks, setBatchMaxPacks] = useState(10);
  const [batchSource, setBatchSource] = useState<BatchSource>('both');
  const [batchUseAiNaming, setBatchUseAiNaming] = useState(true);
  const [batchUseAiTranslation, setBatchUseAiTranslation] = useState(true);
  const [isBatchRunning, setIsBatchRunning] = useState(false);
  const [batchProgress, setBatchProgress] = useState<BatchProgress | null>(null);
  const [batchAiGenerating, setBatchAiGenerating] = useState(false);
  const [importContentType, setImportContentType] = useState<'gifs' | 'stickers'>('stickers');
  const [importCount, setImportCount] = useState(20);
  const [customSearchText, setCustomSearchText] = useState('');
  const [isImporting, setIsImporting] = useState(false);
  const [importProgress, setImportProgress] = useState<{ current: number, total: number, message: string, preview?: string } | null>(null);

  // Draft States
  const [batchSubTab, setBatchSubTab] = useState<'generator' | 'telegram' | 'drafts'>('generator');
  const [draftPacks, setDraftPacks] = useState<StickerPack[]>([]);
  const [draftLoading, setDraftLoading] = useState(false);
  const [selectedDraft, setSelectedDraft] = useState<StickerPack | null>(null);
  const [showDraftEditModal, setShowDraftEditModal] = useState(false);
  const [draftEditData, setDraftEditData] = useState<Partial<StickerPack>>({});
  const [draftPublishing, setDraftPublishing] = useState<string | null>(null);
  const [draftDeleting, setDraftDeleting] = useState<string | null>(null);
  const [deleteAllProgress, setDeleteAllProgress] = useState<{ current: number; total: number } | null>(null);
  const [draftPreviewSticker, setDraftPreviewSticker] = useState<{ url: string, title?: string } | null>(null);
  const [draftDragIdx, setDraftDragIdx] = useState<number | null>(null);
  const [draftDragOverIdx, setDraftDragOverIdx] = useState<number | null>(null);
  const [draftDragPackId, setDraftDragPackId] = useState<string | null>(null);

  // Telegram Import States
  const [telegramBotToken, setTelegramBotToken] = useState(() => localStorage.getItem('telegram_bot_token') || '');
  const [telegramBotName, setTelegramBotName] = useState('');
  const [telegramTokenValid, setTelegramTokenValid] = useState(false);
  const [telegramPacksInput, setTelegramPacksInput] = useState('');
  const [isTelegramImporting, setIsTelegramImporting] = useState(false);
  const [telegramProgress, setTelegramProgress] = useState<any>(null);
  const telegramAbortRef = useRef<AbortController | null>(null);
  const [telegramStickerLimit, setTelegramStickerLimit] = useState(30);
  const [telegramMaxStickers, setTelegramMaxStickers] = useState(0); // 0 = all
  const [telegramSplitPacks, setTelegramSplitPacks] = useState(true);
  const [telegramKeepOriginalName, setTelegramKeepOriginalName] = useState(true);

  // Helper: get fixed dropdown position from button ref
  const getDropdownPos = (ref: React.RefObject<HTMLButtonElement | null>) => {
    if (!ref.current) return { top: 0, left: 0 };
    const rect = ref.current.getBoundingClientRect();
    return { top: rect.bottom + 8, left: Math.max(8, rect.right - 220) };
  };

  // FFmpeg'i önceden yükle - işlem başladığında hazır olsun
  useEffect(() => {
    stickerProcessor.preload().catch(console.error);
  }, []);

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
            alert("Unauthorized Access: " + u.email + " was not found in the admin list.");
            setLoading(false);
            return;
          }

          const adminData = adminDoc.data();
          const name = adminData?.name || adminData?.admin || u.email.split('@')[0];

          // Go directly to panel, no welcome animation
          setLoading(false);

          console.log("Admin login successful, name:", name);
        } catch (error: any) {
          console.error("Admin yetkisi kontrol edilirken hata:", error);
          alert("Login Error: " + (error?.message || "Authorization check failed."));
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
    if (!window.confirm("Are you sure you want to delete this message?")) return;
    try {
      await deleteDoc(doc(db, 'messages', messageId));
      setMessages(messages.filter(m => m.id !== messageId));
    } catch (e) {
      console.error("Mesaj silinemedi:", e);
    }
  };

  const deleteSuggestion = async (suggestionId: string) => {
    if (!window.confirm("Are you sure you want to delete this suggestion?")) return;
    try {
      await deleteDoc(doc(db, 'suggestions', suggestionId));
      setSuggestions(suggestions.filter(s => s.id !== suggestionId));
    } catch (e) {
      console.error("Öneri silinemedi:", e);
    }
  };

  const clearAllMessages = async () => {
    if (!window.confirm("Are you sure you want to delete ALL messages?")) return;
    try {
      setDeleteProgress({ deleting: true, message: 'Deleting messages...', current: 0, total: messages.length });
      const snapshot = await getDocs(collection(db, 'messages'));
      for (let i = 0; i < snapshot.docs.length; i++) {
        await deleteDoc(snapshot.docs[i].ref);
        setDeleteProgress({ deleting: true, message: 'Deleting messages...', current: i + 1, total: snapshot.docs.length });
      }
      setDeleteProgress(null);
    } catch (e) {
      setDeleteProgress(null);
      console.error("Mesajlar silinemedi:", e);
    }
  };

  const clearAllSuggestions = async () => {
    if (!window.confirm("Are you sure you want to delete ALL suggestions?")) return;
    try {
      setDeleteProgress({ deleting: true, message: 'Deleting suggestions...', current: 0, total: suggestions.length });
      const snapshot = await getDocs(collection(db, 'suggestions'));
      for (let i = 0; i < snapshot.docs.length; i++) {
        await deleteDoc(snapshot.docs[i].ref);
        setDeleteProgress({ deleting: true, message: 'Deleting suggestions...', current: i + 1, total: snapshot.docs.length });
      }
      setDeleteProgress(null);
    } catch (e) {
      setDeleteProgress(null);
      console.error("Öneriler silinemedi:", e);
    }
  };

  const handleSendNotification = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!notifBody) return alert("Please enter the notification message.");
    if (!window.confirm("Are you sure you want to send this notification to all users?")) return;

    setIsSendingNotif(true);
    try {
      const notifRef = collection(db, 'notifications');
      await setDoc(doc(notifRef), {
        title: notifTitle || 'Sticky',
        body: notifBody,
        imageUrl: notifImageUrl || '',
        timestamp: serverTimestamp()
      });
      alert(" Notification queued! It will reach all devices within a few seconds.");
      setNotifBody('');
      setNotifImageUrl('');
    } catch (e: any) {
      console.error("Bildirim gönderme hatası:", e);
      alert("Error: " + e.message);
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
      alert("Google sign-in failed: " + error.message);
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
      alert("Firebase Data Fetch Error: " + (error?.message || "Unknown error"));
    } finally {
      setLoading(false);
    }
  };

  // ========== DRAFT MANAGEMENT ==========
  const fetchDrafts = async () => {
    setDraftLoading(true);
    try {
      const draftDocs = await getDocs(collection(db, 'draft_stickers'));
      const drafts: StickerPack[] = draftDocs.docs.map(d => {
        const data = d.data();
        return {
          id: d.id,
          ...data,
          is_premium: data.is_premium ?? false,
          is_animated: data.is_animated ?? true,
          download_count: 0,
          fake_download_base: Number(data.fake_download_base || 0),
          view_count: 0,
          favorite_count: 0,
          sticker_count: Number(data.sticker_count || data.stickers?.length || 0),
        } as StickerPack;
      });
      setDraftPacks(drafts.sort((a, b) => (a.name || '').localeCompare(b.name || '')));
    } catch (error: any) {
      console.error("Draft fetch error:", error);
    } finally {
      setDraftLoading(false);
    }
  };

  const publishDraft = async (draft: StickerPack) => {
    if (!window.confirm(`Are you sure you want to publish "${draft.name}"?`)) return;
    setDraftPublishing(draft.id);
    try {
      const targetCollection = draft.is_premium ? 'premium_stickers' : 'stickers';
      const { id, ...packDataWithoutId } = draft as any;
      await setDoc(doc(db, targetCollection, draft.id), {
        ...packDataWithoutId,
        is_active: true,
        published_at: serverTimestamp(),
      });
      await deleteDoc(doc(db, 'draft_stickers', draft.id));
      setDraftPacks(prev => prev.filter(p => p.id !== draft.id));
      if (selectedDraft?.id === draft.id) setSelectedDraft(null);
      await fetchPacks();
      alert(`✅ "${draft.name}" published successfully!`);
    } catch (error: any) {
      console.error("Publish error:", error);
      alert(`Publish error: ${error.message}`);
    } finally {
      setDraftPublishing(null);
    }
  };

  const publishAllDrafts = async () => {
    if (draftPacks.length === 0) return;
    if (!window.confirm(`Are you sure you want to publish ${draftPacks.length} draft packs?`)) return;
    let published = 0;
    for (const draft of draftPacks) {
      setDraftPublishing(draft.id);
      try {
        const targetCollection = draft.is_premium ? 'premium_stickers' : 'stickers';
        const { id, ...packDataWithoutId } = draft as any;
        await setDoc(doc(db, targetCollection, draft.id), {
          ...packDataWithoutId,
          is_active: true,
          published_at: serverTimestamp(),
        });
        await deleteDoc(doc(db, 'draft_stickers', draft.id));
        published++;
      } catch (error: any) {
        console.error(`Publish error (${draft.name}):`, error);
      }
    }
    setDraftPublishing(null);
    setDraftPacks([]);
    setSelectedDraft(null);
    await fetchPacks();
    alert(`✅ ${published}/${draftPacks.length} packs published!`);
  };

  const deleteAllDrafts = async () => {
    if (draftPacks.length === 0) return;
    if (!window.confirm(`⚠️ Delete ALL ${draftPacks.length} drafts? This cannot be undone!`)) return;
    const total = draftPacks.length;
    let deleted = 0;
    setDeleteAllProgress({ current: 0, total });

    for (let i = 0; i < draftPacks.length; i++) {
      const draft = draftPacks[i];
      setDeleteAllProgress({ current: i, total });
      setDraftDeleting(draft.id);
      try {
        try {
          const folderRef = ref(storage, `stickers/${draft.id}`);
          const fileList = await listAll(folderRef);
          await Promise.all(fileList.items.map(item => deleteObject(item)));
        } catch (e) { console.log('Storage delete:', e); }
        await deleteDoc(doc(db, 'draft_stickers', draft.id));
        deleted++;
      } catch (error: any) {
        console.error(`Delete error (${draft.name}):`, error);
      }
    }

    setDeleteAllProgress(null);
    setDraftDeleting(null);
    setDraftPacks([]);
    setSelectedDraft(null);
    alert(`🗑️ ${deleted}/${total} drafts deleted!`);
  };

  const deleteDraftPack = async (draft: StickerPack) => {
    if (!window.confirm(`Are you sure you want to delete the draft "${draft.name}"? This action cannot be undone!`)) return;
    setDraftDeleting(draft.id);
    try {
      // Storage'dan sticker dosyalarını sil
      try {
        const folderRef = ref(storage, `stickers/${draft.id}`);
        const fileList = await listAll(folderRef);
        for (const item of fileList.items) {
          await deleteObject(item);
        }
      } catch (e) { console.log('Storage silme (draft):', e); }
      await deleteDoc(doc(db, 'draft_stickers', draft.id));
      setDraftPacks(prev => prev.filter(p => p.id !== draft.id));
      if (selectedDraft?.id === draft.id) setSelectedDraft(null);
    } catch (error: any) {
      console.error("Draft delete error:", error);
      alert(`Delete error: ${error.message}`);
    } finally {
      setDraftDeleting(null);
    }
  };

  const updateDraftPack = async () => {
    if (!selectedDraft || !draftEditData) return;
    try {
      const updatedData: any = { ...draftEditData };
      updatedData.image_data_version = Date.now().toString();
      await updateDoc(doc(db, 'draft_stickers', selectedDraft.id), updatedData);
      const updated = { ...selectedDraft, ...updatedData } as StickerPack;
      setDraftPacks(prev => prev.map(p => p.id === selectedDraft.id ? updated : p));
      setSelectedDraft(updated);
      setShowDraftEditModal(false);
      alert("Draft updated successfully.");
    } catch (error: any) {
      console.error("Draft update error:", error);
      alert(`Update error: ${error.message}`);
    }
  };

  const reorderDraftStickers = async (draft: StickerPack, fromIdx: number, toIdx: number) => {
    if (fromIdx === toIdx) return;
    try {
      const stickers = [...draft.stickers];
      const [moved] = stickers.splice(fromIdx, 1);
      stickers.splice(toIdx, 0, moved);
      await updateDoc(doc(db, 'draft_stickers', draft.id), { stickers });
      const updated = { ...draft, stickers } as StickerPack;
      setDraftPacks(prev => prev.map(p => p.id === draft.id ? updated : p));
    } catch (error: any) {
      console.error("Reorder error:", error);
      alert(`Reorder error: ${error.message}`);
    }
  };

  const removeStickerFromDraft = async (draft: StickerPack, stickerIndex: number) => {
    if (!window.confirm('Are you sure you want to remove this sticker from the draft?')) return;
    try {
      const updatedStickers = draft.stickers.filter((_, idx) => idx !== stickerIndex);
      await updateDoc(doc(db, 'draft_stickers', draft.id), {
        stickers: updatedStickers,
        sticker_count: updatedStickers.length,
      });
      const updated = { ...draft, stickers: updatedStickers, sticker_count: updatedStickers.length } as StickerPack;
      setDraftPacks(prev => prev.map(p => p.id === draft.id ? updated : p));
      if (selectedDraft?.id === draft.id) setSelectedDraft(updated);
    } catch (error: any) {
      console.error("Remove sticker error:", error);
      alert(`Remove sticker error: ${error.message}`);
    }
  };

  // Fetch drafts when batch tab is active
  useEffect(() => {
    if (activeTab === 'batch' && batchSubTab === 'drafts') {
      fetchDrafts();
    }
  }, [activeTab, batchSubTab]);

  // ========== STİCKER BOYUT KONTROLÜ (WhatsApp 500KB Limiti) ==========
  const checkStickerSizes = async (pack: StickerPack) => {
    if (!pack.stickers || pack.stickers.length === 0) {
      setStickerSizes({});
      setCheckingSizes(false);
      return;
    }

    setCheckingSizes(true);
    setStickerSizes({}); // Önceki boyutları temizle
    const sizes: Record<string, number> = {};

    try {
      // Her sticker'ın boyutunu kontrol et (paralel, 5'er grup)
      const batchSize = 5;
      for (let i = 0; i < pack.stickers.length; i += batchSize) {
        const batch = pack.stickers.slice(i, i + batchSize);

        await Promise.all(batch.map(async (sticker) => {
          try {
            // GET request ile dosyayı çek ve blob boyutunu al
            const response = await fetch(sticker.url);
            if (response.ok) {
              const blob = await response.blob();
              sizes[sticker.image_file] = blob.size;
            }
          } catch (err) {
            console.warn(`Boyut alınamadı: ${sticker.image_file}`, err);
          }
        }));

        // Her batch sonrası state'i güncelle (anlık görüntüleme için)
        setStickerSizes({ ...sizes });
      }

      setStickerSizes(sizes);
    } catch (error) {
      console.error('Sticker boyut kontrolü hatası:', error);
    } finally {
      setCheckingSizes(false);
    }
  };

  // Paket seçildiğinde boyutları kontrol et
  useEffect(() => {
    if (selectedPack) {
      checkStickerSizes(selectedPack);
    } else {
      setStickerSizes({});
    }
  }, [selectedPack?.id]);

  // 500KB'ı aşan sticker sayısını hesapla
  const oversizedStickersCount = useMemo(() => {
    const MAX_SIZE = 500 * 1024;
    return Object.values(stickerSizes).filter(size => size > MAX_SIZE).length;
  }, [stickerSizes]);

  // ========== KULLANICI YÖNETİM FONKSİYONLARI ==========

  const fetchUsers = async () => {
    setUsersLoading(true);
    try {
      const snapshot = await getDocs(collection(db, 'users'));
      const usersList: UserData[] = snapshot.docs.map(d => {
        const data = d.data();
        return {
          id: d.id,
          email: data.email || '',
          is_premium: data.is_premium || false,
          premium_type: data.premium_type || 'none',
          premium_expiry: data.premium_expiry || 0,
          favorite_packs: data.favorite_packs || [],
          last_sync: data.last_sync || null,
          cancelled_at: data.cancelled_at || null,
          cancelled_reason: data.cancelled_reason || '',
          subscription_source: data.subscription_source || 'none',
          subscription_history: data.subscription_history || [],
          // New fields
          created_at: data.created_at || null,
          display_name: data.display_name || data.displayName || '',
          photo_url: data.photo_url || data.photoURL || '',
          device_info: data.device_info || null,
          total_stickers_added: data.total_stickers_added || 0,
          custom_packs_count: data.custom_packs_count || 0,
        };
      });
      // Sort by created_at (newest first)
      usersList.sort((a, b) => {
        const aTime = a.created_at?.toMillis?.() || a.created_at || 0;
        const bTime = b.created_at?.toMillis?.() || b.created_at || 0;
        return bTime - aTime;
      });
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
    else if (userFilter === 'monthly') filtered = filtered.filter(u => u.premium_type === 'monthly');
    else if (userFilter === 'yearly') filtered = filtered.filter(u => u.premium_type === 'yearly');

    return filtered;
  }, [usersData, userSearch, userFilter]);

  const userStats = useMemo(() => {
    const total = usersData.length;
    const premium = usersData.filter(u => u.is_premium).length;
    const monthly = usersData.filter(u => u.premium_type === 'monthly').length;
    const yearly = usersData.filter(u => u.premium_type === 'yearly').length;
    const free = total - premium;
    return { total, premium, monthly, yearly, free };
  }, [usersData]);

  const handleUpdateSubscription = async (userId: string, plan: string) => {
    try {
      const user = usersData.find(u => u.id === userId);
      // Prevent changing Google Play subscriptions via Admin
      if (user?.subscription_source === 'google_play' && plan !== 'none') {
        alert("Google Play subscriptions cannot be changed from the admin panel.");
        return;
      }

      let isPremium = false;
      let type = 'none';
      let expiry = 0;
      let details = '';

      if (plan === 'monthly') {
        isPremium = true;
        type = 'monthly';
        const d = new Date();
        d.setMonth(d.getMonth() + 1);
        expiry = d.getTime();
        details = 'Added 1 month by Admin';
      } else if (plan === 'yearly') {
        isPremium = true;
        type = 'yearly';
        const d = new Date();
        d.setFullYear(d.getFullYear() + 1);
        expiry = d.getTime();
        details = 'Added 1 year by Admin';
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
      alert("Error occurred while updating subscription.");
    }
  };

  const handleRevokeSubscription = async (userId: string) => {
    const user = usersData.find(u => u.id === userId);
    if (!user) return;

    if (user.subscription_source === 'google_play') {
      alert("Subscriptions purchased through Google Play cannot be cancelled here. The user needs to cancel through the Play Store.");
      return;
    }

    if (!window.confirm("Are you sure you want to cancel this user's subscription?")) return;

    try {
      const historyItem: SubscriptionHistoryItem = {
        id: crypto.randomUUID(),
        type: 'cancel',
        plan: 'none',
        source: 'admin',
        timestamp: Date.now(),
        date_str: new Date().toLocaleDateString('tr-TR'),
        details: 'Cancelled by Admin'
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
      alert("Error occurred while cancelling subscription.");
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
    if (!newPackData.name || !newPackData.publisher) return alert("Please fill in the name and publisher fields.");
    setIsProcessing(true);
    try {
      const packId = newPackData.name.toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, '');
      const collectionName = newPackData.is_premium ? 'premium_stickers' : 'stickers';

      const packData: any = {
        name: newPackData.name,
        publisher: newPackData.publisher,
        publisher_email: newPackData.publisher_email,
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
        // Fiyatlandirma kaldirildi - Tüm paketler ücretsiz/reklamli
        price_try: "", price_usd: "", price_eur: "",
        stickers: [],
        tray_url: "",
        created_at: serverTimestamp()
      };

      // Tüm name_ ile başlayan alanları kopyala (Çeviriler)
      const hasTranslations = Object.keys(newPackData).some(key => key.startsWith('name_') && key !== 'name_en' && (newPackData as any)[key]);
      Object.keys(newPackData).forEach(key => {
        if (key.startsWith('name_')) {
          packData[key] = (newPackData as any)[key] || '';
        }
      });

      // Otomatik çeviri: Eğer çeviriler boşsa ve isim varsa, DeepSeek veya Google Translate ile çevir
      if (!hasTranslations && newPackData.name) {
        try {
          let translations: Record<string, string>;
          if (deepseekService.isConfigured()) {
            translations = await deepseekService.translatePackName(newPackData.name);
          } else {
            translations = await translateTextAllLanguages(newPackData.name);
          }
          Object.entries(translations).forEach(([key, value]) => {
            packData[key] = value;
          });
          console.log('[AUTO-TRANSLATE] Paket oluşturulurken otomatik çeviri yapıldı');
        } catch (translateErr) {
          console.warn('[AUTO-TRANSLATE] Otomatik çeviri hatası:', translateErr);
        }
      }

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
        publisher: 'Sticky',
        publisher_email: 'contact@arain.digital',
        privacy_policy_website: '',
        license_agreement_website: '',
        category: 'humor',
        is_premium: false,
        is_active: true,
        is_animated: true,

      });
      alert("New animated pack created. You can now add video/gif files.");
    } catch (e) {
      alert("Error: " + e);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleAutoTranslate = async (isEdit: boolean) => {
    const textToTranslate = isEdit ? editFormData.name : newPackData.name;
    if (!textToTranslate) {
      alert("Please enter a main name (English) first.");
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
      alert("✅ Gemini translated all languages successfully!");
    } catch (error) {
      console.error("Gemini Error:", error);
      alert("⚠️ An error occurred during translation. Please try again.");
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

        alert(`Pack updated successfully.`);
      } else {
        // Sadece bilgi güncelleme (tip değişikliği yok)
        await updateDoc(doc(db, oldCollection, selectedPack.id), updatedData);
        const updated = { ...selectedPack, ...updatedData } as StickerPack;
        setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
        setSelectedPack(updated);
        setShowEditPackModal(false);
        alert("Pack details updated successfully.");
      }

    } catch (e: any) {
      console.error('[UPDATE] Hata:', e);
      alert("Error: " + e.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const uploadStickersBatch = async (files: File[], removeBgForVideos: boolean = false) => {
    if (!selectedPack) return;

    const uploadCount = files.length;
    setIsProcessing(true);
    setUploadProgress({ current: 0, total: uploadCount, message: 'Processing started...' });

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
          alert(`Error: This is an animated pack. Static images like "${file.name}" cannot be added.`);
          continue;
        }
        if (!selectedPack.is_animated && isAnimatedFile) {
          alert(`Error: This is a static pack. Animated files like "${file.name}" cannot be added.`);
          continue;
        }

        setUploadProgress({
          current: i + 1,
          total: uploadCount,
          message: `${file.name} processing...`
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
              alert(`Error: This is an animated pack. "${file.name}" is a static WebP file.`);
              continue;
            } else if (!isAnimatedPack && isAnimatedWebP) {
              // Statik pakete animasyonlu WebP eklenemez
              alert(`Error: This is a static pack. "${file.name}" is an animated WebP file.`);
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
          alert(`Error: "${file.name}" could not be processed.\n\n${processingError.message || 'Unknown error'}`);
          continue;
        }

        // WhatsApp 500KB limit kontrolü
        const MAX_WHATSAPP_SIZE = 500 * 1024;
        if (processedBlob.size > MAX_WHATSAPP_SIZE) {
          const sizeMB = (processedBlob.size / 1024).toFixed(0);
          console.error(`[UPLOAD] ❌ WhatsApp limit aşıldı: ${file.name} = ${sizeMB}KB (max: 500KB)`);
          alert(`Error: "${file.name}" exceeds the WhatsApp 500KB limit (${sizeMB}KB).\n\nThis sticker could not be uploaded. Try a shorter or lower resolution file.`);
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
        setUploadProgress({ current: uploadCount, total: uploadCount, message: 'Auto-selecting cover image...' });

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
      alert(`${newStickers.length} stickers processed and added successfully. Cover image updated.`);
    } catch (error: any) {
      console.error(error);
      alert("Upload error: " + error.message);
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

  const handleImportStickers = async () => {
    if (!selectedPack) {
      alert('Please select a pack first!');
      return;
    }

    const query = customSearchText.trim() || 'trending';

    const currentCount = selectedPack.stickers?.length || 0;
    const totalCount = currentCount + importCount;

    if (totalCount > 30) {
      alert(`Error: A pack can have a maximum of 30 stickers. (Current: ${currentCount}, To add: ${importCount}, Total: ${totalCount})`);
      return;
    }

    const contentTypeName = importContentType === 'gifs' ? 'GIFs' : 'Stickers';
    if (!window.confirm(`${importCount} ${contentTypeName} for "${query}" will be added from Giphy. Do you confirm?`)) {
      return;
    }

    setIsImporting(true);
    setImportProgress({ current: 0, total: importCount, message: 'Starting...' });

    try {
      const importedStickers = await importStickers({
        source: 'giphy',
        contentType: importContentType,
        query: query,
        count: importCount,
        packId: selectedPack.id,
        onProgress: (progress: StickerImportProgress) => {
          setImportProgress({
            current: progress.current,
            total: progress.total,
            message: progress.message,
            preview: progress.currentSticker
          });
        }
      });

      if (importedStickers.length === 0) {
        alert('No stickers could be added. Please try a different search.');
        return;
      }

      const collectionName = selectedPack.is_premium ? 'premium_stickers' : 'stickers';
      const packRef = doc(db, collectionName, selectedPack.id);

      const newVersion = Date.now().toString();
      const updatedData = {
        stickers: [...(selectedPack.stickers || []), ...importedStickers],
        sticker_count: Math.max(0, (selectedPack.sticker_count || 0) + importedStickers.length),
        image_data_version: newVersion
      };

      await updateDoc(packRef, updatedData);

      const updated = {
        ...selectedPack,
        ...updatedData
      };

      setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
      setSelectedPack(updated);
      setShowImportModal(false);

      alert(`Import successful! ${importedStickers.length} stickers added.\n\nSource: Giphy\nAdded: ${importedStickers.length}`);
    } catch (error: any) {
      console.error('Import error:', error);
      alert(`Error: ${error.message}`);
    } finally {
      setIsImporting(false);
      setImportProgress(null);
    }
  };

  const handleAddSticker = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const filesList = e.target.files;
    if (!filesList || !selectedPack) return;

    // Güncel sticker sayısını al (stickers array'inden, sticker_count'tan değil)
    const currentCount = selectedPack.stickers?.length || 0;
    const uploadCount = filesList.length;
    const totalCount = currentCount + uploadCount;

    // WhatsApp Paket Standartları Kontrolü (Min 3, Max 30 Toplam)
    if (totalCount > 30) {
      alert(`Error: A pack can have a maximum of 30 stickers. (Current: ${currentCount}, New: ${uploadCount}, Total: ${totalCount})`);
      e.target.value = '';
      return;
    }

    if (totalCount < 3) {
      alert(`Error: A pack must have at least 3 stickers. (Current: ${currentCount}, New: ${uploadCount}, Total: ${totalCount}). You need to add at least ${3 - currentCount} more.`);
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
    if (!window.confirm(`Are you sure you want to PERMANENTLY delete "${pack.name}"?\n\nThis action cannot be undone and all files will be deleted!`)) return;

    try {
      setDeleteProgress({ deleting: true, message: 'Listing files...', current: 0, total: 0 });

      const folderRef = ref(storage, `stickers/${pack.id}`);

      // 1. Storage klasöründeki TÜM dosyaları listele ve sil
      try {
        const fileList = await listAll(folderRef);
        const total = fileList.items.length;
        setDeleteProgress({ deleting: true, message: `Deleting from storage...`, current: 0, total });

        for (let i = 0; i < fileList.items.length; i++) {
          await deleteObject(fileList.items[i]);
          setDeleteProgress({ deleting: true, message: `Deleting from storage...`, current: i + 1, total });
        }
      } catch (e) { console.log('Storage silme hatası:', e); }

      // 2. Firestore dokümanını sil
      setDeleteProgress({ deleting: true, message: 'Deleting from database...', current: 0, total: 1 });
      const collectionName = pack.is_premium ? 'premium_stickers' : 'stickers';
      await deleteDoc(doc(db, collectionName, pack.id));

      setPacks(packs.filter(p => p.id !== pack.id));
      if (selectedPack?.id === pack.id) setSelectedPack(null);
      setDeleteProgress(null);
    } catch (error) {
      setDeleteProgress(null);
      alert("Delete error: " + error);
    }
  };

  const deleteSticker = async (pack: StickerPack, sticker: Sticker) => {
    if (!window.confirm("Are you sure you want to delete this sticker?")) return;

    try {
      const collectionName = pack.is_premium ? 'premium_stickers' : 'stickers';
      const packRef = doc(db, collectionName, pack.id);

      const newVersion = Date.now().toString();
      const newStickerCount = Math.max(0, (pack.stickers?.length || pack.sticker_count) - 1);
      await updateDoc(packRef, {
        stickers: arrayRemove(sticker),
        sticker_count: newStickerCount,
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

      // Silinen sticker'ı boyut listesinden kaldır
      setStickerSizes(prev => {
        const newSizes = { ...prev };
        delete newSizes[sticker.image_file];
        return newSizes;
      });
    } catch (error) {
      alert("Sticker delete error: " + error);
    }
  };

  const setAsTray = async (pack: StickerPack, sticker: Sticker) => {
    if (!pack || !sticker || isProcessing) return;

    if (!window.confirm("Are you sure you want to set this sticker as the pack cover?")) return;

    setIsProcessing(true);
    setUploadProgress({ current: 0, total: 1, message: 'Preparing cover image...' });

    try {
      // Sticker'ı blob olarak çek
      const response = await fetch(sticker.url);
      const blob = await response.blob();

      // Blob -> File dönüşümü (stickerProcessor.processTray için)
      // Orijinal dosya adını koruyarak tray_ öneki ekleyelim
      const file = new File([blob], `tray_${sticker.image_file}`, { type: 'image/webp' });

      // Tray resmi olarak işle (PNG & Resize)
      const trayProcessedBlob = await stickerProcessor.processTray(file, (p) => {
        setUploadProgress(prev => prev ? { ...prev, message: `Kapak: ${p.message}` } : null);
      });

      // Storage'a yükle
      const trayFileName = `tray_${Date.now()}.png`;
      const trayStorageRef = ref(storage, `stickers/${pack.id}/${trayFileName}`);

      await uploadBytes(trayStorageRef, trayProcessedBlob);
      const trayUrl = await getDownloadURL(trayStorageRef);

      // Varsa eski kapağı sil
      if (pack.tray_image_file) {
        const oldTrayPath = `stickers/${pack.id}/${pack.tray_image_file}`;
        try { await deleteObject(ref(storage, oldTrayPath)); } catch (e) {
          // tray.png ise ve silinemezse normal, bazı eski paketlerde tray.png statik olabilir
          console.warn("Old tray delete fail", e);
        }
      }

      // Firestore güncelle
      const collectionName = pack.is_premium ? 'premium_stickers' : 'stickers';
      const packRef = doc(db, collectionName, pack.id);

      const newVersion = Date.now().toString();
      const updateData = {
        tray_url: trayUrl,
        tray_image_file: trayFileName,
        image_data_version: newVersion
      };

      await updateDoc(packRef, updateData);

      // Local state güncelle
      const updatedPack = { ...pack, ...updateData };
      setPacks(packs.map(p => p.id === pack.id ? updatedPack : p));
      setSelectedPack(updatedPack);

      alert("Cover image updated successfully.");
    } catch (error: any) {
      console.error(error);
      alert("Set cover error: " + error.message);
    } finally {
      setIsProcessing(false);
      setUploadProgress(null);
    }
  };

  const deleteSelectedStickers = async () => {
    if (!selectedPack || selectedStickerIds.length === 0) return;
    if (!window.confirm(`Are you sure you want to delete ${selectedStickerIds.length} stickers?`)) return;

    try {
      setIsProcessing(true);
      const collectionName = selectedPack.is_premium ? 'premium_stickers' : 'stickers';
      const packRef = doc(db, collectionName, selectedPack.id);

      const stickersToDelete = (selectedPack.stickers || []).filter(s => selectedStickerIds.includes(s.url));
      const remainingStickers = (selectedPack.stickers || []).filter(s => !selectedStickerIds.includes(s.url));

      // 1. Storage'dan dosyaları sil (Parallel)
      await Promise.all(stickersToDelete.map(async (sticker) => {
        const storagePath = `stickers/${selectedPack.id}/${sticker.image_file}`;
        try {
          await deleteObject(ref(storage, storagePath));
          console.log('[DELETE] ✅ Storage dosyası silindi:', storagePath);
        } catch (storageErr: any) {
          console.error(`[DELETE] ❌ Storage silme hatası (${sticker.image_file}):`, storageErr.message);
        }
      }));

      // 2. Firestore güncelle
      const newVersion = Date.now().toString();
      const newStickerCount = remainingStickers.length;
      await updateDoc(packRef, {
        stickers: remainingStickers,
        sticker_count: newStickerCount,
        image_data_version: newVersion,
        updated_at: serverTimestamp()
      });

      // 3. State güncelle
      const updatedPack = {
        ...selectedPack,
        stickers: remainingStickers,
        sticker_count: newStickerCount,
        image_data_version: newVersion
      };

      setSelectedPack(updatedPack);
      setPacks(packs.map(p => p.id === selectedPack.id ? updatedPack : p));

      // Temizlik
      setStickerSizes(prev => {
        const newSizes = { ...prev };
        stickersToDelete.forEach(s => delete newSizes[s.image_file]);
        return newSizes;
      });

      alert(`${selectedStickerIds.length} stickers deleted successfully.`);
      setSelectedStickerIds([]);
      setIsSelectionMode(false);
    } catch (error) {
      console.error('Toplu silme hatası:', error);
      alert('An error occurred while deleting selected stickers.');
    } finally {
      setIsProcessing(false);
    }
  };

  const toggleStickerSelection = (stickerUrl: string) => {
    if (selectedStickerIds.includes(stickerUrl)) {
      setSelectedStickerIds(prev => prev.filter(url => url !== stickerUrl));
    } else {
      setSelectedStickerIds(prev => [...prev, stickerUrl]);
    }
  };

  const toggleSelectAll = () => {
    if (!selectedPack?.stickers) return;
    if (selectedStickerIds.length === selectedPack.stickers.length) {
      setSelectedStickerIds([]);
    } else {
      setSelectedStickerIds(selectedPack.stickers.map(s => s.url));
    }
  };

  // Sticker sıralama fonksiyonu
  const moveStickerPosition = async (pack: StickerPack, fromIndex: number, toIndex: number) => {
    if (!pack.stickers || toIndex < 0 || toIndex >= pack.stickers.length) return;

    const newStickers = [...pack.stickers];
    const [movedSticker] = newStickers.splice(fromIndex, 1);
    newStickers.splice(toIndex, 0, movedSticker);

    try {
      const collectionName = pack.is_premium ? 'premium_stickers' : 'stickers';
      const packRef = doc(db, collectionName, pack.id);
      const newVersion = Date.now().toString();

      await updateDoc(packRef, {
        stickers: newStickers,
        image_data_version: newVersion
      });

      const updatedPack = {
        ...pack,
        stickers: newStickers,
        image_data_version: newVersion
      };

      setPacks(packs.map(p => p.id === pack.id ? updatedPack : p));
      setSelectedPack(updatedPack);
    } catch (error) {
      alert("Reorder error: " + error);
    }
  };

  const resetStats = async (pack: StickerPack) => {
    if (!window.confirm("Do you want to reset the statistics?")) return;
    try {
      const collectionName = pack.is_premium ? 'premium_stickers' : 'stickers';
      await updateDoc(doc(db, collectionName, pack.id), {
        download_count: 0,
        view_count: 0
      });
      const updated = { ...pack, download_count: 0, view_count: 0 };
      setPacks(packs.map(p => p.id === pack.id ? updated : p));
      setSelectedPack(updated);
      alert("Statistics reset.");
    } catch (e) { alert("Error: " + e); }
  };

  // Tüm paketleri fake_download_base ve premium fiyatlarıyla güncelle
  const updateAllPacksWithFakeBase = async (forceUpdate: boolean = false) => {
    const range = fakeBaseMax - fakeBaseMin;
    if (range <= 0) {
      alert("Invalid range! Max value must be greater than Min.");
      return;
    }
    if (!window.confirm(`Fake download base (${fakeBaseMin.toLocaleString()} - ${fakeBaseMax.toLocaleString()}) will be ${forceUpdate ? 'FORCE ' : ''}added to all packs and premium pack prices will be updated. Continue?`)) return;
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

      alert(`${updated} packs updated! Please wait for the list to refresh...`);
      await fetchPacks();
    } catch (e) {
      alert("Error: " + e);
    } finally {
      setIsProcessing(false);
    }
  };

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
            <h1 className="text-2xl font-bold">Sticky Admin Login</h1>
            <p className="text-textSec text-sm">Sign in to access the admin panel</p>
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
            Sign in with Google
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

    if (statusFilter === 'animated') return p.is_animated === true;
    if (statusFilter === 'static') return p.is_animated !== true;
    if (statusFilter === 'premium') return p.is_premium === true;
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
              <h3 className="text-xl font-bold text-white">Deleting...</h3>
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
                <div className="w-1 h-1 bg-primary rounded-full animate-pulse shadow-[0_0_8px_rgba(108,92,231,0.8)]" />
                <span className="text-[9px] text-textSec font-black uppercase tracking-widest opacity-80">Active</span>
              </div>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 md:gap-4">
          <div className="relative group hidden sm:block">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-textSec/50 group-focus-within:text-primary transition-colors" size={16} />
            <input
              type="text"
              placeholder="Quick search..."
              className="bg-white/5 border border-white/5 rounded-full pl-10 pr-4 py-2 focus:ring-2 focus:ring-primary/20 focus:border-primary/50 outline-none text-sm w-48 md:w-64 transition-all text-white placeholder:text-textSec/30"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <button
            onClick={async () => {
              await fetchPacks();
              // Seçili paket varsa boyutları yeniden kontrol et
              if (selectedPack) {
                checkStickerSizes(selectedPack);
              }
            }}
            className="p-2.5 hover:bg-white/10 rounded-xl transition-all active:scale-90 group relative"
            title="Refresh System"
          >
            <RefreshCcw size={18} className={cn("text-textSec group-hover:text-primary transition-colors", (loading || checkingSizes) && 'animate-spin text-primary')} />
          </button>

          <div className="w-px h-6 bg-white/10 mx-1 hidden md:block" />

          <button
            onClick={() => {
              if (window.confirm("Do you want to sign out safely?")) signOut(auth);
            }}
            className="hidden md:flex items-center gap-2.5 px-4 py-2 bg-danger/5 hover:bg-danger/10 text-textSec hover:text-danger rounded-xl transition-all border border-transparent hover:border-danger/20"
          >
            <LogOut size={16} />
            <span className="text-xs font-black uppercase tracking-wider">Sign Out</span>
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
                { id: 'stats', label: 'Statistics', icon: BarChart3 },
                { id: 'messages', label: 'Messages', icon: Mail, count: messages.filter(m => m.status === 'unread').length },
                { id: 'notifications', label: 'Notifications', icon: Bell },
                { id: 'users', label: 'Users', icon: Users }
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
                  if (window.confirm("Are you sure you want to sign out?")) signOut(auth);
                }}
                className="w-full flex items-center justify-center gap-4 p-5 bg-danger/10 text-danger rounded-3xl font-black uppercase tracking-widest"
              >
                <LogOut size={24} />
                Sign Out
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
              { id: 'dashboard', icon: Grid, label: 'Dashboard' },
              { id: 'stats', icon: BarChart3, label: 'Statistics' },
              { id: 'messages', icon: Mail, label: 'Messages', count: messages.filter(m => m.status === 'unread').length },
              { id: 'notifications', icon: Bell, label: 'Notifications' },
              { id: 'users', icon: Users, label: 'Users' },
              { id: 'batch', icon: Zap, label: 'Batch Generator' }
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
          <div className="flex flex-1 h-[calc(100vh)] overflow-hidden">
            {/* Sidebar / Pack List */}
            <div className={cn(
              "border-r border-white/5 flex-col bg-card/30 transition-all duration-500 h-full shrink-0",
              selectedPack ? "md:w-[320px] w-full hidden md:flex" : "w-full flex"
            )}>
              <div className="p-4 space-y-3 shrink-0">
                <div className="flex items-center justify-between">
                  <div className="flex flex-col">
                    <span className="text-[10px] font-bold uppercase tracking-widest text-textSec">Sticker Packs</span>
                    <span className="text-lg font-bold">{filteredPacks.length} Packs</span>
                  </div>
                </div>

                {/* Filter Buttons Row */}
                <div className="flex items-center gap-2 flex-wrap">
                  {/* Status Filter */}
                  <div className="relative">
                      <button
                        ref={statusFilterRef}
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
                        {statusFilter === 'all' ? 'Filter' : statusFilter.toUpperCase()}
                        <ChevronDown size={14} className={cn("transition-transform", showFilterDropdown && "rotate-180")} />
                      </button>

                      {showFilterDropdown && createPortal(
                        <>
                          <div className="fixed inset-0 z-[9990]" onClick={() => setShowFilterDropdown(false)} />
                          <div className="fixed w-48 glass rounded-2xl border border-white/10 shadow-2xl py-2 z-[9991] animate-in fade-in zoom-in-95 duration-200"
                            style={{ top: getDropdownPos(statusFilterRef).top, left: getDropdownPos(statusFilterRef).left }}>
                            {[
                              { id: 'all', label: 'All', icon: Grid },
                              { id: 'active', label: 'Active Packs', icon: Check },
                              { id: 'passive', label: 'Inactive Packs', icon: X },
                              { id: 'animated', label: 'Animated Packs', icon: RefreshCcw },
                              { id: 'static', label: 'Static Packs', icon: ImageIcon },
                              { id: 'premium', label: 'Premium Packs', icon: Crown },
                              { id: 'new', label: 'Recently Added', icon: Clock }
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
                        </>,
                        document.body
                      )}
                    </div>

                    {/* Category Filter */}
                    <div className="relative">
                      <button
                        ref={categoryFilterRef}
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
                        {categoryFilter === 'all' ? 'Category' : CATEGORIES.find(c => c.id === categoryFilter)?.name.toUpperCase()}
                        <ChevronDown size={14} className={cn("transition-transform", showCategoryDropdown && "rotate-180")} />
                      </button>

                      {showCategoryDropdown && createPortal(
                        <>
                          <div className="fixed inset-0 z-[9990]" onClick={() => setShowCategoryDropdown(false)} />
                          <div className="fixed w-56 glass rounded-2xl border border-white/10 shadow-2xl py-2 z-[9991] animate-in fade-in zoom-in-95 duration-200 max-h-[400px] overflow-y-auto custom-scrollbar"
                            style={{ top: getDropdownPos(categoryFilterRef).top, left: getDropdownPos(categoryFilterRef).left }}>
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
                              All Categories
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
                        </>,
                        document.body
                      )}
                    </div>
                  </div>
                </div>

              <div className="flex-1 overflow-y-auto px-3 pb-20 space-y-2 flex flex-col custom-scrollbar">
                <button
                  onClick={() => setShowNewPackModal(true)}
                  className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-white/10 hover:border-primary/50 hover:bg-primary/5 rounded-2xl group transition-all mb-3"
                >
                  <Plus className="text-textSec group-hover:text-primary transition-colors" size={18} />
                  <span className="text-sm font-bold text-textSec group-hover:text-primary">Create New Pack</span>
                </button>

                {filteredPacks.map(pack => (
                  <div
                    key={pack.id}
                    onClick={() => {
                      setSelectedPack(pack);
                    }}
                    className={cn(
                      "group relative cursor-pointer p-3 rounded-2xl transition-all duration-300 border",
                      selectedPack?.id === pack.id
                        ? 'bg-primary/10 border-primary/50 shadow-xl shadow-primary/5 scale-[1.02]'
                        : 'bg-card border-white/5 hover:border-white/20 hover:bg-hover active:scale-[0.98]'
                    )}
                  >
                    <div className="flex items-center gap-3">
                      <div className="relative w-12 h-12 bg-hover rounded-2xl overflow-hidden glass flex-shrink-0 flex items-center justify-center group-hover:scale-105 transition-transform">
                        {pack.tray_url ? (
                          <img src={pack.tray_url} alt="" className="w-9 h-9 object-contain" />
                        ) : (
                          <Package className="w-5 h-5 text-textSec" />
                        )}
                        {/* Pack Type Icon Kaldirildi */}
                        {pack.is_active === false && (
                          <div className="absolute bottom-0 left-0 right-0 bg-danger/80 py-0.5 flex items-center justify-center">
                            <span className="text-[7px] text-white font-black tracking-widest">INACTIVE</span>
                          </div>
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <h3 className="font-bold text-[13px] truncate text-white">
                          {pack.name}
                        </h3>
                        <div className="flex items-center gap-2 mt-1">
                          <span className="text-[10px] bg-white/5 px-2 py-0.5 rounded-full text-textSec font-semibold">
                            {pack.sticker_count} Sticker
                          </span>
                          {pack.category && (
                            <span className="text-[10px] text-primary font-bold uppercase tracking-tighter">{pack.category}</span>
                          )}
                          {isNew(pack) && (
                            <span className="px-1.5 py-0.5 bg-accent/20 text-accent text-[8px] font-black rounded-md animate-pulse">NEW</span>
                          )}
                        </div>
                      </div>
                      <div className="flex flex-col items-end opacity-0 group-hover:opacity-100 transition-opacity">
                        <button
                          onClick={(e) => { e.stopPropagation(); deletePack(pack); }}
                          className="p-1.5 hover:text-danger hover:bg-danger/10 rounded-xl transition-all active:scale-90"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Details Panel */}
            {selectedPack ? (
              <div className="flex-1 flex flex-col bg-background/80 overflow-hidden h-full animate-in fade-in slide-in-from-right-10 duration-500">
                {/* Compact Detail Header */}
                <div className="shrink-0 px-5 py-4 border-b border-white/5 bg-card/20 backdrop-blur-xl">
                  {/* Mobile Back Button */}
                  <button
                    onClick={() => setSelectedPack(null)}
                    className="md:hidden flex items-center gap-2 text-textSec hover:text-white mb-3"
                  >
                    <ChevronRight className="rotate-180" size={20} />
                    <span className="text-sm font-bold">Back to List</span>
                  </button>

                  <div className="flex items-center gap-4 flex-wrap">
                    {/* Tray Image */}
                    <div className="w-14 h-14 bg-card rounded-2xl overflow-hidden glass flex items-center justify-center p-2 shadow-lg shrink-0">
                      {selectedPack.tray_url ? (
                        <img src={selectedPack.tray_url} alt="" className="w-full h-full object-contain" />
                      ) : (
                        <Package className="w-8 h-8 text-textSec/20" />
                      )}
                    </div>

                    {/* Pack Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h2 className="text-xl font-black tracking-tight text-white truncate">{selectedPack.name}</h2>
                        {/* Tip Badge Kaldirildi */}
                        {selectedPack.is_active === false && (
                          <span className="px-2.5 py-1 rounded-full text-[9px] font-black uppercase tracking-widest bg-danger text-white shrink-0">INACTIVE</span>
                        )}
                      </div>
                      <div className="flex items-center gap-3 mt-1 text-xs text-textSec">
                        <span className="flex items-center gap-1"><UserIcon size={12} /> {selectedPack.publisher}</span>
                        <span className="w-1 h-1 bg-white/20 rounded-full" />
                        <span className="text-primary font-bold uppercase tracking-tighter">{selectedPack.category}</span>
                        <span className="w-1 h-1 bg-white/20 rounded-full" />
                        <span>{selectedPack.sticker_count} Sticker</span>
                      </div>
                    </div>

                    {/* Stats */}
                    <div className="flex items-center gap-3 shrink-0">
                      <StatCard label="Downloads" value={selectedPack.download_count} color="primary" />
                      <StatCard label="Views" value={selectedPack.view_count} color="accent" />
                      <button
                        onClick={() => resetStats(selectedPack)}
                        className="p-2 bg-white/5 hover:bg-white/10 rounded-xl transition-all text-textSec hover:text-white"
                        title="Reset Statistics"
                      >
                        <RefreshCcw size={16} />
                      </button>
                    </div>

                    {/* Action Buttons */}
                    <div className="flex items-center gap-2 shrink-0">
                      <label className="relative flex items-center gap-1.5 px-4 py-2.5 bg-primary hover:bg-primary/90 text-white rounded-xl text-xs font-black shadow-lg shadow-primary/20 transition-all hover:translate-y-[-1px] active:translate-y-0 cursor-pointer">
                        <Plus size={16} className="stroke-[3]" /> Add Sticker
                        <input type="file" multiple className="hidden" onChange={handleAddSticker} disabled={isProcessing} />
                      </label>
                      <button
                        onClick={() => {
                          setEditFormData({
                            ...selectedPack
                          });
                          setShowEditPackModal(true);
                        }}
                        className="flex items-center gap-1.5 px-4 py-2.5 bg-card hover:bg-hover border border-white/5 rounded-xl text-xs font-bold transition-all text-textSec hover:text-textMain"
                      >
                        <Settings size={16} /> Edit
                      </button>
                    </div>
                  </div>
                </div>

                {/* Sticker Grid */}
                <div className="flex-1 overflow-y-auto p-4 md:p-6 custom-scrollbar pb-24 md:pb-6">
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 mb-5">
                    <h3 className="text-base font-bold flex items-center gap-2">
                      <Grid className="text-primary" size={20} />
                      Pack Contents
                      <span className="bg-white/5 px-2 py-0.5 rounded-lg text-[10px] font-mono">{selectedPack.sticker_count} FILES</span>
                      {checkingSizes && (
                        <span className="text-xs text-textSec animate-pulse">Checking sizes...</span>
                      )}
                    </h3>
                    <div className="flex items-center gap-3 text-xs text-textSec font-bold uppercase tracking-widest">
                      {isSelectionMode ? (
                        <div className="flex items-center gap-2 animate-in fade-in slide-in-from-right-4 duration-300">
                          <span className="text-white bg-white/10 px-2 py-1 rounded-lg">
                            {selectedStickerIds.length} Selected
                          </span>
                          <button
                            onClick={toggleSelectAll}
                            className="px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg transition-colors border border-white/5"
                          >
                            {selectedPack.stickers?.length === selectedStickerIds.length ? 'Deselect All' : 'Select All'}
                          </button>
                          <button
                            onClick={deleteSelectedStickers}
                            disabled={selectedStickerIds.length === 0 || isProcessing}
                            className="px-3 py-1.5 bg-danger/20 hover:bg-danger/30 text-danger border border-danger/20 rounded-lg transition-colors flex items-center gap-1.5 disabled:opacity-50 disabled:cursor-not-allowed"
                          >
                            <Trash2 size={14} />
                            Sil
                          </button>
                          <button
                            onClick={() => {
                              setIsSelectionMode(false);
                              setSelectedStickerIds([]);
                            }}
                            className="px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg transition-colors border border-white/5"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <div className="flex items-center gap-4">
                          <button
                            onClick={() => setIsSelectionMode(true)}
                            className="px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg transition-colors border border-white/5 flex items-center gap-1.5"
                          >
                            <Check size={14} />
                            Multi Select
                          </button>
                          <span className="hidden md:flex items-center gap-1.5"><Info size={14} /> Live Cloud Preview</span>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* WhatsApp 500KB Limit Uyarısı */}
                  {oversizedStickersCount > 0 && !checkingSizes && (
                    <div className="mb-5 p-3 bg-danger/20 border border-danger/50 rounded-2xl flex items-center gap-3">
                      <div className="p-2 bg-danger/30 rounded-xl">
                        <X size={18} className="text-danger" />
                      </div>
                      <div>
                        <p className="text-danger font-bold text-sm">
                          {oversizedStickersCount} stickers exceed the WhatsApp 500KB limit!
                        </p>
                        <p className="text-danger/70 text-xs mt-0.5">
                          These stickers will cause errors when adding to WhatsApp. Please delete and re-upload.
                        </p>
                      </div>
                    </div>
                  )}

                  <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 2xl:grid-cols-8 gap-3 md:gap-4">
                    {selectedPack.stickers?.map((sticker, idx) => {
                      const stickerSize = stickerSizes[sticker.image_file];
                      const isOversized = stickerSize && stickerSize > 500 * 1024;
                      const sizeKB = stickerSize ? Math.round(stickerSize / 1024) : null;

                      return (
                        <div
                          key={idx}
                          draggable={!isSelectionMode}
                          onDragStart={(e) => {
                            if (isSelectionMode) { e.preventDefault(); return; }
                            setPanelDragIdx(idx);
                            e.dataTransfer.effectAllowed = 'move';
                            e.currentTarget.style.opacity = '0.4';
                          }}
                          onDragEnd={(e) => {
                            e.currentTarget.style.opacity = '1';
                            if (panelDragIdx !== null && panelDragOverIdx !== null && panelDragIdx !== panelDragOverIdx && selectedPack) {
                              moveStickerPosition(selectedPack, panelDragIdx, panelDragOverIdx);
                            }
                            setPanelDragIdx(null);
                            setPanelDragOverIdx(null);
                          }}
                          onDragOver={(e) => {
                            e.preventDefault();
                            e.dataTransfer.dropEffect = 'move';
                            setPanelDragOverIdx(idx);
                          }}
                          onDragLeave={() => {
                            if (panelDragOverIdx === idx) setPanelDragOverIdx(null);
                          }}
                          className={cn(
                            "group relative aspect-square bg-card/50 rounded-2xl glass p-4 transition-all duration-300 shadow-lg hover:shadow-2xl",
                            isSelectionMode ? "cursor-pointer" : "cursor-grab active:cursor-grabbing",
                            panelDragOverIdx === idx && panelDragIdx !== null
                              ? "ring-2 ring-primary/60 bg-primary/10 scale-105 shadow-primary/20"
                              : isOversized
                                ? "ring-2 ring-danger/70 hover:ring-danger"
                                : "hover:ring-2 hover:ring-primary/50 hover:shadow-primary/5"
                          )}
                          onClick={() => !isSelectionMode && setPreviewSticker({ url: sticker.url, title: sticker.image_file })}
                        >
                          {/* Boyut Aşımı İkonu */}
                          {isOversized && (
                            <div className="absolute top-2 right-2 z-10 p-1.5 bg-danger rounded-lg shadow-lg" title={`${sizeKB}KB - Exceeds WhatsApp limit!`}>
                              <X size={14} className="text-white" />
                            </div>
                          )}

                          <div className="w-full h-full flex items-center justify-center pointer-events-none">
                            <img
                              src={sticker.url}
                              alt=""
                              className={cn(
                                "w-full h-full object-contain transition-transform duration-500",
                                isSelectionMode && selectedStickerIds.includes(sticker.url) ? "scale-75" : "group-hover:scale-110"
                              )}
                            />
                          </div>

                          {/* Selection Overlay */}
                          {isSelectionMode && (
                            <div
                              className="absolute inset-0 z-20 cursor-pointer flex items-start justify-end p-2"
                              onClick={(e) => {
                                e.stopPropagation();
                                toggleStickerSelection(sticker.url);
                              }}
                            >
                              <div className={cn(
                                "w-6 h-6 rounded-full border-2 flex items-center justify-center transition-all",
                                selectedStickerIds.includes(sticker.url)
                                  ? "bg-primary border-primary"
                                  : "bg-black/40 border-white/50 hover:border-white"
                              )}>
                                {selectedStickerIds.includes(sticker.url) && <Check size={14} className="text-white" />}
                              </div>
                            </div>
                          )}

                          <div className={cn(
                            "absolute inset-0 bg-background/60 transition-opacity flex flex-col items-center justify-center gap-2 rounded-2xl backdrop-blur-[2px]",
                            isSelectionMode ? "opacity-0 pointer-events-none" : "opacity-0 group-hover:opacity-100"
                          )} onClick={(e) => { e.stopPropagation(); setPreviewSticker({ url: sticker.url, title: sticker.image_file }); }}>
                            {/* Action Buttons */}
                            <div className="flex gap-1.5">
                              <button
                                onClick={(e) => { e.stopPropagation(); deleteSticker(selectedPack, sticker); }}
                                className="p-2 bg-danger hover:bg-danger/80 text-white rounded-xl shadow-lg transition-all hover:scale-110"
                                title="Delete"
                              >
                                <Trash2 size={16} />
                              </button>
                              <button
                                onClick={(e) => { e.stopPropagation(); setAsTray(selectedPack, sticker); }}
                                className="p-2 bg-accent hover:bg-accent/80 text-white rounded-xl shadow-lg transition-all hover:scale-110"
                                title="Set as Cover"
                              >
                                <ImageIcon size={16} />
                              </button>
                            </div>
                            <span className="text-[10px] font-black text-white/60">#{idx + 1}</span>
                          </div>

                          {/* Boyut Bilgisi */}
                          <div className={cn(
                            "absolute bottom-2 left-2 right-2 flex justify-between items-center text-[9px] font-black transition-colors opacity-0 group-hover:opacity-100 tracking-tighter",
                            isOversized ? "text-danger" : "text-textSec group-hover:text-primary"
                          )}>
                            <span>{sticker.image_file.substring(0, 10).toUpperCase()}</span>
                            {sizeKB !== null && (
                              <span className={cn(
                                "px-1.5 py-0.5 rounded",
                                isOversized ? "bg-danger/30 text-danger" : "bg-white/10"
                              )}>
                                {sizeKB}KB
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex-1 flex flex-col items-center justify-center">
                {/* Boş Durum - Temizlendi */}
              </div>
            )}
          </div>
        ) : activeTab === 'stats' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-12 custom-scrollbar bg-background">
            <div className="max-w-7xl mx-auto space-y-6 md:space-y-8 animate-in fade-in duration-500">
              {/* Header Card */}
              <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div className="flex items-center gap-4">
                    <div className="w-14 h-14 bg-gradient-to-br from-purple-500/20 to-purple-500/20 rounded-2xl flex items-center justify-center border border-purple-500/10 shadow-lg shadow-purple-500/5">
                      <BarChart3 size={26} className="text-purple-400" />
                    </div>
                    <div>
                      <h2 className="text-2xl font-black text-white tracking-tight">Performance Analysis</h2>
                      <p className="text-xs text-textSec mt-0.5">App-wide engagement and performance report</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {/* Stats Filter */}
                    <div className="relative">
                      <button
                        ref={statsFilterRef}
                        onClick={() => setShowFilterDropdown(!showFilterDropdown)}
                        className={cn(
                          "flex items-center gap-2 px-4 py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all border",
                          statsFilter !== 'all'
                            ? "bg-primary/10 border-primary/20 text-primary"
                            : "bg-white/5 border-white/5 text-textSec hover:bg-white/10"
                        )}
                      >
                        <Filter size={13} />
                        {statsFilter === 'all' ? 'Filter' : statsFilter.toUpperCase()}
                        <ChevronDown size={13} className={cn("transition-transform duration-300", showFilterDropdown && "rotate-180")} />
                      </button>

                      {showFilterDropdown && createPortal(
                        <>
                          <div className="fixed inset-0 z-[9990]" onClick={() => setShowFilterDropdown(false)} />
                          <div className="fixed w-52 glass rounded-2xl border border-white/10 shadow-2xl py-2 z-[9991] animate-in fade-in zoom-in-95 duration-200"
                            style={{ top: getDropdownPos(statsFilterRef).top, left: getDropdownPos(statsFilterRef).left }}>
                            <div className="px-4 py-2 mb-1 border-b border-white/5">
                              <span className="text-[9px] font-black text-textSec uppercase tracking-widest">View</span>
                            </div>
                            {[
                              { id: 'all', label: 'All Packs', icon: Grid, color: 'text-white' },
                              { id: 'popular', label: 'Popular', icon: Star, color: 'text-yellow-400' },
                              { id: 'active', label: 'Active', icon: Check, color: 'text-primary' },
                              { id: 'passive', label: 'Inactive', icon: X, color: 'text-danger' },
                              { id: 'premium', label: 'Premium', icon: Crown, color: 'text-yellow-400' },
                              { id: 'normal', label: 'Free', icon: Shield, color: 'text-textSec' }
                            ].map(f => (
                              <button
                                key={f.id}
                                onClick={() => {
                                  setStatsFilter(f.id as any);
                                  setShowFilterDropdown(false);
                                }}
                                className={cn(
                                  "w-full flex items-center gap-3 px-4 py-2.5 text-xs font-bold transition-all",
                                  statsFilter === f.id ? "bg-white/10 text-white" : "text-textSec hover:text-white hover:bg-white/5"
                                )}
                              >
                                <f.icon size={14} className={f.color} />
                                {f.label}
                              </button>
                            ))}
                          </div>
                        </>,
                        document.body
                      )}
                    </div>

                    <button
                      onClick={fetchPacks}
                      className="p-2.5 bg-white/5 hover:bg-white/10 border border-white/5 rounded-xl text-textSec transition-all"
                      title="Refresh Data"
                    >
                      <RefreshCcw size={14} className={loading ? 'animate-spin text-primary' : ''} />
                    </button>
                  </div>
                </div>
              </div>

              {/* Fake Base Controls */}
              <div className="glass rounded-2xl p-4 border border-white/5">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="text-[10px] font-black text-textSec uppercase tracking-widest shrink-0">Fake Download Range:</span>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      value={fakeBaseMin}
                      onChange={(e) => setFakeBaseMin(Number(e.target.value))}
                      className="w-24 px-3 py-2 bg-white/[0.03] border border-white/5 rounded-xl text-white text-sm outline-none focus:border-primary/50 transition-all"
                      placeholder="Min"
                    />
                    <span className="text-textSec/40">—</span>
                    <input
                      type="number"
                      value={fakeBaseMax}
                      onChange={(e) => setFakeBaseMax(Number(e.target.value))}
                      className="w-24 px-3 py-2 bg-white/[0.03] border border-white/5 rounded-xl text-white text-sm outline-none focus:border-primary/50 transition-all"
                      placeholder="Max"
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => updateAllPacksWithFakeBase(false)}
                      disabled={isProcessing}
                      className="px-4 py-2 bg-primary/10 hover:bg-primary/20 border border-primary/10 rounded-xl text-primary text-[10px] font-black uppercase tracking-widest transition-all disabled:opacity-50"
                    >
                      {isProcessing ? '..' : 'Missing Only'}
                    </button>
                    <button
                      onClick={() => updateAllPacksWithFakeBase(true)}
                      disabled={isProcessing}
                      className="px-4 py-2 bg-yellow-500/10 hover:bg-yellow-500/20 border border-yellow-500/10 rounded-xl text-yellow-400 text-[10px] font-black uppercase tracking-widest transition-all disabled:opacity-50"
                    >
                      {isProcessing ? '..' : 'All Packs'}
                    </button>
                  </div>
                </div>
              </div>

              {/* Popular Empty State */}
              {statsFilter === 'popular' && packs.filter(p => p.is_popular === true).length === 0 && (
                <div className="glass rounded-2xl p-8 border border-yellow-500/20 text-center space-y-3">
                  <Star size={40} className="text-yellow-500/40 mx-auto" />
                  <h3 className="text-lg font-black text-white">No Popular Packs Yet</h3>
                  <p className="text-xs text-textSec max-w-md mx-auto">
                    No packs have been marked as popular. Go to any pack's edit modal and toggle <span className="text-yellow-400 font-bold">⭐ POPULAR</span> to feature it on the home page and see its stats here.
                  </p>
                </div>
              )}

              {/* Metrics Grid */}
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
                {((): any => {
                  const sPacks = packs.filter(p => {
                    if (statsFilter === 'all') return true;
                    if (statsFilter === 'popular') return p.is_popular === true;
                    if (statsFilter === 'active') return p.is_active !== false;
                    if (statsFilter === 'passive') return p.is_active === false;
                    if (statsFilter === 'premium') return p.is_premium === true;
                    if (statsFilter === 'normal') return p.is_premium === false;
                    return true;
                  });

                  const totalDL = sPacks.reduce((acc, p) => acc + (p.download_count || 0), 0);
                  const totalViews = sPacks.reduce((acc, p) => acc + (p.view_count || 0), 0);
                  const totalStickers = sPacks.reduce((acc, p) => acc + (p.sticker_count || 0), 0);
                  const totalFavorites = sPacks.reduce((acc, p) => acc + (p.favorite_count || 0), 0);
                  const avgCVR = totalViews > 0 ? (totalDL / totalViews) * 100 : 0;

                  return (
                    <>
                      <div className="glass rounded-xl p-4 border border-primary/10 group hover:border-primary/30 transition-all">
                        <div className="flex items-center gap-3 mb-3">
                          <div className="w-10 h-10 bg-primary/10 rounded-xl flex items-center justify-center">
                            <TrendingUp size={18} className="text-primary" />
                          </div>
                          <span className="text-[8px] font-black text-primary/60 bg-primary/5 px-1.5 py-0.5 rounded uppercase tracking-widest">Downloads</span>
                        </div>
                        <p className="text-2xl font-black text-white">{totalDL.toLocaleString()}</p>
                      </div>

                      <div className="glass rounded-xl p-4 border border-accent/10 group hover:border-accent/30 transition-all">
                        <div className="flex items-center gap-3 mb-3">
                          <div className="w-10 h-10 bg-accent/10 rounded-xl flex items-center justify-center">
                            <BarChart3 size={18} className="text-accent" />
                          </div>
                          <span className="text-[8px] font-black text-accent/60 bg-accent/5 px-1.5 py-0.5 rounded uppercase tracking-widest">Views</span>
                        </div>
                        <p className="text-2xl font-black text-white">{totalViews.toLocaleString()}</p>
                      </div>

                      <div className="glass rounded-xl p-4 border border-yellow-500/10 group hover:border-yellow-500/30 transition-all">
                        <div className="flex items-center gap-3 mb-3">
                          <div className="w-10 h-10 bg-yellow-500/10 rounded-xl flex items-center justify-center">
                            <Lightbulb size={18} className="text-yellow-400" />
                          </div>
                          <span className="text-[8px] font-black text-yellow-400/60 bg-yellow-500/5 px-1.5 py-0.5 rounded uppercase tracking-widest">CVR</span>
                        </div>
                        <p className="text-2xl font-black text-white">%{avgCVR.toFixed(1)}</p>
                        <div className="w-full h-1 bg-white/5 rounded-full overflow-hidden mt-2">
                          <div className="h-full bg-yellow-400 rounded-full" style={{ width: `${Math.min(100, avgCVR)}%` }} />
                        </div>
                      </div>

                      <div className="glass rounded-xl p-4 border border-purple-500/10 group hover:border-purple-500/30 transition-all">
                        <div className="flex items-center gap-3 mb-3">
                          <div className="w-10 h-10 bg-purple-500/10 rounded-xl flex items-center justify-center">
                            <Grid size={18} className="text-purple-400" />
                          </div>
                          <span className="text-[8px] font-black text-purple-400/60 bg-purple-500/5 px-1.5 py-0.5 rounded uppercase tracking-widest">Sticker</span>
                        </div>
                        <p className="text-2xl font-black text-white">{totalStickers.toLocaleString()}</p>
                        <p className="text-[9px] font-bold text-textSec/50 mt-0.5">{sPacks.length} packs</p>
                      </div>

                      <div className="glass rounded-xl p-4 border border-pink-500/10 group hover:border-pink-500/30 transition-all">
                        <div className="flex items-center gap-3 mb-3">
                          <div className="w-10 h-10 bg-pink-500/10 rounded-xl flex items-center justify-center">
                            <Crown size={18} className="text-pink-400" />
                          </div>
                          <span className="text-[8px] font-black text-pink-400/60 bg-pink-500/5 px-1.5 py-0.5 rounded uppercase tracking-widest">Favorites</span>
                        </div>
                        <p className="text-2xl font-black text-white">{totalFavorites.toLocaleString()}</p>
                      </div>

                      <div className="glass rounded-xl p-4 border border-cyan-500/10 group hover:border-cyan-500/30 transition-all">
                        <div className="flex items-center gap-3 mb-3">
                          <div className="w-10 h-10 bg-cyan-500/10 rounded-xl flex items-center justify-center">
                            <Users size={18} className="text-cyan-400" />
                          </div>
                          <span className="text-[8px] font-black text-cyan-400/60 bg-cyan-500/5 px-1.5 py-0.5 rounded uppercase tracking-widest">Users</span>
                        </div>
                        <p className="text-2xl font-black text-white">{usersData.length.toLocaleString()}</p>
                        <p className="text-[9px] font-bold text-textSec/50 mt-0.5">{userStats.premium} premium</p>
                      </div>
                    </>
                  );
                })()}
              </div>

              {/* Chart & Ranking Section */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Visual Analysis */}
                <div className="lg:col-span-2 glass rounded-2xl p-6 md:p-8 border border-white/5 overflow-hidden">
                  <div className="flex items-center justify-between mb-6">
                    <div>
                      <h3 className="text-lg font-black text-white tracking-tight">Trend Analysis</h3>
                      <p className="text-[10px] text-textSec mt-0.5">Performance comparison of top 10 packs</p>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="flex items-center gap-1.5">
                        <div className="w-2.5 h-2.5 rounded-full bg-primary" />
                        <span className="text-[9px] font-bold text-textSec">Downloads</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <div className="w-2.5 h-2.5 rounded-full bg-accent" />
                        <span className="text-[9px] font-bold text-textSec">Views</span>
                      </div>
                    </div>
                  </div>

                  <div className="h-[350px] w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={packs
                          .filter(p => {
                            if (statsFilter === 'all') return true;
                            if (statsFilter === 'popular') return p.is_popular === true;
                            if (statsFilter === 'active') return p.is_active !== false;
                            if (statsFilter === 'passive') return p.is_active === false;
                            if (statsFilter === 'premium') return p.is_premium === true;
                            if (statsFilter === 'normal') return p.is_premium === false;
                            return true;
                          })
                          .sort((a, b) => (b.download_count || 0) - (a.download_count || 0))
                          .slice(0, 10)
                          .map(p => {
                            const pName = p.name || 'Unnamed Pack';
                            return {
                              name: pName.length > 10 ? pName.substring(0, 8) + '..' : pName,
                              downloads: p.download_count || 0,
                              views: p.view_count || 0
                            };
                          })}
                        margin={{ top: 10, right: 10, left: 0, bottom: 20 }}
                        barGap={8}
                      >
                        <defs>
                          <linearGradient id="gPrimary" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="#6C5CE7" stopOpacity={1} />
                            <stop offset="100%" stopColor="#6C5CE7" stopOpacity={0.4} />
                          </linearGradient>
                          <linearGradient id="gAccent" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="#A78BFA" stopOpacity={1} />
                            <stop offset="100%" stopColor="#A78BFA" stopOpacity={0.4} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="5 5" stroke="rgba(255,255,255,0.03)" vertical={false} />
                        <XAxis dataKey="name" stroke="rgba(255,255,255,0.2)" fontSize={10} fontWeight="700" axisLine={false} tickLine={false} dy={12} />
                        <YAxis stroke="rgba(255,255,255,0.2)" fontSize={10} fontWeight="700" axisLine={false} tickLine={false} tickFormatter={(v) => v >= 1000 ? `${v / 1000}k` : v} />
                        <Tooltip
                          contentStyle={{ backgroundColor: '#1A1D21', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)', padding: '12px' }}
                          cursor={{ fill: 'rgba(255,255,255,0.02)' }}
                          itemStyle={{ fontWeight: '800', fontSize: '12px' }}
                        />
                        <Bar dataKey="downloads" fill="url(#gPrimary)" radius={[6, 6, 2, 2]} name="Downloads" barSize={20} />
                        <Bar dataKey="views" fill="url(#gAccent)" radius={[6, 6, 2, 2]} name="Views" barSize={20} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                {/* Leaderboard */}
                <div className="glass rounded-2xl border border-white/5 flex flex-col overflow-hidden">
                  <div className="p-5 border-b border-white/5 bg-white/[0.02]">
                    <h3 className="text-sm font-black text-white uppercase tracking-wider">🏆 Leaderboard</h3>
                    <p className="text-[9px] font-bold text-textSec mt-0.5">Top 5 most downloaded</p>
                  </div>
                  <div className="flex-1 p-4 space-y-2.5">
                    {packs
                      .filter(p => {
                        if (statsFilter === 'all') return true;
                        if (statsFilter === 'popular') return p.is_popular === true;
                        if (statsFilter === 'active') return p.is_active !== false;
                        if (statsFilter === 'passive') return p.is_active === false;
                        if (statsFilter === 'premium') return p.is_premium === true;
                        if (statsFilter === 'normal') return p.is_premium === false;
                        return true;
                      })
                      .sort((a, b) => (b.download_count || 0) - (a.download_count || 0))
                      .slice(0, 5)
                      .map((p, i) => (
                        <div key={p.id} className="flex items-center gap-3 p-3 rounded-xl bg-white/[0.02] border border-white/5 hover:bg-white/[0.05] hover:border-white/10 transition-all group">
                          <div className={cn(
                            "w-7 h-7 rounded-lg flex items-center justify-center font-black text-[10px] shrink-0",
                            i === 0 ? "bg-amber-400 text-black" :
                              i === 1 ? "bg-slate-300 text-black" :
                                i === 2 ? "bg-amber-700 text-white" : "bg-white/5 text-textSec"
                          )}>
                            {i + 1}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="text-xs font-bold text-white truncate group-hover:text-primary transition-colors">{p.name || 'Unnamed Pack'}</div>
                            <div className="text-[9px] font-bold text-textSec/50">{p.category}</div>
                          </div>
                          <div className="text-right shrink-0">
                            <div className="text-xs font-black text-primary">{(p.download_count || 0).toLocaleString()}</div>
                          </div>
                        </div>
                      ))}
                  </div>
                  <div className="px-5 py-3 border-t border-white/5 bg-white/[0.02]">
                    <div className="flex items-center justify-between">
                      <span className="text-[9px] font-bold text-textSec/50 uppercase tracking-widest">Scope</span>
                      <span className="text-[9px] font-black text-primary bg-primary/10 px-2 py-0.5 rounded-md border border-primary/10">{statsFilter.toUpperCase()}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* User Preference Intelligence */}
              <div className="glass rounded-2xl p-6 border border-white/5">
                <h3 className="text-sm font-black text-white tracking-tight mb-2 flex items-center gap-2">
                  <Sparkles size={16} className="text-yellow-400" /> User Preference Intelligence
                </h3>
                <p className="text-[9px] text-textSec mb-5">What types of stickers your users love most — powered by engagement analysis</p>
                {(() => {
                  const activePacks = packs.filter(p => p.is_active !== false);
                  // Category performance analysis
                  const catMap = new Map<string, { downloads: number; views: number; favorites: number; packs: number; totalStickers: number }>();
                  activePacks.forEach(p => {
                    const cat = p.category || 'uncategorized';
                    const prev = catMap.get(cat) || { downloads: 0, views: 0, favorites: 0, packs: 0, totalStickers: 0 };
                    catMap.set(cat, {
                      downloads: prev.downloads + (p.download_count || 0),
                      views: prev.views + (p.view_count || 0),
                      favorites: prev.favorites + (p.favorite_count || 0),
                      packs: prev.packs + 1,
                      totalStickers: prev.totalStickers + (p.sticker_count || 0)
                    });
                  });
                  const catArr = Array.from(catMap.entries())
                    .map(([name, d]) => ({
                      name,
                      ...d,
                      cvr: d.views > 0 ? (d.downloads / d.views) * 100 : 0,
                      engagementPerPack: d.packs > 0 ? (d.downloads + d.favorites * 3) / d.packs : 0
                    }))
                    .sort((a, b) => b.engagementPerPack - a.engagementPerPack);
                  const maxEng = catArr[0]?.engagementPerPack || 1;

                  return (
                    <div className="space-y-2.5">
                      {catArr.map((cat, i) => (
                        <div key={cat.name} className="group">
                          <div className="flex items-center gap-3 mb-1">
                            <span className="text-[10px] font-black text-white uppercase w-28 truncate">{cat.name}</span>
                            <div className="flex-1 h-5 bg-white/5 rounded-full overflow-hidden relative">
                              <div className="absolute inset-y-0 left-0 rounded-full transition-all duration-500" style={{
                                width: `${(cat.engagementPerPack / maxEng) * 100}%`,
                                background: i === 0 ? 'linear-gradient(90deg, #f59e0b, #ef4444)' :
                                  i === 1 ? 'linear-gradient(90deg, #8b5cf6, #6366f1)' :
                                  i === 2 ? 'linear-gradient(90deg, #06b6d4, #3b82f6)' :
                                  'linear-gradient(90deg, rgba(255,255,255,0.15), rgba(255,255,255,0.08))'
                              }} />
                              <div className="absolute inset-0 flex items-center px-2">
                                <span className="text-[8px] font-black text-white/90 drop-shadow">{Math.round(cat.engagementPerPack)} eng/pack</span>
                              </div>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                              <span className="text-[9px] font-bold text-primary">{cat.downloads}↓</span>
                              <span className="text-[9px] font-bold text-yellow-400">{cat.favorites}♥</span>
                              <span className="text-[9px] font-bold text-textSec">{cat.cvr.toFixed(1)}%</span>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  );
                })()}
              </div>

              {/* App Health & Improvement Insights */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div className="glass rounded-2xl p-6 border border-white/5">
                  <h3 className="text-sm font-black text-white tracking-tight mb-4 flex items-center gap-2">
                    <BarChart3 size={16} className="text-primary" /> Content Health
                  </h3>
                  <div className="space-y-3">
                    {(() => {
                      const activePacks = packs.filter(p => p.is_active !== false);
                      const animatedPacks = packs.filter(p => p.is_animated);
                      const premiumPacks = packs.filter(p => p.is_premium);
                      const popularPacks = packs.filter(p => p.is_popular === true);
                      const zeroDLPacks = activePacks.filter(p => (p.download_count || 0) === 0);
                      const avgStickersPerPack = activePacks.length > 0 ? Math.round(activePacks.reduce((a, p) => a + (p.sticker_count || 0), 0) / activePacks.length) : 0;
                      const telegramPacks = packs.filter(p => p.batch_source === 'telegram');
                      const giphyPacks = packs.filter(p => p.batch_source === 'giphy' || p.batch_source === 'klipy');

                      return [
                        { label: 'Total Packs', value: packs.length, color: 'text-white' },
                        { label: 'Active / Inactive', value: `${activePacks.length} / ${packs.length - activePacks.length}`, color: 'text-green-400' },
                        { label: '⭐ Popular Packs', value: popularPacks.length, color: 'text-yellow-400' },
                        { label: 'Animated Packs', value: animatedPacks.length, color: 'text-cyan-400' },
                        { label: 'Premium Packs', value: premiumPacks.length, color: 'text-yellow-400' },
                        { label: 'Telegram Content', value: telegramPacks.length, color: 'text-sky-400' },
                        { label: 'Giphy/Klipy Content', value: giphyPacks.length, color: 'text-orange-400' },
                        { label: 'Avg. Sticker/Pack', value: avgStickersPerPack, color: 'text-purple-400' },
                        { label: '⚠️ 0 Downloads (active)', value: zeroDLPacks.length, color: zeroDLPacks.length > 5 ? 'text-red-400' : 'text-green-400' },
                      ].map((item, idx) => (
                        <div key={idx} className="flex items-center justify-between py-1.5 border-b border-white/[0.03] last:border-0">
                          <span className="text-xs text-textSec font-medium">{item.label}</span>
                          <span className={`text-sm font-black ${item.color}`}>{item.value}</span>
                        </div>
                      ));
                    })()}
                  </div>
                </div>

                <div className="glass rounded-2xl p-6 border border-white/5">
                  <h3 className="text-sm font-black text-white tracking-tight mb-4 flex items-center gap-2">
                    <Lightbulb size={16} className="text-yellow-400" /> Algorithm Insights
                  </h3>
                  <p className="text-[9px] text-textSec mb-4">How the ranking algorithm sees your content</p>
                  <div className="space-y-3">
                    {(() => {
                      const suggestions: { icon: string; text: string; severity: 'info' | 'warn' | 'good' }[] = [];
                      const activePacks = packs.filter(p => p.is_active !== false);
                      const animatedRatio = packs.length > 0 ? packs.filter(p => p.is_animated).length / packs.length : 0;
                      const zeroDL = activePacks.filter(p => (p.download_count || 0) === 0).length;
                      const categories = new Set(packs.map(p => p.category).filter(Boolean));
                      const popularCount = packs.filter(p => p.is_popular).length;
                      const highCVR = activePacks.filter(p => (p.view_count || 0) > 10 && ((p.download_count || 0) / (p.view_count || 1)) > 0.15).length;
                      const avgFavPerPack = activePacks.length > 0 ? activePacks.reduce((a, p) => a + (p.favorite_count || 0), 0) / activePacks.length : 0;

                      // Content volume
                      if (packs.length < 50) suggestions.push({ icon: '📦', text: `${packs.length} packs. Target 100+ for organic discovery.`, severity: 'warn' });
                      else suggestions.push({ icon: '✅', text: `${packs.length} packs — solid content library!`, severity: 'good' });

                      // Popular curation
                      if (popularCount === 0) suggestions.push({ icon: '⭐', text: `No popular packs curated. Mark top packs as Popular for home page.`, severity: 'warn' });
                      else if (popularCount > 15) suggestions.push({ icon: '⭐', text: `${popularCount} popular packs — too many dilutes impact. Keep 5-10.`, severity: 'warn' });
                      else suggestions.push({ icon: '⭐', text: `${popularCount} curated popular packs — good curation!`, severity: 'good' });

                      // Engagement quality
                      if (highCVR > 0) suggestions.push({ icon: '🎯', text: `${highCVR} packs have >15% CVR — high quality content performing well.`, severity: 'good' });

                      // Favorites signal
                      if (avgFavPerPack < 0.5) suggestions.push({ icon: '💛', text: `Low favorite rate (${avgFavPerPack.toFixed(1)}/pack). Favorites boost ranking 3x.`, severity: 'info' });
                      else suggestions.push({ icon: '💛', text: `Avg ${avgFavPerPack.toFixed(1)} favorites/pack — users are engaging!`, severity: 'good' });

                      // Animated variety
                      if (animatedRatio < 0.2) suggestions.push({ icon: '🎬', text: `${Math.round(animatedRatio * 100)}% animated. Algorithm gives 8% boost to animated.`, severity: 'info' });

                      // Category diversity
                      if (categories.size < 5) suggestions.push({ icon: '🏷️', text: `Only ${categories.size} categories. More variety = better discovery.`, severity: 'warn' });
                      else suggestions.push({ icon: '🏷️', text: `${categories.size} categories — great diversity for users!`, severity: 'good' });

                      // Zero downloads warning
                      if (zeroDL > activePacks.length * 0.3) suggestions.push({ icon: '📉', text: `${zeroDL} active packs have 0 downloads. Check names/SEO.`, severity: 'warn' });

                      // Premium insights
                      if (usersData.length > 0) {
                        const premUsers = usersData.filter((u: any) => u.isPremium || u.subscription_type).length;
                        const convRate = (premUsers / usersData.length * 100).toFixed(1);
                        suggestions.push({ icon: '👑', text: `Premium conversion: ${convRate}% (${premUsers}/${usersData.length})`, severity: Number(convRate) > 5 ? 'good' : 'info' });
                      }

                      return suggestions.map((s, idx) => (
                        <div key={idx} className={cn(
                          "flex items-start gap-3 p-3 rounded-xl border",
                          s.severity === 'warn' ? 'bg-yellow-500/5 border-yellow-500/10' :
                          s.severity === 'good' ? 'bg-green-500/5 border-green-500/10' :
                          'bg-blue-500/5 border-blue-500/10'
                        )}>
                          <span className="text-base shrink-0">{s.icon}</span>
                          <span className="text-xs text-white/80 font-medium leading-relaxed">{s.text}</span>
                        </div>
                      ));
                    })()}
                  </div>
                </div>
              </div>

              {/* Full Performance List */}
              <div className="glass rounded-2xl border border-white/5 overflow-hidden">
                <div className="px-5 py-4 border-b border-white/5 bg-white/[0.02] flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 bg-primary/10 rounded-xl flex items-center justify-center">
                      <Grid size={16} className="text-primary" />
                    </div>
                    <h3 className="text-sm font-black text-white uppercase tracking-wider">Detailed Performance List</h3>
                  </div>
                </div>
                <div className="overflow-x-auto custom-scrollbar">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-white/[0.02]">
                        <th className="px-5 py-3.5 text-[9px] font-black uppercase tracking-widest text-textSec/60 border-b border-white/5">Pack</th>
                        <th className="px-5 py-3.5 text-[9px] font-black uppercase tracking-widest text-textSec/60 border-b border-white/5 text-center">Downloads</th>
                        <th className="hidden sm:table-cell px-5 py-3.5 text-[9px] font-black uppercase tracking-widest text-textSec/60 border-b border-white/5 text-center">Views</th>
                        <th className="hidden lg:table-cell px-5 py-3.5 text-[9px] font-black uppercase tracking-widest text-textSec/60 border-b border-white/5 text-center">Favorites</th>
                        <th className="hidden md:table-cell px-5 py-3.5 text-[9px] font-black uppercase tracking-widest text-textSec/60 border-b border-white/5 text-right w-56">CVR</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/[0.03]">
                      {packs
                        .filter(p => {
                          if (statsFilter === 'all') return true;
                          if (statsFilter === 'popular') return p.is_popular === true;
                          if (statsFilter === 'active') return p.is_active !== false;
                          if (statsFilter === 'passive') return p.is_active === false;
                          if (statsFilter === 'premium') return p.is_premium === true;
                          if (statsFilter === 'normal') return p.is_premium === false;
                          return true;
                        })
                        .sort((a, b) => (b.download_count || 0) - (a.download_count || 0))
                        .map((p) => (
                          <tr key={p.id} className="hover:bg-white/[0.02] transition-all group">
                            <td className="px-5 py-3.5">
                              <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-xl bg-white/[0.03] border border-white/5 p-1 relative overflow-hidden shrink-0">
                                  <img src={p.tray_url} className="w-full h-full object-contain" />
                                  {p.is_premium && <div className="absolute top-0 right-0 w-2.5 h-2.5 bg-yellow-400 rounded-bl-md" />}
                                </div>
                                <div className="min-w-0">
                                  <div className="font-bold text-white text-sm group-hover:text-primary transition-colors truncate">{p.name || 'Unnamed Pack'}</div>
                                  <div className="flex items-center gap-1.5 mt-0.5">
                                    <span className="text-[9px] font-bold text-textSec/50 uppercase">{p.category}</span>
                                    <span className={cn(
                                      "px-1.5 py-0.5 rounded text-[7px] font-black uppercase",
                                      p.is_active !== false ? "bg-primary/10 text-primary/70" : "bg-red-500/10 text-red-400/70"
                                    )}>
                                      {p.is_active !== false ? 'ACTIVE' : 'INACTIVE'}
                                    </span>
                                  </div>
                                </div>
                              </div>
                            </td>
                            <td className="px-5 py-3.5 text-center">
                              <span className="text-sm font-black text-primary">{(p.download_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="hidden sm:table-cell px-5 py-3.5 text-center">
                              <span className="text-sm font-black text-accent">{(p.view_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="hidden lg:table-cell px-5 py-3.5 text-center">
                              <span className="text-sm font-black text-yellow-400">{(p.favorite_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="hidden md:table-cell px-5 py-3.5">
                              <div className="flex items-center justify-end gap-3">
                                <div className="flex-1 max-w-[100px] h-1.5 bg-white/5 rounded-full overflow-hidden">
                                  <div
                                    className={cn(
                                      "h-full rounded-full transition-all duration-1000",
                                      ((p.download_count || 0) / (p.view_count || 1)) * 100 > 25 ? "bg-primary" : "bg-yellow-400"
                                    )}
                                    style={{ width: `${Math.min(100, ((p.download_count || 0) / (p.view_count || 1)) * 100)}%` }}
                                  />
                                </div>
                                <span className="text-xs font-black text-white tabular-nums w-10 text-right">
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
          <div className="flex-1 overflow-y-auto p-4 md:p-8 custom-scrollbar bg-background">
            <div className="max-w-7xl mx-auto animate-in fade-in duration-500">
              {/* Header */}
              <div className="flex items-center justify-between mb-8">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 bg-gradient-to-br from-primary to-accent rounded-2xl flex items-center justify-center shadow-lg shadow-primary/20">
                    <Bell className="text-white" size={22} />
                  </div>
                  <div>
                    <h2 className="text-2xl font-black text-white tracking-tight">Push Notifications</h2>
                    <p className="text-textSec text-xs mt-0.5">Send instant notifications to all Sticky users</p>
                  </div>
                </div>
                <div className="hidden md:flex items-center gap-2 text-[10px] text-textSec font-bold uppercase tracking-widest">
                  <span className="w-2 h-2 bg-violet-500 rounded-full animate-pulse" />
                  FCM Active
                </div>
              </div>

              <div className="flex flex-col lg:flex-row gap-6">
                {/* Left: Templates */}
                <div className="lg:w-[340px] shrink-0 space-y-4">
                  <div className="flex items-center gap-2 mb-1">
                    <Sparkles size={16} className="text-accent" />
                    <h3 className="text-sm font-black text-white uppercase tracking-wider">Quick Templates</h3>
                  </div>

                  <div className="space-y-2.5 max-h-[calc(100vh-220px)] overflow-y-auto custom-scrollbar pr-1">
                    {[
                      {
                        cat: '🆕 New Content', color: 'from-purple-500/20 to-purple-500/5', border: 'border-purple-500/20 hover:border-purple-500/40', templates: [
                          { title: 'Sticky', body: '🎨 Fresh stickers just dropped! Express yourself like never before ✨' },
                          { title: 'Sticky', body: '🔥 New sticker pack alert! Be the first to check it out 👀💫' },
                          { title: 'Sticky', body: '✨ Your chats are about to get a whole lot cooler! New stickers inside 🚀' },
                          { title: 'Sticky', body: '🎁 Surprise! We just added amazing new stickers you\'ll love 💖' },
                        ]
                      },
                      {
                        cat: '💎 Premium', color: 'from-amber-500/20 to-amber-500/5', border: 'border-amber-500/20 hover:border-amber-500/40', templates: [
                          { title: 'Sticky Premium', body: '👑 Unlock exclusive premium stickers & make your chats legendary ✨💎' },
                          { title: 'Sticky Premium', body: '🌟 Premium members get early access to our newest collection! Upgrade now 🚀' },
                          { title: 'Sticky', body: '💎 Go Premium today and get 100+ exclusive stickers! Limited time offer 🔥' },
                        ]
                      },
                      {
                        cat: '🎉 Engagement', color: 'from-pink-500/20 to-pink-500/5', border: 'border-pink-500/20 hover:border-pink-500/40', templates: [
                          { title: 'Sticky', body: '😍 Your friends are already using the trending stickers! Don\'t miss out 🔥' },
                          { title: 'Sticky', body: '🎭 Over 1000+ stickers waiting for you! Find your perfect match 💫' },
                          { title: 'Sticky', body: '💬 Make every conversation unforgettable with Sticky stickers! Open now ✨' },
                          { title: 'Sticky', body: '🌈 Bored of plain texts? Spice up your chats with our stickers! 🎨🔥' },
                        ]
                      },
                      {
                        cat: '📢 Updates', color: 'from-blue-500/20 to-blue-500/5', border: 'border-blue-500/20 hover:border-blue-500/40', templates: [
                          { title: 'Sticky', body: '🚀 Big update! Faster loading, smoother experience & new stickers inside ⚡' },
                          { title: 'Sticky', body: '✅ We listened to your feedback! Check out what\'s new in Sticky 🎉' },
                          { title: 'Sticky', body: '⚡ Sticky just got better! Update now for the best sticker experience 💪' },
                        ]
                      },
                      {
                        cat: '🐱 Fun & Seasonal', color: 'from-purple-500/20 to-purple-500/5', border: 'border-purple-500/20 hover:border-purple-500/40', templates: [
                          { title: 'Sticky', body: '🎄 Holiday stickers are here! Spread the festive vibes in your chats 🎅✨' },
                          { title: 'Sticky', body: '😂 Need a laugh? Our funniest sticker pack ever just landed! Check it out 🤣' },
                          { title: 'Sticky', body: '🐶🐱 Animal lovers unite! Adorable pet stickers are waiting for you 💕' },
                          { title: 'Sticky', body: '🌙 Good vibes only! Send some love with our wholesome sticker collection 💖✨' },
                        ]
                      },
                      {
                        cat: '🤖 AI Features', color: 'from-cyan-500/20 to-cyan-500/5', border: 'border-cyan-500/20 hover:border-cyan-500/40', templates: [
                          { title: 'Sticky AI ✨', body: '🤖 NEW: Create your own stickers with AI! Just describe it & Sticky makes it 🎨✨' },
                          { title: 'Sticky AI', body: '🧠 AI sticker generator is here! Turn your imagination into stickers in seconds 🚀' },
                          { title: 'Sticky AI', body: '✨ Custom stickers powered by AI! Type anything → get a unique sticker instantly 🔥' },
                          { title: 'Sticky AI', body: '🎭 Your creativity + Our AI = Unlimited stickers! Try the new AI generator now 💫' },
                        ]
                      },
                      {
                        cat: '📱 Animated Stickers', color: 'from-green-500/20 to-green-500/5', border: 'border-green-500/20 hover:border-green-500/40', templates: [
                          { title: 'Sticky', body: '🎬 Animated stickers are HERE! Moving stickers for next-level chats 🔥✨' },
                          { title: 'Sticky', body: '💃 Your stickers now MOVE! Check out our animated collection 🎉🚀' },
                          { title: 'Sticky', body: '⚡ Static is boring! Try our new animated stickers & blow up your group chats 🎆' },
                        ]
                      },
                      {
                        cat: '⭐ Rate & Review', color: 'from-yellow-500/20 to-yellow-500/5', border: 'border-yellow-500/20 hover:border-yellow-500/40', templates: [
                          { title: 'Sticky', body: '⭐ Enjoying Sticky? A quick 5-star review helps us grow & make more stickers for you! 💖' },
                          { title: 'Sticky', body: '🙏 Love our stickers? Share the love with a review on Play Store! It means the world to us ✨' },
                          { title: 'Sticky', body: '📱 Help us reach more sticker lovers! Rate Sticky on Play Store ⭐⭐⭐⭐⭐' },
                        ]
                      },
                    ].map((group, gi) => (
                      <div key={gi} className="space-y-1.5">
                        <p className="text-[10px] font-black text-textSec uppercase tracking-widest px-1 pt-2">{group.cat}</p>
                        {group.templates.map((t, ti) => (
                          <button
                            key={ti}
                            type="button"
                            onClick={() => { setNotifTitle(t.title); setNotifBody(t.body); }}
                            className={cn(
                              "w-full text-left p-3 rounded-xl border transition-all duration-200 group/tpl",
                              group.border,
                              "bg-gradient-to-r",
                              group.color,
                              notifBody === t.body ? "ring-2 ring-primary scale-[1.02] shadow-lg" : "hover:scale-[1.01]"
                            )}
                          >
                            <p className="text-[11px] text-white/90 font-medium leading-relaxed line-clamp-2 group-hover/tpl:text-white transition-colors">{t.body}</p>
                          </button>
                        ))}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Center: Form */}
                <div className="flex-1 min-w-0">
                  <form onSubmit={handleSendNotification} className="space-y-5">
                    <div className="glass p-6 rounded-2xl bg-gradient-to-br from-white/[0.03] to-transparent border border-white/5 shadow-2xl space-y-5">
                      {/* Title Input */}
                      <div className="space-y-2">
                        <label className="text-[10px] font-black text-textSec uppercase tracking-widest flex items-center gap-2">
                          <div className="w-5 h-5 bg-primary/20 rounded-md flex items-center justify-center"><Bell size={10} className="text-primary" /></div>
                          TITLE
                        </label>
                        <input
                          type="text"
                          value={notifTitle}
                          onChange={(e) => setNotifTitle(e.target.value)}
                          placeholder="Sticky"
                          className="w-full bg-card/60 border border-white/10 rounded-xl px-4 py-3 text-white text-sm font-bold outline-none focus:ring-2 focus:ring-primary focus:border-primary/50 transition-all"
                        />
                      </div>

                      {/* Message Input */}
                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <label className="text-[10px] font-black text-textSec uppercase tracking-widest flex items-center gap-2">
                            <div className="w-5 h-5 bg-accent/20 rounded-md flex items-center justify-center"><MessageSquare size={10} className="text-accent" /></div>
                            MESSAGE
                          </label>
                          <span className="text-[10px] font-mono text-accent/60 font-bold">{notifBody.length} chars</span>
                        </div>
                        <textarea
                          required
                          value={notifBody}
                          onChange={(e) => setNotifBody(e.target.value)}
                          placeholder="Type your notification message..."
                          rows={3}
                          className="w-full bg-card/60 border border-white/10 rounded-xl px-4 py-3 text-white text-sm font-medium outline-none focus:ring-2 focus:ring-accent focus:border-accent/50 transition-all resize-none"
                        />
                        {/* Quick Emojis */}
                        <div className="flex flex-wrap gap-1">
                          {['😊', '😂', '❤️', '🔥', '✨', '🚀', '🎉', '🌟', '💫', '🎁', '💎', '📱', '🌈', '🎭', '🐱', '👑', '⚡', '🔔', '💯', '😍', '🎨', '💪', '👀', '💖', '🤩'].map(emoji => (
                            <button
                              key={emoji}
                              type="button"
                              onClick={() => setNotifBody(prev => prev + emoji)}
                              className="w-7 h-7 flex items-center justify-center bg-white/5 hover:bg-accent/20 rounded-lg text-xs transition-all hover:scale-125 active:scale-95"
                            >
                              {emoji}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Image URL */}
                      <div className="space-y-2">
                        <label className="text-[10px] font-black text-textSec uppercase tracking-widest flex items-center gap-2">
                          <div className="w-5 h-5 bg-warning/20 rounded-md flex items-center justify-center"><ImageIcon size={10} className="text-warning" /></div>
                          IMAGE URL <span className="text-textSec/50 font-normal normal-case">(optional)</span>
                        </label>
                        <input
                          type="url"
                          value={notifImageUrl}
                          onChange={(e) => setNotifImageUrl(e.target.value)}
                          placeholder="https://example.com/image.webp"
                          className="w-full bg-card/60 border border-white/10 rounded-xl px-4 py-3 text-white font-mono text-xs outline-none focus:ring-2 focus:ring-warning focus:border-warning/50 transition-all"
                        />
                      </div>

                      {/* Send Button */}
                      <button
                        type="submit"
                        disabled={isSendingNotif || !notifBody}
                        className={cn(
                          "w-full py-4 rounded-xl font-black text-sm shadow-xl transition-all flex items-center justify-center gap-2.5 disabled:opacity-40 text-white uppercase tracking-wider",
                          isSendingNotif ? "bg-hover" : "bg-gradient-to-r from-primary to-accent shadow-primary/20 hover:shadow-2xl hover:shadow-primary/30 hover:scale-[1.01] active:scale-[0.99]"
                        )}
                      >
                        <Send size={18} className={cn(isSendingNotif && "animate-spin")} />
                        {isSendingNotif ? 'Sending...' : 'Send to All Users'}
                      </button>
                    </div>

                    {/* Info Card */}
                    <div className="flex items-start gap-3 p-4 bg-white/[0.02] border border-white/5 rounded-xl">
                      <Info size={16} className="text-textSec shrink-0 mt-0.5" />
                      <div className="space-y-1">
                        <p className="text-[10px] text-textSec leading-relaxed">Notifications are sent to all devices subscribed to the <span className="text-primary font-bold">"stickers"</span> topic via FCM. Recommended: max 2-3 per day.</p>
                      </div>
                    </div>
                  </form>
                </div>

                {/* Right: Phone Preview */}
                <div className="hidden xl:flex flex-col items-center gap-3 shrink-0">
                  <p className="text-[10px] font-black text-textSec uppercase tracking-widest">Live Preview</p>
                  <div className="w-[280px] bg-gradient-to-b from-gray-900 to-gray-950 rounded-[2.5rem] p-3 shadow-2xl border border-white/10">
                    <div className="bg-black rounded-[2rem] overflow-hidden">
                      {/* Phone Status Bar */}
                      <div className="flex items-center justify-between px-6 pt-3 pb-2">
                        <span className="text-[10px] text-white/60 font-semibold">9:41</span>
                        <div className="w-20 h-5 bg-white/10 rounded-full" />
                        <div className="flex items-center gap-1">
                          <div className="w-4 h-2 bg-white/40 rounded-sm" />
                        </div>
                      </div>

                      {/* Notification Area */}
                      <div className="px-4 pt-6 pb-8 min-h-[380px] flex flex-col">
                        {/* Wallpaper placeholder */}
                        <div className="flex-1 flex items-start justify-center pt-8">
                          <div className="text-center space-y-2">
                            <div className="text-4xl">🔒</div>
                            <p className="text-white/30 text-xs font-medium">Lock Screen</p>
                          </div>
                        </div>

                        {/* Notification Card */}
                        <div className={cn(
                          "bg-white/10 backdrop-blur-xl rounded-2xl p-3.5 border border-white/10 transition-all duration-500",
                          notifBody ? "opacity-100 translate-y-0" : "opacity-30 translate-y-2"
                        )}>
                          <div className="flex items-start gap-2.5">
                            <div className="w-8 h-8 bg-gradient-to-br from-primary to-accent rounded-lg flex items-center justify-center shrink-0 shadow-lg">
                              <span className="text-[10px] font-black text-white">S</span>
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center justify-between">
                                <p className="text-[11px] font-bold text-white">{notifTitle || 'Sticky'}</p>
                                <p className="text-[9px] text-white/40">now</p>
                              </div>
                              <p className="text-[11px] text-white/70 mt-0.5 leading-relaxed line-clamp-3">
                                {notifBody || 'Your notification message will appear here...'}
                              </p>
                              {notifImageUrl && (
                                <div className="mt-2 w-full h-24 bg-white/5 rounded-lg overflow-hidden">
                                  <img src={notifImageUrl} alt="" className="w-full h-full object-cover" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
                                </div>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Home Indicator */}
                      <div className="flex justify-center pb-2">
                        <div className="w-28 h-1 bg-white/20 rounded-full" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        ) : activeTab === 'messages' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-12 custom-scrollbar bg-background">
            <div className="max-w-6xl mx-auto space-y-6 md:space-y-8 animate-in fade-in duration-500">
              {/* Header Card */}
              <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div className="flex items-center gap-4">
                    <div className="w-14 h-14 bg-gradient-to-br from-blue-500/20 to-indigo-500/20 rounded-2xl flex items-center justify-center border border-blue-500/10 shadow-lg shadow-blue-500/5">
                      <Mail size={26} className="text-blue-400" />
                    </div>
                    <div>
                      <h2 className="text-2xl font-black text-white tracking-tight">Messages & Suggestions</h2>
                      <p className="text-xs text-textSec mt-0.5">Communication requests from users</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] text-violet-500 font-black uppercase tracking-widest flex items-center gap-1.5 bg-violet-500/5 px-3 py-1.5 rounded-lg border border-violet-500/10">
                      <span className="w-1.5 h-1.5 bg-violet-500 rounded-full animate-pulse"></span>
                      Live
                    </span>
                    <a
                      href="https://mail.hostinger.com/v2/mailboxes/INBOX"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="px-3 py-1.5 bg-sky-500/10 hover:bg-sky-500/20 text-sky-400 text-[10px] font-black uppercase tracking-widest rounded-xl transition-all border border-sky-500/10 flex items-center gap-1.5"
                    >
                      <Mail size={11} /> Send Email
                    </a>
                    <button
                      onClick={clearAllMessages}
                      className="px-3 py-1.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 text-[10px] font-black uppercase tracking-widest rounded-xl transition-all border border-red-500/10 flex items-center gap-1.5"
                    >
                      <Trash2 size={11} /> Clear Messages
                    </button>
                    <button
                      onClick={clearAllSuggestions}
                      className="px-3 py-1.5 bg-yellow-500/10 hover:bg-yellow-500/20 text-yellow-400 text-[10px] font-black uppercase tracking-widest rounded-xl transition-all border border-yellow-500/10 flex items-center gap-1.5"
                    >
                      <Trash2 size={11} /> Clear Suggestions
                    </button>
                  </div>
                </div>
              </div>

              {/* Stats Row */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <div className="glass rounded-xl p-4 border border-white/5">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-blue-500/10 rounded-xl flex items-center justify-center">
                      <MessageSquare size={18} className="text-blue-400" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-white">{messages.length}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Total Messages</span>
                    </div>
                  </div>
                </div>
                <div className="glass rounded-xl p-4 border border-primary/10">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-primary/10 rounded-xl flex items-center justify-center">
                      <Mail size={18} className="text-primary" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-primary">{messages.filter(m => m.status === 'unread').length}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Unread</span>
                    </div>
                  </div>
                </div>
                <div className="glass rounded-xl p-4 border border-yellow-500/10">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-yellow-500/10 rounded-xl flex items-center justify-center">
                      <Lightbulb size={18} className="text-yellow-400" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-yellow-400">{suggestions.length}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Total Suggestions</span>
                    </div>
                  </div>
                </div>
                <div className="glass rounded-xl p-4 border border-violet-500/10">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-violet-500/10 rounded-xl flex items-center justify-center">
                      <Check size={18} className="text-violet-400" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-violet-400">{messages.filter(m => m.status === 'read').length}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Read</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Sub Tabs */}
              <div className="flex bg-white/[0.02] rounded-2xl p-1.5 gap-1.5 border border-white/5">
                <button
                  onClick={() => setMessagesSubTab('messages')}
                  className={cn(
                    "flex-1 py-3 px-6 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2.5",
                    messagesSubTab === 'messages'
                      ? "bg-gradient-to-r from-blue-500 to-indigo-600 text-white shadow-lg shadow-blue-500/20"
                      : "text-textSec hover:text-white hover:bg-white/5"
                  )}
                >
                  <MessageSquare size={15} />
                  Messages
                  {messages.length > 0 && (
                    <span className={cn(
                      "px-2 py-0.5 rounded-full text-[10px] font-black",
                      messagesSubTab === 'messages' ? "bg-white/20" : "bg-blue-500/10 text-blue-400"
                    )}>{messages.length}</span>
                  )}
                </button>
                <button
                  onClick={() => setMessagesSubTab('suggestions')}
                  className={cn(
                    "flex-1 py-3 px-6 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2.5",
                    messagesSubTab === 'suggestions'
                      ? "bg-gradient-to-r from-yellow-500 to-orange-500 text-white shadow-lg shadow-yellow-500/20"
                      : "text-textSec hover:text-white hover:bg-white/5"
                  )}
                >
                  <Lightbulb size={15} />
                  Suggestions
                  {suggestions.length > 0 && (
                    <span className={cn(
                      "px-2 py-0.5 rounded-full text-[10px] font-black",
                      messagesSubTab === 'suggestions' ? "bg-white/20" : "bg-yellow-500/10 text-yellow-400"
                    )}>{suggestions.length}</span>
                  )}
                </button>
              </div>

              {/* Messages Content */}
              {messagesSubTab === 'messages' ? (
                <div className="space-y-3">
                  {messages.length === 0 ? (
                    <div className="glass rounded-2xl p-16 text-center border border-white/5">
                      <div className="w-20 h-20 bg-gradient-to-br from-blue-500/10 to-indigo-500/10 rounded-2xl flex items-center justify-center mx-auto mb-5 border border-white/5">
                        <MessageSquare size={36} className="text-textSec" />
                      </div>
                      <h3 className="text-xl font-black text-white mb-2">No Messages Yet</h3>
                      <p className="text-sm text-textSec max-w-md mx-auto">Messages from users will appear here when they send them from the app.</p>
                    </div>
                  ) : (
                    messages.map(msg => (
                      <div
                        key={msg.id}
                        className={cn(
                          "glass rounded-2xl border transition-all overflow-hidden",
                          msg.status === 'unread' ? "border-primary/30 bg-primary/[0.03]" : "border-white/5 hover:border-white/10"
                        )}
                      >
                        <div className="p-5">
                          <div className="flex items-start justify-between gap-4">
                            <div className="flex items-start gap-4 flex-1 min-w-0">
                              <div className={cn(
                                "w-11 h-11 rounded-xl flex items-center justify-center shrink-0",
                                msg.status === 'unread' ? "bg-primary/10" : "bg-white/5"
                              )}>
                                <Mail size={18} className={msg.status === 'unread' ? "text-primary" : "text-textSec"} />
                              </div>
                              <div className="flex-1 min-w-0 space-y-2.5">
                                <div className="flex items-center gap-2.5 flex-wrap">
                                  {msg.status === 'unread' && (
                                    <span className="bg-primary text-white text-[8px] font-black px-2 py-0.5 rounded-md uppercase tracking-wider">New</span>
                                  )}
                                  <h4 className="text-base font-black text-white truncate">{msg.subject || 'No subject specified'}</h4>
                                </div>
                                <div className="flex items-center gap-3 text-xs text-textSec flex-wrap">
                                  <span className="font-bold text-white/70 flex items-center gap-1.5">
                                    <UserIcon size={12} /> {msg.name}
                                  </span>
                                  <a href={`mailto:${msg.email}`} className="text-primary hover:underline font-bold flex items-center gap-1.5">
                                    <Mail size={12} /> {msg.email}
                                  </a>
                                  <span className="flex items-center gap-1 text-textSec/60">
                                    <Clock size={12} /> {msg.date} {msg.time}
                                  </span>
                                </div>
                                <div className="bg-white/[0.03] rounded-xl p-4 border border-white/5">
                                  <p className="text-sm text-white/80 leading-relaxed">{msg.message}</p>
                                </div>
                              </div>
                            </div>
                            <div className="flex items-center gap-1.5 shrink-0">
                              {msg.status === 'unread' && (
                                <button
                                  onClick={() => markMessageAsRead(msg.id)}
                                  className="p-2.5 bg-primary/10 hover:bg-primary/20 text-primary rounded-xl transition-all"
                                  title="Mark as read"
                                >
                                  <Check size={16} />
                                </button>
                              )}
                              <button
                                onClick={() => deleteMessage(msg.id)}
                                className="p-2.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 rounded-xl transition-all"
                                title="Delete"
                              >
                                <Trash2 size={16} />
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              ) : (
                <div className="space-y-3">
                  {suggestions.length === 0 ? (
                    <div className="glass rounded-2xl p-16 text-center border border-white/5">
                      <div className="w-20 h-20 bg-gradient-to-br from-yellow-500/10 to-orange-500/10 rounded-2xl flex items-center justify-center mx-auto mb-5 border border-white/5">
                        <Lightbulb size={36} className="text-textSec" />
                      </div>
                      <h3 className="text-xl font-black text-white mb-2">No Suggestions Yet</h3>
                      <p className="text-sm text-textSec max-w-md mx-auto">Sticker suggestions from users will appear here.</p>
                    </div>
                  ) : (
                    suggestions.map(sugg => (
                      <div
                        key={sugg.id}
                        className="glass rounded-2xl border border-white/5 transition-all hover:border-yellow-500/20 overflow-hidden"
                      >
                        <div className="p-5">
                          <div className="flex items-start justify-between gap-4">
                            <div className="flex items-start gap-4 flex-1 min-w-0">
                              <div className="w-11 h-11 bg-yellow-500/10 rounded-xl flex items-center justify-center shrink-0">
                                <Lightbulb size={18} className="text-yellow-400" />
                              </div>
                              <div className="flex-1 min-w-0 space-y-2.5">
                                <div className="flex items-center gap-2.5">
                                  <span className="text-[9px] font-black text-yellow-400 bg-yellow-400/10 px-2 py-0.5 rounded-md uppercase tracking-wider border border-yellow-400/10">Suggestion</span>
                                  <span className="text-xs text-textSec/60 flex items-center gap-1">
                                    <Clock size={12} /> {sugg.date} {sugg.time}
                                  </span>
                                </div>
                                <div className="bg-white/[0.03] rounded-xl p-4 border border-white/5">
                                  <p className="text-sm text-white/80 leading-relaxed">{sugg.suggestion}</p>
                                </div>
                              </div>
                            </div>
                            <button
                              onClick={() => deleteSuggestion(sugg.id)}
                              className="p-2.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 rounded-xl transition-all shrink-0"
                              title="Sil"
                            >
                              <Trash2 size={16} />
                            </button>
                          </div>
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
            <div className="max-w-7xl mx-auto space-y-6 md:space-y-8 animate-in fade-in duration-500">
              {/* Users Header Card */}
              <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                  <div className="flex items-center gap-4">
                    <div className="w-14 h-14 bg-gradient-to-br from-purple-500/20 to-pink-500/20 rounded-2xl flex items-center justify-center border border-purple-500/10 shadow-lg shadow-purple-500/5">
                      <Users size={26} className="text-purple-400" />
                    </div>
                    <div>
                      <h1 className="text-2xl font-black text-white tracking-tight">Users</h1>
                      <p className="text-xs text-textSec mt-0.5">Firebase user management and subscription control</p>
                    </div>
                  </div>
                  <button
                    onClick={fetchUsers}
                    disabled={usersLoading}
                    className="flex items-center gap-2.5 px-5 py-2.5 bg-white/5 hover:bg-white/10 text-textSec hover:text-white border border-white/5 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all"
                  >
                    <RefreshCcw size={14} className={usersLoading ? "animate-spin" : ""} />
                    Refresh
                  </button>
                </div>
              </div>

              {/* Stats Cards */}
              <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
                <div className="glass rounded-xl p-4 border border-white/5">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-white/5 rounded-xl flex items-center justify-center">
                      <Users size={18} className="text-white/60" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-white">{userStats.total.toLocaleString()}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Total</span>
                    </div>
                  </div>
                </div>
                <div className="glass rounded-xl p-4 border border-primary/10">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-primary/10 rounded-xl flex items-center justify-center">
                      <Crown size={18} className="text-primary" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-primary">{userStats.premium.toLocaleString()}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Premium</span>
                    </div>
                  </div>
                </div>
                <div className="glass rounded-xl p-4 border border-accent/10">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-accent/10 rounded-xl flex items-center justify-center">
                      <Clock size={18} className="text-accent" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-accent">{userStats.monthly.toLocaleString()}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Monthly</span>
                    </div>
                  </div>
                </div>
                <div className="glass rounded-xl p-4 border border-yellow-500/10">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-yellow-500/10 rounded-xl flex items-center justify-center">
                      <Calendar size={18} className="text-yellow-400" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-yellow-400">{userStats.yearly.toLocaleString()}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Yearly</span>
                    </div>
                  </div>
                </div>
                <div className="glass rounded-xl p-4 border border-white/5">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-white/5 rounded-xl flex items-center justify-center">
                      <Shield size={18} className="text-textSec" />
                    </div>
                    <div>
                      <p className="text-xl font-black text-textSec">{userStats.free.toLocaleString()}</p>
                      <span className="text-[9px] font-bold text-textSec uppercase tracking-widest">Free</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* User Insights */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div className="glass rounded-xl p-4 border border-white/5">
                  <div className="flex items-center gap-2 mb-2">
                    <TrendingUp size={14} className="text-green-400" />
                    <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Conversion Rate</span>
                  </div>
                  <p className="text-lg font-black text-white">
                    {usersData.length > 0 ? ((userStats.premium / usersData.length) * 100).toFixed(1) : '0'}%
                  </p>
                  <p className="text-[9px] text-textSec mt-0.5">Free → Premium conversion</p>
                </div>
                <div className="glass rounded-xl p-4 border border-white/5">
                  <div className="flex items-center gap-2 mb-2">
                    <Smartphone size={14} className="text-sky-400" />
                    <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Device Distribution</span>
                  </div>
                  {(() => {
                    const devices: Record<string, number> = {};
                    usersData.forEach((u: any) => {
                      const brand = (u.device_model || u.deviceModel || 'Unknown').split(' ')[0];
                      devices[brand] = (devices[brand] || 0) + 1;
                    });
                    const top = Object.entries(devices).sort((a, b) => b[1] - a[1]).slice(0, 3);
                    return (
                      <div className="space-y-1">
                        {top.map(([brand, count]) => (
                          <div key={brand} className="flex items-center justify-between">
                            <span className="text-xs text-white/70 font-medium">{brand}</span>
                            <span className="text-xs font-black text-sky-400">{count}</span>
                          </div>
                        ))}
                      </div>
                    );
                  })()}
                </div>
                <div className="glass rounded-xl p-4 border border-white/5">
                  <div className="flex items-center gap-2 mb-2">
                    <Globe size={14} className="text-purple-400" />
                    <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Language Distribution</span>
                  </div>
                  {(() => {
                    const langs: Record<string, number> = {};
                    usersData.forEach((u: any) => {
                      const lang = u.language || u.device_language || '?';
                      langs[lang] = (langs[lang] || 0) + 1;
                    });
                    const top = Object.entries(langs).sort((a, b) => b[1] - a[1]).slice(0, 3);
                    return (
                      <div className="space-y-1">
                        {top.map(([lang, count]) => (
                          <div key={lang} className="flex items-center justify-between">
                            <span className="text-xs text-white/70 font-medium">{lang.toUpperCase()}</span>
                            <span className="text-xs font-black text-purple-400">{count}</span>
                          </div>
                        ))}
                      </div>
                    );
                  })()}
                </div>
              </div>

              {/* Search & Filter Bar */}
              <div className="glass rounded-2xl p-4 border border-white/5">
                <div className="flex flex-col sm:flex-row gap-3">
                  <div className="flex-1 relative">
                    <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-textSec/40" size={18} />
                    <input
                      type="text"
                      value={userSearch}
                      onChange={(e) => setUserSearch(e.target.value)}
                      placeholder="Search email or name..."
                      className="w-full h-12 bg-white/[0.03] border border-white/5 rounded-xl pl-11 pr-5 text-sm text-white font-bold placeholder:text-textSec/30 outline-none focus:border-primary/50 transition-all"
                    />
                  </div>
                  <select
                    value={userFilter}
                    onChange={(e) => setUserFilter(e.target.value as any)}
                    className="h-12 bg-white/[0.03] border border-white/5 rounded-xl px-4 text-sm text-white font-bold outline-none focus:border-primary/50 transition-all appearance-none min-w-[160px]"
                  >
                    <option value="all">Show All</option>
                    <option value="premium">Premium</option>
                    <option value="free">Free</option>
                    <option value="monthly">Monthly Subscriber</option>
                    <option value="yearly">Yearly Subscriber</option>
                  </select>
                </div>
              </div>

              {/* Loading State */}
              {usersLoading && (
                <div className="flex flex-col items-center justify-center py-24">
                  <RefreshCcw size={28} className="animate-spin text-primary mb-3" />
                  <p className="text-xs text-textSec font-bold">Loading users...</p>
                </div>
              )}

              {/* Users Content */}
              {!usersLoading && (
                <div className="flex flex-col lg:flex-row gap-6">
                  {/* Users Table */}
                  <div className="flex-1 glass rounded-2xl border border-white/5 overflow-hidden">
                    {/* Table Header */}
                    <div className="hidden md:grid grid-cols-12 gap-4 px-5 py-3.5 border-b border-white/5 bg-white/[0.02]">
                      <span className="col-span-4 text-[9px] font-black text-textSec uppercase tracking-widest">User</span>
                      <span className="col-span-2 text-[9px] font-black text-textSec uppercase tracking-widest">Registered</span>
                      <span className="col-span-2 text-[9px] font-black text-textSec uppercase tracking-widest">Status</span>
                      <span className="col-span-2 text-[9px] font-black text-textSec uppercase tracking-widest">Type</span>
                      <span className="col-span-2 text-[9px] font-black text-textSec uppercase tracking-widest">Expiry</span>
                    </div>

                    {/* Table Body */}
                    <div className="divide-y divide-white/[0.03] max-h-[calc(100vh-420px)] overflow-y-auto custom-scrollbar">
                      {filteredUsers.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-20 text-center space-y-4">
                          <div className="w-16 h-16 bg-white/5 rounded-2xl flex items-center justify-center">
                            <Users size={28} className="text-textSec/30" />
                          </div>
                          <p className="text-textSec text-sm font-bold">No users found</p>
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
                              "w-full grid grid-cols-1 md:grid-cols-12 gap-2 md:gap-4 px-5 py-3.5 text-left transition-all hover:bg-white/[0.03]",
                              selectedUser?.id === u.id && "bg-primary/5 border-l-2 border-l-primary"
                            )}
                          >
                            <div className="col-span-4 flex items-center gap-3 min-w-0">
                              <div className={cn(
                                "w-9 h-9 rounded-xl flex items-center justify-center shrink-0 border",
                                u.is_premium ? "bg-primary/10 border-primary/20" : "bg-white/5 border-white/5"
                              )}>
                                {u.photo_url ? (
                                  <img src={u.photo_url} alt="" className="w-9 h-9 rounded-xl object-cover" />
                                ) : (
                                  <UserIcon size={15} className={u.is_premium ? "text-primary" : "text-textSec"} />
                                )}
                              </div>
                              <div className="min-w-0">
                                <span className="text-sm font-bold text-white truncate block">{u.display_name || u.email || u.id}</span>
                                {u.display_name && u.email && (
                                  <span className="text-[10px] text-textSec/60 truncate block">{u.email}</span>
                                )}
                              </div>
                            </div>
                            <div className="col-span-2 flex items-center">
                              <span className="text-[10px] font-bold text-textSec/60">
                                {u.created_at?.toDate
                                  ? u.created_at.toDate().toLocaleDateString('tr-TR')
                                  : u.created_at
                                    ? new Date(u.created_at).toLocaleDateString('tr-TR')
                                    : '-'}
                              </span>
                            </div>
                            <div className="col-span-2 flex items-center">
                              {u.is_premium ? (
                                <span className="flex items-center gap-1.5 text-[10px] font-black text-primary bg-primary/10 px-2 py-1 rounded-md border border-primary/10">
                                  <Crown size={11} /> Premium
                                </span>
                              ) : (
                                <span className="text-[10px] font-bold text-textSec/50">Free</span>
                              )}
                            </div>
                            <div className="col-span-2 flex items-center">
                              <span className={cn(
                                "text-[10px] font-bold px-2 py-1 rounded-md",
                                u.premium_type === 'monthly' ? "bg-accent/10 text-accent border border-accent/10" :
                                  u.premium_type === 'yearly' ? "bg-yellow-500/10 text-yellow-400 border border-yellow-500/10" : "text-textSec/40"
                              )}>
                                {u.premium_type === 'monthly' ? 'Monthly' : u.premium_type === 'yearly' ? 'Yearly' : '-'}
                              </span>
                            </div>
                            <div className="col-span-2 flex items-center">
                              <span className="text-[10px] font-bold text-textSec/50">
                                {u.premium_expiry ? new Date(u.premium_expiry).toLocaleDateString('tr-TR') : '-'}
                              </span>
                            </div>
                          </button>
                        ))
                      )}
                    </div>

                    {/* Table Footer */}
                    <div className="px-5 py-3 border-t border-white/5 bg-white/[0.02] flex items-center justify-between">
                      <span className="text-[9px] font-black text-textSec uppercase tracking-widest">
                        {filteredUsers.length} / {usersData.length} users
                      </span>
                      <span className="text-[9px] font-bold text-textSec/40">
                        Click for details →
                      </span>
                    </div>
                  </div>

                  {/* User Detail Panel */}
                  {selectedUser ? (
                    <div className="lg:w-[420px] glass rounded-2xl border border-white/5 overflow-hidden shrink-0">
                      {/* Detail Header */}
                      <div className="p-5 border-b border-white/5 bg-white/[0.02] flex items-center justify-between">
                        <h3 className="text-sm font-black text-white tracking-tight uppercase">User Details</h3>
                        <button
                          onClick={() => setSelectedUser(null)}
                          className="p-2 hover:bg-white/10 rounded-xl transition-all text-textSec"
                        >
                          <X size={16} />
                        </button>
                      </div>

                      <div className="p-5 space-y-4 max-h-[calc(100vh-420px)] overflow-y-auto custom-scrollbar">
                        {/* User Profile Header */}
                        <div className="flex items-center gap-4 pb-4 border-b border-white/5">
                          {selectedUser.photo_url ? (
                            <img
                              src={selectedUser.photo_url}
                              alt={selectedUser.display_name || 'User'}
                              className="w-14 h-14 rounded-2xl object-cover border-2 border-primary/20 shadow-lg"
                            />
                          ) : (
                            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-primary/10 to-accent/10 flex items-center justify-center border border-primary/10">
                              <UserIcon size={24} className="text-primary" />
                            </div>
                          )}
                          <div className="flex-1 min-w-0">
                            <p className="text-base font-black text-white truncate">
                              {selectedUser.display_name || 'Anonymous User'}
                            </p>
                            <p className="text-xs font-medium text-textSec truncate">{selectedUser.email || selectedUser.id}</p>
                            {selectedUser.is_premium && (
                              <span className="inline-flex items-center gap-1 mt-1 text-[9px] font-black text-primary bg-primary/10 px-2 py-0.5 rounded-md border border-primary/10">
                                <Crown size={10} /> Premium
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Info Grid */}
                        <div className="grid grid-cols-2 gap-2.5">
                          <div className="bg-white/[0.03] rounded-xl p-3 border border-white/5">
                            <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest block mb-1">Registration Date</span>
                            <p className="text-xs font-bold text-white">
                              {selectedUser.created_at?.toDate
                                ? selectedUser.created_at.toDate().toLocaleDateString('tr-TR', { year: 'numeric', month: 'short', day: 'numeric' })
                                : selectedUser.created_at
                                  ? new Date(selectedUser.created_at).toLocaleDateString('tr-TR', { year: 'numeric', month: 'short', day: 'numeric' })
                                  : '-'}
                            </p>
                          </div>
                          <div className="bg-white/[0.03] rounded-xl p-3 border border-white/5">
                            <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest block mb-1">Subscription Type</span>
                            <p className={cn(
                              "text-xs font-black",
                              selectedUser.premium_type === 'monthly' ? "text-accent" :
                                selectedUser.premium_type === 'yearly' ? "text-yellow-400" : "text-textSec/50"
                            )}>
                              {selectedUser.premium_type === 'monthly' ? 'Monthly' :
                                selectedUser.premium_type === 'yearly' ? 'Yearly' : 'None'}
                            </p>
                          </div>
                          <div className="bg-white/[0.03] rounded-xl p-3 border border-white/5">
                            <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest block mb-1">Expiry Date</span>
                            <p className="text-xs font-bold text-white">
                              {selectedUser.premium_expiry ? new Date(selectedUser.premium_expiry).toLocaleDateString('tr-TR', { year: 'numeric', month: 'short', day: 'numeric' }) : '-'}
                            </p>
                          </div>
                          <div className="bg-white/[0.03] rounded-xl p-3 border border-white/5">
                            <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest block mb-1">Last Sync</span>
                            <p className="text-xs font-bold text-textSec">
                              {selectedUser.last_sync?.toDate ? selectedUser.last_sync.toDate().toLocaleDateString('tr-TR') : selectedUser.last_sync ? new Date(selectedUser.last_sync).toLocaleDateString('tr-TR') : '-'}
                            </p>
                          </div>
                        </div>

                        {/* User Stats */}
                        <div className="grid grid-cols-2 gap-2.5">
                          <div className="bg-primary/5 border border-primary/10 rounded-xl p-3 text-center">
                            <p className="text-2xl font-black text-primary">{selectedUser.total_stickers_added || 0}</p>
                            <span className="text-[9px] font-bold text-textSec uppercase">Stickers Added</span>
                          </div>
                          <div className="bg-accent/5 border border-accent/10 rounded-xl p-3 text-center">
                            <p className="text-2xl font-black text-accent">{selectedUser.custom_packs_count || 0}</p>
                            <span className="text-[9px] font-bold text-textSec uppercase">Custom Packs</span>
                          </div>
                        </div>

                        {/* UID */}
                        <div className="bg-white/[0.02] rounded-xl p-3 border border-white/5">
                          <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest block mb-1">UID</span>
                          <p className="text-[10px] font-mono text-textSec/50 break-all">{selectedUser.id}</p>
                        </div>

                        {/* Device Info */}
                        {selectedUser.device_info && (
                          <div className="bg-white/[0.02] rounded-xl p-3 border border-white/5">
                            <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest flex items-center gap-1 mb-2">
                              <Smartphone size={10} /> Cihaz Bilgisi
                            </span>
                            <div className="grid grid-cols-2 gap-2">
                              {selectedUser.device_info.model && (
                                <div>
                                  <span className="text-[8px] text-textSec/40 uppercase">Model</span>
                                  <p className="text-xs font-bold text-white">{selectedUser.device_info.model}</p>
                                </div>
                              )}
                              {selectedUser.device_info.os_version && (
                                <div>
                                  <span className="text-[8px] text-textSec/40 uppercase">OS</span>
                                  <p className="text-xs font-bold text-white">{selectedUser.device_info.os_version}</p>
                                </div>
                              )}
                              {selectedUser.device_info.app_version && (
                                <div>
                                  <span className="text-[8px] text-textSec/40 uppercase">App</span>
                                  <p className="text-xs font-bold text-white">v{selectedUser.device_info.app_version}</p>
                                </div>
                              )}
                              {selectedUser.device_info.language && (
                                <div>
                                  <span className="text-[8px] text-textSec/40 uppercase">Language</span>
                                  <p className="text-xs font-bold text-white">{selectedUser.device_info.language}</p>
                                </div>
                              )}
                            </div>
                          </div>
                        )}

                        {/* Favourite Packs */}
                        {selectedUser.favorite_packs && selectedUser.favorite_packs.length > 0 && (
                          <div className="bg-white/[0.02] rounded-xl p-3 border border-white/5">
                            <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest block mb-2">Favorite Packs</span>
                            <div className="flex flex-wrap gap-1.5">
                              {selectedUser.favorite_packs.map((p, i) => (
                                <span key={i} className="px-2 py-1 bg-white/5 text-textSec text-[10px] font-bold rounded-lg border border-white/5">{p}</span>
                              ))}
                            </div>
                          </div>
                        )}

                        {/* Cancelled Info */}
                        {selectedUser.cancelled_at && (
                          <div className="space-y-1 bg-danger/5 border border-danger/10 rounded-xl p-3">
                            <span className="text-[10px] font-black text-danger uppercase tracking-widest">Cancelled</span>
                            <p className="text-xs font-bold text-textSec">{selectedUser.cancelled_reason || '-'}</p>
                          </div>
                        )}

                        <div className="border-t border-white/5 pt-5 space-y-3">

                          {/* Subscription History */}
                          {selectedUser.subscription_history && selectedUser.subscription_history.length > 0 && (
                            <div className="space-y-2 mb-4">
                              <label className="text-[10px] font-black text-textSec uppercase tracking-widest">Subscription History</label>
                              <div className="bg-black/20 rounded-xl max-h-32 overflow-y-auto">
                                {selectedUser.subscription_history.slice().reverse().map((item: any) => (
                                  <div key={item.id} className="p-2 border-b border-white/5 last:border-0 flex items-center justify-between">
                                    <div className="flex flex-col">
                                      <span className="text-[10px] font-bold text-white capitalize">{item.type === 'start' ? 'Start' : item.type === 'cancel' ? 'Cancel' : item.type}</span>
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
                              {/* Google Play abonelikleri düzenlenemez, diger tum kullanicilar düzenlenebilir */}
                              <button
                                disabled={selectedUser.subscription_source === 'google_play'}
                                onClick={() => {
                                  if (selectedUser.subscription_source === 'google_play') return;
                                  setEditingSubscription(true);
                                  setSubPlan(selectedUser.premium_type === 'monthly' ? 'monthly' : selectedUser.premium_type === 'yearly' ? 'yearly' : 'none');
                                }}
                                className={cn(
                                  "flex-1 h-12 rounded-xl font-black text-xs uppercase tracking-widest flex items-center justify-center gap-2 transition-all",
                                  selectedUser.subscription_source === 'google_play'
                                    ? "bg-white/5 text-textSec cursor-not-allowed opacity-50"
                                    : "bg-primary/10 hover:bg-primary/20 text-primary"
                                )}
                              >
                                {selectedUser.subscription_source === 'google_play' ? (
                                  <span className="text-[10px]">Play Store Subscription</span>
                                ) : (
                                  <>
                                    <Edit3 size={14} />
                                    Abonelik Duzenle
                                  </>
                                )}
                              </button>

                              {selectedUser.is_premium && selectedUser.subscription_source !== 'google_play' && (
                                <button
                                  onClick={() => handleRevokeSubscription(selectedUser.id)}
                                  className="h-12 px-4 rounded-xl font-black text-xs uppercase tracking-widest flex items-center justify-center gap-2 transition-all bg-danger/10 hover:bg-danger/20 text-danger"
                                  title="Cancel Subscription"
                                >
                                  <X size={14} />
                                  Iptal Et
                                </button>
                              )}
                            </div>
                          ) : (
                            <div className="space-y-4 bg-white/[0.02] border border-white/5 rounded-2xl p-4">
                              <div className="space-y-2">
                                <label className="text-[10px] font-black text-textSec uppercase tracking-widest">Subscription Plan</label>
                                <select
                                  value={subPlan}
                                  onChange={(e) => setSubPlan(e.target.value)}
                                  className="w-full h-12 bg-hover border border-white/5 rounded-xl px-4 text-white font-bold outline-none focus:border-primary/50 transition-all appearance-none"
                                >
                                  <option value="none">None (Free)</option>
                                  <option value="monthly">Monthly</option>
                                  <option value="yearly">Yearly</option>
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
                  ) : null}
                </div>
              )}
            </div>
          </div>
        ) : activeTab === 'batch' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-8 custom-scrollbar bg-background">
            <div className="max-w-7xl mx-auto animate-in fade-in duration-500">
              {/* Header */}
              <div className="flex items-center justify-between mb-8">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 bg-gradient-to-br from-yellow-500 to-orange-600 rounded-2xl flex items-center justify-center shadow-lg shadow-orange-500/20">
                    <Zap className="text-white" size={22} />
                  </div>
                  <div>
                    <h2 className="text-2xl font-black text-white tracking-tight">Batch Generator V2</h2>
                    <p className="text-textSec text-xs mt-0.5">Multi-source AI-powered sticker pack factory</p>
                  </div>
                </div>
                <div className="hidden md:flex items-center gap-3">
                  <span className={cn(
                    "text-[10px] font-black uppercase tracking-widest flex items-center gap-1.5 px-3 py-1.5 rounded-lg border",
                    deepseekService.isConfigured()
                      ? "text-violet-500 bg-violet-500/5 border-violet-500/10"
                      : "text-danger bg-danger/5 border-danger/10"
                  )}>
                    <span className={cn("w-1.5 h-1.5 rounded-full", deepseekService.isConfigured() ? "bg-violet-500 animate-pulse" : "bg-danger")} />
                    {deepseekService.isConfigured() ? 'DeepSeek AI' : 'AI Offline'}
                  </span>
                  <span className="text-[10px] font-black uppercase tracking-widest flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-blue-400 bg-blue-400/5 border-blue-400/10">
                    <span className="w-1.5 h-1.5 rounded-full bg-blue-400 animate-pulse" />
                    Giphy + Klipy
                  </span>
                </div>
              </div>

              {/* Sub-Tab Navigation */}
              <div className="flex items-center gap-2 mb-6">
                <button
                  onClick={() => setBatchSubTab('generator')}
                  className={cn(
                    "px-5 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all",
                    batchSubTab === 'generator'
                      ? "bg-gradient-to-r from-yellow-500 to-orange-500 text-white shadow-lg shadow-orange-500/20"
                      : "bg-white/5 text-textSec hover:bg-white/10 hover:text-white"
                  )}
                >
                  <Zap size={14} className="inline mr-1.5 -mt-0.5" />
                  Generator
                </button>
                <button
                  onClick={() => setBatchSubTab('telegram')}
                  className={cn(
                    "px-5 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all",
                    batchSubTab === 'telegram'
                      ? "bg-gradient-to-r from-sky-500 to-blue-500 text-white shadow-lg shadow-sky-500/20"
                      : "bg-white/5 text-textSec hover:bg-white/10 hover:text-white"
                  )}
                >
                  <Send size={14} className="inline mr-1.5 -mt-0.5" />
                  Telegram
                </button>
                <button
                  onClick={() => setBatchSubTab('drafts')}
                  className={cn(
                    "px-5 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center gap-2",
                    batchSubTab === 'drafts'
                      ? "bg-gradient-to-r from-blue-500 to-purple-500 text-white shadow-lg shadow-blue-500/20"
                      : "bg-white/5 text-textSec hover:bg-white/10 hover:text-white"
                  )}
                >
                  <Edit3 size={14} className="inline -mt-0.5" />
                  Drafts
                  {draftPacks.length > 0 && (
                    <span className="ml-1 px-2 py-0.5 bg-white/20 rounded-full text-[10px] font-black">{draftPacks.length}</span>
                  )}
                </button>
              </div>

              {batchSubTab === 'generator' && !isBatchRunning ? (
                <div className="max-w-2xl mx-auto space-y-4">
                  {/* Search Terms */}
                  <div className="glass rounded-2xl p-5 border border-white/5 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-[10px] font-black text-textSec uppercase tracking-widest flex items-center gap-2">
                        <List size={10} className="text-primary" /> Search Terms
                        <span className="ml-1 px-1.5 py-0.5 bg-primary/20 text-primary rounded text-[10px] font-black">{batchTermsInput.split(',').filter(t => t.trim()).length}</span>
                      </span>
                      <button
                        onClick={async () => {
                          if (!deepseekService.isConfigured()) {
                            alert('DeepSeek API key not found!');
                            return;
                          }
                          setBatchAiGenerating(true);
                          try {
                            const existingTerms = batchTermsInput.split(',').map(t => t.trim()).filter(Boolean);
                            const catStats = await getCategoryStats();
                            const suggestions = await deepseekService.generateSearchTerms(batchMaxPacks, existingTerms, [], catStats);
                            const newTerms = suggestions.map(s => s.searchTerm).join(', ');
                            setBatchTermsInput(prev => prev ? prev + ', ' + newTerms : newTerms);
                          } catch (e: any) {
                            alert('AI topic generation error: ' + e.message);
                          } finally {
                            setBatchAiGenerating(false);
                          }
                        }}
                        disabled={batchAiGenerating}
                        className="flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-700 hover:to-pink-700 text-white rounded-lg text-[10px] font-black transition-all disabled:opacity-50"
                      >
                        {batchAiGenerating ? <RefreshCcw size={12} className="animate-spin" /> : <Sparkles size={12} />}
                        {batchAiGenerating ? 'Generating...' : `AI Generate ${batchMaxPacks} Topics`}
                      </button>
                    </div>
                    <textarea
                      value={batchTermsInput}
                      onChange={(e) => setBatchTermsInput(e.target.value)}
                      placeholder="Enter search terms separated by commas...&#10;e.g: cute cats, angry reactions, good morning, birthday party, anime kawaii, love hearts, funny memes..."
                      rows={4}
                      className="w-full bg-card/60 border border-white/10 rounded-xl px-4 py-3 text-white text-sm font-medium outline-none focus:ring-2 focus:ring-primary focus:border-primary/50 transition-all resize-none placeholder:text-white/15"
                    />
                  </div>

                  {/* Settings Row */}
                  <div className="glass rounded-2xl p-5 border border-white/5 space-y-3">
                    <div className="flex items-center gap-3 flex-wrap">
                      {/* Source */}
                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] font-black text-textSec uppercase tracking-widest shrink-0">Source</span>
                        <div className="flex rounded-lg border border-white/10 overflow-hidden">
                          {([
                            { id: 'both' as BatchSource, label: 'Both' },
                            { id: 'giphy' as BatchSource, label: 'Giphy' },
                            { id: 'klipy' as BatchSource, label: 'Klipy' },
                          ]).map(s => (
                            <button
                              key={s.id}
                              onClick={() => setBatchSource(s.id)}
                              className={cn(
                                "px-3 py-1.5 text-[10px] font-bold transition-all",
                                batchSource === s.id ? "bg-purple-600 text-white" : "bg-white/5 text-textSec hover:text-white"
                              )}
                            >
                              {s.label}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Content Type */}
                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] font-black text-textSec uppercase tracking-widest shrink-0">Type</span>
                        <div className="flex rounded-lg border border-white/10 overflow-hidden">
                          <button
                            onClick={() => setBatchContentType('stickers')}
                            className={cn("px-3 py-1.5 text-[10px] font-bold transition-all", batchContentType === 'stickers' ? "bg-purple-600 text-white" : "bg-white/5 text-textSec hover:text-white")}
                          >Stickers</button>
                          <button
                            onClick={() => setBatchContentType('gifs')}
                            className={cn("px-3 py-1.5 text-[10px] font-bold transition-all", batchContentType === 'gifs' ? "bg-purple-600 text-white" : "bg-white/5 text-textSec hover:text-white")}
                          >GIFs</button>
                        </div>
                      </div>

                      {/* Max Packs */}
                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] font-black text-textSec uppercase tracking-widest shrink-0">Packs</span>
                        <input
                          type="number" min={1} max={50}
                          value={batchMaxPacks}
                          onChange={(e) => setBatchMaxPacks(Number(e.target.value))}
                          className="w-14 h-8 bg-card/60 border border-white/10 rounded-lg px-2 text-white text-xs font-bold text-center outline-none focus:border-purple-500/50"
                        />
                      </div>

                      {/* Per Pack */}
                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] font-black text-textSec uppercase tracking-widest shrink-0">Per Pack</span>
                        <input
                          type="number" min={5} max={30} step={5}
                          value={batchStickersPerPack}
                          onChange={(e) => setBatchStickersPerPack(Number(e.target.value))}
                          className="w-14 h-8 bg-card/60 border border-white/10 rounded-lg px-2 text-white text-xs font-bold text-center outline-none focus:border-purple-500/50"
                        />
                      </div>
                    </div>

                    {/* AI Toggles */}
                    <div className="flex items-center gap-4 pt-1 border-t border-white/5">
                      <button
                        onClick={() => setBatchUseAiNaming(!batchUseAiNaming)}
                        className="flex items-center gap-2 py-1"
                      >
                        <div className={cn("w-8 h-4 rounded-full transition-all relative", batchUseAiNaming ? "bg-purple-600" : "bg-white/10")}>
                          <div className={cn("w-3 h-3 rounded-full bg-white absolute top-0.5 transition-all", batchUseAiNaming ? "left-4" : "left-0.5")} />
                        </div>
                        <span className={cn("text-[10px] font-bold", batchUseAiNaming ? "text-purple-300" : "text-textSec")}>AI Naming</span>
                      </button>
                      <button
                        onClick={() => setBatchUseAiTranslation(!batchUseAiTranslation)}
                        className="flex items-center gap-2 py-1"
                      >
                        <div className={cn("w-8 h-4 rounded-full transition-all relative", batchUseAiTranslation ? "bg-purple-600" : "bg-white/10")}>
                          <div className={cn("w-3 h-3 rounded-full bg-white absolute top-0.5 transition-all", batchUseAiTranslation ? "left-4" : "left-0.5")} />
                        </div>
                        <span className={cn("text-[10px] font-bold", batchUseAiTranslation ? "text-purple-300" : "text-textSec")}>AI Translation</span>
                      </button>
                    </div>
                  </div>

                  {/* Launch */}
                  <button
                    onClick={async () => {
                      const terms = batchTermsInput.split(',').map(t => t.trim()).filter(Boolean);
                      if (terms.length === 0) {
                        alert('Please enter at least one search term!');
                        return;
                      }
                      const limitedTerms = terms.slice(0, batchMaxPacks);
                      const actualCount = limitedTerms.length;
                      if (!window.confirm(`${actualCount} packs will be created (Limit: ${batchMaxPacks}). This may take a while. Do you want to continue?`)) return;
                      setIsBatchRunning(true);
                      setBatchProgress(null);
                      try {
                        const completedPacks = await generateBatchPacks({
                          searchTerms: limitedTerms,
                          source: batchSource,
                          stickersPerPack: batchStickersPerPack,
                          useAiNaming: batchUseAiNaming,
                          useAiTranslation: batchUseAiTranslation,
                          existingPackNames: packs.map(p => p.name.toLowerCase()),
                          contentType: batchContentType,
                          onProgress: (progress) => {
                            setBatchProgress(progress);
                          }
                        });
                        await fetchDrafts();
                        alert(`✅ ${completedPacks.length} packs created as drafts! You can review and publish them from the Drafts tab.`);
                      } catch (error: any) {
                        console.error('Batch generation error:', error);
                        alert(`Error: ${error.message}`);
                      } finally {
                        setIsBatchRunning(false);
                      }
                    }}
                    disabled={batchTermsInput.split(',').filter(t => t.trim()).length === 0}
                    className="w-full py-4 bg-gradient-to-r from-yellow-500 via-orange-500 to-red-500 hover:from-yellow-600 hover:via-orange-600 hover:to-red-600 text-white rounded-2xl font-black text-sm shadow-xl shadow-orange-500/20 transition-all hover:translate-y-[-2px] active:translate-y-0 disabled:opacity-30 disabled:cursor-not-allowed flex items-center justify-center gap-3"
                  >
                    <Zap size={20} />
                    START BATCH GENERATION
                    <span className="text-xs font-bold opacity-70">~{Math.min(batchTermsInput.split(',').filter(t => t.trim()).length, batchMaxPacks) * batchStickersPerPack} stickers</span>
                  </button>
                </div>
              ) : batchSubTab === 'generator' && isBatchRunning ? (
                /* Batch Progress UI */
                <div className="space-y-6">
                  <div className="glass rounded-[2rem] p-8 border border-white/5 space-y-6">
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="text-xl font-black text-white">
                          {batchProgress?.status === 'done' ? 'Generation Complete!' : 'Generation in Progress...'}
                        </h3>
                        <p className="text-sm text-textSec mt-1">
                          Pack {batchProgress?.currentPack || 0} / {batchProgress?.totalPacks || 0}
                        </p>
                      </div>
                      {batchProgress?.status === 'done' && (
                        <button
                          onClick={() => {
                            setIsBatchRunning(false);
                            setBatchProgress(null);
                          }}
                          className="px-6 py-3 bg-primary text-white rounded-xl font-black text-xs uppercase tracking-widest"
                        >
                          New Generation
                        </button>
                      )}
                    </div>

                    {/* Progress Bar */}
                    <div className="space-y-2">
                      <div className="w-full bg-white/5 h-3 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-gradient-to-r from-yellow-500 via-orange-500 to-red-500 transition-all duration-500 rounded-full"
                          style={{ width: `${batchProgress?.totalPacks ? (batchProgress.currentPack / batchProgress.totalPacks) * 100 : 0}%` }}
                        />
                      </div>
                      <div className="flex justify-between text-[10px] font-bold text-textSec">
                        <span>{Math.round(batchProgress?.totalPacks ? (batchProgress.currentPack / batchProgress.totalPacks) * 100 : 0)}%</span>
                        <span>{batchProgress?.currentPack || 0} / {batchProgress?.totalPacks || 0} packs</span>
                      </div>
                    </div>

                    {/* Current Step */}
                    <div className="bg-primary/5 border border-primary/20 px-5 py-3 rounded-xl">
                      <p className="text-xs text-primary font-bold">{batchProgress?.currentStep || 'Starting...'}</p>
                    </div>

                    {/* Sticker Progress (if available) */}
                    {batchProgress?.stickerProgress && (
                      <div className="space-y-1">
                        <div className="flex justify-between text-[10px] font-bold text-textSec">
                          <span>Sticker: {batchProgress.stickerProgress.current} / {batchProgress.stickerProgress.total}</span>
                          <span>{batchProgress.packName}</span>
                        </div>
                        <div className="w-full bg-white/5 h-1.5 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-accent transition-all duration-300 rounded-full"
                            style={{ width: `${(batchProgress.stickerProgress.current / batchProgress.stickerProgress.total) * 100}%` }}
                          />
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Completed Packs List */}
                  {batchProgress?.completedPacks && batchProgress.completedPacks.length > 0 && (
                    <div className="glass rounded-[2rem] p-6 border border-white/5 space-y-4">
                      <h4 className="text-sm font-black text-white uppercase tracking-widest flex items-center gap-2">
                        <Check size={16} className="text-violet-500" />
                        Created Packs ({batchProgress.completedPacks.length})
                      </h4>
                      <div className="space-y-2 max-h-[300px] overflow-y-auto custom-scrollbar">
                        {batchProgress.completedPacks.map((pack, idx) => (
                          <div key={pack.id} className="flex items-center gap-3 p-3 bg-white/[0.02] rounded-xl border border-white/5">
                            <span className="w-7 h-7 rounded-lg bg-violet-500/20 text-violet-500 flex items-center justify-center text-[10px] font-black">{idx + 1}</span>
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-bold text-white truncate">{pack.name}</p>
                              <p className="text-[10px] text-textSec">{pack.stickerCount} sticker • {pack.searchTerm}</p>
                            </div>
                            <span className="text-[9px] font-bold text-textSec uppercase bg-white/5 px-2 py-1 rounded">{pack.source}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ) : batchSubTab === 'telegram' ? (
                /* ========== TELEGRAM IMPORT UI ========== */
                <div className="max-w-2xl mx-auto space-y-4">
                  {/* Bot Token — compact single row */}
                  <div className="glass rounded-2xl p-4 border border-white/5">
                    <div className="flex items-center gap-2">
                      <Send size={14} className="text-sky-400 shrink-0" />
                      <input
                        type="password"
                        value={telegramBotToken}
                        onChange={(e) => {
                          setTelegramBotToken(e.target.value);
                          setTelegramTokenValid(false);
                        }}
                        placeholder="Bot token..."
                        className="flex-1 min-w-0 bg-card border border-white/10 rounded-lg px-3 py-2 text-white text-sm outline-none focus:ring-2 focus:ring-sky-500 transition-all placeholder:text-white/20"
                      />
                      <button
                        onClick={async () => {
                          if (!telegramBotToken.trim()) return;
                          const result = await validateBotToken(telegramBotToken.trim());
                          if (result.valid) {
                            setTelegramTokenValid(true);
                            setTelegramBotName(result.botName || '');
                            localStorage.setItem('telegram_bot_token', telegramBotToken.trim());
                          } else {
                            alert('Invalid bot token! Please check and try again.');
                            setTelegramTokenValid(false);
                          }
                        }}
                        className="px-4 py-2 bg-sky-500 hover:bg-sky-600 text-white rounded-lg text-xs font-bold transition-all shrink-0"
                      >
                        Verify
                      </button>
                      {telegramTokenValid && (
                        <span className="shrink-0 flex items-center gap-1 px-2 py-1 bg-green-500/20 text-green-400 rounded-lg text-[10px] font-bold">
                          <Check size={10} /> @{telegramBotName}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Pack URLs Input */}
                  <div className="glass rounded-2xl p-4 border border-white/5 space-y-2">
                    <div className="flex items-center justify-between">
                      <label className="text-[10px] font-black text-textSec uppercase tracking-widest flex items-center gap-2">
                        <List size={10} className="text-sky-400" />
                        Sticker Pack Links
                      </label>
                      {telegramPacksInput.trim() && (
                        <span className="text-[10px] font-black text-sky-400 bg-sky-500/10 px-2 py-0.5 rounded-full">
                          {telegramPacksInput.split('\n').map(s => s.trim()).filter(Boolean).length} packs
                        </span>
                      )}
                    </div>
                    <textarea
                      value={telegramPacksInput}
                      onChange={(e) => setTelegramPacksInput(e.target.value)}
                      placeholder={"Paste Telegram sticker pack links, one per line...\n\nhttps://t.me/addstickers/AnimatedCats\nhttps://t.me/addstickers/CoolDogs\nhttps://t.me/addstickers/FunnyMemes\nhttps://t.me/addstickers/CuteAnimals"}
                      rows={7}
                      className="w-full bg-card/60 border border-white/10 rounded-xl px-4 py-3 text-white text-sm font-medium outline-none focus:ring-2 focus:ring-sky-500 focus:border-sky-500/50 transition-all resize-none placeholder:text-white/15"
                    />
                    <p className="text-[9px] text-textSec">💡 Paste multiple links at once — they will all be imported in parallel</p>
                  </div>

                  {/* Directory links — single row of small icon buttons */}
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-[10px] font-bold text-textSec mr-1">Find packs:</span>
                    {[
                      { emoji: '✨', name: 'Stickers.gg', url: 'https://stickers.gg/packs/telegram' },
                      { emoji: '📂', name: 'TLGRM', url: 'https://tlgrm.eu/stickers' },
                      { emoji: '📊', name: 'Fullyst', url: 'https://fullyst.com/stickers' },
                    ].map(dir => (
                      <a
                        key={dir.name}
                        href={dir.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="px-2.5 py-1.5 bg-white/5 hover:bg-white/10 border border-white/5 rounded-lg text-[10px] font-bold text-white/60 hover:text-white transition-all"
                      >
                        {dir.emoji} {dir.name}
                      </a>
                    ))}
                  </div>

                  {/* Import Settings */}
                  <div className="glass rounded-2xl p-5 border border-white/5 space-y-4">
                    <div className="flex items-center gap-2">
                      <Settings size={14} className="text-sky-400" />
                      <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Import Settings</span>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                      {/* Max Stickers to Fetch */}
                      <div className="space-y-2">
                        <label className="text-[10px] font-bold text-textSec uppercase">Max Stickers to Download</label>
                        <input
                          type="number"
                          min={1}
                          max={500}
                          value={telegramMaxStickers || ''}
                          onChange={(e) => setTelegramMaxStickers(Math.max(0, Math.min(500, parseInt(e.target.value) || 0)))}
                          placeholder="Required"
                          className={cn("w-full bg-card/60 border rounded-xl px-4 py-2.5 text-white text-sm font-bold outline-none focus:ring-2 focus:ring-sky-500 placeholder:text-white/20",
                            telegramMaxStickers === 0 ? "border-red-500/50" : "border-white/10"
                          )}
                        />
                        <p className={cn("text-[8px]", telegramMaxStickers === 0 ? "text-red-400 font-bold" : "text-textSec")}>
                          {telegramMaxStickers === 0 ? '⚠️ Required! Set how many stickers to download' : `Will download first ${telegramMaxStickers} stickers`}
                        </p>
                      </div>

                      {/* Stickers per Pack */}
                      <div className="space-y-2">
                        <label className="text-[10px] font-bold text-textSec uppercase">Stickers per Pack</label>
                        <input
                          type="number"
                          min={1}
                          max={30}
                          value={telegramStickerLimit}
                          onChange={(e) => setTelegramStickerLimit(Math.max(1, Math.min(30, parseInt(e.target.value) || 30)))}
                          className="w-full bg-card/60 border border-white/10 rounded-xl px-4 py-2.5 text-white text-sm font-bold outline-none focus:ring-2 focus:ring-sky-500"
                        />
                        <p className="text-[8px] text-textSec">WhatsApp max: 30 per pack</p>
                      </div>

                      {/* Split Packs Toggle */}
                      <div className="space-y-2">
                        <label className="text-[10px] font-bold text-textSec uppercase">Large Pack Handling</label>
                        <div className="flex bg-hover rounded-xl p-1 gap-1">
                          <button
                            onClick={() => setTelegramSplitPacks(true)}
                            className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", telegramSplitPacks ? "bg-sky-500 text-white" : "text-textSec")}
                          >Split</button>
                          <button
                            onClick={() => setTelegramSplitPacks(false)}
                            className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", !telegramSplitPacks ? "bg-orange-500 text-white" : "text-textSec")}
                          >Single Pack</button>
                        </div>
                      </div>

                      {/* Keep Original Name Toggle */}
                      <div className="space-y-2">
                        <label className="text-[10px] font-bold text-textSec uppercase">Pack Naming</label>
                        <div className="flex bg-hover rounded-xl p-1 gap-1">
                          <button
                            onClick={() => setTelegramKeepOriginalName(true)}
                            className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", telegramKeepOriginalName ? "bg-sky-500 text-white" : "text-textSec")}
                          >Original</button>
                          <button
                            onClick={() => setTelegramKeepOriginalName(false)}
                            className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", !telegramKeepOriginalName ? "bg-purple-500 text-white" : "text-textSec")}
                          >AI Name</button>
                        </div>
                      </div>

                    </div>
                    <div className="p-3 bg-sky-500/5 rounded-xl border border-sky-500/10">
                      <p className="text-[10px] text-sky-300 font-bold">
                        📋 {telegramMaxStickers > 0 ? `Download first ${telegramMaxStickers} stickers from each pack` : '⚠️ Set max stickers count!'} → {telegramSplitPacks
                          ? `Split into packs of ${telegramStickerLimit}`
                          : `Single pack (max 30)`
                        } → {telegramKeepOriginalName ? 'Keep Telegram names' : 'AI-generated names'}
                      </p>
                    </div>
                    {!telegramSplitPacks && telegramStickerLimit > 30 && (
                      <p className="text-[9px] text-red-400 font-bold">⚠️ WhatsApp supports max 30 stickers per pack! Please reduce "Stickers per Pack" to 30 or less.</p>
                    )}
                  </div>

                  {/* Import Button */}
                  <div className="flex gap-2">
                  <button
                    onClick={async () => {
                      if (!telegramTokenValid) {
                        alert('Please verify your bot token first!');
                        return;
                      }
                      if (!telegramMaxStickers || telegramMaxStickers <= 0) {
                        alert('⚠️ Please set "Max Stickers to Download"!\n\nYou must specify how many stickers to download from each pack.');
                        return;
                      }
                      if (!telegramSplitPacks && telegramStickerLimit > 30) {
                        alert('⚠️ WhatsApp supports maximum 30 stickers per pack!\n\nPlease reduce "Stickers per Pack" to 30 or less, or switch to "Split" mode.');
                        return;
                      }
                      const packs = telegramPacksInput.split('\n').map(s => s.trim()).filter(Boolean);
                      if (packs.length === 0) {
                        alert('Please enter at least one sticker pack link!');
                        return;
                      }
                      setIsTelegramImporting(true);
                      const ac = new AbortController();
                      telegramAbortRef.current = ac;
                      try {
                        await importTelegramPacks(telegramBotToken.trim(), packs, {
                          useAiNaming: !telegramKeepOriginalName,
                          useAiTranslation: true,
                          stickerLimit: telegramStickerLimit,
                          maxStickers: telegramMaxStickers,
                          splitPacks: telegramSplitPacks,
                          keepOriginalName: telegramKeepOriginalName,
                          abortSignal: ac.signal,
                          onProgress: (p) => setTelegramProgress(p)
                        });
                      } catch (e: any) {
                        alert('Import error: ' + e.message);
                      } finally {
                        setIsTelegramImporting(false);
                        telegramAbortRef.current = null;
                      }
                    }}
                    disabled={isTelegramImporting || !telegramTokenValid}
                    className="flex-1 py-4 bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-600 hover:to-blue-700 text-white rounded-2xl text-sm font-black uppercase tracking-wider transition-all disabled:opacity-50 shadow-lg shadow-sky-500/20"
                  >
                    {isTelegramImporting ? (
                      <span className="flex items-center justify-center gap-2">
                        <RefreshCcw size={16} className="animate-spin" /> Importing...
                      </span>
                    ) : (
                      <span className="flex items-center justify-center gap-2">
                        <Send size={16} /> Import from Telegram
                      </span>
                    )}
                  </button>
                  {isTelegramImporting && (
                    <button
                      onClick={() => {
                        telegramAbortRef.current?.abort();
                        setIsTelegramImporting(false);
                      }}
                      className="py-4 px-6 bg-gradient-to-r from-red-500 to-red-600 hover:from-red-600 hover:to-red-700 text-white rounded-2xl text-sm font-black uppercase tracking-wider transition-all shadow-lg shadow-red-500/20"
                    >
                      <span className="flex items-center justify-center gap-2">
                        <X size={16} /> Stop
                      </span>
                    </button>
                  )}
                  </div>

                  {/* Progress */}
                  {telegramProgress && (
                    <div className="glass rounded-2xl p-6 border border-sky-500/20 space-y-4 bg-gradient-to-br from-sky-500/5 to-blue-500/5">
                      {/* Header */}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className={cn("w-10 h-10 rounded-xl flex items-center justify-center",
                            telegramProgress.status === 'done' ? "bg-green-500/20" : telegramProgress.status === 'error' ? "bg-red-500/20" : "bg-sky-500/20"
                          )}>
                            {telegramProgress.status === 'done' ? (
                              <Check size={18} className="text-green-400" />
                            ) : telegramProgress.status === 'error' ? (
                              <AlertTriangle size={18} className="text-red-400" />
                            ) : (
                              <RefreshCcw size={18} className="text-sky-400 animate-spin" />
                            )}
                          </div>
                          <div>
                            <h4 className="text-sm font-black text-white">
                              {telegramProgress.status === 'done' ? 'Import Complete' : `Importing Pack ${telegramProgress.currentPack}/${telegramProgress.totalPacks}`}
                            </h4>
                            {telegramProgress.packName && (
                              <p className="text-xs text-sky-400 font-bold mt-0.5">{telegramProgress.packName}</p>
                            )}
                          </div>
                        </div>
                        {telegramProgress.stickerProgress && (
                          <span className="text-lg font-black text-white">
                            {Math.round((telegramProgress.stickerProgress.current / telegramProgress.stickerProgress.total) * 100)}%
                          </span>
                        )}
                      </div>

                      {/* Progress Bar */}
                      {telegramProgress.stickerProgress ? (
                        <div className="space-y-2">
                          <div className="w-full bg-white/5 rounded-full h-3 overflow-hidden">
                            <div
                              className="h-full bg-gradient-to-r from-sky-500 to-blue-500 rounded-full transition-all duration-500 ease-out"
                              style={{ width: `${Math.max(2, (telegramProgress.stickerProgress.current / telegramProgress.stickerProgress.total) * 100)}%` }}
                            />
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-[10px] text-textSec font-bold">
                              {telegramProgress.stickerProgress.current} / {telegramProgress.stickerProgress.total} stickers
                            </span>
                            <span className="text-[10px] text-textSec">{telegramProgress.currentStep}</span>
                          </div>
                        </div>
                      ) : (
                        <div className="space-y-2">
                          <div className="w-full bg-white/5 rounded-full h-3 overflow-hidden">
                            <div
                              className="h-full bg-gradient-to-r from-sky-500 to-blue-500 rounded-full transition-all duration-500"
                              style={{ width: `${Math.max(2, (telegramProgress.currentPack / telegramProgress.totalPacks) * 100)}%` }}
                            />
                          </div>
                          <p className="text-[10px] text-textSec font-medium">{telegramProgress.currentStep}</p>
                        </div>
                      )}

                      {/* Current step detail */}
                      {telegramProgress.status === 'running' && (
                        <div className="flex items-center gap-2 px-3 py-2 bg-white/5 rounded-lg">
                          <div className="w-1.5 h-1.5 bg-sky-400 rounded-full animate-pulse" />
                          <span className="text-[10px] text-white/70 font-medium truncate">{telegramProgress.currentStep}</span>
                        </div>
                      )}

                      {/* Completed Packs List */}
                      {telegramProgress.completedPacks?.length > 0 && (
                        <div className="space-y-2">
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">
                            ✅ Completed ({telegramProgress.completedPacks.length})
                          </span>
                          <div className="space-y-1.5 max-h-60 overflow-y-auto">
                            {telegramProgress.completedPacks.map((p: any) => (
                              <div key={p.id} className="flex items-center gap-3 p-3 bg-green-500/10 rounded-xl border border-green-500/10">
                                <Check size={14} className="text-green-400 shrink-0" />
                                <div className="flex-1 min-w-0">
                                  <span className="text-xs text-white font-bold block truncate">{p.name}</span>
                                  <span className="text-[10px] text-green-400/70">@{p.telegramName}</span>
                                </div>
                                <span className="text-xs text-green-400 font-black shrink-0">{p.stickerCount} stickers</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Error display */}
                      {telegramProgress.error && (
                        <div className="flex items-start gap-2 p-3 bg-red-500/10 rounded-xl border border-red-500/20">
                          <AlertTriangle size={14} className="text-red-400 shrink-0 mt-0.5" />
                          <span className="text-xs text-red-300 font-medium">{telegramProgress.error}</span>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ) : batchSubTab === 'drafts' ? (
                /* ========== DRAFTS UI ========== */
                <div className="space-y-6">
                  {/* Drafts Header */}
                  <div className="glass rounded-2xl p-5 border border-white/5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-4">
                        <div className="w-12 h-12 bg-gradient-to-br from-blue-500/20 to-purple-500/20 rounded-xl flex items-center justify-center border border-blue-500/10">
                          <Edit3 size={20} className="text-blue-400" />
                        </div>
                        <div>
                          <h3 className="text-xl font-black text-white">Draft Packs</h3>
                          <p className="text-xs text-textSec mt-0.5">{draftPacks.length} drafts awaiting review</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={fetchDrafts}
                          disabled={draftLoading}
                          className="px-4 py-2.5 bg-white/5 hover:bg-white/10 text-textSec hover:text-white rounded-xl text-[10px] font-black uppercase tracking-widest transition-all flex items-center gap-2"
                        >
                          <RefreshCcw size={13} className={cn(draftLoading && "animate-spin")} /> Refresh
                        </button>
                        {draftPacks.length > 0 && (
                          <>
                          <button
                            onClick={deleteAllDrafts}
                            disabled={!!deleteAllProgress}
                            className="px-5 py-2.5 bg-gradient-to-r from-red-500 to-red-600 text-white rounded-xl text-[10px] font-black uppercase tracking-widest shadow-lg shadow-red-500/20 hover:translate-y-[-1px] transition-all flex items-center gap-2 disabled:opacity-50"
                          >
                            <Trash2 size={13} /> Delete All
                          </button>
                          <button
                            onClick={publishAllDrafts}
                            className="px-5 py-2.5 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl text-[10px] font-black uppercase tracking-widest shadow-lg shadow-violet-500/20 hover:translate-y-[-1px] transition-all flex items-center gap-2"
                          >
                            <Check size={13} /> Publish All
                          </button>
                          </>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Delete All Progress */}
                  {deleteAllProgress && (
                    <div className="glass rounded-2xl p-5 border border-red-500/20 space-y-3 bg-gradient-to-br from-red-500/5 to-red-600/5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <Trash2 size={16} className="text-red-400 animate-pulse" />
                          <span className="text-sm font-black text-white">
                            Deleting {deleteAllProgress.current + 1}/{deleteAllProgress.total}
                          </span>
                        </div>
                        <span className="text-lg font-black text-red-400">
                          {Math.round(((deleteAllProgress.current + 1) / deleteAllProgress.total) * 100)}%
                        </span>
                      </div>
                      <div className="w-full bg-white/5 rounded-full h-3 overflow-hidden">
                        <div
                          className="h-full bg-gradient-to-r from-red-500 to-red-600 rounded-full transition-all duration-300"
                          style={{ width: `${Math.max(2, ((deleteAllProgress.current + 1) / deleteAllProgress.total) * 100)}%` }}
                        />
                      </div>
                      {draftDeleting && (
                        <p className="text-[10px] text-red-300/70 truncate">
                          🗑️ {draftPacks.find(d => d.id === draftDeleting)?.name || draftDeleting}
                        </p>
                      )}
                    </div>
                  )}

                  {draftLoading ? (
                    <div className="flex items-center justify-center py-24">
                      <div className="text-center">
                        <RefreshCcw size={28} className="animate-spin text-primary mx-auto mb-3" />
                        <p className="text-xs text-textSec font-bold">Loading drafts...</p>
                      </div>
                    </div>
                  ) : draftPacks.length === 0 ? (
                    <div className="glass rounded-2xl p-16 border border-white/5 text-center">
                      <div className="w-20 h-20 bg-gradient-to-br from-blue-500/10 to-purple-500/10 rounded-2xl flex items-center justify-center mx-auto mb-5 border border-white/5">
                        <Package size={36} className="text-textSec" />
                      </div>
                      <h4 className="text-xl font-black text-white mb-3">No Drafts Yet</h4>
                      <p className="text-sm text-textSec max-w-lg mx-auto leading-relaxed">When you create packs with the batch generator, they will first appear here as drafts. You can review and publish them after approval.</p>
                    </div>
                  ) : (
                    <div className="space-y-6">
                      {/* Draft Cards */}
                      {draftPacks.map((draft) => (
                        <div
                          key={draft.id}
                          className="glass rounded-2xl border border-white/5 overflow-hidden transition-all hover:border-white/10"
                        >
                          {/* Pack Header Bar */}
                          <div className="p-5 border-b border-white/5">
                            <div className="flex items-center justify-between">
                              <div className="flex items-center gap-4">
                                {draft.tray_url ? (
                                  <img src={draft.tray_url} alt="" className="w-16 h-16 rounded-xl object-cover bg-white/5 border-2 border-white/10 shadow-lg" />
                                ) : (
                                  <div className="w-16 h-16 rounded-xl bg-gradient-to-br from-white/5 to-white/[0.02] flex items-center justify-center border-2 border-white/10">
                                    <Package size={24} className="text-textSec" />
                                  </div>
                                )}
                                <div>
                                  <div className="flex items-center gap-2.5">
                                    <h3 className="text-lg font-black text-white">{draft.name}</h3>
                                    <span className="text-[9px] font-black text-yellow-400 bg-yellow-400/10 px-2 py-0.5 rounded-md uppercase border border-yellow-400/20">Draft</span>
                                  </div>
                                  <div className="flex items-center gap-2 mt-1.5">
                                    <span className="text-[10px] font-bold text-white/60 bg-white/5 px-2.5 py-0.5 rounded-md">{draft.category}</span>
                                    <span className="text-[10px] font-bold text-white/60">{draft.sticker_count || draft.stickers?.length || 0} sticker</span>
                                    {(draft as any).batch_source && (
                                      <span className="text-[10px] font-bold text-blue-400 bg-blue-400/10 px-2.5 py-0.5 rounded-md border border-blue-400/10">{(draft as any).batch_source}</span>
                                    )}
                                    {(draft as any).batch_search_term && (
                                      <span className="text-[10px] font-bold text-purple-400 bg-purple-400/10 px-2.5 py-0.5 rounded-md border border-purple-400/10">"{(draft as any).batch_search_term}"</span>
                                    )}
                                  </div>
                                  {/* Translations inline */}
                                  {draft.name_tr && (
                                    <div className="flex flex-wrap gap-1.5 mt-2">
                                      {[
                                        { code: 'tr', flag: '🇹🇷' }, { code: 'es', flag: '🇪🇸' }, { code: 'ar', flag: '🇸🇦' },
                                        { code: 'zh', flag: '🇨🇳' }, { code: 'hi', flag: '🇮🇳' }, { code: 'pt', flag: '🇧🇷' },
                                        { code: 'de', flag: '🇩🇪' }, { code: 'ja', flag: '🇯🇵' }, { code: 'fr', flag: '🇫🇷' },
                                      ].map(lang => {
                                        const val = (draft as any)[`name_${lang.code}`];
                                        return val ? (
                                          <span key={lang.code} className="text-[9px] text-textSec bg-white/[0.03] px-2 py-0.5 rounded-md border border-white/5">
                                            {lang.flag} {val}
                                          </span>
                                        ) : null;
                                      })}
                                    </div>
                                  )}
                                </div>
                              </div>
                              <div className="flex items-center gap-2 shrink-0">
                                <button
                                  onClick={() => {
                                    setSelectedDraft(draft);
                                    setDraftEditData({
                                      name: draft.name,
                                      name_tr: draft.name_tr,
                                      category: draft.category,
                                      is_premium: draft.is_premium,
                                      is_animated: draft.is_animated,
                                      is_active: draft.is_active,
                                    });
                                    setShowDraftEditModal(true);
                                  }}
                                  className="px-4 py-2.5 bg-white/5 hover:bg-white/10 text-textSec hover:text-white rounded-xl text-[10px] font-black uppercase tracking-widest transition-all flex items-center gap-2"
                                >
                                  <Edit3 size={13} /> Edit
                                </button>
                                <button
                                  onClick={() => deleteDraftPack(draft)}
                                  disabled={draftDeleting === draft.id}
                                  className="px-4 py-2.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all flex items-center gap-2"
                                >
                                  <Trash2 size={13} /> {draftDeleting === draft.id ? 'Deleting...' : 'Delete'}
                                </button>
                                <button
                                  onClick={() => publishDraft(draft)}
                                  disabled={draftPublishing === draft.id}
                                  className="px-5 py-2.5 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl text-[10px] font-black uppercase tracking-widest shadow-lg shadow-violet-500/20 hover:translate-y-[-1px] transition-all flex items-center gap-2 disabled:opacity-50"
                                >
                                  <Check size={13} /> {draftPublishing === draft.id ? 'Publishing...' : 'Publish'}
                                </button>
                              </div>
                            </div>
                          </div>

                          {/* Sticker Grid - Large Previews with Drag & Drop */}
                          <div className="p-5">
                            <div className="flex items-center justify-between mb-3">
                              <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Pack Contents — {draft.stickers?.length || 0} Files</span>
                              <span className="text-[9px] text-textSec bg-white/5 px-2 py-1 rounded-md">Drag & drop to reorder</span>
                            </div>
                            <div className="grid grid-cols-4 sm:grid-cols-5 md:grid-cols-6 lg:grid-cols-8 gap-3">
                              {draft.stickers?.map((sticker, idx) => (
                                <div
                                  key={`${draft.id}-${idx}`}
                                  draggable
                                  onDragStart={(e) => {
                                    setDraftDragIdx(idx);
                                    setDraftDragPackId(draft.id);
                                    e.dataTransfer.effectAllowed = 'move';
                                    e.currentTarget.style.opacity = '0.4';
                                  }}
                                  onDragEnd={(e) => {
                                    e.currentTarget.style.opacity = '1';
                                    if (draftDragIdx !== null && draftDragOverIdx !== null && draftDragPackId === draft.id) {
                                      reorderDraftStickers(draft, draftDragIdx, draftDragOverIdx);
                                    }
                                    setDraftDragIdx(null);
                                    setDraftDragOverIdx(null);
                                    setDraftDragPackId(null);
                                  }}
                                  onDragOver={(e) => {
                                    e.preventDefault();
                                    e.dataTransfer.dropEffect = 'move';
                                    if (draftDragPackId === draft.id) setDraftDragOverIdx(idx);
                                  }}
                                  onDragLeave={() => {
                                    if (draftDragOverIdx === idx) setDraftDragOverIdx(null);
                                  }}
                                  className={cn(
                                    "group relative aspect-square bg-white/[0.03] rounded-2xl border overflow-hidden transition-all cursor-grab active:cursor-grabbing",
                                    draftDragOverIdx === idx && draftDragPackId === draft.id
                                      ? "border-primary/60 bg-primary/10 shadow-lg shadow-primary/10 scale-105"
                                      : "border-white/5 hover:border-primary/30 hover:shadow-lg hover:shadow-primary/5"
                                  )}
                                >
                                  <img
                                    src={sticker.url}
                                    alt={sticker.image_file}
                                    className="w-full h-full object-contain p-2"
                                    onClick={() => setDraftPreviewSticker({ url: sticker.url, title: sticker.image_file })}
                                    loading="lazy"
                                    draggable={false}
                                  />
                                  <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-all pointer-events-none" />
                                  <button
                                    onClick={(e) => { e.stopPropagation(); removeStickerFromDraft(draft, idx); }}
                                    className="absolute top-1.5 right-1.5 w-6 h-6 bg-red-500/90 text-white rounded-lg flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all shadow-lg"
                                  >
                                    <X size={12} />
                                  </button>
                                  <div className="absolute bottom-1 left-1 right-1">
                                    <span className="text-[8px] font-bold text-white/50 bg-black/40 backdrop-blur-sm px-1.5 py-0.5 rounded block text-center">{idx + 1}</span>
                                  </div>
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Sticker Preview Modal */}
                  {draftPreviewSticker && (
                    <div className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4" onClick={() => setDraftPreviewSticker(null)}>
                      <div className="relative max-w-lg max-h-[80vh]" onClick={e => e.stopPropagation()}>
                        <img src={draftPreviewSticker.url} alt="" className="max-w-full max-h-[70vh] object-contain rounded-2xl" />
                        <button onClick={() => setDraftPreviewSticker(null)} className="absolute -top-3 -right-3 w-8 h-8 bg-white/10 backdrop-blur rounded-full flex items-center justify-center text-white hover:bg-white/20">
                          <X size={16} />
                        </button>
                        {draftPreviewSticker.title && <p className="text-center text-xs text-textSec mt-3">{draftPreviewSticker.title}</p>}
                      </div>
                    </div>
                  )}

                  {/* Draft Edit Modal */}
                  {showDraftEditModal && selectedDraft && (
                    <div className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4" onClick={() => setShowDraftEditModal(false)}>
                      <div className="bg-card border border-white/10 rounded-2xl p-6 w-full max-w-lg space-y-5 shadow-2xl" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between">
                          <h3 className="text-lg font-black text-white">Edit Draft</h3>
                          <button onClick={() => setShowDraftEditModal(false)} className="w-8 h-8 bg-white/5 rounded-lg flex items-center justify-center text-textSec hover:text-white hover:bg-white/10">
                            <X size={16} />
                          </button>
                        </div>

                        <div className="space-y-4">
                          <div>
                            <label className="text-[10px] font-black text-textSec uppercase tracking-widest mb-1.5 block">Pack Name (EN)</label>
                            <input
                              type="text"
                              value={draftEditData.name || ''}
                              onChange={e => setDraftEditData(prev => ({ ...prev, name: e.target.value }))}
                              className="w-full bg-background border border-white/10 rounded-xl px-4 py-3 text-white text-sm font-bold outline-none focus:ring-2 focus:ring-primary"
                            />
                          </div>
                          <div>
                            <label className="text-[10px] font-black text-textSec uppercase tracking-widest mb-1.5 block">Pack Name (TR)</label>
                            <input
                              type="text"
                              value={draftEditData.name_tr || ''}
                              onChange={e => setDraftEditData(prev => ({ ...prev, name_tr: e.target.value }))}
                              className="w-full bg-background border border-white/10 rounded-xl px-4 py-3 text-white text-sm font-bold outline-none focus:ring-2 focus:ring-primary"
                            />
                          </div>
                          <div>
                            <label className="text-[10px] font-black text-textSec uppercase tracking-widest mb-1.5 block">Category</label>
                            <select
                              value={draftEditData.category || 'humor'}
                              onChange={e => setDraftEditData(prev => ({ ...prev, category: e.target.value }))}
                              className="w-full bg-background border border-white/10 rounded-xl px-4 py-3 text-white text-sm font-bold outline-none focus:ring-2 focus:ring-primary"
                            >
                              {['humor', 'love', 'greetings', 'animals', 'food', 'sports', 'movies', 'music', 'gaming', 'memes', 'reactions', 'cute', 'holidays', 'other'].map(cat => (
                                <option key={cat} value={cat}>{cat.charAt(0).toUpperCase() + cat.slice(1)}</option>
                              ))}
                            </select>
                          </div>
                          <div className="flex items-center gap-6">
                            <label className="flex items-center gap-2 cursor-pointer">
                              <input
                                type="checkbox"
                                checked={draftEditData.is_premium || false}
                                onChange={e => setDraftEditData(prev => ({ ...prev, is_premium: e.target.checked }))}
                                className="w-4 h-4 rounded accent-yellow-500"
                              />
                              <span className="text-xs font-bold text-white">Premium</span>
                            </label>
                            <label className="flex items-center gap-2 cursor-pointer">
                              <input
                                type="checkbox"
                                checked={draftEditData.is_animated ?? true}
                                onChange={e => setDraftEditData(prev => ({ ...prev, is_animated: e.target.checked }))}
                                className="w-4 h-4 rounded accent-blue-500"
                              />
                              <span className="text-xs font-bold text-white">Animated</span>
                            </label>
                            <label className="flex items-center gap-2 cursor-pointer">
                              <input
                                type="checkbox"
                                checked={draftEditData.is_active ?? true}
                                onChange={e => setDraftEditData(prev => ({ ...prev, is_active: e.target.checked }))}
                                className="w-4 h-4 rounded accent-violet-500"
                              />
                              <span className="text-xs font-bold text-white">Active</span>
                            </label>
                          </div>
                        </div>

                        <div className="flex items-center gap-3 pt-2">
                          <button
                            onClick={() => setShowDraftEditModal(false)}
                            className="flex-1 py-3 bg-white/5 hover:bg-white/10 text-textSec rounded-xl font-black text-xs uppercase tracking-widest transition-all"
                          >
                            Cancel
                          </button>
                          <button
                            onClick={updateDraftPack}
                            className="flex-1 py-3 bg-primary text-white rounded-xl font-black text-xs uppercase tracking-widest shadow-lg shadow-primary/20 hover:translate-y-[-1px] transition-all flex items-center justify-center gap-2"
                          >
                            <Save size={14} /> Save
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ) : null}
            </div>
          </div>
        ) : null
        }
      </main >

      <footer className="hidden md:flex glass h-8 px-6 items-center justify-between text-[10px] font-bold text-textSec uppercase tracking-widest border-t border-white/5 fixed bottom-0 left-0 right-0 z-30">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <div className="w-1.5 h-1.5 bg-primary rounded-full shadow-sm shadow-primary/50" />
            Firebase Connected: {selectedPack ? selectedPack.id : 'Ready'}
          </div>
          <div className="flex items-center gap-2">
            <div className="w-1.5 h-1.5 bg-accent rounded-full" />
            Storage: Data Stream Active
          </div>
        </div>
        <div>
          v2.0 PRO • {new Date().toLocaleTimeString()}
        </div>
      </footer>

      {/* New Pack Modal */}
      <Modal show={showNewPackModal} onClose={() => setShowNewPackModal(false)} title="Create New Pack">
        <div className="space-y-6">
          <p className="text-sm text-textSec">Manually add a new pack directly to the StickyApp database.</p>

          {/* Çoklu Dil Desteği */}
          <div className="bg-gradient-to-r from-primary/10 to-transparent border border-primary/20 rounded-2xl p-4 space-y-4">
            <div className="flex items-center gap-2 mb-2">
              <Globe className="text-primary" size={20} />
              <span className="text-sm font-bold text-white">Multi-Language Support</span>
              <span className="text-xs text-textSec ml-auto">Displayed based on selected language in the app</span>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2">
                {/* Header */}
                <div className="flex items-center justify-between mb-3">
                  <label className="flex items-center gap-2 text-primary font-bold text-sm">
                    <Globe size={18} />
                    Multi-Language Support ({TARGET_LANGUAGES.length} languages)
                  </label>
                  <button
                    onClick={() => handleAutoTranslate(false)}
                    disabled={isTranslating || !newPackData.name}
                    type="button"
                    className="px-4 py-2 bg-gradient-to-r from-primary to-accent text-white rounded-xl flex items-center gap-2 hover:opacity-90 transition-all font-bold text-sm disabled:opacity-40 disabled:cursor-not-allowed shadow-lg"
                  >
                    {isTranslating ? <RefreshCcw size={16} className="animate-spin" /> : <Sparkles size={16} />}
                    {isTranslating ? 'AI Translating...' : '✨ Auto Translate'}
                  </button>
                </div>

                {/* Ana İsim (İngilizce) */}
                <div className="mb-3">
                  <label className="flex items-center gap-2 text-xs font-bold text-white/80 mb-1.5">
                    🇬🇧 English (Main Name) <span className="text-red-400">*</span>
                  </label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      placeholder="e.g. Funny Cats, Love Stickers..."
                      className="flex-1 bg-bgSecondary border-2 border-primary/50 rounded-xl p-3 text-white placeholder:text-white/30 text-base focus:border-primary outline-none transition-all"
                      value={newPackData.name}
                      onChange={(e) => setNewPackData({ ...newPackData, name: e.target.value, name_en: e.target.value })}
                    />
                    <button
                      type="button"
                      onClick={async () => {
                        const newName = await generateCreativeName(newPackData.name);
                        setNewPackData({ ...newPackData, name: newName, name_en: newName });
                      }}
                      className="px-4 bg-accent/10 border-2 border-accent/20 hover:bg-accent/20 hover:border-accent/50 text-accent rounded-xl transition-all flex items-center justify-center active:scale-95 group"
                      title="Suggest Creative Name"
                    >
                      <Wand2 size={24} className="group-hover:rotate-12 transition-transform" />
                    </button>
                  </div>
                  <p className="text-xs text-textSec mt-1">Enter the English name, then click the "Auto Translate" button</p>
                </div>

                {/* Arama */}
                <div className="relative mb-3">
                  <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-textSec" />
                  <input
                    type="text"
                    placeholder="Search language... (Turkish, German, Japanese...)"
                    className="w-full bg-bgSecondary border border-white/10 rounded-xl py-2.5 pl-10 pr-4 text-sm text-white placeholder:text-white/30"
                    value={langSearch}
                    onChange={(e) => setLangSearch(e.target.value)}
                  />
                </div>

                {/* Doluluk Durumu */}
                <div className="flex items-center gap-4 mb-3 text-xs">
                  <span className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-violet-500"></span>
                    Dolu: {TARGET_LANGUAGES.filter(l => (newPackData as any)[`name_${l.code}`]).length}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-white/20"></span>
                    Empty: {TARGET_LANGUAGES.filter(l => !(newPackData as any)[`name_${l.code}`]).length}
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
                          <label className={`flex items-center gap-1.5 text-xs font-medium ${isFilled ? 'text-violet-400' : 'text-textSec'}`}>
                            <span>{lang.flag}</span> {lang.name}
                            {isFilled && <Check size={12} className="text-violet-400" />}
                          </label>
                          <input
                            type="text"
                            placeholder={`${lang.name}...`}
                            className={`w-full bg-bgSecondary border rounded-lg p-2 text-sm text-white placeholder:text-white/20 ${isFilled ? 'border-violet-500/30' : 'border-white/10'}`}
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
            label="Publisher"
            placeholder="Sticky"
            value={newPackData.publisher}
            onChange={(e: any) => setNewPackData({ ...newPackData, publisher: e.target.value })}
          />
          <Input
            label="Publisher Email"
            value={newPackData.publisher_email}
            onChange={(e: any) => setNewPackData({ ...newPackData, publisher_email: e.target.value })}
          />
          <div>
            <label className="text-xs font-bold text-textSec uppercase mb-2 block">Category</label>
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
          {/* Paket Tipi Secimi Kaldirildi */}



          <div className="flex bg-hover rounded-xl p-1 gap-1">
            <button
              onClick={() => setNewPackData({ ...newPackData, is_animated: false })}
              className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", !newPackData.is_animated ? "bg-blue-500 text-white" : "text-textSec")}
            >STATIC PACK</button>
            <button
              onClick={() => setNewPackData({ ...newPackData, is_animated: true })}
              className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", newPackData.is_animated ? "bg-purple-500 text-white" : "text-textSec")}
            >ANIMATED PACK</button>
          </div>
          <div className="bg-primary/5 border border-primary/20 p-4 rounded-xl flex items-center gap-3">
            <Info className="text-primary" size={20} />
            <span className="text-xs text-textMain/70 uppercase font-bold">
              {newPackData.is_animated
                ? "Animated pack: Supports GIF, Video and Animated WebP"
                : "Static pack: Supports PNG, JPG and Static WebP"}
            </span>
          </div>
          <button
            onClick={handleCreatePack}
            disabled={isProcessing}
            className="w-full bg-primary py-4 rounded-2xl font-black shadow-lg shadow-primary/20 hover:scale-[1.01] active:scale-95 transition-all disabled:opacity-50 text-white"
          >
            {isProcessing ? 'CREATING PACK...' : 'CREATE AND START'}
          </button>
        </div>
      </Modal>

      {/* Edit Pack Modal */}
      <Modal show={showEditPackModal} onClose={() => setShowEditPackModal(false)} title="Edit Pack Details">
        {selectedPack && (
          <div className="space-y-6">
            {/* Çoklu Dil Desteği */}
            <div className="bg-gradient-to-r from-primary/10 to-transparent border border-primary/20 rounded-2xl p-4 space-y-4">
              <div className="flex items-center gap-2 mb-2">
                <Globe className="text-primary" size={20} />
                <span className="text-sm font-bold text-white">Multi-Language Support</span>
                <span className="text-xs text-textSec ml-auto">Displayed based on selected language in the app</span>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  {/* Header */}
                  <div className="flex items-center justify-between mb-3">
                    <label className="flex items-center gap-2 text-primary font-bold text-sm">
                      <Globe size={18} />
                      Multi-Language Support ({TARGET_LANGUAGES.length} languages)
                    </label>
                    <button
                      onClick={() => handleAutoTranslate(true)}
                      disabled={isTranslating || !editFormData.name}
                      type="button"
                      className="px-4 py-2 bg-gradient-to-r from-primary to-accent text-white rounded-xl flex items-center gap-2 hover:opacity-90 transition-all font-bold text-sm disabled:opacity-40 disabled:cursor-not-allowed shadow-lg"
                    >
                      {isTranslating ? <RefreshCcw size={16} className="animate-spin" /> : <Sparkles size={16} />}
                      {isTranslating ? 'AI Translating...' : '✨ Auto Translate'}
                    </button>
                  </div>

                  {/* Ana İsim (İngilizce) */}
                  <div className="mb-3">
                    <label className="flex items-center gap-2 text-xs font-bold text-white/80 mb-1.5">
                      🇬🇧 English (Main Name) <span className="text-red-400">*</span>
                    </label>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        placeholder="e.g. Funny Cats, Love Stickers..."
                        className="flex-1 bg-bgSecondary border-2 border-primary/50 rounded-xl p-3 text-white placeholder:text-white/30 text-base focus:border-primary outline-none transition-all"
                        value={editFormData.name || ''}
                        onChange={(e) => setEditFormData({ ...editFormData, name: e.target.value, name_en: e.target.value })}
                      />
                      <button
                        type="button"
                        onClick={async () => {
                          const newName = await generateCreativeName(editFormData.name || "");
                          setEditFormData({ ...editFormData, name: newName, name_en: newName });
                        }}
                        className="px-4 bg-accent/10 border-2 border-accent/20 hover:bg-accent/20 hover:border-accent/50 text-accent rounded-xl transition-all flex items-center justify-center active:scale-95 group"
                        title="Suggest Creative Name"
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
                      <span className="w-2 h-2 rounded-full bg-violet-500"></span>
                      Dolu: {TARGET_LANGUAGES.filter(l => (editFormData as any)[`name_${l.code}`]).length}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-white/20"></span>
                      Empty: {TARGET_LANGUAGES.filter(l => !(editFormData as any)[`name_${l.code}`]).length}
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
                            <label className={`flex items-center gap-1.5 text-xs font-medium ${isFilled ? 'text-violet-400' : 'text-textSec'}`}>
                              <span>{lang.flag}</span> {lang.name}
                              {isFilled && <Check size={12} className="text-violet-400" />}
                            </label>
                            <input
                              type="text"
                              placeholder={`${lang.name}...`}
                              className={`w-full bg-bgSecondary border rounded-lg p-2 text-sm text-white placeholder:text-white/20 ${isFilled ? 'border-violet-500/30' : 'border-white/10'}`}
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
              label="Publisher"
              value={editFormData.publisher}
              onChange={(e: any) => setEditFormData({ ...editFormData, publisher: e.target.value })}
            />
            <Input
              label="Publisher Email"
              value={editFormData.publisher_email}
              onChange={(e: any) => setEditFormData({ ...editFormData, publisher_email: e.target.value })}
            />
            <div className="grid grid-cols-2 gap-4">
              <Input
                label="Privacy Policy Link"
                value={editFormData.privacy_policy_website}
                onChange={(e: any) => setEditFormData({ ...editFormData, privacy_policy_website: e.target.value })}
              />
              <Input
                label="License Agreement Link"
                value={editFormData.license_agreement_website}
                onChange={(e: any) => setEditFormData({ ...editFormData, license_agreement_website: e.target.value })}
              />
            </div>
            <div className="flex items-center gap-4">
              <div className="flex-1">
                <label className="text-xs font-bold text-textSec uppercase mb-2 block">Category</label>
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
            </div>

            {/* Premium Toggle */}
            <div>
              <label className="text-xs font-bold text-textSec uppercase mb-2 block">Premium Status</label>
              <div className="flex bg-hover rounded-xl p-1 gap-1">
                <button
                  onClick={() => setEditFormData({ ...editFormData, is_premium: false })}
                  className={cn("flex-1 py-2.5 rounded-lg text-[10px] font-black transition-all flex items-center justify-center gap-1.5", !editFormData.is_premium ? "bg-primary text-white" : "text-textSec")}
                >🆓 FREE</button>
                <button
                  onClick={() => setEditFormData({ ...editFormData, is_premium: true })}
                  className={cn("flex-1 py-2.5 rounded-lg text-[10px] font-black transition-all flex items-center justify-center gap-1.5", editFormData.is_premium ? "bg-yellow-500 text-black" : "text-textSec")}
                >💎 PREMIUM</button>
              </div>
              {editFormData.is_premium && !selectedPack?.is_premium && (
                <p className="text-[10px] text-yellow-400 mt-1.5">⚠️ Pack will be moved to premium_stickers collection when saved</p>
              )}
              {!editFormData.is_premium && selectedPack?.is_premium && (
                <p className="text-[10px] text-violet-400 mt-1.5">⚠️ Pack will be moved to stickers collection (free) when saved</p>
              )}
            </div>

            <div className="flex items-center gap-4">
              <div className="flex-1">
                <label className="text-xs font-bold text-textSec uppercase mb-2 block">Pack Type</label>
                <div className="bg-primary/10 border border-primary/20 p-2.5 rounded-xl flex items-center justify-center gap-2">
                  <RefreshCcw className="text-primary animate-spin" size={14} />
                  <span className="text-[10px] text-primary font-black uppercase">ANIMATED (REQUIRED)</span>
                </div>
              </div>
              <div className="flex-1">
                <label className="text-xs font-bold text-textSec uppercase mb-2 block">Status (Visibility)</label>
                <div className="flex bg-hover rounded-xl p-1 gap-1">
                  <button
                    onClick={() => setEditFormData({ ...editFormData, is_active: true })}
                    className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", editFormData.is_active !== false ? "bg-primary text-white" : "text-textSec")}
                  >ACTIVE</button>
                  <button
                    onClick={() => setEditFormData({ ...editFormData, is_active: false })}
                    className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", editFormData.is_active === false ? "bg-danger text-white" : "text-textSec")}
                  >INACTIVE (HIDDEN)</button>
                </div>
              </div>
              <div className="flex-1">
                <label className="text-xs font-bold text-textSec uppercase mb-2 block">Popular (Home Page)</label>
                <div className="flex bg-hover rounded-xl p-1 gap-1">
                  <button
                    onClick={() => setEditFormData({ ...editFormData, is_popular: true })}
                    className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", editFormData.is_popular === true ? "bg-yellow-500 text-white" : "text-textSec")}
                  >⭐ POPULAR</button>
                  <button
                    onClick={() => setEditFormData({ ...editFormData, is_popular: false })}
                    className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", editFormData.is_popular !== true ? "bg-hover text-textSec border border-white/10" : "text-textSec")}
                  >NORMAL</button>
                </div>
              </div>
            </div>
            <button
              onClick={handleUpdatePack}
              disabled={isProcessing}
              className="w-full bg-primary py-4 rounded-2xl font-black shadow-lg shadow-primary/20 flex items-center justify-center gap-2 hover:scale-[1.01] active:scale-95 transition-all disabled:opacity-50 text-white"
            >
              {isProcessing ? 'SAVING...' : (
                <>
                  <Save size={20} /> SAVE CHANGES
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
              <h3 className="text-3xl font-black text-white tracking-tight uppercase">Processing Stickers</h3>
              <div className="bg-white/5 px-6 py-2 rounded-2xl border border-white/5 inline-block">
                <p className="text-primary font-black uppercase tracking-[0.2em] text-[10px]">
                  {uploadProgress.message || 'Converting media to WhatsApp format...'}
                </p>
              </div>
            </div>

            <div className="w-full max-w-md space-y-4">
              <div className="flex items-center justify-between text-[10px] font-black text-textSec uppercase tracking-widest px-1">
                <span>Progress: {uploadProgress.current} / {uploadProgress.total}</span>
                <span className="text-primary">{Math.round((uploadProgress.current / uploadProgress.total) * 100)}%</span>
              </div>
              <div className="w-full bg-white/5 h-3 rounded-full overflow-hidden border border-white/5 p-1">
                <div
                  className="h-full bg-gradient-to-r from-primary via-accent to-primary shadow-[0_0_20px_rgba(108,92,231,0.6)] transition-all duration-700 ease-out rounded-full"
                  style={{ width: `${(uploadProgress.current / uploadProgress.total) * 100}%` }}
                />
              </div>
            </div>

            <div className="bg-primary/5 border border-primary/20 p-5 rounded-3xl flex items-center gap-4 max-w-sm">
              <div className="bg-primary/20 p-2 rounded-xl text-primary">
                <Info size={20} />
              </div>
              <p className="text-[10px] text-textMain/70 font-bold uppercase leading-relaxed text-left">
                Video and GIF processing requires CPU power. Please do not interrupt the process.
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
                  System Transfer
                </h3>
                <p className="text-textSec font-bold text-sm tracking-wide">
                  Draft pack is being transferred to the main server...
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
                  <span>TRANSFERRED: {deleteProgress.current} Stickers</span>
                </div>
                <div className="bg-white/10 px-3 py-1 rounded-lg">
                  <span className="text-primary">{deleteProgress.total > 0 ? Math.round((deleteProgress.current / deleteProgress.total) * 100) : 0}%</span>
                </div>
              </div>

              <div className="w-full bg-white/5 h-4 rounded-full overflow-hidden border border-white/10 p-1.5">
                <div
                  className="h-full bg-gradient-to-r from-primary via-purple-400 to-primary shadow-[0_0_30px_rgba(108,92,231,0.5)] transition-all duration-500 ease-out rounded-full"
                  style={{ width: `${deleteProgress.total > 0 ? (deleteProgress.current / deleteProgress.total) * 100 : 0}%` }}
                />
              </div>

              <div className="pt-2">
                <p className="text-[10px] text-textSec font-bold uppercase italic opacity-40">
                  Once this process is complete, the pack will be published in the app.
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
                className="max-w-full max-h-[80vh] object-contain drop-shadow-[0_0_50px_rgba(108,92,231,0.3)] animate-in zoom-in-90 duration-300"
                onClick={(e) => e.stopPropagation()}
              />
              <div className="absolute -bottom-12 left-1/2 -translate-x-1/2 text-primary font-black text-sm tracking-widest uppercase opacity-0 group-hover:opacity-100 transition-opacity">
                STICKER PREVIEW
              </div>
            </div>
          </div>
        )
      }

      {/* Video/GIF Background Removal Modal */}
      <Modal show={showVideoBgModal} onClose={() => setShowVideoBgModal(false)} title="Animated Media Processing">
        <div className="space-y-6">
          <div className="bg-primary/10 border border-primary/20 p-6 rounded-2xl flex items-center gap-4">
            <div className="bg-primary/20 p-3 rounded-xl animate-pulse">
              <CloudLightning className="text-primary" size={28} />
            </div>
            <div>
              <h3 className="text-lg font-black text-white">Remove Background?</h3>
              <p className="text-textSec text-xs mt-1">The background of the uploaded video or GIF can be automatically removed using AI.</p>
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
                <span className="block font-black text-sm text-white group-hover:text-textMain">NO</span>
                <span className="text-[10px] text-textSec">Keep as is</span>
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
                <span className="block font-black text-sm text-white">YES, REMOVE</span>
                <span className="text-[10px] text-white/70">Remove with AI</span>
              </div>
            </button>
          </div>

          <p className="text-[10px] text-center text-textSec opacity-60">
            Note: Background removal may take some time depending on file length.
          </p>
        </div>
      </Modal>

      {/* Sticker Import Modal - Serbest Arama + Kaynak Seçimi */}
      <Modal show={showImportModal} onClose={() => !isImporting && setShowImportModal(false)} title="🚀 Sticker Import">
        <div className="space-y-6">
          <div className="bg-gradient-to-r from-purple-600/10 to-pink-600/10 border border-purple-500/20 p-6 rounded-2xl flex items-center gap-4">
            <div className="bg-gradient-to-r from-purple-600 to-pink-600 p-3 rounded-xl">
              <Sparkles className="text-white" size={28} />
            </div>
            <div>
              <h3 className="text-lg font-black text-white">Sticker Import</h3>
              <p className="text-textSec text-xs mt-1">Add stickers from Giphy with free search.</p>
            </div>
          </div>

          {!isImporting ? (
            <>
              <div className="space-y-4">
                {/* Kaynak - Sadece Giphy (Klipy API key geçersiz) */}
                <div>
                  <label className="text-[10px] font-black text-textSec uppercase tracking-[0.2em] px-1 mb-2 block">Source</label>
                  <div className="bg-gradient-to-r from-purple-600 to-pink-600 rounded-xl p-3 text-center">
                    <span className="text-white text-sm font-black">Giphy</span>
                  </div>
                </div>

                {/* Serbest Arama */}
                <div>
                  <label className="text-[10px] font-black text-textSec uppercase tracking-[0.2em] px-1 mb-2 block">Search Term</label>
                  <div className="relative">
                    <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-textSec" size={16} />
                    <input
                      type="text"
                      value={customSearchText}
                      onChange={(e) => setCustomSearchText(e.target.value)}
                      placeholder="Search anything... (e.g. cute cats, anime reactions, neon text)"
                      className="w-full bg-card/60 border border-white/10 rounded-xl pl-11 pr-4 py-3 text-white text-sm font-medium outline-none focus:ring-2 focus:ring-purple-500 transition-all"
                    />
                  </div>
                </div>

                {/* İçerik Tipi */}
                <div>
                  <label className="text-[10px] font-black text-textSec uppercase tracking-[0.2em] px-1 mb-2 block">Content Type</label>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      onClick={() => setImportContentType('stickers')}
                      className={cn(
                        "flex flex-col items-center gap-2 p-3 rounded-xl border transition-all",
                        importContentType === 'stickers'
                          ? "bg-purple-600/10 border-purple-500/30 text-white"
                          : "bg-card border-white/5 text-textSec hover:border-white/10"
                      )}
                    >
                      <div className={cn("w-6 h-6 rounded-lg flex items-center justify-center text-xs", importContentType === 'stickers' ? "bg-purple-600" : "bg-white/10")}>
                        {importContentType === 'stickers' && '✓'}
                      </div>
                      <div className="text-center">
                        <span className="text-xs font-bold block">Stickers</span>
                        <span className="text-[9px] text-textSec">Cartoon/Graphic</span>
                      </div>
                    </button>
                    <button
                      onClick={() => setImportContentType('gifs')}
                      className={cn(
                        "flex flex-col items-center gap-2 p-3 rounded-xl border transition-all",
                        importContentType === 'gifs'
                          ? "bg-purple-600/10 border-purple-500/30 text-white"
                          : "bg-card border-white/5 text-textSec hover:border-white/10"
                      )}
                    >
                      <div className={cn("w-6 h-6 rounded-lg flex items-center justify-center text-xs", importContentType === 'gifs' ? "bg-purple-600" : "bg-white/10")}>
                        {importContentType === 'gifs' && '✓'}
                      </div>
                      <div className="text-center">
                        <span className="text-xs font-bold block">GIFs</span>
                        <span className="text-[9px] text-textSec">Real video/photo</span>
                      </div>
                    </button>
                  </div>
                </div>

                {/* Sticker Sayısı */}
                <div>
                  <label className="text-[10px] font-black text-textSec uppercase tracking-[0.2em] px-1 mb-2 block">
                    How many stickers to add? ({importCount})
                  </label>
                  <input
                    type="range"
                    min="5"
                    max="30"
                    step="5"
                    value={importCount}
                    onChange={(e) => setImportCount(Number(e.target.value))}
                    className="w-full h-2 bg-hover rounded-lg appearance-none cursor-pointer accent-purple-600"
                  />
                  <div className="flex justify-between text-xs text-textSec mt-1 px-1">
                    <span>5</span><span>15</span><span>30</span>
                  </div>
                </div>

                {/* Özet */}
                <div className="bg-white/5 p-4 rounded-xl text-xs text-textSec space-y-1">
                  <p>📦 <strong className="text-white">Pack:</strong> {selectedPack?.name}</p>
                  <p>📊 <strong className="text-white">Current:</strong> {selectedPack?.sticker_count || 0}</p>
                  <p>🔍 <strong className="text-white">Search:</strong> {customSearchText || 'trending'}</p>
                  <p>🌐 <strong className="text-white">Source:</strong> Giphy</p>
                  <p>🎬 <strong className="text-white">Type:</strong> {importContentType === 'gifs' ? 'GIFs' : 'Stickers'}</p>
                  <p>➕ <strong className="text-white">To add:</strong> {importCount}</p>
                  <p>🎯 <strong className="text-white">Total:</strong> {(selectedPack?.sticker_count || 0) + importCount} / 30</p>
                </div>
              </div>

              <div className="flex gap-3">
                <button
                  onClick={() => setShowImportModal(false)}
                  className="flex-1 px-6 py-3.5 bg-card hover:bg-hover border border-white/5 rounded-2xl text-sm font-bold transition-all text-textSec hover:text-white"
                >
                  Cancel
                </button>
                <button
                  onClick={handleImportStickers}
                  disabled={!selectedPack}
                  className="flex-1 px-6 py-3.5 bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-700 hover:to-pink-700 text-white rounded-2xl text-sm font-black shadow-xl shadow-purple-500/20 transition-all hover:translate-y-[-2px] active:translate-y-0 disabled:opacity-50"
                >
                  🚀 Start
                </button>
              </div>
            </>
          ) : (
            <div className="space-y-4">
              <div className="bg-card p-6 rounded-2xl space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold text-white">
                    {importProgress?.current || 0} / {importProgress?.total || 0}
                  </span>
                  <span className="text-xs text-textSec">
                    {Math.round((importProgress?.current || 0) / (importProgress?.total || 1) * 100)}%
                  </span>
                </div>

                <div className="w-full bg-white/5 h-2 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-purple-600 to-pink-600 transition-all duration-300"
                    style={{ width: `${(importProgress?.current || 0) / (importProgress?.total || 1) * 100}%` }}
                  />
                </div>

                <p className="text-xs text-textSec text-center">{importProgress?.message}</p>

                {importProgress?.preview && (
                  <div className="flex justify-center">
                    <img
                      src={importProgress.preview}
                      alt="Preview"
                      className="w-32 h-32 object-contain rounded-xl border border-white/10"
                    />
                  </div>
                )}
              </div>

              <p className="text-[10px] text-center text-textSec opacity-60 animate-pulse">
                ⏳ Please wait, stickers are being processed...
              </p>
            </div>
          )}
        </div>
      </Modal>

    </div >
  );
}

export default App;
