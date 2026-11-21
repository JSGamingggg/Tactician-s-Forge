package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.MadeByAttributionBadge
import com.example.ui.theme.LocalHextechColors

@Composable
fun AuthWelcomeScreen(
    onExploreAsGuest: () -> Unit,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val isCloudSyncAvailable = remember(context) { GoogleAuthHelper.isFirebaseConfigured(context) }
    var isSigningIn by remember { mutableStateOf(false) }
    var inlineAuthError by remember { mutableStateOf<String?>(null) }

    val triggerSignIn = {
        inlineAuthError = null
        isSigningIn = true
        GoogleAuthHelper.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = {
                isSigningIn = false
                inlineAuthError = null
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isSigningIn = false
                inlineAuthError = msg
                onAuthError(msg)
            },
            onAuthCancelled = { isSigningIn = false }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        val isLandscapeSplit = maxWidth >= 600.dp && maxHeight < 560.dp

        if (isLandscapeSplit) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AuthHeroBanner(bannerHeight = 168.dp)
                    Spacer(Modifier.height(10.dp))
                    MadeByAttributionBadge()
                }
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                ) {
                    AuthFeaturesCard()
                    inlineAuthError?.let { err ->
                        AuthInlineErrorBanner(message = err)
                    }
                    AuthActionsColumn(
                        isCloudSyncAvailable = isCloudSyncAvailable,
                        isSigningIn = isSigningIn,
                        onExploreAsGuest = onExploreAsGuest,
                        onSignInClick = triggerSignIn
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AuthHeroBanner(bannerHeight = 176.dp)
                AuthFeaturesCard()
                inlineAuthError?.let { err ->
                    AuthInlineErrorBanner(message = err)
                }
                AuthActionsColumn(
                    isCloudSyncAvailable = isCloudSyncAvailable,
                    isSigningIn = isSigningIn,
                    onExploreAsGuest = onExploreAsGuest,
                    onSignInClick = triggerSignIn
                )
                MadeByAttributionBadge(
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.riot_legal_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthInlineErrorBanner(message: String) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_error_banner")
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun AuthHeroBanner(bannerHeight: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .clip(RoundedCornerShape(22.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hextech_banner),
            contentDescription = "TFT's Helper Tactical Arena",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_tfts_helper_emblem),
                contentDescription = "TFT's Helper Emblem",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set 18 LIVE & Set 19 PBE Tactical Companion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AuthFeaturesCard() {
    val hextechColors = LocalHextechColors.current
    HextechPanelCard(accentBorder = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureRow(
                icon = Icons.Default.AutoAwesome,
                tint = MaterialTheme.colorScheme.primary,
                title = "LIVE & PBE Meta Snapshots",
                subtitle = "LIVE & PBE meta compositions, level & transition guides, unit items, and real-time patch alerts."
            )
            FeatureRow(
                icon = Icons.Default.MilitaryTech,
                tint = MaterialTheme.colorScheme.secondary,
                title = "Champions, Items & Augments Tier Lists",
                subtitle = "Full recipes, component breakdowns, trait icons, and patch balance shifts."
            )
            FeatureRow(
                icon = Icons.Default.OfflinePin,
                tint = hextechColors.buff,
                title = "Explore as Guest with Offline Caching",
                subtitle = "Browse meta snapshots, tier lists, favorites, and floating overlay offline with zero sign-in."
            )
            FeatureRow(
                icon = Icons.Default.NotificationsActive,
                tint = hextechColors.pbeBadge,
                title = "Instant Patch & Comp Push Alerts",
                subtitle = "Notifies you automatically whenever new LIVE/PBE patches or comps release."
            )
        }
    }
}

@Composable
private fun AuthActionsColumn(
    isCloudSyncAvailable: Boolean,
    isSigningIn: Boolean,
    onExploreAsGuest: () -> Unit,
    onSignInClick: () -> Unit
) {
    val hextechColors = LocalHextechColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onExploreAsGuest,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("explore_as_guest_button")
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = "Explore as Guest",
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.explore_as_guest),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (isCloudSyncAvailable) {
            OutlinedButton(
                onClick = onSignInClick,
                enabled = !isSigningIn,
                border = BorderStroke(1.dp, hextechColors.goldBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auth_google_sign_in_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in with Google...")
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Sign in with Google for Cloud Sync",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Google (Cloud Sync)",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        Text(
            text = "Guest Mode caches all meta snapshots and tier lists locally on your device. You can sign in later anytime to sync favorites across devices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
