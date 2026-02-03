import { useState, useEffect } from 'react';
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
  getDoc
} from 'firebase/firestore';
import {
  ref,
  deleteObject,
  uploadBytes,
  getDownloadURL,
  listAll,
  getBytes,
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
  Wand2,
  Zap,
  Image as ImageIcon,
  CheckCircle2,
  Menu
} from 'lucide-react';
import { translateTextAllLanguages, TARGET_LANGUAGES } from './utils/translator';
import { magicWizardService, type GifResult } from './utils/magicWizardService';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';
import type { StickerPack, Sticker, ContactMessage, StickerSuggestion } from './types';
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { stickerProcessor } from './utils/stickerProcessor';

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

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
  const [activeTab, setActiveTab] = useState<'dashboard' | 'stats' | 'messages' | 'notifications' | 'magic-wizard'>('dashboard');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'passive' | 'premium' | 'normal' | 'new'>('all');
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

  // Magic Wizard State'leri
  const [wizardStep, setWizardStep] = useState(1);
  const [wizardSuggestions, setWizardSuggestions] = useState<string[]>([]);
  const [wizardMetadata, setWizardMetadata] = useState<any>(null);
  const [wizardGifs, setWizardGifs] = useState<GifResult[]>([]);
  const [selectedGifs, setSelectedGifs] = useState<GifResult[]>([]);
  const [wizardLoading, setWizardLoading] = useState(false);
  const [wizardSearchQuery, setWizardSearchQuery] = useState('');
  const [wizardView, setWizardView] = useState<'create' | 'drafts'>('create');
  const [wizardPage, setWizardPage] = useState(0);

  // Otomasyon / Taslak State'leri
  const [automationDrafts, setAutomationDrafts] = useState<StickerPack[]>([]);
  const [loadingDrafts, setLoadingDrafts] = useState(false);
  const [selectedDraft, setSelectedDraft] = useState<StickerPack | null>(null);

  const [adminName, setAdminName] = useState('');
  const [showWelcome, setShowWelcome] = useState(false);
  const [welcomeExit, setWelcomeExit] = useState(false);

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

  // ========== MAGIC WIZARD (SİHİRBAZ) FONKSİYONLARI ==========

  const fetchWizardSuggestions = async () => {
    setWizardLoading(true);
    try {
      const suggestions = await magicWizardService.getTopicSuggestions();
      setWizardSuggestions(suggestions);
    } catch (error) {
      console.error("Suggestions error:", error);
    } finally {
      setWizardLoading(false);
    }
  };

  const handleSelectTopic = async (topic: string) => {
    setWizardStep(2);
    setWizardLoading(true);
    setWizardPage(0);
    try {
      const existingNames = packs.map(p => p.name);
      const meta = await magicWizardService.generatePackMetadata(topic, existingNames);
      if (meta) {
        setWizardMetadata(meta);
        setWizardSearchQuery(meta.search_keyword || topic);
      }
    } catch (error) {
      console.error("Meta generation error:", error);
    } finally {
      setWizardLoading(false);
    }
  };

  const handleWizardRegenerateMetadata = async () => {
    if (!wizardMetadata) return;
    setWizardLoading(true);
    try {
      const existingNames = packs.map(p => p.name);
      const meta = await magicWizardService.generatePackMetadata(wizardSearchQuery, existingNames);
      if (meta) {
        setWizardMetadata(meta);
      }
    } catch (error) {
      console.error("Regenerate error:", error);
    } finally {
      setWizardLoading(false);
    }
  };

  const handleWizardSearch = async (page: number = 0) => {
    if (!wizardSearchQuery) return;
    setWizardLoading(true);
    setWizardPage(page);
    try {
      const gifs = await magicWizardService.searchGifs(wizardSearchQuery, page);
      setWizardGifs(gifs);
      // Sayfa değişince yukarı kaydır (opsiyonel)
      const container = document.getElementById('wizard-gif-container');
      if (container) container.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (error) {
      console.error("Search error:", error);
    } finally {
      setWizardLoading(false);
    }
  };

  const toggleGifSelection = (gif: GifResult) => {
    if (selectedGifs.find(g => g.id === gif.id)) {
      setSelectedGifs(selectedGifs.filter(g => g.id !== gif.id));
    } else {
      if (selectedGifs.length >= 30) {
        alert("WhatsApp limitleri gereği en fazla 30 sticker seçebilirsiniz.");
        return;
      }
      setSelectedGifs([...selectedGifs, gif]);
    }
  };

  const handleCreateWizardDraft = async () => {
    if (selectedGifs.length < 3) {
      alert("Bir paket için en az 3 sticker seçmelisiniz.");
      return;
    }

    setIsProcessing(true);
    const packId = `wizard_${Date.now()}_${wizardMetadata.name.toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, '')}`;

    try {
      setDeleteProgress({ deleting: true, message: 'Dosyalar işleniyor (FFmpeg)...', current: 0, total: selectedGifs.length });

      const processedStickers: any[] = [];
      let trayUrl = "";

      // 1. Her bir GIF'i indir ve WebP'ye çevir
      for (let i = 0; i < selectedGifs.length; i++) {
        const gif = selectedGifs[i];
        setDeleteProgress(prev => prev ? { ...prev, current: i + 1, message: `Dönüştürülüyor: ${gif.title || 'Sticker'}` } : null);

        const response = await fetch(gif.url);
        const blob = await response.blob();
        const file = new File([blob], `stk_${i}.gif`, { type: 'image/gif' });

        const processedBlob = await stickerProcessor.processAnimated(file);
        const fileName = `stk_${Date.now()}_${i}.webp`;
        const storageRef = ref(storage, `automation_drafts/${packId}/${fileName}`);

        await uploadBytes(storageRef, processedBlob);
        const downloadUrl = await getDownloadURL(storageRef);

        processedStickers.push({
          image_file: fileName,
          url: downloadUrl,
          emojis: ["😀"] // Default emoji
        });

        // İlk sticker'ı tray yap
        if (i === 0) {
          const trayBlob = await stickerProcessor.processTray(file);
          const trayRef = ref(storage, `automation_drafts/${packId}/tray.png`);
          await uploadBytes(trayRef, trayBlob);
          trayUrl = await getDownloadURL(trayRef);
        }
      }

      setDeleteProgress(prev => prev ? { ...prev, message: 'Firestore kaydı oluşturuluyor...' } : null);

      // 2. Draft verisini Firestore'a yaz
      const draftData = {
        id: packId,
        name: wizardMetadata.name,
        name_tr: wizardMetadata.name_tr || wizardMetadata.name,
        category: wizardMetadata.category || 'other',
        publisher: 'Sticky Wizard',
        publisher_email: 'wizard@sticly.com',
        is_premium: false,
        is_active: false,
        is_animated: true,
        sticker_count: processedStickers.length,
        stickers: processedStickers,
        tray_url: trayUrl,
        storage_path: `automation_drafts/${packId}`,
        source_query: wizardSearchQuery,
        created_at: new Date().toISOString(),
        automation_created: true
      };

      await setDoc(doc(db, 'automation_drafts', packId), draftData);

      setDeleteProgress(null);
      alert(`✅ "${wizardMetadata.name}" paketi başarıyla taslak olarak oluşturuldu! Şimdi ana listeye gidip onaylayabilirsin.`);

      // Wizard'ı sıfırla ve dashboard'a dön
      resetWizard();
      setActiveTab('dashboard');
      fetchPacks();
    } catch (error) {
      console.error("Wizard draft creation error:", error);
      alert("Taslak oluşturulurken hata oluştu.");
      setDeleteProgress(null);
    } finally {
      setIsProcessing(false);
    }
  };

  const resetWizard = () => {
    setWizardStep(1);
    setWizardMetadata(null);
    setWizardGifs([]);
    setSelectedGifs([]);
    setWizardPage(0);
  };

  const fetchAutomationDrafts = async () => {
    setLoadingDrafts(true);
    try {
      const draftsSnap = await getDocs(collection(db, 'automation_drafts'));
      const drafts: StickerPack[] = draftsSnap.docs.map(d => {
        const data = d.data();
        return {
          id: d.id,
          ...data,
          is_premium: false,
          is_animated: data.is_animated ?? true,
          download_count: 0,
          view_count: 0,
          favorite_count: 0,
          sticker_count: Number(data.sticker_count || data.stickers?.length || 0),
        } as StickerPack;
      });
      setAutomationDrafts(drafts.sort((a, b) => (b.created_at || '').localeCompare(a.created_at || '')));
    } catch (error) {
      console.error("Automation drafts fetch error:", error);
    } finally {
      setLoadingDrafts(false);
    }
  };

  const approveAutomationDraft = async (draft: StickerPack) => {
    if (!window.confirm(`"${draft.name}" paketini yayına almak istediğinize emin misiniz?`)) return;

    setIsProcessing(true);
    // Overlay'in görünmesi için başlangıç değerleri
    setDeleteProgress({ deleting: true, message: 'Paket hazırlanıyor (Sistem Aktarımı)...', current: 0, total: 100 });

    try {
      // Bir tick bekleyelim ki React render yapabilsin
      await new Promise(resolve => setTimeout(resolve, 300));

      const oldPath = (draft as any).storage_path || `automation_drafts/${draft.id}`;
      const newPath = `stickers/${draft.id}`;

      // 1. Dosyaları listele
      const oldFolderRef = ref(storage, oldPath);
      const filesRes = await listAll(oldFolderRef);
      const totalFiles = filesRes.items.length;

      if (totalFiles === 0) {
        throw new Error("Taşınacak çıkartma bulunamadı! Lütfen taslağın hazır olduğundan emin olun.");
      }

      setDeleteProgress({ deleting: true, message: `${totalFiles} çıkartma taşınmaya hazır...`, current: 0, total: totalFiles });

      const updatedStickers = [];
      let newTrayUrl = draft.tray_url;

      // 2. Dosyaları tek tek taşı (Hata riskini azaltmak için seri işlem)
      for (let i = 0; i < totalFiles; i++) {
        const item = filesRes.items[i];
        const fileName = item.name;

        setDeleteProgress(prev => prev ? { ...prev, current: i + 1, message: `Taşınıyor: ${fileName}` } : null);

        // Dosyayı çek (CORS bypass)
        const fileBytes = await getBytes(item);
        const blob = new Blob([fileBytes]);

        // Yeni konuma yükle
        const newFileRef = ref(storage, `${newPath}/${fileName}`);
        await uploadBytes(newFileRef, blob);
        const newUrl = await getDownloadURL(newFileRef);

        // Eskiyi sil
        await deleteObject(item);

        if (fileName.startsWith('tray')) {
          newTrayUrl = newUrl;
        } else {
          const existingSticker = draft.stickers?.find(s => s.image_file === fileName);
          if (existingSticker) {
            updatedStickers.push({
              image_file: fileName,
              url: newUrl,
              emojis: existingSticker.emojis || ["😀"]
            });
          }
        }

        // Sunucuya nefes aldır (Küçük bir gecikme)
        await new Promise(resolve => setTimeout(resolve, 100));
      }

      setDeleteProgress(prev => prev ? { ...prev, message: 'Firestore güncelleniyor...' } : null);

      // 3. Yeni pack verisini hazırla
      const packData = {
        ...draft,
        stickers: updatedStickers,
        sticker_count: updatedStickers.length,
        tray_url: newTrayUrl,
        is_active: true,
        storage_path: "stickers", // Uygulamanın beklediği sabit yol
        automation_created: false
      };

      delete (packData as any).source_query;

      // 4. Ana koleksiyona yaz ve taslağı temizle
      await setDoc(doc(db, 'stickers', draft.id), packData);
      await deleteDoc(doc(db, 'automation_drafts', draft.id));

      setAutomationDrafts(prev => prev.filter(d => d.id !== draft.id));
      await fetchPacks();

      setDeleteProgress(null);
      alert(`✅ "${draft.name}" paketi başarıyla "stickers" klasörüne taşındı ve yayınlandı!`);
    } catch (error: any) {
      console.error("Approve error:", error);
      let errorMsg = error.message || String(error);

      if (errorMsg.includes('retry-limit-exceeded')) {
        errorMsg = "Giriş/Çıkış Hatası (Ağ Sorunu). CORS ayarları uygulanmış olmalı. Lütfen sayfayı yenileyip tekrar deneyin.";
      }

      alert("⚠️ Onaylama sırasında bir sorun oluştu:\n\n" + errorMsg);
      setDeleteProgress(null);
    } finally {
      setIsProcessing(false);
    }
  };

  const removeStickerFromDraft = async (draft: StickerPack, stickerIdx: number) => {
    if (!window.confirm("Bu çıkartmayı taslaktan kaldırmak istediğinize emin misiniz?")) return;

    try {
      const stickerToDelete = draft.stickers[stickerIdx];
      const updatedStickers = [...draft.stickers];
      updatedStickers.splice(stickerIdx, 1);

      // 1. Firestore'u Güncelle
      const draftRef = doc(db, 'automation_drafts', draft.id);
      await updateDoc(draftRef, {
        stickers: updatedStickers,
        sticker_count: updatedStickers.length
      });

      // 2. Storage'dan Sil
      const storagePath = (draft as any).storage_path || `automation_drafts/${draft.id}`;
      const fileRef = ref(storage, `${storagePath}/${stickerToDelete.image_file}`);

      try {
        await deleteObject(fileRef);
        console.log('[DRAFT DELETE] ✅ Storage dosyası silindi:', stickerToDelete.image_file);
      } catch (storageErr: any) {
        console.warn('[DRAFT DELETE] ⚠️ Storage dosyası zaten yok veya silinemedi:', storageErr.message);
      }

      // 3. State'i güncelle
      const updatedDraft = { ...draft, stickers: updatedStickers, sticker_count: updatedStickers.length };
      setAutomationDrafts(prev => prev.map(d => d.id === draft.id ? updatedDraft : d));
      setSelectedDraft(updatedDraft);

      // 4. Kullanıcıya Bildirim Ver
      alert(`✅ "${stickerToDelete.image_file}" çıkartması taslaktan ve sunucudan başarıyla silindi.`);
    } catch (error) {
      console.error("Remove sticker error:", error);
      alert("Çıkartma silinemedi: " + error);
    }
  };

  const deleteAutomationDraft = async (draft: StickerPack) => {
    if (!window.confirm(`"${draft.name}" taslağını silmek istediğinize emin misiniz? Bu işlem geri alınamaz.`)) return;

    setIsProcessing(true);
    setDeleteProgress({ deleting: true, message: 'Taslak siliniyor...', current: 0, total: 0 });

    try {
      const storagePath = (draft as any).storage_path || `automation_drafts/${draft.id}`;

      // Storage'daki dosyaları sil
      const folderRef = ref(storage, storagePath);
      const files = await listAll(folderRef);

      const totalFiles = files.items.length;
      setDeleteProgress(prev => prev ? { ...prev, total: totalFiles, message: 'Dosyalar siliniyor...' } : null);

      for (let i = 0; i < totalFiles; i++) {
        const item = files.items[i];
        setDeleteProgress(prev => prev ? { ...prev, current: i + 1, message: `Siliniyor: ${item.name}` } : null);
        await deleteObject(item);
      }

      setDeleteProgress(prev => prev ? { ...prev, message: 'Veritabanı kaydı siliniyor...' } : null);

      // Firestore'dan sil
      await deleteDoc(doc(db, 'automation_drafts', draft.id));

      // State'i güncelle
      setAutomationDrafts(prev => prev.filter(d => d.id !== draft.id));

      setTimeout(() => {
        setDeleteProgress(null);
        alert(`🗑️ "${draft.name}" taslağı silindi.`);
      }, 500);

    } catch (error) {
      console.error("Delete draft error:", error);
      setDeleteProgress(null);
      alert("Silme sırasında hata oluştu: " + error);
    } finally {
      setIsProcessing(false);
    }
  };

  // Wizard sekmesine geçince otomatik yüklemeler
  useEffect(() => {
    if (activeTab === 'magic-wizard') {
      if (wizardView === 'create' && wizardSuggestions.length === 0) {
        fetchWizardSuggestions();
      } else if (wizardView === 'drafts') {
        fetchAutomationDrafts();
      }
    }
  }, [activeTab, wizardView]);

  // ========== OTOMASYON FONKSİYONLARI SON ==========

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

  const handleAddSticker = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || !selectedPack) return;

    const currentCount = selectedPack.sticker_count || 0;
    const uploadCount = files.length;
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

    setIsProcessing(true);
    setUploadProgress({ current: 0, total: uploadCount, message: 'İşlem başlıyor...' });

    try {
      const collectionName = selectedPack.is_premium ? 'premium_stickers' : 'stickers';
      const packRef = doc(db, collectionName, selectedPack.id);
      const newStickers: Sticker[] = [];

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
        if (isAnimatedPack) {
          processedBlob = await stickerProcessor.processAnimated(file, (p) => {
            setUploadProgress(prev => prev ? { ...prev, message: `${file.name}: ${p.message}` } : null);
          });
        } else {
          processedBlob = await stickerProcessor.processStatic(file, (p) => {
            setUploadProgress(prev => prev ? { ...prev, message: `${file.name}: ${p.message}` } : null);
          });
        }

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

      const newVersion = Date.now().toString();
      await updateDoc(packRef, {
        stickers: [...(selectedPack.stickers || []), ...newStickers],
        sticker_count: Math.max(0, (selectedPack.sticker_count || 0) + newStickers.length),
        image_data_version: newVersion
      });

      const updated = {
        ...selectedPack,
        stickers: [...(selectedPack.stickers || []), ...newStickers],
        sticker_count: (selectedPack.sticker_count || 0) + newStickers.length,
        image_data_version: newVersion
      };

      setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
      setSelectedPack(updated);
      alert(`${newStickers.length} sticker başarıyla işlendi ve eklendi.`);
    } catch (error) {
      console.error(error);
      alert("Yükleme hatası: " + error);
    } finally {
      setIsProcessing(false);
      setUploadProgress(null);
      if (e.target) e.target.value = '';
    }
  };

  const handleUpdateTray = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !selectedPack) return;

    setIsProcessing(true);
    setUploadProgress({ current: 1, total: 1, message: 'Kapak resmi hazırlanıyor...' });

    // Kısa gecikme: State'in render edilmesine ve overlay'in görünmesine izin ver
    await new Promise(r => setTimeout(r, 100));

    try {
      // Eski kapak resmini sil (eğer varsa)
      if (selectedPack.tray_image_file) {
        setUploadProgress(prev => prev ? { ...prev, message: 'Eski kapak resmi siliniyor...' } : null);
        const oldTrayPath = `stickers/${selectedPack.id}/${selectedPack.tray_image_file}`;
        try {
          await deleteObject(ref(storage, oldTrayPath));
          console.log('Eski kapak resmi silindi:', oldTrayPath);
        } catch (deleteError) {
          console.log('Eski kapak resmi silinemedi (muhtemelen mevcut değil):', deleteError);
        }
      }

      setUploadProgress(prev => prev ? { ...prev, message: 'Arka plan siliniyor...' } : null);
      const processedBlob = await stickerProcessor.processTray(file, (p) => {
        setUploadProgress(prev => prev ? { ...prev, message: p.message } : null);
      });

      const collectionName = selectedPack.is_premium ? 'premium_stickers' : 'stickers';
      const fileName = `tray_${Date.now()}.png`;
      const storagePath = `stickers/${selectedPack.id}/${fileName}`;
      const storageRef = ref(storage, storagePath);

      await uploadBytes(storageRef, processedBlob);
      const url = await getDownloadURL(storageRef);
      const newVersion = Date.now().toString();

      await updateDoc(doc(db, collectionName, selectedPack.id), {
        tray_url: url,
        tray_image_file: fileName,
        image_data_version: newVersion
      });

      const updated = { ...selectedPack, tray_url: url, tray_image_file: fileName, image_data_version: newVersion };
      setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
      setSelectedPack(updated);
      alert("Kapak resmi başarıyla işlendi ve güncellendi. Eski kapak resmi silindi.");
    } catch (e) {
      alert("Hata: " + e);
    } finally {
      setIsProcessing(false);
      setUploadProgress(null);
    }
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
              <Wand2 className="text-primary animate-bounce-subtle" size={56} />
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
                { id: 'magic-wizard', label: 'Sihirbaz', icon: Wand2, count: automationDrafts.length }
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
              { id: 'magic-wizard', icon: Wand2, label: 'Sihirbaz', count: automationDrafts.length }
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
                        <label className="absolute inset-0 bg-primary/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center cursor-pointer">
                          <RefreshCcw className="text-white" size={24} />
                          <input type="file" className="hidden" onChange={handleUpdateTray} disabled={isProcessing} />
                        </label>
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
        ) : activeTab === 'magic-wizard' ? (
          <div className="flex-1 overflow-y-auto p-4 md:p-12 custom-scrollbar bg-background">
            <div className="max-w-6xl mx-auto space-y-8 md:space-y-12">
              {/* Wizard Header */}
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4 px-4">
                <div className="flex items-center gap-5">
                  <div className="w-16 h-16 bg-gradient-to-br from-primary/20 to-accent/20 rounded-2xl flex items-center justify-center shadow-inner">
                    <Wand2 size={36} className="text-primary animate-pulse" />
                  </div>
                  <div>
                    <h1 className="text-3xl font-black text-white tracking-tight">Sihirbaz v2.0</h1>
                    <div className="flex items-center gap-6 mt-2">
                      <button
                        onClick={() => setWizardView('create')}
                        className={cn("text-xs font-black uppercase tracking-widest transition-all", wizardView === 'create' ? "text-primary border-b-2 border-primary pb-1" : "text-textSec hover:text-white")}
                      >
                        Paket Tasarla
                      </button>
                      <button
                        onClick={() => setWizardView('drafts')}
                        className={cn("text-xs font-black uppercase tracking-widest transition-all relative", wizardView === 'drafts' ? "text-primary border-b-2 border-primary pb-1" : "text-textSec hover:text-white")}
                      >
                        Bekleyen Taslaklar
                        {automationDrafts.length > 0 && (
                          <span className="absolute -top-3 -right-4 w-5 h-5 bg-danger text-[10px] text-white rounded-full flex items-center justify-center border-2 border-background font-black">
                            {automationDrafts.length}
                          </span>
                        )}
                      </button>
                    </div>
                  </div>
                </div>

                {wizardView === 'create' && (
                  <div className="flex items-center gap-3 bg-card/50 backdrop-blur-md rounded-2xl p-2 border border-white/5">
                    {[1, 2, 3].map(step => (
                      <div
                        key={step}
                        className={cn(
                          "px-4 h-10 rounded-xl flex items-center gap-2 text-xs font-black transition-all",
                          wizardStep === step ? "bg-primary text-white shadow-lg shadow-primary/20" : wizardStep > step ? "bg-success/20 text-success" : "text-textSec bg-white/5"
                        )}
                      >
                        <span className="w-5 h-5 rounded-lg flex items-center justify-center bg-black/20">
                          {wizardStep > step ? <Check size={12} /> : step}
                        </span>
                        <span className="hidden sm:inline">
                          {step === 1 ? 'KONU' : step === 2 ? 'DETAYLAR' : 'GÖRSELLER'}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Wizard Body */}
              <div className="bg-card/40 backdrop-blur-sm rounded-[40px] border border-white/5 overflow-hidden shadow-2xl relative min-h-[600px] flex flex-col transition-all duration-500">
                {wizardLoading && (
                  <div className="absolute inset-0 bg-background/80 backdrop-blur-md z-50 flex flex-col items-center justify-center animate-in fade-in duration-300">
                    <div className="relative mb-8">
                      <div className="absolute inset-0 bg-primary/20 blur-[60px] rounded-full animate-pulse" />
                      <RefreshCcw size={64} className="animate-spin text-primary relative z-10" />
                    </div>
                    <h3 className="text-2xl font-black text-white uppercase tracking-tighter">AI Sihir Yapıyor...</h3>
                    <p className="text-textSec text-sm mt-3 font-bold opacity-60">Milyarlarca görsel arasında sizin için en iyileri aranıyor</p>
                  </div>
                )}

                {wizardView === 'create' && wizardStep === 1 && (
                  <div className="p-5 md:p-10 flex-1 flex flex-col animate-in fade-in slide-in-from-bottom-8 duration-700">
                    <div className="max-w-3xl mx-auto w-full text-center space-y-2 md:space-y-4 mb-8 md:mb-12 relative">
                      <h2 className="text-2xl md:text-5xl font-black text-white tracking-tighter italic">Yeni Bir Hikaye Başlat.</h2>
                      <p className="text-sm md:text-lg text-textSec font-medium">Trendlerden birini seçin veya hayalinizdeki konuyu AI'ya anlatın.</p>
                      <button
                        onClick={fetchWizardSuggestions}
                        className="absolute -top-4 -right-8 p-4 bg-primary/10 hover:bg-primary/20 text-primary rounded-2xl transition-all shadow-xl z-20"
                        title="Önerileri Yenile"
                      >
                        <RefreshCcw size={24} className={wizardLoading ? "animate-spin" : ""} />
                      </button>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-12">
                      {wizardSuggestions.map((suggestion, idx) => (
                        <button
                          key={idx}
                          onClick={() => handleSelectTopic(suggestion)}
                          className="group relative h-32 md:h-48 bg-hover/30 hover:bg-primary/10 border border-white/5 rounded-[2rem] p-5 md:p-6 text-left transition-all hover:scale-[1.02] hover:border-primary/20"
                        >
                          <div className="bg-primary/10 w-8 h-8 md:w-10 md:h-10 rounded-xl flex items-center justify-center text-primary mb-3 md:mb-4 group-hover:scale-110 group-hover:bg-primary group-hover:text-white transition-all">
                            <Sparkles className="w-4 h-4 md:w-5 md:h-5 text-primary group-hover:text-white transition-colors" />
                          </div>
                          <span className="block font-black text-white text-base md:text-xl leading-tight tracking-tight">{suggestion}</span>
                          <div className="absolute bottom-6 right-6 opacity-0 group-hover:opacity-100 transition-opacity">
                            <ChevronRight size={24} className="text-primary" />
                          </div>
                        </button>
                      ))}
                    </div>

                    <div className="max-w-2xl mx-auto w-full">
                      <div className="relative group">
                        <div className="absolute inset-0 bg-primary/10 blur-2xl rounded-full opacity-0 group-focus-within:opacity-100 transition-opacity" />
                        <input
                          type="text"
                          placeholder="Kendi konunu yaz..."
                          className="relative w-full h-14 md:h-20 bg-hover/50 border border-white/10 rounded-full px-6 md:px-10 pr-16 md:pr-20 text-sm md:text-xl text-white font-bold placeholder:text-textSec/40 focus:border-primary focus:bg-card outline-none transition-all shadow-2xl"
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') handleSelectTopic(e.currentTarget.value);
                          }}
                        />
                        <button
                          onClick={(e) => handleSelectTopic((e.currentTarget.previousSibling as HTMLInputElement).value)}
                          className="absolute right-2 md:right-4 top-2 md:top-4 w-10 h-10 md:w-12 md:h-12 bg-primary hover:bg-primary/80 rounded-full flex items-center justify-center text-white transition-all shadow-lg shadow-primary/30 active:scale-90"
                        >
                          <Send className="w-4 h-4 md:w-5 md:h-5 text-white" />
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {/* Step 2: Confirmation & Metadata */}
                {wizardView === 'create' && wizardStep === 2 && (
                  <div className="p-5 md:p-10 flex-1 animate-in fade-in slide-in-from-right-8 duration-700">
                    <div className="flex items-center gap-6 mb-12">
                      <button onClick={() => setWizardStep(1)} className="w-12 h-12 bg-hover hover:bg-white/10 rounded-2xl flex items-center justify-center text-textSec transition-all">
                        <ChevronRight size={24} className="rotate-180" />
                      </button>
                      <h2 className="text-4xl font-black text-white tracking-tighter italic">Paket Kimliği.</h2>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 max-w-5xl mx-auto">
                      <div className="lg:col-span-12 space-y-10">
                        <div className="flex items-end gap-4">
                          <div className="flex-1">
                            <Input
                              label="Paket İsmi (İngilizce)"
                              value={wizardMetadata?.name || ''}
                              onChange={(e: any) => setWizardMetadata({ ...wizardMetadata, name: e.target.value })}
                              placeholder="Emoji Master ✨"
                              helpText="Emoji ve isim mağazada bu şekilde görünecek."
                            />
                          </div>
                          <button
                            onClick={handleWizardRegenerateMetadata}
                            className="w-14 h-14 bg-primary/10 hover:bg-primary/20 text-primary rounded-xl flex items-center justify-center transition-all shrink-0 mb-6"
                            title="Yeni İsim Öner"
                          >
                            <RefreshCcw size={24} className={wizardLoading ? "animate-spin" : ""} />
                          </button>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                          <div className="space-y-2 relative group">
                            <label className="text-[10px] font-black text-textSec uppercase tracking-widest px-1">Kategori</label>
                            <div className="flex gap-2">
                              <select
                                value={wizardMetadata?.category || 'other'}
                                onChange={(e) => setWizardMetadata({ ...wizardMetadata, category: e.target.value })}
                                className="flex-1 h-14 bg-hover border-transparent border focus:border-primary/50 rounded-xl px-4 outline-none transition-all text-white font-bold appearance-none"
                              >
                                {CATEGORIES.map(c => <option key={c.id} value={c.id}>{c.emoji} {c.name}</option>)}
                              </select>
                              <button
                                onClick={async () => {
                                  setWizardLoading(true);
                                  const cat = await magicWizardService.getAutoCategory(wizardMetadata?.name || wizardSearchQuery);
                                  setWizardMetadata({ ...wizardMetadata, category: cat });
                                  setWizardLoading(false);
                                }}
                                className="w-14 h-14 bg-success/10 hover:bg-success/20 text-success rounded-xl flex items-center justify-center transition-all"
                                title="AI ile Kategori Belirle"
                              >
                                <Zap size={20} />
                              </button>
                            </div>
                          </div>
                          <div className="space-y-2">
                            <label className="text-[10px] font-black text-textSec uppercase tracking-widest px-1">Tip</label>
                            <div className="h-14 bg-success/10 border border-success/20 rounded-xl px-6 flex items-center justify-between text-success font-black text-sm">
                              <span>HAREKETLİ (ANIMATED)</span>
                              <Sparkles size={18} />
                            </div>
                          </div>
                        </div>

                        <div className="bg-primary/5 border border-primary/10 rounded-[32px] p-8 flex items-start gap-6">
                          <div className="w-16 h-16 bg-primary/20 rounded-2xl flex items-center justify-center text-primary shrink-0 transition-transform group-hover:scale-110">
                            <Search size={32} />
                          </div>
                          <div className="space-y-2 flex-1">
                            <label className="text-[10px] font-black text-primary uppercase tracking-[0.2em]">GIF Arama Stratejisi</label>
                            <p className="text-textSec text-sm font-medium leading-relaxed">
                              Sizin için en iyi sonuçları verecek anahtar kelimeyi hazırladık. İsterseniz değiştirebilirsiniz.
                            </p>
                            <input
                              type="text"
                              value={wizardSearchQuery}
                              onChange={(e) => setWizardSearchQuery(e.target.value)}
                              className="w-full mt-4 bg-white/5 border border-white/10 rounded-xl px-6 py-4 text-white font-bold focus:border-primary outline-none"
                            />
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="mt-16 flex justify-end">
                      <button
                        onClick={() => {
                          setWizardStep(3);
                          handleWizardSearch(0);
                        }}
                        className="px-12 h-20 bg-primary hover:bg-primary/80 text-white rounded-[24px] font-black text-xl flex items-center justify-center gap-4 transition-all shadow-xl shadow-primary/20 hover:scale-[1.02] active:scale-95"
                      >
                        GÖRSELLERİ BUL
                        <ChevronRight size={28} />
                      </button>
                    </div>
                  </div>
                )}

                {/* Step 3: GIF Selection */}
                {wizardView === 'create' && wizardStep === 3 && (
                  <div className="p-8 flex-1 flex flex-col animate-in fade-in slide-in-from-right-8 duration-700">
                    <div className="flex flex-col md:flex-row items-start md:items-center justify-between mb-8 gap-6 px-4">
                      <div className="flex items-center gap-6">
                        <button onClick={() => setWizardStep(2)} className="w-12 h-12 bg-hover hover:bg-white/10 rounded-2xl flex items-center justify-center text-textSec transition-all">
                          <ChevronRight size={24} className="rotate-180" />
                        </button>
                        <div>
                          <h2 className="text-3xl font-black text-white italic tracking-tight">Koleksiyonu Oluştur.</h2>
                          <div className="flex items-center gap-3">
                            <p className="text-textSec font-bold text-sm opacity-60">Paketiniz için en az 3, en fazla 30 görsel seçin.</p>
                            <span className="w-1.5 h-1.5 rounded-full bg-white/20" />
                            <p className="text-primary font-black text-sm uppercase tracking-widest">SAYFA {wizardPage + 1}</p>
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-4 bg-hover/50 p-2 rounded-[24px] border border-white/5 w-full md:w-auto">
                        <div className="flex items-center gap-2 pl-4 flex-1">
                          <Search className="text-textSec" size={20} />
                          <input
                            type="text"
                            value={wizardSearchQuery}
                            onChange={(e) => setWizardSearchQuery(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && handleWizardSearch(0)}
                            className="bg-transparent border-none outline-none text-white font-bold text-base md:w-64"
                            placeholder="Farklı stickerlar ara..."
                          />
                        </div>
                        <button onClick={() => handleWizardSearch(0)} className="h-12 px-8 bg-primary hover:bg-primary/80 text-white font-black rounded-full transition-all text-xs uppercase tracking-widest shadow-lg shadow-primary/20">ARA</button>
                      </div>
                    </div>

                    <div id="wizard-gif-container" className="flex-1 overflow-auto bg-black/20 rounded-[40px] p-6 border border-white/5 custom-scrollbar min-h-[460px]">
                      {wizardGifs.length === 0 ? (
                        <div className="h-full flex flex-col items-center justify-center opacity-40">
                          <div className="w-20 h-20 bg-white/5 rounded-full flex items-center justify-center mb-4">
                            <ImageIcon size={40} />
                          </div>
                          <p className="font-black uppercase tracking-widest text-xs">Görsel Bulunamadı</p>
                        </div>
                      ) : (
                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-6">
                          {wizardGifs.map((gif) => {
                            const isSelected = selectedGifs.find(g => g.id === gif.id);
                            return (
                              <div
                                key={gif.id}
                                onClick={() => toggleGifSelection(gif)}
                                className={cn(
                                  "aspect-square rounded-[32px] p-4 border-2 relative group cursor-pointer transition-all duration-300",
                                  isSelected ? "border-primary bg-primary/5 shadow-2xl scale-[0.95]" : "border-transparent bg-hover/20 hover:bg-hover/40"
                                )}
                              >
                                <img
                                  src={gif.preview}
                                  alt=""
                                  className={cn("w-full h-full object-contain transition-all duration-500", isSelected ? "animate-pulse" : "group-hover:scale-110")}
                                />
                                <div className={cn(
                                  "absolute top-4 right-4 w-8 h-8 rounded-xl flex items-center justify-center transition-all shadow-lg",
                                  isSelected ? "bg-primary text-white scale-110" : "bg-black/40 text-white/0 group-hover:text-white/50"
                                )}>
                                  <Check size={20} strokeWidth={4} />
                                </div>
                                <div className="absolute inset-x-4 bottom-4 opacity-0 group-hover:opacity-100 transition-all">
                                  <div className="bg-black/60 backdrop-blur-md px-3 py-1.5 rounded-lg text-[10px] font-black text-white/80 truncate uppercase text-center border border-white/10">
                                    {gif.title || 'STICKER'}
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    <div className="mt-8 flex flex-col sm:flex-row items-center justify-between gap-6 px-4">
                      <div className="flex items-center gap-6">
                        <div className="flex -space-x-4 overflow-hidden p-2">
                          {selectedGifs.slice(0, 6).map((g, i) => (
                            <div key={i} className="relative">
                              <img src={g.preview} className="inline-block h-12 w-12 rounded-2xl ring-4 ring-card object-cover bg-bgSecondary" />
                            </div>
                          ))}
                          {selectedGifs.length > 6 && (
                            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-hover ring-4 ring-card text-xs font-black text-white">
                              +{selectedGifs.length - 6}
                            </div>
                          )}
                        </div>
                        <div>
                          <span className="block text-2xl font-black text-white leading-none">{selectedGifs.length} <span className="text-sm font-bold opacity-40">/ 30</span></span>
                          <span className="text-[10px] font-black text-primary uppercase tracking-[0.2em]">Seçilen Görsel</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-4">
                        <div className="flex items-center gap-2 bg-white/5 p-1 rounded-2xl mr-4 border border-white/5">
                          <button
                            onClick={() => handleWizardSearch(wizardPage - 1)}
                            disabled={wizardPage === 0 || wizardLoading}
                            className="w-12 h-12 rounded-xl flex items-center justify-center hover:bg-white/10 disabled:opacity-20 transition-all font-black text-white"
                          >
                            <ChevronRight size={24} className="rotate-180" />
                          </button>
                          <span className="w-12 text-center font-black text-white text-sm">{wizardPage + 1}</span>
                          <button
                            onClick={() => handleWizardSearch(wizardPage + 1)}
                            disabled={wizardGifs.length < 30 || wizardLoading}
                            className="w-12 h-12 rounded-xl flex items-center justify-center hover:bg-white/10 disabled:opacity-20 transition-all font-black text-white"
                          >
                            <ChevronRight size={24} />
                          </button>
                        </div>

                        <button
                          onClick={handleCreateWizardDraft}
                          disabled={selectedGifs.length < 3 || isProcessing}
                          className="w-full sm:w-auto px-12 h-16 bg-primary hover:bg-primary/80 disabled:opacity-30 disabled:grayscale text-white rounded-[24px] font-black text-lg flex items-center justify-center gap-4 transition-all shadow-xl shadow-primary/30 hover:scale-[1.05] active:scale-95"
                        >
                          <Zap size={24} className="fill-current" />
                          TASLAĞI KAYDET VE BİTİR
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {/* Drafts View */}
                {wizardView === 'drafts' && (
                  <div className="p-10 flex-1 flex flex-col animate-in fade-in slide-in-from-bottom-8 duration-700">
                    <div className="flex items-center justify-between mb-10">
                      <h2 className="text-4xl font-black text-white tracking-tighter italic">Taslak Havuzu.</h2>
                      <button
                        onClick={fetchAutomationDrafts}
                        disabled={loadingDrafts}
                        className="flex items-center gap-3 px-6 py-3 bg-white/5 hover:bg-white/10 border border-white/5 rounded-2xl text-xs font-black uppercase tracking-widest text-textSec hover:text-white transition-all"
                      >
                        <RefreshCcw size={16} className={loadingDrafts ? "animate-spin" : ""} />
                        LİSTEYİ GÜNCELLE
                      </button>
                    </div>

                    {loadingDrafts ? (
                      <div className="flex-1 flex flex-col items-center justify-center py-20 grayscale opacity-50">
                        <RefreshCcw size={64} className="animate-spin text-primary mb-6" />
                        <p className="text-white font-black uppercase tracking-widest text-sm">Veriler Çekiliyor...</p>
                      </div>
                    ) : automationDrafts.length === 0 ? (
                      <div className="flex-1 flex flex-col items-center justify-center py-20 text-center space-y-6">
                        <div className="w-32 h-32 bg-white/5 rounded-[40px] flex items-center justify-center text-textSec/20">
                          <Package size={64} />
                        </div>
                        <div className="space-y-2">
                          <h3 className="text-2xl font-black text-textSec uppercase">Havuz Tamamen Boş</h3>
                          <p className="text-textSec text-sm font-medium opacity-50 max-w-xs mx-auto">Henüz yayına hazırlanan bir paket taslağı bulunmuyor.</p>
                        </div>
                        <button
                          onClick={() => setWizardView('create')}
                          className="px-8 py-4 bg-primary/20 text-primary hover:bg-primary/30 rounded-2xl font-black text-sm uppercase tracking-widest transition-all"
                        >
                          İLK PAKETİNİ TASARLA
                        </button>
                      </div>
                    ) : (
                      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-8">
                        {automationDrafts.map(draft => (
                          <div
                            key={draft.id}
                            className="bg-hover/20 rounded-[32px] border border-white/5 overflow-hidden group hover:border-primary/20 hover:bg-hover/40 transition-all flex flex-col shadow-xl"
                          >
                            <div className="h-40 bg-gradient-to-br from-white/5 to-transparent flex items-center justify-center relative p-6">
                              <div className="absolute inset-0 bg-primary/5 opacity-0 group-hover:opacity-100 transition-opacity" />
                              {draft.tray_url ? (
                                <img src={draft.tray_url} className="w-20 h-20 object-contain filter drop-shadow-2xl transition-transform duration-500 group-hover:scale-110" alt="" />
                              ) : (
                                <ImageIcon size={64} className="text-textSec/20" />
                              )}
                              <div className="absolute top-4 right-4 px-3 py-1.5 bg-black/40 backdrop-blur-md text-white text-[10px] font-black rounded-lg uppercase border border-white/5">
                                {draft.sticker_count} Stickers
                              </div>
                            </div>
                            <div className="p-6 flex-1 flex flex-col space-y-6">
                              <div>
                                <h3 className="text-xl font-black text-white mb-1 truncate">{draft.name}</h3>
                                <div className="flex items-center gap-2">
                                  <span className="w-2 h-2 bg-warning rounded-full shadow-[0_0_8px_rgba(255,191,0,0.5)]" />
                                  <span className="text-textSec text-[10px] font-black uppercase tracking-widest">ONAY BEKLİYOR • {(draft as any).category}</span>
                                </div>
                              </div>

                              <div className="flex gap-3">
                                <button
                                  onClick={() => setSelectedDraft(draft)}
                                  className="flex-1 h-12 bg-white/5 hover:bg-white/10 text-white text-[10px] font-black uppercase tracking-widest rounded-xl transition-all border border-white/5"
                                >
                                  ÖNİZLE
                                </button>
                                <button
                                  onClick={() => approveAutomationDraft(draft)}
                                  className="h-12 px-6 bg-primary hover:bg-primary/80 text-white rounded-xl transition-all shadow-lg shadow-primary/20"
                                >
                                  <Check size={20} strokeWidth={3} />
                                </button>
                                <button
                                  onClick={() => deleteAutomationDraft(draft)}
                                  className="h-12 px-6 bg-danger/10 hover:bg-danger/20 text-danger rounded-xl transition-all"
                                >
                                  <Trash2 size={20} />
                                </button>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Draft Preview Modal */}
              {selectedDraft && (
                <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
                  <div className="absolute inset-0 bg-black/90 backdrop-blur-2xl" onClick={() => setSelectedDraft(null)} />
                  <div className="bg-card w-full max-w-5xl max-h-[90vh] rounded-[40px] overflow-hidden border border-white/10 shadow-2xl relative z-10 flex flex-col animate-in zoom-in-95 duration-300">
                    {/* Modal Header */}
                    <div className="p-10 border-b border-white/5 flex items-center justify-between bg-gradient-to-br from-card to-background">
                      <div className="flex items-center gap-8">
                        <div className="w-24 h-24 bg-white/5 rounded-[32px] flex items-center justify-center p-4 border border-white/5 shadow-inner">
                          <img src={selectedDraft.tray_url} alt="" className="w-full h-full object-contain filter drop-shadow-xl" />
                        </div>
                        <div>
                          <div className="flex items-center gap-3 mb-2">
                            <h2 className="text-4xl font-black text-white tracking-tighter italic">{selectedDraft.name}</h2>
                            <span className="px-3 py-1 bg-primary/20 text-primary text-[10px] font-black rounded-full uppercase tracking-widest">{(selectedDraft as any).category}</span>
                          </div>
                          <p className="text-textSec font-bold tracking-widest text-xs uppercase opacity-40">Toplam {selectedDraft.sticker_count} Profesyonel Çıkartma</p>
                        </div>
                      </div>
                      <button onClick={() => setSelectedDraft(null)} className="w-14 h-14 bg-white/5 hover:bg-danger/20 hover:text-danger rounded-2xl flex items-center justify-center transition-all">
                        <X size={28} />
                      </button>
                    </div>

                    {/* Modal Content */}
                    <div className="flex-1 overflow-auto p-10 bg-black/20 custom-scrollbar">
                      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-6">
                        {selectedDraft.stickers?.map((sticker, idx) => (
                          <div
                            key={idx}
                            className="aspect-square bg-hover/10 rounded-[32px] p-6 border border-white/5 flex items-center justify-center relative group hover:bg-hover/30 transition-all cursor-zoom-in"
                            onClick={() => setPreviewSticker({ url: sticker.url, title: sticker.image_file })}
                          >
                            <img
                              src={sticker.url}
                              alt=""
                              className="w-full h-full object-contain transition-transform duration-500 group-hover:scale-125 pointer-events-none"
                            />
                            <div className="absolute inset-0 bg-primary/20 opacity-0 group-hover:opacity-100 transition-all rounded-[32px] flex items-end justify-center p-4 pointer-events-none">
                              <span className="text-xs font-black text-white bg-black/60 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/10 shadow-xl">{sticker.emojis.join('')}</span>
                            </div>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                removeStickerFromDraft(selectedDraft, idx);
                              }}
                              className="absolute top-2 right-2 w-10 h-10 bg-danger text-white rounded-xl flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all shadow-xl hover:scale-110 active:scale-90"
                            >
                              <X size={20} strokeWidth={3} />
                            </button>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Modal Footer */}
                    <div className="p-10 border-t border-white/5 flex gap-6 bg-card/50">
                      <button
                        onClick={() => {
                          approveAutomationDraft(selectedDraft);
                          setSelectedDraft(null);
                        }}
                        disabled={isProcessing}
                        className="flex-1 h-20 bg-primary hover:bg-primary/80 text-white rounded-[24px] font-black text-xl flex items-center justify-center gap-4 transition-all shadow-2xl shadow-primary/20 hover:scale-[1.02] active:scale-95"
                      >
                        <CheckCircle2 size={24} />
                        ONAYLA VE MAĞAZAYA GÖNDER
                      </button>
                      <button
                        onClick={() => {
                          deleteAutomationDraft(selectedDraft);
                          setSelectedDraft(null);
                        }}
                        disabled={isProcessing}
                        className="w-20 h-20 bg-danger/10 hover:bg-danger/20 text-danger rounded-[24px] flex items-center justify-center transition-all border border-danger/10"
                      >
                        <Trash2 size={32} />
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        ) : null}
      </main>

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
                  <input
                    type="text"
                    placeholder="Örn: Funny Cats, Love Stickers..."
                    className="w-full bg-bgSecondary border-2 border-primary/50 rounded-xl p-3 text-white placeholder:text-white/30 text-base focus:border-primary outline-none transition-all"
                    value={newPackData.name}
                    onChange={(e) => setNewPackData({ ...newPackData, name: e.target.value, name_en: e.target.value })}
                  />
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



          <div className="bg-primary/5 border border-primary/20 p-4 rounded-xl flex items-center gap-3">
            <RefreshCcw className="text-primary animate-spin" size={20} />
            <span className="text-xs text-textMain/70 font-bold uppercase">HAREKETLİ PAKET MODALI AKTİF</span>
          </div>
          <div className="bg-primary/5 border border-primary/20 p-4 rounded-xl flex items-center gap-3">
            <Info className="text-primary" size={20} />
            <span className="text-xs text-textMain/70 uppercase font-bold">Yeni paket oluşturduktan sonra video ekleme paneli açılacaktır.</span>
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
                    <input
                      type="text"
                      placeholder="Örn: Funny Cats, Love Stickers..."
                      className="w-full bg-bgSecondary border-2 border-primary/50 rounded-xl p-3 text-white placeholder:text-white/30 text-base focus:border-primary outline-none transition-all"
                      value={editFormData.name || ''}
                      onChange={(e) => setEditFormData({ ...editFormData, name: e.target.value, name_en: e.target.value })}
                    />
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

    </div >
  );
}

export default App;
