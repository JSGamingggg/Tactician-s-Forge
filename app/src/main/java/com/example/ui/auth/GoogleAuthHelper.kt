package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import android.content.ContextWrapper
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.LocalHextechColors
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    @Suppress("DiscouragedApi")
    private fun resolveDefaultWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId == 0) return null
        return runCatching { context.getString(resId) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun isFirebaseConfigured(context: Context): Boolean =
        com.google.firebase.FirebaseApp.getApps(context).isNotEmpty() &&
            resolveDefaultWebClientId(context) != null

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            onUnauthenticated()
            return
        }
        val firebaseAuth = runCatching { Firebase.auth }.getOrNull()
        if (firebaseAuth == null) {
            onUnauthenticated()
            return
        }
        if (firebaseAuth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = resolveDefaultWebClientId(context)
        if (clientId == null) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val hostContext: Context = context.findActivity() ?: context
                val cred = credentialManager.getCredential(hostContext, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit = {}
    ) {
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            onAuthError(
                "Firebase is not initialized in this APK build (missing google-services.json). Tap Explore as Guest to use all offline & live features."
            )
            return
        }
        val firebaseAuth = runCatching { Firebase.auth }.getOrNull()
        if (firebaseAuth == null) {
            onAuthError("Firebase Auth is unavailable in this APK build. Tap Explore as Guest for offline & local features.")
            return
        }
        val clientId = resolveDefaultWebClientId(context)
        if (clientId == null) {
            onAuthError(
                "Google Sign-In Web Client ID (default_web_client_id) is missing in this APK build. Tap Explore as Guest to continue."
            )
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            val hostContext: Context = context.findActivity() ?: context
            try {
                val cred = try {
                    credentialManager.getCredential(hostContext, primaryRequest).credential
                } catch (primaryErr: GetCredentialException) {
                    if (primaryErr is GetCredentialCancellationException && !looksLikeProviderConfigError(primaryErr.message)) {
                        throw primaryErr
                    }
                    // Fallback to GetGoogleIdOption(filterByAuthorizedAccounts = false) for devices
                    // where GetSignInWithGoogleOption returns NoCredential or provider config errors.
                    val fallbackOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    credentialManager.getCredential(hostContext, fallbackRequest).credential
                }

                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned by Google Play Services.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                val rawMsg = e.message.orEmpty()
                if (looksLikeProviderConfigError(rawMsg)) {
                    onAuthError(
                        "Google Sign-In could not verify this APK signing key or Google account ($rawMsg). Tap Explore as Guest to use all offline features immediately."
                    )
                } else {
                    onAuthCancelled()
                }
            } catch (e: NoCredentialException) {
                Log.w(TAG, "No Google credential available: ${e.message}", e)
                onAuthError(
                    "No Google account or credential available on this device. Add a Google account in Settings or tap Explore as Guest."
                )
            } catch (e: GetCredentialProviderConfigurationException) {
                Log.e(TAG, "Credential provider configuration error", e)
                onAuthError(
                    "Google Play Services Credential Manager is unavailable or needs updating. Tap Explore as Guest to continue."
                )
            } catch (e: GetCredentialUnsupportedException) {
                Log.e(TAG, "Credential Manager unsupported on device", e)
                onAuthError(
                    "Google Sign-In is unsupported on this device configuration. Tap Explore as Guest to continue."
                )
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error: ${e.type} - ${e.message}", e)
                onAuthError(e.errorMessage?.toString() ?: e.localizedMessage ?: "Google Sign-In failed (${e.type}).")
            } catch (e: FirebaseAuthException) {
                Log.e(TAG, "FirebaseAuth error: ${e.errorCode} - ${e.message}", e)
                onAuthError("Firebase Sign-In failed (${e.errorCode}): ${e.localizedMessage ?: "Try again or Explore as Guest."}")
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    private fun looksLikeProviderConfigError(rawMsg: String?): Boolean {
        val msg = rawMsg.orEmpty()
        return msg.contains("16", ignoreCase = true) ||
            msg.contains("10", ignoreCase = true) ||
            msg.contains("28436", ignoreCase = true) ||
            msg.contains("developer", ignoreCase = true) ||
            msg.contains("sha", ignoreCase = true) ||
            msg.contains("reauth", ignoreCase = true) ||
            msg.contains("matching credential", ignoreCase = true)
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    runCatching { Firebase.auth.signOut() }
                    onSignOutComplete()
                }
            }
        }
    }
}

@Composable
fun CloudSyncStatusCard(
    currentUser: FirebaseUser?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val hextechColors = LocalHextechColors.current
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, hextechColors.goldBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (currentUser == null) {
                val firebaseReady = remember(context) { GoogleAuthHelper.isFirebaseConfigured(context) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (firebaseReady) stringResource(R.string.guest_mode_label) else "Local Device Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (firebaseReady) {
                                "Favorites are saved on this device. Sign in optionally to sync comps & Riot IDs to Firestore."
                            } else {
                                "Favorites, offline meta snapshots, tier lists, and overlay pins are saved locally on this device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (firebaseReady) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            isLoading = true
                            GoogleAuthHelper.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    onAuthError(msg)
                                },
                                onAuthCancelled = { isLoading = false }
                            )
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.sign_in_google))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = currentUser.displayName ?: "Cloud Synced Tactician",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentUser.email ?: "Firestore Cloud Sync Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            GoogleAuthHelper.signOut(credentialManager, scope) {
                                onSignedOut()
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.sign_out),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
        }
    }
}
