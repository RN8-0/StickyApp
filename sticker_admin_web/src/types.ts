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
    publisher_user_id?: string;
    publisher_photo_url?: string;
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
    like_count?: number;
    comment_count?: number;
    engagement_score?: number;
    sticker_count: number;
    image_data_version: string;
    is_active: boolean;
    is_popular: boolean;
    stickers: Sticker[];
    created_at?: any;
    batch_source?: string;
    source?: string;
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
    id: string;           // PB record ID
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
    bio?: string;
    show_email?: boolean;
    social?: {
        followers: number;
        following: number;
        comments: number;
        likes: number;
        published_packs: number;
    };
    followers_list?: Array<{ id?: string; email?: string; name?: string; photo_url?: string }>;
    following_list?: Array<{ id?: string; email?: string; name?: string; photo_url?: string }>;
    published_packs?: Array<{
        id: string;
        name: string;
        publisher?: string;
        sticker_count?: number;
        download_count: number;
        favorite_count: number;
        like_count: number;
        comment_count: number;
        engagement_score?: number;
    }>;
    recent_comments?: Array<{
        id: string;
        pack_id: string;
        body: string;
        created_at?: any;
    }>;
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
    device_id?: string;
    user_email: string;
    display_name: string;
    publisher_name?: string;
    pack_name: string;
    description?: string;
    category: string;
    stickers: Array<{ name: string; image_url: string }>;
    status: 'pending' | 'processing' | 'approved' | 'flagged' | 'rejected' | 'error';
    flag_reasons?: string[];
    rejection_reason?: string;
    sticker_count?: number;
    sticker_pack_id?: string;
    created_at: any;
    processed_at?: any;
    approved_at?: any;
    auto_approved?: boolean;
    note?: string;
}
