package com.sticly
import android.content.Context
import com.google.gson.Gson

object Loader {
    fun load(c: Context): List<Pack> = try {
        val json = c.assets.open("contents.json").bufferedReader().use { it.readText() }
        Gson().fromJson(json, Response::class.java).packs
    } catch (e: Exception) { emptyList() }
    
    fun get(c: Context, id: String) = load(c).find { it.id == id }
}
