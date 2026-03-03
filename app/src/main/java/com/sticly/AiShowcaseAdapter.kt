package com.sticly

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

data class ShowcaseItem(val id: String, val prompt: String)

class AiShowcaseAdapter(
    private val context: Context,
    private var items: List<ShowcaseItem>,
    private val onClick: (ShowcaseItem) -> Unit
) : RecyclerView.Adapter<AiShowcaseAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val iv: ImageView = view.findViewById(R.id.ivShowcase)
        init {
            view.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < items.size) onClick(items[pos])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_showcase, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        Glide.with(context)
            .load(android.net.Uri.parse("file:///android_asset/ai_showcase/${item.id}.webp"))
            .override(200)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(R.drawable.ic_ai_robot)
            .into(holder.iv)
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        Glide.with(context).clear(holder.iv)
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ShowcaseItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
