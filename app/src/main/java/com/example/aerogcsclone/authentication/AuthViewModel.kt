package com.example.aerogcsclone.authentication

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private var oneTapClient: SignInClient? = null

    private val _authState = MutableLiveData<AuthState>()

    val authState: LiveData<AuthState> = _authState


    init {
        checkAuthStatus()
    }

    fun checkAuthStatus() {
        if(auth.currentUser==null){

            _authState.value = AuthState.Unauthenticated

        }else{
            _authState.value = AuthState.Authenticated

        }

    }



    fun login(email : String,password : String){

        if(email.isEmpty() || password.isEmpty() ){

            _authState.value = AuthState.Error("Email and password can't be empty")

            return

        }

        _authState.value = AuthState.Loading

        auth.signInWithEmailAndPassword(email, password)

            .addOnCompleteListener { task->

                if (task.isSuccessful){

                    _authState.value = AuthState.Authenticated

                }else{

                    _authState.value = AuthState.Error(task.exception?.message?:"somthing went wrong dued")

                }

            }

    }

    fun signup(email : String,password : String){

        if(email.isEmpty() || password.isEmpty() ){
            _authState.value = AuthState.Error("Email and password can't be empty")

            return

        }

        _authState.value = AuthState.Loading

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task->
                if (task.isSuccessful){
                    _authState.value = AuthState.Authenticated

                }else{

                    _authState.value = AuthState.Error(task.exception?.message?:"somthing went wrong dued")



                }



            }

    }

    fun signout(){

        auth.signOut()

        _authState.value = AuthState.Unauthenticated

    }

    fun signInWithGoogle(
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        _authState.value = AuthState.Loading

        oneTapClient = Identity.getSignInClient(context)

        val signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId("207246398449-r3bqutal959vfb4p5ktrklu1gaah9ufd.apps.googleusercontent.com")
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()

        oneTapClient?.beginSignIn(signInRequest)
            ?.addOnSuccessListener { result ->
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    launcher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    _authState.value = AuthState.Error(e.message ?: "Error launching Google Sign-In")
                }
            }
            ?.addOnFailureListener { e ->
                _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
            }
    }

    fun handleGoogleSignInResult(context: Context, data: android.content.Intent?) {
        try {
            val credential = oneTapClient?.getSignInCredentialFromIntent(data)
            val idToken = credential?.googleIdToken

            if (idToken != null) {
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            _authState.value = AuthState.Authenticated
                        } else {
                            _authState.value = AuthState.Error(task.exception?.message ?: "Authentication failed")
                        }
                    }
            } else {
                _authState.value = AuthState.Error("No ID token received")
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Error processing Google Sign-In")
        }
    }

    fun checkIfEmailIsGoogleAccount(email: String, onResult: (Boolean) -> Unit) {
        // ...existing code...
    }

}

sealed class AuthState {

    object Authenticated : AuthState()

    object Unauthenticated : AuthState()

    object Loading : AuthState()

    data class Error(val message: String) : AuthState()

}
