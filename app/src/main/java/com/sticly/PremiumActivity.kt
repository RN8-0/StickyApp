package com.sticly

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PremiumActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private var billingManager: BillingManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Initialize Billing Manager
        billingManager = BillingManager(this) { isPurchased ->
            if (isPurchased) {
                Toast.makeText(this, R.string.premium_purchased, Toast.LENGTH_SHORT).show()
                updateUI()
            }
        }

        val btnBuyPremium = findViewById<MaterialButton>(R.id.btnBuyPremium)
        val btnRestorePurchases = findViewById<TextView>(R.id.btnRestorePurchases)
        val badgeCurrentPlan = findViewById<TextView>(R.id.badgeCurrentPlan)

        btnBuyPremium.setOnClickListener {
            // Sistem diline/bölgesine göre SKU seçimi
            val locale = resources.configuration.locales[0]
            val targetSku = when {
                locale.country == "TR" -> BillingManager.PREMIUM_TRY_SKU
                listOf("DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT", "FI", "GR", "IE", "SK", "SI", "EE", "LV", "LT", "MT", "CY", "LU").contains(locale.country) -> BillingManager.PREMIUM_EUR_SKU
                else -> BillingManager.PREMIUM_USD_SKU
            }
            billingManager?.launchPurchase(this, targetSku)
        }

        btnRestorePurchases.setOnClickListener {
            restorePurchases()
        }

        updateUI()
    }

    private fun updateUI() {
        val isPremium = PreferencesHelper.isPremium(this)
        val btnBuyPremium = findViewById<MaterialButton>(R.id.btnBuyPremium)
        val badgeCurrentPlan = findViewById<TextView>(R.id.badgeCurrentPlan)

        if (isPremium) {
            btnBuyPremium.visibility = View.GONE
            badgeCurrentPlan.text = getString(R.string.premium_active)
            badgeCurrentPlan.visibility = View.VISIBLE
        } else {
            btnBuyPremium.visibility = View.VISIBLE
            badgeCurrentPlan.visibility = View.VISIBLE
            badgeCurrentPlan.text = getString(R.string.free)
            
            // Dinamik fiyatı yükle - Her zaman sunucudan çekmeyi dene
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val settings = withContext(Dispatchers.IO) { 
                        // Cached version if possible, but Repository will handle it
                        StickerRepository.getGlobalBillingSettings() 
                    }
                    
                    val locale = resources.configuration.locales[0]
                    val priceValue = when {
                        locale.country == "TR" -> settings.priceTRY.replace(Regex("[^0-9,.]"), "").trim()
                        listOf("DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT", "FI", "GR", "IE", "SK", "SI", "EE", "LV", "LT", "MT", "CY", "LU").contains(locale.country) -> settings.priceEUR.replace(Regex("[^0-9,.]"), "").trim()
                        else -> settings.priceUSD.replace(Regex("[^0-9,.]"), "").trim()
                    }
                    
                    val priceFormatted = when {
                        locale.country == "TR" -> "₺$priceValue"
                        listOf("DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT", "FI", "GR", "IE", "SK", "SI", "EE", "LV", "LT", "MT", "CY", "LU").contains(locale.country) -> "€$priceValue"
                        else -> "$$priceValue"
                    }
                    
                    val txtPremiumPrice = findViewById<TextView>(R.id.txtPremiumPrice)
                    txtPremiumPrice.text = priceFormatted
                    btnBuyPremium.text = getString(R.string.buy_premium_with_price, priceFormatted)
                } catch (e: Exception) {
                    Log.e("PremiumActivity", "Price sync failed: ${e.message}")
                    findViewById<TextView>(R.id.txtPremiumPrice).text = "₺--"
                    btnBuyPremium.text = "Premium Al"
                }
            }
        }
    }

    private fun restorePurchases() {
        Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT).show()
        billingManager?.restorePurchases { result ->
            val messageRes = when (result) {
                BillingManager.RestoreResult.SUCCESS -> {
                    updateUI()
                    R.string.restore_success
                }
                BillingManager.RestoreResult.NOT_FOUND -> R.string.restore_not_found
                BillingManager.RestoreResult.ERROR -> R.string.restore_error
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager?.destroy()
    }
}
