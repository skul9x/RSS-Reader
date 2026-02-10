package com.skul9x.rssreader.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.skul9x.rssreader.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Singleton manager for Google Sign-In with Firebase Authentication.
 * Handles sign-in, sign-out, and user state management.
 */
class AuthManager private constructor(private val context: Context) {
    
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val googleSignInClient: GoogleSignInClient
    
    private val _userStateFlow = MutableStateFlow<FirebaseUser?>(firebaseAuth.currentUser)
    val userStateFlow: StateFlow<FirebaseUser?> = _userStateFlow.asStateFlow()
    
    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
        
        // Listen for auth state changes
        firebaseAuth.addAuthStateListener { auth ->
            _userStateFlow.value = auth.currentUser
        }
    }
    
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent
    
    suspend fun handleSignInResult(data: Intent?): Result<FirebaseUser> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            val idToken = account.idToken
            if (idToken == null) {
                return Result.failure(Exception("Không lấy được token từ Google"))
            }
            
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            
            if (user != null) {
                Log.d(TAG, "Sign-in successful: ${user.email}")
                Result.success(user)
            } else {
                Result.failure(Exception("Đăng nhập thất bại"))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed: ${e.statusCode}", e)
            when (e.statusCode) {
                12501 -> Result.failure(Exception("Đã hủy đăng nhập"))
                7 -> Result.failure(Exception("Không có kết nối mạng"))
                else -> Result.failure(Exception("Đăng nhập thất bại (${e.statusCode})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in error", e)
            Result.failure(Exception("Đăng nhập thất bại: ${e.message}"))
        }
    }
    
    fun signOut() {
        firebaseAuth.signOut()
        googleSignInClient.signOut()
        Log.d(TAG, "User signed out")
    }
    
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser
    
    fun isSignedIn(): Boolean = firebaseAuth.currentUser != null
    
    companion object {
        private const val TAG = "AuthManager"
        
        @Volatile
        private var INSTANCE: AuthManager? = null
        
        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
