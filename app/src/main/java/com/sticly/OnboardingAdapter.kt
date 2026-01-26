package com.sticly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sticly.R

class OnboardingAdapter(private val onFinish: () -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_INTRO = 0
        private const val TYPE_PREMIUM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < 3) TYPE_INTRO else TYPE_PREMIUM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_INTRO) {
            IntroViewHolder(inflater.inflate(R.layout.item_onboarding, parent, false))
        } else {
            PremiumViewHolder(inflater.inflate(R.layout.item_onboarding_premium, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        if (holder is IntroViewHolder) {
            val (imageRes, titleRes, descRes) = when(position) {
                0 -> Triple(R.drawable.emoji_3d_laughing, R.string.onboarding_1_title, R.string.onboarding_1_desc)
                1 -> Triple(R.drawable.emoji_3d_palette, R.string.onboarding_2_title, R.string.onboarding_2_desc)
                2 -> Triple(R.drawable.emoji_3d_bell, R.string.onboarding_3_title, R.string.onboarding_3_desc)
                else -> Triple(0, 0, 0)
            }
            if (imageRes != 0) {
                holder.bind(imageRes, context.getString(titleRes), context.getString(descRes), position)
            }
        } else if (holder is PremiumViewHolder) {
            holder.setup(onFinish)
        }
    }

    override fun getItemCount(): Int = 4

    class IntroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgOnboardingIcon)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val desc: TextView = view.findViewById(R.id.tvDesc)
        val brandContainer: View = view.findViewById(R.id.stickyBrandContainer)

        fun bind(imageRes: Int, titleStr: String, descStr: String, position: Int) {
            icon.setImageResource(imageRes)
            title.text = titleStr
            desc.text = descStr

            if (position == 0) {
                brandContainer.visibility = View.VISIBLE
                icon.translationY = -60f 
            } else {
                brandContainer.visibility = View.GONE
                icon.translationY = 0f
            }
            
            // ANIMATIONS REMOVED as requested. Static and clean.
            icon.animate().cancel()
        }
    }

    class PremiumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun setup(onFinish: () -> Unit) {
            setupRow(itemView.findViewById(R.id.rowAds), R.string.ad_free, R.string.ads_limited_value, R.string.ads_free_value)
            setupRow(itemView.findViewById(R.id.rowPacks), R.string.premium_sticker_packs, R.string.basic_packs, R.string.all_packs)
            setupRow(itemView.findViewById(R.id.rowAI), R.string.ai_background, R.string.unlimited, R.string.unlimited)
            setupRow(itemView.findViewById(R.id.rowSupport), R.string.lifetime_access, R.string.no, R.string.yes)

            itemView.findViewById<View>(R.id.btnPremiumCTA).setOnClickListener {
                onFinish()
            }
            itemView.findViewById<View>(R.id.btnFreeCTA).setOnClickListener {
                onFinish()
            }
        }

        private fun setupRow(row: View, name: Int, free: Int, premium: Int) {
            row.findViewById<TextView>(R.id.tvFeatureName).setText(name)
            row.findViewById<TextView>(R.id.tvFreeValue).setText(free)
            row.findViewById<TextView>(R.id.tvPremiumValue).setText(premium)
        }
    }
}
