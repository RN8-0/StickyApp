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
  onSnapshot
} from 'firebase/firestore';
import { ref, deleteObject, uploadBytes, getDownloadURL, listAll } from 'firebase/storage';
import {
  signInWithEmailAndPassword,
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
  ChevronDown
} from 'lucide-react';
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

function App() {
  const [user, setUser] = useState<User | null>(null);
  console.log("STICKY ADMIN V3 LOADING...");
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [packs, setPacks] = useState<StickerPack[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedPack, setSelectedPack] = useState<StickerPack | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState<'dashboard' | 'stats' | 'messages' | 'notifications'>('dashboard');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'passive' | 'premium' | 'normal' | 'new'>('all');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [statsFilter, setStatsFilter] = useState<'all' | 'active' | 'passive' | 'premium' | 'normal'>('all');
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);

  // Mail System States
  const [messages, setMessages] = useState<ContactMessage[]>([]);
  const [suggestions, setSuggestions] = useState<StickerSuggestion[]>([]);
  const [messagesSubTab, setMessagesSubTab] = useState<'messages' | 'suggestions'>('messages');

  // Modals
  const [showNewPackModal, setShowNewPackModal] = useState(false);
  const [showEditPackModal, setShowEditPackModal] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  // Form States
  const [newPackData, setNewPackData] = useState({
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

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (u) => {
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


  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch (error) {
      alert("Giriş hatası: Şifre veya e-posta hatalı.");
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

      console.log("FETCHED PACKS DATA:");
      console.table(allPacks.map(p => ({ name: p.name, dl: p.download_count, views: p.view_count })));
      setPacks(allPacks.sort((a, b) => a.name.localeCompare(b.name)));
    } catch (error) {
      console.error("Fetch error:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreatePack = async () => {
    if (!newPackData.name || !newPackData.publisher) return alert("Lütfen isim ve yayıncı alanlarını doldurun.");
    setIsProcessing(true);
    try {
      const packId = newPackData.name.toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, '');
      const collectionName = newPackData.is_premium ? 'premium_stickers' : 'stickers';

      const packData: any = {
        name: newPackData.name,
        name_tr: newPackData.name_tr,
        name_zh: newPackData.name_zh || '',
        name_es: newPackData.name_es || '',
        name_ar: newPackData.name_ar || '',
        name_hi: newPackData.name_hi || '',
        name_pt: newPackData.name_pt || '',
        publisher: newPackData.publisher,
        publisher_email: "contact@sticly.com",
        category: newPackData.category,
        is_premium: newPackData.is_premium,
        is_animated: true, // Always animated
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
        // Move document between collections
        const oldRef = doc(db, oldCollection, selectedPack.id);
        const newRef = doc(db, newCollection, selectedPack.id);

        const fullData = { ...selectedPack, ...updatedData };
        await setDoc(newRef, fullData);
        await deleteDoc(oldRef);
      } else {
        await updateDoc(doc(db, oldCollection, selectedPack.id), updatedData);
      }

      const updated = { ...selectedPack, ...updatedData } as StickerPack;
      setPacks(packs.map(p => p.id === selectedPack.id ? updated : p));
      setSelectedPack(updated);
      setShowEditPackModal(false);
      alert("Paket bilgileri ve tipi başarıyla güncellendi. Uygulamada yansıması birkaç dakika sürebilir (Önbellek nedeniyle).");
    } catch (e) {
      alert("Hata: " + e);
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
        const storagePath = `${collectionName}/${selectedPack.id}/${fileName}`;
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
      setUploadProgress(prev => prev ? { ...prev, message: 'Arka plan siliniyor...' } : null);
      const processedBlob = await stickerProcessor.processTray(file, (p) => {
        setUploadProgress(prev => prev ? { ...prev, message: p.message } : null);
      });

      const collectionName = selectedPack.is_premium ? 'premium_stickers' : 'stickers';
      const fileName = `tray_${Date.now()}.png`;
      const storagePath = `${collectionName}/${selectedPack.id}/${fileName}`;
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
      alert("Kapak resmi başarıyla işlendi ve güncellendi.");
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

      const storagePath = pack.is_premium ? 'premium_stickers' : 'stickers';
      const folderRef = ref(storage, `${storagePath}/${pack.id}`);

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

      const storagePath = `${pack.is_premium ? 'premium_stickers' : 'stickers'}/${pack.id}/${sticker.image_file}`;
      try { await deleteObject(ref(storage, storagePath)); } catch (e) { }

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

          <form onSubmit={handleLogin} className="space-y-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-textSec uppercase">E-posta</label>
              <div className="relative">
                <UserIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-textSec" size={18} />
                <input
                  type="email"
                  required
                  placeholder="admin@sticly.com"
                  className="w-full bg-hover border-none rounded-xl pl-10 pr-4 py-3 outline-none focus:ring-2 focus:ring-primary transition-all text-white"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-textSec uppercase">Şifre</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 text-textSec" size={18} />
                <input
                  type="password"
                  required
                  placeholder="••••••••"
                  className="w-full bg-hover border-none rounded-xl pl-10 pr-4 py-3 outline-none focus:ring-2 focus:ring-primary transition-all text-white"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-primary hover:bg-primary/90 py-3 rounded-xl font-bold text-white transition-all shadow-lg shadow-primary/20 disabled:opacity-50"
            >
              {loading ? 'Giriş Yapılıyor...' : 'Giriş Yap'}
            </button>
          </form>
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
    <div className="min-h-screen bg-background text-textMain flex flex-col font-sans">
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
      <header className="glass sticky top-0 z-20 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-5">
          <div className="w-11 h-11 bg-primary rounded-2xl flex items-center justify-center shadow-lg shadow-primary/20 transition-transform duration-500 hover:scale-110">
            <div className="w-3.5 h-3.5 bg-white rounded-full" />
          </div>
          <div className="flex flex-col">
            <h1 className="text-2xl font-black tracking-tight text-white flex items-center gap-2">
              Sticky <span className="text-primary/80">Web Admin</span>
            </h1>
            <div className="flex items-center gap-2">
              <div className="w-1.5 h-1.5 bg-primary rounded-full shadow-[0_0_8px_rgba(0,168,132,0.6)]" />
              <span className="text-[10px] text-textSec font-black uppercase tracking-[0.2em]">Sistem Çevrimiçi</span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="relative group">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-textSec group-focus-within:text-primary transition-colors" size={18} />
            <input
              type="text"
              placeholder="Paket ara..."
              className="bg-hover border-transparent border rounded-full pl-10 pr-4 py-2.5 focus:ring-2 focus:ring-primary focus:bg-background outline-none text-sm w-64 transition-all text-white"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          <button
            onClick={fetchPacks}
            className="p-2.5 hover:bg-hover rounded-xl transition-all active:scale-95"
          >
            <RefreshCcw size={20} className={cn("text-textSec", loading && 'animate-spin text-primary')} />
          </button>
          <div className="w-px h-6 bg-white/10 mx-2" />
          <button
            onClick={() => signOut(auth)}
            className="flex items-center gap-2 px-3 py-2 hover:bg-danger/10 text-textSec hover:text-danger rounded-xl transition-all"
          >
            <LogOut size={18} />
            <span className="text-sm font-semibold">Çıkış</span>
          </button>
        </div>
      </header>

      <main className="flex-1 overflow-hidden flex">
        {/* Navigation Sidebar */}
        <div className="w-16 flex flex-col items-center py-6 gap-6 border-r border-white/5 bg-card/20">
          <button
            onClick={() => setActiveTab('dashboard')}
            className={cn("p-3 rounded-2xl transition-all", activeTab === 'dashboard' ? "bg-primary text-white shadow-lg" : "text-textSec hover:bg-hover")}
            title="Dashboard"
          >
            <Grid size={24} />
          </button>
          <button
            onClick={() => setActiveTab('stats')}
            className={cn("p-3 rounded-2xl transition-all", activeTab === 'stats' ? "bg-primary text-white shadow-lg" : "text-textSec hover:bg-hover")}
            title="İstatistikler"
          >
            <BarChart3 size={24} />
          </button>
          <button
            onClick={() => setActiveTab('messages')}
            className={cn("p-3 rounded-2xl transition-all relative", activeTab === 'messages' ? "bg-primary text-white shadow-lg" : "text-textSec hover:bg-hover")}
            title="Mesajlar"
          >
            <Mail size={24} />
            {messages.filter(m => m.status === 'unread').length > 0 && (
              <span className="absolute -top-1 -right-1 bg-danger text-white text-[10px] font-bold w-5 h-5 rounded-full flex items-center justify-center">
                {messages.filter(m => m.status === 'unread').length}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('notifications')}
            className={cn("p-3 rounded-2xl transition-all shadow-inner", activeTab === 'notifications' ? "bg-primary text-white shadow-lg" : "text-textSec hover:bg-hover")}
            title="Bildirim Gönder"
          >
            <Bell size={24} />
          </button>

        </div>

        {activeTab === 'dashboard' ? (
          <>
            {/* Sidebar / Pack List */}
            <div className={cn(
              "border-r border-white/5 flex flex-col bg-card/30 transition-all duration-500",
              selectedPack ? "w-[400px]" : "w-full"
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
                <div className="p-8 border-b border-white/5 bg-card/20 backdrop-blur-xl">
                  <div className="max-w-7xl mx-auto flex items-start justify-between">
                    <div className="flex gap-8 items-center">
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
                <div className="flex-1 overflow-y-auto p-8 custom-scrollbar">
                  <div className="max-w-7xl mx-auto">
                    <div className="flex items-center justify-between mb-8">
                      <h3 className="text-xl font-bold flex items-center gap-3">
                        <Grid className="text-primary" size={24} />
                        Paket İçeriği
                        <span className="bg-white/5 px-2.5 py-1 rounded-lg text-xs font-mono ml-2">{selectedPack.sticker_count} DOSYA</span>
                      </h3>
                      <div className="flex items-center gap-4 text-xs text-textSec font-bold uppercase tracking-widest">
                        <span className="flex items-center gap-1.5"><Info size={14} /> Anlık Bulut Önizleme</span>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-7 xl:grid-cols-8 gap-5">
                      {selectedPack.stickers?.map((sticker, idx) => (
                        <div key={idx} className="group relative aspect-square bg-card/50 rounded-2xl glass p-4 hover:ring-2 hover:ring-primary/50 transition-all duration-300 shadow-lg hover:shadow-2xl hover:shadow-primary/5">
                          <div className="w-full h-full flex items-center justify-center">
                            <img
                              src={sticker.url}
                              alt=""
                              className="w-full h-full object-contain group-hover:scale-110 transition-transform duration-500"
                            />
                          </div>
                          <div className="absolute inset-0 bg-background/60 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2 rounded-2xl backdrop-blur-[2px]">
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
          <div className="flex-1 overflow-y-auto p-12 custom-scrollbar bg-background">
            <div className="max-w-6xl mx-auto space-y-12 animate-in fade-in duration-500">
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-3 mb-1">
                    <BarChart3 className="text-primary" size={28} />
                    <h2 className="text-4xl font-black text-white uppercase tracking-tighter">Performans Analizi</h2>
                  </div>
                  <p className="text-textSec font-medium">Uygulama genelindeki etkileşim ve verimlilik raporu</p>
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
              <div className="glass rounded-3xl p-4 mb-6 border border-white/10">
                <div className="flex flex-wrap items-center gap-4">
                  <span className="text-xs font-bold text-textSec uppercase">Fake İndirme Aralığı:</span>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      value={fakeBaseMin}
                      onChange={(e) => setFakeBaseMin(Number(e.target.value))}
                      className="w-28 px-3 py-2 bg-white/5 border border-white/10 rounded-xl text-white text-sm"
                      placeholder="Min"
                    />
                    <span className="text-textSec">-</span>
                    <input
                      type="number"
                      value={fakeBaseMax}
                      onChange={(e) => setFakeBaseMax(Number(e.target.value))}
                      className="w-28 px-3 py-2 bg-white/5 border border-white/10 rounded-xl text-white text-sm"
                      placeholder="Max"
                    />
                  </div>
                  <button
                    onClick={() => updateAllPacksWithFakeBase(false)}
                    disabled={isProcessing}
                    className="px-4 py-2 bg-primary/20 hover:bg-primary/30 border border-primary/30 rounded-xl text-primary text-xs font-bold transition-all active:scale-90 disabled:opacity-50"
                  >
                    {isProcessing ? 'İşleniyor...' : 'Eksiklere Ekle'}
                  </button>
                  <button
                    onClick={() => updateAllPacksWithFakeBase(true)}
                    disabled={isProcessing}
                    className="px-4 py-2 bg-warning/20 hover:bg-warning/30 border border-warning/30 rounded-xl text-warning text-xs font-bold transition-all active:scale-90 disabled:opacity-50"
                  >
                    {isProcessing ? 'İşleniyor...' : 'Tümünü Güncelle'}
                  </button>
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
                      <h3 className="text-2xl font-black text-white tracking-tight">Eğilim Analizi</h3>
                      <p className="text-textSec text-sm">En popüler 10 paketin performans karşılaştırması</p>
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
                          .map(p => ({
                            name: p.name.length > 10 ? p.name.substring(0, 8) + '..' : p.name,
                            downloads: p.download_count || 0,
                            views: p.view_count || 0
                          }))}
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
                  <div className="mb-8">
                    <h3 className="text-xl font-black text-white mb-1 uppercase tracking-tighter">🏆 Lider Tablosu</h3>
                    <p className="text-[10px] font-bold text-textSec uppercase tracking-widest">En çok indirilen ilk 5</p>
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
                            <div className="text-xs font-black text-white truncate group-hover:text-primary transition-colors">{p.name}</div>
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
                <div className="px-10 py-8 border-b border-white/5 bg-white/2 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="bg-primary p-2.5 rounded-xl">
                      <Grid className="text-white" size={20} />
                    </div>
                    <h3 className="text-2xl font-black text-white tracking-tighter uppercase">Detaylı Performans Listesi</h3>
                  </div>
                </div>
                <div className="overflow-x-auto custom-scrollbar">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-card">
                        <th className="px-10 py-5 text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5">Paket Bilgisi</th>
                        <th className="px-10 py-5 text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5 text-center">İndirme</th>
                        <th className="px-10 py-5 text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5 text-center">Görüntülenme</th>
                        <th className="px-10 py-5 text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5 text-center">Favori</th>
                        <th className="px-10 py-5 text-[10px] font-black uppercase tracking-widest text-textSec border-b border-white/5 text-right w-64">Dönüşüm Oranı (CVR)</th>
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
                            <td className="px-10 py-6">
                              <div className="flex items-center gap-5">
                                <div className="w-12 h-12 rounded-2xl bg-card border border-white/5 p-1 relative overflow-hidden group-hover:scale-110 transition-transform">
                                  <img src={p.tray_url} className="w-full h-full object-contain" />
                                  {p.is_premium && <div className="absolute top-0 right-0 w-3 h-3 bg-warning rounded-bl-lg" />}
                                </div>
                                <div className="min-w-0">
                                  <div className="font-black text-white text-base group-hover:text-primary transition-colors truncate">{p.name}</div>
                                  <div className="flex items-center gap-2 mt-1">
                                    <span className="text-[10px] font-bold text-textSec uppercase tracking-widest">{p.category}</span>
                                    <span className={cn(
                                      "px-2 py-0.5 rounded-md text-[8px] font-black tracking-widest uppercase",
                                      p.is_active !== false ? "bg-primary/20 text-primary" : "bg-danger/20 text-danger"
                                    )}>
                                      {p.is_active !== false ? 'AKTİF' : 'PASİF'}
                                    </span>
                                  </div>
                                </div>
                              </div>
                            </td>
                            <td className="px-10 py-6 text-center">
                              <span className="text-lg font-black text-primary">{(p.download_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="px-10 py-6 text-center">
                              <span className="text-lg font-black text-accent">{(p.view_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="px-10 py-6 text-center">
                              <span className="text-lg font-black text-warning">{(p.favorite_count || 0).toLocaleString()}</span>
                            </td>
                            <td className="px-10 py-6">
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
          <div className="flex-1 overflow-y-auto p-12 custom-scrollbar bg-background">
            <div className="max-w-3xl mx-auto space-y-12 animate-in fade-in duration-500">
              <div>
                <div className="flex items-center gap-4 mb-2">
                  <Bell className="text-primary" size={32} />
                  <h2 className="text-4xl font-black text-white">BİLDİRİM GÖNDER</h2>
                </div>
                <p className="text-textSec text-lg">Sticky uygulamasını kullanan tüm cihazlara anlık bildirim gönderin.</p>
              </div>

              <form onSubmit={handleSendNotification} className="space-y-8">
                <div className="glass p-10 rounded-[2.5rem] bg-gradient-to-br from-primary/5 to-transparent border border-white/5 shadow-2xl space-y-8">
                  <div className="space-y-3">
                    <label className="text-xs font-bold text-textSec uppercase tracking-widest flex items-center gap-2">
                      <Info size={14} className="text-primary" /> BİLDİRİM BAŞLIĞI
                    </label>
                    <input
                      type="text"
                      value={notifTitle}
                      onChange={(e) => setNotifTitle(e.target.value)}
                      placeholder="Sticky"
                      className="w-full bg-card/60 border border-white/10 rounded-2xl px-5 py-4 text-white font-bold outline-none focus:ring-2 focus:ring-primary focus:bg-background transition-all"
                    />
                    <p className="text-[10px] text-textSec font-medium pl-1">Bildirimde görünecek kalın başlık. Boş bırakılırsa "Sticky" yazısı görünecektir.</p>
                  </div>

                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <label className="text-xs font-bold text-textSec uppercase tracking-widest flex items-center gap-2">
                        <MessageSquare size={14} className="text-accent" /> BİLDİRİM MESAJI
                      </label>

                      {/* Emojis Grid */}
                      <div className="flex flex-wrap gap-1.5 max-w-[400px] justify-end">
                        {['😊', '😂', '❤️', '🔥', '✨', '🚀', '🎉', '🌟', '💫', '🎁', '💎', '📱', '🌈', '🎭', '🐱', '🧿', '👑', '⚡', '🔔', '💯'].map(emoji => (
                          <button
                            key={emoji}
                            type="button"
                            onClick={() => setNotifBody(prev => prev + emoji)}
                            className="w-8 h-8 flex items-center justify-center bg-card/40 hover:bg-accent/20 border border-white/5 rounded-lg text-sm transition-all hover:scale-110 active:scale-95"
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
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-4xl font-black text-white">MESAJLAR VE ÖNERİLER</h2>
                  <p className="text-textSec">Uygulama kullanıcılarından gelen iletişim talepleri</p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-xs text-green-500 flex items-center gap-1">
                    <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
                    Canlı
                  </span>
                  <button
                    onClick={clearAllMessages}
                    className="px-3 py-1.5 bg-danger/20 hover:bg-danger/30 text-danger text-xs font-bold rounded-lg transition-all"
                  >
                    Mesajları Temizle
                  </button>
                  <button
                    onClick={clearAllSuggestions}
                    className="px-3 py-1.5 bg-warning/20 hover:bg-warning/30 text-warning text-xs font-bold rounded-lg transition-all"
                  >
                    Önerileri Temizle
                  </button>
                </div>
              </div>

              {/* Sub Tabs */}
              <div className="flex bg-hover rounded-2xl p-1.5 gap-1">
                <button
                  onClick={() => setMessagesSubTab('messages')}
                  className={cn(
                    "flex-1 py-3 px-6 rounded-xl text-sm font-bold transition-all flex items-center justify-center gap-2",
                    messagesSubTab === 'messages' ? "bg-primary text-white shadow-lg" : "text-textSec hover:text-white"
                  )}
                >
                  <MessageSquare size={18} />
                  Mesajlar
                  {messages.length > 0 && (
                    <span className="bg-white/20 px-2 py-0.5 rounded-full text-xs">{messages.length}</span>
                  )}
                </button>
                <button
                  onClick={() => setMessagesSubTab('suggestions')}
                  className={cn(
                    "flex-1 py-3 px-6 rounded-xl text-sm font-bold transition-all flex items-center justify-center gap-2",
                    messagesSubTab === 'suggestions' ? "bg-warning text-background shadow-lg" : "text-textSec hover:text-white"
                  )}
                >
                  <Lightbulb size={18} />
                  Sticker Önerileri
                  {suggestions.length > 0 && (
                    <span className="bg-white/20 px-2 py-0.5 rounded-full text-xs">{suggestions.length}</span>
                  )}
                </button>
              </div>

              {/* Messages Content */}
              {messagesSubTab === 'messages' ? (
                <div className="space-y-4">
                  {messages.length === 0 ? (
                    <div className="glass rounded-[2rem] p-12 text-center">
                      <MessageSquare className="mx-auto text-textSec mb-4" size={48} />
                      <h3 className="text-xl font-bold text-white">Henüz mesaj yok</h3>
                      <p className="text-textSec mt-2">Kullanıcılar uygulamadan mesaj gönderdiğinde burada görünecek.</p>
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
                    )))}
                </div>
              )}
            </div>
          </div>
        ) : null}
      </main>

      <footer className="glass h-8 px-6 flex items-center justify-between text-[10px] font-bold text-textSec uppercase tracking-widest border-t border-white/5 fixed bottom-0 left-0 right-0 z-30">
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
              <div>
                <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                  <span>🇬🇧</span> İngilizce (Varsayılan)
                </label>
                <input
                  type="text"
                  placeholder="Funny Cats"
                  className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                  value={newPackData.name}
                  onChange={(e: any) => setNewPackData({ ...newPackData, name: e.target.value })}
                />
              </div>
              <div>
                <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                  <span>🇹🇷</span> Türkçe
                </label>
                <input
                  type="text"
                  placeholder="Komik Kediler"
                  className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                  value={newPackData.name_tr}
                  onChange={(e: any) => setNewPackData({ ...newPackData, name_tr: e.target.value })}
                />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                  <span>🇨🇳</span> Çince
                </label>
                <input
                  type="text"
                  placeholder="搞笑猫咪"
                  className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                  value={newPackData.name_zh}
                  onChange={(e: any) => setNewPackData({ ...newPackData, name_zh: e.target.value })}
                />
              </div>
              <div>
                <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                  <span>🇪🇸</span> İspanyolca
                </label>
                <input
                  type="text"
                  placeholder="Gatos Graciosos"
                  className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                  value={newPackData.name_es}
                  onChange={(e: any) => setNewPackData({ ...newPackData, name_es: e.target.value })}
                />
              </div>
              <div>
                <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                  <span>🇸🇦</span> Arapça
                </label>
                <input
                  type="text"
                  placeholder="قطط مضحكة"
                  className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30 text-right"
                  dir="rtl"
                  value={newPackData.name_ar}
                  onChange={(e: any) => setNewPackData({ ...newPackData, name_ar: e.target.value })}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                  <span>🇮🇳</span> Hintçe
                </label>
                <input
                  type="text"
                  placeholder="मज़ेदार बिल्लियाँ"
                  className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                  value={newPackData.name_hi}
                  onChange={(e: any) => setNewPackData({ ...newPackData, name_hi: e.target.value })}
                />
              </div>
              <div>
                <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                  <span>🇧🇷</span> Portekizce
                </label>
                <input
                  type="text"
                  placeholder="Gatos Engraçados"
                  className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                  value={newPackData.name_pt}
                  onChange={(e: any) => setNewPackData({ ...newPackData, name_pt: e.target.value })}
                />
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
                <div>
                  <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                    <span>🇬🇧</span> İngilizce (Varsayılan)
                  </label>
                  <input
                    type="text"
                    placeholder="Funny Cats"
                    className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                    value={editFormData.name}
                    onChange={(e: any) => setEditFormData({ ...editFormData, name: e.target.value })}
                  />
                </div>
                <div>
                  <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                    <span>🇹🇷</span> Türkçe
                  </label>
                  <input
                    type="text"
                    placeholder="Komik Kediler"
                    className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                    value={editFormData.name_tr}
                    onChange={(e: any) => setEditFormData({ ...editFormData, name_tr: e.target.value })}
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                    <span>🇨🇳</span> Çince
                  </label>
                  <input
                    type="text"
                    placeholder="搞笑猫咪"
                    className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                    value={editFormData.name_zh}
                    onChange={(e: any) => setEditFormData({ ...editFormData, name_zh: e.target.value })}
                  />
                </div>
                <div>
                  <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                    <span>🇪🇸</span> İspanyolca
                  </label>
                  <input
                    type="text"
                    placeholder="Gatos Graciosos"
                    className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                    value={editFormData.name_es}
                    onChange={(e: any) => setEditFormData({ ...editFormData, name_es: e.target.value })}
                  />
                </div>
                <div>
                  <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                    <span>🇸🇦</span> Arapça
                  </label>
                  <input
                    type="text"
                    placeholder="قطط مضحكة"
                    className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30 text-right"
                    dir="rtl"
                    value={editFormData.name_ar}
                    onChange={(e: any) => setEditFormData({ ...editFormData, name_ar: e.target.value })}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                    <span>🇮🇳</span> Hintçe
                  </label>
                  <input
                    type="text"
                    placeholder="मज़ेदार बिल्लियाँ"
                    className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                    value={editFormData.name_hi}
                    onChange={(e: any) => setEditFormData({ ...editFormData, name_hi: e.target.value })}
                  />
                </div>
                <div>
                  <label className="flex items-center gap-2 text-sm font-medium text-textSec mb-1">
                    <span>🇧🇷</span> Portekizce
                  </label>
                  <input
                    type="text"
                    placeholder="Gatos Engraçados"
                    className="w-full bg-bgSecondary border border-white/10 rounded-lg p-2.5 text-white placeholder:text-white/30"
                    value={editFormData.name_pt}
                    onChange={(e: any) => setEditFormData({ ...editFormData, name_pt: e.target.value })}
                  />
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

    </div >
  );
}

// Components
function StatCard({ label, value, color }: { label: string, value: number, color: 'primary' | 'accent' }) {
  return (
    <div className="bg-card px-5 py-3 rounded-2xl border border-white/5 flex flex-col min-w-[100px]">
      <span className="text-[10px] font-bold text-textSec uppercase tracking-widest mb-1">{label}</span>
      <span className={cn("text-2xl font-black", color === 'primary' ? 'text-primary' : 'text-accent')}>
        {(value || 0).toLocaleString()}
      </span>
    </div>
  );
}

function Modal({ show, onClose, title, children }: { show: boolean, onClose: () => void, title: string, children: React.ReactNode }) {
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
}

function Input({ label, ...props }: any) {
  return (
    <div className="space-y-2">
      <label className="text-[10px] font-black text-textSec uppercase tracking-widest px-1">{label}</label>
      <input
        className="w-full bg-hover border-transparent border focus:border-primary/50 rounded-xl px-4 py-3 outline-none transition-all placeholder:text-textSec/30 text-white"
        {...props}
      />
    </div>
  );
}

export default App;
