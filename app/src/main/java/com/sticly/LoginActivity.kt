package com.sticly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val auth = FirebaseAuth.getInstance()
    private var loadingOverlay: View? = null

    private val googleSignInLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        setLoading(false)
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val idToken = account.idToken
            if (idToken != null) {
                firebaseAuthWithGoogle(idToken)
            } else {
                Toast.makeText(this, "Google Error: ID Token is null", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            val progress = result.resultCode
            val msg = when (e.statusCode) {
                10 -> "Geliştirici hatası (SHA-1 mismatch?)"
                7 -> "Ağ hatası (İnternet yok?)"
                12500 -> "Google Play Hizmetleri güncel değil veya yapılandırma hatası"
                12501 -> "Giriş iptal edildi"
                else -> "Google Error: ${e.statusCode} - ${e.message}"
            }
            if (e.statusCode != 12501) { // User cancel is not an error to toast usually
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
            android.util.Log.e("LoginActivity", "Google sign in failed: ${e.statusCode}", e)
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        loadingOverlay = findViewById(R.id.loadingOverlay)

        // Pre-initialize Google client
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupListeners()

        // Arka planda verileri ve görselleri önceden yükle
        preloadDataInBackground()
    }

    private var isDataPreloaded = false

    /**
     * Kullanıcı login ekranındayken arka planda:
     * 1. Firebase'den paketleri çek ve önbelleğe al
     * 2. Popüler paketlerin görsellerini preload et
     * 3. İlk paketlerin önizlemelerini preload et
     * 
     * ÖNEMLI: Bu işlem TAMAMLANANA KADAR MainActivity'ye geçiş yapılmaz
     */
    private fun preloadDataInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Firebase'den paketleri yükle (önbelleğe alınacak)
                val packs = StickerRepository.loadPacks(this@LoginActivity, forceRefresh = false)

                if (packs.isNotEmpty()) {
                    // En popüler 10 paketi al
                    val popularPacks = packs
                        .filter { it.isActive && it.category != "custom" }
                        .sortedByDescending { it.downloadCount }
                        .take(10)

                    // Popüler paketlerin görsellerini EN YÜKSEK öncelikle preload et
                    StickyGlideModule.preloadPopularPacks(this@LoginActivity, popularPacks)

                    // İlk 30 paketin önizlemelerini preload et (daha fazla)
                    StickyGlideModule.preloadStickerPreviews(this@LoginActivity, packs, packCount = 30, stickersPerPack = 5)
                }
                isDataPreloaded = true
            } catch (e: Exception) {
                // Hata olursa yine de geçişe izin ver
                android.util.Log.e("LoginActivity", "Background preload error: ${e.message}")
                isDataPreloaded = true
            }
        }
    }

    private fun setLoading(show: Boolean) {
        loadingOverlay?.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnGoogleLogin).isEnabled = !show
        findViewById<View>(R.id.btnSkipLogin).isEnabled = !show
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnGoogleLogin).setOnClickListener {
            loginWithGoogle()
        }

        findViewById<View>(R.id.btnSkipLogin).setOnClickListener {
            startMainActivity()
        }
    }

    private fun loginWithGoogle() {
        setLoading(true)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        setLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user != null) {
                    PreferencesHelper.syncUserDataWithFirebase(this, user.uid)
                }
                startMainActivity()
            } else {
                setLoading(false)
                Toast.makeText(this, "Firebase Auth failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
