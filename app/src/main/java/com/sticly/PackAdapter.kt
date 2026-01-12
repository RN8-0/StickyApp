package com.sticly
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PackAdapter(private val items: List<Pack>, private val click: (Pack) -> Unit) :
    RecyclerView.Adapter<PackAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tray: ImageView = v.findViewById(R.id.tray)
        val name: TextView = v.findViewById(R.id.name)
        val pub: TextView = v.findViewById(R.id.pub)
        val count: TextView = v.findViewById(R.id.count)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_pack, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val pack = items[pos]
        h.name.text = pack.name
        h.pub.text = pack.pub
        h.count.text = "${pack.stickers.size} stickers"
        h.itemView.setOnClickListener { click(pack) }

        try {
            val path = "${pack.id}/${pack.tray}"
            val stream = h.itemView.context.assets.open(path)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            h.tray.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount() = items.size
}
