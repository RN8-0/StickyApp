package com.sticly

import android.content.Context
import android.net.Uri

object WhitelistCheck {
    private const val AUTHORITY_QUERY_WHATSAPP = "com.whatsapp.provider.sticker_whitelist_check"
    private const val AUTHORITY_QUERY_WHATSAPP_SMALL = "com.whatsapp.w4b.provider.sticker_whitelist_check"
    private const val STICKER_PACK_ID_QUERY_PARAM = "sticker_pack_id"
    private const val STICKER_PACK_AUTHORITY_QUERY_PARAM = "sticker_pack_authority"
    private const val QUERY_RESULT_COLUMN_NAME = "result"

    fun isWhitelisted(context: Context, identifier: String): Boolean {
        val authority = "${context.packageName}.stickers"
        
        // Check WhatsApp
        if (isWhitelistedFromAuthority(context, AUTHORITY_QUERY_WHATSAPP, identifier, authority)) {
            return true
        }
        
        // Check WhatsApp Business
        if (isWhitelistedFromAuthority(context, AUTHORITY_QUERY_WHATSAPP_SMALL, identifier, authority)) {
            return true
        }
        
        return false
    }

    private fun isWhitelistedFromAuthority(
        context: Context,
        queryAuthority: String,
        identifier: String,
        stickerPackAuthority: String
    ): Boolean {
        try {
            val packageManager = context.packageManager
            val providerInfo = packageManager.resolveContentProvider(queryAuthority, 0)
            if (providerInfo == null) {
                return false
            }
            
            val uri = Uri.Builder()
                .scheme("content")
                .authority(queryAuthority)
                .appendPath(QUERY_RESULT_COLUMN_NAME)
                .appendQueryParameter(STICKER_PACK_ID_QUERY_PARAM, identifier)
                .appendQueryParameter(STICKER_PACK_AUTHORITY_QUERY_PARAM, stickerPackAuthority)
                .build()
                
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getInt(cursor.getColumnIndexOrThrow(QUERY_RESULT_COLUMN_NAME))
                    return result == 1
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WhitelistCheck", "Error checking whitelist for $identifier: ${e.message}")
        }
        return false
    }
}
