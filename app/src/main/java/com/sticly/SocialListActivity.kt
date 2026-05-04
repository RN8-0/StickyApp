package com.sticly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

class SocialListActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.app_name) }
        val items = JSONArray(intent.getStringExtra(EXTRA_ITEMS).orEmpty().ifBlank { "[]" })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@SocialListActivity, R.color.modern_background))
        }
        val toolbar = MaterialToolbar(this).apply {
            this.title = title
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
            setTitleTextColor(ContextCompat.getColor(this@SocialListActivity, R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@SocialListActivity, R.color.toolbar_bg))
        }
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp()))

        if (items.length() == 0) {
            root.addView(TextView(this).apply {
                text = getString(R.string.social_list_empty)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@SocialListActivity, R.color.text_secondary))
                gravity = android.view.Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            val list = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@SocialListActivity)
                adapter = SocialAdapter(items)
                setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
                clipToPadding = false
            }
            root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        setContentView(root)
    }

    private inner class SocialAdapter(private val items: JSONArray) : RecyclerView.Adapter<SocialAdapter.VH>() {
        inner class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
            val avatar: ImageView = card.findViewWithTag("avatar")
            val name: TextView = card.findViewWithTag("name")
            val subtitle: TextView = card.findViewWithTag("subtitle")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            }
            val avatar = ImageView(parent.context).apply {
                tag = "avatar"
                setImageResource(R.drawable.ic_person)
                background = ContextCompat.getDrawable(parent.context, R.drawable.bg_category_unselected)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            row.addView(avatar, LinearLayout.LayoutParams(54.dp(), 54.dp()))
            val texts = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 0, 0, 0)
            }
            texts.addView(TextView(parent.context).apply {
                tag = "name"
                textSize = 15f
                setTextColor(ContextCompat.getColor(parent.context, R.color.text_primary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            texts.addView(TextView(parent.context).apply {
                tag = "subtitle"
                textSize = 12f
                setTextColor(ContextCompat.getColor(parent.context, R.color.text_secondary))
            })
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val card = MaterialCardView(parent.context).apply {
                radius = 18.dp().toFloat()
                cardElevation = 3.dp().toFloat()
                setCardBackgroundColor(ContextCompat.getColor(parent.context, R.color.surface))
                strokeColor = ContextCompat.getColor(parent.context, R.color.primary_light)
                strokeWidth = 1.dp()
                addView(row)
            }
            card.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10.dp() }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items.optJSONObject(position) ?: JSONObject()
            holder.name.text = item.optString("name", getString(R.string.app_name))
            holder.subtitle.text = item.optString("subtitle")
            val photo = item.optString("photo")
            if (photo.isNotBlank()) Glide.with(holder.avatar).load(photo).circleCrop().placeholder(R.drawable.ic_person).into(holder.avatar) else holder.avatar.setImageResource(R.drawable.ic_person)
            holder.card.setOnClickListener {
                if (item.optString("type") == "pack") {
                    startActivity(Intent(this@SocialListActivity, DetailsActivity::class.java).putExtra("id", item.optString("id")))
                } else {
                    startActivity(Intent(this@SocialListActivity, PublisherProfileActivity::class.java).apply {
                        putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_ID, item.optString("id"))
                        putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_NAME, item.optString("name"))
                        putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_EMAIL, item.optString("email"))
                        putExtra(PublisherProfileActivity.EXTRA_PUBLISHER_PHOTO, item.optString("photo"))
                    })
                }
            }
        }

        override fun getItemCount(): Int = items.length()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TITLE = "social_title"
        const val EXTRA_ITEMS = "social_items"
    }
}