export interface Sticker {
    image_file: string;
    url: string;
    emojis: string[];
}

export interface StickerPack {
    id: string;
    name: string;
    publisher: string;
    publisher_email: string;
    privacy_policy_website: string;
    license_agreement_website: string;
    tray_image_file: string;
    tray_url: string;
    is_animated: boolean;
    is_premium: boolean;
    category: string;
    download_count: number;
    view_count: number;
    sticker_count: number;
    image_data_version: string;
    is_active: boolean;
    stickers: Sticker[];
}
