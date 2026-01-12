package com.sticly
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class StickerAdapter(private val packId: String, private val items: List<Sticker>) :
    RecyclerView.Adapter<StickerAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.img)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_sticker, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val sticker = items[pos]
        try {
            val path = "$packId/${sticker.file}"
            val stream = h.itemView.context.assets.open(path)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            h.img.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount() = items.size
}
