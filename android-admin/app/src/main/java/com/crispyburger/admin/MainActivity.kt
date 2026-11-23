package com.crispyburger.admin

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.DeveloperProfile
import com.crispyburger.admin.data.ErrorMessages
import com.crispyburger.admin.notifications.AdminNotificationHelper
import com.crispyburger.admin.notifications.FcmTokenManager
import com.crispyburger.admin.ui.auth.DeveloperLoginScreen
import com.crispyburger.admin.ui.auth.LockScreen
import com.crispyburger.admin.ui.home.AdminDashboardScreen
import com.crispyburger.admin.ui.theme.CbmsTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var lockRequired by mutableStateOf(true)
    private var lockMessage by mutableStateOf<String?>(null)
    private var pendingOrderId by mutableStateOf<String?>(null)
    private var pendingTab by mutableStateOf<String?>(null)
    private var backgroundedAt = 0L

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOrderId = intent?.getStringExtra(AdminNotificationHelper.EXTRA_ORDER_ID)
        pendingTab = intent?.getStringExtra(AdminNotificationHelper.EXTRA_OPEN_TAB)
        setupBiometric()
        setContent {
            CbmsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeveloperOnlyApp(
                        lockRequired = lockRequired,
                        lockMessage = lockMessage,
                        pendingOrderId = pendingOrderId,
                        pendingTab = pendingTab,
                        onRequestUnlock = ::requestUnlock,
                        onUnlocked = { lockMessage = null; lockRequired = false },
                        onOrderHandled = { pendingOrderId = null },
                        onTabHandled = { pendingTab = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(AdminNotificationHelper.EXTRA_ORDER_ID)?.let { pendingOrderId = it }
        intent.getStringExtra(AdminNotificationHelper.EXTRA_OPEN_TAB)?.let { pendingTab = it }
    }

    override fun onStop() {
        backgroundedAt = SystemClock.elapsedRealtime()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundedAt != 0L &&
            SystemClock.elapsedRealtime() - backgroundedAt > LOCK_AFTER_BACKGROUND_MS
        ) {
            lockRequired = true
        }
    }

    private fun setupBiometric() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lockMessage = null
                    lockRequired = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    lockMessage = errString.toString()
                }
            }
        )
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock CBMS Admin")
            .setSubtitle("Confirm it's you to view orders and payments")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
    }

    private fun canLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private fun requestUnlock() {
        // A phone with no screen lock or biometrics cannot be locked: do not trap the owner out.
        if (!canLock()) {
            lockRequired = false
            return
        }
        lockMessage = null
        biometricPrompt.authenticate(promptInfo)
    }

    private companion object {
        const val LOCK_AFTER_BACKGROUND_MS = 2 * 60 * 1000L
        const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

@Composable
private fun DeveloperOnlyApp(
    lockRequired: Boolean,
    lockMessage: String?,
    pendingOrderId: String?,
    pendingTab: String?,
    onRequestUnlock: () -> Unit,
    onUnlocked: () -> Unit,
    onOrderHandled: () -> Unit,
    onTabHandled: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var profile by remember { mutableStateOf<DeveloperProfile?>(null) }
    var isCheckingSession by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun signOut() {
        scope.launch {
            runCatching {
                AdminSupabase.signOut(FcmTokenManager.currentToken(context.applicationContext))
            }
            profile = null
            errorMessage = null
        }
    }

    LaunchedEffect(Unit) {
        if (!AdminSupabase.isConfigured) {
            errorMessage = "Add the Supabase URL and anon key to android-admin/local.properties."
            isCheckingSession = false
            return@LaunchedEffect
        }

        try {
            profile = AdminSupabase.restoreDeveloperSession()
        } catch (e: CancellationException) {
            throw e
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

    // Prompt once per lock event, only when the screen is actually resumed.
    LaunchedEffect(profile?.id, lockRequired) {
        if (profile != null && lockRequired) {
            lifecycleOwner.lifecycle.withResumed { onRequestUnlock() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isCheckingSession -> LoadingScreen()
            profile != null -> AdminDashboardScreen(
                profile = requireNotNull(profile),
                openOrderId = pendingOrderId,
                onOrderOpenHandled = onOrderHandled,
                openTab = pendingTab,
                onTabOpenHandled = onTabHandled,
                onSignOut = { signOut() }
            )
            else -> DeveloperLoginScreen(
                isConfigured = AdminSupabase.isConfigured,
                isLoading = isSigningIn,
                errorMessage = errorMessage,
                initialEmail = AdminSupabase.configuredEmail,
                onSignIn = { email, password ->
                    scope.launch {
                        isSigningIn = true
                        errorMessage = null
                        try {
                            profile = AdminSupabase.signInDeveloper(email, password)
                            onUnlocked() // password was just proven; no need to prompt again
                        } catch (e: CancellationException) {
                            throw e
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

        if (profile != null && lockRequired) {
            LockScreen(
                message = lockMessage,
                onUnlock = onRequestUnlock,
                onSignOut = { signOut() }
            )
        }
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
            Text("Checking admin access...")
        }
    }
}

internal fun friendlySignInError(error: Exception): String {
    if (error is SecurityException) {
        return error.message ?: "This account is not approved for CBMS Admin."
    }
    if (error is IllegalArgumentException) {
        return error.message ?: "Check your email and password."
    }

    val message = ErrorMessages.describe(error)
    return when {
        message.contains("invalid login", ignoreCase = true) ->
            "The admin email or password is incorrect."
        message.contains("email not confirmed", ignoreCase = true) ->
            "Confirm the admin email in Supabase Auth before signing in."
        else -> ErrorMessages.map(
            message,
            "Sign-in failed. Confirm this account has an active OWNER or ADMIN profile and check your internet connection."
        )
    }
}
