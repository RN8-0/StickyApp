package com.sticly
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.layoutManager = GridLayoutManager(this, 2)
        val packs = Loader.load(this)
        rv.adapter = PackAdapter(packs) {
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("id", it.id))
        }
    }
}
