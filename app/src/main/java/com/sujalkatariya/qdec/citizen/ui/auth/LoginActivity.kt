package com.sujalkatariya.qdec.citizen.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.sujalkatariya.qdec.citizen.R
import com.sujalkatariya.qdec.citizen.databinding.ActivityLoginBinding
import com.sujalkatariya.qdec.citizen.ui.home.HomeActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth

    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // 🔥 Google config
        val options = GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, options)

        // 🔥 FORCE LOGOUT (IMPORTANT 🔐)
        auth.signOut()
        googleSignInClient.signOut()

        // 🔥 Button click
        binding.btnGoogle.setOnClickListener {
            signIn()
        }
    }

    // ---------------------------------------------------
    // GOOGLE SIGN IN
    // ---------------------------------------------------

    private fun signIn() {
        val intent = googleSignInClient.signInIntent
        startActivityForResult(intent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuth(account.idToken!!)
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------
    // FIREBASE AUTH
    // ---------------------------------------------------

    private fun firebaseAuth(idToken: String) {

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {

                    val user = auth.currentUser

                    Toast.makeText(
                        this,
                        "Welcome ${user?.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 🔥 Go to main screen
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()

                } else {

                    Toast.makeText(
                        this,
                        "Authentication Failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}