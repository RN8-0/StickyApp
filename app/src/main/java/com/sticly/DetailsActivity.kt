package com.sticly
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DetailsActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_details)
        val id = intent.getStringExtra("id") ?: return finish()
        val pack = Loader.get(this, id) ?: return finish()
        
        findViewById<TextView>(R.id.name).text = pack.name
        findViewById<TextView>(R.id.pub).text = pack.pub
        
        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.layoutManager = GridLayoutManager(this, 3)
        rv.adapter = StickerAdapter(pack.id, pack.stickers)
        
        findViewById<Button>(R.id.btn).setOnClickListener {
            try {
                val i = Intent().apply {
                    action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                    putExtra("sticker_pack_id", pack.id)
                    putExtra("sticker_pack_authority", "$packageName.stickers")
                    putExtra("sticker_pack_name", pack.name)
                }
                startActivityForResult(i, 200)
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 200 && res == Activity.RESULT_OK) {
            Toast.makeText(this, "Added to WhatsApp!", Toast.LENGTH_SHORT).show()
        }
    }
}
