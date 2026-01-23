package com.sticly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class TopStickerAdapter(
    private val onClick: (Pack) -> Unit
) : RecyclerView.Adapter<TopStickerAdapter.ViewHolder>() {

    private var packs: List<Pack> = emptyList()

    fun updatePacks(newPacks: List<Pack>) {
        packs = newPacks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carousel_sticker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(packs[position])
    }

    override fun getItemCount(): Int = packs.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgTray: ImageView = itemView.findViewById(R.id.id_imgTray_transparent)
        private val txtName: TextView = itemView.findViewById(R.id.txtName)
        private val txtCount: TextView = itemView.findViewById(R.id.txtCount)
        private val premiumBadge: View = itemView.findViewById(R.id.premiumBadge)

        fun bind(pack: Pack) {
            txtName.text = pack.localizedName
            txtCount.text = itemView.context.getString(R.string.sticker_count, pack.stickers.size)
            premiumBadge.visibility = if (pack.isPremium) View.VISIBLE else View.GONE

            Glide.with(itemView.context)
                .load(pack.trayUrl.ifEmpty { pack.tray })
                .into(imgTray)

            itemView.setOnClickListener { onClick(pack) }
        }
    }
}
