package com.sticly

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView

class OnboardingAdapter(
    private val onFinish: () -> Unit,
    private val onPurchase: (Int) -> Unit,
    private val getPriceForPlan: ((Int) -> String?)? = null,
    private val getOldPriceForPlan: ((Int) -> String?)? = null // Artık kullanılmıyor ama uyumluluk için tutuldu
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_INTRO = 0
        private const val TYPE_PREMIUM = 1
    }

    private var premiumHolder: PremiumViewHolder? = null

    override fun getItemViewType(position: Int): Int {
        return TYPE_INTRO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_INTRO) {
            IntroViewHolder(inflater.inflate(R.layout.item_onboarding, parent, false))
        } else {
            PremiumViewHolder(inflater.inflate(R.layout.item_onboarding_premium, parent, false)).also {
                premiumHolder = it
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        if (holder is IntroViewHolder) {
            val (imageRes, titleRes, descRes) = when(position) {
                0 -> Triple(R.drawable.onboarding_3d_welcome, R.string.onboarding_1_title, R.string.onboarding_1_desc)
                1 -> Triple(R.drawable.onboarding_3d_create, R.string.onboarding_2_title, R.string.onboarding_2_desc)
                2 -> Triple(R.drawable.onboarding_3d_notify, R.string.onboarding_3_title, R.string.onboarding_3_desc)
                else -> Triple(0, 0, 0)
            }
            if (imageRes != 0) {
                holder.bind(imageRes, context.getString(titleRes), context.getString(descRes), position)
            }
        } else if (holder is PremiumViewHolder) {
            holder.setup(onFinish, onPurchase, getPriceForPlan)
        }
    }

    override fun getItemCount(): Int = 3

    fun updatePrices() {
        premiumHolder?.updatePrices(getPriceForPlan)
    }

    class IntroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgOnboardingIcon)
        val lottie: LottieAnimationView = view.findViewById(R.id.lottieOnboarding)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val desc: TextView = view.findViewById(R.id.tvDesc)
        val brandContainer: View = view.findViewById(R.id.stickyBrandContainer)

        fun bind(imageRes: Int, titleStr: String, descStr: String, position: Int) {
            title.text = titleStr
            desc.text = descStr

            when (position) {
                0 -> {
                    brandContainer.visibility = View.VISIBLE
                    icon.visibility = View.GONE
                    lottie.visibility = View.VISIBLE
                    lottie.setAnimation("onboarding_welcome.json")
                    lottie.repeatCount = 0
                    lottie.playAnimation()
                    lottie.translationY = -120f
                }
                1 -> {
                    brandContainer.visibility = View.GONE
                    icon.visibility = View.GONE
                    lottie.visibility = View.VISIBLE
                    lottie.setAnimation("onboarding_create.json")
                    lottie.repeatCount = 0
                    lottie.playAnimation()
                    lottie.translationY = 0f
                }
                2 -> {
                    brandContainer.visibility = View.GONE
                    icon.visibility = View.GONE
                    lottie.visibility = View.VISIBLE
                    lottie.setAnimation("onboarding_notify.json")
                    lottie.repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
                    lottie.playAnimation()
                    lottie.translationY = 0f
                }
                else -> {
                    brandContainer.visibility = View.GONE
                    icon.visibility = View.VISIBLE
                    lottie.visibility = View.GONE
                    icon.setImageResource(imageRes)
                    icon.translationY = 0f
                }
            }

            icon.animate().cancel()
        }
    }

    class PremiumViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        fun setup(onFinish: () -> Unit, onPurchase: (Int) -> Unit, getPriceForPlan: ((Int) -> String?)?) {
            setupComparisonRows()
            setupPlanCards(onPurchase, getPriceForPlan)

            itemView.findViewById<View>(R.id.btnPremiumCTA).setOnClickListener {
                onFinish()
            }
            itemView.findViewById<View>(R.id.btnFreeCTA).setOnClickListener {
                onFinish()
            }

            // Privacy Policy link
            itemView.findViewById<View>(R.id.btnPrivacyPolicy)?.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sticky-privacy-legal.web.app/#privacy"))
                itemView.context.startActivity(intent)
            }
        }

        fun updatePrices(getPriceForPlan: ((Int) -> String?)?) {
            val context = itemView.context
            val loadingText = context.getString(R.string.price_loading)
            val monthPeriod = context.getString(R.string.period_month)
            val yearPeriod = context.getString(R.string.period_year)

            // Aylık - /ay formatında
            val monthlyPrice = getPriceForPlan?.invoke(0)
            itemView.findViewById<View>(R.id.cardMonthly)?.findViewById<TextView>(R.id.tvPlanPrice)?.text =
                if (monthlyPrice != null) "$monthlyPrice/$monthPeriod" else loadingText

            // Yıllık - /yıl formatında
            val cardYearly = itemView.findViewById<View>(R.id.cardYearly)
            val yearlyPrice = getPriceForPlan?.invoke(1)
            cardYearly?.findViewById<TextView>(R.id.tvPlanPrice)?.text =
                if (yearlyPrice != null) "$yearlyPrice/$yearPeriod" else loadingText
            // Üstü çizili eski fiyat kaldırıldı
            cardYearly?.findViewById<TextView>(R.id.tvOldPrice)?.visibility = View.GONE

        }

        private fun setupComparisonRows() {
            setupComparisonRow(itemView.findViewById(R.id.rowAds), R.string.premium_ads, R.string.var_label, R.string.yok_label, false)
            setupComparisonRow(itemView.findViewById(R.id.rowPacks), R.string.premium_sticker_packs, R.string.kilitli_label, R.string.acik_label)
            setupComparisonRow(itemView.findViewById(R.id.rowAI), R.string.premium_ai_removal, R.string.acik_label, R.string.acik_label)
            setupComparisonRow(itemView.findViewById(R.id.rowCreate), R.string.priority_support, R.string.acik_label, R.string.acik_label)
        }

        private fun setupComparisonRow(row: View, nameRes: Int, freeRes: Int, premRes: Int, useCheck: Boolean = false) {
            row.findViewById<TextView>(R.id.tvFeatureName).setText(nameRes)
            row.findViewById<TextView>(R.id.tvFreeValue).setText(freeRes)

            val tvPrem = row.findViewById<TextView>(R.id.tvPremiumValue)
            val imgCheck = row.findViewById<ImageView>(R.id.imgPremiumCheck)

            if (useCheck) {
                tvPrem.visibility = View.GONE
                imgCheck.visibility = View.VISIBLE
            } else {
                tvPrem.visibility = View.VISIBLE
                imgCheck.visibility = View.GONE
                tvPrem.setText(premRes)
            }
        }

        private fun setupPlanCards(onPurchase: (Int) -> Unit, getPriceForPlan: ((Int) -> String?)?) {
            val context = itemView.context
            val cardMonthly = itemView.findViewById<View>(R.id.cardMonthly)
            val cardYearly = itemView.findViewById<View>(R.id.cardYearly)

            val monthPeriod = context.getString(R.string.period_month)
            val yearPeriod = context.getString(R.string.period_year)

            // Aylık Plan
            val monthlyPrice = getPriceForPlan?.invoke(0)
            setupPlanCard(
                card = cardMonthly,
                icon = "📅",
                name = context.getString(R.string.plan_monthly).replace("📅 ", ""),
                subtitle = context.getString(R.string.plan_monthly_subtitle),
                badge = "🎁 " + context.getString(R.string.plan_monthly_trial),
                priceNote = context.getString(R.string.plan_monthly_note),
                price = if (monthlyPrice != null) "$monthlyPrice/$monthPeriod" else null,
                onClick = { onPurchase(0) }
            )

            // Yıllık Plan
            val yearlyPrice = getPriceForPlan?.invoke(1)
            setupPlanCard(
                card = cardYearly,
                icon = "⭐",
                name = context.getString(R.string.plan_yearly).replace("⭐ ", ""),
                subtitle = context.getString(R.string.plan_yearly_subtitle),
                badge = "🏆 " + context.getString(R.string.save_percentage),
                priceNote = context.getString(R.string.plan_yearly_note),
                price = if (yearlyPrice != null) "$yearlyPrice/$yearPeriod" else null,
                onClick = { onPurchase(1) }
            )

        }

        private fun setupPlanCard(
            card: View,
            icon: String,
            name: String,
            subtitle: String,
            badge: String,
            priceNote: String,
            price: String?,
            onClick: () -> Unit
        ) {
            val context = card.context
            card.findViewById<TextView>(R.id.tvPlanIcon).text = icon
            card.findViewById<TextView>(R.id.tvPlanName).text = name
            card.findViewById<TextView>(R.id.tvPlanPrice).text = price ?: context.getString(R.string.price_loading)

            // Alt açıklama
            card.findViewById<TextView>(R.id.tvPlanSubtitle)?.text = subtitle

            // Fiyat altı notu
            card.findViewById<TextView>(R.id.tvPriceNote)?.apply {
                text = priceNote
                visibility = if (priceNote.isNotEmpty()) View.VISIBLE else View.GONE
            }

            // Üstü çizili eski fiyat kaldırıldı
            card.findViewById<TextView>(R.id.tvOldPrice)?.visibility = View.GONE

            val badgeView = card.findViewById<TextView>(R.id.tvBadge)
            if (badge.isNotEmpty()) {
                badgeView.text = badge
                badgeView.visibility = View.VISIBLE
            } else {
                badgeView.visibility = View.GONE
            }

            card.setOnClickListener { onClick() }
        }
    }
}
