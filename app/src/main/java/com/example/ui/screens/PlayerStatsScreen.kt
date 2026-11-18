package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.model.MatchAnalysisSummary
import com.example.data.model.PlayerProfileResult
import com.example.data.model.RiotPlatform
import com.example.data.model.TrackedPlayerDoc
import com.example.ui.components.HextechEmptyState
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.TftAugmentChip
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftItemChip
import com.example.ui.components.TftTraitChip
import com.example.ui.components.formatRiotAssetLabel
import com.example.ui.theme.LocalHextechColors
import com.example.ui.viewmodel.PlayerLookupUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStatsScreen(
    lookupState: PlayerLookupUiState,
    selectedPlatform: RiotPlatform,
    isApiKeyConfigured: Boolean,
    trackedPlayers: List<TrackedPlayerDoc>,
    selectedMatch: MatchAnalysisSummary?,
    onSelectPlatform: (RiotPlatform) -> Unit,
    onSearchPlayer: (String, RiotPlatform) -> Unit,
    onBookmarkPlayer: (PlayerProfileResult) -> Unit,
    onRemoveTrackedPlayer: (String) -> Unit,
    onOpenMatchAnalysis: (MatchAnalysisSummary) -> Unit,
    onDismissMatchAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    var riotIdQuery by remember { mutableStateOf("") }
    var showPlatformMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useSideMatchPanel = maxWidth >= 680.dp && selectedMatch != null
        val matchColumns = if (!useSideMatchPanel && maxWidth >= 680.dp) 2 else 1

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = if (useSideMatchPanel) {
                    Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
        item {
            Column {
                Text(
                    text = "Player Stats & Match Analysis",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Live Riot Games TFT telemetry, LP tracking, and post-game synergy diagnostics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isApiKeyConfigured) {
            item {
                HextechPanelCard(accentBorder = true) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Riot API Key Required for Live Lookups",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Add RIOT_API_KEY in the AI Studio Secrets panel and rebuild to enable live Account-v1, TFT-League-v1, and TFT-Match-v1 queries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Riot ID Search Box
        item {
            HextechPanelCard {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = riotIdQuery,
                            onValueChange = { riotIdQuery = it },
                            label = { Text("Riot ID (Name#TAG)") },
                            placeholder = { Text("k3soju#NA1") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSearchPlayer(riotIdQuery, selectedPlatform) }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("riot_id_input")
                        )

                        Box {
                            OutlinedButton(
                                onClick = { showPlatformMenu = true },
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("platform_picker_button")
                            ) {
                                Text(selectedPlatform.code)
                            }
                            DropdownMenu(
                                expanded = showPlatformMenu,
                                onDismissRequest = { showPlatformMenu = false }
                            ) {
                                RiotPlatform.entries.forEach { platform ->
                                    DropdownMenuItem(
                                        text = { Text("${platform.code} (${platform.region.label})") },
                                        onClick = {
                                            onSelectPlatform(platform)
                                            showPlatformMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { onSearchPlayer(riotIdQuery, selectedPlatform) },
                        enabled = lookupState !is PlayerLookupUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("search_player_button")
                    ) {
                        if (lookupState is PlayerLookupUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Analyzing Recent Matches...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Analyze Player & Match History")
                        }
                    }
                }
            }
        }

        // Saved Tracked Players
        if (trackedPlayers.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tracked Riot IDs",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(trackedPlayers, key = { it.id.ifBlank { "${it.platform}_${it.gameName}_${it.tagLine}" } }) { player ->
                            val platform = RiotPlatform.fromRaw(player.platform)
                            Surface(
                                onClick = {
                                    val fullId = "${player.gameName}#${player.tagLine}"
                                    riotIdQuery = fullId
                                    onSelectPlatform(platform)
                                    onSearchPlayer(fullId, platform)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${player.gameName}#${player.tagLine}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${player.platform} • ${player.lastTier} (${player.lastLp} LP)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveTrackedPlayer(player.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove tracked player",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        when (lookupState) {
            PlayerLookupUiState.Idle -> {
                item {
                    HextechEmptyState(
                        icon = Icons.Default.PersonSearch,
                        title = "Scout Any Tactician",
                        subtitle = "Enter a Riot ID (e.g. Name#NA1) and region above to fetch live ranked standing, LP, top-4 rate, and per-match board diagnostics."
                    )
                }
            }
            PlayerLookupUiState.Loading -> {
                item {
                    HextechPanelCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Fetching Riot Account, Ranked League & Match History...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            is PlayerLookupUiState.Error -> {
                item {
                    HextechPanelCard(accentBorder = lookupState.isMissingApiKey) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (lookupState.isMissingApiKey) "API Key Configuration Needed" else "Lookup Failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = lookupState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            is PlayerLookupUiState.Success -> {
                val profile = lookupState.profile
                item {
                    PlayerSummaryCard(
                        profile = profile,
                        onBookmark = { onBookmarkPlayer(profile) }
                    )
                }

                if (profile.matches.isEmpty()) {
                    item {
                        HextechEmptyState(
                            icon = Icons.Default.Analytics,
                            title = "No Recent TFT Matches",
                            subtitle = "This Riot ID has no recent TFT matches on ${profile.platform.code}."
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "Recent Matches (${profile.matches.size} analyzed — tap for breakdown)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (matchColumns > 1) {
                        items(profile.matches.chunked(matchColumns), key = { row -> row.joinToString("_") { it.matchId } }) { rowMatches ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                rowMatches.forEach { match ->
                                    MatchHistoryCard(
                                        match = match,
                                        onClick = { onOpenMatchAnalysis(match) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowMatches.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(profile.matches, key = { it.matchId }) { match ->
                            MatchHistoryCard(
                                match = match,
                                onClick = { onOpenMatchAnalysis(match) }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
            }

            if (useSideMatchPanel) {
                val sideMatch = selectedMatch
                if (sideMatch != null) {
                    HextechPanelCard(
                        accentBorder = true,
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                            .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Match Breakdown",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = onDismissMatchAnalysis,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close match analysis",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            MatchAnalysisSheetContent(match = sideMatch)
                        }
                    }
                }
            }
        }

        if (!useSideMatchPanel) {
            selectedMatch?.let { match ->
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = onDismissMatchAnalysis,
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    MatchAnalysisSheetContent(match = match)
                }
            }
        }
    }
}

@Composable
private fun PlayerSummaryCard(
    profile: PlayerProfileResult,
    onBookmark: () -> Unit
) {
    val ranked = profile.rankedEntry
    val tierText = ranked?.let { "${it.tier} ${it.rank}".trim() } ?: "UNRANKED"
    val lpText = ranked?.let { "${it.leaguePoints} LP" } ?: "0 LP"

    HextechPanelCard(accentBorder = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${profile.account.gameName}#${profile.account.tagLine}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${profile.platform.code} • Level ${profile.summonerLevel} • $tierText ($lpText)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = onBookmark,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Track")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCol("AVG PLACE", String.format("%.2f", profile.avgPlacement))
                MetricCol("TOP 4 RATE", "${profile.top4Rate.toInt()}%")
                MetricCol("WIN RATE", "${profile.winRate.toInt()}%")
                MetricCol("W / L", "${ranked?.wins ?: 0} / ${ranked?.losses ?: 0}")
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchHistoryCard(
    match: MatchAnalysisSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hextech = LocalHextechColors.current
    val placeColor = when (match.placement) {
        1 -> hextech.tierS
        in 2..4 -> hextech.buff
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dateStr = remember(match.gameDatetime) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(match.gameDatetime))
    }

    HextechPanelCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(placeColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${match.placement}",
                            style = MaterialTheme.typography.titleMedium,
                            color = placeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "Set ${match.setNumber} • Lv ${match.levelReached} • ${match.durationMinutes}m",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$dateStr • ${match.totalDamageToPlayers} Player Dmg • ${match.itemCompletionPercent}% Items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.Default.Analytics, contentDescription = "View match analysis", tint = MaterialTheme.colorScheme.secondary)
            }

            if (match.activeTraits.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    match.activeTraits.take(5).forEach { trait ->
                        val cleanTrait = formatRiotAssetLabel(trait.name)
                        TftTraitChip(traitLabel = "${trait.numUnits} $cleanTrait")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchAnalysisSheetContent(match: MatchAnalysisSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Match #${match.placement} Diagnostic Breakdown",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Level ${match.levelReached} • ${match.playersEliminated} Eliminations • ${match.goldLeft}g Remaining • ${match.threeStarCount} 3★ Units",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        HextechPanelCard(accentBorder = true) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tactical Analysis",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                match.tacticalInsights.forEach { insight ->
                    Text(
                        text = "• $insight",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (match.activeTraits.isNotEmpty()) {
            Text(
                text = "Active Traits",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                match.activeTraits.forEach { trait ->
                    val cleanTrait = formatRiotAssetLabel(trait.name)
                    TftTraitChip(traitLabel = "${trait.numUnits} $cleanTrait")
                }
            }
        }

        if (match.augments.isNotEmpty()) {
            Text(
                text = "Selected Augments",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                match.augments.forEach { aug ->
                    TftAugmentChip(augmentName = aug)
                }
            }
        }

        if (match.units.isNotEmpty()) {
            Text(
                text = "Final Board Units & Items",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                match.units.forEach { unit ->
                    val unitName = formatRiotAssetLabel(unit.name.ifBlank { unit.characterId })
                    val stars = "★".repeat(unit.tier.coerceIn(1, 3))
                    val approxCost = (unit.rarity + 1).coerceIn(1, 5)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (unit.tier >= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TftChampionAvatar(
                                    name = unitName,
                                    cost = approxCost,
                                    isCarry = unit.itemNames.size >= 3,
                                    stars = unit.tier,
                                    size = 28.dp
                                )
                                Text(
                                    text = "$unitName $stars",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (unit.itemNames.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    unit.itemNames.forEach { rawItem ->
                                        TftItemChip(itemName = rawItem, compact = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
