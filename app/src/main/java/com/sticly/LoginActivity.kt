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
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

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
                10 -> "Developer error (SHA-1 mismatch?)"
                7 -> "Network error (No internet?)"
                12500 -> "Google Play Services outdated or configuration error"
                12501 -> "Sign in cancelled"
                else -> "Google Error: ${e.statusCode} - ${e.message}"
            }
            if (e.statusCode != 12501) { // User cancel is not an error to toast usually
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
            android.util.Log.e("LoginActivity", "Google sign in failed: ${e.statusCode}", e)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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

        // Pre-load data and images in background
        preloadDataInBackground()
    }

    private var isDataPreloaded = false

    /**
     * While user is on login screen, preload in background:
     * 1. Fetch packs from Firebase and cache them
     * 2. Preload images of popular packs
     * 3. Preload previews of first packs
     */
    private fun preloadDataInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Load packs from Firebase (will be cached)
                val packs = StickerRepository.loadPacks(this@LoginActivity, forceRefresh = false)

                if (packs.isNotEmpty()) {
                    // Get top 10 popular packs
                    val popularPacks = packs
                        .filter { it.isActive && it.category != "custom" }
                        .sortedByDescending { it.downloadCount }
                        .take(10)

                    // Preload popular pack images with highest priority
                    StickyGlideModule.preloadPopularPacks(this@LoginActivity, popularPacks)

                    // Preload previews of first 30 packs
                    StickyGlideModule.preloadStickerPreviews(this@LoginActivity, packs, packCount = 30, stickersPerPack = 5)
                }
                isDataPreloaded = true
            } catch (e: Exception) {
                // Allow transition even if error occurs
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
                lifecycleScope.launch {
                    val account = GoogleSignIn.getLastSignedInAccount(this@LoginActivity)
                    PreferencesHelper.setUserProfile(
                        this@LoginActivity,
                        user?.email ?: account?.email,
                        user?.displayName ?: account?.displayName,
                        user?.photoUrl?.toString() ?: account?.photoUrl?.toString()
                    )

                    try {
                        withContext(Dispatchers.IO) {
                            val authResult = PocketBaseHelper.authWithOAuth("google", idToken)
                            PreferencesHelper.setPocketBaseAuth(
                                this@LoginActivity,
                                authResult.optString("token").takeIf { it.isNotBlank() },
                                authResult.optJSONObject("record")?.optString("id")?.takeIf { it.isNotBlank() }
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("LoginActivity", "PocketBase Google auth skipped: ${e.message}")
                    }

                    if (user != null) {
                        PreferencesHelper.syncUserData(this@LoginActivity, user.uid)
                    } else {
                        val deviceId = PreferencesHelper.getDeviceId(this@LoginActivity)
                        PreferencesHelper.syncUserDataWithPocketBase(this@LoginActivity, deviceId)
                    }
                    startMainActivity()
                }
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
