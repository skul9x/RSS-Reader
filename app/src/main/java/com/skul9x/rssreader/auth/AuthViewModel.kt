package com.skul9x.rssreader.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for authentication
 */
data class AuthUiState(
    val user: FirebaseUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for managing authentication state in Settings screen.
 */
class AuthViewModel(private val authManager: AuthManager) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    init {
        // Observe user state from AuthManager
        viewModelScope.launch {
            authManager.userStateFlow.collect { user ->
                _uiState.value = _uiState.value.copy(user = user, error = null)
            }
        }
    }
    
    fun getSignInIntent(): Intent = authManager.getSignInIntent()
    
    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = authManager.handleSignInResult(data)
            
            result.fold(
                onSuccess = { user ->
                    _uiState.value = _uiState.value.copy(
                        user = user,
                        isLoading = false,
                        error = null
                    )
                },
                onFailure = { e ->
                    // Don't show error for cancellation
                    val errorMsg = if (e.message?.contains("hủy") == true) null else e.message
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            )
        }
    }
    
    fun signOut() {
        authManager.signOut()
        _uiState.value = AuthUiState()
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
