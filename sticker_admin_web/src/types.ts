export interface Sticker {
    image_file: string;
    url: string;
    emojis: string[];
}

export interface StickerPack {
    id: string;
    name: string;
    name_tr: string;
    name_zh: string;
    name_es: string;
    name_ar: string;
    name_hi: string;
    name_pt: string;
    [key: `name_${string}`]: string | any; // Tüm dil kodlarını desteklemek için
    publisher: string;
    publisher_email: string;
    privacy_policy_website: string;
    license_agreement_website: string;
    tray_image_file: string;
    tray_url: string;
    is_animated: boolean;
    is_premium: boolean;
    product_id?: string;
    category: string;
    download_count: number;
    fake_download_base: number;
    view_count: number;
    favorite_count: number;
    sticker_count: number;
    image_data_version: string;
    is_active: boolean;
    is_popular: boolean;
    stickers: Sticker[];
    created_at?: any;
    batch_source?: string;
    batch_generated?: boolean;
}

export interface ContactMessage {
    id: string;
    name: string;
    email: string;
    subject: string;
    message: string;
    timestamp: number;
    date: string;
    time: string;
    status: 'read' | 'unread';
}

export interface StickerSuggestion {
    id: string;
    suggestion: string;
    timestamp: number;
    date: string;
    time: string;
}

export interface UserData {
    id: string;           // Firestore document ID (uid)
    email: string;
    is_premium: boolean;
    premium_type: string; // "monthly" | "yearly" | "lifetime" | "none"
    premium_expiry: number;
    favorite_packs: string[];
    last_sync: any;
    cancelled_at?: any;
    cancelled_reason?: string;
    subscription_source?: 'google_play' | 'admin' | 'none';
    subscription_history?: SubscriptionHistoryItem[];
    // New fields
    created_at?: any;     // Registration date/time
    display_name?: string;
    photo_url?: string;
    device_info?: {
        model?: string;
        os_version?: string;
        app_version?: string;
        language?: string;
    };
    total_stickers_added?: number;
    custom_packs_count?: number;
}

export interface SubscriptionHistoryItem {
    id: string; // unique event id
    type: 'start' | 'renew' | 'cancel' | 'expire';
    plan: 'monthly' | 'yearly' | 'none';
    source: 'google_play' | 'admin' | 'system';
    timestamp: number;
    date_str: string;
    details?: string;
}

export interface PublisherUser {
    id: string;
    display_name: string;
    avatar_url: string;
    bio: string;
    category: string;
    packs_published: number;
    total_downloads: number;
    created_at: any;
    is_active: boolean;
}

export interface UserSubmission {
    id: string;
    user_id: string;
    user_email: string;
    display_name: string;
    pack_name: string;
    category: string;
    stickers: Array<{ name: string; image_url: string }>;
    status: 'pending' | 'processing' | 'approved' | 'flagged' | 'rejected' | 'error';
    flag_reasons?: string[];
    created_at: any;
    processed_at?: any;
}
