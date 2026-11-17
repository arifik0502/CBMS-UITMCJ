package com.crispyburger.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.DeveloperProfile
import com.crispyburger.admin.notifications.FcmTokenManager
import com.crispyburger.admin.ui.auth.DeveloperLoginScreen
import com.crispyburger.admin.ui.home.AdminDashboardScreen
import com.crispyburger.admin.ui.theme.CbmsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CbmsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeveloperOnlyApp()
                }
            }
        }
    }
}

@Composable
private fun DeveloperOnlyApp() {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<DeveloperProfile?>(null) }
    var isCheckingSession by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!AdminSupabase.isConfigured) {
            errorMessage = "Add the Supabase URL and anon key to android-admin/local.properties."
            isCheckingSession = false
            return@LaunchedEffect
        }

        try {
            profile = AdminSupabase.restoreDeveloperSession()
        } catch (_: Exception) {
            profile = null
            errorMessage = "The saved session could not be verified. Sign in again."
        } finally {
            isCheckingSession = false
        }
    }

    LaunchedEffect(profile?.id) {
        if (profile != null) {
            FcmTokenManager.registerCurrentDevice(context.applicationContext)
        }
    }

    when {
        isCheckingSession -> LoadingScreen()
        profile != null -> AdminDashboardScreen(
            profile = requireNotNull(profile),
            onSignOut = {
                scope.launch {
                    runCatching {
                        AdminSupabase.signOut(
                            FcmTokenManager.currentToken(context.applicationContext)
                        )
                    }
                    profile = null
                    errorMessage = null
                }
            }
        )
        else -> DeveloperLoginScreen(
            isConfigured = AdminSupabase.isConfigured,
            isLoading = isSigningIn,
            errorMessage = errorMessage,
            onSignIn = { password ->
                scope.launch {
                    isSigningIn = true
                    errorMessage = null
                    try {
                        profile = AdminSupabase.signInDeveloper(password)
                    } catch (error: Exception) {
                        runCatching { AdminSupabase.signOut() }
                        errorMessage = friendlySignInError(error)
                    } finally {
                        isSigningIn = false
                    }
                }
            }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text("Checking developer access...")
        }
    }
}

private fun friendlySignInError(error: Exception): String {
    if (error is SecurityException) {
        return error.message ?: "This account is not approved for CBMS Admin."
    }

    val message = error.message.orEmpty()
    return when {
        message.contains("invalid login", ignoreCase = true) ->
            "The developer email or password is incorrect."
        message.contains("email not confirmed", ignoreCase = true) ->
            "Confirm the developer email in Supabase Auth before signing in."
        else ->
            "Sign-in failed. Confirm the developer-only migration has been applied and check your internet connection."
    }
}
