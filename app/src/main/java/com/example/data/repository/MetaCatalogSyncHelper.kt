package com.example.data.repository

import com.example.data.model.AugmentTier
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.ItemCategory
import com.example.data.model.ItemTierEntry
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.data.network.TftAcademyCatalogTiers
import com.example.data.network.TftNinjaChampionRecommendation
import com.example.ui.components.TftAssetIconResolver
import com.example.ui.components.formatRiotAssetLabel
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Keeps LIVE Set Champions, Items, and Augments synchronized with the latest
 * Riot Data Dragon catalogs, TFTAcademy live tierlists, and active compositions.
 */
object MetaCatalogSyncHelper {

    data class LiveCatalogRefreshResult(
        val champions: List<ChampionTierEntry>,
        val items: List<ItemTierEntry>,
        val augments: List<AugmentTierEntry>
    )

    fun refreshLiveCatalog(
        existingChampions: List<ChampionTierEntry>,
        existingItems: List<ItemTierEntry>,
        existingAugments: List<AugmentTierEntry>,
        liveComps: List<MetaComp>,
        liveSetNumber: Int,
        ddragonChampJson: String?,
        ddragonItemJson: String?,
        ddragonAugmentJson: String?,
        academyTiers: TftAcademyCatalogTiers?,
        ninjaChampRecommendations: Map<String, TftNinjaChampionRecommendation> = emptyMap()
    ): LiveCatalogRefreshResult {
        val updatedChampions = refreshLiveChampions(
            existing = existingChampions,
            liveComps = liveComps,
            liveSetNumber = liveSetNumber,
            ddragonChampJson = ddragonChampJson,
            targetEnv = TftEnvironment.LIVE,
            ninjaChampRecommendations = ninjaChampRecommendations
        )
        val updatedItems = refreshLiveItems(
            existing = existingItems,
            liveComps = liveComps,
            liveSetNumber = liveSetNumber,
            ddragonItemJson = ddragonItemJson,
            academyTiers = academyTiers
        )
        val updatedAugments = refreshLiveAugments(
            existing = existingAugments,
            liveComps = liveComps,
            liveSetNumber = liveSetNumber,
            ddragonAugmentJson = ddragonAugmentJson,
            academyTiers = academyTiers
        )
        return LiveCatalogRefreshResult(
            champions = updatedChampions,
            items = updatedItems,
            augments = updatedAugments
        )
    }

    fun refreshPbeCatalog(
        existingChampions: List<ChampionTierEntry>,
        existingItems: List<ItemTierEntry>,
        existingAugments: List<AugmentTierEntry>,
        pbeComps: List<MetaComp>,
        academyTiers: TftAcademyCatalogTiers?,
        ninjaChampRecommendations: Map<String, TftNinjaChampionRecommendation> = emptyMap()
    ): LiveCatalogRefreshResult {
        if (pbeComps.isEmpty() && academyTiers == null && ninjaChampRecommendations.isEmpty()) {
            return LiveCatalogRefreshResult(existingChampions, existingItems, existingAugments)
        }
        val updatedChampions = refreshLiveChampions(
            existing = existingChampions,
            liveComps = pbeComps,
            liveSetNumber = 19,
            ddragonChampJson = null,
            targetEnv = TftEnvironment.PBE,
            ninjaChampRecommendations = ninjaChampRecommendations
        )
        val updatedItems = refreshLiveItems(
            existing = existingItems,
            liveComps = pbeComps,
            liveSetNumber = 19,
            ddragonItemJson = null,
            academyTiers = academyTiers,
            targetEnv = TftEnvironment.PBE
        )
        val updatedAugments = refreshLiveAugments(
            existing = existingAugments,
            liveComps = pbeComps,
            liveSetNumber = 19,
            ddragonAugmentJson = null,
            academyTiers = academyTiers,
            targetEnv = TftEnvironment.PBE
        )
        return LiveCatalogRefreshResult(
            champions = updatedChampions,
            items = updatedItems,
            augments = updatedAugments
        )
    }

