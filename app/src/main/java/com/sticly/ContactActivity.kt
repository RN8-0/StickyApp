package com.sticly

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var inputName: TextInputEditText
    private lateinit var inputEmail: TextInputEditText
    private lateinit var inputSubject: TextInputEditText
    private lateinit var inputMessage: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact)

        // Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Views
        inputName = findViewById(R.id.inputName)
        inputEmail = findViewById(R.id.inputEmail)
        inputSubject = findViewById(R.id.inputSubject)
        inputMessage = findViewById(R.id.inputMessage)
        btnSend = findViewById(R.id.btnSend)
        progressBar = findViewById(R.id.progressBar)

        btnSend.setOnClickListener {
            validateAndSend()
        }
        setupEdgeToEdge()
    }

    private fun setupEdgeToEdge() {
        val root = findViewById<android.view.View>(R.id.contact_root)
        val toolbar = findViewById<android.view.View>(R.id.toolbar)
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            toolbar?.setPadding(toolbar.paddingLeft, systemBars.top, toolbar.paddingRight, toolbar.paddingBottom)
            root?.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, systemBars.bottom)
            
            insets
        }
    }

    private fun validateAndSend() {
        val name = inputName.text?.toString()?.trim() ?: ""
        val email = inputEmail.text?.toString()?.trim() ?: ""
        val subject = inputSubject.text?.toString()?.trim() ?: ""
        val message = inputMessage.text?.toString()?.trim() ?: ""

        // Validation
        if (name.isEmpty() || email.isEmpty() || subject.isEmpty() || message.isEmpty()) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.invalid_email, Toast.LENGTH_SHORT).show()
            return
        }

        // Send to Firebase
        sendMessage(name, email, subject, message)
    }

    private fun sendMessage(name: String, email: String, subject: String, message: String) {
        // Show loading
        btnSend.isEnabled = false
        btnSend.text = getString(R.string.sending)
        progressBar.visibility = View.VISIBLE

        val db = FirebaseFirestore.getInstance()
        val now = Date()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("tr", "TR"))
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR"))

        val data = hashMapOf(
            "name" to name,
            "email" to email,
            "subject" to subject,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "date" to dateFormat.format(now),
            "time" to timeFormat.format(now),
            "status" to "unread"
        )

        db.collection("messages")
            .add(data)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, R.string.message_sent, Toast.LENGTH_LONG).show()

                // Clear fields
                inputName.text?.clear()
                inputEmail.text?.clear()
                inputSubject.text?.clear()
                inputMessage.text?.clear()

                // Go back
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSend.isEnabled = true
                btnSend.text = getString(R.string.send)
                // Hatayı logla ve göster
                android.util.Log.e("ContactActivity", "Mesaj gönderilemedi: ${e.message}", e)
                Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
