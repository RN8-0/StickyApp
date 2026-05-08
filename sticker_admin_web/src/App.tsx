import { useState, useEffect, useMemo, useRef } from 'react';
import { createPortal } from 'react-dom';
import { pb, signInWithGitHub, WORKER_URL, getFileUrl, uploadFile } from './pocketbase';

interface AdminUser { email: string; }
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
  List,
  Star,
  AlertTriangle,
  FileText,
  Flag,
  UserPlus,
  Inbox,
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
import type { StickerPack, Sticker, ContactMessage, StickerSuggestion, UserData, SubscriptionHistoryItem, PublisherUser, UserSubmission } from './types';
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { stickerProcessor } from './utils/stickerProcessor';
import { importStickers, type StickerImportProgress } from './utils/stickerImporter';
import { deepseekService } from './utils/deepseekService';
import { importTelegramPacks, validateBotToken } from './utils/telegramImporter';

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

// Simple admin audit log
async function logAdminAction(action: string, detail?: string, packId?: string) {
  try {
    const user = pb.authStore.record;
    await pb.collection('admin_logs').create({
      admin_email: user?.email || 'unknown',
      action,
      detail: detail || '',
      pack_id: packId || '',
      timestamp: new Date().toISOString(),
    }).catch(() => { /* collection may not exist */ });
  } catch { /* silent */ }
}

