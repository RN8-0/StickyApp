package com.sticly
import android.content.Context
import com.google.gson.Gson

object Loader {
    private var cachedLocalPacks: List<Pack>? = null

    fun load(c: Context): List<Pack> {
        cachedLocalPacks?.let { return it }
        
        return try {
            val json = c.assets.open("contents.json").bufferedReader().use { it.readText() }
            val packs = Gson().fromJson(json, Response::class.java).packs
            cachedLocalPacks = packs
            packs
        } catch (e: Exception) { emptyList() }
    }
    
    fun get(c: Context, id: String) = load(c).find { it.id == id }
}
