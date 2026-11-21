package com.example.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank

enum class OverlayBrowseMode {
    ALL_COMPS,
    FAVORITES
}

enum class OverlayFinderTab {
    CHAMPIONS,
    ITEMS
}

enum class OverlayItemFilterScope {
    ALL,
    COMPONENTS,
    COMPLETED
}

/**
 * Holds all interactive user state for an active [OverlayService] session:
 * selected tab/browse mode, LIVE/PBE filter, tier filter, expanded tier accordions,
 * Army Finder filters (champions, items, search query), active selected composition,
 * champion inspector target, and scroll positions.
 *
 * State survives drawer minimize/reopen, screen rotation, drag/resize, and live syncs,
 * and is cleared only when the overlay is fully closed off ([reset] in [OverlayService.onDestroy]).
 */
class OverlaySessionState {
    var browseMode by mutableStateOf(OverlayBrowseMode.ALL_COMPS)
        private set

    var envFilter by mutableStateOf(TftEnvironment.LIVE)
        private set

    var tierFilter by mutableStateOf<TierRank?>(null)
        private set

    var selectedCompId by mutableStateOf<String?>(null)
        private set

    var inspectedChampion by mutableStateOf<Pair<String, Int?>?>(null)
        private set

    private var expandedTiersByScope by mutableStateOf<Map<Pair<OverlayBrowseMode, TftEnvironment>, Set<TierRank>>>(
        emptyMap()
    )

    var listScrollIndex by mutableIntStateOf(0)
        private set

    var listScrollOffset by mutableIntStateOf(0)
        private set

    var detailScrollOffset by mutableIntStateOf(0)
        private set

    // Army Finder (search armies by Champions or Items)
    var isArmyFinderOpen by mutableStateOf(false)
        private set

    // Transparency / Opacity slider panel visibility
    var isOpacityControlOpen by mutableStateOf(false)
        private set

    var finderTab by mutableStateOf(OverlayFinderTab.CHAMPIONS)
        private set

    var finderQuery by mutableStateOf("")
        private set

    var finderCostFilter by mutableStateOf<Int?>(null)
        private set

    var finderItemScope by mutableStateOf(OverlayItemFilterScope.ALL)
        private set

    var selectedChampions by mutableStateOf<Set<String>>(emptySet())
        private set

    var selectedItems by mutableStateOf<Set<String>>(emptySet())
        private set

    val expandedTiers: Set<TierRank>
        get() = expandedTiersByScope[browseMode to envFilter].orEmpty()

    val activeArmyFilterCount: Int
        get() = selectedChampions.size + selectedItems.size + if (finderQuery.isNotBlank()) 1 else 0

    val hasActiveArmyFilters: Boolean
        get() = selectedChampions.isNotEmpty() || selectedItems.isNotEmpty() || finderQuery.isNotBlank()

    fun selectBrowseMode(mode: OverlayBrowseMode) {
        if (browseMode != mode) {
            browseMode = mode
            selectedCompId = null
            inspectedChampion = null
            listScrollIndex = 0
            listScrollOffset = 0
        }
    }

    fun selectEnvironment(env: TftEnvironment) {
        if (envFilter != env) {
            envFilter = env
            selectedCompId = null
            inspectedChampion = null
            // Reset set-specific champion & item selections when switching LIVE <-> PBE
            selectedChampions = emptySet()
            selectedItems = emptySet()
            finderQuery = ""
            listScrollIndex = 0
            listScrollOffset = 0
        }
    }

    fun toggleTierFilter(tier: TierRank) {
        tierFilter = if (tierFilter == tier) null else tier
        listScrollIndex = 0
        listScrollOffset = 0
    }

    fun toggleTierExpanded(tier: TierRank) {
        val key = browseMode to envFilter
        val current = expandedTiersByScope[key].orEmpty()
        val updated = if (current.contains(tier)) current - tier else current + tier
        expandedTiersByScope = expandedTiersByScope + (key to updated)
    }

    fun setExpandedTiers(tiers: Set<TierRank>) {
        val key = browseMode to envFilter
        expandedTiersByScope = expandedTiersByScope + (key to tiers)
    }

    fun toggleArmyFinder() {
        isArmyFinderOpen = !isArmyFinderOpen
    }

    fun updateArmyFinderOpen(open: Boolean) {
        isArmyFinderOpen = open
    }

    fun toggleOpacityControl() {
        isOpacityControlOpen = !isOpacityControlOpen
    }

    fun updateOpacityControlOpen(open: Boolean) {
        isOpacityControlOpen = open
    }

    fun selectFinderTab(tab: OverlayFinderTab) {
        finderTab = tab
    }

    fun updateFinderQuery(query: String) {
        finderQuery = query
        listScrollIndex = 0
        listScrollOffset = 0
    }

    fun selectFinderCostFilter(cost: Int?) {
        finderCostFilter = if (finderCostFilter == cost) null else cost
    }

    fun selectFinderItemScope(scope: OverlayItemFilterScope) {
        finderItemScope = scope
    }

    fun toggleSelectedChampion(championName: String) {
        selectedChampions = if (selectedChampions.contains(championName)) {
            selectedChampions - championName
        } else {
            selectedChampions + championName
        }
        listScrollIndex = 0
        listScrollOffset = 0
    }

    fun toggleSelectedItem(itemName: String) {
        selectedItems = if (selectedItems.contains(itemName)) {
            selectedItems - itemName
        } else {
            selectedItems + itemName
        }
        listScrollIndex = 0
        listScrollOffset = 0
    }

    fun clearArmyFilters() {
        selectedChampions = emptySet()
        selectedItems = emptySet()
        finderQuery = ""
        finderCostFilter = null
        listScrollIndex = 0
        listScrollOffset = 0
    }

    fun selectComp(compId: String?) {
        if (selectedCompId != compId) {
            selectedCompId = compId
            detailScrollOffset = 0
        }
        inspectedChampion = null
    }

    fun openCompInEnvironment(compId: String, environment: TftEnvironment) {
        if (envFilter != environment) {
            envFilter = environment
            selectedChampions = emptySet()
            selectedItems = emptySet()
            finderQuery = ""
        }
        selectedCompId = compId
        inspectedChampion = null
        detailScrollOffset = 0
    }

    fun inspectChampion(name: String, cost: Int?) {
        inspectedChampion = name to cost
    }

    fun closeChampionInspector() {
        inspectedChampion = null
    }

    fun updateListScroll(index: Int, offset: Int) {
        listScrollIndex = index.coerceAtLeast(0)
        listScrollOffset = offset.coerceAtLeast(0)
    }

    fun updateDetailScroll(offset: Int) {
        detailScrollOffset = offset.coerceAtLeast(0)
    }

    /**
     * Clears all remembered inputs and selected composition when the overlay is fully closed off.
     */
    fun reset() {
        browseMode = OverlayBrowseMode.ALL_COMPS
        envFilter = TftEnvironment.LIVE
        tierFilter = null
        selectedCompId = null
        inspectedChampion = null
        expandedTiersByScope = emptyMap()
        listScrollIndex = 0
        listScrollOffset = 0
        detailScrollOffset = 0
        isArmyFinderOpen = false
        isOpacityControlOpen = false
        finderTab = OverlayFinderTab.CHAMPIONS
        finderQuery = ""
        finderCostFilter = null
        finderItemScope = OverlayItemFilterScope.ALL
        selectedChampions = emptySet()
        selectedItems = emptySet()
    }
}