function packEngagementScore(pack: Partial<StickerPack>) {
  const downloads = Number(pack.download_count || 0);
  const favorites = Number(pack.favorite_count || 0);
  const likes = Number(pack.like_count || 0);
  const comments = Number(pack.comment_count || 0);
  const views = Number(pack.view_count || 0);
  const createdAt = pack.created_at ? new Date(pack.created_at as any).getTime() : 0;
  const ageHours = createdAt > 0 ? Math.max(1, (Date.now() - createdAt) / 36e5) : 240;
  const freshness = 120 / Math.pow(ageHours + 2, 0.35);
  const communityBoost = pack.source === 'user_submission' || pack.publisher_user_id ? 18 : 0;
  return Math.round((downloads * 5 + favorites * 4 + likes * 6 + comments * 8 + views * 0.35 + freshness + communityBoost) * 100) / 100;
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

const PACK_NAME_EMOJIS = [
  '😂', '❤️', '🔥', '✨', '😍', '😘', '🥰', '😭', '😎', '🤩', '🥳', '😈',
  '🎉', '🎁', '🎂', '💎', '⭐', '🌟', '💫', '⚡', '🚀', '💯', '👑', '🔔',
  '🐱', '🐶', '🐻', '🐼', '🦊', '🐰', '🧸', '🌸', '🌙', '☀️', '🌈', '🌿',
  '🎮', '🎬', '🎵', '⚽', '🍔', '☕', '🚗', '✍️', '🕌', '⛩️', '🎭', '📂'
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
  const [user, setUser] = useState<AdminUser | null>(null);
  console.info('[App] Initialized');

  const [packs, setPacks] = useState<StickerPack[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedPack, setSelectedPack] = useState<StickerPack | null>(null);
  const [selectedStickerIds, setSelectedStickerIds] = useState<string[]>([]);
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [panelDragIdx, setPanelDragIdx] = useState<number | null>(null);
  const [panelDragOverIdx, setPanelDragOverIdx] = useState<number | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState<'dashboard' | 'packs' | 'stats' | 'messages' | 'notifications' | 'users' | 'imports' | 'submissions'>('dashboard');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'passive' | 'animated' | 'static' | 'premium' | 'new' | 'user_submission'>('all');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [statsFilter, setStatsFilter] = useState<'all' | 'active' | 'passive' | 'premium' | 'normal' | 'popular'>('all');
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);
  const [showMobileMenu, setShowMobileMenu] = useState(false);
  const statusFilterRef = useRef<HTMLButtonElement>(null);
  const categoryFilterRef = useRef<HTMLButtonElement>(null);
  const statsFilterRef = useRef<HTMLButtonElement>(null);

  // Login form state (must be declared at top level, before any early returns)
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');

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
    publisher_email: '',
    publisher_user_id: '',
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

  // Publisher Users State
  const [publisherUsers, setPublisherUsers] = useState<PublisherUser[]>([]);
  const [usersSubTab, setUsersSubTab] = useState<'users' | 'publishers'>('users');
  const [showPublisherModal, setShowPublisherModal] = useState(false);
  const [editingPublisher, setEditingPublisher] = useState<PublisherUser | null>(null);
  const [publisherFormData, setPublisherFormData] = useState({
    display_name: '', avatar_url: '', bio: '', category: 'community', is_active: true
  });

  // Submissions State
  const [userSubmissions, setUserSubmissions] = useState<UserSubmission[]>([]);
  const [submissionFilter, setSubmissionFilter] = useState<'pending' | 'flagged' | 'approved' | 'rejected'>('approved');
  const [selectedSubmission, setSelectedSubmission] = useState<UserSubmission | null>(null);
  const [submissionEditData, setSubmissionEditData] = useState<UserSubmission | null>(null);
  const [rejectModalSubmission, setRejectModalSubmission] = useState<UserSubmission | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [feedbackModalSubmission, setFeedbackModalSubmission] = useState<UserSubmission | null>(null);
  const [feedbackMessage, setFeedbackMessage] = useState('');
  const [feedbackSending, setFeedbackSending] = useState(false);

  const [showVideoBgModal, setShowVideoBgModal] = useState(false);
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);

  const [showImportModal, setShowImportModal] = useState(false);

  const [importContentType, setImportContentType] = useState<'gifs' | 'stickers'>('stickers');
  const [importCount, setImportCount] = useState(20);
  const [customSearchText, setCustomSearchText] = useState('');
  const [isImporting, setIsImporting] = useState(false);
  const [importProgress, setImportProgress] = useState<{ current: number, total: number, message: string, preview?: string } | null>(null);


  // Telegram import and draft review states
  const [importSubTab, setImportSubTab] = useState<'telegram' | 'drafts'>('telegram');
  const [draftPacks, setDraftPacks] = useState<StickerPack[]>([]);
  const [draftLoading, setDraftLoading] = useState(false);
  const [selectedDraft, setSelectedDraft] = useState<StickerPack | null>(null);
  const [showDraftEditModal, setShowDraftEditModal] = useState(false);
  const [draftEditData, setDraftEditData] = useState<any>({});
  const [draftLangSearch, setDraftLangSearch] = useState('');
  const [draftPublishing, setDraftPublishing] = useState<string | null>(null);
  const [draftDeleting, setDraftDeleting] = useState<string | null>(null);
  const [deleteAllProgress, setDeleteAllProgress] = useState<{ current: number; total: number } | null>(null);
  const [publishAllProgress, setPublishAllProgress] = useState<{ current: number; total: number; currentName?: string } | null>(null);
  const [singlePublishProgress, setSinglePublishProgress] = useState<{ step: string; percent: number } | null>(null);
  const [singleDeleteProgress, setSingleDeleteProgress] = useState<{ step: string; fileProgress?: { current: number; total: number } } | null>(null);
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

  // Log state for imports
  const [telegramLogs, setTelegramLogs] = useState<{ time: string; message: string; type: 'info' | 'success' | 'error' | 'warn' }[]>([]);
  const telegramLogRef = useRef<HTMLDivElement>(null);

  // Helper: detect log type from progress message
  const getLogType = (msg: string): 'info' | 'success' | 'error' | 'warn' => {
    if (msg.startsWith('✅') || msg.startsWith('✓') || msg.includes('published') || msg.includes('Complete') || msg.includes('done') || msg.includes('imported!')) return 'success';
    if (msg.startsWith('❌') || msg.includes('failed') || msg.includes('Error') || msg.includes('error')) return 'error';
    if (msg.startsWith('⚠️') || msg.startsWith('⏭️') || msg.startsWith('⏱️') || msg.includes('skipping') || msg.includes('timeout') || msg.includes('Mixed pack')) return 'warn';
    return 'info';
  };
  const addTelegramLog = (msg: string) => {
    const time = new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    setTelegramLogs(prev => [...prev, { time, message: msg, type: getLogType(msg) }]);
    setTimeout(() => telegramLogRef.current?.scrollTo({ top: telegramLogRef.current.scrollHeight, behavior: 'smooth' }), 50);
  };

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

  const handleSignOut = () => {
    pb.authStore.clear();
    setUser(null);
  };

  const fetchMessagesAndSuggestions = async () => {
    try {
      const [msgRecords, suggRecords] = await Promise.all([
        pb.collection('messages').getFullList({ sort: '-timestamp' }).catch(() => []),
        pb.collection('suggestions').getFullList({ sort: '-timestamp' }).catch(() => []),
      ]);
      setMessages((msgRecords as any[]).map(r => ({ id: r.id, name: r.name || r.title, email: r.email, subject: r.subject || '', message: r.message || r.body || '', timestamp: r.timestamp || new Date(r.created).getTime(), date: r.date || r.created?.split('T')[0] || '', time: r.time || '', status: r.status || 'unread' } as ContactMessage)));
      setSuggestions((suggRecords as any[]).map(r => ({ id: r.id, suggestion: r.suggestion || r.text || '', timestamp: r.timestamp || new Date(r.created).getTime(), date: r.date || r.created?.split('T')[0] || '', time: r.time || '', category: r.category || 'other' } as StickerSuggestion)));
    } catch (e) {
      console.error('PB messages/suggestions fetch error:', e);
    }
  };

  useEffect(() => {
    // Initial check
    const checkAuth = () => {
      try {
        if (pb.authStore.isValid && pb.authStore.record?.email) {
          setUser({ email: pb.authStore.record.email });
          fetchPacks();
          return true;
        }
      } catch (e) {
        console.warn('[Auth] init error:', e);
      }
      return false;
    };

    const authReady = checkAuth();

    // Force loading timeout — after 8s show login even if auth is stuck
    const loadingTimeout = setTimeout(() => {
      setLoading(false);
    }, 8000);

    if (!authReady) {
      setLoading(false);
      clearTimeout(loadingTimeout);
    }

    // Listen for auth changes (e.g., OAuth popup completes)
    const unsubscribe = pb.authStore.onChange((token, record) => {
      clearTimeout(loadingTimeout);
      if (token && record?.email) {
        setUser({ email: record.email });
        setLoading(false);
        fetchPacks();
      } else {
        setUser(null);
        setLoading(false);
      }
    });

    return () => { unsubscribe(); clearTimeout(loadingTimeout); };
  }, []);

  useEffect(() => {
    if (!user) return;
    let mounted = true;
    fetchMessagesAndSuggestions();
    const subscriptions = [
      pb.collection('messages').subscribe('*', () => { if (mounted) fetchMessagesAndSuggestions(); }).catch((e) => { console.warn('Messages realtime unavailable:', e); return null; }),
      pb.collection('suggestions').subscribe('*', () => { if (mounted) fetchMessagesAndSuggestions(); }).catch((e) => { console.warn('Suggestions realtime unavailable:', e); return null; }),
    ];
    return () => {
      mounted = false;
      subscriptions.forEach((promise) => promise.then((unsubscribe) => unsubscribe?.()).catch(() => {}));
    };
  }, [user]);

  // Fetch publisher_users and user_submissions from PocketBase
  useEffect(() => {
    let mounted = true;
    const fetchData = async () => {
      try {
        const [publishers, submissions] = await Promise.all([
          pb.collection('publisher_users').getFullList({ sort: 'name' }).catch(() => []),
          pb.collection('user_submissions').getFullList({ sort: '-created_at' }).catch(() => []),
        ]);
        if (!mounted) return;
        setPublisherUsers((publishers as any[]).map(r => ({ id: r.id, display_name: r.display_name || r.name, avatar_url: r.avatar_url || '', bio: r.bio || '', category: r.category || '', packs_published: r.packs_published || 0, total_downloads: r.total_downloads || 0, created_at: r.created, is_active: r.is_active !== false } as PublisherUser)).sort((a, b) => (a.display_name || '').localeCompare(b.display_name || '')));
        setUserSubmissions((submissions as any[]).map(r => {
          const createdValue = r.created_at || r.created;
          const createdMs = createdValue ? new Date(createdValue).getTime() : 0;
          const collId = r.collectionId || 'user_submissions';
          const parseField = (raw: any): any[] => {
            if (Array.isArray(raw) && raw.length > 0) return raw;
            if (typeof raw === 'string' && raw.length > 2) {
              try { const p = JSON.parse(raw); return Array.isArray(p) ? p : []; } catch { return []; }
            }
            return [];
          };
          const images = Array.isArray(r.images) ? r.images : (typeof r.images === 'string' && r.images ? [r.images] : []);
          const derivedStickers = images.map((filename: string, index: number) => ({
            name: `sticker_${index + 1}`,
            image_file: filename,
            image_url: getFileUrl(collId, r.id, filename),
            url: getFileUrl(collId, r.id, filename),
            emojis: ['⭐'],
          }));
          const parsedStickers = parseField(r.stickers);
          const parsedStickerData = parseField(r.sticker_data);
          // Ensure each sticker has image_url populated via PocketBase file API
          const enrichStickers = (arr: any[]) => arr.map((s: any) => ({
            ...s,
            image_url: s.image_url || s.url || getFileUrl(collId, r.id, s.image_file || s.name || ''),
            url: s.url || s.image_url || getFileUrl(collId, r.id, s.image_file || s.name || ''),
          }));
          // Use the source with the most stickers: prefer images count when stickers JSON is partial
          const jsonStickers = parsedStickers.length > 0 ? enrichStickers(parsedStickers)
            : parsedStickerData.length > 0 ? enrichStickers(parsedStickerData)
            : [];
          // If images field has more entries than the stickers JSON, use derivedStickers (all uploaded files)
          const stickers = derivedStickers.length > jsonStickers.length ? derivedStickers : (jsonStickers.length > 0 ? jsonStickers : derivedStickers);
          return { id: r.id, user_id: r.user_id || '', device_id: r.device_id || '', user_email: r.user_email || '', display_name: r.display_name || '', publisher_name: r.publisher_name, pack_name: r.pack_name || r.name || '', description: r.description, category: r.category || '', stickers, status: r.status || 'pending', flag_reasons: r.flag_reasons, rejection_reason: r.rejection_reason, sticker_count: r.sticker_count || stickers.length || 0, sticker_pack_id: r.sticker_pack_id, created_at: { seconds: createdMs / 1000 }, note: r.note } as UserSubmission;
        }).sort((a, b) => (b.created_at?.seconds || 0) - (a.created_at?.seconds || 0)));
      } catch (e) {
        console.error('PB publishers/submissions fetch error:', e);
      }
    };
    fetchData();
    const subscriptions = [
      pb.collection('publisher_users').subscribe('*', () => { if (mounted) fetchData(); }).catch((e) => { console.warn('Publisher realtime unavailable:', e); return null; }),
      pb.collection('user_submissions').subscribe('*', () => { if (mounted) fetchData(); }).catch((e) => { console.warn('Submission realtime unavailable:', e); return null; }),
    ];
    return () => {
      mounted = false;
      subscriptions.forEach((promise) => promise.then((unsubscribe) => unsubscribe?.()).catch(() => {}));
    };
  }, []);

  // Publisher User CRUD handlers
  const handleSavePublisher = async () => {
    try {
      const pubData = {
        display_name: publisherFormData.display_name,
        name: publisherFormData.display_name,
        avatar_url: publisherFormData.avatar_url,
        bio: publisherFormData.bio,
        category: publisherFormData.category,
        is_active: publisherFormData.is_active,
      };
      if (editingPublisher?.id) {
        await pb.collection('publisher_users').update(editingPublisher.id, pubData);
      } else {
        await pb.collection('publisher_users').create({ ...pubData, packs_published: 0, total_downloads: 0 });
      }
      setShowPublisherModal(false);
      setEditingPublisher(null);
      setPublisherFormData({ display_name: '', avatar_url: '', bio: '', category: 'community', is_active: true });
    } catch (e) {
      console.error('Publisher save error:', e);
      alert('Failed to save publisher user.');
    }
  };

  const handleDeletePublisher = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this publisher user?')) return;
    try {
      await pb.collection('publisher_users').delete(id);
      setPublisherUsers(publisherUsers.filter(p => p.id !== id));
    } catch (e) {
      console.error('Publisher delete error:', e);
      alert('Failed to delete publisher user.');
    }
  };

  // Submission handlers
  const getSubmissionRecipientId = (submission: UserSubmission) =>
    submission.device_id || submission.user_id || submission.user_email || '';

  const createSubmissionNotification = async (submission: UserSubmission, title: string, body: string, data: Record<string, unknown> = {}) => {
    const recipientId = getSubmissionRecipientId(submission);
    if (!recipientId) throw new Error('Submission has no recipient identifier.');

    const basePayload = {
      title,
      body,
      message: body,
      user_id: recipientId,
      pack_id: submission.id,
      from: 'admin',
      read: false,
      sent: false,
    };

    const payload = {
      ...basePayload,
      timestamp: new Date().toISOString(),
      topic: 'user_submission',
      data: {
        ...data,
        submission_id: submission.id,
        pack_name: submission.pack_name,
        user_id: submission.user_id || '',
        user_email: submission.user_email || '',
        device_id: submission.device_id || '',
      },
    };

    try {
      await pb.collection('notifications').create(payload);
    } catch (e) {
      console.warn('Submission notification metadata skipped:', e);
      await pb.collection('notifications').create(basePayload);
    }
  };

  const notifySubmissionUser = async (submission: UserSubmission, title: string, body: string, data: Record<string, unknown> = {}) => {
    // 1. Create in-app notification in PocketBase (hooks will trigger FCM via individual tokens)
    await createSubmissionNotification(submission, title, body, data)
      .catch((e) => console.warn('Submission notification skipped:', e));

    // 2. Send push via worker /api/notify (both device-specific AND broadcast for reliability)
    const deviceId = submission.device_id;
    const notifyPayload: any = { title, body, data };
    if (deviceId) notifyPayload.deviceId = deviceId;

    // Use the full worker URL from PocketBase config for direct access
    const workerBaseUrl = 'https://sticky-worker.46.225.95.201.sslip.io';
    fetch(`${workerBaseUrl}/api/notify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(notifyPayload),
    }).catch((e) => console.warn('Push notification (device) skipped:', e));

    // Also send via nginx-proxied worker URL as fallback
    fetch(`${WORKER_URL}/api/notify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(notifyPayload),
    }).catch((e) => console.warn('Push notification (proxied) skipped:', e));
  };

  const handleEditSubmission = (submission: UserSubmission) => {
    setSelectedSubmission(submission);
    setSubmissionEditData({ ...submission, stickers: [...(submission.stickers || [])] });
  };

  const handleRemoveSubmissionSticker = (index: number) => {
    if (!submissionEditData) return;
    setSubmissionEditData({
      ...submissionEditData,
      stickers: submissionEditData.stickers.filter((_, i) => i !== index),
      sticker_count: Math.max(0, (submissionEditData.stickers?.length || 1) - 1),
    });
  };

  const handleSaveSubmissionEdit = async () => {
    if (!selectedSubmission || !submissionEditData) return;
    const stickers = submissionEditData.stickers || [];
    const updateData = {
      pack_name: submissionEditData.pack_name,
      name: submissionEditData.pack_name,
      category: submissionEditData.category || 'other',
      description: submissionEditData.description || '',
      stickers,
      sticker_data: stickers,
      sticker_count: stickers.length,
    };
    try {
      await pb.collection('user_submissions').update(selectedSubmission.id, updateData);
      if (selectedSubmission.status === 'approved' && selectedSubmission.sticker_pack_id) {
        const firstSticker: any = stickers[0] || {};
        await pb.collection('stickers').update(selectedSubmission.sticker_pack_id, {
          name: updateData.pack_name,
          category: updateData.category,
          stickers: stickers.map((s: any) => ({ image_file: s.image_file || s.name, url: s.image_url || s.url || '', emojis: s.emojis || ['⭐'] })),
          sticker_count: stickers.length,
          tray_image_file: firstSticker.image_file || firstSticker.name || '',
          tray_url: firstSticker.image_url || firstSticker.url || '',
        }).catch(() => {});
      }
      setUserSubmissions(userSubmissions.map(s => s.id === selectedSubmission.id ? { ...s, ...updateData } as UserSubmission : s));
      setSelectedSubmission(null);
      setSubmissionEditData(null);
    } catch (e) {
      console.error('Submission edit save error:', e);
      alert('Failed to save submission changes.');
    }
  };

  const handleApproveSubmission = async (submission: UserSubmission) => {
    if (!window.confirm(`Approve "${submission.pack_name}" and move to stickers collection?`)) return;
    setIsProcessing(true);
    try {
      const submissionStickers: any[] = submission.stickers || [];
      if (submissionStickers.length < 9) {
        alert('A public pack must contain at least 9 stickers. Edit the submission or reject it with a reason.');
        setIsProcessing(false);
        return;
      }

      const sourceStickerRefs: Sticker[] = submissionStickers
        .filter((s: any) => s.image_url || s.url)
        .map((s: any, i: number) => ({
          image_file: s.image_file || s.name || `sticker_${i + 1}.webp`,
          url: s.image_url || s.url || '',
          emojis: s.emojis || ['⭐'],
        }));

      if (sourceStickerRefs.length < 9) {
        alert('A public pack must contain at least 9 stickers with valid image URLs.');
        setIsProcessing(false);
        return;
      }

      // 2. Create the pack with existing submission sticker URLs (no re-upload needed)
      const packData: any = {
        name: submission.pack_name,
        publisher: submission.publisher_name || submission.display_name || 'Community Artist',
        publisher_email: submission.user_email || '',
        publisher_user_id: submission.user_id || submission.user_email || '',
        publisher_photo_url: (submission as any).photo_url || (submission as any).user_photo_url || '',
        category: submission.category || 'other',
        is_premium: false,
        is_animated: false,
        download_count: 0,
        view_count: 0,
        favorite_count: 0,
        like_count: 0,
        comment_count: 0,
        engagement_score: 0,
        sticker_count: sourceStickerRefs.length,
        image_data_version: '1',
        is_active: true,
        stickers: sourceStickerRefs,
        tray_url: sourceStickerRefs[0]?.url || '',
        created_at: new Date().toISOString(),
      };
      const created = await pb.collection('stickers').create(packData);

      // 3. Update submission status
      await pb.collection('user_submissions').update(submission.id, {
        status: 'approved',
        sticker_pack_id: created.id,
        processed_at: new Date().toISOString(),
      });

      // 5. Send notification to user
      await notifySubmissionUser(submission, 'Pack approved! 🎉', `Your pack "${submission.pack_name}" has been approved and is now live in Sticky!`, { type: 'submission_approved', sticker_pack_id: created.id });

      pb.collection('user_follows').getFullList().then(async (follows: any[]) => {
        const publisherKeys = [submission.user_id, submission.user_email, submission.display_name, submission.publisher_name]
          .filter(Boolean)
          .map((value) => String(value).trim().toLowerCase());
        const recipients = follows.filter((follow) =>
          [follow.target_id, follow.target_email, follow.target_name, follow.following_id, follow.following_email]
            .filter(Boolean)
            .some((value) => publisherKeys.includes(String(value).trim().toLowerCase()))
        );
        await Promise.all(recipients.map((follow) => pb.collection('notifications').create({
          title: 'New sticker pack',
          body: `${submission.publisher_name || submission.display_name || 'A creator you follow'} published "${submission.pack_name}"`,
          message: `${submission.publisher_name || submission.display_name || 'A creator you follow'} published "${submission.pack_name}"`,
          user_id: follow.follower_id || follow.follower_email,
          pack_id: created.id,
          from: 'publisher_follow',
          read: false,
          sent: false,
          timestamp: new Date().toISOString(),
          topic: 'followed_publisher_pack',
          data: { type: 'followed_publisher_pack', sticker_pack_id: created.id, publisher_user_id: submission.user_id || '', publisher_email: submission.user_email || '' },
        }).catch((e) => console.warn('Follower notification skipped:', e))));
      }).catch((e) => console.warn('Follower notification lookup skipped:', e));

      setUserSubmissions(userSubmissions.map(s => s.id === submission.id ? { ...s, status: 'approved' as any, sticker_pack_id: created.id } : s));
      alert(`"${submission.pack_name}" approved and published!`);
    } catch (e: any) {
      console.error('Approve error:', e);
      alert('Failed to approve submission: ' + (e.message || 'Unknown error'));
    } finally {
      setIsProcessing(false);
    }
  };

  const handleRejectSubmission = (submission: UserSubmission) => {
    setRejectReason('');
    setRejectModalSubmission(submission);
  };

  const confirmRejectSubmission = async () => {
    const submission = rejectModalSubmission;
    if (!submission) return;
    const finalReason = rejectReason.trim() || 'Your submission did not meet our content guidelines.';
    try {
      await pb.collection('user_submissions').update(submission.id, {
        status: 'rejected',
        rejection_reason: finalReason,
        processed_at: new Date().toISOString(),
      });
      await notifySubmissionUser(submission, 'Sticker pack rejected', `Your pack "${submission.pack_name}" was rejected. Reason: ${finalReason}`, { type: 'submission_rejected', reason: finalReason });
      setUserSubmissions(userSubmissions.map(s => s.id === submission.id ? { ...s, status: 'rejected' as any, rejection_reason: finalReason } : s));
      setRejectModalSubmission(null);
      setRejectReason('');
    } catch (e) {
      console.error('Reject error:', e);
      alert('Failed to reject submission.');
    }
  };

  const handleDeleteSubmission = async (submission: UserSubmission) => {
    const msg = submission.status === 'approved'
      ? `Permanently delete "${submission.pack_name}"? This will also remove it from the public sticker store.`
      : `Permanently delete "${submission.pack_name}"? This cannot be undone.`;
    if (!window.confirm(msg)) return;
    try {
      if (submission.status === 'approved' && submission.sticker_pack_id) {
        const cascade = await fetch(`${WORKER_URL}/api/social/pack/delete`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ submission_id: submission.id, pack_id: submission.sticker_pack_id, collection: 'stickers', admin: true })
        }).catch(() => null);
        if (!cascade?.ok) {
          await pb.collection('user_submissions').delete(submission.id);
          await pb.collection('stickers').delete(submission.sticker_pack_id).catch(() => {});
        }
      } else {
        await pb.collection('user_submissions').delete(submission.id);
      }
      setUserSubmissions(userSubmissions.filter(s => s.id !== submission.id));
    } catch (e) {
      console.error('Delete submission error:', e);
      alert('Failed to delete submission.');
    }
  };

  const handleSendFeedback = (submission: UserSubmission) => {
    setFeedbackMessage('');
    setFeedbackModalSubmission(submission);
  };

  const confirmSendFeedback = async () => {
    const submission = feedbackModalSubmission;
    if (!submission || !feedbackMessage.trim()) return;
    setFeedbackSending(true);
    try {
      await notifySubmissionUser(
        submission,
        `Regarding your pack: ${submission.pack_name}`,
        feedbackMessage.trim(),
        { type: 'admin_feedback' }
      );
      setFeedbackModalSubmission(null);
      setFeedbackMessage('');
    } catch (e) {
      console.error('Send feedback error:', e);
      alert('Failed to send message.');
    } finally {
      setFeedbackSending(false);
    }
  };

  const markMessageAsRead = async (messageId: string) => {
    try {
      await pb.collection('messages').update(messageId, { status: 'read' });
      setMessages(messages.map(m => m.id === messageId ? { ...m, status: 'read' } : m));
    } catch (e) {
      console.error('Mesaj okundu işaretlenemedi:', e);
    }
  };

  const deleteMessage = async (messageId: string) => {
    if (!window.confirm('Are you sure you want to delete this message?')) return;
    try {
      await pb.collection('messages').delete(messageId);
      setMessages(messages.filter(m => m.id !== messageId));
    } catch (e) {
      console.error('Mesaj silinemedi:', e);
    }
  };

  const deleteSuggestion = async (suggestionId: string) => {
    if (!window.confirm('Are you sure you want to delete this suggestion?')) return;
    try {
      await pb.collection('suggestions').delete(suggestionId);
      setSuggestions(suggestions.filter(s => s.id !== suggestionId));
    } catch (e) {
      console.error('Öneri silinemedi:', e);
    }
  };

  const clearAllMessages = async () => {
    if (!window.confirm('Are you sure you want to delete ALL messages?')) return;
    try {
      setDeleteProgress({ deleting: true, message: 'Deleting messages...', current: 0, total: messages.length });
      for (let i = 0; i < messages.length; i++) {
        await pb.collection('messages').delete(messages[i].id).catch(() => {});
        setDeleteProgress({ deleting: true, message: 'Deleting messages...', current: i + 1, total: messages.length });
      }
      setMessages([]);
      setDeleteProgress(null);
    } catch (e) {
      setDeleteProgress(null);
      console.error('Mesajlar silinemedi:', e);
    }
  };

  const clearAllSuggestions = async () => {
    if (!window.confirm('Are you sure you want to delete ALL suggestions?')) return;
    try {
      setDeleteProgress({ deleting: true, message: 'Deleting suggestions...', current: 0, total: suggestions.length });
      for (let i = 0; i < suggestions.length; i++) {
        await pb.collection('suggestions').delete(suggestions[i].id).catch(() => {});
        setDeleteProgress({ deleting: true, message: 'Deleting suggestions...', current: i + 1, total: suggestions.length });
      }
      setSuggestions([]);
      setDeleteProgress(null);
    } catch (e) {
      setDeleteProgress(null);
      console.error('Öneriler silinemedi:', e);
    }
  };

  const handleSendNotification = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!notifBody) return alert("Please enter the notification message.");
    if (!window.confirm("Are you sure you want to send this notification to all users?")) return;

    setIsSendingNotif(true);
    try {
      // 1. Save to PocketBase as broadcast (visible to all users in-app)
      try {
        await pb.collection('notifications').create({
          title: notifTitle || 'Sticky',
          body: notifBody,
          message: notifBody,
          user_id: 'broadcast',
          from: 'admin',
          read: false,
          sent: false,
          timestamp: new Date().toISOString(),
          topic: 'broadcast',
          image_url: notifImageUrl || '',
        });
      } catch (pbErr) {
        console.warn('Broadcast PocketBase save skipped:', pbErr);
      }

      // 2. Send FCM push via worker (broadcast to all)
      const resp = await fetch(`${WORKER_URL}/api/notify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: notifTitle || 'Sticky',
          body: notifBody,
          imageUrl: notifImageUrl || undefined,
        }),
      });
      const data = await resp.json();
      if (!resp.ok) throw new Error(data.error || 'Failed to send notification');
      alert("Notification sent! FCM: " + (data.results?.fcm?.success ? "✓" : "✗") + " ntfy: " + (data.results?.ntfy?.success ? "✓" : "✗"));
      setNotifBody('');
      setNotifImageUrl('');
    } catch (e: any) {
      console.error("Bildirim gönderme hatası:", e);
      alert("Error: " + e.message);
    } finally {
      setIsSendingNotif(false);
    }
  };



  const handleGithubLogin = async () => {
    setLoading(true);
    try {
      const authData = await signInWithGitHub();
      const email = authData.record.email || (authData.meta as any)?.rawUser?.email;
      if (!email) throw new Error('Could not get email from GitHub');

      // Admin kontrolü
      let isAdminUser = false;
      try {
        const admins = await pb.collection('admins_list').getFullList({
          filter: pb.filter('email = {:email}', { email })
        });
        if (admins.length > 0) isAdminUser = true;
      } catch (_) {}
      if (!isAdminUser) {

        return;
      }

      setUser({ email });
      fetchPacks();
    } catch (error: any) {
      console.error('GitHub login error:', error);
      alert('GitHub sign-in failed: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleEmailLogin = async (email: string, password: string) => {
    setLoading(true);
    try {
      const { signInWithEmail } = await import('./pocketbase');
      const authData = await signInWithEmail(email, password);
      const userEmail = authData.record?.email;
      if (!userEmail) throw new Error('Could not get email from login');

      let isAdminUser = false;
      try {
        const admins = await pb.collection('admins_list').getFullList({
          filter: pb.filter('email = {:email}', { email: userEmail })
        });
        if (admins.length > 0) isAdminUser = true;
      } catch (_) {}
      if (!isAdminUser) {
        pb.authStore.clear();
        alert('Access denied: ' + userEmail + ' is not an admin.');
        return;
      }

      setUser({ email: userEmail });
      fetchPacks();
    } catch (error: any) {
      console.error('Email login error:', error);
      alert('Login failed: ' + (error.message || 'Invalid credentials'));
    } finally {
      setLoading(false);
    }
  };

  const mapPbRecord = (r: any, isPremium: boolean): StickerPack => ({
    ...r,
    _collection: isPremium ? 'premium_stickers' : 'stickers',
    is_premium: r.is_premium !== undefined ? r.is_premium : isPremium,
    is_animated: r.is_animated ?? r.animated ?? false,
    download_count: Number(r.download_count || 0),
    fake_download_base: Number(r.fake_download_base || 0),
    view_count: Number(r.view_count || 0),
    favorite_count: Number(r.favorite_count || 0),
    like_count: Number(r.like_count || 0),
    comment_count: Number(r.comment_count || 0),
    engagement_score: Number(r.engagement_score || packEngagementScore(r) || 0),
    source: r.source || r.batch_source || '',
    publisher_user_id: r.publisher_user_id || '',
    publisher_photo_url: r.publisher_photo_url || r.photo_url || '',
    sticker_count: Number(r.sticker_count || (r.stickers as any[])?.length || 0),
  } as StickerPack);

  const fetchPacks = async () => {
    setLoading(true);
    try {
      let allPacks: StickerPack[] = [];

      // 1. PocketBase'den oku (birincil kaynak) - her koleksiyon bağımsız fetch edilir
      const [normalRecords, premiumRecords] = await Promise.all([
        pb.collection('stickers').getFullList({ perPage: 500 }).catch(e => {
          console.warn('stickers fetch failed:', e);
          return [];
        }),
        pb.collection('premium_stickers').getFullList({ perPage: 500 }).catch(e => {
          console.warn('premium_stickers fetch failed:', e);
          return [];
        }),
      ]);
      allPacks = [
        ...normalRecords.map(r => mapPbRecord(r, false)),
        ...premiumRecords.map(r => mapPbRecord(r, true)),
      ];
      console.info(`[Packs] ${allPacks.length} packs loaded`);
      setPacks(allPacks.sort((a, b) => (a.name || '').localeCompare(b.name || '')));
    } catch (error: any) {
      console.error("Fetch error:", error);
      alert("Data Fetch Error: " + (error?.message || "Unknown error"));
    } finally {
      setLoading(false);
    }
  };

  // ========== DRAFT MANAGEMENT ==========
  const fetchDrafts = async () => {
    setDraftLoading(true);
    try {
      const records = await pb.collection('draft_stickers').getFullList({ sort: '-created_at' });
      const DRAFT_COLL = 'draft_stickers';
      const drafts: StickerPack[] = (records as any[]).map(r => ({
        id: r.id,
        ...r,
        is_premium: r.is_premium ?? false,
        is_animated: r.is_animated ?? true,
        download_count: 0,
        fake_download_base: Number(r.fake_download_base || 0),
        view_count: 0,
        favorite_count: 0,
      } as StickerPack)).map((draft: any) => {
        const parseField = (raw: any): any[] => {
          if (Array.isArray(raw) && raw.length > 0) return raw;
          if (typeof raw === 'string' && raw.length > 2) {
            try { const parsed = JSON.parse(raw); return Array.isArray(parsed) ? parsed : []; } catch { return []; }
          }
          return [];
        };
        const images = Array.isArray(draft.images) ? draft.images : (typeof draft.images === 'string' && draft.images ? [draft.images] : []);
        const imageNames = new Set(images);

        // Parse JSON stickers — primary source
        const parsedStickers: any[] = parseField(draft.stickers);
        const jsonStickers = parsedStickers.length > 0
          ? parsedStickers.map((sticker: any, index: number) => {
              // Preserve EXISTING url — do NOT reconstruct unless it's missing
              const existingUrl = sticker.url || sticker.image_url || '';
              const existingFile = sticker.image_file || sticker.name || '';
              const validUrl = existingUrl && existingUrl.startsWith('http');
              return {
                name: sticker.name || `sticker_${index + 1}`,
                image_file: existingFile,
                image_url: validUrl ? existingUrl : (existingFile ? getFileUrl(DRAFT_COLL, draft.id, existingFile) : ''),
                url: validUrl ? existingUrl : (existingFile ? getFileUrl(DRAFT_COLL, draft.id, existingFile) : ''),
                emojis: Array.isArray(sticker.emojis) && sticker.emojis.length > 0 ? sticker.emojis : ['⭐'],
              };
            })
          : [];
        // File field stickers — fallback if no JSON stickers
        const fileStickers = jsonStickers.length === 0 && images.length > 0
          ? images.map((filename: string, index: number) => ({
              name: `sticker_${index + 1}`,
              image_file: filename,
              image_url: getFileUrl(DRAFT_COLL, draft.id, filename),
              url: getFileUrl(DRAFT_COLL, draft.id, filename),
              emojis: ['⭐'],
            }))
          : [];

        const stickers = jsonStickers.length > 0 ? jsonStickers : fileStickers;
        const trayFallback = (draft.tray_url && draft.tray_url.startsWith('http'))
          ? draft.tray_url
          : (stickers[0]?.url || '');
        return {
          ...draft,
          status: draft.status === 'processing' && stickers.length > 0 ? 'draft' : (draft.status || 'draft'),
          stickers,
          tray_url: trayFallback,
          sticker_count: Number(draft.sticker_count || stickers.length || 0),
        } as StickerPack;
      });
      setDraftPacks(drafts);
    } catch (error: any) {
      console.error('Draft fetch error:', error);
    } finally {
      setDraftLoading(false);
    }
  };

  const stripDraftPublishMeta = (draft: StickerPack, translations: Record<string, string>, currentName: string) => {
    if (!currentName?.trim()) {
      throw new Error('Pack name is empty. Please edit the draft and set a name before publishing.');
    }
    const packDataWithoutMeta = { ...(draft as any) };
    [
      'id',
      'collectionId',
      'collectionName',
      'expand',
      'created',
      'updated',
      'images',
      'tray_image',
      'status',
      'draft_data',
    ].forEach((key) => delete packDataWithoutMeta[key]);

    // Filter out empty translation values to avoid overwriting with blanks
    const cleanTranslations: Record<string, string> = {};
    for (const [key, val] of Object.entries(translations)) {
      if (val?.trim()) {
        cleanTranslations[key] = val;
      }
    }

    return {
      ...packDataWithoutMeta,
      ...cleanTranslations,
      name: currentName,
      name_en: currentName,
      is_active: true,
    };
  };

  const filenameFromUrl = (url: string, fallback: string) => {
    try {
      const lastSegment = new URL(url).pathname.split('/').filter(Boolean).pop();
      return lastSegment ? decodeURIComponent(lastSegment) : fallback;
    } catch {
      return fallback;
    }
  };

  const normalizeFileName = (name: string, fallback: string) => {
    const clean = (name || fallback).split('/').pop()?.replace(/[^a-zA-Z0-9._-]/g, '_') || fallback;
    return clean.includes('.') ? clean : `${clean}.webp`;
  };

  const fetchAsFile = async (url: string, fileName: string) => {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`File copy failed (${response.status}) for ${fileName}`);
    const blob = await response.blob();
    return new File([blob], fileName, { type: blob.type || 'image/webp' });
  };

  const publishDraftRecord = async (
    draft: StickerPack,
    targetCollection: 'stickers' | 'premium_stickers',
    packData: any,
    onStep?: (step: string, percent: number) => void
  ) => {
    const sourceStickers = Array.isArray(draft.stickers) ? draft.stickers : [];
    if (sourceStickers.length === 0) throw new Error('Draft has no stickers to publish.');

    let targetRecord: any;
    const initialData = {
      ...packData,
      stickers: [],
      sticker_count: 0,
      tray_url: '',
      tray_image_file: '',
      image_data_version: Date.now().toString(),
    };

    // Strip fields that may not exist in the target collection schema
    const safePackData = { ...initialData };
    const draftOnlyFields = ['status', 'draft_data', 'batch_generated', 'batch_source', 'batch_search_term',
      'telegram_part', 'telegram_total_parts', 'telegram_set_name', 'telegram_set_title', 'source',
      'collectionId', 'collectionName', 'expand', 'created', 'updated'];
    draftOnlyFields.forEach(key => delete safePackData[key]);

    try {
      // Attempt 1: create with custom ID (draft.id)
      try {
        targetRecord = await pb.collection(targetCollection).create({ id: draft.id, ...safePackData });
      } catch (pbErr1: any) {
        const msg1 = (pbErr1?.message || '').toLowerCase();
        const status1 = pbErr1?.status || 0;
        // Retry without custom ID if: 400 (invalid id), 404 (collection context), or message mentions 'id'
        if (status1 === 400 || status1 === 404 || msg1.includes('id')) {
          // Attempt 2: create without custom ID
          try {
            targetRecord = await pb.collection(targetCollection).create(safePackData);
          } catch (pbErr2: any) {
            const msg2 = (pbErr2?.message || '').toLowerCase();
            const status2 = pbErr2?.status || 0;
            // If still 404, try with even more stripped data (only core fields)
            if (status2 === 404 && msg2.includes('collection context')) {
              const coreData: any = {
                name: safePackData.name,
                name_en: safePackData.name_en || safePackData.name,
                publisher: safePackData.publisher || 'Sticky',
                category: safePackData.category || 'other',
                is_premium: safePackData.is_premium ?? false,
                is_animated: safePackData.is_animated ?? false,
                sticker_count: 0,
                stickers: [],
                is_active: true,
                image_data_version: Date.now().toString(),
              };
              targetRecord = await pb.collection(targetCollection).create(coreData);
            } else {
              throw pbErr2;
            }
          }
        } else {
          throw pbErr1;
        }
      }

      // Get file token for protected draft files
      const draftFileToken = await pb.files.getToken().catch(() => null);

      // Fetch actual stored filenames from the draft record as fallback for broken URLs
      let draftImageFiles: string[] = [];
      try {
        const draftRec = await pb.collection('draft_stickers').getOne(draft.id);
        const imgs = draftRec?.images;
        draftImageFiles = Array.isArray(imgs) ? imgs : (imgs ? [imgs] : []);
      } catch { /* best effort */ }

      const addToken = (url: string) => draftFileToken ? `${url}${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(draftFileToken)}` : url;

      const copiedStickers: Sticker[] = [];
      for (let index = 0; index < sourceStickers.length; index++) {
        const sticker: any = sourceStickers[index];
        const sourceUrl = sticker.url || sticker.image_url;
        if (!sourceUrl) throw new Error(`Sticker #${index + 1} is missing a source URL.`);

        onStep?.(`Copying sticker ${index + 1}/${sourceStickers.length}...`, 55 + Math.round((index / sourceStickers.length) * 25));
        const requestedName = normalizeFileName(sticker.image_file || sticker.name || `sticker_${index + 1}.webp`, `sticker_${index + 1}.webp`);
        const srcUrl = addToken(sourceUrl);
        let file: File;
        try {
          file = await fetchAsFile(srcUrl, requestedName);
        } catch (fetchErr: any) {
          // Fallback: URL in JSON might be stale — find the actual file in the record's images field
          if (String(fetchErr?.message || '').includes('404') && draftImageFiles.length > 0) {
            const baseName = (sticker.image_file || '').replace(/\.[^.]+$/, '');
            const actualFile = (baseName ? draftImageFiles.find(f => f.startsWith(baseName)) : null) || draftImageFiles[index];
            if (!actualFile) throw fetchErr;
            const fallbackUrl = addToken(getFileUrl('draft_stickers', draft.id, actualFile));
            file = await fetchAsFile(fallbackUrl, requestedName);
          } else {
            throw fetchErr;
          }
        }
        const uploadedUrl = await uploadFile(targetCollection, targetRecord.id, 'images', file, requestedName);
        const uploadedName = filenameFromUrl(uploadedUrl, requestedName);

        copiedStickers.push({
          image_file: uploadedName,
          url: uploadedUrl,
          emojis: Array.isArray(sticker.emojis) && sticker.emojis.length > 0 ? sticker.emojis : ['⭐'],
        });
      }

      let trayUrl = '';
      let trayFile = '';
      const traySourceUrl = draft.tray_url || copiedStickers[0]?.url;
      if (traySourceUrl) {
        onStep?.('Copying tray image...', 84);
        const requestedTrayName = normalizeFileName(draft.tray_image_file || 'tray.png', 'tray.png');
        let trayFileObject: File;
        try {
          trayFileObject = await fetchAsFile(addToken(traySourceUrl), requestedTrayName);
        } catch (trayErr: any) {
          if (String(trayErr?.message || '').includes('404') && copiedStickers[0]) {
            trayFileObject = await fetchAsFile(addToken(copiedStickers[0].url), requestedTrayName);
          } else {
            throw trayErr;
          }
        }
        trayUrl = await uploadFile(targetCollection, targetRecord.id, 'tray_image', trayFileObject, requestedTrayName);
        trayFile = filenameFromUrl(trayUrl, requestedTrayName);
      }

      const finalData = {
        ...packData,
        stickers: copiedStickers,
        sticker_count: copiedStickers.length,
        tray_url: trayUrl,
        tray_image_file: trayFile,
        image_data_version: Date.now().toString(),
        is_active: true,
      };

      onStep?.('Finalizing published pack...', 88);
      return await pb.collection(targetCollection).update(targetRecord.id, finalData);
    } catch (error) {
      if (targetRecord?.id) {
        await pb.collection(targetCollection).delete(targetRecord.id).catch(() => {});
      }
      // Log diagnostic info for collection context errors
      const msg = (error as any)?.message || '';
      if (msg.includes('collection context') || msg.includes('Missing or invalid collection')) {
        console.error('[PUBLISH_RECORD] Collection context error — possible causes:');
        console.error('  1. Target collection "' + targetCollection + '" does not exist in PocketBase');
        console.error('  2. The admin user lacks create permission on "' + targetCollection + '"');
        console.error('  3. The "admins_list" collection is missing or admin email not registered');
        console.error('  4. PocketBase server cache is stale — try restarting PocketBase');
      }
      throw error;
    }
  };

  const publishDraft = async (draft: StickerPack) => {
    if (!window.confirm(`Are you sure you want to publish "${draft.name}"?`)) return;
    // Validate pack has a name before publishing
    if (!draft.name?.trim()) {
      alert('❌ Cannot publish: Pack name is empty. Please edit the draft and set a name first.');
      return;
    }
    setDraftPublishing(draft.id);
    setSinglePublishProgress({ step: 'Preparing...', percent: 5 });
    try {
      const currentName = draft.name;
      const stickerCount = draft.sticker_count || draft.stickers?.length || 0;

      setSinglePublishProgress({ step: `Translating "${currentName}" to 33 languages...`, percent: 15 });
      let translations: Record<string, string> = {};
      if (deepseekService.isConfigured()) {
        try { translations = await deepseekService.translatePackName(currentName); } catch { translations = {}; }
      }

      setSinglePublishProgress({ step: `Publishing ${stickerCount} stickers to database...`, percent: 55 });
      const targetCollection = draft.is_premium ? 'premium_stickers' : 'stickers';
      const packData = stripDraftPublishMeta(draft, translations, currentName);
      await publishDraftRecord(draft, targetCollection, packData, (step, percent) => {
        setSinglePublishProgress({ step, percent });
      });

      setSinglePublishProgress({ step: 'Cleaning up draft...', percent: 92 });
      await pb.collection('draft_stickers').delete(draft.id);
      setDraftPacks(prev => prev.filter(p => p.id !== draft.id));
      if (selectedDraft?.id === draft.id) setSelectedDraft(null);

      setSinglePublishProgress({ step: 'Refreshing pack list...', percent: 90 });
      await fetchPacks();
      logAdminAction('publish', `Published "${currentName}" (${stickerCount} stickers)`, draft.id);
      alert(`✅ "${currentName}" published successfully! (${stickerCount} stickers)`);
    } catch (error: any) {
      console.error('Publish error:', error);
      const msg = error?.message || '';
      let hint = '';
      if (msg.includes('collection context') || msg.includes('Missing or invalid collection')) {
        hint = '\n\n⚠️ PocketBase cannot find the target collection.\n' +
          'Possible fixes:\n' +
          '• Run schema setup scripts (pb_patch_schema.mjs) on the server\n' +
          '• Restart PocketBase server to refresh its cache\n' +
          '• Verify "stickers" and "premium_stickers" collections exist\n' +
          '• Check that your admin email is in the "admins_list" collection';
      } else if (msg.includes('permission') || msg.includes('403') || msg.includes('no permission')) {
        hint = '\n\n⚠️ Permission denied. Make sure:\n' +
          '• Your admin email is in the "admins_list" collection\n' +
          '• Your auth session hasn\'t expired (try refreshing the page)';
      }
      alert(`Publish error: ${msg}${hint}`);
    } finally {
      setDraftPublishing(null);
      setSinglePublishProgress(null);
    }
  };

  const publishAllDrafts = async () => {
    if (draftPacks.length === 0) return;
    if (!window.confirm(`Are you sure you want to publish ${draftPacks.length} draft packs?`)) return;
    let published = 0;
    const total = draftPacks.length;
    setPublishAllProgress({ current: 0, total, currentName: draftPacks[0]?.name });
    for (let idx = 0; idx < draftPacks.length; idx++) {
      const draft = draftPacks[idx];
      setDraftPublishing(draft.id);
      setPublishAllProgress({ current: idx, total, currentName: draft.name });
      try {
        let translations: Record<string, string> = {};
        const currentName = draft.name;
        if (deepseekService.isConfigured()) {
          try { translations = await deepseekService.translatePackName(currentName); } catch { translations = {}; }
        }
        const targetCollection = draft.is_premium ? 'premium_stickers' : 'stickers';
        const packData = stripDraftPublishMeta(draft, translations, currentName);
        await publishDraftRecord(draft, targetCollection, packData);
        await pb.collection('draft_stickers').delete(draft.id).catch(() => {});
        published++;
      } catch (error: any) {
        console.error(`Publish error (${draft.name}):`, error);
      }
    }
    setPublishAllProgress(null);
    setDraftPublishing(null);
    setDraftPacks([]);
    setSelectedDraft(null);
    await fetchPacks();
    alert(`✅ ${published}/${total} packs published!`);
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
        await pb.collection('draft_stickers').delete(draft.id).catch(() => {});
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
    setSingleDeleteProgress({ step: 'Removing from database...' });
    try {
      await pb.collection('draft_stickers').delete(draft.id);
      logAdminAction('delete_draft', `Deleted draft "${draft.name}"`, draft.id);
      setDraftPacks(prev => prev.filter(p => p.id !== draft.id));
      if (selectedDraft?.id === draft.id) setSelectedDraft(null);
    } catch (error: any) {
      console.error('Draft delete error:', error);
      alert(`Delete error: ${error.message}`);
    } finally {
      setDraftDeleting(null);
      setSingleDeleteProgress(null);
    }
  };

  const updateDraftPack = async () => {
    if (!selectedDraft || !draftEditData) return;
    if (!draftEditData.name?.trim()) {
      alert('Pack name is required!');
      return;
    }
    try {
      const trimmedName = draftEditData.name.trim();
      const updatedData: any = {
        name: trimmedName,
        name_en: trimmedName,
        category: draftEditData.category ?? selectedDraft.category,
        is_premium: draftEditData.is_premium ?? (selectedDraft as any).is_premium ?? false,
        is_animated: draftEditData.is_animated ?? (selectedDraft as any).is_animated ?? false,
        is_active: draftEditData.is_active ?? (selectedDraft as any).is_active ?? true,
        is_popular: draftEditData.is_popular ?? (selectedDraft as any).is_popular ?? false,
        publisher: ((draftEditData.publisher || (selectedDraft as any).publisher || 'Sticky') as string).trim() || 'Sticky',
        publisher_email: draftEditData.publisher_email || (selectedDraft as any).publisher_email || '',
        image_data_version: Date.now().toString(),
        stickers: selectedDraft.stickers || [],
        sticker_count: (selectedDraft.stickers || []).length,
      };
      if (draftEditData.privacy_policy_website) updatedData.privacy_policy_website = draftEditData.privacy_policy_website;
      if (draftEditData.license_agreement_website) updatedData.license_agreement_website = draftEditData.license_agreement_website;
      if ((selectedDraft as any).tray_url) updatedData.tray_url = (selectedDraft as any).tray_url;
      if ((selectedDraft as any).tray_image_file) updatedData.tray_image_file = (selectedDraft as any).tray_image_file;
      TARGET_LANGUAGES.forEach(lang => {
        const key = `name_${lang.code}`;
        if (draftEditData[key]) updatedData[key] = draftEditData[key];
      });
      await pb.collection('draft_stickers').update(selectedDraft.id, updatedData);
      const saved = await pb.collection('draft_stickers').getOne(selectedDraft.id);
      const updated = { ...selectedDraft, ...updatedData, name: saved.name, name_en: saved.name_en, sticker_count: saved.sticker_count } as StickerPack;
      setDraftPacks(prev => prev.map(p => p.id === selectedDraft.id ? updated : p));
      setSelectedDraft(updated);
      setShowDraftEditModal(false);
      if (saved.name !== trimmedName) {
        alert(`Warning: PocketBase saved name as "${saved.name}" instead of "${trimmedName}". Check collection write rules.`);
      } else {
        alert('Draft updated successfully.');
      }
    } catch (error: any) {
      console.error('Draft update error:', error);
      const fieldErrors = error?.data
        ? '\n' + Object.entries(error.data).map(([k, v]: any) => `${k}: ${v?.message || JSON.stringify(v)}`).join('\n')
        : '';
      alert(`Update error: ${error.message}${fieldErrors}`);
    }
  };

  const reorderDraftStickers = async (draft: StickerPack, fromIdx: number, toIdx: number) => {
    if (fromIdx === toIdx) return;
    try {
      const stickers = [...draft.stickers];
      const [moved] = stickers.splice(fromIdx, 1);
      stickers.splice(toIdx, 0, moved);
      const image_data_version = Date.now().toString();
      await pb.collection('draft_stickers').update(draft.id, { stickers, image_data_version });
      const updated = { ...draft, stickers, image_data_version } as StickerPack;
      setDraftPacks(prev => prev.map(p => p.id === draft.id ? updated : p));
    } catch (error: any) {
      console.error('Reorder error:', error);
      alert(`Reorder error: ${error.message}`);
    }
  };

  const removeStickerFromDraft = async (draft: StickerPack, stickerIndex: number) => {
    if (!window.confirm('Are you sure you want to remove this sticker from the draft?')) return;
    try {
      const updatedStickers = draft.stickers.filter((_, idx) => idx !== stickerIndex);
      await pb.collection('draft_stickers').update(draft.id, {
        stickers: updatedStickers,
        sticker_count: updatedStickers.length,
        image_data_version: Date.now().toString(),
      });
      const updated = { ...draft, stickers: updatedStickers, sticker_count: updatedStickers.length } as StickerPack;
      setDraftPacks(prev => prev.map(p => p.id === draft.id ? updated : p));
      if (selectedDraft?.id === draft.id) setSelectedDraft(updated);
    } catch (error: any) {
      console.error('Remove sticker error:', error);
      alert(`Remove sticker error: ${error.message}`);
    }
  };

  // Fetch drafts when import review is active
  useEffect(() => {
    if (activeTab === 'imports' && importSubTab === 'drafts') {
      fetchDrafts();
    }
  }, [activeTab, importSubTab]);

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
      const token = pb.authStore.token;
      const resp = await fetch(`${WORKER_URL}/api/users`, {
        headers: token ? { 'Authorization': `Bearer ${token}` } : {}
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const { users } = await resp.json();
      const usersList: UserData[] = (users || []).map((data: any) => ({
        id: data.id,
        email: data.email || '',
        is_premium: data.is_premium || false,
        premium_type: data.premium_type || 'none',
        premium_expiry: data.premium_expiry || data.premium_expires_at || 0,
        favorite_packs: data.favorite_packs || [],
        last_sync: data.last_sync || data.updated || null,
        cancelled_at: data.cancelled_at || null,
        cancelled_reason: data.cancelled_reason || '',
        subscription_source: data.subscription_source || 'none',
        subscription_history: data.subscription_history || [],
        created_at: data.created_at || data.joined_at || data.created || null,
        display_name: data.display_name || data.displayName || data.name || '',
        photo_url: data.photo_url || data.photoURL || data.avatar_url || data.picture || '',
        device_info: data.device_info || null,
        total_stickers_added: data.total_stickers_added || 0,
        custom_packs_count: data.custom_packs_count || 0,
        social: data.social || undefined,
        followers_list: data.followers_list || [],
        following_list: data.following_list || [],
        published_packs: data.published_packs || [],
        share_requests: data.share_requests || [],
        recent_comments: data.recent_comments || [],
      }));
      setUsersData(usersList);
    } catch (error) {
      console.error("Users fetch error:", error);
    } finally {
      setUsersLoading(false);
    }
  };

  const refreshCurrentView = async () => {
    if (activeTab === 'messages') {
      await fetchMessagesAndSuggestions();
    } else if (activeTab === 'users') {
      await fetchUsers();
    } else if (activeTab === 'imports') {
      await fetchDrafts();
    } else if (activeTab === 'submissions') {
      const submissions = await pb.collection('user_submissions').getFullList({ sort: '-created_at' }).catch(() => []);
      setUserSubmissions((submissions as any[]).map(r => {
        const createdValue = r.created_at || r.created;
        const createdMs = createdValue ? new Date(createdValue).getTime() : 0;
        const collId = r.collectionId || 'user_submissions';
        const images = Array.isArray(r.images) ? r.images : (typeof r.images === 'string' && r.images ? [r.images] : []);
        const stickers = images.map((filename: string, index: number) => ({
          name: `sticker_${index + 1}`,
          image_file: filename,
          image_url: getFileUrl(collId, r.id, filename),
          url: getFileUrl(collId, r.id, filename),
          emojis: ['⭐'],
        }));
        return { id: r.id, user_id: r.user_id || '', device_id: r.device_id || '', user_email: r.user_email || '', display_name: r.display_name || '', publisher_name: r.publisher_name, pack_name: r.pack_name || r.name || '', description: r.description, category: r.category || '', stickers, status: r.status || 'pending', sticker_count: r.sticker_count || stickers.length || 0, sticker_pack_id: r.sticker_pack_id, created_at: { seconds: createdMs / 1000 }, note: r.note } as UserSubmission;
      }).sort((a, b) => (b.created_at?.seconds || 0) - (a.created_at?.seconds || 0)));
    } else {
      await fetchPacks();
      if (selectedPack) checkStickerSizes(selectedPack);
    }
  };

  const filteredUsers = useMemo(() => {
    let filtered = usersData;
    if (userSearch) {
      const q = userSearch.toLowerCase();
      filtered = filtered.filter(u =>
        u.email.toLowerCase().includes(q) ||
        (u.display_name || '').toLowerCase().includes(q)
      );
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

      const resp = await fetch(`${WORKER_URL}/api/users/${userId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${pb.authStore.token}` },
        body: JSON.stringify({
          is_premium: isPremium,
          premium_type: type,
          premium_expiry: expiry,
          subscription_source: isPremium ? 'admin' : 'none',
          historyItem,
        }),
      });
      if (!resp.ok) throw new Error((await resp.json()).error || 'Update failed');

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

      const resp = await fetch(`${WORKER_URL}/api/users/${userId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${pb.authStore.token}` },
        body: JSON.stringify({
          is_premium: false,
          premium_type: 'none',
          premium_expiry: 0,
          subscription_source: 'none',
          historyItem,
        }),
      });
      if (!resp.ok) throw new Error((await resp.json()).error || 'Update failed');

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
        publisher_user_id: newPackData.publisher_user_id || '',
        category: newPackData.category,
        is_premium: newPackData.is_premium,
        is_animated: newPackData.is_animated ?? true,
        download_count: 0,
        view_count: 0,
        favorite_count: 0,
        sticker_count: 0,
        image_data_version: "1",
        is_active: newPackData.is_active,
        // Fiyatlandirma kaldirildi - Tüm paketler ücretsiz/reklamli
        price_try: "", price_usd: "", price_eur: "",
        stickers: [],
        tray_url: "",
        created_at: new Date().toISOString()
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

      // PocketBase'e kaydet (birincil)
      let createdId = packId;
      try {
        const created = await pb.collection(collectionName).create({ id: packId, ...packData });
        createdId = created.id;
      } catch (pbErr) {
        // Try without specifying id if it conflicts
        const created = await pb.collection(collectionName).create(packData);
        createdId = created.id;
      }

      const createdPack = { id: createdId, ...packData } as StickerPack;
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
        publisher_user_id: '',
        privacy_policy_website: '',
        license_agreement_website: '',
        category: 'humor',
        is_premium: false,
        is_active: true,
        is_animated: true,

      });
      if (createdId) {
        logAdminAction('create_pack', `Created "${newPackData.name}" in ${collectionName}`, createdId);
      }
      alert(newPackData.is_animated ? "New animated pack created. You can now add video/gif files." : "New static pack created. You can now add WebP/PNG files.");
    } catch (e) {
      alert("Error: " + e);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleAutoTranslate = async (isEdit: boolean, isDraft: boolean = false) => {
    const textToTranslate = isDraft ? draftEditData.name : (isEdit ? editFormData.name : newPackData.name);
    if (!textToTranslate) {
      alert("Please enter a main name (English) first.");
      return;
    }

    setIsTranslating(true);
    try {
      const translations = await translateTextAllLanguages(textToTranslate);
      if (isDraft) {
        setDraftEditData((prev: any) => ({ ...prev, ...translations }));
      } else if (isEdit) {
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

  const packCollection = (pack: StickerPack): string =>
    (pack as any)._collection || (pack.is_premium ? 'premium_stickers' : 'stickers');

  const handleUpdatePack = async () => {
    if (!selectedPack || !editFormData) return;
    setIsProcessing(true);

    try {
      const sanitizePackUpdate = (data: Partial<StickerPack>) => {
        const allowed = new Set([
          'name', 'publisher', 'publisher_email', 'publisher_user_id', 'publisher_photo_url',
          'category', 'is_animated', 'is_premium',
          'is_active', 'is_popular', 'product_id', 'price_try', 'price_usd', 'price_eur',
          'image_data_version'
        ]);
        return Object.fromEntries(
          Object.entries(data).filter(([key, value]) =>
            value !== undefined &&
            (allowed.has(key) || key.startsWith('name_'))
          )
        );
      };

      const updatedData: any = sanitizePackUpdate(editFormData);
      updatedData.publisher = (updatedData.publisher || 'Sticky').trim() || 'Sticky';
      updatedData.image_data_version = (Number(selectedPack.image_data_version || 0) + 1).toString();
      if (updatedData.is_premium) {
        if (!updatedData.price_try) updatedData.price_try = '69,99 TL';
        if (!updatedData.price_usd) updatedData.price_usd = '$4.99';
        if (!updatedData.price_eur) updatedData.price_eur = '€4.49';
      } else {
        updatedData.price_try = '';
        updatedData.price_usd = '';
        updatedData.price_eur = '';
      }

      // Update in-place; Android reads is_premium field directly from the record.
      // Moving records between collections is avoided because file references are tied to collectionId/recordId.
      const oldCollection = (selectedPack as any)._collection || (selectedPack.is_premium ? 'premium_stickers' : 'stickers');
      await pb.collection(oldCollection).update(selectedPack.id, updatedData);

      const updated = { ...selectedPack, ...updatedData } as StickerPack;
      setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
      setSelectedPack(updated);
      setShowEditPackModal(false);
      alert("Pack details updated successfully.");

    } catch (e: any) {
      console.error('[UPDATE] Hata:', e);
      const details = e?.data ? '\n' + Object.entries(e.data).map(([k, v]: any) => `${k}: ${v?.message || JSON.stringify(v)}`).join('\n') : '';
      alert("Error: " + e.message + details);
    } finally {
      setIsProcessing(false);
    }
  };

  const uploadStickersBatch = async (files: File[], removeBackground: boolean = false) => {
    if (!selectedPack) return;

    const uploadCount = files.length;
    setIsProcessing(true);
    setUploadProgress({ current: 0, total: uploadCount, message: 'Processing started...' });

    try {
      const collectionName = packCollection(selectedPack);
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
            // Video/GIF için removeBackground parametresini geçir
            processedBlob = await stickerProcessor.processAnimated(file, (p) => {
              setUploadProgress(prev => prev ? { ...prev, message: `${file.name}: ${p.message}` } : null);
            }, removeBackground);
          } else {
            processedBlob = await stickerProcessor.processStatic(file, (p) => {
              setUploadProgress(prev => prev ? { ...prev, message: `${file.name}: ${p.message}` } : null);
            }, removeBackground);
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
        const url = await uploadFile(collectionName, selectedPack.id, 'images', processedBlob, fileName);

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
          // Random seçim
          const randomIdx = Math.floor(Math.random() * processedBlobs.length);
          const chosenBlob = processedBlobs[randomIdx];

          // Blob -> File dönüşümü (stickerProcessor.processTray için)
          const tempFile = new File([chosenBlob], "auto_tray.webp", { type: 'image/webp' });

          const trayProcessedBlob = await stickerProcessor.processTray(tempFile, (p) => {
            setUploadProgress(prev => prev ? { ...prev, message: `Kapak: ${p.message}` } : null);
          });

          const trayFileName = `tray_${Date.now()}.png`;
          newTrayUrl = await uploadFile(collectionName, selectedPack.id, 'tray_image', trayProcessedBlob, trayFileName);
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

      await pb.collection(collectionName).update(selectedPack.id, updatedData);

      const updated = { ...selectedPack, ...updatedData };
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
      const collectionName = packCollection(selectedPack);
      const importedStickers = await importStickers({
        source: 'giphy',
        contentType: importContentType,
        query: query,
        count: importCount,
        packId: selectedPack.id,
        collection: collectionName,
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

      const newVersion = Date.now().toString();
      const updatedData = {
        stickers: [...(selectedPack.stickers || []), ...importedStickers],
        sticker_count: Math.max(0, (selectedPack.sticker_count || 0) + importedStickers.length),
        image_data_version: newVersion
      };

      await pb.collection(collectionName).update(selectedPack.id, updatedData);

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

    setPendingFiles(files);
    setShowVideoBgModal(true);
    e.target.value = '';
  };



  const deletePack = async (pack: StickerPack) => {
    const packName = pack.name || 'Unnamed';
    if (!window.confirm(`⚠️ PERMANENTLY DELETE "${packName}"?\n\nThis action CANNOT be undone!\nPack ID: ${pack.id}\nStickers: ${pack.sticker_count || pack.stickers?.length || '?'}`)) return;

    try {
      setDeleteProgress({ deleting: true, message: 'Deleting from database...', current: 0, total: 1 });
      const collectionName = packCollection(pack);
      const cascade = await fetch(`${WORKER_URL}/api/social/pack/delete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pack_id: pack.id, collection: collectionName, admin: true })
      }).catch(() => null);
      if (!cascade?.ok) await pb.collection(collectionName).delete(pack.id);

      logAdminAction('delete_pack', `Deleted "${packName}" from ${collectionName}`, pack.id);
      setPacks(packs.filter(p => p.id !== pack.id));
      if (selectedPack?.id === pack.id) setSelectedPack(null);
      setDeleteProgress(null);
    } catch (error) {
      setDeleteProgress(null);
      alert('Delete error: ' + error);
    }
  };

  const deleteSticker = async (pack: StickerPack, sticker: Sticker) => {
    if (!window.confirm('Are you sure you want to delete this sticker?')) return;

    try {
      const collectionName = packCollection(pack);
      const newVersion = Date.now().toString();
      const newStickerCount = Math.max(0, (pack.stickers?.length || pack.sticker_count) - 1);
      const newStickers = pack.stickers.filter(s => s.image_file !== sticker.image_file);

      await pb.collection(collectionName).update(pack.id, {
        stickers: newStickers,
        sticker_count: newStickerCount,
        image_data_version: newVersion,
      });

      const updatedPack = {
        ...pack,
        stickers: newStickers,
        sticker_count: newStickerCount,
        image_data_version: newVersion
      };

      setPacks(packs.map(p => p.id === pack.id ? updatedPack : p));
      setSelectedPack(updatedPack);

      setStickerSizes(prev => {
        const newSizes = { ...prev };
        delete newSizes[sticker.image_file];
        return newSizes;
      });
    } catch (error) {
      alert('Sticker delete error: ' + error);
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

      // PocketBase'e yükle
      const collectionName = packCollection(pack);
      const trayFileName = `tray_${Date.now()}.png`;
      const trayUrl = await uploadFile(collectionName, pack.id, 'tray_image', trayProcessedBlob, trayFileName);

      const newVersion = Date.now().toString();
      const updateData = {
        tray_url: trayUrl,
        tray_image_file: trayFileName,
        image_data_version: newVersion
      };

      await pb.collection(collectionName).update(pack.id, updateData);

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
      const collectionName = packCollection(selectedPack);

      const stickersToDelete = (selectedPack.stickers || []).filter(s => selectedStickerIds.includes(s.url));
      const remainingStickers = (selectedPack.stickers || []).filter(s => !selectedStickerIds.includes(s.url));

      // Not: Files remain in PocketBase 'images' field (no single-file delete API for multi-file fields).
      // Sticker metadata removed from the JSON array below.

      // PocketBase güncelle
      const newVersion = Date.now().toString();
      const newStickerCount = remainingStickers.length;
      await pb.collection(collectionName).update(selectedPack.id, {
        stickers: remainingStickers,
        sticker_count: newStickerCount,
        image_data_version: newVersion,
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
      const collectionName = packCollection(pack);
      const newVersion = Date.now().toString();

      await pb.collection(collectionName).update(pack.id, {
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
      const collectionName = packCollection(pack);
      await pb.collection(collectionName).update(pack.id, {
        download_count: 0,
        view_count: 0
      });
      const updated = { ...pack, download_count: 0, view_count: 0 };
      setPacks(packs.map(p => p.id === pack.id ? updated : p));
      setSelectedPack(updated);
      alert("Statistics reset.");
    } catch (e) { alert("Error: " + e); }
  };

  const resetAllStats = async () => {
    if (!window.confirm(`Reset all stats (download, view, favorite counts) to 0 for ALL ${packs.length} packs? This cannot be undone.`)) return;
    setIsProcessing(true);
    let done = 0;
    let errors = 0;
    try {
      for (const p of packs) {
        try {
          const col = packCollection(p);
          await pb.collection(col).update(p.id, {
            download_count: 0,
            view_count: 0,
            favorite_count: 0,
            fake_download_base: 0,
          });
          done++;
        } catch {
          errors++;
        }
      }
      setPacks(prev => prev.map(p => ({ ...p, download_count: 0, view_count: 0, favorite_count: 0, fake_download_base: 0 })));
      alert(`Reset complete — ${done} packs updated.${errors > 0 ? ` (${errors} errors)` : ''}`);
    } catch (e) {
      alert('Error: ' + e);
    } finally {
      setIsProcessing(false);
    }
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
        const records = await pb.collection(collectionName).getFullList({ perPage: 500 });
        for (const record of records) {
          const updates: any = {};

          if (forceUpdate || !record.fake_download_base || record.fake_download_base === 0) {
            updates.fake_download_base = Math.floor(Math.random() * (range + 1)) + fakeBaseMin;
          }

          if (collectionName === 'premium_stickers') {
            updates.price_try = '4,99 TL';
            updates.price_usd = '$0.99';
            updates.price_eur = '€0.99';
          }

          if (Object.keys(updates).length > 0) {
            await pb.collection(collectionName).update(record.id, updates);
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

  const statsPacks = useMemo<Partial<StickerPack>[]>(() => {
    const byId = new Map<string, Partial<StickerPack>>();
    const addPack = (pack: Partial<StickerPack>, source = pack.source) => {
      const id = String(pack.id || '').trim();
      if (!id) return;
      const current = byId.get(id);
      const merged: Partial<StickerPack> = {
        ...current,
        ...pack,
        id,
        source,
        name: pack.name || current?.name || 'User Pack',
        publisher: pack.publisher || current?.publisher || 'Sticky',
        category: pack.category || current?.category || 'user',
        is_active: pack.is_active ?? current?.is_active ?? true,
        is_premium: pack.is_premium ?? current?.is_premium ?? false,
        sticker_count: Number(pack.sticker_count ?? current?.sticker_count ?? pack.stickers?.length ?? 0),
        download_count: Math.max(Number(current?.download_count || 0), Number(pack.download_count || 0)),
        view_count: Math.max(Number(current?.view_count || 0), Number(pack.view_count || 0)),
        favorite_count: Math.max(Number(current?.favorite_count || 0), Number(pack.favorite_count || 0)),
        like_count: Math.max(Number(current?.like_count || 0), Number(pack.like_count || 0)),
        comment_count: Math.max(Number(current?.comment_count || 0), Number(pack.comment_count || 0)),
      };
      byId.set(id, merged);
    };

    packs.forEach(pack => addPack(pack));
    usersData.forEach(user => {
      user.published_packs?.forEach(pack => addPack({
        id: pack.id,
        name: pack.name,
        publisher: pack.publisher || user.display_name || 'Sticky',
        publisher_user_id: user.id,
        publisher_photo_url: user.photo_url,
        tray_url: pack.tray_url,
        source: 'user_submission',
        is_active: true,
        is_premium: false,
        sticker_count: pack.sticker_count || pack.stickers?.length || 0,
        download_count: pack.download_count,
        favorite_count: pack.favorite_count,
        like_count: pack.like_count,
        comment_count: pack.comment_count,
        engagement_score: pack.engagement_score,
      }, 'user_submission'));
      user.share_requests?.filter(submission => submission.status === 'approved' && submission.sticker_pack_id).forEach(submission => addPack({
        id: submission.sticker_pack_id || submission.id,
        name: submission.pack_name,
        publisher: submission.publisher_name || submission.display_name || user.display_name || 'Sticky',
        publisher_user_id: user.id,
        publisher_photo_url: user.photo_url,
        category: submission.category || 'user',
        source: 'user_submission',
        is_active: true,
        is_premium: false,
        sticker_count: submission.sticker_count || submission.stickers?.length || 0,
        created_at: submission.approved_at || submission.processed_at || submission.created_at,
      }, 'user_submission'));
    });

    return Array.from(byId.values());
  }, [packs, usersData]);

  if (loading && !user) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <RefreshCcw className="text-primary animate-spin" size={40} />
      </div>
    );
  }

  if (!user) {
    if (loading) {
      return (
        <div className="min-h-screen bg-[#0a0a0f] flex items-center justify-center">
          <div className="flex flex-col items-center gap-4">
            <div className="w-10 h-10 border-2 border-[#7c3aed] border-t-transparent rounded-full animate-spin" />
            <p className="text-[#94a3b8] text-sm">Loading...</p>
          </div>
        </div>
      );
    }
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="glass w-full max-w-md p-8 rounded-3xl space-y-6 animate-in fade-in zoom-in duration-300">
          <div className="text-center space-y-2">
            <div className="bg-primary w-16 h-16 rounded-2xl flex items-center justify-center mx-auto shadow-lg shadow-primary/20">
              <Lock className="text-white" size={32} />
            </div>
            <h1 className="text-2xl font-bold">Sticky Admin Login</h1>
            <p className="text-textSec text-sm">Sign in to access the admin panel</p>
          </div>

          <div className="space-y-3">
            <input
              type="email"
              placeholder="Email"
              value={loginEmail}
              onChange={e => setLoginEmail(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleEmailLogin(loginEmail, loginPassword)}
              className="w-full bg-surface border border-border rounded-xl px-4 py-3 text-sm outline-none focus:border-primary"
            />
            <input
              type="password"
              placeholder="Password"
              value={loginPassword}
              onChange={e => setLoginPassword(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleEmailLogin(loginEmail, loginPassword)}
              className="w-full bg-surface border border-border rounded-xl px-4 py-3 text-sm outline-none focus:border-primary"
            />
            <button
              onClick={() => handleEmailLogin(loginEmail, loginPassword)}
              disabled={loading || !loginEmail || !loginPassword}
              className="w-full bg-primary hover:bg-primary/90 py-3 rounded-xl font-bold text-white transition-all disabled:opacity-50"
            >
              {loading ? 'Signing in...' : 'Sign In'}
            </button>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex-1 h-px bg-border" />
            <span className="text-textSec text-xs">or</span>
            <div className="flex-1 h-px bg-border" />
          </div>

          <button
            onClick={handleGithubLogin}
            disabled={loading}
            className="w-full bg-[#24292e] hover:bg-[#1a1f24] py-3 rounded-xl font-bold text-white transition-all flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
            </svg>
            Sign in with GitHub
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
    if (statusFilter === 'user_submission') {
      if (p.source === 'user_submission') return true;
      const approvedPackIds = new Set(userSubmissions.filter(s => s.status === 'approved' && s.sticker_pack_id).map(s => s.sticker_pack_id));
      return approvedPackIds.has(p.id);
    }
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
            onClick={refreshCurrentView}
            className="p-2.5 hover:bg-white/10 rounded-xl transition-all active:scale-90 group relative"
            title="Refresh System"
          >
            <RefreshCcw size={18} className={cn("text-textSec group-hover:text-primary transition-colors", (loading || checkingSizes) && 'animate-spin text-primary')} />
          </button>

          <div className="w-px h-6 bg-white/10 mx-1 hidden md:block" />

          <button
            onClick={() => {
              if (window.confirm("Do you want to sign out safely?")) handleSignOut();
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
                { id: 'users', label: 'Users', icon: Users },
                { id: 'imports', label: 'Telegram Import', icon: Send },
                { id: 'submissions', label: 'Submissions', icon: Inbox, count: userSubmissions.filter(s => s.status === 'pending' || s.status === 'flagged').length }
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
                  if (window.confirm("Are you sure you want to sign out?")) handleSignOut();
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
              { id: 'submissions', icon: Inbox, label: 'Submissions', count: userSubmissions.filter(s => s.status === 'pending' || s.status === 'flagged').length },
              { id: 'imports', icon: Send, label: 'Telegram Import' }
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
                              { id: 'new', label: 'Recently Added', icon: Clock },
                              { id: 'user_submission', label: 'Approved User Packs', icon: UserPlus }
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
                            ...selectedPack,
                            publisher: selectedPack.publisher || 'Sticky',
                            publisher_email: '',
                            publisher_user_id: ''
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
                      onClick={resetAllStats}
                      className="px-3 py-2 bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 rounded-xl text-red-400 text-[10px] font-black uppercase tracking-widest transition-all"
                      title="Reset all download/view/favorite counts to 0"
                    >
                      Reset All Stats
                    </button>

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

              {/* Popular Empty State */}
              {statsFilter === 'popular' && statsPacks.filter(p => p.is_popular === true).length === 0 && (
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
                  const sPacks = statsPacks.filter(p => {
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
                  const totalLikes = sPacks.reduce((acc, p) => acc + (p.like_count || 0), 0);
                  const totalComments = sPacks.reduce((acc, p) => acc + (p.comment_count || 0), 0);
                  const totalEngagement = sPacks.reduce((acc, p) => acc + packEngagementScore(p), 0);
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
                          <span className="text-[8px] font-black text-yellow-400/60 bg-yellow-500/5 px-1.5 py-0.5 rounded uppercase tracking-widest">Score</span>
                        </div>
                        <p className="text-2xl font-black text-white">{Math.round(totalEngagement).toLocaleString()}</p>
                        <p className="text-[9px] font-bold text-textSec/50 mt-0.5">CVR %{avgCVR.toFixed(1)}</p>
                        <div className="w-full h-1 bg-white/5 rounded-full overflow-hidden mt-2">
                          <div className="h-full bg-yellow-400 rounded-full" style={{ width: `${Math.min(100, totalEngagement / Math.max(1, sPacks.length * 25))}%` }} />
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
                        <p className="text-[9px] font-bold text-textSec/50 mt-0.5">{totalLikes.toLocaleString()} likes</p>
                      </div>

                      <div className="glass rounded-xl p-4 border border-cyan-500/10 group hover:border-cyan-500/30 transition-all">
                        <div className="flex items-center gap-3 mb-3">
                          <div className="w-10 h-10 bg-cyan-500/10 rounded-xl flex items-center justify-center">
                            <Users size={18} className="text-cyan-400" />
                          </div>
                          <span className="text-[8px] font-black text-cyan-400/60 bg-cyan-500/5 px-1.5 py-0.5 rounded uppercase tracking-widest">Comments</span>
                        </div>
                        <p className="text-2xl font-black text-white">{totalComments.toLocaleString()}</p>
                        <p className="text-[9px] font-bold text-textSec/50 mt-0.5">{usersData.length.toLocaleString()} users</p>
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
                        data={statsPacks
                          .filter(p => {
                            if (statsFilter === 'all') return true;
                            if (statsFilter === 'popular') return p.is_popular === true;
                            if (statsFilter === 'active') return p.is_active !== false;
                            if (statsFilter === 'passive') return p.is_active === false;
                            if (statsFilter === 'premium') return p.is_premium === true;
                            if (statsFilter === 'normal') return p.is_premium === false;
                            return true;
                          })
                          .sort((a, b) => packEngagementScore(b) - packEngagementScore(a))
                          .slice(0, 10)
                          .map(p => {
                            const pName = p.name || 'Unnamed Pack';
                            return {
                              name: pName.length > 10 ? pName.substring(0, 8) + '..' : pName,
                              downloads: p.download_count || 0,
                              views: p.view_count || 0,
                              score: packEngagementScore(p),
                              likes: p.like_count || 0,
                              comments: p.comment_count || 0,
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
                    <p className="text-[9px] font-bold text-textSec mt-0.5">Top 5 by engagement score</p>
                  </div>
                  <div className="flex-1 p-4 space-y-2.5">
                    {statsPacks
                      .filter(p => {
                        if (statsFilter === 'all') return true;
                        if (statsFilter === 'popular') return p.is_popular === true;
                        if (statsFilter === 'active') return p.is_active !== false;
                        if (statsFilter === 'passive') return p.is_active === false;
                        if (statsFilter === 'premium') return p.is_premium === true;
                        if (statsFilter === 'normal') return p.is_premium === false;
                        return true;
                      })
                      .sort((a, b) => packEngagementScore(b) - packEngagementScore(a))
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
                            <div className="text-[9px] font-bold text-textSec/50">{p.category} · {(p.like_count || 0).toLocaleString()} likes · {(p.comment_count || 0).toLocaleString()} comments</div>
                          </div>
                          <div className="text-right shrink-0">
                            <div className="text-xs font-black text-primary">{Math.round(packEngagementScore(p)).toLocaleString()}</div>
                            <div className="text-[8px] font-bold text-textSec/50">score</div>
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
                  const activePacks = statsPacks.filter(p => p.is_active !== false);
                  // Category performance analysis
                  const catMap = new Map<string, { downloads: number; views: number; favorites: number; likes: number; comments: number; score: number; packs: number; totalStickers: number }>();
                  activePacks.forEach(p => {
                    const cat = p.category || 'uncategorized';
                    const prev = catMap.get(cat) || { downloads: 0, views: 0, favorites: 0, likes: 0, comments: 0, score: 0, packs: 0, totalStickers: 0 };
                    catMap.set(cat, {
                      downloads: prev.downloads + (p.download_count || 0),
                      views: prev.views + (p.view_count || 0),
                      favorites: prev.favorites + (p.favorite_count || 0),
                      likes: prev.likes + (p.like_count || 0),
                      comments: prev.comments + (p.comment_count || 0),
                      score: prev.score + packEngagementScore(p),
                      packs: prev.packs + 1,
                      totalStickers: prev.totalStickers + (p.sticker_count || 0)
                    });
                  });
                  const catArr = Array.from(catMap.entries())
                    .map(([name, d]) => ({
                      name,
                      ...d,
                      cvr: d.views > 0 ? (d.downloads / d.views) * 100 : 0,
                      engagementPerPack: d.packs > 0 ? d.score / d.packs : 0
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
                              <span className="text-[9px] font-bold text-pink-400">{cat.likes} likes</span>
                              <span className="text-[9px] font-bold text-cyan-400">{cat.comments} comments</span>
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
                      const activePacks = statsPacks.filter(p => p.is_active !== false);
                      const animatedPacks = statsPacks.filter(p => p.is_animated);
                      const premiumPacks = statsPacks.filter(p => p.is_premium);
                      const popularPacks = statsPacks.filter(p => p.is_popular === true);
                      const zeroDLPacks = activePacks.filter(p => (p.download_count || 0) === 0);
                      const avgStickersPerPack = activePacks.length > 0 ? Math.round(activePacks.reduce((a, p) => a + (p.sticker_count || 0), 0) / activePacks.length) : 0;
                      const telegramPacks = statsPacks.filter(p => p.batch_source === 'telegram');
                      const giphyPacks = statsPacks.filter(p => p.batch_source === 'giphy' || p.batch_source === 'klipy');
                      const communityPacks = statsPacks.filter(p => p.source === 'user_submission' || p.publisher_user_id);

                      return [
                        { label: 'Total Packs', value: statsPacks.length, color: 'text-white' },
                        { label: 'Active / Inactive', value: `${activePacks.length} / ${statsPacks.length - activePacks.length}`, color: 'text-green-400' },
                        { label: 'User Shared Packs', value: communityPacks.length, color: 'text-cyan-400' },
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
                      const activePacks = statsPacks.filter(p => p.is_active !== false);
                      const animatedRatio = statsPacks.length > 0 ? statsPacks.filter(p => p.is_animated).length / statsPacks.length : 0;
                      const zeroDL = activePacks.filter(p => (p.download_count || 0) === 0).length;
                      const categories = new Set(statsPacks.map(p => p.category).filter(Boolean));
                      const popularCount = statsPacks.filter(p => p.is_popular).length;
                      const highCVR = activePacks.filter(p => (p.view_count || 0) > 10 && ((p.download_count || 0) / (p.view_count || 1)) > 0.15).length;
                      const avgFavPerPack = activePacks.length > 0 ? activePacks.reduce((a, p) => a + (p.favorite_count || 0), 0) / activePacks.length : 0;

                      // Content volume
                      if (statsPacks.length < 50) suggestions.push({ icon: '📦', text: `${statsPacks.length} packs. Target 100+ for organic discovery.`, severity: 'warn' });
                      else suggestions.push({ icon: '✅', text: `${statsPacks.length} packs — solid content library!`, severity: 'good' });

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
                        <th className="hidden lg:table-cell px-5 py-3.5 text-[9px] font-black uppercase tracking-widest text-textSec/60 border-b border-white/5 text-center">Likes</th>
                        <th className="hidden xl:table-cell px-5 py-3.5 text-[9px] font-black uppercase tracking-widest text-textSec/60 border-b border-white/5 text-center">Comments</th>
                        <th className="hidden md:table-cell px-5 py-3.5 text-[9px] font-black uppercase tracking-widest text-textSec/60 border-b border-white/5 text-right w-56">CVR</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/[0.03]">
                      {statsPacks
                        .filter(p => {
                          if (statsFilter === 'all') return true;
                          if (statsFilter === 'popular') return p.is_popular === true;
                          if (statsFilter === 'active') return p.is_active !== false;
                          if (statsFilter === 'passive') return p.is_active === false;
                          if (statsFilter === 'premium') return p.is_premium === true;
                          if (statsFilter === 'normal') return p.is_premium === false;
                          return true;
                        })
                        .sort((a, b) => packEngagementScore(b) - packEngagementScore(a))
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
                            <td className="hidden lg:table-cell px-5 py-3.5 text-center">
                              <span className="text-sm font-black text-pink-400">{(p.like_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="hidden xl:table-cell px-5 py-3.5 text-center">
                              <span className="text-sm font-black text-cyan-400">{(p.comment_count || 0).toLocaleString()}</span>
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
                      <p className="text-xs text-textSec mt-0.5">PocketBase user management and subscription control</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
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
              </div>

              {/* Users/Publishers Sub-Tabs */}
              <div className="flex gap-2">
                {[
                  { id: 'users' as const, label: 'Users', icon: Users },
                  { id: 'publishers' as const, label: 'Publisher Users', icon: UserPlus }
                ].map(tab => (
                  <button
                    key={tab.id}
                    onClick={() => setUsersSubTab(tab.id)}
                    className={cn(
                      "flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest transition-all border",
                      usersSubTab === tab.id
                        ? "bg-primary/20 text-primary border-primary/30"
                        : "bg-white/5 text-textSec border-transparent hover:bg-white/10"
                    )}
                  >
                    <tab.icon size={14} />
                    {tab.label}
                  </button>
                ))}
              </div>

              {usersSubTab === 'users' ? (<>
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
                    <div className="fixed left-1/2 top-1/2 z-50 w-[min(1120px,calc(100vw-48px))] max-h-[calc(100vh-48px)] -translate-x-1/2 -translate-y-1/2 glass rounded-3xl border border-white/10 overflow-hidden shrink-0 shadow-2xl shadow-black/40">
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

                      <div className="p-5 space-y-4 max-h-[calc(100vh-150px)] overflow-y-auto custom-scrollbar">
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

                        {(selectedUser.bio || selectedUser.social) && (
                          <div className="bg-white/[0.02] rounded-xl p-3 border border-white/5 space-y-3">
                            {selectedUser.bio && (
                              <div>
                                <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest block mb-1">Bio</span>
                                <p className="text-xs font-medium text-white/80 leading-relaxed">{selectedUser.bio}</p>
                              </div>
                            )}
                            {selectedUser.social && (
                              <div className="grid grid-cols-5 gap-2 text-center">
                                <div>
                                  <p className="text-sm font-black text-primary">{selectedUser.social.followers || 0}</p>
                                  <span className="text-[8px] text-textSec uppercase">Followers</span>
                                </div>
                                <div>
                                  <p className="text-sm font-black text-primary">{selectedUser.social.following || 0}</p>
                                  <span className="text-[8px] text-textSec uppercase">Following</span>
                                </div>
                                <div>
                                  <p className="text-sm font-black text-primary">{selectedUser.social.published_packs || 0}</p>
                                  <span className="text-[8px] text-textSec uppercase">Packs</span>
                                </div>
                                <div>
                                  <p className="text-sm font-black text-primary">{selectedUser.social.likes || 0}</p>
                                  <span className="text-[8px] text-textSec uppercase">Likes</span>
                                </div>
                                <div>
                                  <p className="text-sm font-black text-primary">{selectedUser.social.comments || 0}</p>
                                  <span className="text-[8px] text-textSec uppercase">Comments</span>
                                </div>
                              </div>
                            )}
                            {((selectedUser.followers_list && selectedUser.followers_list.length > 0) || (selectedUser.following_list && selectedUser.following_list.length > 0)) && (
                              <div className="grid md:grid-cols-2 gap-3 pt-2">
                                <div className="rounded-xl bg-white/[0.03] border border-white/5 p-3">
                                  <span className="text-[9px] font-black text-textSec uppercase tracking-widest">Followers</span>
                                  <div className="mt-2 space-y-1.5 max-h-28 overflow-y-auto custom-scrollbar">
                                    {(selectedUser.followers_list || []).map((f, i) => (
                                      <div key={`${f.id || f.email || i}`} className="text-[11px] text-white/80 bg-white/[0.03] rounded-lg px-2 py-1">
                                        {f.name || f.email || 'Unknown'}
                                      </div>
                                    ))}
                                  </div>
                                </div>
                                <div className="rounded-xl bg-white/[0.03] border border-white/5 p-3">
                                  <span className="text-[9px] font-black text-textSec uppercase tracking-widest">Following</span>
                                  <div className="mt-2 space-y-1.5 max-h-28 overflow-y-auto custom-scrollbar">
                                    {(selectedUser.following_list || []).map((f, i) => (
                                      <div key={`${f.id || f.email || i}`} className="text-[11px] text-white/80 bg-white/[0.03] rounded-lg px-2 py-1">
                                        {f.name || f.email || 'Unknown'}
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              </div>
                            )}
                          </div>
                        )}

                        {selectedUser.published_packs && selectedUser.published_packs.length > 0 && (
                          <div className="bg-white/[0.02] rounded-xl p-3 border border-white/5">
                            <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest flex items-center gap-1 mb-2">
                              <Package size={10} /> Published Packs ({selectedUser.published_packs.length})
                            </span>
                            <div className="space-y-3">
                              {selectedUser.published_packs.map(pack => (
                                <div key={pack.id} className="p-3 rounded-xl bg-white/[0.03] border border-white/5 space-y-3">
                                  <div className="flex items-start gap-3">
                                    {pack.tray_url ? (
                                      <img src={pack.tray_url} alt={pack.name} className="w-12 h-12 rounded-lg object-cover bg-black/20 border border-white/10 shrink-0" />
                                    ) : (
                                      <div className="w-12 h-12 rounded-lg bg-white/5 border border-white/10 shrink-0" />
                                    )}
                                    <div className="min-w-0 flex-1">
                                      <p className="text-xs font-black text-white truncate">{pack.name}</p>
                                      <p className="text-[9px] text-textSec/60">{pack.sticker_count || pack.stickers?.length || 0} stickers</p>
                                      <div className="grid grid-cols-4 gap-1.5 mt-2 text-center">
                                        <span className="rounded-lg bg-white/[0.04] px-2 py-1 text-[9px] font-bold text-textSec">DL {pack.download_count || 0}</span>
                                        <span className="rounded-lg bg-white/[0.04] px-2 py-1 text-[9px] font-bold text-textSec">Fav {pack.favorite_count || 0}</span>
                                        <span className="rounded-lg bg-white/[0.04] px-2 py-1 text-[9px] font-bold text-primary">Likes {pack.like_count || 0}</span>
                                        <span className="rounded-lg bg-white/[0.04] px-2 py-1 text-[9px] font-bold text-accent">Com {pack.comment_count || 0}</span>
                                      </div>
                                    </div>
                                  </div>
                                  {pack.stickers && pack.stickers.length > 0 && (
                                    <div className="grid grid-cols-8 sm:grid-cols-10 md:grid-cols-12 gap-1.5">
                                      {pack.stickers.slice(0, 24).map((sticker, index) => (
                                        <img
                                          key={`${pack.id}-sticker-${index}`}
                                          src={sticker.url || sticker.image_url}
                                          alt={sticker.name || `Sticker ${index + 1}`}
                                          className="w-full aspect-square rounded-lg object-cover bg-black/20 border border-white/5"
                                        />
                                      ))}
                                    </div>
                                  )}
                                  {pack.comments && pack.comments.length > 0 && (
                                    <div className="rounded-xl bg-black/10 border border-white/5 p-2 space-y-1.5 max-h-32 overflow-y-auto custom-scrollbar">
                                      {pack.comments.map(comment => (
                                        <div key={comment.id} className="rounded-lg bg-white/[0.03] px-2 py-1.5">
                                          <p className="text-[10px] font-bold text-white/70">{comment.display_name || comment.user_email || 'User'} <span className="text-primary">{comment.like_count || 0} likes</span></p>
                                          <p className="text-[11px] text-white/85 leading-relaxed">{comment.body}</p>
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        {selectedUser.recent_comments && selectedUser.recent_comments.length > 0 && (
                          <div className="bg-white/[0.02] rounded-xl p-3 border border-white/5">
                            <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest flex items-center gap-1 mb-2">
                              <MessageSquare size={10} /> Recent Comments
                            </span>
                            <div className="space-y-2">
                              {selectedUser.recent_comments.map(comment => (
                                <div key={comment.id} className="p-2 rounded-lg bg-white/[0.03]">
                                  <p className="text-xs font-medium text-white/80 leading-relaxed">{comment.body}</p>
                                  <p className="text-[9px] text-textSec/40 mt-1">Pack: {comment.pack_id}</p>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

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

                        {/* Submitted Packs */}
                        {(() => {
                          const fallbackSubs = userSubmissions.filter(s =>
                            (selectedUser.id && (s.user_id === selectedUser.id || s.device_id === selectedUser.id)) ||
                            (selectedUser.email && s.user_email === selectedUser.email)
                          );
                          const subs = selectedUser.share_requests && selectedUser.share_requests.length > 0 ? selectedUser.share_requests : fallbackSubs;
                          if (subs.length === 0) return null;
                          return (
                            <div className="bg-white/[0.02] rounded-xl p-3 border border-white/5">
                              <span className="text-[9px] font-bold text-textSec/60 uppercase tracking-widest flex items-center gap-1 mb-2">
                                <UserPlus size={10} /> Share Requests ({subs.length})
                              </span>
                              <div className="space-y-3">
                                {subs.map(s => (
                                  <div key={s.id} className="p-3 rounded-xl bg-white/[0.03] border border-white/5 space-y-2">
                                    <div className="flex items-center justify-between gap-2">
                                      <div className="min-w-0">
                                        <p className="text-xs font-bold text-white truncate">{s.pack_name}</p>
                                        <p className="text-[9px] text-textSec/50">{s.sticker_count || s.stickers?.length || 0} stickers {s.category ? `/ ${s.category}` : ''}</p>
                                      </div>
                                      <span className={cn(
                                        "text-[9px] font-black px-2 py-0.5 rounded-md shrink-0",
                                        s.status === 'approved' ? "bg-primary/10 text-primary border border-primary/10" :
                                          s.status === 'rejected' ? "bg-danger/10 text-danger border border-danger/10" :
                                            s.status === 'pending' ? "bg-yellow-500/10 text-yellow-400 border border-yellow-500/10" :
                                              "bg-white/5 text-textSec border border-white/5"
                                      )}>{s.status.toUpperCase()}</span>
                                    </div>
                                    {s.description && <p className="text-[10px] text-textSec/80 leading-relaxed">{s.description}</p>}
                                    {s.stickers && s.stickers.length > 0 && (
                                      <div className="grid grid-cols-8 sm:grid-cols-10 md:grid-cols-12 gap-1.5">
                                        {s.stickers.slice(0, 24).map((sticker: any, index: number) => (
                                          <img
                                            key={`${s.id}-request-sticker-${index}`}
                                            src={sticker.url || sticker.image_url}
                                            alt={sticker.name || `Sticker ${index + 1}`}
                                            className="w-full aspect-square rounded-lg object-cover bg-black/20 border border-white/5"
                                          />
                                        ))}
                                      </div>
                                    )}
                                  </div>
                                ))}
                              </div>
                            </div>
                          );
                        })()}

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
              </>) : (
              /* Publisher Users Management */
              <div className="space-y-6">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-black text-white">Publisher Users</h2>
                    <p className="text-xs text-textSec mt-0.5">Manage virtual publisher accounts for packs</p>
                  </div>
                  <button
                    onClick={() => {
                      setEditingPublisher(null);
                      setPublisherFormData({ display_name: '', avatar_url: '', bio: '', category: 'community', is_active: true });
                      setShowPublisherModal(true);
                    }}
                    className="flex items-center gap-2 px-4 py-2.5 bg-primary hover:bg-primary/80 text-white rounded-xl text-xs font-black uppercase tracking-widest transition-all shadow-lg shadow-primary/20"
                  >
                    <Plus size={14} />
                    Add Publisher
                  </button>
                </div>

                {/* Publisher Stats */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                  <div className="glass rounded-xl p-4 border border-white/5">
                    <p className="text-xl font-black text-white">{publisherUsers.length}</p>
                    <p className="text-[10px] text-textSec uppercase tracking-widest">Total</p>
                  </div>
                  <div className="glass rounded-xl p-4 border border-white/5">
                    <p className="text-xl font-black text-green-400">{publisherUsers.filter(p => p.is_active).length}</p>
                    <p className="text-[10px] text-textSec uppercase tracking-widest">Active</p>
                  </div>
                  <div className="glass rounded-xl p-4 border border-white/5">
                    <p className="text-xl font-black text-purple-400">{publisherUsers.reduce((sum, p) => sum + (p.packs_published || 0), 0)}</p>
                    <p className="text-[10px] text-textSec uppercase tracking-widest">Total Packs</p>
                  </div>
                  <div className="glass rounded-xl p-4 border border-white/5">
                    <p className="text-xl font-black text-blue-400">{publisherUsers.reduce((sum, p) => sum + (p.total_downloads || 0), 0).toLocaleString()}</p>
                    <p className="text-[10px] text-textSec uppercase tracking-widest">Total Downloads</p>
                  </div>
                </div>

                {/* Publisher List */}
                <div className="space-y-3">
                  {publisherUsers.length === 0 ? (
                    <div className="glass rounded-2xl p-12 border border-white/5 text-center">
                      <UserPlus size={40} className="text-textSec mx-auto mb-3 opacity-40" />
                      <p className="text-textSec text-sm">No publisher users yet. Create one to get started.</p>
                    </div>
                  ) : publisherUsers.map(pub => (
                    <div key={pub.id} className="glass rounded-xl p-4 border border-white/5 hover:border-white/10 transition-all">
                      <div className="flex items-center gap-4">
                        <div className="w-12 h-12 rounded-xl overflow-hidden bg-white/5 flex items-center justify-center shrink-0">
                          {pub.avatar_url ? (
                            <img src={pub.avatar_url} alt={pub.display_name} className="w-full h-full object-cover" />
                          ) : (
                            <UserIcon size={20} className="text-textSec" />
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <h3 className="text-sm font-black text-white truncate">{pub.display_name}</h3>
                            <span className={cn(
                              "text-[9px] font-black uppercase tracking-widest px-2 py-0.5 rounded-full",
                              pub.category === 'official' ? "bg-yellow-500/20 text-yellow-400" :
                              pub.category === 'artist' ? "bg-purple-500/20 text-purple-400" :
                              "bg-blue-500/20 text-blue-400"
                            )}>
                              {pub.category}
                            </span>
                            {!pub.is_active && (
                              <span className="text-[9px] font-black uppercase tracking-widest px-2 py-0.5 rounded-full bg-red-500/20 text-red-400">Inactive</span>
                            )}
                          </div>
                          <p className="text-xs text-textSec truncate mt-0.5">{pub.bio || 'No bio'}</p>
                          <div className="flex items-center gap-4 mt-1">
                            <span className="text-[10px] text-textSec"><Package size={10} className="inline mr-1" />{pub.packs_published || 0} packs</span>
                            <span className="text-[10px] text-textSec"><TrendingUp size={10} className="inline mr-1" />{(pub.total_downloads || 0).toLocaleString()} downloads</span>
                          </div>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <button
                            onClick={() => {
                              setEditingPublisher(pub);
                              setPublisherFormData({
                                display_name: pub.display_name,
                                avatar_url: pub.avatar_url || '',
                                bio: pub.bio || '',
                                category: pub.category || 'community',
                                is_active: pub.is_active
                              });
                              setShowPublisherModal(true);
                            }}
                            className="p-2 bg-white/5 hover:bg-white/10 rounded-lg transition-all"
                            title="Edit"
                          >
                            <Edit3 size={14} className="text-textSec" />
                          </button>
                          <button
                            onClick={() => handleDeletePublisher(pub.id)}
                            className="p-2 bg-red-500/10 hover:bg-red-500/20 rounded-lg transition-all"
                            title="Delete"
                          >
                            <Trash2 size={14} className="text-red-400" />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Publisher Create/Edit Modal */}
                {showPublisherModal && (
                  <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-card rounded-2xl border border-white/10 p-6 w-full max-w-md space-y-4 shadow-2xl">
                      <div className="flex items-center justify-between">
                        <h3 className="text-lg font-black text-white">{editingPublisher ? 'Edit Publisher' : 'New Publisher'}</h3>
                        <button onClick={() => { setShowPublisherModal(false); setEditingPublisher(null); }} className="p-2 bg-white/5 rounded-lg hover:bg-white/10 transition-all">
                          <X size={16} className="text-textSec" />
                        </button>
                      </div>
                      <div className="space-y-3">
                        <div>
                          <label className="text-xs font-bold text-textSec uppercase mb-1 block">Display Name</label>
                          <input
                            className="w-full bg-hover rounded-xl px-4 py-2.5 text-sm outline-none border-none text-white"
                            value={publisherFormData.display_name}
                            onChange={(e) => setPublisherFormData({ ...publisherFormData, display_name: e.target.value })}
                            placeholder="Publisher name"
                          />
                        </div>
                        <div>
                          <label className="text-xs font-bold text-textSec uppercase mb-1 block">Avatar URL</label>
                          <input
                            className="w-full bg-hover rounded-xl px-4 py-2.5 text-sm outline-none border-none text-white"
                            value={publisherFormData.avatar_url}
                            onChange={(e) => setPublisherFormData({ ...publisherFormData, avatar_url: e.target.value })}
                            placeholder="https://..."
                          />
                        </div>
                        <div>
                          <label className="text-xs font-bold text-textSec uppercase mb-1 block">Bio</label>
                          <textarea
                            className="w-full bg-hover rounded-xl px-4 py-2.5 text-sm outline-none border-none text-white resize-none"
                            rows={3}
                            value={publisherFormData.bio}
                            onChange={(e) => setPublisherFormData({ ...publisherFormData, bio: e.target.value })}
                            placeholder="Short description..."
                          />
                        </div>
                        <div>
                          <label className="text-xs font-bold text-textSec uppercase mb-1 block">Category</label>
                          <select
                            className="w-full bg-hover rounded-xl px-4 py-2.5 text-sm outline-none border-none text-white cursor-pointer"
                            value={publisherFormData.category}
                            onChange={(e) => setPublisherFormData({ ...publisherFormData, category: e.target.value })}
                          >
                            <option value="official">Official</option>
                            <option value="artist">Artist</option>
                            <option value="community">Community</option>
                          </select>
                        </div>
                        <label className="flex items-center gap-3 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={publisherFormData.is_active}
                            onChange={(e) => setPublisherFormData({ ...publisherFormData, is_active: e.target.checked })}
                            className="w-4 h-4 accent-primary"
                          />
                          <span className="text-sm text-white font-bold">Active</span>
                        </label>
                      </div>
                      <div className="flex gap-3 pt-2">
                        <button
                          onClick={() => { setShowPublisherModal(false); setEditingPublisher(null); }}
                          className="flex-1 py-2.5 bg-white/5 hover:bg-white/10 text-textSec rounded-xl text-xs font-black uppercase tracking-widest transition-all"
                        >
                          Cancel
                        </button>
                        <button
                          onClick={handleSavePublisher}
                          disabled={!publisherFormData.display_name}
                          className="flex-1 py-2.5 bg-primary hover:bg-primary/80 disabled:opacity-50 text-white rounded-xl text-xs font-black uppercase tracking-widest transition-all shadow-lg shadow-primary/20"
                        >
                          <Save size={14} className="inline mr-1" />
                          {editingPublisher ? 'Update' : 'Create'}
                        </button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
              )}
            </div>
          </div>
        ) : activeTab === 'submissions' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-12 custom-scrollbar bg-background">
            <div className="max-w-7xl mx-auto space-y-6 md:space-y-8 animate-in fade-in duration-500">
              {/* Submissions Header */}
              <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                  <div className="flex items-center gap-4">
                    <div className="w-14 h-14 bg-gradient-to-br from-green-500/20 to-emerald-500/20 rounded-2xl flex items-center justify-center border border-green-500/10 shadow-lg shadow-green-500/5">
                      <Inbox size={26} className="text-green-400" />
                    </div>
                    <div>
                      <h1 className="text-2xl font-black text-white tracking-tight">User Submissions</h1>
                      <p className="text-xs text-textSec mt-0.5">Review and manage user-submitted sticker packs</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-textSec">
                    <span className="px-2 py-1 bg-yellow-500/10 text-yellow-400 rounded-lg font-bold">{userSubmissions.filter(s => s.status === 'pending').length} Pending</span>
                    <span className="px-2 py-1 bg-red-500/10 text-red-400 rounded-lg font-bold">{userSubmissions.filter(s => s.status === 'flagged').length} Flagged</span>
                  </div>
                </div>
              </div>

              {/* Filter Tabs */}
              <div className="flex gap-2 flex-wrap">
                {(['approved', 'pending', 'flagged', 'rejected'] as const).map(f => (
                  <button
                    key={f}
                    onClick={() => setSubmissionFilter(f)}
                    className={cn(
                      "px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest transition-all border",
                      submissionFilter === f
                        ? "bg-primary/20 text-primary border-primary/30"
                        : "bg-white/5 text-textSec border-transparent hover:bg-white/10"
                    )}
                  >
                    {f === 'pending' ? `Pending (${userSubmissions.filter(s => s.status === 'pending').length})` :
                     f === 'flagged' ? `Flagged (${userSubmissions.filter(s => s.status === 'flagged').length})` :
                     f === 'approved' ? `Approved (${userSubmissions.filter(s => s.status === 'approved').length})` :
                     `Rejected (${userSubmissions.filter(s => s.status === 'rejected').length})`}
                  </button>
                ))}
              </div>

              {/* Submissions List */}
              <div className="space-y-3">
                {userSubmissions.filter(s => s.status === submissionFilter).length === 0 ? (
                  <div className="glass rounded-2xl p-12 border border-white/5 text-center">
                    <Inbox size={40} className="text-textSec mx-auto mb-3 opacity-40" />
                    <p className="text-textSec text-sm">No submissions found.</p>
                  </div>
                ) : userSubmissions.filter(s => s.status === submissionFilter).map(sub => (
                  <div key={sub.id} className="glass rounded-xl p-4 border border-white/5 hover:border-white/10 transition-all">
                    <div className="flex items-start gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h3 className="text-sm font-black text-white">{sub.pack_name}</h3>
                          <span className={cn(
                            "text-[9px] font-black uppercase tracking-widest px-2 py-0.5 rounded-full",
                            sub.status === 'pending' ? "bg-yellow-500/20 text-yellow-400" :
                            sub.status === 'flagged' ? "bg-red-500/20 text-red-400" :
                            sub.status === 'approved' ? "bg-green-500/20 text-green-400" :
                            sub.status === 'rejected' ? "bg-gray-500/20 text-gray-400" :
                            sub.status === 'processing' ? "bg-blue-500/20 text-blue-400" :
                            "bg-orange-500/20 text-orange-400"
                          )}>
                            {sub.status}
                          </span>
                          {sub.category && (
                            <span className="text-[9px] font-bold text-textSec bg-white/5 px-2 py-0.5 rounded-full">{sub.category}</span>
                          )}
                        </div>
                        <div className="flex items-center gap-4 mt-1.5 text-[10px] text-textSec">
                          <span><UserIcon size={10} className="inline mr-1" />{sub.display_name || sub.user_email}</span>
                          <span><ImageIcon size={10} className="inline mr-1" />{sub.stickers?.length || 0} stickers</span>
                          <span><Clock size={10} className="inline mr-1" />{sub.created_at?.seconds ? new Date(sub.created_at.seconds * 1000).toLocaleDateString() : 'Unknown'}</span>
                        </div>
                        {sub.status === 'flagged' && sub.flag_reasons && sub.flag_reasons.length > 0 && (
                          <div className="mt-2 flex items-start gap-2 p-2 bg-red-500/10 rounded-lg">
                            <Flag size={12} className="text-red-400 mt-0.5 shrink-0" />
                            <div className="text-[10px] text-red-300">
                              {sub.flag_reasons.some(r => r.includes('vision_api_error')) ? (
                                <span className="block text-orange-300">⚠ Automatic content scan unavailable — please review images manually before approving or rejecting.</span>
                              ) : (
                                sub.flag_reasons.map((reason, i) => (
                                  <span key={i} className="block">{reason}</span>
                                ))
                              )}
                            </div>
                          </div>
                        )}
                        {sub.status === 'rejected' && sub.rejection_reason && (
                          <div className="mt-2 flex items-start gap-2 p-2 bg-gray-500/10 rounded-lg">
                            <span className="text-[10px] text-gray-300"><span className="font-bold text-gray-400">Rejection reason:</span> {sub.rejection_reason}</span>
                          </div>
                        )}
                        {sub.note && (
                          <div className="mt-2 flex items-start gap-2 p-2 bg-blue-500/10 rounded-lg">
                            <span className="text-[10px] text-blue-300">{sub.note}</span>
                          </div>
                        )}
                        {/* Sticker Preview — click to enlarge */}
                        {sub.stickers && sub.stickers.length > 0 && (
                          <div className="mt-3 grid grid-cols-6 md:grid-cols-10 gap-2">
                            {sub.stickers.slice(0, 20).map((s: any, i) => (
                              <button
                                key={i}
                                type="button"
                                onClick={() => setPreviewSticker({ url: s.image_url || s.url, title: s.name || s.image_file })}
                                className="aspect-square rounded-lg overflow-hidden bg-white/5 border border-white/5 hover:border-primary/40 hover:scale-105 transition-all cursor-zoom-in"
                              >
                                <img src={s.image_url} alt={s.name} className="w-full h-full object-contain" />
                              </button>
                            ))}
                            {sub.stickers.length > 20 && (
                              <div className="aspect-square rounded-lg bg-white/5 border border-white/5 flex items-center justify-center text-[10px] text-textSec font-bold">
                                +{sub.stickers.length - 20}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                      <div className="flex flex-col items-end gap-2 shrink-0">
                        <button
                          onClick={() => handleEditSubmission(sub)}
                          className="flex items-center gap-1.5 px-3 py-1.5 bg-violet-500/10 hover:bg-violet-500/20 text-violet-300 rounded-lg text-[11px] font-bold transition-all border border-violet-500/20"
                        >
                          <Edit3 size={12} /> Edit
                        </button>
                        {(sub.status === 'pending' || sub.status === 'flagged') && (
                          <>
                            <button
                              onClick={() => handleApproveSubmission(sub)}
                              className="flex items-center gap-1.5 px-3 py-1.5 bg-green-500/15 hover:bg-green-500/25 text-green-400 rounded-lg text-[11px] font-bold transition-all border border-green-500/20"
                            >
                              <Check size={12} /> Approve
                            </button>
                            <button
                              onClick={() => handleRejectSubmission(sub)}
                              className="flex items-center gap-1.5 px-3 py-1.5 bg-red-500/15 hover:bg-red-500/25 text-red-400 rounded-lg text-[11px] font-bold transition-all border border-red-500/20"
                            >
                              <X size={12} /> Reject
                            </button>
                          </>
                        )}
                        <button
                          onClick={() => handleDeleteSubmission(sub)}
                          className="flex items-center gap-1.5 px-3 py-1.5 bg-white/5 hover:bg-red-500/15 text-textSec hover:text-red-400 rounded-lg text-[11px] font-bold transition-all border border-white/5"
                        >
                          <Trash2 size={12} /> Delete
                        </button>
                        <button
                          onClick={() => handleSendFeedback(sub)}
                          className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-500/10 hover:bg-blue-500/20 text-blue-400 rounded-lg text-[11px] font-bold transition-all border border-blue-500/20"
                        >
                          <MessageSquare size={12} /> Message
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {submissionEditData && selectedSubmission && (
                <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                  <div className="bg-card rounded-2xl border border-white/10 w-full max-w-4xl max-h-[90vh] overflow-y-auto p-6 shadow-2xl space-y-5">
                    <div className="flex items-center justify-between gap-4">
                      <div>
                        <h3 className="text-lg font-black text-white">Edit Submission</h3>
                        <p className="text-xs text-textSec mt-0.5">Review name, category and remove unsuitable stickers before approval.</p>
                      </div>
                      <button onClick={() => { setSelectedSubmission(null); setSubmissionEditData(null); }} className="p-2 bg-white/5 rounded-lg hover:bg-white/10 transition-all">
                        <X size={16} className="text-textSec" />
                      </button>
                    </div>

                    <div className="grid md:grid-cols-2 gap-4">
                      <div className="space-y-2">
                        <label className="text-[10px] font-black text-textSec uppercase tracking-widest">Pack Name</label>
                        <input
                          className="w-full bg-hover rounded-xl px-4 py-3 text-sm outline-none border border-white/5 text-white"
                          value={submissionEditData.pack_name || ''}
                          onChange={(e) => setSubmissionEditData({ ...submissionEditData, pack_name: e.target.value })}
                        />
                      </div>
                      <div className="space-y-2">
                        <label className="text-[10px] font-black text-textSec uppercase tracking-widest">Category</label>
                        <select
                          className="w-full bg-hover rounded-xl px-4 py-3 text-sm outline-none border border-white/5 text-white"
                          value={submissionEditData.category || 'other'}
                          onChange={(e) => setSubmissionEditData({ ...submissionEditData, category: e.target.value })}
                        >
                          {CATEGORIES.map(cat => <option key={cat.id} value={cat.id}>{cat.name}</option>)}
                        </select>
                      </div>
                    </div>

                    <div className="space-y-2">
                      <label className="text-[10px] font-black text-textSec uppercase tracking-widest">Description / Note</label>
                      <textarea
                        className="w-full bg-hover rounded-xl px-4 py-3 text-sm outline-none border border-white/5 text-white resize-none"
                        rows={3}
                        value={submissionEditData.description || ''}
                        onChange={(e) => setSubmissionEditData({ ...submissionEditData, description: e.target.value })}
                      />
                    </div>

                    <div className="space-y-3">
                      <div className="flex items-center justify-between">
                        <label className="text-[10px] font-black text-textSec uppercase tracking-widest">Stickers ({submissionEditData.stickers?.length || 0})</label>
                        {(submissionEditData.stickers?.length || 0) < 9 && <span className="text-[10px] font-bold text-red-400">Minimum 9 required for approval</span>}
                      </div>
                      <div className="grid grid-cols-3 sm:grid-cols-5 md:grid-cols-8 gap-3">
                        {(submissionEditData.stickers || []).map((sticker: any, index: number) => (
                          <div key={`${sticker.image_url || sticker.url || sticker.name}-${index}`} className="relative aspect-square bg-white/5 rounded-xl border border-white/5 overflow-hidden group">
                            <img
                              src={sticker.image_url || sticker.url}
                              alt={sticker.name || `Sticker ${index + 1}`}
                              className="w-full h-full object-contain p-1 cursor-zoom-in"
                              onClick={() => setPreviewSticker({ url: sticker.image_url || sticker.url, title: sticker.name || sticker.image_file })}
                            />
                            <button
                              onClick={(e) => { e.stopPropagation(); handleRemoveSubmissionSticker(index); }}
                              className="absolute top-1 right-1 w-7 h-7 rounded-lg bg-red-500/90 text-white opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity flex items-center justify-center"
                              title="Remove sticker"
                            >
                              <Trash2 size={13} />
                            </button>
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="flex flex-col sm:flex-row gap-3 pt-2">
                      <button
                        onClick={handleSaveSubmissionEdit}
                        className="flex-1 h-11 bg-primary hover:bg-primary/80 text-white rounded-xl font-black text-xs uppercase tracking-widest flex items-center justify-center gap-2 transition-all"
                      >
                        <Save size={14} /> Save Changes
                      </button>
                      <button
                        onClick={() => { setSelectedSubmission(null); setSubmissionEditData(null); }}
                        className="h-11 px-5 bg-white/5 hover:bg-white/10 text-textSec rounded-xl font-black text-xs uppercase tracking-widest transition-all"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        ) : activeTab === 'imports' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-8 custom-scrollbar bg-background">
            <div className="max-w-7xl mx-auto animate-in fade-in duration-500">
              {/* Header */}
              <div className="flex items-center justify-between mb-8">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 bg-gradient-to-br from-yellow-500 to-orange-600 rounded-2xl flex items-center justify-center shadow-lg shadow-orange-500/20">
                    <Send className="text-white" size={22} />
                  </div>
                  <div>
                    <h2 className="text-2xl font-black text-white tracking-tight">Telegram Import</h2>
                    <p className="text-textSec text-xs mt-0.5">Import Telegram sticker sets into drafts for admin review</p>
                  </div>
                </div>
                <div className="hidden md:flex items-center gap-2 text-[10px] text-sky-400 font-black uppercase tracking-widest">
                  <span className="w-1.5 h-1.5 bg-sky-400 rounded-full animate-pulse" />
                  Draft Review Active
                </div>
              </div>

              {/* Sub-Tab Navigation */}
              <div className="flex items-center gap-2 mb-6">
                <button
                  onClick={() => setImportSubTab('telegram')}
                  className={cn(
                    "px-5 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all",
                    importSubTab === 'telegram'
                      ? "bg-gradient-to-r from-sky-500 to-blue-500 text-white shadow-lg shadow-sky-500/20"
                      : "bg-white/5 text-textSec hover:bg-white/10 hover:text-white"
                  )}
                >
                  <Send size={14} className="inline mr-1.5 -mt-0.5" />
                  Telegram
                </button>
                <button
                  onClick={() => setImportSubTab('drafts')}
                  className={cn(
                    "px-5 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center gap-2",
                    importSubTab === 'drafts'
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

              {importSubTab === 'telegram' ? (
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
                          const cleanToken = telegramBotToken.trim().replace(/^bot/i, '').replace(/[\u200B-\u200D\uFEFF\s]/g, '');
                          if (!cleanToken) return;
                          setTelegramBotToken(cleanToken);
                          const result = await validateBotToken(cleanToken);
                          if (result.valid) {
                            setTelegramTokenValid(true);
                            setTelegramBotName(result.botName || '');
                            localStorage.setItem('telegram_bot_token', cleanToken);
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
                      setTelegramLogs([]);
                      try {
                        await importTelegramPacks(telegramBotToken.trim(), packs, {
                          useAiNaming: !telegramKeepOriginalName,
                          useAiTranslation: true,
                          stickerLimit: telegramStickerLimit,
                          maxStickers: telegramMaxStickers,
                          splitPacks: telegramSplitPacks,
                          keepOriginalName: telegramKeepOriginalName,
                          abortSignal: ac.signal,
                          onProgress: (p) => {
                            setTelegramProgress(p);
                            if (p.currentStep) addTelegramLog(p.currentStep);
                          }
                        });
                        await fetchDrafts();
                        setImportSubTab('drafts');
                        addTelegramLog('✅ Draft list refreshed. Imported packs are ready for review.');
                      } catch (e: any) {
                        addTelegramLog(`❌ Fatal error: ${e.message}`);
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
                              {telegramProgress.status === 'done'
                                ? `✅ Import Complete — ${telegramProgress.completedPacks?.length || 0} packs`
                                : `Importing Pack ${telegramProgress.currentPack}/${telegramProgress.totalPacks}`}
                            </h4>
                            {telegramProgress.status !== 'done' && telegramProgress.packName && (
                              <p className="text-xs text-sky-400 font-bold mt-0.5">{telegramProgress.packName}</p>
                            )}
                            {telegramProgress.status === 'done' && (
                              <p className="text-xs text-green-400 font-bold mt-0.5">{telegramProgress.currentStep}</p>
                            )}
                          </div>
                        </div>
                        {telegramProgress.status === 'done' ? (
                          <span className="text-lg font-black text-green-400">100%</span>
                        ) : telegramProgress.stickerProgress ? (
                          <span className="text-lg font-black text-white">
                            {Math.round((telegramProgress.stickerProgress.current / telegramProgress.stickerProgress.total) * 100)}%
                          </span>
                        ) : null}
                      </div>

                      {/* Progress Bar */}
                      {telegramProgress.status === 'done' ? (
                        <div className="space-y-2">
                          <div className="w-full bg-white/5 rounded-full h-3 overflow-hidden">
                            <div className="h-full bg-gradient-to-r from-green-500 to-emerald-500 rounded-full w-full transition-all duration-500" />
                          </div>
                        </div>
                      ) : telegramProgress.stickerProgress ? (
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

                  {/* Telegram Import Log Panel */}
                  {telegramLogs.length > 0 && (
                    <div className="glass rounded-2xl border border-sky-500/10 overflow-hidden">
                      <div className="flex items-center justify-between px-5 py-3 border-b border-white/5 bg-white/[0.02]">
                        <div className="flex items-center gap-2">
                          <div className="w-2 h-2 rounded-full bg-sky-500 animate-pulse" />
                          <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Live Log</span>
                          <span className="text-[9px] text-textSec bg-white/5 px-2 py-0.5 rounded-md">{telegramLogs.length} entries</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <button onClick={() => {
                            const text = telegramLogs.map(l => `${l.time} ${l.message}`).join('\n');
                            navigator.clipboard.writeText(text);
                          }} className="text-[9px] text-textSec hover:text-white transition-colors font-bold uppercase tracking-wider">Copy</button>
                          <button onClick={() => setTelegramLogs([])} className="text-[9px] text-textSec hover:text-white transition-colors font-bold uppercase tracking-wider">Clear</button>
                        </div>
                      </div>
                      <div ref={telegramLogRef} className="max-h-[250px] overflow-y-auto p-3 space-y-0.5 font-mono text-[11px] bg-black/40">
                        {telegramLogs.map((log, idx) => (
                          <div key={idx} className="flex gap-2 py-0.5 px-2 rounded hover:bg-white/5">
                            <span className="text-white/20 shrink-0 select-none">{log.time}</span>
                            <span className={
                              log.type === 'success' ? 'text-green-400' :
                              log.type === 'error' ? 'text-red-400' :
                              log.type === 'warn' ? 'text-yellow-400' :
                              'text-white/60'
                            }>{log.message}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ) : importSubTab === 'drafts' ? (
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
                            disabled={!!deleteAllProgress || !!publishAllProgress}
                            className="px-5 py-2.5 bg-gradient-to-r from-red-500 to-red-600 text-white rounded-xl text-[10px] font-black uppercase tracking-widest shadow-lg shadow-red-500/20 hover:translate-y-[-1px] transition-all flex items-center gap-2 disabled:opacity-50"
                          >
                            <Trash2 size={13} /> Delete All
                          </button>
                          <button
                            onClick={publishAllDrafts}
                            disabled={!!publishAllProgress || !!deleteAllProgress}
                            className="px-5 py-2.5 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl text-[10px] font-black uppercase tracking-widest shadow-lg shadow-violet-500/20 hover:translate-y-[-1px] transition-all flex items-center gap-2 disabled:opacity-50"
                          >
                            {publishAllProgress ? <RefreshCcw size={13} className="animate-spin" /> : <Check size={13} />} {publishAllProgress ? 'Publishing...' : 'Publish All'}
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

                  {/* Publish All Progress */}
                  {publishAllProgress && (
                    <div className="glass rounded-2xl p-5 border border-violet-500/20 space-y-3 bg-gradient-to-br from-violet-500/5 to-purple-600/5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <Check size={16} className="text-violet-400 animate-pulse" />
                          <span className="text-sm font-black text-white">
                            Publishing {publishAllProgress.current + 1}/{publishAllProgress.total}
                          </span>
                        </div>
                        <span className="text-lg font-black text-violet-400">
                          {Math.round(((publishAllProgress.current + 1) / publishAllProgress.total) * 100)}%
                        </span>
                      </div>
                      <div className="w-full bg-white/5 rounded-full h-3 overflow-hidden">
                        <div
                          className="h-full bg-gradient-to-r from-violet-500 to-purple-600 rounded-full transition-all duration-300"
                          style={{ width: `${Math.max(2, ((publishAllProgress.current + 1) / publishAllProgress.total) * 100)}%` }}
                        />
                      </div>
                      {publishAllProgress.currentName && (
                        <p className="text-[10px] text-violet-300/70 truncate">
                          📦 {publishAllProgress.currentName}
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
                      <p className="text-sm text-textSec max-w-lg mx-auto leading-relaxed">Imported Telegram packs appear here as drafts. Review them here before publishing.</p>
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
                                </div>
                              </div>
                              <div className="flex items-center gap-2 shrink-0">
                                <button
                                  onClick={() => {
                                    setSelectedDraft(draft);
                                    const initData: any = {
                                      name: draft.name,
                                      name_en: (draft as any).name_en || draft.name,
                                      category: draft.category,
                                      is_premium: draft.is_premium,
                                      is_animated: draft.is_animated,
                                      is_active: draft.is_active,
                                      is_popular: (draft as any).is_popular ?? false,
                                      publisher: (draft as any).publisher || 'Sticky',
                                      publisher_email: (draft as any).publisher_email || '',
                                      privacy_policy_website: (draft as any).privacy_policy_website || '',
                                      license_agreement_website: (draft as any).license_agreement_website || '',
                                    };
                                    // Copy existing translations
                                    TARGET_LANGUAGES.forEach(lang => {
                                      const key = `name_${lang.code}`;
                                      if ((draft as any)[key]) initData[key] = (draft as any)[key];
                                    });
                                    setDraftEditData(initData);
                                    setDraftLangSearch('');
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
                                  {draftPublishing === draft.id ? <RefreshCcw size={13} className="animate-spin" /> : <Check size={13} />} {draftPublishing === draft.id ? 'Publishing...' : 'Publish'}
                                </button>
                              </div>
                            </div>
                            {/* Single item progress bar */}
                            {draftPublishing === draft.id && singlePublishProgress && (
                              <div className="mt-3 space-y-2">
                                <div className="flex items-center gap-2">
                                  <div className="flex-1 bg-white/5 rounded-full h-1.5 overflow-hidden">
                                    <div className="h-full bg-gradient-to-r from-violet-500 to-purple-600 rounded-full transition-all duration-500 ease-out" style={{ width: `${singlePublishProgress.percent}%` }} />
                                  </div>
                                  <span className="text-[9px] font-bold text-violet-400 shrink-0">{singlePublishProgress.percent}%</span>
                                </div>
                                <p className="text-[9px] text-violet-300/70 truncate">{singlePublishProgress.step}</p>
                              </div>
                            )}
                            {draftDeleting === draft.id && singleDeleteProgress && (
                              <div className="mt-3 space-y-2">
                                <div className="flex items-center gap-2">
                                  <div className="flex-1 bg-white/5 rounded-full h-1.5 overflow-hidden">
                                    <div
                                      className="h-full bg-gradient-to-r from-red-500 to-red-600 rounded-full transition-all duration-300"
                                      style={{ width: singleDeleteProgress.fileProgress ? `${Math.max(5, (singleDeleteProgress.fileProgress.current / singleDeleteProgress.fileProgress.total) * 100)}%` : '60%' }}
                                    />
                                  </div>
                                  <span className="text-[9px] font-bold text-red-400 shrink-0">{singleDeleteProgress.step}</span>
                                </div>
                              </div>
                            )}
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
                      <div className="bg-card border border-white/10 rounded-2xl p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto space-y-6 shadow-2xl" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between">
                          <h3 className="text-lg font-black text-white">Edit Draft Details</h3>
                          <button onClick={() => setShowDraftEditModal(false)} className="w-8 h-8 bg-white/5 rounded-lg flex items-center justify-center text-textSec hover:text-white hover:bg-white/10">
                            <X size={16} />
                          </button>
                        </div>

                        {/* Multi-Language Support */}
                        <div className="bg-gradient-to-r from-primary/10 to-transparent border border-primary/20 rounded-2xl p-4 space-y-3">
                          <div className="flex items-center gap-2">
                            <Globe className="text-primary" size={20} />
                            <span className="text-sm font-bold text-white">Multi-Language Support</span>
                          </div>
                          <p className="text-xs text-textSec">
                            🌍 Translations are handled automatically. Pack names will be translated to all supported languages via Cloud Function when saved.
                          </p>
                        </div>

                        <div className="space-y-3">
                          <Input
                            label="Pack Name"
                            value={draftEditData.name || ''}
                            onChange={(e: any) => setDraftEditData((prev: any) => ({ ...prev, name: e.target.value }))}
                          />
                          <div className="space-y-2">
                            <div className="flex items-center justify-between px-1">
                              <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Emoji Library</span>
                              <span className="text-[10px] text-textSec/60">Tap to add to pack name</span>
                            </div>
                            <div className="flex flex-wrap gap-1.5 rounded-xl border border-white/5 bg-white/[0.02] p-2">
                              {PACK_NAME_EMOJIS.map((emoji) => (
                                <button
                                  key={emoji}
                                  type="button"
                                  onClick={() => setDraftEditData((prev: any) => ({ ...prev, name: `${(prev.name || '').trimEnd()} ${emoji}`.trim() }))}
                                  className="w-8 h-8 flex items-center justify-center rounded-lg bg-white/5 hover:bg-primary/20 border border-white/5 hover:border-primary/30 text-base transition-all hover:scale-110 active:scale-95"
                                  title={`Add ${emoji}`}
                                >
                                  {emoji}
                                </button>
                              ))}
                            </div>
                          </div>
                        </div>

                        {/* Publisher */}
                        <Input
                          label="Publisher Name"
                          value={draftEditData.publisher || 'Sticky'}
                          onChange={(e: any) => setDraftEditData((prev: any) => ({ ...prev, publisher: e.target.value || 'Sticky', publisher_email: '', publisher_user_id: '' }))}
                        />

                        {/* Category */}
                        <div className="flex items-center gap-4">
                          <div className="flex-1">
                            <label className="text-xs font-bold text-textSec uppercase mb-2 block">Category</label>
                            <select
                              className="w-full bg-hover rounded-xl px-4 py-2.5 text-sm outline-none border-none text-white cursor-pointer"
                              value={draftEditData.category || 'humor'}
                              onChange={(e) => setDraftEditData((prev: any) => ({ ...prev, category: e.target.value }))}
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
                              onClick={() => setDraftEditData((prev: any) => ({ ...prev, is_premium: false }))}
                              className={cn("flex-1 py-2.5 rounded-lg text-[10px] font-black transition-all flex items-center justify-center gap-1.5", !draftEditData.is_premium ? "bg-primary text-white" : "text-textSec")}
                            >🆓 FREE</button>
                            <button
                              onClick={() => setDraftEditData((prev: any) => ({ ...prev, is_premium: true }))}
                              className={cn("flex-1 py-2.5 rounded-lg text-[10px] font-black transition-all flex items-center justify-center gap-1.5", draftEditData.is_premium ? "bg-yellow-500 text-black" : "text-textSec")}
                            >💎 PREMIUM</button>
                          </div>
                        </div>

                        {/* Status + Popular */}
                        <div className="flex items-center gap-4">
                          <div className="flex-1">
                            <label className="text-xs font-bold text-textSec uppercase mb-2 block">Status (Visibility)</label>
                            <div className="flex bg-hover rounded-xl p-1 gap-1">
                              <button
                                onClick={() => setDraftEditData((prev: any) => ({ ...prev, is_active: true }))}
                                className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", draftEditData.is_active !== false ? "bg-primary text-white" : "text-textSec")}
                              >ACTIVE</button>
                              <button
                                onClick={() => setDraftEditData((prev: any) => ({ ...prev, is_active: false }))}
                                className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", draftEditData.is_active === false ? "bg-danger text-white" : "text-textSec")}
                              >INACTIVE (HIDDEN)</button>
                            </div>
                          </div>
                          <div className="flex-1">
                            <label className="text-xs font-bold text-textSec uppercase mb-2 block">Popular (Home Page)</label>
                            <div className="flex bg-hover rounded-xl p-1 gap-1">
                              <button
                                onClick={() => setDraftEditData((prev: any) => ({ ...prev, is_popular: true }))}
                                className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", draftEditData.is_popular === true ? "bg-yellow-500 text-white" : "text-textSec")}
                              >⭐ POPULAR</button>
                              <button
                                onClick={() => setDraftEditData((prev: any) => ({ ...prev, is_popular: false }))}
                                className={cn("flex-1 py-2 rounded-lg text-[10px] font-black transition-all", draftEditData.is_popular !== true ? "bg-hover text-textSec border border-white/10" : "text-textSec")}
                              >NORMAL</button>
                            </div>
                          </div>
                        </div>

                        {/* Save Button */}
                        <button
                          onClick={updateDraftPack}
                          className="w-full bg-primary py-4 rounded-2xl font-black shadow-lg shadow-primary/20 flex items-center justify-center gap-2 hover:scale-[1.01] active:scale-95 transition-all text-white"
                        >
                          <Save size={20} /> SAVE CHANGES
                        </button>
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
            PB Connected: {selectedPack ? selectedPack.id : 'Ready'}
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

      {/* Reject Submission Modal */}
      {rejectModalSubmission && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={() => setRejectModalSubmission(null)}>
          <div className="bg-surface rounded-2xl shadow-2xl p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-bold text-textPrimary mb-1">Reject Submission</h2>
            <p className="text-sm text-textSec mb-4">
              Rejecting <span className="font-semibold text-textPrimary">"{rejectModalSubmission.pack_name}"</span>. Enter a reason to show the user:
            </p>
            <textarea
              className="w-full bg-bg border border-gray-600 rounded-xl p-3 text-sm text-textPrimary resize-none focus:outline-none focus:border-primary"
              rows={4}
              placeholder="e.g. Content violates guidelines, inappropriate imagery, or pack quality too low..."
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              autoFocus
            />
            <div className="flex gap-3 mt-4">
              <button
                onClick={() => setRejectModalSubmission(null)}
                className="flex-1 py-2 rounded-xl border border-gray-600 text-textSec text-sm hover:bg-gray-700 transition"
              >
                Cancel
              </button>
              <button
                onClick={confirmRejectSubmission}
                className="flex-1 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white text-sm font-semibold transition"
              >
                Reject
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Feedback / Message Modal */}
      {feedbackModalSubmission && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm" onClick={() => setFeedbackModalSubmission(null)}>
          <div className="bg-card rounded-2xl shadow-2xl border border-white/10 w-full max-w-lg mx-4 overflow-hidden" onClick={e => e.stopPropagation()}>
            {/* Header */}
            <div className="flex items-center justify-between p-5 border-b border-white/5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-blue-500/15 rounded-xl flex items-center justify-center">
                  <MessageSquare size={18} className="text-blue-400" />
                </div>
                <div>
                  <h2 className="text-sm font-black text-white">Message User</h2>
                  <p className="text-[10px] text-textSec mt-0.5">
                    To: <span className="text-white font-semibold">{feedbackModalSubmission.display_name || feedbackModalSubmission.user_email}</span>
                    {' '}&mdash; Pack: <span className="text-primary font-semibold">{feedbackModalSubmission.pack_name}</span>
                  </p>
                </div>
              </div>
              <button onClick={() => setFeedbackModalSubmission(null)} className="p-2 bg-white/5 rounded-lg hover:bg-white/10 transition-all">
                <X size={14} className="text-textSec" />
              </button>
            </div>

            {/* Quick Templates */}
            <div className="p-4 border-b border-white/5">
              <p className="text-[9px] font-black text-textSec uppercase tracking-widest mb-2">Quick Templates</p>
              <div className="flex flex-wrap gap-2">
                {[
                  { label: '✅ Approved', text: `Great news! Your sticker pack "${feedbackModalSubmission.pack_name}" has been approved and is now live in Sticky!` },
                  { label: '⚠️ Need More', text: `Your pack "${feedbackModalSubmission.pack_name}" needs at least 9 stickers to be published. Please add more stickers and resubmit.` },
                  { label: '🎨 Quality', text: `We reviewed "${feedbackModalSubmission.pack_name}" and the image quality needs improvement. Please use higher resolution images and resubmit.` },
                  { label: '📋 Guidelines', text: `Your pack "${feedbackModalSubmission.pack_name}" was reviewed but doesn't meet our content guidelines. Please review our guidelines and resubmit.` },
                  { label: '🔄 Resubmit', text: `We made some edits to your pack "${feedbackModalSubmission.pack_name}". Please review and resubmit if you'd like any changes.` },
                ].map((t) => (
                  <button
                    key={t.label}
                    onClick={() => setFeedbackMessage(t.text)}
                    className="px-2.5 py-1 bg-white/5 hover:bg-primary/15 hover:text-primary text-textSec border border-white/5 hover:border-primary/30 rounded-lg text-[10px] font-bold transition-all"
                  >
                    {t.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Message Input */}
            <div className="p-4 space-y-3">
              <textarea
                className="w-full bg-hover rounded-xl px-4 py-3 text-sm text-white resize-none outline-none border border-white/5 focus:border-primary/40 transition-all placeholder:text-textSec/40 min-h-[100px]"
                rows={4}
                placeholder="Write your message to the user..."
                value={feedbackMessage}
                onChange={e => setFeedbackMessage(e.target.value)}
                autoFocus
              />
              <p className="text-[9px] text-textSec">The user will receive a push notification AND see this message in their in-app notifications.</p>
              <div className="flex gap-3">
                <button
                  onClick={() => setFeedbackModalSubmission(null)}
                  className="flex-1 py-2.5 rounded-xl border border-white/10 text-textSec text-xs font-bold hover:bg-white/5 transition-all"
                >
                  Cancel
                </button>
                <button
                  onClick={confirmSendFeedback}
                  disabled={!feedbackMessage.trim() || feedbackSending}
                  className="flex-1 py-2.5 rounded-xl bg-blue-500/20 hover:bg-blue-500/30 border border-blue-500/30 text-blue-300 text-xs font-black transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                >
                  {feedbackSending ? <RefreshCcw size={12} className="animate-spin" /> : <MessageSquare size={12} />}
                  {feedbackSending ? 'Sending...' : 'Send Message'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* New Pack Modal */}
      <Modal show={showNewPackModal} onClose={() => setShowNewPackModal(false)} title="Create New Pack">
        <div className="space-y-6">
          <p className="text-sm text-textSec">Manually add a new pack directly to the StickyApp database.</p>

          {/* Çoklu Dil Desteği */}
          <div className="bg-gradient-to-r from-primary/10 to-transparent border border-primary/20 rounded-2xl p-4 space-y-3">
            <div className="flex items-center gap-2">
              <Globe className="text-primary" size={20} />
              <span className="text-sm font-bold text-white">Multi-Language Support</span>
            </div>
            <p className="text-xs text-textSec">
              🌍 Translations are handled automatically. Pack names will be translated to all supported languages via Cloud Function when saved.
            </p>
          </div>
          <Input
            label="Publisher Name"
            placeholder="Sticky"
            value={newPackData.publisher}
            onChange={(e: any) => setNewPackData({ ...newPackData, publisher: e.target.value || 'Sticky', publisher_email: '', publisher_user_id: '' })}
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
            <div className="bg-gradient-to-r from-primary/10 to-transparent border border-primary/20 rounded-2xl p-4 space-y-3">
              <div className="flex items-center gap-2">
                <Globe className="text-primary" size={20} />
                <span className="text-sm font-bold text-white">Multi-Language Support</span>
              </div>
              <p className="text-xs text-textSec">
                🌍 Translations are handled automatically. Pack names will be translated to all supported languages via Cloud Function when saved.
              </p>
            </div>
            <div className="space-y-3">
              <Input
                label="Pack Name"
                value={editFormData.name || ''}
                onChange={(e: any) => setEditFormData({ ...editFormData, name: e.target.value })}
              />
              <div className="space-y-2">
                <div className="flex items-center justify-between px-1">
                  <span className="text-[10px] font-black text-textSec uppercase tracking-widest">Emoji Library</span>
                  <span className="text-[10px] text-textSec/60">Tap to add to pack name</span>
                </div>
                <div className="flex flex-wrap gap-1.5 rounded-xl border border-white/5 bg-white/[0.02] p-2">
                  {PACK_NAME_EMOJIS.map((emoji) => (
                    <button
                      key={emoji}
                      type="button"
                      onClick={() => setEditFormData({ ...editFormData, name: `${(editFormData.name || '').trimEnd()} ${emoji}`.trim() })}
                      className="w-8 h-8 flex items-center justify-center rounded-lg bg-white/5 hover:bg-primary/20 border border-white/5 hover:border-primary/30 text-base transition-all hover:scale-110 active:scale-95"
                      title={`Add ${emoji}`}
                    >
                      {emoji}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <Input
              label="Publisher Name"
              value={editFormData.publisher || 'Sticky'}
              onChange={(e: any) => setEditFormData({ ...editFormData, publisher: e.target.value || 'Sticky' } as any)}
            />
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

            <div>
              <label className="text-xs font-bold text-textSec uppercase mb-2 block">Premium Status</label>
              <div className="flex bg-hover rounded-xl p-1 gap-1">
                <button
                  onClick={() => setEditFormData({ ...editFormData, is_premium: false })}
                  className={cn("flex-1 py-2.5 rounded-lg text-[10px] font-black transition-all flex items-center justify-center gap-1.5", !editFormData.is_premium ? "bg-primary text-white" : "text-textSec")}
                >FREE</button>
                <button
                  onClick={() => setEditFormData({ ...editFormData, is_premium: true })}
                  className={cn("flex-1 py-2.5 rounded-lg text-[10px] font-black transition-all flex items-center justify-center gap-1.5", editFormData.is_premium ? "bg-yellow-500 text-black" : "text-textSec")}
                >PREMIUM</button>
              </div>
            </div>

            <div className="flex items-center gap-4">
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

      {/* Sticker Background Removal Modal */}
      <Modal show={showVideoBgModal} onClose={() => setShowVideoBgModal(false)} title="Sticker Processing">
        <div className="space-y-6">
          <div className="bg-primary/10 border border-primary/20 p-6 rounded-2xl flex items-center gap-4">
            <div className="bg-primary/20 p-3 rounded-xl animate-pulse">
              <CloudLightning className="text-primary" size={28} />
            </div>
            <div>
              <h3 className="text-lg font-black text-white">Remove Background?</h3>
              <p className="text-textSec text-xs mt-1">Keep the original look for fast upload, or use AI only when you want the background removed.</p>
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
