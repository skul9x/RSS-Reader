package com.skul9x.rssreader.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.skul9x.rssreader.R
import com.skul9x.rssreader.auth.AuthUiState
import com.skul9x.rssreader.auth.AuthViewModel

/**
 * Account section in Settings screen.
 * Shows Google Sign-In button when not signed in,
 * or user info card when signed in.
 */
@Composable
fun AccountSection(
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val uiState by authViewModel.uiState.collectAsState()
    
    // Launcher for Google Sign-In intent
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authViewModel.handleSignInResult(result.data)
    }
    
    // Show error toast
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            authViewModel.clearError()
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = "Tài khoản",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (uiState.user != null) {
                // Signed in - show user info
                UserInfoCard(
                    uiState = uiState,
                    onSignOut = { authViewModel.signOut() }
                )
            } else {
                // Not signed in - show sign-in button
                GoogleSignInButton(
                    isLoading = uiState.isLoading,
                    onClick = {
                        val signInIntent = authViewModel.getSignInIntent()
                        launcher.launch(signInIntent)
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Đăng nhập để đồng bộ tin đã đọc giữa các thiết bị",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun UserInfoCard(
    uiState: AuthUiState,
    onSignOut: () -> Unit
) {
    val user = uiState.user ?: return
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (user.photoUrl != null) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // User info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName ?: "Người dùng",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = user.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Sign out button
    OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Đăng xuất")
    }
}

@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = if (isLoading) "Đang đăng nhập..." else "Đăng nhập bằng Google",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            )
        )
    }
}