    private fun refreshLiveChampions(
        existing: List<ChampionTierEntry>,
        liveComps: List<MetaComp>,
        liveSetNumber: Int,
        ddragonChampJson: String?,
        targetEnv: TftEnvironment = TftEnvironment.LIVE,
        ninjaChampRecommendations: Map<String, TftNinjaChampionRecommendation> = emptyMap()
    ): List<ChampionTierEntry> {
        val compsByUnit = LinkedHashMap<String, MutableList<MetaComp>>()
        val itemFreqByUnit = LinkedHashMap<String, MutableMap<String, Int>>()
        val costByUnit = LinkedHashMap<String, Int>()
        val displayNameByUnit = LinkedHashMap<String, String>()

        val normalizedNinjaRecs = LinkedHashMap<String, TftNinjaChampionRecommendation>()
        ninjaChampRecommendations.forEach { (rawKey, rec) ->
            val norm = normKey(rec.championName.ifBlank { rawKey })
            if (norm.isNotBlank()) {
                normalizedNinjaRecs[norm] = rec
                displayNameByUnit.putIfAbsent(norm, rec.championName.ifBlank { rawKey })
            }
        }

        liveComps.forEach { comp ->
            comp.coreUnits.forEach { unit ->
                val norm = normKey(unit)
                if (norm.isNotBlank()) {
                    displayNameByUnit.putIfAbsent(norm, unit)
                    compsByUnit.getOrPut(norm) { mutableListOf() }.add(comp)
                }
            }
            comp.hexPlacements.forEach { p ->
                val norm = normKey(p.unitName)
                if (norm.isNotBlank()) {
                    displayNameByUnit.putIfAbsent(norm, p.unitName)
                    if (p.cost in 1..5) costByUnit.putIfAbsent(norm, p.cost)
                    if (compsByUnit[norm]?.none { it.id == comp.id } != false) {
                        compsByUnit.getOrPut(norm) { mutableListOf() }.add(comp)
                    }
                    val counts = itemFreqByUnit.getOrPut(norm) { LinkedHashMap() }
                    val weight = if (p.isCarry) 2 else 1
                    p.items.forEach { rawItem ->
                        val label = formatRiotAssetLabel(rawItem).ifBlank { rawItem.trim() }
                        if (label.isNotBlank()) counts[label] = (counts[label] ?: 0) + weight
                    }
                }
            }
        }

        val hasSyncedNinjaComps = liveComps.isNotEmpty() || normalizedNinjaRecs.isNotEmpty()

        fun resolveNinjaItemsFor(norm: String, fallback: List<String>): List<String> {
            val merged = LinkedHashMap<String, Int>()
            // 1. Direct tft.ninja champion/unit page item recommendations
            normalizedNinjaRecs[norm]?.recommendedItems.orEmpty().forEachIndexed { idx, rawItem ->
                val label = formatRiotAssetLabel(rawItem).ifBlank { rawItem.trim() }
                if (label.isNotBlank()) {
                    val weight = (12 - idx).coerceAtLeast(3)
                    merged[label] = (merged[label] ?: 0) + weight
                }
            }
            // 2. Per-unit items from tft.ninja meta comps
            itemFreqByUnit[norm]?.forEach { (label, freq) ->
                merged[label] = (merged[label] ?: 0) + freq * 3
            }
            val ranked = merged.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .distinct()
                .take(6)
            return if (ranked.isNotEmpty()) ranked else fallback
        }

        fun resolveNinjaCompTitlesFor(norm: String): List<String> {
            val fromComps = compsByUnit[norm].orEmpty()
                .sortedWith(compareBy<MetaComp> { it.tier.sortOrder }.thenBy { it.avgPlacement })
                .map { it.title }
            val fromSite = normalizedNinjaRecs[norm]?.recommendedCompTitles.orEmpty()
            return (fromComps + fromSite).distinct().take(6)
        }

        val byNormName = LinkedHashMap<String, ChampionTierEntry>()
        existing.forEach { champ ->
            val norm = normKey(champ.name)
            val matchedComps = compsByUnit[norm].orEmpty()
            val ninjaRec = normalizedNinjaRecs[norm]
            val carryCount = matchedComps.count { normKey(it.carryName) == norm }
            val topItems = resolveNinjaItemsFor(norm, fallback = champ.bestItems)

            val updatedTier = ninjaRec?.tier ?: when {
                carryCount > 0 && matchedComps.any { it.tier == TierRank.S } -> TierRank.S
                carryCount > 0 -> TierRank.S
                matchedComps.isNotEmpty() -> matchedComps.minBy { it.tier.sortOrder }.tier
                else -> champ.tier
            }
            val updatedAvg = ninjaRec?.avgPlacement ?: if (matchedComps.isNotEmpty()) {
                val base = matchedComps.map { it.avgPlacement }.average() - if (carryCount > 0) 0.12 else 0.0
                (base.coerceIn(3.15, 4.75) * 100.0).roundToInt() / 100.0
            } else {
                champ.avgPlacement
            }
            val updatedTop4 = ninjaRec?.top4Rate ?: if (matchedComps.isNotEmpty()) {
                ((100.0 - (updatedAvg - 1.5) * 18.0).coerceIn(44.0, 74.0) * 10.0).roundToInt() / 10.0
            } else {
                champ.top4Rate
            }

            // Strictly replace synergyComps with tft.ninja comps so non-tft.ninja comp titles never leak in
            val resolvedSynergies = if (hasSyncedNinjaComps) {
                resolveNinjaCompTitlesFor(norm)
            } else {
                emptyList()
            }

            byNormName[norm] = champ.copy(
                tier = updatedTier,
                avgPlacement = updatedAvg,
                top4Rate = updatedTop4,
                bestItems = topItems,
                synergyComps = resolvedSynergies
            )
        }

        // Ensure every champion referenced in tft.ninja comps or champion recommendations exists in catalog
        val envPrefix = if (targetEnv == TftEnvironment.PBE) "pbe" else "live"
        displayNameByUnit.forEach { (norm, displayName) ->
            if (byNormName.containsKey(norm)) return@forEach
            val matchedComps = compsByUnit[norm].orEmpty()
            val ninjaRec = normalizedNinjaRecs[norm]
            val cost = costByUnit[norm]
                ?: TftAssetIconResolver.getChampionCost(displayName, targetEnv)
                ?: 3
            val traits = TftAssetIconResolver.getChampionTraits(displayName, targetEnv)
            val isCarry = matchedComps.any { normKey(it.carryName) == norm }
            val tier = ninjaRec?.tier ?: when {
                isCarry -> TierRank.S
                matchedComps.isNotEmpty() -> matchedComps.minBy { it.tier.sortOrder }.tier
                cost >= 4 -> TierRank.A
                else -> TierRank.B
            }
            val avg = ninjaRec?.avgPlacement ?: if (matchedComps.isNotEmpty()) {
                (matchedComps.map { it.avgPlacement }.average().coerceIn(3.15, 4.75) * 100.0).roundToInt() / 100.0
            } else {
                if (cost >= 4) 3.92 else 4.22
            }
            val top4 = ninjaRec?.top4Rate ?: avgToTop4(avg)
            byNormName[norm] = ChampionTierEntry(
                id = "${envPrefix}_$norm",
                name = displayName,
                cost = cost.coerceIn(1, 5),
                tier = tier,
                environment = targetEnv,
                traits = traits,
                role = if (isCarry) "Carry" else if (cost >= 4) "Flex Carry" else "Core Unit",
                avgPlacement = avg,
                top4Rate = top4,
                bestItems = resolveNinjaItemsFor(norm, fallback = emptyList()),
                synergyComps = resolveNinjaCompTitlesFor(norm)
            )
        }

        // Merge any newly added live-set champions from Data Dragon
        if (!ddragonChampJson.isNullOrBlank()) {
            val setToken = "TFT${liveSetNumber}_"
            runCatching {
                val data = JSONObject(ddragonChampJson).optJSONObject("data") ?: return@runCatching
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = data.optJSONObject(key) ?: continue
                    val id = obj.optString("id", key)
                    val fullImg = obj.optJSONObject("image")?.optString("full").orEmpty()
                    if (!id.startsWith(setToken, ignoreCase = true) && !fullImg.contains("TFT_Set$liveSetNumber", ignoreCase = true)) {
                        continue
                    }
                    val name = obj.optString("name").trim()
                    if (name.isBlank() || name.contains("null", ignoreCase = true)) continue
                    val norm = normKey(name)
                    if (byNormName.containsKey(norm)) continue
                    val cost = obj.optInt("tier", obj.optInt("cost", 3)).coerceIn(1, 5)
                    val traits = TftAssetIconResolver.getChampionTraits(name, TftEnvironment.LIVE)
                    val matchedComps = compsByUnit[norm].orEmpty()
                    byNormName[norm] = ChampionTierEntry(
                        id = "live_$norm",
                        name = name,
                        cost = cost,
                        tier = if (matchedComps.isNotEmpty()) matchedComps.minBy { it.tier.sortOrder }.tier else if (cost >= 4) TierRank.A else TierRank.B,
                        environment = TftEnvironment.LIVE,
                        traits = traits,
                        role = if (cost >= 4) "Carry" else "Trait Unit",
                        avgPlacement = if (cost >= 4) 3.92 else 4.22,
                        top4Rate = if (cost >= 4) 56.4 else 51.0,
                        bestItems = resolveNinjaItemsFor(norm, fallback = emptyList()),
                        synergyComps = resolveNinjaCompTitlesFor(norm)
                    )
                }
            }
        }

