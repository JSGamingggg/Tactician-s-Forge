package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.BrightGold
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.PbePurple

@Composable
fun PatchTierAlertBanner(
    latestPatch: PatchUpdateRecord?,
    environment: TftEnvironment,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (latestPatch == null) return
    val envColor = if (environment == TftEnvironment.PBE) PbePurple else ArcaneCyan

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DeepSlate,
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(envColor, BrightGold))),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails)
            .testTag("patch_tier_alert_banner")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(envColor.copy(alpha = 0.22f), DeepSlate)
                    )
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = envColor.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, envColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = envColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${environment.label} ${latestPatch.version}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = envColor
                                )
                            }
                        }
                        if (latestPatch.isMajorPatch) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrightGold.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, BrightGold)
                            ) {
                                Text(
                                    text = "MAJOR PATCH",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrightGold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = BrightGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Tier List Shifts",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrightGold
                        )
                    }
                }

                Text(
                    text = latestPatch.title.ifBlank { latestPatch.headline },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (latestPatch.highlights.isNotEmpty()) {
                    Text(
                        text = "• ${latestPatch.highlights.first()}",
                        fontSize = 11.sp,
                        color = Color(0xFFC7D3EA),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap to view full balance changes & tier list updates →",
                        fontSize = 10.sp,
                        color = ArcaneCyan,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = latestPatch.releaseDate,
                        fontSize = 9.sp,
                        color = Color(0xFF93A1B8)
                    )
                }
            }
        }
    }
}

@Composable
fun PatchTierDetailsDialog(
    patch: PatchUpdateRecord?,
    onDismiss: () -> Unit
) {
    if (patch == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepSlate,
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE3EBFA),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrightGold)
                Text("Patch ${patch.version} & Tier List Shifts")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = patch.headline,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArcaneCyan
                )

                if (patch.highlights.isNotEmpty()) {
                    Text(
                        text = "Key Patch Highlights & Tier Shifts:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrightGold
                    )
                    patch.highlights.forEach { h ->
                        Text(
                            text = "• $h",
                            fontSize = 11.sp,
                            color = Color(0xFFE3EBFA)
                        )
                    }
                }

                if (patch.balanceChanges.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Significant Balance & Tier Adjustments:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrightGold
                    )
                    patch.balanceChanges.take(6).forEach { bc ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                DeltaBadge(bc.deltaType)
                                Text(
                                    text = "${bc.targetName}: ${bc.summary}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (bc.previousValue.isNotBlank() && bc.newValue.isNotBlank()) {
                                Text(
                                    text = "${bc.previousValue} → ${bc.newValue}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrightGold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = ArcaneCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}
