package com.sujalkatariya.qdec2.citizen.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.sujalkatariya.qdec2.citizen.R
import com.sujalkatariya.qdec2.citizen.databinding.ActivityLoginBinding
import com.sujalkatariya.qdec2.citizen.ui.home.HomeActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth

    // ✅ NEW RESULT API (no deprecated method)
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            Log.d("LOGIN_DEBUG", "Result received")

            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(ApiException::class.java)

                Log.d("LOGIN_DEBUG", "Email: ${account.email}")
                Log.d("LOGIN_DEBUG", "ID Token: ${account.idToken}")

                if (account.idToken == null) {
                    Log.e("LOGIN_ERROR", "ID Token NULL ❌")
                    Toast.makeText(this, "Token error (SHA issue)", Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }

                firebaseAuth(account.idToken!!)

            } catch (e: ApiException) {
                Log.e("LOGIN_ERROR", "ApiException Code: ${e.statusCode}", e)
                Toast.makeText(
                    this,
                    "Google Sign-In Failed: ${e.statusCode}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e("LOGIN_ERROR", "Exception: ${e.message}", e)
                Toast.makeText(
                    this,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val webClientId = getString(R.string.default_web_client_id)
        Log.d("LOGIN_DEBUG", "WebClientID: $webClientId")

        val options = GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, options)

        // optional logout
        auth.signOut()
        googleSignInClient.signOut()

        binding.btnGoogle.setOnClickListener {
            Log.d("LOGIN_DEBUG", "Google Clicked")
            val intent = googleSignInClient.signInIntent
            signInLauncher.launch(intent)
        }
    }

    private fun firebaseAuth(idToken: String) {

        Log.d("LOGIN_DEBUG", "Firebase Auth Start")

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {

                    val user = auth.currentUser
                    Log.d("LOGIN_DEBUG", "SUCCESS: ${user?.email}")

                    Toast.makeText(
                        this,
                        "Welcome ${user?.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()

                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()

                } else {

                    Log.e("LOGIN_ERROR", "Firebase Error: ${task.exception?.message}")

                    Toast.makeText(
                        this,
                        "Auth Failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}