        return byNormName.values.sortedWith(
            compareBy<ChampionTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenByDescending { it.cost }
                .thenBy { it.name }
        )
    }

    private fun refreshLiveItems(
        existing: List<ItemTierEntry>,
        liveComps: List<MetaComp>,
        liveSetNumber: Int,
        ddragonItemJson: String?,
        academyTiers: TftAcademyCatalogTiers?,
        targetEnv: TftEnvironment = TftEnvironment.LIVE
    ): List<ItemTierEntry> {
        val holdersByItem = LinkedHashMap<String, MutableList<String>>()
        liveComps.forEach { comp ->
            comp.hexPlacements.forEach { p ->
                p.items.forEach { rawItem ->
                    val label = formatRiotAssetLabel(rawItem)
                    holdersByItem.getOrPut(normKey(rawItem)) { mutableListOf() }.add(p.unitName)
                    holdersByItem.getOrPut(normKey(label)) { mutableListOf() }.add(p.unitName)
                }
            }
        }

        val byNormName = LinkedHashMap<String, ItemTierEntry>()
        existing.forEach { item ->
            val norm = normKey(item.name)
            val apiTier = academyTiers?.itemTiersByKey?.get(norm)
            val newTier = apiTier ?: item.tier
            val newAvg = if (apiTier != null) tierToAvg(apiTier) else item.avgPlacement
            val newTop4 = if (apiTier != null) avgToTop4(newAvg) else item.top4Rate
            val holders = holdersByItem[norm]?.distinct()?.take(4).orEmpty().ifEmpty { item.bestHolders }
            byNormName[norm] = item.copy(
                tier = newTier,
                avgPlacement = newAvg,
                top4Rate = newTop4,
                bestHolders = holders
            )
        }

        // Merge any new active Set items from Data Dragon or TFTAcademy
        if (!ddragonItemJson.isNullOrBlank()) {
            val setPrefix = "TFT${liveSetNumber}_Item_"
            runCatching {
                val data = JSONObject(ddragonItemJson).optJSONObject("data") ?: return@runCatching
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!key.startsWith(setPrefix, ignoreCase = true) && !key.startsWith("TFT_Item_", ignoreCase = true)) {
                        continue
                    }
                    if (key.contains("Consumable", true) || key.contains("Debug", true) ||
                        key.contains("Tutorial", true) || key.contains("Corrupted", true)
                    ) {
                        continue
                    }
                    val obj = data.optJSONObject(key) ?: continue
                    val name = obj.optString("name").trim()
                    if (name.isBlank() || name.contains("null", true)) continue
                    val norm = normKey(name)
                    if (byNormName.containsKey(norm)) continue

                    // Skip obsolete Set emblems not in live TFTAcademy tierlist unless current set
                    if (name.contains("Emblem", true) &&
                        !key.startsWith(setPrefix, true) &&
                        academyTiers?.itemTiersByKey?.containsKey(norm) != true
                    ) {
                        continue
                    }

                    val tier = academyTiers?.itemTiersByKey?.get(norm) ?: TierRank.B
                    val avg = tierToAvg(tier)
                    byNormName[norm] = ItemTierEntry(
                        id = "live_item_$norm",
                        name = name,
                        tier = tier,
                        environment = TftEnvironment.LIVE,
                        category = classifyItemCategory(name),
                        components = emptyList(),
                        statsSummary = "$name — Set $liveSetNumber item.",
                        avgPlacement = avg,
                        top4Rate = avgToTop4(avg),
                        bestHolders = holdersByItem[norm]?.distinct()?.take(4).orEmpty()
                    )
                }
            }
        }

        // Guarantee any item listed in TFTAcademy item tierlist is represented
        val itemPrefix = if (targetEnv == TftEnvironment.PBE) "pbe_item" else "live_item"
        val envDesc = if (targetEnv == TftEnvironment.PBE) "PBE item" else "live item"
        academyTiers?.itemTiersByKey?.forEach { (rawOrNorm, rank) ->
            val label = formatRiotAssetLabel(rawOrNorm)
            val norm = normKey(label)
            if (norm.isBlank() || byNormName.containsKey(norm)) return@forEach
            if (rawOrNorm == norm) return@forEach // skip duplicate normalized keys; only iterate raw IDs
            val avg = tierToAvg(rank)
            byNormName[norm] = ItemTierEntry(
                id = "${itemPrefix}_$norm",
                name = label,
                tier = rank,
                environment = targetEnv,
                category = classifyItemCategory(label),
                components = emptyList(),
                statsSummary = "$label — Set $liveSetNumber $envDesc.",
                avgPlacement = avg,
                top4Rate = avgToTop4(avg),
                bestHolders = holdersByItem[norm]?.distinct()?.take(4).orEmpty()
            )
        }

        return byNormName.values.sortedWith(
            compareBy<ItemTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.name }
        )
    }

    private fun refreshLiveAugments(
        existing: List<AugmentTierEntry>,
        liveComps: List<MetaComp>,
        liveSetNumber: Int,
        ddragonAugmentJson: String?,
        academyTiers: TftAcademyCatalogTiers?,
        targetEnv: TftEnvironment = TftEnvironment.LIVE
    ): List<AugmentTierEntry> {
        val compsByAugment = LinkedHashMap<String, MutableList<String>>()
        liveComps.forEach { comp ->
            comp.augmentPriorities.forEach { aug ->
                compsByAugment.getOrPut(normKey(aug)) { mutableListOf() }.add(comp.title)
                compsByAugment.getOrPut(normKey(formatRiotAssetLabel(aug))) { mutableListOf() }.add(comp.title)
            }
        }

        val byNormName = LinkedHashMap<String, AugmentTierEntry>()
        existing.forEach { aug ->
            val norm = normKey(aug.name)
            val apiTier = academyTiers?.augmentTiersByKey?.get(norm)
            val apiRank = academyTiers?.augmentRanksByKey?.get(norm)
            val newTier = apiTier ?: aug.tier
            val newAvg = if (apiTier != null) tierToAvg(apiTier) else aug.avgPlacement
            val newTop4 = if (apiTier != null) avgToTop4(newAvg) else aug.top4Rate
            val matchedComps = if (liveComps.isNotEmpty()) {
                compsByAugment[norm]?.distinct()?.take(4).orEmpty()
            } else {
                emptyList()
            }
            byNormName[norm] = aug.copy(
                tier = newTier,
                augmentTier = apiRank ?: aug.augmentTier,
                avgPlacement = newAvg,
                top4Rate = newTop4,
                synergyComps = matchedComps
            )
        }

        // Merge any new active Set augments from Data Dragon
        if (!ddragonAugmentJson.isNullOrBlank()) {
            val setToken = "DA_${liveSetNumber}_"
            val setAugToken = "TFT${liveSetNumber}_Augment_"
            runCatching {
                val data = JSONObject(ddragonAugmentJson).optJSONObject("data") ?: return@runCatching
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = data.optJSONObject(key) ?: continue
                    val fullImg = obj.optJSONObject("image")?.optString("full").orEmpty()
                    if (fullImg.startsWith("Missing-", ignoreCase = true)) continue
                    val name = obj.optString("name").trim()
                    if (name.isBlank() || name.contains("null", true)) continue
                    val norm = normKey(name)
                    if (byNormName.containsKey(norm)) continue

                    val isCurrentSet = key.startsWith(setToken, true) ||
                        key.startsWith(setAugToken, true) ||
                        academyTiers?.augmentTiersByKey?.containsKey(norm) == true
                    if (!isCurrentSet) continue

                    val tier = academyTiers?.augmentTiersByKey?.get(norm) ?: TierRank.B
                    val rank = academyTiers?.augmentRanksByKey?.get(norm) ?: when {
                        name.contains("Crown", true) || name.contains("Prismatic", true) -> AugmentTier.PRISMATIC
                        name.endsWith(" I") -> AugmentTier.SILVER
                        else -> AugmentTier.GOLD
                    }
                    val avg = tierToAvg(tier)
                    byNormName[norm] = AugmentTierEntry(
                        id = "live_aug_$norm",
                        name = name,
                        tier = tier,
                        augmentTier = rank,
                        environment = TftEnvironment.LIVE,
                        category = if (name.contains("Emblem", true) || name.contains("Crest", true) || name.contains("Crown", true)) {
                            "Trait / Emblem"
                        } else {
                            "Combat / Items"
                        },
                        avgPlacement = avg,
                        top4Rate = avgToTop4(avg),
                        description = "$name — Set $liveSetNumber augment.",
                        synergyComps = compsByAugment[norm]?.distinct()?.take(3).orEmpty()
                    )
                }
            }
        }

        // Guarantee any augment in TFTAcademy augment tierlist is represented
        val augPrefix = if (targetEnv == TftEnvironment.PBE) "pbe_aug" else "live_aug"
        val envAugDesc = if (targetEnv == TftEnvironment.PBE) "PBE augment" else "live augment"
        academyTiers?.augmentTiersByKey?.forEach { (rawOrNorm, rank) ->
            val label = formatRiotAssetLabel(rawOrNorm)
            val norm = normKey(label)
            if (norm.isBlank() || byNormName.containsKey(norm)) return@forEach
            if (rawOrNorm == norm) return@forEach
            val avg = tierToAvg(rank)
            val augTier = academyTiers.augmentRanksByKey[rawOrNorm] ?: AugmentTier.GOLD
            byNormName[norm] = AugmentTierEntry(
                id = "${augPrefix}_$norm",
                name = label,
                tier = rank,
                augmentTier = augTier,
                environment = targetEnv,
                category = if (label.contains("Emblem", true) || label.contains("Crest", true) || label.contains("Crown", true)) {
                    "Trait / Emblem"
                } else {
                    "Combat / Items"
                },
                avgPlacement = avg,
                top4Rate = avgToTop4(avg),
                description = "$label — Set $liveSetNumber $envAugDesc.",
                synergyComps = compsByAugment[norm]?.distinct()?.take(3).orEmpty()
            )
        }

        return byNormName.values.sortedWith(
            compareBy<AugmentTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.name }
        )
    }

    fun classifyItemCategory(name: String, desc: String = ""): ItemCategory {
        val n = name.lowercase(Locale.US)
        val d = desc.lowercase(Locale.US)
        return when {
            n.contains("guinsoo") || n.contains("nashor") || n.contains("red buff") ||
                n.contains("kraken") || n.contains("statikk") || n.contains("recurve") ||
                n.contains("flicker") || n.contains("rapid firecannon") || n.contains("wit's end") -> ItemCategory.ATTACK_SPEED
            n.contains("warmog") || n.contains("gargoyle") || n.contains("bramble") ||
                n.contains("dragon's claw") || n.contains("sunfire") || n.contains("spirit visage") ||
                n.contains("steadfast") || n.contains("protector's vow") || n.contains("evenshroud") ||
                n.contains("adaptive helm") || n.contains("quicksilver") || n.contains("vest") ||
                n.contains("cloak") || n.contains("belt") || n.contains("hullcrusher") ||
                n.contains("mogul") || n.contains("lightshield") || n.contains("eternal pact") -> ItemCategory.TANK
            n.contains("blue buff") || n.contains("shojin") || n.contains("jeweled") ||
                n.contains("rabadon") || n.contains("archangel") || n.contains("morello") ||
                n.contains("ionic") || n.contains("crownguard") || n.contains("hextech") ||
                n.contains("void staff") || n.contains("rod") || n.contains("tear") ||
                n.contains("zhonya") || n.contains("lich bane") || n.contains("blighting") ||
                n.contains("dawncore") || n.contains("luden") -> ItemCategory.AP
            n.contains("infinity edge") || n.contains("last whisper") || n.contains("giant slayer") ||
                n.contains("deathblade") || n.contains("striker") || n.contains("guardbreaker") ||
                n.contains("runaan") || n.contains("b.f. sword") || n.contains("collector") ||
                n.contains("prowler") || n.contains("fishbones") -> ItemCategory.AD
            n.contains("bloodthirster") || n.contains("titan") || n.contains("sterak") ||
                n.contains("hand of justice") || n.contains("edge of night") ||
                n.contains("death's defiance") || n.contains("tactician") -> ItemCategory.FIGHTER
            d.contains("ability power") || d.contains("mana") -> ItemCategory.AP
            d.contains("attack speed") -> ItemCategory.ATTACK_SPEED
            d.contains("armor") || d.contains("magic resist") || d.contains("health") -> ItemCategory.TANK
            d.contains("attack damage") || d.contains("crit") -> ItemCategory.AD
            else -> ItemCategory.FIGHTER
        }
    }

    private fun tierToAvg(tier: TierRank): Double = when (tier) {
        TierRank.S -> 3.52
        TierRank.A -> 3.84
        TierRank.B -> 4.18
        TierRank.C -> 4.46
        TierRank.X -> 4.30
    }

    private fun avgToTop4(avg: Double): Double =
        ((100.0 - (avg - 1.5) * 18.0).coerceIn(44.0, 74.0) * 10.0).roundToInt() / 10.0

    private fun normKey(raw: String): String =
        raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}
