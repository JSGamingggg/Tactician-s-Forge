package com.example

import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.data.network.TftNinjaCompsScraper
import com.example.ui.components.TftAssetIconResolver
import com.example.ui.components.formatChampionDisplayName
import com.example.ui.components.formatRiotAssetLabel
import com.example.ui.components.sanitizeTftDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fast host-JVM unit tests (no Android org.json or Robolectric dependencies).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun `sortCompsTierSDown orders LIVE and PBE comps strictly S down with avgPlacement and top4 tie breaks`() {
        fun comp(title: String, tier: TierRank, avg: Double, top4: Double, env: TftEnvironment) = MetaComp(
            id = "tftninja_${env.name.lowercase()}_${title.lowercase().replace(' ', '_')}",
            title = title,
            environment = env,
            tier = tier,
            playstyle = "Fast 8",
            setName = env.setName,
            patchTag = if (env == TftEnvironment.LIVE) "18.8" else "19.1-PBE",
            carryName = "Ahri",
            avgPlacement = avg,
            top4Rate = top4,
            winRate = 18.0,
            pickRate = 8.0,
            isTrending = false,
            trendDelta = "",
            traits = listOf("6 Arcanist"),
            coreUnits = listOf("Ahri", "Ornn"),
            hexPlacements = emptyList(),
            augmentPriorities = emptyList(),
            levelingGuide = "",
            notes = "",
            sourceUrl = if (env == TftEnvironment.LIVE) {
                TftNinjaCompsScraper.TFTNINJA_LIVE_COMPS_URL
            } else {
                TftNinjaCompsScraper.TFTNINJA_PBE_COMPS_URL
            }
        )

        val unsortedLive = listOf(
            comp("C-Tier Flex", TierRank.C, 4.60, 48.0, TftEnvironment.LIVE),
            comp("A-Tier Tempo", TierRank.A, 3.95, 58.0, TftEnvironment.LIVE),
            comp("X-Tier Gamble", TierRank.X, 4.40, 50.0, TftEnvironment.LIVE),
            comp("S-Tier Runner Up", TierRank.S, 3.78, 63.0, TftEnvironment.LIVE),
            comp("B-Tier Reroll", TierRank.B, 4.25, 53.0, TftEnvironment.LIVE),
            comp("S-Tier Best", TierRank.S, 3.62, 66.0, TftEnvironment.LIVE)
        )
        val sortedLive = TftNinjaCompsScraper.sortCompsTierSDown(unsortedLive)
        assertEquals(
            listOf(TierRank.S, TierRank.S, TierRank.A, TierRank.B, TierRank.C, TierRank.X),
            sortedLive.map { it.tier }
        )
        assertEquals("S-Tier Best", sortedLive[0].title)
        assertEquals("S-Tier Runner Up", sortedLive[1].title)

        val unsortedPbe = listOf(
            comp("PBE B-Tier", TierRank.B, 4.30, 52.0, TftEnvironment.PBE),
            comp("PBE S-Tier", TierRank.S, 3.55, 68.0, TftEnvironment.PBE),
            comp("PBE C-Tier", TierRank.C, 4.65, 46.0, TftEnvironment.PBE),
            comp("PBE A-Tier", TierRank.A, 3.92, 60.0, TftEnvironment.PBE)
        )
        val sortedPbe = TftNinjaCompsScraper.sortCompsTierSDown(unsortedPbe)
        assertEquals(
            listOf(TierRank.S, TierRank.A, TierRank.B, TierRank.C),
            sortedPbe.map { it.tier }
        )
        assertEquals("PBE S-Tier", sortedPbe[0].title)
    }

    @Test
    fun `placeholder icons are rejected and names and descriptions are cleanly formatted`() {
        assertTrue(
            TftAssetIconResolver.isPlaceholderIconUrl(
                "https://raw.communitydragon.org/latest/game/assets/maps/particles/tft/item_icons/augments/choiceui/missing-t2.png"
            )
        )
        assertTrue(TftAssetIconResolver.isPlaceholderIconUrl("https://cdn.example.com/missing-t3.png"))
        assertFalse(
            TftAssetIconResolver.isPlaceholderIconUrl(
                "https://raw.communitydragon.org/latest/game/assets/maps/particles/tft/item_icons/augments/choiceui/jeweledlotus2.png"
            )
        )

        assertEquals("Kai'Sa", formatChampionDisplayName("TFT19_Kaisa"))
        assertEquals("Graves", formatChampionDisplayName("TFT19_Graves"))
        assertEquals("Blue Buff", formatRiotAssetLabel("TFT_Item_BlueBuff"))
        assertEquals(
            "Gain a champion.",
            sanitizeTftDescription("Gain a champion. {Augment.Variant.RivalsAugment.AbilityTooltip}")
        )
        assertEquals(
            "Deal (AP) magic damage and stun.",
            sanitizeTftDescription("Deal %i:scaleAP% magic damage and stun. @Damage@")
        )
    }
}


