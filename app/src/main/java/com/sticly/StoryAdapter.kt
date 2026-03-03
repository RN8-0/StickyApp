package com.sticly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class StoryAdapter(
    private var packs: List<Pack>,
    private val onClick: (Pack) -> Unit
) : RecyclerView.Adapter<StoryAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.storyImage)
        val name: TextView = v.findViewById(R.id.storyName)
        init {
            v.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < packs.size) onClick(packs[pos])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_story_circle, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pack = packs[position]
        holder.name.text = pack.localizedName

        val firstStickerUrl = pack.stickers.firstOrNull()?.url?.ifEmpty { null }
        val imageUrl = firstStickerUrl ?: pack.trayUrl.ifEmpty { null }
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(holder.image.context)
                .load(imageUrl)
                .override(STORY_SIZE)
                .thumbnail(0.25f)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(holder.image)
        }
    }

    override fun getItemCount(): Int = packs.size

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        Glide.with(holder.image.context).clear(holder.image)
    }

    fun updateData(newPacks: List<Pack>) {
        packs = newPacks
        notifyDataSetChanged()
    }

    companion object {
        private const val STORY_SIZE = 160
    }
}
