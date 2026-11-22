package com.example

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.HexUnitPlacement
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.data.network.TftAcademyCompsScraper
import com.example.data.network.TftipsPatchScraper
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.MetaCatalogData
import com.example.data.repository.MetaRepository
import com.example.data.repository.OfflineMetaBundle
import com.example.data.repository.OfflineMetaCacheStore
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private fun sampleTftAcademyComp(
        id: String,
        title: String,
        environment: TftEnvironment,
        tier: TierRank = TierRank.S,
        carry: String = "Ahri"
    ) = MetaComp(
        id = id,
        title = title,
        environment = environment,
        tier = tier,
        playstyle = "Fast 8",
        setName = environment.setName,
        patchTag = if (environment == TftEnvironment.LIVE) "18.8" else "19.1-PBE",
        carryName = carry,
        avgPlacement = 3.55,
        top4Rate = 64.2,
        winRate = 18.1,
        pickRate = 8.4,
        isTrending = true,
        trendDelta = "Top Meta",
        traits = listOf("6 Elderwood", "4 Arcanist"),
        coreUnits = listOf("Ornn", "Lillia", carry, "Veigar", "Nami"),
        hexPlacements = listOf(
            HexUnitPlacement("Ornn", 0, 3, cost = 4),
            HexUnitPlacement(carry, 3, 6, cost = 4, isCarry = true, items = listOf("Blue Buff", "Jeweled Gauntlet"))
        ),
        augmentPriorities = listOf("Jeweled Lotus II", "Level Up!"),
        levelingGuide = "Fast 8 on 4-2",
        notes = "Synced from tft.ninja",
        sourceUrl = if (environment == TftEnvironment.PBE) {
            com.example.data.network.TftNinjaCompsScraper.TFTNINJA_PBE_COMPS_URL
        } else {
            com.example.data.network.TftNinjaCompsScraper.TFTNINJA_LIVE_COMPS_URL
        }
    )

    @Test
    fun `offline cache store removes non-tftninja comps and persists tftninja comps exclusively`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineMetaCacheStore(context)

        // All hardcoded legacy comps must be removed
        assertTrue(MetaCatalogData.liveComps.isEmpty())
        assertTrue(MetaCatalogData.pbeComps.isEmpty())

        val seeded = store.readOrSeedBundle()
        assertTrue(store.hasPersistedCache())
        // Cold start must contain ZERO third-party/preloaded armies until synced from tft.ninja
        assertTrue(seeded.liveComps.isEmpty())
        assertTrue(seeded.pbeComps.isEmpty())
        assertEquals(0, seeded.toCacheStatus(environment = TftEnvironment.LIVE).compsCount)
        assertEquals(0, seeded.toCacheStatus(environment = TftEnvironment.PBE).compsCount)
        // Preloaded champions & augments must not carry stale non-tft.ninja synergy comp titles
        assertTrue(seeded.liveChampions.all { it.synergyComps.isEmpty() })
        assertTrue(seeded.liveAugments.all { it.synergyComps.isEmpty() })

        // Complete Set 18 LIVE + Set 19 PBE catalogs preloaded cleanly for offline viewing
        assertEquals(74, seeded.liveChampions.size)
        assertTrue(seeded.liveChampions.all { it.traits.isNotEmpty() && it.abilityDescription.isNotBlank() })
        assertEquals(94, seeded.pbeChampions.size)
        assertTrue(seeded.pbeChampions.all { it.environment == TftEnvironment.PBE && it.traits.isNotEmpty() })
        assertTrue(seeded.pbeChampions.count { it.abilityDescription.isNotBlank() } >= 80)
        assertEquals(94, seeded.pbeChampionIcons.size)
        assertTrue(seeded.pbeTraitIcons.size >= 40)
        assertTrue(seeded.liveItems.size >= 140)
        assertTrue(seeded.liveItems.all { it.statsSummary.isNotBlank() })
        assertTrue(seeded.liveAugments.size >= 340)
        assertTrue(seeded.liveAugments.all { it.description.isNotBlank() })
        assertTrue(seeded.pbeItems.size >= 80)
        assertTrue(seeded.pbeItems.all { it.environment == TftEnvironment.PBE && it.statsSummary.isNotBlank() })
        assertTrue(seeded.pbeAugments.size >= 240)
        assertTrue(seeded.pbeAugments.all { it.environment == TftEnvironment.PBE && it.description.isNotBlank() })
        assertTrue(seeded.pbeItemIcons.size >= 80)

        val ninjaLive = sampleTftAcademyComp("tftninja_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val ninjaPbe = sampleTftAcademyComp("tftninja_pbe_ionic_spark_hitmakers", "Ionic Spark Hitmakers", TftEnvironment.PBE, carry = "Jinx")
        val customLive = ninjaLive.copy(id = "custom_my_comp", title = "My Custom Comp", sourceUrl = "")
        val academyLive = ninjaLive.copy(id = "tftacademy_live_ahri", sourceUrl = TftAcademyCompsScraper.TFTACADEMY_COMPS_URL)
        val legacyComp = ninjaLive.copy(id = "legacy_comp", sourceUrl = "https://legacy.example/comps")
        val academyPbe = ninjaPbe.copy(id = "tftacademy_pbe_comp", sourceUrl = TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL)
        val legacyPbeComp = ninjaPbe.copy(id = "legacy_tftactics_pbe", sourceUrl = "https://tftactics.gg/tierlist/team-comps/")

        val updated = seeded.copy(
            liveVersionInfo = seeded.liveVersionInfo.copy(
                livePatchVersion = "18.8",
                pbePatchVersion = "19.1-PBE"
            ),
            liveComps = listOf(ninjaLive, customLive, academyLive, legacyComp),
            pbeComps = listOf(ninjaPbe, academyPbe, legacyPbeComp)
        )
        assertTrue(store.writeBundle(updated))

        val reloaded = store.readOrSeedBundle()
        assertEquals("18.8", reloaded.liveVersionInfo.livePatchVersion)
        assertEquals("19.1-PBE", reloaded.liveVersionInfo.pbePatchVersion)
        // ALL non-tft.ninja armies (including TFTAcademy, TFTactics, and legacy) are strictly stripped
        assertEquals(listOf("tftninja_live_ahri", "custom_my_comp"), reloaded.liveComps.map { it.id })
        assertEquals(listOf("tftninja_pbe_ionic_spark_hitmakers"), reloaded.pbeComps.map { it.id })
    }

    @Test
    fun `local preferences store persists guest mode choice`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsStore = LocalPreferencesStore(context)

        prefsStore.setGuestModeChoice(true)
        assertTrue(prefsStore.isGuestModeFlow.first())

        prefsStore.setGuestModeChoice(false)
        assertFalse(prefsStore.isGuestModeFlow.first())
    }

    @Test
    fun `tftips patch scraper parses live and pbe patches from html and next data`() {
        val scraper = TftipsPatchScraper()
        val sampleHtml = """
            <html>
              <body>
                <h2>Patch 18.8 Live Balance Notes</h2>
                <ul>
                  <li>Ahri: Buff orb return damage 275 -> 310</li>
                  <li>Ornn: Nerf base armor 60 -> 55</li>
                </ul>
                <h2>Patch 18.9 PBE Preview Notes</h2>
                <ul>
                  <li>Xerath: New PBE True Damage charm scaling 18% -> 24%</li>
                </ul>
              </body>
            </html>
        """.trimIndent()

        val parsed = scraper.parseHtmlPatchSections(sampleHtml)
        val live = parsed.firstOrNull { it.environment == TftEnvironment.LIVE }
        val pbe = parsed.firstOrNull { it.environment == TftEnvironment.PBE }

        assertNotNull(live)
        assertNotNull(pbe)
        assertEquals("18.8", live?.version)
        assertTrue(pbe?.version?.contains("18.9") == true)
        assertEquals(TftipsPatchScraper.TFTIPS_PATCHES_URL, live?.sourceUrl)

        val realCardHtml = """
            <div>
              <a href="/patches/pbe">Set 18 → Set 19 Changes</a>
              <time datetime="2026-11-23">Mon, 11/23</time>
              <ul aria-label="Buffs">
                <li title="Ionic Spark"><a href="/items/ionic_spark" class="tft-icon icon-item"></a></li>
              </ul>
              <a href="/patches/18.7">Patch 18.7</a>
              <span>Latest</span>
              <time datetime="2026-11-18">Wed, 11/18</time>
              <ul aria-label="Buffs">
                <li title="Aphelios"><a href="/champs/18_aphelios" class="tft-icon icon-champ"></a></li>
                <li title="Inferno"><a href="/traits/18_inferno" class="tft-icon icon-trait"></a></li>
              </ul>
              <ul aria-label="Nerfs">
                <li title="Krug"><a href="/champs/18_krug" class="tft-icon icon-champ"></a></li>
              </ul>
            </div>
        """.trimIndent()
        val detailHtml187 = """
            <html><body>
              <h1>TFT Patch 18.7 Changes</h1>
              <h2>Buffs</h2>
              <h3 class="text-sm font-bold leading-tight">Aphelios</h3>
              <div>Blast Damage: <span class="tft-diff-change"><span class="tft-diff-change-old">490/735</span><span class="tft-diff-change-new">540/810</span></span></div>
              <h2>Nerfs</h2>
              <h3 class="text-sm font-bold leading-tight">Krug</h3>
              <div>Kruglette Health: <span class="tft-diff-change"><span class="tft-diff-change-old">182/338/608</span><span class="tft-diff-change-new">182/313/578</span></span></div>
            </body></html>
        """.trimIndent()

        val cardRecords = scraper.parseHtmlPatchCards(realCardHtml)
        val live187 = cardRecords.firstOrNull { it.environment == TftEnvironment.LIVE && it.version == "18.7" }
        val pbe191 = cardRecords.firstOrNull { it.environment == TftEnvironment.PBE }
        assertNotNull(live187)
        assertNotNull(pbe191)
        val enriched187 = scraper.enrichPatchWithDetailHtml(live187!!, detailHtml187)
        val apheliosChange = enriched187.balanceChanges.firstOrNull { it.targetName == "Aphelios" }
        assertEquals("490/735", apheliosChange?.previousValue)
        assertEquals("540/810", apheliosChange?.newValue)
    }

    @Test
    fun `tftacademy scraper parses next data and app router rsc chunks and enforces matching set`() {
        val scraper = TftAcademyCompsScraper()
        val liveHtml = """
            <html>
              <body>
                <h1>TFTAcademy Tier List - Set 18: Enchanted Wilds</h1>
                <script>
                  self.__next_f.push([1,"{\"guides\":[{\"title\":\"Elderwood Arcanists\",\"tier\":\"S\",\"playstyle\":\"Fast 8\",\"set\":\"Set 18\",\"carry\":\"Ahri\",\"stats\":{\"avgPlacement\":3.42,\"top4Rate\":66.8,\"winRate\":19.4,\"pickRate\":8.9},\"units\":[{\"name\":\"Ornn\",\"cost\":4,\"row\":0,\"col\":3},{\"name\":\"Lillia\",\"cost\":3,\"row\":0,\"col\":2},{\"name\":\"Ahri\",\"cost\":4,\"row\":3,\"col\":6,\"isCarry\":true,\"items\":[\"Blue Buff\",\"Jeweled Gauntlet\"]},{\"name\":\"Veigar\",\"cost\":4,\"row\":3,\"col\":5},{\"name\":\"Nami\",\"cost\":5,\"row\":3,\"col\":4}],\"traits\":[\"6 Elderwood\",\"4 Arcanist\"]},{\"title\":\"Stale Wrong Set Comp\",\"tier\":\"A\",\"playstyle\":\"Reroll\",\"set\":\"Set 19\",\"carry\":\"Lux\",\"units\":[\"Lux\",\"Garen\",\"Jarvan\",\"Sona\"]}]}"])
                </script>
              </body>
            </html>
        """.trimIndent()

        val pbeHtml = """
            <html>
              <body>
                <h1>TFTAcademy PBE Tier List - Set 19: Cyber Horizon PBE</h1>
                <script id="__NEXT_DATA__" type="application/json">
                {
                  "props": {
                    "pageProps": {
                      "pbeComps": [
                        {
                          "title": "Cybernetic Vayne",
                          "tier": "S",
                          "playstyle": "Fast 8",
                          "set": "Set 19",
                          "carry": "Vayne",
                          "units": ["Vayne", "Vi", "Leona", "Ekko", "Zeri"],
                          "traits": ["5 Cybernetic", "2 Sniper"]
                        }
                      ]
                    }
                  }
                }
                </script>
              </body>
            </html>
        """.trimIndent()

        val liveDoc = scraper.parseCompsDocument(liveHtml, TftEnvironment.LIVE, "18.8")
        val alignedLive = scraper.enforceSetAlignment(liveDoc.comps, liveDoc.detectedSetNumber, TftEnvironment.LIVE, "18.8")

        val pbeDoc = scraper.parseCompsDocument(pbeHtml, TftEnvironment.PBE, "19.1-PBE")
        val alignedPbe = scraper.enforceSetAlignment(pbeDoc.comps, pbeDoc.detectedSetNumber, TftEnvironment.PBE, "19.1-PBE")

        assertEquals(18, liveDoc.detectedSetNumber)
        assertEquals(1, alignedLive.size)
        val liveComp = alignedLive.first()
        assertEquals("Elderwood Arcanists", liveComp.title)
        assertEquals(3.42, liveComp.avgPlacement, 0.01)
        assertEquals(66.8, liveComp.top4Rate, 0.01)
        assertEquals(TftAcademyCompsScraper.TFTACADEMY_COMPS_URL, liveComp.sourceUrl)
        val ahriPlacement = liveComp.hexPlacements.first { it.unitName == "Ahri" }
        assertEquals(3, ahriPlacement.row)
        assertEquals(6, ahriPlacement.col)
        assertEquals(listOf("Blue Buff", "Jeweled Gauntlet"), ahriPlacement.items)

        assertEquals(19, pbeDoc.detectedSetNumber)
        assertEquals(1, alignedPbe.size)
        assertEquals("Cybernetic Vayne", alignedPbe.first().title)
        assertEquals(TftEnvironment.PBE, alignedPbe.first().environment)
        assertEquals(TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL, alignedPbe.first().sourceUrl)
    }

    @Test
    fun `main viewmodel loads guest mode offline catalog asynchronously with zero firestore access`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        val ninjaLive = sampleTftAcademyComp("tftninja_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val ninjaPbe = sampleTftAcademyComp("tftninja_pbe_vayne", "Cyber Vayne", TftEnvironment.PBE, carry = "Vayne")
        cacheStore.writeBundle(
            OfflineMetaBundle(
                liveComps = listOf(ninjaLive),
                pbeComps = listOf(ninjaPbe)
            )
        )
        val localStore = LocalPreferencesStore(context)
        val metaRepo = MetaRepository(cacheStore)

        val vm = MainViewModel(
            currentUserId = null,
            metaRepository = metaRepo,
            localStore = localStore,
            firestoreRepo = FirestoreUserRepository(context),
            autoSyncOnInit = false
        )

        // Allow async cache hydration coroutine on viewModelScope to complete
        repeat(20) {
            if (vm.metaComps.value.isNotEmpty() && vm.offlineCacheStatus.value.isReady) return@repeat
            delay(25)
        }

        assertTrue(vm.offlineCacheStatus.value.isReady)
        assertTrue(vm.metaComps.value.isNotEmpty())
        assertTrue(vm.championTiers.value.isNotEmpty())
        assertTrue(vm.itemTiers.value.isNotEmpty())
        assertTrue(vm.augmentTiers.value.isNotEmpty())

        // Verify all LIVE + PBE comps are available for the overlay drawer
        val allOverlayComps = metaRepo.getComps(TftEnvironment.LIVE) + metaRepo.getComps(TftEnvironment.PBE)
        assertTrue(allOverlayComps.any { it.environment == TftEnvironment.LIVE })
        assertTrue(allOverlayComps.any { it.environment == TftEnvironment.PBE })

        // Verify starring a comp saves it cleanly into local favorites & overlay pinned list
        val sampleComp = allOverlayComps.first()
        localStore.saveLocalFavorite(
            com.example.data.model.FavoriteCompDoc(
                id = sampleComp.id,
                userId = "local",
                compId = sampleComp.id,
                environment = sampleComp.environment.name,
                title = sampleComp.title,
                tier = sampleComp.tier.name,
                playstyle = sampleComp.playstyle,
                carryName = sampleComp.carryName,
                coreUnits = sampleComp.coreUnits,
                hexPositions = sampleComp.hexPlacements.map { it.encode() },
                pinnedToOverlay = true
            )
        )
        val favs = localStore.localFavoritesFlow.first()
        assertTrue(favs.any { it.compId == sampleComp.id })
    }

    @Test
    fun `asset icon resolver resolves champion item augment and trait urls and updates from json`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OfflineMetaCacheStore(context).readOrSeedBundle()
        val resolver = com.example.ui.components.TftAssetIconResolver
        val initialVersion = resolver.ddragonVersion
        val initialRevision = resolver.catalogRevision

        val ahriUrl = resolver.resolveChampionIconUrl("Ahri")
        val pebblesUrl = resolver.resolveChampionIconUrl("DA_18_Sentry")
        val brambleUrl = resolver.resolveChampionIconUrl("Brambleback")
        val ornnUrl = resolver.resolveChampionIconUrl("DA_18_Ornn")

        assertTrue(ahriUrl?.contains("img/") == true && ahriUrl.endsWith(".png"))
        assertTrue(pebblesUrl?.endsWith("DA_18_Sentry.TFT_Set18.png") == true)
        assertTrue(brambleUrl?.endsWith("DA_Brambleback18.TFT_Set18.png") == true)
        assertTrue(ornnUrl?.endsWith("Ornn_splash_centered_2.TFT_Set18.png") == true)
        assertEquals(
            "https://sunderarmor.com/characters/Skin/18/Pebbles.png",
            resolver.resolveSquareChampionPortraitUrl("DA_18_Sentry", TftEnvironment.LIVE)
        )
        assertEquals(
            "https://sunderarmor.com/characters/Skin/18/Khazix.png",
            resolver.resolveSquareChampionPortraitUrl("Kha'Zix", TftEnvironment.LIVE)
        )
        assertEquals(
            "https://sunderarmor.com/characters/Skin/18/Lux.png",
            resolver.resolveSquareChampionPortraitUrl("Lux (Elderwood)", TftEnvironment.LIVE)
        )
        assertEquals("Pebbles", com.example.ui.components.formatRiotAssetLabel("DA_18_Sentry"))
        assertEquals("Ravager Emblem", com.example.ui.components.formatRiotAssetLabel("DA_18_EmblemSlayer"))
        assertEquals(listOf("Elderwood", "Defender"), resolver.getChampionTraits("Ornn"))

        val blueBuffUrl = resolver.resolveItemIconUrl("Blue Buff")
        val nashorsUrl = resolver.resolveItemIconUrl("Nashor's Tooth")
        val redBuffUrl = resolver.resolveItemIconUrl("Red Buff")
        val emblemUrl = resolver.resolveItemIconUrl("DA_18_EmblemSlayer")
        val apiItemUrl = resolver.resolveItemIconUrl("TFT18_Item_JeweledGauntlet")

        assertTrue(blueBuffUrl?.endsWith(".png") == true)
        assertTrue(nashorsUrl?.endsWith(".png") == true)
        assertTrue(redBuffUrl?.endsWith(".png") == true)
        assertTrue(emblemUrl?.endsWith("DA_18_EmblemSlayer.png") == true)
        assertTrue(apiItemUrl?.endsWith("/img/tft-item/DA_JeweledGauntlet.png") == true)

        val levelUpUrl = resolver.resolveAugmentIconUrl("Level Up!")
        val floraUrl = resolver.resolveAugmentIconUrl("DA_18_FloraFatalisAugment")
        val wandOverflowUrl = resolver.resolveAugmentIconUrl("DA_WandOverflow")
        val riftbeastTraitUrl = resolver.resolveTraitIconUrl("3 Riftbeast")
        assertTrue(levelUpUrl?.let { it.endsWith(".png") || it.endsWith(".webp") } == true)
        assertTrue(floraUrl?.let { it.endsWith(".png") || it.endsWith(".webp") } == true)
        assertTrue(wandOverflowUrl?.contains("WandOverflow") == true)
        assertTrue(riftbeastTraitUrl?.contains("Trait_Icon_18_Riftbeast") == true)

        val champJson = """
            {
              "data": {
                "TFT18_Aurora": {
                  "name": "Aurora",
                  "image": { "full": "TFT18_Aurora.TFT_Set18.png", "group": "tft-champion" }
                },
                "TFT19_BaronNashor": {
                  "name": "Baron Nashor",
                  "image": { "full": "TFT19_BaronNashor.png", "group": "tft-champion" }
                }
              }
            }
        """.trimIndent()
        val itemJson = """
            {
              "data": {
                "TFT18_Item_ChronoBlade": {
                  "name": "Chrono Blade",
                  "image": { "full": "TFT18_Item_ChronoBlade.png", "group": "tft-item" }
                }
              }
            }
        """.trimIndent()
        val augmentJson = """
            {
              "data": {
                "TFT18_Augment_ElderwoodCrest": {
                  "name": "Elderwood Crest",
                  "image": { "full": "TFT18_Augment_ElderwoodCrest.png", "group": "tft-augment" }
                }
              }
            }
        """.trimIndent()

        resolver.updateDdragonCatalog(
            version = "15.23.1",
            championJson = champJson,
            itemJson = itemJson,
            augmentJson = augmentJson
        )

        assertTrue(resolver.catalogRevision > initialRevision)
        assertEquals(
            "https://ddragon.leagueoflegends.com/cdn/15.23.1/img/tft-champion/TFT18_Aurora.TFT_Set18.png",
            resolver.resolveChampionIconUrl("Aurora")
        )
        assertEquals(
            "https://ddragon.leagueoflegends.com/cdn/15.23.1/img/tft-champion/TFT19_BaronNashor.png",
            resolver.resolveChampionIconUrl("Baron Nashor")
        )
        assertEquals(
            "https://ddragon.leagueoflegends.com/cdn/15.23.1/img/tft-item/TFT18_Item_ChronoBlade.png",
            resolver.resolveItemIconUrl("Chrono Blade")
        )
        assertEquals(
            "https://ddragon.leagueoflegends.com/cdn/15.23.1/img/tft-augment/TFT18_Augment_ElderwoodCrest.png",
            resolver.resolveAugmentIconUrl("Elderwood Crest")
        )

        resolver.updateDdragonCatalog(version = initialVersion)
    }

    @Test
    fun `tftacademy scraper extracts all dom and link team comps across tiers without truncation`() {
        val scraper = TftAcademyCompsScraper()
        val cardsHtml = (1..15).joinToString("\n") { idx ->
            val tierChar = when {
                idx <= 5 -> "S"
                idx <= 10 -> "A"
                idx <= 13 -> "B"
                else -> "C"
            }
            """
            <a class="tftacademy-comp comp-card" href="/tierlist/comps/archetype-$idx">
              <div class="comp-header">
                <div class="tier-badge">$tierChar</div>
                <div class="comp-title">TFTAcademy Archetype $idx</div>
                <div class="comp-tempo">Fast 8</div>
              </div>
              <div class="comp-units">
                <img alt="Ornn" /><img alt="Ahri" /><img alt="Lillia" /><img alt="Syndra" /><img alt="Rakan" />
              </div>
            </a>
            """.trimIndent()
        }
        val html = "<html><body><h1>Set 18: Enchanted Wilds</h1>$cardsHtml</body></html>"
        val parsed = scraper.parseCompsDocument(html, TftEnvironment.LIVE, "18.6b", isDedicatedEnvPage = true)
        assertEquals(15, parsed.comps.size)
        assertTrue(parsed.comps.any { it.tier == TierRank.C })
        assertTrue(parsed.comps.all { it.sourceUrl == TftAcademyCompsScraper.TFTACADEMY_COMPS_URL })
    }

    @Test
    fun `made by credits string and meta update notifier diff detection work accurately`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val context: Context = app
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        assertEquals("Made By JS96 + MK", context.getString(R.string.made_by_credits))

        val prefsStore = LocalPreferencesStore(context)
        prefsStore.clearAllForTests()
        val initialLive = sampleTftAcademyComp("tftacademy_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val initialBundle = OfflineMetaBundle(liveComps = listOf(initialLive))
        val initialSync = com.example.data.model.MetaSyncResult(
            versionInfo = initialBundle.liveVersionInfo,
            cacheStatus = initialBundle.toCacheStatus(),
            newLivePatchDetected = false,
            newPbePatchDetected = false,
            newlyAddedComps = emptyList(),
            syncedFromNetwork = true
        )

        // Initial seed records baseline without duplicate comp spam
        val firstDispatch = com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = initialSync,
            bundle = initialBundle,
            prefsStore = prefsStore
        )
        assertEquals(0, firstDispatch.newCompsDetectedCount)

        // Second cycle introduces a new LIVE patch, a new PBE patch, and a brand-new composition
        val newComp = initialLive.copy(
            id = "tftacademy_live_lux_99",
            title = "Arcanist Lux Reroll"
        )
        val updatedBundle = initialBundle.copy(
            liveVersionInfo = initialBundle.liveVersionInfo.copy(
                livePatchVersion = "18.9",
                pbePatchVersion = "19.2-PBE"
            ),
            liveComps = listOf(newComp, initialLive)
        )
        val secondSync = com.example.data.model.MetaSyncResult(
            versionInfo = updatedBundle.liveVersionInfo,
            cacheStatus = updatedBundle.toCacheStatus(),
            newLivePatchDetected = true,
            newPbePatchDetected = true,
            newlyAddedComps = listOf(newComp),
            syncedFromNetwork = true
        )

        val secondDispatch = com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = secondSync,
            bundle = updatedBundle,
            prefsStore = prefsStore
        )
        assertTrue(secondDispatch.livePatchNotified)
        assertTrue(secondDispatch.pbePatchNotified)
        assertEquals(1, secondDispatch.newCompsDetectedCount)

        // Third unchanged cycle should not re-notify on the same versions or comps
        val repeatSync = secondSync.copy(
            newLivePatchDetected = false,
            newPbePatchDetected = false,
            newlyAddedComps = emptyList()
        )
        val thirdDispatch = com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = repeatSync,
            bundle = updatedBundle,
            prefsStore = prefsStore
        )
        assertFalse(thirdDispatch.livePatchNotified)
        assertFalse(thirdDispatch.pbePatchNotified)
        assertEquals(0, thirdDispatch.newCompsDetectedCount)
    }

    @Test
    fun `overlay edge snap decision detects left collapse right collapse and mid screen bubble`() {
        val leftSnap = com.example.overlay.computeEdgeSnapDecision(
            leftEdgePx = 18,
            viewWidthPx = 180,
            screenWidthPx = 1080,
            snapThresholdPx = 72
        )
        assertEquals(com.example.overlay.OverlayDockEdge.LEFT, leftSnap.dockEdge)
        assertTrue(leftSnap.shouldCollapseToEdge)

        val rightSnap = com.example.overlay.computeEdgeSnapDecision(
            leftEdgePx = 885,
            viewWidthPx = 180,
            screenWidthPx = 1080,
            snapThresholdPx = 72
        )
        assertEquals(com.example.overlay.OverlayDockEdge.RIGHT, rightSnap.dockEdge)
        assertTrue(rightSnap.shouldCollapseToEdge)

        val midScreen = com.example.overlay.computeEdgeSnapDecision(
            leftEdgePx = 450,
            viewWidthPx = 180,
            screenWidthPx = 1080,
            snapThresholdPx = 72
        )
        assertFalse(midScreen.shouldCollapseToEdge)
    }

    @Test
    fun `tftacademy scraper extracts nested comp arrays from js bundles and detects content edits via signature`() {
        val scraper = TftAcademyCompsScraper()
        val jsBundle = """
            var x=1;var comps=[
              {
                "title": "Shadow Isles Kalista",
                "tier": "S",
                "playstyle": "Fast 8",
                "set": "Set 18",
                "carry": "Kalista",
                "traits": [{"name": "5 Shadow Isles"}, {"name": "2 Challenger"}],
                "augments": ["Lotus I", "Cybernetic Uplink"],
                "units": [
                  {"name": "Yorick", "items": ["Warmog's Armor"]},
                  {"name": "Thresh", "items": []},
                  {"name": "Gwen", "items": ["Jeweled Gauntlet"]},
                  {"name": "Kalista", "items": ["Guinsoo's Rageblade", "Runaan's Hurricane"], "threeStar": true}
                ]
              },
              {
                "title": "Deep Scroll Bastion Aphelios",
                "tier": "C",
                "playstyle": "Reroll",
                "set": "Set 18",
                "carry": "Aphelios",
                "traits": ["4 Bastion"],
                "units": ["Poppy", "Leona", "Taric", "Aphelios"]
              }
            ];function noop(){}
        """.trimIndent()

        val extracted = scraper.extractCandidateCompArrays(jsBundle)
        assertEquals(1, extracted.size)

        val html = "<html><body><h1>Set 18</h1><script>${extracted.first()}</script></body></html>"
        val parsed = scraper.parseCompsDocument(html, TftEnvironment.LIVE, "18.8", isDedicatedEnvPage = true)
        assertEquals(2, parsed.comps.size)
        val kalistaComp = parsed.comps.firstOrNull { it.title == "Shadow Isles Kalista" && it.carryName == "Kalista" }
        assertNotNull(kalistaComp)
        assertEquals(3, kalistaComp?.hexPlacements?.first { it.unitName == "Kalista" }?.stars)
        assertTrue(parsed.comps.any { it.title == "Deep Scroll Bastion Aphelios" && it.tier == TierRank.C })

        val sigBefore = TftAcademyCompsScraper.computeCompsSignature(parsed.comps, emptyList())
        val mutatedComps = parsed.comps.map {
            if (it.title == "Deep Scroll Bastion Aphelios") it.copy(tier = TierRank.A) else it
        }
        val sigAfter = TftAcademyCompsScraper.computeCompsSignature(mutatedComps, emptyList())
        assertNotEquals(sigBefore, sigAfter)
    }

    @Test
    fun `overlay drawer dimensions default to 650x391dp in landscape and adapt safely to compact viewports`() {
        val portraitViewport = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 411, screenHeightDp = 915)
        assertFalse(portraitViewport.isLandscape)
        assertFalse(portraitViewport.useSideDetailPane)
        assertFalse(portraitViewport.useFixedDefaultHeight)
        assertEquals((411 - 16).dp, portraitViewport.widthDp)
        assertTrue(portraitViewport.widthDp > portraitViewport.maxHeightDp)
        assertTrue(portraitViewport.maxHeightDp.value < 915f)

        val landscapePhone = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 915, screenHeightDp = 411)
        assertTrue(landscapePhone.isLandscape)
        assertTrue(landscapePhone.useSideDetailPane)
        assertTrue(landscapePhone.useTwoColumnCards)
        assertTrue(landscapePhone.useFixedDefaultHeight)
        assertEquals(650.dp, landscapePhone.widthDp)
        assertEquals(391.dp, landscapePhone.maxHeightDp)
        assertEquals(
            391.dp,
            com.example.overlay.resolveEffectiveOverlayHeightDp(
                customHeightDp = null,
                defaultDims = landscapePhone
            )
        )
        assertEquals(
            null,
            com.example.overlay.resolveEffectiveOverlayHeightDp(
                customHeightDp = null,
                defaultDims = portraitViewport
            )
        )

        val compactLandscape = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 640, screenHeightDp = 360)
        assertTrue(compactLandscape.isLandscape)
        assertTrue(compactLandscape.useSideDetailPane)
        assertTrue(compactLandscape.useFixedDefaultHeight)
        assertEquals((640 - 16).dp, compactLandscape.widthDp)
        assertEquals((360 - 16).dp, compactLandscape.maxHeightDp)

        val tabletLandscape = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 1280, screenHeightDp = 800)
        assertTrue(tabletLandscape.isLandscape)
        assertTrue(tabletLandscape.useSideDetailPane)
        assertTrue(tabletLandscape.useFixedDefaultHeight)
        assertEquals(650.dp, tabletLandscape.widthDp)
        assertEquals(391.dp, tabletLandscape.maxHeightDp)

        assertTrue(com.example.overlay.shouldResetOverlaySizeForOrientationChange(previousIsLandscape = false, currentIsLandscape = true))
        assertTrue(com.example.overlay.shouldResetOverlaySizeForOrientationChange(previousIsLandscape = true, currentIsLandscape = false))
        assertFalse(com.example.overlay.shouldResetOverlaySizeForOrientationChange(previousIsLandscape = null, currentIsLandscape = true))
        assertFalse(com.example.overlay.shouldResetOverlaySizeForOrientationChange(previousIsLandscape = true, currentIsLandscape = true))
    }

    @Test
    fun `overlay window preserves device orientation when expanded and computes cutout safe bounds`() {
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            com.example.overlay.resolveOverlayWindowScreenOrientation(com.example.overlay.OverlayDisplayState.EXPANDED)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            com.example.overlay.resolveOverlayWindowScreenOrientation(com.example.overlay.OverlayDisplayState.BUBBLE)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            com.example.overlay.resolveOverlayWindowScreenOrientation(com.example.overlay.OverlayDisplayState.EDGE_COLLAPSED)
        )

        val landscapeBounds = com.example.overlay.OverlayWindowBounds(
            widthPx = 2400,
            heightPx = 1080,
            safeLeftPx = 96,
            safeTopPx = 48,
            safeRightPx = 0,
            safeBottomPx = 24,
            density = 3f
        )
        assertEquals(2304, landscapeBounds.usableWidthPx)
        assertEquals(1008, landscapeBounds.usableHeightPx)
    }

    @Test
    fun `google auth helper safely reports unconfigured firebase in robolectric runtime`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configured = com.example.ui.auth.GoogleAuthHelper.isFirebaseConfigured(context)
        assertFalse(configured)
    }

    @Test
    fun `groupCompsByTier orders tiers S to X and sorts comps inside each tier`() {
        val xTier = sampleTftAcademyComp("x1", "Wildcard Flex", TftEnvironment.LIVE, tier = TierRank.X).copy(avgPlacement = 4.35)
        val cTier = sampleTftAcademyComp("c1", "Flex Bastion", TftEnvironment.LIVE, tier = TierRank.C).copy(avgPlacement = 4.60)
        val aTier = sampleTftAcademyComp("a1", "Reroll Yone", TftEnvironment.LIVE, tier = TierRank.A).copy(avgPlacement = 4.05)
        val sSlow = sampleTftAcademyComp("s2", "Invoker Ahri", TftEnvironment.LIVE, tier = TierRank.S).copy(avgPlacement = 3.80)
        val sTop = sampleTftAcademyComp("s1", "Fast 9 Azir", TftEnvironment.LIVE, tier = TierRank.S).copy(avgPlacement = 3.12)

        val grouped = com.example.ui.screens.groupCompsByTier(listOf(xTier, cTier, aTier, sSlow, sTop))
        assertEquals(listOf(TierRank.S, TierRank.A, TierRank.C, TierRank.X), grouped.map { it.first })
        assertEquals(listOf("Fast 9 Azir", "Invoker Ahri"), grouped.first().second.map { it.title })
    }

    @Test
    fun `tftacademy scraper parses sveltekit fetched json body with finalComp apiName boardIndex tier X and filters drafts`() {
        val scraper = TftAcademyCompsScraper()
        val apiPayload = """
            {
              "guides": [
                {
                  "title": "Tempo Riftbeast",
                  "compSlug": "set-18-tempo-riftbeast",
                  "tier": "S",
                  "style": "Fast 9",
                  "set": 18,
                  "displayIndex": 0,
                  "isPublic": true,
                  "mainChampion": {"apiName": "DA_Brambleback18", "cost": 4},
                  "finalComp": [
                    {"apiName": "DA_18_ElderDragon", "boardIndex": 15, "items": ["DA_InfinityEdge", "DA_LastWhisper"], "stars": 2},
                    {"apiName": "DA_Brambleback18", "boardIndex": 2, "items": ["DA_WarmogsArmor"], "stars": 2},
                    {"apiName": "DA_18_Ezreal", "boardIndex": 27, "items": ["DA_GuinsoosRageblade"], "stars": 1},
                    {"apiName": "DA_18_GnarSmall", "boardIndex": 3, "items": [], "stars": 1}
                  ],
                  "augments": [
                    {"apiName": "DA_VerticalityII", "disabled": false},
                    {"apiName": "DA_DisabledAugment", "disabled": true}
                  ],
                  "tips": [
                    {"stage": "Stage 2", "tip": "Play strongest board and slam tank items"}
                  ],
                  "augmentsTip": "Prioritize economy or combat augments."
                },
                {
                  "title": "Wildcard Rift Flex",
                  "compSlug": "set-18-wildcard-flex",
                  "tier": "X",
                  "style": "4-Cost Fast 8",
                  "set": 18,
                  "displayIndex": 12,
                  "isPublic": true,
                  "mainChampion": {"apiName": "DA_18_MasterYi_AD", "cost": 4},
                  "finalComp": [
                    {"apiName": "DA_18_MasterYi_AD", "boardIndex": 21, "items": [], "stars": 2},
                    {"apiName": "DA_18_Ezreal", "boardIndex": 26, "items": [], "stars": 1},
                    {"apiName": "DA_Brambleback18", "boardIndex": 1, "items": [], "stars": 1},
                    {"apiName": "DA_18_ElderDragon", "boardIndex": 8, "items": [], "stars": 1}
                  ]
                },
                {
                  "title": "Hidden Draft Comp",
                  "compSlug": "hidden-draft",
                  "tier": "S",
                  "set": 18,
                  "displayIndex": 1,
                  "isPublic": false,
                  "finalComp": [
                    {"apiName": "DA_18_Ezreal", "boardIndex": 0},
                    {"apiName": "DA_Brambleback18", "boardIndex": 1},
                    {"apiName": "DA_18_ElderDragon", "boardIndex": 2},
                    {"apiName": "DA_18_GnarSmall", "boardIndex": 3}
                  ]
                },
                {
                  "title": "Archived Overflow Comp",
                  "compSlug": "archived-overflow",
                  "tier": "A",
                  "set": 18,
                  "displayIndex": 1001,
                  "isPublic": true,
                  "finalComp": [
                    {"apiName": "DA_18_Ezreal", "boardIndex": 0},
                    {"apiName": "DA_Brambleback18", "boardIndex": 1},
                    {"apiName": "DA_18_ElderDragon", "boardIndex": 2},
                    {"apiName": "DA_18_GnarSmall", "boardIndex": 3}
                  ]
                }
              ]
            }
        """.trimIndent()

        val envelopeJson = org.json.JSONObject().apply {
            put("status", 200)
            put("statusText", "")
            put("headers", org.json.JSONObject())
            put("body", apiPayload)
        }.toString()

        val html = """
            <html>
              <body>
                <script type="application/json" data-sveltekit-fetched data-url="/api/tierlist/comps?set=18" data-ttl="300">$envelopeJson</script>
              </body>
            </html>
        """.trimIndent()

        val parsed = scraper.parseCompsDocument(html, TftEnvironment.LIVE, "18.6b", isDedicatedEnvPage = true)
        assertEquals(18, parsed.detectedSetNumber)
        assertEquals(2, parsed.comps.size)
        assertFalse(parsed.comps.any { it.title == "Hidden Draft Comp" || it.title == "Archived Overflow Comp" })

        val tempo = parsed.comps.first { it.title == "Tempo Riftbeast" }
        assertEquals(TierRank.S, tempo.tier)
        assertEquals("Fast 9", tempo.playstyle)
        assertEquals("Brambleback", tempo.carryName)
        assertEquals(listOf("Verticality II"), tempo.augmentPriorities)
        assertTrue(tempo.levelingGuide.contains("Stage 2"))
        assertTrue(tempo.notes.contains("economy or combat augments"))

        val ezrealPlacement = tempo.hexPlacements.first { it.unitName == "Ezreal" }
        assertEquals(3, ezrealPlacement.row) // boardIndex 27 / 7 == 3
        assertEquals(6, ezrealPlacement.col) // boardIndex 27 % 7 == 6
        assertEquals(listOf("Guinsoo's Rageblade"), ezrealPlacement.items)

        val xComp = parsed.comps.first { it.title == "Wildcard Rift Flex" }
        assertEquals(TierRank.X, xComp.tier)
        assertEquals("Master Yi", xComp.carryName)

        // Parsing the same LIVE HTML as PBE with isDedicatedEnvPage = false must NOT clone LIVE comps into PBE
        val clonedAsPbe = scraper.parseCompsDocument(html, TftEnvironment.PBE, "19.1-PBE", isDedicatedEnvPage = false)
        assertTrue(clonedAsPbe.comps.isEmpty())
    }

    @Test
    fun `tftacademy scraper preserves carries beyond 10 finalComp entries when summon tokens precede units`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OfflineMetaCacheStore(context).readOrSeedBundle()
        val scraper = TftAcademyCompsScraper()
        val payload = """
            {
              "guides": [
                {
                  "title": "Aphelios Elderwood",
                  "compSlug": "set-18-aphelios-vanguards-copy",
                  "tier": "X",
                  "style": "Fast 9",
                  "set": 18,
                  "isPublic": true,
                  "displayIndex": 5,
                  "mainChampion": {"apiName": "DA_18_Aphelios", "cost": 4},
                  "finalComp": [
                    {"apiName": "DA_Elderwood18_Protector", "boardIndex": 3},
                    {"apiName": "DA_Elderwood18_Lifeblossom", "boardIndex": 18},
                    {"apiName": "DA_Elderwood18_StonebarkTree", "boardIndex": 6},
                    {"apiName": "DA_Elderwood18_StonebarkTree", "boardIndex": 1},
                    {"apiName": "DA_18_Ornn", "boardIndex": 13},
                    {"apiName": "DA_18_Xayah", "boardIndex": 27},
                    {"apiName": "DA_18_Alistar", "boardIndex": 5},
                    {"apiName": "DA_18_Diana", "boardIndex": 4},
                    {"apiName": "DA_18_Hecarim", "boardIndex": 2},
                    {"apiName": "DA_18_Lillia", "boardIndex": 0},
                    {"apiName": "DA_18_Aphelios", "boardIndex": 21, "items": ["DA_GuinsoosRageblade", "DA_KrakensFury"]},
                    {"apiName": "DA_18_Ezreal", "boardIndex": 22, "items": ["DA_SpearOfShojin"]},
                    {"apiName": "DA_18_GnarSmall", "boardIndex": 9}
                  ]
                }
              ]
            }
        """.trimIndent()
        val parsed = scraper.parseCompsDocument(payload, TftEnvironment.LIVE, "18.6b", isDedicatedEnvPage = true)
        assertEquals(1, parsed.comps.size)
        val comp = parsed.comps.first()
        assertEquals(13, comp.hexPlacements.size)
        assertTrue(comp.coreUnits.contains("Aphelios"))
        assertTrue(comp.coreUnits.contains("Ezreal"))
        assertTrue(comp.coreUnits.contains("Gnar"))
        assertEquals("Aphelios", comp.carryName)
        val apheliosHex = comp.hexPlacements.first { it.unitName == "Aphelios" }
        assertTrue(apheliosHex.isCarry)
        assertEquals(3, apheliosHex.row)
        assertEquals(0, apheliosHex.col)
        assertEquals(listOf("Guinsoo's Rageblade", "Kraken's Fury"), apheliosHex.items)
        // Duplicate summon token Stonebark Tree must appear only once in coreUnits badge list
        assertEquals(1, comp.coreUnits.count { it == "Stonebark Tree" })
    }

    @Test
    fun `tftacademy pbe scraper fetches set 19 comps from set19 url and api fallback`() {
        val bodyPayload = """
            {
              "guides": [
                {
                  "title": "Nav Bar Set 18 Comp",
                  "set": 18,
                  "tier": "A",
                  "units": ["Ahri", "Ornn", "Lillia", "Veigar"]
                },
                {
                  "title": "Prismatic Tour Jinx",
                  "compSlug": "set-19-prismatic-jinx",
                  "set": 19,
                  "tier": "S",
                  "style": "4-Cost Fast 8",
                  "isPublic": true,
                  "displayIndex": 0,
                  "mainChampion": {"apiName": "Jinx", "cost": 4},
                  "finalComp": [
                    {"apiName": "Jinx", "boardIndex": 27, "items": ["Guinsoo's Rageblade", "Infinity Edge"], "stars": 2},
                    {"apiName": "Shyvana", "boardIndex": 5, "items": ["Warmog's Armor"], "stars": 2},
                    {"apiName": "Yunara", "boardIndex": 21, "items": [], "stars": 3},
                    {"apiName": "Elise", "boardIndex": 1, "items": [], "stars": 2}
                  ],
                  "traits": ["5 Ionic Spark", "4 Frontman"],
                  "augments": ["Prismatic Ticket", "Sword Overflow"],
                  "tips": [{"stage": "Stage 4", "tip": "Roll on 8 for Jinx and Shyvana."}]
                }
              ]
            }
        """.trimIndent()
        val envelope = org.json.JSONObject().put("status", 200).put("body", bodyPayload).toString()
        val pbeHtml = """
            <html>
              <head><title>Set 19: Prismatic Tour Tier List - TFTAcademy</title></head>
              <body>
                <nav><a href="/tierlist/comps">Set 18</a><a href="/tierlist/comps/set19">Set 19: Prismatic Tour</a></nav>
                <script type="application/json" data-sveltekit-fetched data-url="/api/tierlist/comps?set=19">$envelope</script>
              </body>
            </html>
        """.trimIndent()

        val requestedUrls = mutableListOf<String>()
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                requestedUrls.add(url)
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, pbeHtml))
                    .build()
            }
            .build()

        val scraper = TftAcademyCompsScraper(mockClient)
        val result = scraper.fetchPbeTeamComps("19.1-PBE")

        assertTrue(requestedUrls.first() == TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL)
        assertTrue(requestedUrls.all { it.startsWith(TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL) })
        assertTrue(result.fetchedFromNetwork)
        assertEquals("Set 19: Prismatic Tour (PBE)", result.detectedPbeSet)
        assertEquals(1, result.pbeComps.size)

        val comp = result.pbeComps.first()
        assertEquals("Prismatic Tour Jinx", comp.title)
        assertEquals(TierRank.S, comp.tier)
        assertEquals(TftEnvironment.PBE, comp.environment)
        assertTrue(comp.sourceUrl.startsWith(TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL))
        assertEquals("Jinx", comp.carryName)
        assertEquals("Set 19", comp.setName)
        assertTrue(comp.levelingGuide.contains("Stage 4: Roll on 8"))

        val jinxHex = comp.hexPlacements.first { it.unitName == "Jinx" }
        assertEquals(3, jinxHex.row)
        assertEquals(6, jinxHex.col)
        assertTrue(jinxHex.isCarry)

        // Verify /api/tierlist/comps?set=19 JSON fallback works when SSR page omits inline comps
        val apiClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = if (url == TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL) {
                    "<html><body><h1>Set 19: Prismatic Tour</h1></body></html>"
                } else {
                    bodyPayload
                }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, body))
                    .build()
            }
            .build()
        val apiResult = TftAcademyCompsScraper(apiClient).fetchPbeTeamComps("19.1-PBE")
        assertEquals(1, apiResult.pbeComps.size)
        assertTrue(apiResult.pbeComps.first().sourceUrl.startsWith(TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL))
    }

    @Test
    fun `meta repository serves cached snapshots and tier lists when completely offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        val seeded = cacheStore.readOrSeedBundle()

        // Mutate and persist an updated cached snapshot as if synced online earlier from tft.ninja
        val syncedComp = sampleTftAcademyComp(
            id = "tftninja_live_cached_offline",
            title = "Cached Offline Live Comp",
            environment = TftEnvironment.LIVE
        )
        val syncedPbeComp = sampleTftAcademyComp(
            id = "tftninja_pbe_cached_offline",
            title = "Cached Offline PBE Comp",
            environment = TftEnvironment.PBE,
            carry = "Jinx"
        )

        val persisted = seeded.copy(
            liveComps = listOf(syncedComp),
            pbeComps = listOf(syncedPbeComp)
        )
        assertTrue(cacheStore.writeBundle(persisted))

        // Simulate offline failure on every scraper using an intercepting OkHttpClient that throws IOException
        val offlineClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { throw java.io.IOException("Simulated airplane mode offline") }
            .build()

        val offlineDdragonApi = retrofit2.Retrofit.Builder()
            .baseUrl("https://ddragon.leagueoflegends.com/")
            .client(offlineClient)
            .build()
            .create(com.example.data.network.DDragonApi::class.java)

        val offlineRepo = MetaRepository(
            cacheStore = OfflineMetaCacheStore(context),
            tftipsScraper = TftipsPatchScraper(offlineClient),
            tftAcademyScraper = TftAcademyCompsScraper(offlineClient),
            tftNinjaScraper = com.example.data.network.TftNinjaCompsScraper(urlFetcher = { null }, rscFetcher = { null }),
            ddragonApi = offlineDdragonApi,
            publicClient = offlineClient
        )

        val syncResult = offlineRepo.refreshAndPersistMeta()
        assertFalse(syncResult.syncedFromNetwork)
        assertTrue(syncResult.cacheStatus.isOfflineFallback)

        // Fresh cold-store instance reading solely from local storage must preserve all comps, tier lists, and PBE catalogs
        val reloadedStore = OfflineMetaCacheStore(context)
        val reloaded = reloadedStore.readOrSeedBundle()
        assertTrue(reloaded.liveComps.any { it.id == "tftninja_live_cached_offline" })
        assertTrue(reloaded.pbeComps.any { it.id == "tftninja_pbe_cached_offline" })
        assertEquals(74, reloaded.liveChampions.size)
        assertEquals(94, reloaded.pbeChampions.size)
        assertTrue(reloaded.liveItems.size >= 140)
        assertTrue(reloaded.liveAugments.size >= 340)
        assertTrue(reloaded.pbeItems.size >= 80)
        assertTrue(reloaded.pbeAugments.size >= 240)
        assertEquals(
            "https://sunderarmor.com/characters/Skin/19/Jinx.png",
            com.example.ui.components.TftAssetIconResolver.resolveChampionIconUrl("Jinx", TftEnvironment.PBE)
        )
        assertEquals(
            "https://sunderarmor.com/items/WarmogsArmor.png",
            com.example.ui.components.TftAssetIconResolver.resolveItemIconUrl("Warmog's Armor", TftEnvironment.PBE)
        )
    }

    @Test
    fun `live catalog sync updates tiers and merges newly released ddragon champions items and augments`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seeded = OfflineMetaCacheStore(context).readOrSeedBundle()

        val itemsApiJson = """{"items_tierlists":[{"type":"craftables","tier":{"S":["DA_WarmogsArmor","DA_18_NewLiveItem"]}}]}"""
        val augsApiJson = """{"augments_tierlists":[{"augmenttier":3,"tier":{"S":["DA_LevelUp","DA_18_NewPrismaticAug"]}}]}"""
        val academyTiers = TftAcademyCompsScraper().parseLiveItemsAndAugmentsJson(itemsApiJson, augsApiJson)

        val ddragonChampJson = """{"data":{"TFT18_TestNewChamp":{"id":"TFT18_TestNewChamp","name":"Test New Champ","tier":5,"image":{"full":"TFT18_TestNewChamp.png","group":"tft-champion"}}}}"""
        val ddragonItemJson = """{"data":{"TFT18_Item_NewLiveItem":{"id":"TFT18_Item_NewLiveItem","name":"New Live Item","image":{"full":"TFT18_Item_NewLiveItem.png","group":"tft-item"}}}}"""
        val ddragonAugJson = """{"data":{"DA_18_NewPrismaticAug":{"id":"DA_18_NewPrismaticAug","name":"New Prismatic Aug","image":{"full":"DA_18_NewPrismaticAug.png","group":"tft-augment"}}}}"""

        val refreshed = com.example.data.repository.MetaCatalogSyncHelper.refreshLiveCatalog(
            existingChampions = seeded.liveChampions,
            existingItems = seeded.liveItems,
            existingAugments = seeded.liveAugments,
            liveComps = seeded.liveComps,
            liveSetNumber = 18,
            ddragonChampJson = ddragonChampJson,
            ddragonItemJson = ddragonItemJson,
            ddragonAugmentJson = ddragonAugJson,
            academyTiers = academyTiers
        )

        assertTrue(refreshed.champions.any { it.name == "Test New Champ" && it.cost == 5 })
        assertTrue(refreshed.items.any { it.name == "New Live Item" })
        assertEquals(TierRank.S, refreshed.items.first { it.name == "Warmog's Armor" }.tier)
        assertTrue(refreshed.augments.any { it.name == "New Prismatic Aug" && it.tier == TierRank.S })
    }

    @Test
    fun `overlay free resize bounds clamp width and height and preserve adaptive side pane threshold`() {
        val defaultDims = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 915, screenHeightDp = 411)
        val bounds = com.example.overlay.resolveOverlaySizeBounds(screenWidthDp = 915, screenHeightDp = 411)
        assertEquals(280f, bounds.minWidthDp, 0.01f)
        assertEquals(190f, bounds.minHeightDp, 0.01f)
        assertEquals(899f, bounds.maxWidthDp, 0.01f)
        assertEquals(395f, bounds.maxHeightDp, 0.01f)

        // Default un-resized mode keeps custom height null (wrap content up to maxHeightDp)
        val (defaultW, defaultH) = com.example.overlay.clampOverlayCustomSize(null, null, defaultDims, bounds)
        assertEquals(defaultDims.widthDp, defaultW)
        assertEquals(null, defaultH)

        // Too small values clamp to minimum usable bounds
        val (minW, minH) = com.example.overlay.clampOverlayCustomSize(120f, 90f, defaultDims, bounds)
        assertEquals(280.dp, minW)
        assertEquals(190.dp, minH)

        // Oversized values clamp inside screen bounds minus safe margin
        val (maxW, maxH) = com.example.overlay.clampOverlayCustomSize(1600f, 900f, defaultDims, bounds)
        assertEquals(899.dp, maxW)
        assertEquals(395.dp, maxH)

        // Mid-range custom size preserves exact dp dimensions & updates adaptive side detail breakpoint
        val (midW, midH) = com.example.overlay.clampOverlayCustomSize(540f, 320f, defaultDims, bounds)
        assertEquals(540.dp, midW)
        assertEquals(320.dp, midH)
        assertTrue(midW >= 480.dp)

        val (narrowW, _) = com.example.overlay.clampOverlayCustomSize(420f, 300f, defaultDims, bounds)
        assertEquals(420.dp, narrowW)
        assertFalse(narrowW >= 480.dp)
    }

    @Test
    fun `overlay left edge resize extends leftward across device width while anchoring right edge`() {
        val bounds = com.example.overlay.resolveOverlaySizeBounds(screenWidthDp = 915, screenHeightDp = 411)
        val density = 2f
        // Start with 500dp wide overlay positioned at x = 600px (right edge = 1600px)
        val (expandedW, expandedX) = com.example.overlay.computeLeftEdgeResizeResult(
            startOriginX = 600,
            startWidthDp = 500f,
            deltaRawX = -300f, // drag left by 150dp -> width grows to 650dp, x shifts left by 300px to 300px
            density = density,
            minLeftPx = 16,
            maxRightPx = 1814,
            bounds = bounds
        )
        assertEquals(650f, expandedW, 0.01f)
        assertEquals(300, expandedX)
        assertEquals(1600, expandedX + (expandedW * density).toInt())

        // Dragging right shrinks width down and shifts origin rightward
        val (shrunkW, shrunkX) = com.example.overlay.computeLeftEdgeResizeResult(
            startOriginX = 600,
            startWidthDp = 500f,
            deltaRawX = 200f, // drag right by 100dp -> width 400dp, origin = 800px
            density = density,
            minLeftPx = 16,
            maxRightPx = 1814,
            bounds = bounds
        )
        assertEquals(400f, shrunkW, 0.01f)
        assertEquals(800, shrunkX)
    }

    @Test
    fun `overlay bauble toggles open and closed repeatedly and clamps drag inside safe screen bounds`() {
        var state = com.example.overlay.OverlayDisplayState.BUBBLE
        state = com.example.overlay.toggleOverlayDisplayState(state)
        assertEquals(com.example.overlay.OverlayDisplayState.EXPANDED, state)
        // Re-clicking bauble while overlay drawer is open closes drawer cleanly
        state = com.example.overlay.toggleOverlayDisplayState(state)
        assertEquals(com.example.overlay.OverlayDisplayState.BUBBLE, state)
        // Clicking again re-opens drawer
        state = com.example.overlay.toggleOverlayDisplayState(state)
        assertEquals(com.example.overlay.OverlayDisplayState.EXPANDED, state)

        val bounds = com.example.overlay.OverlayWindowBounds(
            widthPx = 2400,
            heightPx = 1080,
            safeLeftPx = 96,
            safeTopPx = 48,
            safeRightPx = 24,
            safeBottomPx = 32,
            density = 2f
        )
        val bubblePx = 128
        val marginPx = 12

        // Dragging past top-left safe boundary clamps to safeLeft + margin, safeTop + margin
        val topLeftClamp = com.example.overlay.clampBubbleWindowPosition(
            desiredX = -200,
            desiredY = -100,
            viewWidthPx = bubblePx,
            viewHeightPx = bubblePx,
            bounds = bounds,
            marginPx = marginPx
        )
        assertEquals(96 + 12, topLeftClamp.first)
        assertEquals(48 + 12, topLeftClamp.second)

        // Dragging past bottom-right safe boundary clamps inside safeRight & safeBottom
        val bottomRightClamp = com.example.overlay.clampBubbleWindowPosition(
            desiredX = 2500,
            desiredY = 1200,
            viewWidthPx = bubblePx,
            viewHeightPx = bubblePx,
            bounds = bounds,
            marginPx = marginPx
        )
        assertEquals(2400 - 24 - bubblePx - marginPx, bottomRightClamp.first)
        assertEquals(1080 - 32 - bubblePx - marginPx, bottomRightClamp.second)

        // Mid-screen position passes through unchanged
        val freePos = com.example.overlay.clampBubbleWindowPosition(
            desiredX = 820,
            desiredY = 420,
            viewWidthPx = bubblePx,
            viewHeightPx = bubblePx,
            bounds = bounds,
            marginPx = marginPx
        )
        assertEquals(820 to 420, freePos)
    }

    @Test
    fun `github release parser normalizes slugs selects matching apk variant and detects newer builds`() {
        assertEquals("owner/tacticians-forge", com.example.update.GitHubReleaseParser.normalizeRepoSlug("https://github.com/owner/tacticians-forge.git"))
        assertEquals("owner/tacticians-forge", com.example.update.GitHubReleaseParser.normalizeRepoSlug("owner/tacticians-forge"))
        assertEquals(null, com.example.update.GitHubReleaseParser.normalizeRepoSlug("MY_GITHUB_OWNER_REPO"))
        assertEquals(null, com.example.update.GitHubReleaseParser.normalizeRepoSlug("invalid"))

        val releasesJson = """
            [
              {
                "id": 201,
                "tag_name": "latest-apk",
                "name": "Latest Build",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-11-18T10:30:00Z",
                "html_url": "https://github.com/owner/repo/releases/tag/latest-apk",
                "body": "Version: 1.0.12\nBuildCode: 12\nCommit: abcdef1234567890",
                "assets": [
                  {
                    "id": 9001,
                    "name": "tacticians-forge-debug.apk",
                    "size": 24500000,
                    "updated_at": "2026-11-18T10:31:00Z",
                    "browser_download_url": "https://github.com/owner/repo/releases/download/latest-apk/tacticians-forge-debug.apk"
                  },
                  {
                    "id": 9002,
                    "name": "tacticians-forge-release.apk",
                    "size": 18200000,
                    "updated_at": "2026-11-18T10:31:30Z",
                    "browser_download_url": "https://github.com/owner/repo/releases/download/latest-apk/tacticians-forge-release.apk"
                  }
                ]
              },
              {
                "id": 199,
                "tag_name": "v1.0.10",
                "name": "Release v1.0.10",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-11-17T08:00:00Z",
                "html_url": "https://github.com/owner/repo/releases/tag/v1.0.10",
                "body": "Version: 1.0.10\nBuildCode: 10\nCommit: 11111112222222",
                "assets": [
                  {
                    "id": 8001,
                    "name": "app-debug.apk",
                    "size": 24000000,
                    "updated_at": "2026-11-17T08:01:00Z",
                    "browser_download_url": "https://github.com/owner/repo/releases/download/v1.0.10/app-debug.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val debugReleases = com.example.update.GitHubReleaseParser.parseGitHubReleases(releasesJson, isDebugBuild = true)
        assertEquals(2, debugReleases.size)
        val latestDebug = debugReleases.first()
        assertEquals(9001L, latestDebug.apkAssetId)
        assertEquals("tacticians-forge-debug.apk", latestDebug.apkAssetName)
        assertEquals(12, latestDebug.parsedVersionCode)
        assertEquals("1.0.12", latestDebug.parsedVersionName)
        assertEquals("abcdef1234567890", latestDebug.parsedCommitSha)

        val releaseVariant = com.example.update.GitHubReleaseParser.parseGitHubReleases(releasesJson, isDebugBuild = false).first()
        assertEquals(9002L, releaseVariant.apkAssetId)
        assertEquals("tacticians-forge-release.apk", releaseVariant.apkAssetName)

        // Older installed build should detect update
        val olderInstalled = com.example.update.InstalledAppVersionInfo(
            versionCode = 11,
            versionName = "1.0.11",
            gitCommitSha = "9999999aaaaaaa",
            buildTimestampMs = 0L,
            isDebugBuild = true
        )
        assertTrue(com.example.update.GitHubReleaseParser.isRemoteReleaseNewer(latestDebug, olderInstalled))

        // Same commit SHA must NOT re-prompt
        val sameCommitInstalled = com.example.update.InstalledAppVersionInfo(
            versionCode = 12,
            versionName = "1.0.12",
            gitCommitSha = "abcdef1234567890",
            buildTimestampMs = 0L,
            isDebugBuild = true
        )
        assertFalse(com.example.update.GitHubReleaseParser.isRemoteReleaseNewer(latestDebug, sameCommitInstalled))

        // Equal buildCode but newer commit SHA must detect update
        val differentCommitSameCode = sameCommitInstalled.copy(gitCommitSha = "fedcba9876543210")
        assertTrue(com.example.update.GitHubReleaseParser.isRemoteReleaseNewer(latestDebug, differentCommitSameCode))

        // Token normalization and API URL handling
        assertEquals("JSGamingggg/Tactician-s-Forge", com.example.update.GitHubReleaseParser.normalizeRepoSlug("https://api.github.com/repos/JSGamingggg/Tactician-s-Forge/releases"))
        assertEquals("ghp_secret123", com.example.update.GitHubReleaseParser.normalizeGithubToken("Bearer ghp_secret123"))
        assertEquals(null, com.example.update.GitHubReleaseParser.normalizeGithubToken("MY_GITHUB_TOKEN"))

        val htmlReleases = com.example.update.GitHubReleaseParser.parseGitHubHtmlReleasesFallback(
            releasesHtml = "<div>Version: 1.0.18 BuildCode: 19 Commit: 7777777abcdef</div>",
            expandedAssetsHtml = """<a href="/JSGamingggg/Tactician-s-Forge/releases/download/latest-apk/app-debug.apk">app-debug.apk</a>""",
            repoSlug = "JSGamingggg/Tactician-s-Forge",
            isDebugBuild = true
        )
        assertEquals(1, htmlReleases.size)
        assertEquals(19, htmlReleases.first().parsedVersionCode)
        assertEquals("https://github.com/JSGamingggg/Tactician-s-Forge/releases/download/latest-apk/app-debug.apk", htmlReleases.first().apkDownloadUrl)
    }

    @Test
    fun `local preferences store persists github repo override and app update alert watermarks`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.repository.LocalPreferencesStore(context)

        assertTrue(prefs.isAppUpdateAlertsEnabled())
        prefs.setGithubRepoOverride("my-org/tacticians-forge")
        prefs.setGithubTokenOverride("github_pat_test_999")
        prefs.setAppUpdateAlertsEnabled(false)
        prefs.setLastNotifiedAppReleaseKey("201_9001_abcdef1")
        prefs.setLastInstalledApkAssetId(9001L)
        prefs.setDismissedAppReleaseKey("201_9001_abcdef1")

        assertEquals("my-org/tacticians-forge", prefs.getGithubRepoOverride())
        assertEquals("github_pat_test_999", prefs.getGithubTokenOverride())
        assertFalse(prefs.isAppUpdateAlertsEnabled())
        assertEquals("201_9001_abcdef1", prefs.getLastNotifiedAppReleaseKey())
        assertEquals(9001L, prefs.getLastInstalledApkAssetId())
        assertEquals("201_9001_abcdef1", prefs.getDismissedAppReleaseKey())
    }

    @Test
    fun `champion tier list filtering supports traits multi-cost tier and search combinations`() {
        val sampleChamps = listOf(
            com.example.data.model.ChampionTierEntry(
                id = "ahri",
                name = "Ahri",
                cost = 4,
                tier = com.example.data.model.TierRank.S,
                environment = com.example.data.model.TftEnvironment.LIVE,
                traits = listOf("Elderwood", "Arcanist"),
                role = "AP Carry",
                avgPlacement = 3.8,
                top4Rate = 61.0,
                bestItems = listOf("Blue Buff", "Jeweled Gauntlet"),
                synergyComps = listOf("Elderwood Arcanists")
            ),
            com.example.data.model.ChampionTierEntry(
                id = "veigar",
                name = "Veigar",
                cost = 3,
                tier = com.example.data.model.TierRank.A,
                environment = com.example.data.model.TftEnvironment.LIVE,
                traits = listOf("Elderwood", "Arcanist"),
                role = "AP Carry",
                avgPlacement = 4.1,
                top4Rate = 56.0,
                bestItems = listOf("Spear of Shojin"),
                synergyComps = listOf("Elderwood Reroll")
            ),
            com.example.data.model.ChampionTierEntry(
                id = "ornn",
                name = "Ornn",
                cost = 5,
                tier = com.example.data.model.TierRank.S,
                environment = com.example.data.model.TftEnvironment.LIVE,
                traits = listOf("Elderwood", "Vanguard"),
                role = "Tank",
                avgPlacement = 3.9,
                top4Rate = 59.5,
                bestItems = listOf("Warmog's Armor"),
                synergyComps = listOf("Elderwood Arcanists")
            ),
            com.example.data.model.ChampionTierEntry(
                id = "lux",
                name = "Lux",
                cost = 2,
                tier = com.example.data.model.TierRank.B,
                environment = com.example.data.model.TftEnvironment.LIVE,
                traits = listOf("Blossom", "Arcanist"),
                role = "AP Utility",
                avgPlacement = 4.4,
                top4Rate = 50.2,
                bestItems = listOf("Morellonomicon"),
                synergyComps = listOf("Arcanist Flex")
            )
        )

        // Cost & trait option extraction
        assertEquals(listOf(2, 3, 4, 5), com.example.ui.screens.extractChampionCosts(sampleChamps))
        val traits = com.example.ui.screens.extractChampionTraitOptions(sampleChamps)
        assertEquals("Arcanist", traits[0].name)
        assertEquals(3, traits[0].championCount)
        assertEquals("Elderwood", traits[1].name)
        assertEquals(3, traits[1].championCount)
        assertEquals(4, traits.size)

        // Single trait filter
        val arcanists = com.example.ui.screens.filterChampionTiers(
            champions = sampleChamps,
            searchQuery = "",
            selectedTier = null,
            selectedCosts = emptySet(),
            selectedTraits = setOf("arcanist")
        )
        assertEquals(listOf("Ahri", "Veigar", "Lux"), arcanists.map { it.name })

        // Multi-trait intersection filter (Elderwood + Arcanist)
        val elderwoodArcanists = com.example.ui.screens.filterChampionTiers(
            champions = sampleChamps,
            searchQuery = "",
            selectedTier = null,
            selectedCosts = emptySet(),
            selectedTraits = setOf("Elderwood", "Arcanist")
        )
        assertEquals(listOf("Ahri", "Veigar"), elderwoodArcanists.map { it.name })

        // Multi-cost filter (4g and 5g)
        val highCostElderwood = com.example.ui.screens.filterChampionTiers(
            champions = sampleChamps,
            searchQuery = "",
            selectedTier = null,
            selectedCosts = setOf(4, 5),
            selectedTraits = setOf("Elderwood")
        )
        assertEquals(listOf("Ahri", "Ornn"), highCostElderwood.map { it.name })

        // Combined trait + cost + tier + search
        val strictMatch = com.example.ui.screens.filterChampionTiers(
            champions = sampleChamps,
            searchQuery = "Shojin",
            selectedTier = com.example.data.model.TierRank.A,
            selectedCosts = setOf(3, 4),
            selectedTraits = setOf("Elderwood")
        )
        assertEquals(listOf("Veigar"), strictMatch.map { it.name })
    }

    @Test
    fun `item tier list filtering supports multi-category multi-component tier and search combinations`() {
        val sampleItems = listOf(
            com.example.data.model.ItemTierEntry(
                id = "blue_buff",
                name = "Blue Buff",
                tier = com.example.data.model.TierRank.S,
                environment = com.example.data.model.TftEnvironment.LIVE,
                category = com.example.data.model.ItemCategory.AP,
                components = listOf("Tear of the Goddess", "Tear of the Goddess"),
                statsSummary = "+20 Mana, +20 AP",
                avgPlacement = 3.54,
                top4Rate = 63.8,
                bestHolders = listOf("Ahri")
            ),
            com.example.data.model.ItemTierEntry(
                id = "shojin",
                name = "Spear of Shojin",
                tier = com.example.data.model.TierRank.A,
                environment = com.example.data.model.TftEnvironment.LIVE,
                category = com.example.data.model.ItemCategory.AP,
                components = listOf("B.F. Sword", "Tear of the Goddess"),
                statsSummary = "+20% AD, +20 AP, +15 Mana",
                avgPlacement = 3.84,
                top4Rate = 57.9,
                bestHolders = listOf("Veigar")
            ),
            com.example.data.model.ItemTierEntry(
                id = "infinity_edge",
                name = "Infinity Edge",
                tier = com.example.data.model.TierRank.S,
                environment = com.example.data.model.TftEnvironment.LIVE,
                category = com.example.data.model.ItemCategory.AD,
                components = listOf("B.F. Sword", "Sparring Gloves"),
                statsSummary = "+35% AD, +35% Crit Chance",
                avgPlacement = 3.66,
                top4Rate = 61.1,
                bestHolders = listOf("Jhin")
            ),
            com.example.data.model.ItemTierEntry(
                id = "warmogs",
                name = "Warmog's Armor",
                tier = com.example.data.model.TierRank.S,
                environment = com.example.data.model.TftEnvironment.LIVE,
                category = com.example.data.model.ItemCategory.TANK,
                components = listOf("Giant's Belt", "Giant's Belt"),
                statsSummary = "+600 Bonus HP",
                avgPlacement = 3.62,
                top4Rate = 61.9,
                bestHolders = listOf("Ornn")
            )
        )

        val categories = com.example.ui.screens.extractItemCategoryOptions(sampleItems)
        assertEquals(3, categories.size)
        assertEquals(2, categories.first { it.category == com.example.data.model.ItemCategory.AP }.itemCount)

        val components = com.example.ui.screens.extractItemComponentOptions(sampleItems)
        // Tear appears in Blue Buff + Shojin (2), B.F. Sword appears in Shojin + IE (2)
        assertEquals(2, components.first { it.name == "Tear of the Goddess" }.itemCount)
        assertEquals(2, components.first { it.name == "B.F. Sword" }.itemCount)

        // Multi-category filter (AP OR Tank)
        val apAndTank = com.example.ui.screens.filterItemTiers(
            items = sampleItems,
            searchQuery = "",
            selectedTier = null,
            selectedCategories = setOf(
                com.example.data.model.ItemCategory.AP,
                com.example.data.model.ItemCategory.TANK
            ),
            selectedComponents = emptySet()
        )
        assertEquals(listOf("Blue Buff", "Warmog's Armor", "Spear of Shojin"), apAndTank.map { it.name })

        // Single component filter (Tear)
        val tearItems = com.example.ui.screens.filterItemTiers(
            items = sampleItems,
            searchQuery = "",
            selectedTier = null,
            selectedCategories = emptySet(),
            selectedComponents = setOf("tear of the goddess")
        )
        assertEquals(listOf("Blue Buff", "Spear of Shojin"), tearItems.map { it.name })

        // Multi-component intersection (Tear + B.F. Sword -> Shojin only)
        val tearPlusSword = com.example.ui.screens.filterItemTiers(
            items = sampleItems,
            searchQuery = "",
            selectedTier = null,
            selectedCategories = emptySet(),
            selectedComponents = setOf("Tear of the Goddess", "B.F. Sword")
        )
        assertEquals(listOf("Spear of Shojin"), tearPlusSword.map { it.name })

        // Combined tier + category + component + search
        val strictMatch = com.example.ui.screens.filterItemTiers(
            items = sampleItems,
            searchQuery = "Jhin",
            selectedTier = com.example.data.model.TierRank.S,
            selectedCategories = setOf(com.example.data.model.ItemCategory.AD),
            selectedComponents = setOf("Sparring Gloves")
        )
        assertEquals(listOf("Infinity Edge"), strictMatch.map { it.name })
    }

    @Test
    fun `augment tier list filtering supports multi-rarity multi-category tier and search combinations`() {
        val sampleAugments = listOf(
            com.example.data.model.AugmentTierEntry(
                id = "elderwood_crest",
                name = "Elderwood Crest",
                tier = com.example.data.model.TierRank.S,
                augmentTier = com.example.data.model.AugmentTier.GOLD,
                environment = com.example.data.model.TftEnvironment.LIVE,
                category = "Trait Emblem",
                avgPlacement = 3.31,
                top4Rate = 68.4,
                description = "Gain an Elderwood Emblem and Lillia.",
                synergyComps = listOf("Elderwood Arcanists")
            ),
            com.example.data.model.AugmentTierEntry(
                id = "blossom_crown",
                name = "Blossom Crown",
                tier = com.example.data.model.TierRank.S,
                augmentTier = com.example.data.model.AugmentTier.PRISMATIC,
                environment = com.example.data.model.TftEnvironment.LIVE,
                category = "Trait Emblem",
                avgPlacement = 3.44,
                top4Rate = 65.8,
                description = "Gain a Blossom Emblem and Redemption.",
                synergyComps = listOf("Blossom Vanguard Marksman")
            ),
            com.example.data.model.AugmentTierEntry(
                id = "level_up",
                name = "Level Up!",
                tier = com.example.data.model.TierRank.A,
                augmentTier = com.example.data.model.AugmentTier.PRISMATIC,
                environment = com.example.data.model.TftEnvironment.LIVE,
                category = "Economy / XP",
                avgPlacement = 3.74,
                top4Rate = 59.6,
                description = "Gain +2 extra XP when buying XP.",
                synergyComps = listOf("Elderwood Arcanists")
            ),
            com.example.data.model.AugmentTierEntry(
                id = "mana_flow",
                name = "Mana Flow",
                tier = com.example.data.model.TierRank.A,
                augmentTier = com.example.data.model.AugmentTier.SILVER,
                environment = com.example.data.model.TftEnvironment.LIVE,
                category = "Utility",
                avgPlacement = 3.92,
                top4Rate = 56.1,
                description = "Backline allies gain +2 mana per attack.",
                synergyComps = listOf("Elderwood Arcanists")
            )
        )

        val rarities = com.example.ui.screens.extractAugmentRarityOptions(sampleAugments)
        assertEquals(3, rarities.size)
        assertEquals(2, rarities.first { it.augmentTier == com.example.data.model.AugmentTier.PRISMATIC }.augmentCount)

        val categories = com.example.ui.screens.extractAugmentCategoryOptions(sampleAugments)
        assertEquals("Trait Emblem", categories[0].name)
        assertEquals(2, categories[0].augmentCount)

        // Multi-rarity filter (Gold OR Prismatic)
        val goldOrPrismatic = com.example.ui.screens.filterAugmentTiers(
            augments = sampleAugments,
            searchQuery = "",
            selectedTier = null,
            selectedAugmentTiers = setOf(
                com.example.data.model.AugmentTier.GOLD,
                com.example.data.model.AugmentTier.PRISMATIC
            ),
            selectedCategories = emptySet()
        )
        assertEquals(listOf("Elderwood Crest", "Blossom Crown", "Level Up!"), goldOrPrismatic.map { it.name })

        // Multi-category filter (Trait Emblem OR Utility)
        val emblemsOrUtility = com.example.ui.screens.filterAugmentTiers(
            augments = sampleAugments,
            searchQuery = "",
            selectedTier = null,
            selectedAugmentTiers = emptySet(),
            selectedCategories = setOf("trait emblem", "Utility")
        )
        assertEquals(listOf("Elderwood Crest", "Blossom Crown", "Mana Flow"), emblemsOrUtility.map { it.name })

        // Combined rarity + category + tier + search
        val strictMatch = com.example.ui.screens.filterAugmentTiers(
            augments = sampleAugments,
            searchQuery = "Redemption",
            selectedTier = com.example.data.model.TierRank.S,
            selectedAugmentTiers = setOf(com.example.data.model.AugmentTier.PRISMATIC),
            selectedCategories = setOf("Trait Emblem")
        )
        assertEquals(listOf("Blossom Crown"), strictMatch.map { it.name })
    }

    @Test
    fun `meta update notifier preserves watermarks when permission denied and notifies once granted`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val context: Context = app
        val prefsStore = LocalPreferencesStore(context)
        prefsStore.clearAllForTests()
        val initialLive = sampleTftAcademyComp("tftacademy_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val initialBundle = OfflineMetaBundle(liveComps = listOf(initialLive))
        val initialSync = com.example.data.model.MetaSyncResult(
            versionInfo = initialBundle.liveVersionInfo,
            cacheStatus = initialBundle.toCacheStatus(),
            newLivePatchDetected = false,
            newPbePatchDetected = false,
            newlyAddedComps = emptyList(),
            syncedFromNetwork = true
        )

        // Seed initial baseline first
        com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = initialSync,
            bundle = initialBundle,
            prefsStore = prefsStore
        )

        // Ensure POST_NOTIFICATIONS is denied when a new patch & comp arrive in background
        org.robolectric.Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val newComp = initialLive.copy(id = "tftacademy_live_synd", title = "Syndra Invokers")
        val updatedBundle = initialBundle.copy(
            liveVersionInfo = initialBundle.liveVersionInfo.copy(
                livePatchVersion = "18.10",
                pbePatchVersion = "19.3-PBE"
            ),
            liveComps = listOf(newComp, initialLive)
        )
        val updateSync = com.example.data.model.MetaSyncResult(
            versionInfo = updatedBundle.liveVersionInfo,
            cacheStatus = updatedBundle.toCacheStatus(),
            newLivePatchDetected = true,
            newPbePatchDetected = true,
            newlyAddedComps = listOf(newComp),
            syncedFromNetwork = true
        )

        val deniedDispatch = com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = updateSync,
            bundle = updatedBundle,
            prefsStore = prefsStore
        )
        assertFalse(deniedDispatch.livePatchNotified)
        assertFalse(deniedDispatch.pbePatchNotified)
        assertFalse(deniedDispatch.newCompsNotified)

        // Watermarks must NOT have swallowed the unposted patch/comp alerts
        val (savedLive, savedPbe) = prefsStore.getLastNotifiedVersions()
        assertNotEquals("18.10", savedLive)
        assertNotEquals("19.3-PBE", savedPbe)
        assertFalse(prefsStore.getNotifiedCompIds().orEmpty().contains(newComp.id))

        // Grant POST_NOTIFICATIONS and re-run without new delta: missed updates now notify!
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val recoverySync = updateSync.copy(
            newLivePatchDetected = false,
            newPbePatchDetected = false,
            newlyAddedComps = emptyList()
        )
        val grantedDispatch = com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = recoverySync,
            bundle = updatedBundle,
            prefsStore = prefsStore
        )
        assertTrue(grantedDispatch.livePatchNotified)
        assertTrue(grantedDispatch.pbePatchNotified)
        assertTrue(grantedDispatch.newCompsNotified)
        assertEquals(1, grantedDispatch.newCompsDetectedCount)

        val (finalLive, finalPbe) = prefsStore.getLastNotifiedVersions()
        assertEquals("18.10", finalLive)
        assertEquals("19.3-PBE", finalPbe)
        assertTrue(prefsStore.getNotifiedCompIds().orEmpty().contains(newComp.id))
    }

    @Test
    fun `background scheduler uses non-resetting KEEP policy and arms alarm backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching {
            androidx.work.WorkManager.initialize(
                context,
                androidx.work.Configuration.Builder().build()
            )
        }

        com.example.notifications.PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(
            context = context,
            triggerImmediateCheck = false,
            replaceChained = false
        )

        val wm = androidx.work.WorkManager.getInstance(context)
        val periodicFirst = wm.getWorkInfosForUniqueWork(com.example.notifications.PatchMonitorWorker.PERIODIC_WORK_NAME).get()
        val chainedFirst = wm.getWorkInfosForUniqueWork(com.example.notifications.PatchMonitorWorker.CHAINED_WORK_NAME).get()
        assertEquals(1, periodicFirst.size)
        assertEquals(1, chainedFirst.size)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val shadowAlarm = org.robolectric.Shadows.shadowOf(alarmManager)
        assertTrue(com.example.notifications.BackgroundSyncAlarmReceiver.isAlarmScheduled(context))
        assertEquals(1, shadowAlarm.scheduledAlarms.size)
        val firstAlarmTrigger = shadowAlarm.peekNextScheduledAlarm()?.triggerAtMs
        assertNotNull(firstAlarmTrigger)

        // Re-invoking on subsequent lifecycle events must KEEP existing work & alarm IDs instead of resetting timers
        com.example.notifications.PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(
            context = context,
            triggerImmediateCheck = false,
            replaceChained = false
        )
        val periodicSecond = wm.getWorkInfosForUniqueWork(com.example.notifications.PatchMonitorWorker.PERIODIC_WORK_NAME).get()
        val chainedSecond = wm.getWorkInfosForUniqueWork(com.example.notifications.PatchMonitorWorker.CHAINED_WORK_NAME).get()
        assertEquals(periodicFirst.first().id, periodicSecond.first().id)
        assertEquals(chainedFirst.first().id, chainedSecond.first().id)
        assertEquals(1, shadowAlarm.scheduledAlarms.size)
        assertEquals(firstAlarmTrigger, shadowAlarm.peekNextScheduledAlarm()?.triggerAtMs)
    }

    @Test
    fun `boot and alarm receivers schedule worker and rearm alarm without activity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching {
            androidx.work.WorkManager.initialize(
                context,
                androidx.work.Configuration.Builder().build()
            )
        }

        val bootReceiver = com.example.notifications.BackgroundSyncBootReceiver()
        bootReceiver.onReceive(context, android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED))

        val wm = androidx.work.WorkManager.getInstance(context)
        val immediateAfterBoot = wm.getWorkInfosForUniqueWork(com.example.notifications.PatchMonitorWorker.IMMEDIATE_WORK_NAME).get()
        assertTrue(immediateAfterBoot.isNotEmpty())
        assertTrue(com.example.notifications.BackgroundSyncAlarmReceiver.isAlarmScheduled(context))

        val alarmReceiver = com.example.notifications.BackgroundSyncAlarmReceiver()
        alarmReceiver.onReceive(
            context,
            android.content.Intent(com.example.notifications.BackgroundSyncAlarmReceiver.ACTION_BACKGROUND_SYNC_ALARM)
        )
        val periodicAfterAlarm = wm.getWorkInfosForUniqueWork(com.example.notifications.PatchMonitorWorker.PERIODIC_WORK_NAME).get()
        val immediateAfterAlarm = wm.getWorkInfosForUniqueWork(com.example.notifications.PatchMonitorWorker.IMMEDIATE_WORK_NAME).get()
        assertTrue(periodicAfterAlarm.isNotEmpty())
        assertTrue(immediateAfterAlarm.isNotEmpty())
        assertTrue(com.example.notifications.BackgroundSyncAlarmReceiver.isAlarmScheduled(context))
    }

    @Test
    fun `notifications never fire on initial seed stat ticks or repeated opens without genuine updates`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val context: Context = app
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNotif = org.robolectric.Shadows.shadowOf(notifManager)
        notifManager.cancelAll()

        val prefsStore = LocalPreferencesStore(context)
        prefsStore.clearAllForTests()
        val initialLive = sampleTftAcademyComp("tftacademy_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val initialBundle = OfflineMetaBundle(liveComps = listOf(initialLive))

        // Even if sync flags report deltas on very first run, baseline seeds silently with zero OS notifications
        val noisyFirstSync = com.example.data.model.MetaSyncResult(
            versionInfo = initialBundle.liveVersionInfo,
            cacheStatus = initialBundle.toCacheStatus(),
            newLivePatchDetected = true,
            newPbePatchDetected = true,
            newlyAddedComps = listOf(initialLive),
            compsUpdatedDetected = true,
            syncedFromNetwork = true
        )
        val firstResult = com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = noisyFirstSync,
            bundle = initialBundle,
            prefsStore = prefsStore
        )
        assertFalse(firstResult.livePatchNotified)
        assertFalse(firstResult.pbePatchNotified)
        assertFalse(firstResult.newCompsNotified)
        assertEquals(0, shadowNotif.size())

        // Volatile winRate/avgPlacement stat ticks must keep structural signature identical and post 0 notifications
        val statDriftComp = initialLive.copy(avgPlacement = 3.99, top4Rate = 58.1, winRate = 12.4, patchTag = "18.8-hf")
        val statDriftBundle = initialBundle.copy(liveComps = listOf(statDriftComp))
        assertEquals(
            TftAcademyCompsScraper.computeCompsSignature(initialBundle.liveComps, emptyList()),
            TftAcademyCompsScraper.computeCompsSignature(statDriftBundle.liveComps, emptyList())
        )
        val unchangedSync = com.example.data.model.MetaSyncResult(
            versionInfo = statDriftBundle.liveVersionInfo,
            cacheStatus = statDriftBundle.toCacheStatus(),
            newLivePatchDetected = true,
            newPbePatchDetected = true,
            newlyAddedComps = emptyList(),
            compsUpdatedDetected = true,
            syncedFromNetwork = true
        )
        val secondResult = com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = unchangedSync,
            bundle = statDriftBundle,
            prefsStore = prefsStore
        )
        assertFalse(secondResult.livePatchNotified)
        assertFalse(secondResult.pbePatchNotified)
        assertFalse(secondResult.newCompsNotified)
        assertEquals(0, shadowNotif.size())

        // In-app view marks a newly arrived release as seen silently so background worker won't re-notify
        val viewedBundle = initialBundle.copy(
            liveVersionInfo = initialBundle.liveVersionInfo.copy(livePatchVersion = "18.12")
        )
        com.example.notifications.MetaUpdateNotifier.markCurrentSnapshotAsSeen(viewedBundle, prefsStore)
        val afterOpenResult = com.example.notifications.MetaUpdateNotifier.dispatchNotifications(
            context = context,
            syncResult = com.example.data.model.MetaSyncResult(
                versionInfo = viewedBundle.liveVersionInfo,
                cacheStatus = viewedBundle.toCacheStatus(),
                newLivePatchDetected = true,
                syncedFromNetwork = true
            ),
            bundle = viewedBundle,
            prefsStore = prefsStore
        )
        assertFalse(afterOpenResult.livePatchNotified)
        assertEquals(0, shadowNotif.size())
    }

    @Test
    fun `github release key matches across api and html fallback and same version does not false trigger`() {
        val apiJson = """
            [
              {
                "id": 9991,
                "tag_name": "v1.0.20",
                "name": "v1.0.20",
                "body": "Version: 1.0.20\nBuildCode: 20\nCommit: abc1234deadbeef",
                "assets": [
                  {
                    "id": 777,
                    "name": "app-debug.apk",
                    "size": 123456,
                    "browser_download_url": "https://github.com/owner/repo/releases/download/v1.0.20/app-debug.apk"
                  }
                ]
              }
            ]
        """.trimIndent()
        val apiRelease = com.example.update.GitHubReleaseParser.parseGitHubReleases(apiJson, isDebugBuild = true).first()
        val htmlRelease = com.example.update.GitHubReleaseParser.parseGitHubHtmlReleasesFallback(
            releasesHtml = "<div>Version: 1.0.20 BuildCode: 20 Commit: abc1234deadbeef</div>",
            expandedAssetsHtml = """<a href="/owner/repo/releases/download/v1.0.20/app-debug.apk">app-debug.apk</a>""",
            repoSlug = "owner/repo",
            isDebugBuild = true
        ).first()
        assertEquals(apiRelease.releaseKey, htmlRelease.releaseKey)

        // Same version without buildCode/timestamp must NOT falsely trigger update popup on launch
        val noBuildCodeRelease = apiRelease.copy(parsedVersionCode = null, parsedCommitSha = null, parsedVersionName = "1.0")
        val localInstalled = com.example.update.InstalledAppVersionInfo(
            versionCode = 1,
            versionName = "1.0",
            gitCommitSha = "",
            buildTimestampMs = 0L,
            isDebugBuild = true
        )
        assertFalse(com.example.update.GitHubReleaseParser.isRemoteReleaseNewer(noBuildCodeRelease, localInstalled))
    }

    @Test
    fun `champion detail profile resolves stats ability optimal items and synergy comps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineMetaCacheStore(context)
        val bundle = store.readOrSeedBundle()

        val ahriEntry = bundle.liveChampions.first { it.name.equals("Ahri", ignoreCase = true) }
        val customAhriCarryComp = sampleTftAcademyComp(
            id = "comp_ahri_carry",
            title = "Elderwood Arcanist Ahri",
            environment = TftEnvironment.LIVE,
            tier = TierRank.S,
            carry = "Ahri"
        ).copy(
            avgPlacement = 3.45,
            hexPlacements = listOf(
                HexUnitPlacement("Ornn", 0, 3, cost = 4, items = listOf("Warmog's Armor")),
                HexUnitPlacement(
                    "Ahri",
                    3,
                    6,
                    cost = 4,
                    stars = 2,
                    isCarry = true,
                    items = listOf("Blue Buff", "Jeweled Gauntlet", "Rabadon's Deathcap")
                )
            )
        )
        val supportComp = sampleTftAcademyComp(
            id = "comp_ornn_flex",
            title = "Elderwood Ornn Vanguard",
            environment = TftEnvironment.LIVE,
            tier = TierRank.A,
            carry = "Ornn"
        ).copy(
            avgPlacement = 3.90,
            coreUnits = listOf("Ornn", "Ahri", "Syndra"),
            hexPlacements = listOf(
                HexUnitPlacement("Ornn", 0, 3, cost = 4, isCarry = true, items = listOf("Gargoyle Stoneplate")),
                HexUnitPlacement("Ahri", 3, 2, cost = 4, items = listOf("Blue Buff"))
            )
        )

        val profile = com.example.ui.components.resolveChampionDetailProfile(
            championName = "Ahri",
            environment = TftEnvironment.LIVE,
            champions = bundle.liveChampions,
            comps = listOf(supportComp, customAhriCarryComp),
            items = bundle.liveItems
        )

        assertTrue(profile.hasExactCatalogStats)
        assertEquals(ahriEntry.cost, profile.cost)
        assertEquals(ahriEntry.tier, profile.tier)
        assertTrue(profile.traits.isNotEmpty())
        assertTrue(profile.abilityDescription.isNotBlank())

        // Carry comp should be ranked first above support comp
        assertEquals(2, profile.synergyComps.size)
        assertEquals("comp_ahri_carry", profile.synergyComps.first().comp.id)
        assertEquals("Primary Carry", profile.synergyComps.first().roleInComp)
        assertTrue(profile.synergyComps.first().isCarry)
        assertEquals("comp_ornn_flex", profile.synergyComps[1].comp.id)
        assertEquals("Secondary Carry", profile.synergyComps[1].roleInComp)

        // Optimal items should merge BIS + comp placements and attach ItemTierEntry metadata
        val blueBuffUsage = profile.optimalItems.firstOrNull { it.itemName.contains("Blue Buff", ignoreCase = true) }
        assertNotNull(blueBuffUsage)
        assertEquals(2, blueBuffUsage!!.compCount)
        assertNotNull(blueBuffUsage.itemEntry)
        assertTrue(profile.optimalItems.any { it.itemName.contains("Rabadon", ignoreCase = true) })
    }

    @Test
    fun `champion detail profile handles apostrophe normalization and comp-only fallback unit`() {
        val khazixComp = sampleTftAcademyComp(
            id = "comp_khazix_reroll",
            title = "Reaper Kha'Zix",
            environment = TftEnvironment.LIVE,
            tier = TierRank.A,
            carry = "Kha'Zix"
        ).copy(
            avgPlacement = 3.80,
            top4Rate = 58.4,
            coreUnits = listOf("Kha'Zix", "CustomScoutUnit"),
            hexPlacements = listOf(
                HexUnitPlacement("Kha'Zix", 1, 2, cost = 2, stars = 3, isCarry = true, items = listOf("Infinity Edge")),
                HexUnitPlacement("CustomScoutUnit", 0, 4, cost = 5, stars = 2, isCarry = false, items = listOf("Warmog's Armor"))
            )
        )

        val fallbackProfile = com.example.ui.components.resolveChampionDetailProfile(
            championName = "CustomScoutUnit",
            environment = TftEnvironment.LIVE,
            champions = emptyList(),
            comps = listOf(khazixComp),
            items = emptyList(),
            fallbackCost = 5
        )
        assertFalse(fallbackProfile.hasExactCatalogStats)
        assertEquals(5, fallbackProfile.cost)
        assertEquals(TierRank.A, fallbackProfile.tier)
        assertEquals(3.80, fallbackProfile.avgPlacement, 0.01)
        assertEquals(1, fallbackProfile.synergyComps.size)
        assertEquals("Itemized Tank", fallbackProfile.synergyComps.first().roleInComp)
        assertTrue(fallbackProfile.optimalItems.any { it.itemName.contains("Warmog", ignoreCase = true) })

        val khazixEntry = com.example.data.model.ChampionTierEntry(
            id = "live_khazix",
            name = "Kha'Zix",
            cost = 2,
            tier = TierRank.A,
            environment = TftEnvironment.LIVE,
            traits = listOf("Reaper"),
            role = "Assassin Carry",
            avgPlacement = 3.72,
            top4Rate = 60.0,
            bestItems = listOf("Infinity Edge", "Bloodthirster"),
            synergyComps = listOf("Reaper Kha'Zix"),
            abilityName = "Taste Their Fear",
            abilityDescription = "Slashes target for bonus isolated physical damage."
        )
        val normalizedProfile = com.example.ui.components.resolveChampionDetailProfile(
            championName = "TFT18_KhaZix",
            environment = TftEnvironment.LIVE,
            champions = listOf(khazixEntry),
            comps = listOf(khazixComp),
            items = emptyList()
        )
        assertTrue(normalizedProfile.hasExactCatalogStats)
        assertEquals("Kha'Zix", normalizedProfile.name)
        assertEquals("Taste Their Fear", normalizedProfile.abilityName)
        assertEquals(1, normalizedProfile.synergyComps.size)
        assertTrue(normalizedProfile.synergyComps.first().isCarry)
    }

    @Test
    fun `asset prefetch planner prioritizes S-tier carries traits items augments deduplicates and honors scope`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seededBundle = OfflineMetaCacheStore(context).readOrSeedBundle()
        val bundle = seededBundle.copy(
            liveComps = listOf(
                sampleTftAcademyComp("tftninja_live_prefetch_ahri", "Arcanist Ahri", TftEnvironment.LIVE, tier = TierRank.S, carry = "Ahri")
            )
        )

        val liveQueue = com.example.prefetch.AssetPrefetchPlanner.buildPrefetchQueue(
            bundle = bundle,
            scope = com.example.prefetch.PrefetchScope.LIVE,
            maxAssets = 180
        )
        assertTrue(liveQueue.isNotEmpty())
        assertTrue(liveQueue.size <= 180)
        // Every planned URL must be unique and valid http(s)
        assertEquals(liveQueue.size, liveQueue.map { it.url }.distinct().size)
        assertTrue(liveQueue.all { it.url.startsWith("http") })

        // First asset must be the top LIVE comp carry's primary square portrait
        val topLiveComp = bundle.liveComps
            .sortedWith(compareBy({ it.tier.sortOrder }, { it.avgPlacement }))
            .first()
        val expectedCarryUrl = com.example.ui.components.TftAssetIconResolver
            .resolveSquareChampionPortraitUrl(topLiveComp.carryName, TftEnvironment.LIVE)
        assertEquals(expectedCarryUrl, liveQueue.first().url)
        assertEquals(com.example.prefetch.PrefetchAssetCategory.CHAMPION_PORTRAIT, liveQueue.first().category)

        // All four asset categories should be represented across a full LIVE queue
        val fullLiveQueue = com.example.prefetch.AssetPrefetchPlanner.buildPrefetchQueue(
            bundle = bundle,
            scope = com.example.prefetch.PrefetchScope.LIVE,
            maxAssets = 500
        )
        val categories = fullLiveQueue.map { it.category }.toSet()
        assertTrue(categories.contains(com.example.prefetch.PrefetchAssetCategory.CHAMPION_PORTRAIT))
        assertTrue(categories.contains(com.example.prefetch.PrefetchAssetCategory.TRAIT_ICON))
        assertTrue(categories.contains(com.example.prefetch.PrefetchAssetCategory.ITEM_ICON))
        assertTrue(categories.contains(com.example.prefetch.PrefetchAssetCategory.AUGMENT_ICON))

        // PBE scope must resolve PBE Set 19 assets rather than LIVE-only entries
        val pbeQueue = com.example.prefetch.AssetPrefetchPlanner.buildPrefetchQueue(
            bundle = bundle,
            scope = com.example.prefetch.PrefetchScope.PBE,
            maxAssets = 120
        )
        assertTrue(pbeQueue.isNotEmpty())
        assertTrue(pbeQueue.any { it.url.contains("sunderarmor.com", ignoreCase = true) })
    }

    @Test
    fun `asset prefetch executor skips disk cached urls handles partial failures and persists telemetry`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.prefetch.PrefetchTelemetryStore.resetForTests(context)

        val targets = listOf(
            com.example.prefetch.PrefetchAssetTarget("https://cdn.example/cached1.png", 128, com.example.prefetch.PrefetchAssetCategory.CHAMPION_PORTRAIT),
            com.example.prefetch.PrefetchAssetTarget("https://cdn.example/cached2.png", 64, com.example.prefetch.PrefetchAssetCategory.ITEM_ICON),
            com.example.prefetch.PrefetchAssetTarget("https://cdn.example/new1.png", 128, com.example.prefetch.PrefetchAssetCategory.CHAMPION_PORTRAIT),
            com.example.prefetch.PrefetchAssetTarget("https://cdn.example/new2.png", 64, com.example.prefetch.PrefetchAssetCategory.TRAIT_ICON),
            com.example.prefetch.PrefetchAssetTarget("https://cdn.example/fail1.png", 64, com.example.prefetch.PrefetchAssetCategory.AUGMENT_ICON)
        )

        val fetchedUrls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val summary = com.example.prefetch.AssetPrefetchExecutor.prefetchTargets(
            targets = targets,
            isCachedOnDisk = { url -> url.contains("cached") },
            fetchTarget = { target ->
                fetchedUrls.add(target.url)
                if (target.url.contains("fail")) throw java.io.IOException("Simulated 404")
                true
            },
            maxConcurrency = 3
        )

        assertEquals(5, summary.plannedCount)
        assertEquals(2, summary.skippedCachedCount)
        assertEquals(2, summary.downloadedCount)
        assertEquals(1, summary.failedCount)
        assertEquals(4, summary.totalReadyCount)
        // Cached URLs must never hit the network fetcher
        assertFalse(fetchedUrls.any { it.contains("cached") })
        assertEquals(3, fetchedUrls.size)

        // Verify PrefetchTelemetryStore persists and re-hydrates accurately across restarts
        com.example.prefetch.PrefetchTelemetryStore.markCompleted(
            context = context,
            scope = com.example.prefetch.PrefetchScope.WARM_LAUNCH,
            cached = summary.downloadedCount,
            skipped = summary.skippedCachedCount,
            failed = summary.failedCount,
            totalPlanned = summary.plannedCount,
            completedAtMillis = 123_456_789L
        )
        assertFalse(com.example.prefetch.PrefetchTelemetryStore.status.value.isRunning)
        assertEquals(4, com.example.prefetch.PrefetchTelemetryStore.status.value.totalReadyCount)

        // Clear in-memory value only via fresh hydrate call and verify SharedPreferences restores it
        com.example.prefetch.PrefetchTelemetryStore.hydrate(context)
        val restored = com.example.prefetch.PrefetchTelemetryStore.status.value
        assertEquals(4, restored.totalReadyCount)
        assertEquals(5, restored.totalPlannedCount)
        assertEquals(123_456_789L, restored.lastCompletedAtMillis)
        assertEquals("WARM_LAUNCH", restored.lastScope)
    }

    @Test
    fun `meta prefetch scheduler enqueues periodic KEEP and immediate WorkManager requests and survives boot`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching {
            androidx.work.WorkManager.initialize(
                context,
                androidx.work.Configuration.Builder().build()
            )
        }
        val wm = androidx.work.WorkManager.getInstance(context)

        com.example.prefetch.MetaPrefetchScheduler.schedulePeriodicPrefetch(context)
        val periodicFirst = wm.getWorkInfosForUniqueWork(
            com.example.prefetch.MetaPrefetchScheduler.PERIODIC_PREFETCH_WORK_NAME
        ).get()
        assertEquals(1, periodicFirst.size)
        assertTrue(periodicFirst.first().tags.contains(com.example.prefetch.MetaPrefetchScheduler.TAG_PREFETCH))

        // Calling schedulePeriodicPrefetch again must KEEP the same WorkRequest ID
        com.example.prefetch.MetaPrefetchScheduler.schedulePeriodicPrefetch(context)
        val periodicSecond = wm.getWorkInfosForUniqueWork(
            com.example.prefetch.MetaPrefetchScheduler.PERIODIC_PREFETCH_WORK_NAME
        ).get()
        assertEquals(periodicFirst.first().id, periodicSecond.first().id)

        // Immediate prefetch should enqueue under IMMEDIATE_PREFETCH_WORK_NAME and honor KEEP vs REPLACE
        com.example.prefetch.MetaPrefetchScheduler.enqueuePrefetch(
            context = context,
            scope = com.example.prefetch.PrefetchScope.WARM_LAUNCH,
            refreshSnapshot = false,
            replaceExisting = true
        )
        val immediateFirst = wm.getWorkInfosForUniqueWork(
            com.example.prefetch.MetaPrefetchScheduler.IMMEDIATE_PREFETCH_WORK_NAME
        ).get()
        assertEquals(1, immediateFirst.size)

        com.example.prefetch.MetaPrefetchScheduler.enqueuePrefetch(
            context = context,
            scope = com.example.prefetch.PrefetchScope.LIVE,
            refreshSnapshot = false,
            replaceExisting = false
        )
        val immediateKept = wm.getWorkInfosForUniqueWork(
            com.example.prefetch.MetaPrefetchScheduler.IMMEDIATE_PREFETCH_WORK_NAME
        ).get()
        assertEquals(immediateFirst.first().id, immediateKept.first().id)

        // Boot receiver should also ensure periodic prefetch is scheduled
        val bootReceiver = com.example.notifications.BackgroundSyncBootReceiver()
        bootReceiver.onReceive(context, android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED))
        val periodicAfterBoot = wm.getWorkInfosForUniqueWork(
            com.example.prefetch.MetaPrefetchScheduler.PERIODIC_PREFETCH_WORK_NAME
        ).get()
        assertEquals(1, periodicAfterBoot.size)
    }

    @Test
    fun `hex avatar bounds scale cleanly without capping to fill landscape high dpi hex cells`() {
        // Simulated landscape high-DPI overlay hex radius (e.g. xxhdpi/xxxhdpi right-side board pane)
        val landscapeHexRadiusPx = 68f
        val (hexWpx, hexHpx) = com.example.ui.components.computeHexAvatarPixelBounds(
            hexRadiusPx = landscapeHexRadiusPx,
            minWidthPx = 18,
            minHeightPx = 20
        )
        // Must fill the full sqrt(3)*R x 2*R bounding box instead of being clamped at 60x70
        assertTrue(hexWpx >= kotlin.math.floor(1.7320508f * landscapeHexRadiusPx).toInt())
        assertTrue(hexHpx >= kotlin.math.floor(2.0f * landscapeHexRadiusPx).toInt())
        assertTrue(hexWpx > 100)
        assertTrue(hexHpx > 120)
        // Aspect ratio must preserve regular pointed hexagon ratio (2 / sqrt(3) ~= 1.1547)
        val ratio = hexHpx.toFloat() / hexWpx.toFloat()
        assertEquals(1.1547f, ratio, 0.03f)

        // Tiny compact boards should still honor safe minimum bounds
        val (minW, minH) = com.example.ui.components.computeHexAvatarPixelBounds(
            hexRadiusPx = 5f,
            minWidthPx = 18,
            minHeightPx = 20
        )
        assertEquals(18, minW)
        assertEquals(20, minH)
    }

    @Test
    fun `display refresh rate helper selects highest supported hz at native resolution without downgrading`() {
        val current60 = com.example.ui.util.DisplayModeSpec(
            modeId = 1,
            physicalWidth = 1440,
            physicalHeight = 3120,
            refreshRateHz = 60f
        )
        val supported = listOf(
            current60,
            com.example.ui.util.DisplayModeSpec(2, 1440, 3120, 120f),
            com.example.ui.util.DisplayModeSpec(3, 1080, 2340, 144f) // lower resolution — must NOT downgrade!
        )
        val selected = com.example.ui.util.DisplayRefreshRateHelper.selectOptimalDisplayMode(
            currentMode = current60,
            supportedModes = supported
        )
        assertEquals(2, selected.modeId)
        assertEquals(120f, selected.refreshRateHz, 0.01f)
        assertEquals(1440, selected.physicalWidth)
        assertEquals(3120, selected.physicalHeight)

        // When 144Hz is available at the exact native resolution, select 144Hz
        val withNative144 = supported + com.example.ui.util.DisplayModeSpec(4, 1440, 3120, 144f)
        val selected144 = com.example.ui.util.DisplayRefreshRateHelper.selectOptimalDisplayMode(
            currentMode = current60,
            supportedModes = withNative144
        )
        assertEquals(4, selected144.modeId)
        assertEquals(144f, selected144.refreshRateHz, 0.01f)
    }

    @Test
    fun `bottom navigation places Patches between Tiers and About with valid deep link routing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            listOf(AppTab.META, AppTab.TIERS, AppTab.PATCHES, AppTab.ABOUT),
            AppTab.entries
        )
        assertEquals("Meta", context.getString(AppTab.META.labelRes))
        assertEquals("Tiers", context.getString(AppTab.TIERS.labelRes))
        assertEquals("Patches", context.getString(AppTab.PATCHES.labelRes))
        assertEquals("About", context.getString(AppTab.ABOUT.labelRes))

        assertEquals(AppTab.META, AppTab.fromKey("meta"))
        assertEquals(AppTab.TIERS, AppTab.fromKey("tiers"))
        assertEquals(AppTab.PATCHES, AppTab.fromKey("patches"))
        assertEquals(AppTab.ABOUT, AppTab.fromKey("about"))
        // Legacy "alerts" deep link safely resolves to About tab
        assertEquals(AppTab.ABOUT, AppTab.fromKey("alerts"))
        assertEquals(AppTab.META, AppTab.fromKey(null))
    }

    @Test
    fun `tft team planner code encoder generates valid riot v02 codes for LIVE Set 18 and PBE Set 19 without cross-set contamination`() {
        val mockLiveCDragonJson = """
            {
              "TFTSet16": [
                {"character_id": "TFT16_Ezreal", "display_name": "Ezreal", "team_planner_code": 99}
              ],
              "TFTSet18": [
                {"character_id": "DA_Lux18_Blackthorn", "display_name": "Lux", "team_planner_code": 3840},
                {"character_id": "DA_18_Ezreal", "display_name": "Ezreal", "team_planner_code": 16},
                {"character_id": "DA_18_Sentry", "display_name": "Sentry", "team_planner_code": 1065},
                {"character_id": "DA_Lux18_Base", "display_name": "Lux", "team_planner_code": 4095},
                {"character_id": "DA_CrimsonRaptor18", "display_name": "Crimson Raptor", "team_planner_code": 7},
                {"character_id": "DA_18_GnarSmall", "display_name": "Gnar", "team_planner_code": 42},
                {"character_id": "DA_Cinderling18", "display_name": "Cinderling", "team_planner_code": 1015}
              ]
            }
        """.trimIndent()

        val liveCatalog = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(mockLiveCDragonJson)
        assertNotNull(liveCatalog)
        assertTrue(liveCatalog!!.isFromRiotCommunityDragon)

        // League client patchTag "16.23" must NOT cause LIVE Set 18 comp to target TFTSet16
        val liveComp = MetaComp(
            id = "set18-ezreal-pebbles",
            environment = TftEnvironment.LIVE,
            title = "Ezreal Pebbles Reroll",
            tier = TierRank.S,
            playstyle = "Slow Roll",
            avgPlacement = 3.9,
            top4Rate = 61.2,
            winRate = 18.4,
            pickRate = 9.5,
            trendDelta = "+0.2",
            isTrending = true,
            patchTag = "16.23",
            carryName = "Ezreal",
            traits = listOf("Elderwood"),
            coreUnits = listOf("Ezreal", "Pebbles", "Lux", "Mama Beak", "Gnar", "Cinderling", "Stonebark Tree", "Lifeblossom"),
            hexPlacements = listOf(
                HexUnitPlacement("Ezreal", row = 3, col = 0, cost = 4, isCarry = true, items = listOf("IE", "LW", "GS")),
                HexUnitPlacement("Pebbles", row = 0, col = 3, cost = 3, isCarry = false, items = listOf("Warmog", "Bramble")),
                HexUnitPlacement("Lux", row = 3, col = 6, cost = 3, isCarry = false, items = emptyList()),
                HexUnitPlacement("Mama Beak", row = 1, col = 2, cost = 2, isCarry = false, items = emptyList()),
                HexUnitPlacement("Gnar", row = 0, col = 4, cost = 2, isCarry = false, items = emptyList()),
                HexUnitPlacement("Cinderling", row = 1, col = 5, cost = 1, isCarry = false, items = emptyList()),
                HexUnitPlacement("Stonebark Tree", row = 0, col = 1, cost = 1, isCarry = false, items = emptyList())
            ),
            augmentPriorities = listOf("Combat", "Econ"),
            levelingGuide = "Slow roll at 6",
            notes = "Corner Ezreal",
            setName = "Set 18"
        )

        val liveResult = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(liveComp, liveCatalog)
        assertTrue(liveResult.isSuccess)
        assertEquals("TFTSet18", liveResult.setKey)
        assertTrue(com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(liveResult.code))
        assertTrue(Regex("^02[0-9a-f]{30}TFTSet18$").matches(liveResult.code))
        // Order: Ezreal (010, carry), Pebbles/Sentry (429=1065, 2 items), Lux Base (fff=4095, NOT Blackthorn variant),
        // Mama Beak (007), Gnar (02a), Cinderling (3f7=1015) + 4 empty slots (000)
        assertEquals("02010429fff00702a3f7000000000000TFTSet18", liveResult.code)
        assertEquals(6, liveResult.matchedChampions.size)
        assertTrue(liveResult.skippedUnits.contains("Stonebark Tree"))
        assertTrue(liveResult.skippedUnits.contains("Lifeblossom"))

        // Hex-string team_planner_code values ("429" and "3f7") must parse in base 16 (not decimal 429 -> 1ad)
        val mockHexStringJson = """
            {
              "TFTSet18": [
                {"character_id": "DA_18_Sentry", "display_name": "Sentry", "team_planner_code": "429"},
                {"character_id": "DA_Cinderling18", "display_name": "Cinderling", "team_planner_code": "3f7"}
              ]
            }
        """.trimIndent()
        val hexCatalog = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(mockHexStringJson)
        assertNotNull(hexCatalog)
        val hexResult = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(
            liveComp.copy(
                carryName = "Pebbles",
                coreUnits = listOf("Pebbles", "Cinderling"),
                hexPlacements = listOf(
                    HexUnitPlacement("Pebbles", row = 0, col = 3, cost = 3, isCarry = true, items = emptyList()),
                    HexUnitPlacement("Cinderling", row = 1, col = 4, cost = 1, isCarry = false, items = emptyList())
                )
            ),
            hexCatalog!!
        )
        assertTrue(hexResult.isSuccess)
        assertEquals("024293f7000000000000000000000000TFTSet18", hexResult.code)

        // PBE Set 19 encoding resolves Riot internal aliases (Wukong -> TFT19_MonkeyKing, Renata Glasc, Akali KDA)
        val mockPbeJson = """
            {
              "TFTSet10": [
                {"character_id": "TFT10_Ahri", "display_name": "Ahri", "team_planner_code": 11},
                {"character_id": "TFT10_Akali", "display_name": "Akali", "team_planner_code": 12}
              ],
              "TFTSet19": [
                {"character_id": "TFT19_MonkeyKing", "display_name": "Wukong", "team_planner_code": 1280},
                {"character_id": "TFT19_AkaliKDA", "display_name": "Akali KDA", "team_planner_code": 1281},
                {"character_id": "TFT19_Renata", "display_name": "Renata", "team_planner_code": 1282}
              ]
            }
        """.trimIndent()
        val pbeCatalog = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(mockPbeJson)
        assertNotNull(pbeCatalog)

        val pbeComp = liveComp.copy(
            id = "pbe-set19-prismatic",
            environment = TftEnvironment.PBE,
            title = "Prismatic Wukong KDA",
            patchTag = "16.24 PBE",
            setName = "Set 19 PBE",
            carryName = "Wukong",
            coreUnits = listOf("Wukong", "Akali KDA", "Renata Glasc"),
            hexPlacements = listOf(
                HexUnitPlacement("Wukong", row = 0, col = 3, cost = 5, isCarry = true, items = listOf("Bloodthirster")),
                HexUnitPlacement("Akali KDA", row = 2, col = 5, cost = 4, isCarry = false, items = emptyList()),
                HexUnitPlacement("Renata Glasc", row = 3, col = 0, cost = 3, isCarry = false, items = emptyList())
            )
        )
        val pbeResult = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(pbeComp, pbeCatalog!!)
        assertTrue(pbeResult.isSuccess)
        assertEquals("TFTSet19", pbeResult.setKey)
        assertTrue(Regex("^02[0-9a-f]{30}TFTSet19$").matches(pbeResult.code))
        // 1280=500, 1281=501, 1282=502 -> emits Riot's exact set key from tftchampions-teamplanner.json
        assertEquals("02500501502000000000000000000000TFTSet19", pbeResult.code)

        // Strict set guard: PBE Set 19 comp MUST NOT fall back to TFTSet18 or TFTSet10 when TFTSet19 is missing
        val wrongSetResult = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(pbeComp, liveCatalog)
        org.junit.Assert.assertFalse(wrongSetResult.isSuccess)
        assertEquals("", wrongSetResult.code)
        assertEquals("TFTSet19", wrongSetResult.setKey)

        // Explicit direct teamPlannerCode passthrough (must match target set)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directCode = "02010429fff00702a3f7000000000000TFTSet18"
        val directComp = liveComp.copy(teamPlannerCode = directCode)
        val directResult = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, directComp)
        }
        assertTrue(directResult.isSuccess)
        assertEquals(directCode, directResult.code)
        assertTrue(directResult.isVerifiedRiotMapping)

        // Offline uncached resolution MUST fail cleanly rather than emitting bogus synthetic 001/002/003 codes
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        val offlineClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { throw java.io.IOException("Offline test") }
            .build()
        val offlineResult = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, liveComp, offlineClient)
        }
        org.junit.Assert.assertFalse(offlineResult.isSuccess)
        assertEquals("", offlineResult.code)
        assertNotNull(offlineResult.errorMessage)
    }

    @Test
    fun `overlay army search engine filters comps by multiple champions completed items base component recipes and query`() {
        val ezrealComp = MetaComp(
            id = "live-ezreal",
            environment = TftEnvironment.LIVE,
            title = "Elderwood Ezreal",
            tier = TierRank.S,
            playstyle = "Fast 8",
            avgPlacement = 3.8,
            top4Rate = 60.0,
            winRate = 17.0,
            pickRate = 8.0,
            trendDelta = "+0.3",
            isTrending = true,
            patchTag = "16.23",
            carryName = "Ezreal",
            traits = listOf("Elderwood", "Sniper"),
            coreUnits = listOf("Ezreal", "Pebbles", "Gnar"),
            hexPlacements = listOf(
                HexUnitPlacement("Ezreal", row = 3, col = 0, cost = 4, isCarry = true, items = listOf("Infinity Edge", "Last Whisper")),
                HexUnitPlacement("Pebbles", row = 0, col = 3, cost = 3, isCarry = false, items = listOf("Warmog's Armor"))
            ),
            augmentPriorities = emptyList(),
            levelingGuide = "Fast 8",
            notes = "",
            setName = "Set 18"
        )
        val luxComp = ezrealComp.copy(
            id = "live-lux",
            title = "Coven Lux",
            carryName = "Lux",
            traits = listOf("Coven", "Invoker"),
            coreUnits = listOf("Lux", "Pebbles", "Amumu"),
            hexPlacements = listOf(
                HexUnitPlacement("Lux", row = 3, col = 6, cost = 3, isCarry = true, items = listOf("Spear of Shojin", "Jeweled Gauntlet")),
                HexUnitPlacement("Pebbles", row = 0, col = 3, cost = 3, isCarry = false, items = listOf("Bramble Vest"))
            )
        )
        val comps = listOf(ezrealComp, luxComp)

        // Single champion matches both comps sharing Pebbles
        val pebblesArmies = com.example.overlay.OverlayArmySearchEngine.filterArmies(
            comps = comps,
            selectedChampions = setOf("Pebbles"),
            selectedItems = emptySet(),
            query = ""
        )
        assertEquals(listOf("live-ezreal", "live-lux"), pebblesArmies.map { it.id })

        // Multi-champion AND intersection narrows to Lux comp
        val pebblesAndLux = com.example.overlay.OverlayArmySearchEngine.filterArmies(
            comps = comps,
            selectedChampions = setOf("Pebbles", "Lux"),
            selectedItems = emptySet(),
            query = ""
        )
        assertEquals(listOf("live-lux"), pebblesAndLux.map { it.id })

        // Exact completed item filter
        val ieArmies = com.example.overlay.OverlayArmySearchEngine.filterArmies(
            comps = comps,
            selectedChampions = emptySet(),
            selectedItems = setOf("Infinity Edge"),
            query = ""
        )
        assertEquals(listOf("live-ezreal"), ieArmies.map { it.id })

        // Base component recipe matching: B.F. Sword builds both Infinity Edge (Ezreal) and Spear of Shojin (Lux)
        val bfSwordArmies = com.example.overlay.OverlayArmySearchEngine.filterArmies(
            comps = comps,
            selectedChampions = emptySet(),
            selectedItems = setOf("B.F. Sword"),
            query = ""
        )
        assertEquals(listOf("live-ezreal", "live-lux"), bfSwordArmies.map { it.id })

        // Combined base component (Tear -> Spear of Shojin) + Champion (Pebbles) narrows to Lux comp
        val tearAndPebbles = com.example.overlay.OverlayArmySearchEngine.filterArmies(
            comps = comps,
            selectedChampions = setOf("Pebbles"),
            selectedItems = setOf("Tear of the Goddess"),
            query = ""
        )
        assertEquals(listOf("live-lux"), tearAndPebbles.map { it.id })

        // Free-text query matches trait or item
        val invokerQuery = com.example.overlay.OverlayArmySearchEngine.filterArmies(
            comps = comps,
            selectedChampions = emptySet(),
            selectedItems = emptySet(),
            query = "invoker"
        )
        assertEquals(listOf("live-lux"), invokerQuery.map { it.id })

        // Choice extraction surfaces champions and both base components + completed items
        val champChoices = com.example.overlay.OverlayArmySearchEngine.extractChampionChoices(comps, emptyList(), TftEnvironment.LIVE)
        assertTrue(champChoices.any { it.name == "Pebbles" && it.compCount == 2 })
        val itemChoices = com.example.overlay.OverlayArmySearchEngine.extractItemChoices(comps, emptyList(), TftEnvironment.LIVE)
        assertTrue(itemChoices.any { it.name == "B.F. Sword" && it.isComponent && it.compCount == 2 })
        assertTrue(itemChoices.any { it.name == "Infinity Edge" && !it.isComponent && it.compCount == 1 })
    }

    @Test
    fun `overlay session state preserves last clicked filters army finder selections expanded tiers scroll and selected comp until reset on full close`() {
        val state = com.example.overlay.OverlaySessionState()

        // Clean initial defaults
        assertEquals(com.example.overlay.OverlayBrowseMode.ALL_COMPS, state.browseMode)
        assertEquals(TftEnvironment.LIVE, state.envFilter)
        org.junit.Assert.assertNull(state.tierFilter)
        org.junit.Assert.assertNull(state.selectedCompId)
        org.junit.Assert.assertNull(state.inspectedChampion)
        assertTrue(state.expandedTiers.isEmpty())
        assertEquals(0, state.listScrollIndex)
        assertEquals(0, state.listScrollOffset)
        assertEquals(0, state.detailScrollOffset)
        org.junit.Assert.assertFalse(state.isArmyFinderOpen)
        org.junit.Assert.assertFalse(state.hasActiveArmyFilters)

        // User interacts with overlay controls, Army Finder, and opens a comp
        state.selectEnvironment(TftEnvironment.PBE)
        state.selectBrowseMode(com.example.overlay.OverlayBrowseMode.FAVORITES)
        state.toggleTierFilter(TierRank.S)
        state.toggleTierExpanded(TierRank.S)
        state.toggleTierExpanded(TierRank.A)
        state.toggleArmyFinder()
        state.selectFinderTab(com.example.overlay.OverlayFinderTab.ITEMS)
        state.selectFinderItemScope(com.example.overlay.OverlayItemFilterScope.COMPONENTS)
        state.toggleSelectedChampion("Wukong")
        state.toggleSelectedItem("B.F. Sword")
        state.updateFinderQuery("kda")
        state.updateListScroll(index = 2, offset = 140)
        state.selectComp("pbe-comp-01")
        state.updateDetailScroll(offset = 220)
        state.inspectChampion("Ahri", 4)

        // State remains completely intact across minimize/reopen cycles
        assertEquals(com.example.overlay.OverlayBrowseMode.FAVORITES, state.browseMode)
        assertEquals(TftEnvironment.PBE, state.envFilter)
        assertEquals(TierRank.S, state.tierFilter)
        assertEquals(setOf(TierRank.S, TierRank.A), state.expandedTiers)
        assertTrue(state.isArmyFinderOpen)
        assertEquals(com.example.overlay.OverlayFinderTab.ITEMS, state.finderTab)
        assertEquals(com.example.overlay.OverlayItemFilterScope.COMPONENTS, state.finderItemScope)
        assertEquals(setOf("Wukong"), state.selectedChampions)
        assertEquals(setOf("B.F. Sword"), state.selectedItems)
        assertEquals("kda", state.finderQuery)
        assertEquals(3, state.activeArmyFilterCount)
        assertTrue(state.hasActiveArmyFilters)
        assertEquals(2, state.listScrollIndex)
        assertEquals(140, state.listScrollOffset)
        assertEquals("pbe-comp-01", state.selectedCompId)
        assertEquals(220, state.detailScrollOffset)
        assertEquals("Ahri" to 4, state.inspectedChampion)

        // Switching LIVE <-> PBE clears set-specific champion/item filters cleanly
        state.selectEnvironment(TftEnvironment.LIVE)
        assertTrue(state.selectedChampions.isEmpty())
        assertTrue(state.selectedItems.isEmpty())
        assertEquals("", state.finderQuery)

        // Full overlay close resets every field back to clean defaults
        state.reset()
        assertEquals(com.example.overlay.OverlayBrowseMode.ALL_COMPS, state.browseMode)
        assertEquals(TftEnvironment.LIVE, state.envFilter)
        org.junit.Assert.assertNull(state.tierFilter)
        org.junit.Assert.assertNull(state.selectedCompId)
        org.junit.Assert.assertNull(state.inspectedChampion)
        assertTrue(state.expandedTiers.isEmpty())
        assertEquals(0, state.listScrollIndex)
        assertEquals(0, state.listScrollOffset)
        assertEquals(0, state.detailScrollOffset)
        org.junit.Assert.assertFalse(state.isArmyFinderOpen)
        assertEquals(com.example.overlay.OverlayFinderTab.CHAMPIONS, state.finderTab)
        assertEquals(com.example.overlay.OverlayItemFilterScope.ALL, state.finderItemScope)
        assertTrue(state.selectedChampions.isEmpty())
        assertTrue(state.selectedItems.isEmpty())
        assertEquals("", state.finderQuery)
    }

    @Test
    fun `overlay bauble gesture controller drags smoothly from center or any point and taps to toggle`() {
        val bounds = com.example.overlay.OverlayWindowBounds(
            widthPx = 1080,
            heightPx = 2400,
            safeLeftPx = 0,
            safeTopPx = 80,
            safeRightPx = 0,
            safeBottomPx = 60,
            density = 3f
        )
        var posX = 40
        var posY = 120
        var pressed = false
        var dragging = false
        var toggleCount = 0

        val controller = com.example.overlay.OverlayBaubleGestureController(
            touchSlopPx = 12,
            marginPx = 18,
            currentOriginProvider = { posX to posY },
            viewSizeProvider = { 192 to 192 },
            boundsProvider = { bounds },
            onPressedChanged = { pressed = it },
            onDraggingChanged = { dragging = it },
            onPositionUpdated = { x, y ->
                posX = x
                posY = y
            },
            onTap = { toggleCount += 1 }
        )

        // 1. Tap right in the center of the bauble (within touch slop) -> toggles drawer without moving
        assertTrue(controller.onTouchEvent(android.view.MotionEvent.ACTION_DOWN, 136f, 216f))
        assertTrue(pressed)
        org.junit.Assert.assertFalse(dragging)
        assertTrue(controller.onTouchEvent(android.view.MotionEvent.ACTION_MOVE, 141f, 220f))
        org.junit.Assert.assertFalse(dragging)
        assertTrue(controller.onTouchEvent(android.view.MotionEvent.ACTION_UP, 141f, 220f))
        org.junit.Assert.assertFalse(pressed)
        assertEquals(1, toggleCount)
        assertEquals(40, posX)
        assertEquals(120, posY)

        // 2. Drag starting right in the center of the bauble -> moves bauble smoothly & does NOT toggle
        assertTrue(controller.onTouchEvent(android.view.MotionEvent.ACTION_DOWN, 136f, 216f))
        assertTrue(controller.onTouchEvent(android.view.MotionEvent.ACTION_MOVE, 436f, 616f))
        assertTrue(dragging)
        assertEquals(340, posX)
        assertEquals(520, posY)
        assertTrue(controller.onTouchEvent(android.view.MotionEvent.ACTION_UP, 436f, 616f))
        org.junit.Assert.assertFalse(dragging)
        org.junit.Assert.assertFalse(pressed)
        assertEquals(1, toggleCount)
        assertEquals(340, posX)
        assertEquals(520, posY)
    }

    @Test
    fun `pbe set 19 team planner resolves codename keys object maps top level arrays endpoint fallbacks and indie seraphine alias`() {
        val hyperpopComp = MetaComp(
            id = "tftacademy_pbe_hyperpop_reroll",
            environment = TftEnvironment.PBE,
            title = "Hyperpop Reroll",
            tier = TierRank.S,
            playstyle = "1-Cost Reroll",
            avgPlacement = 3.45,
            top4Rate = 65.0,
            winRate = 18.5,
            pickRate = 9.0,
            trendDelta = "Top Meta",
            isTrending = true,
            patchTag = "16.24 PBE",
            carryName = "Ziggs",
            traits = listOf("5 Hyperpop", "2 Arcanist", "2 Frontman"),
            coreUnits = listOf("Kobuko", "Ziggs", "Lulu", "Tristana", "Indie", "Swain", "Teemo"),
            hexPlacements = listOf(
                HexUnitPlacement("Ziggs", row = 3, col = 6, cost = 1, isCarry = true, items = listOf("Guinsoo's Rageblade")),
                HexUnitPlacement("Kobuko", row = 0, col = 3, cost = 1, isCarry = false, items = emptyList()),
                HexUnitPlacement("Lulu", row = 3, col = 5, cost = 2, isCarry = false, items = emptyList()),
                HexUnitPlacement("Tristana", row = 3, col = 0, cost = 2, isCarry = false, items = emptyList()),
                HexUnitPlacement("Indie", row = 3, col = 2, cost = 2, isCarry = false, items = emptyList()),
                HexUnitPlacement("Swain", row = 0, col = 4, cost = 3, isCarry = false, items = emptyList()),
                HexUnitPlacement("Teemo", row = 3, col = 4, cost = 5, isCarry = false, items = emptyList())
            ),
            augmentPriorities = emptyList(),
            levelingGuide = "Slow roll at 5",
            notes = "",
            setName = "Set 19"
        )

        // 1. Codename set key with TFT19_* character_ids + Seraphine form variants (Indie base vs Superstar variant)
        val codenameJson = """
            {
              "TFTSet18": [
                {"character_id": "DA_18_Kobuko", "display_name": "Kobuko", "team_planner_code": 1001},
                {"character_id": "DA_18_Teemo", "display_name": "Teemo", "team_planner_code": 1002}
              ],
              "TFTSetPrismatic": [
                {"character_id": "TFT19_Ziggs", "display_name": "Ziggs", "team_planner_code": 1200},
                {"character_id": "TFT19_Kobuko", "display_name": "Kobuko", "team_planner_code": 1201},
                {"character_id": "TFT19_Lulu", "display_name": "Lulu", "team_planner_code": 1202},
                {"character_id": "TFT19_Tristana", "display_name": "Tristana", "team_planner_code": 1203},
                {"character_id": "TFT19_Seraphine_Superstar", "display_name": "Seraphine", "team_planner_code": 1299},
                {"character_id": "TFT19_Seraphine_Indie", "display_name": "Seraphine", "team_planner_code": 1204},
                {"character_id": "TFT19_Swain", "display_name": "Swain", "team_planner_code": 1205},
                {"character_id": "TFT19_Teemo", "display_name": "Teemo", "team_planner_code": 1206}
              ]
            }
        """.trimIndent()
        val codenameCatalog = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(codenameJson)
        assertNotNull(codenameCatalog)
        val codenameResult = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(hyperpopComp, codenameCatalog!!)
        assertTrue(codenameResult.isSuccess)
        assertEquals("TFTSetPrismatic", codenameResult.setKey)
        assertEquals(7, codenameResult.matchedChampions.size)
        // Base Seraphine_Indie (1204 = 0x4b4) MUST be chosen over Superstar variant (1299 = 0x513)
        assertTrue(codenameResult.code.contains("4b4"))
        org.junit.Assert.assertFalse(codenameResult.code.contains("513"))

        // 2. Object-map JSON structure keyed by character_id under pure numeric set key "19"
        val mapStyleJson = """
            {
              "19": {
                "TFT19_Ziggs": {"display_name": "Ziggs", "team_planner_code": "4b0"},
                "TFT19_Kobuko": {"display_name": "Kobuko", "team_planner_code": "4b1"},
                "TFT19_Seraphine_Indie": {"display_name": "Seraphine", "team_planner_code": "4b4"}
              }
            }
        """.trimIndent()
        val mapCatalog = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(mapStyleJson)
        assertNotNull(mapCatalog)
        val mapResult = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(hyperpopComp, mapCatalog!!)
        assertTrue(mapResult.isSuccess)
        assertEquals("TFTSet19", mapResult.setKey)
        assertTrue(Regex("^02[0-9a-f]{30}TFTSet19$").matches(mapResult.code))

        // 3. Network fallback: if primary pbe teamplanner JSON lacks TFTSet19, encoder continues to tftchampions.json array
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        val fallbackArrayJson = """
            [
              {"character_id": "TFT19_Ziggs", "display_name": "Ziggs", "team_planner_code": 1200},
              {"character_id": "TFT19_Kobuko", "display_name": "Kobuko", "team_planner_code": 1201},
              {"character_id": "TFT19_Lulu", "display_name": "Lulu", "team_planner_code": 1202},
              {"character_id": "TFT19_Seraphine_Indie", "display_name": "Seraphine", "team_planner_code": 1204}
            ]
        """.trimIndent()
        val fallbackClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val bodyStr = if (url.endsWith("tftchampions-teamplanner.json")) {
                    """{"TFTSet18":[{"character_id":"DA_18_Kobuko","display_name":"Kobuko","team_planner_code":1001}]}"""
                } else {
                    fallbackArrayJson
                }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, bodyStr))
                    .build()
            }
            .build()
        val resolved = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, hyperpopComp, fallbackClient)
        }
        assertTrue(resolved.isSuccess)
        assertEquals("TFTSet19", resolved.setKey)
        assertTrue(resolved.code.startsWith("024b0"))
        assertTrue(resolved.code.endsWith("TFTSet19"))
        assertEquals(4, resolved.matchedChampions.size)
    }

    @Test
    fun `set 19 seraphine indie and multi word champion ids format cleanly without altering indie trait`() {
        assertEquals("Seraphine", com.example.ui.components.formatRiotAssetLabel("TFT19_Seraphine_Indie"))
        assertEquals("Seraphine", com.example.ui.components.formatRiotAssetLabel("TFT19_Seraphine_RisingStar"))
        assertEquals("Seraphine", com.example.ui.components.formatRiotAssetLabel("TFT19_Seraphine_Superstar"))
        assertEquals("Akali True Damage", com.example.ui.components.formatRiotAssetLabel("TFT19_Akali_TrueDamage"))
        assertEquals("Akali KDA", com.example.ui.components.formatRiotAssetLabel("TFT19_Akali_KDA"))

        // Cached 'Indie' champion slot normalizes to Seraphine in champion contexts
        assertEquals("Seraphine", com.example.ui.components.formatChampionDisplayName("Indie"))
        assertEquals("Seraphine", com.example.ui.components.formatChampionDisplayName("TFT19_Seraphine_Indie"))

        // Trait contexts MUST keep "Indie" intact
        assertEquals("Indie", com.example.ui.components.formatRiotAssetLabel("Indie"))
        assertEquals("Indie", com.example.ui.components.formatRiotAssetLabel("TFT19_Trait_Indie"))
    }

    @Test
    fun `team composition champions sort strictly by gold cost ascending from 1g upwards`() {
        val mixedComp = MetaComp(
            id = "test_cost_sort",
            title = "Hyperpop Reroll",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            setName = "Set 19",
            patchTag = "19.1-PBE",
            playstyle = "1-Cost Reroll",
            avgPlacement = 3.45,
            top4Rate = 62.0,
            winRate = 18.0,
            pickRate = 4.2,
            trendDelta = "+0.3",
            isTrending = true,
            carryName = "Ziggs",
            coreUnits = listOf("Swain", "Teemo", "Tristana", "Seraphine", "Kobuko", "Ziggs", "Lulu"),
            traits = listOf("5 Hyperpop"),
            hexPlacements = listOf(
                HexUnitPlacement(row = 0, col = 3, unitName = "Swain", cost = 5, stars = 2),
                HexUnitPlacement(row = 3, col = 6, unitName = "Teemo", cost = 4, stars = 2),
                HexUnitPlacement(row = 3, col = 5, unitName = "Tristana", cost = 3, stars = 2),
                HexUnitPlacement(row = 3, col = 4, unitName = "Seraphine", cost = 4, stars = 2),
                HexUnitPlacement(row = 0, col = 2, unitName = "Kobuko", cost = 2, stars = 2),
                HexUnitPlacement(row = 3, col = 0, unitName = "Ziggs", cost = 1, stars = 3, isCarry = true),
                HexUnitPlacement(row = 3, col = 1, unitName = "Lulu", cost = 1, stars = 3)
            ),
            augmentPriorities = emptyList(),
            levelingGuide = "Slow roll at 5",
            notes = ""
        )

        val sorted = com.example.ui.components.sortCompUnitsByCostAscending(mixedComp)
        assertEquals(
            listOf("Ziggs", "Lulu", "Kobuko", "Tristana", "Teemo", "Seraphine", "Swain"),
            sorted
        )
        val sortedCosts = sorted.map { com.example.ui.components.resolveCompUnitCost(it, mixedComp) }
        assertEquals(listOf(1, 1, 2, 3, 4, 4, 5), sortedCosts)
    }

    @Test
    fun `set 19 pbe team planner enforces strict v02 codes resolves metatft and tftacademy live and enriches comps`() {
        val hyperpopComp = MetaComp(
            id = "pbe_set19_hyperpop_test",
            title = "Hyperpop Reroll",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            setName = "Set 19: Prismatic Tour",
            patchTag = "19.1-PBE",
            playstyle = "1-Cost Reroll",
            avgPlacement = 3.45,
            top4Rate = 62.0,
            winRate = 18.0,
            pickRate = 4.2,
            trendDelta = "+0.3",
            isTrending = true,
            carryName = "Ziggs",
            coreUnits = listOf("Ziggs", "Lulu", "Kobuko", "Tristana", "Teemo", "Seraphine", "Swain"),
            traits = listOf("5 Hyperpop"),
            hexPlacements = emptyList(),
            augmentPriorities = emptyList(),
            levelingGuide = "Slow roll at 5",
            notes = "",
            sourceUrl = "https://tftacademy.com/tierlist/comps/set19/hyperpop-reroll"
        )

        // 0. Strict v02 validator: modern sets (Set 14+, including Set 18 LIVE & Set 19 PBE) reject legacy v01 codes
        val legacyV01Set19 = "0101020304050607000000TFTSet19"
        val validV02Set19 = "024b04b14b24b34b44b54b6000000000TFTSet19"
        org.junit.Assert.assertFalse(
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(legacyV01Set19, expectedSetNumber = 19)
        )
        assertTrue(
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(validV02Set19, expectedSetNumber = 19)
        )
        org.junit.Assert.assertFalse(
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(validV02Set19, expectedSetNumber = 18)
        )

        // 1. Wrapped `character_record` objects in v1/tftchampions.json with explicit v02 team_planner_code
        val wrappedV2Json = """
            [
              {"character_record": {"character_id": "TFT19_Ziggs", "display_name": "Ziggs", "team_planner_code": 1200, "cost": 1, "traits": ["Hyperpop"]}},
              {"character_record": {"character_id": "TFT19_Lulu", "display_name": "Lulu", "team_planner_code": 1201, "cost": 1, "traits": ["Hyperpop"]}},
              {"character_record": {"character_id": "TFT19_Kobuko", "display_name": "Kobuko", "team_planner_code": 1202, "cost": 2, "traits": ["Hyperpop"]}}
            ]
        """.trimIndent()
        val wrappedCatalog = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(wrappedV2Json)
        assertNotNull(wrappedCatalog)
        val wrappedRes = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(hyperpopComp, wrappedCatalog!!)
        assertTrue(wrappedRes.isSuccess)
        assertEquals("TFTSet19", wrappedRes.setKey)
        assertTrue(wrappedRes.code.startsWith("024b0"))
        assertTrue(wrappedRes.code.endsWith("TFTSet19"))
        assertEquals(3, wrappedRes.matchedChampions.size)

        // 2. MetaTFT lookup `{ "units": [...] }` JSON resolves v02 Set 19 codes when CommunityDragon pbe-planner only has Set 17/18
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        val metaTftLookupJson = """
            {
              "units": [
                {"apiName": "TFT19_Ziggs", "name": "Ziggs", "team_planner_code": 1200},
                {"apiName": "TFT19_Lulu", "name": "Lulu", "team_planner_code": 1201},
                {"apiName": "TFT19_Kobuko", "name": "Kobuko", "team_planner_code": 1202},
                {"apiName": "TFT19_Tristana", "name": "Tristana", "team_planner_code": 1203},
                {"apiName": "TFT19_Seraphine_Indie", "name": "Seraphine", "team_planner_code": 1204},
                {"apiName": "TFT19_Swain", "name": "Swain", "team_planner_code": 1205},
                {"apiName": "TFT19_Teemo", "name": "Teemo", "team_planner_code": 1206}
              ]
            }
        """.trimIndent()
        val metaTftClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val bodyStr = when {
                    url.contains("data.metatft.com") -> metaTftLookupJson
                    url.endsWith("tftchampions-teamplanner.json") ->
                        """{"TFTSet17":[],"TFTSet18":[{"character_id":"DA_18_Kobuko","display_name":"Kobuko","team_planner_code":1001}]}"""
                    else -> """[]"""
                }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, bodyStr))
                    .build()
            }
            .build()
        val metaTftResolved = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, hyperpopComp, metaTftClient)
        }
        assertTrue(metaTftResolved.isSuccess)
        assertEquals("TFTSet19", metaTftResolved.setKey)
        assertTrue(Regex("^02[0-9a-f]{30}TFTSet19$").matches(metaTftResolved.code))
        assertEquals(7, metaTftResolved.matchedChampions.size)

        // 3. Batch enrichCompsWithTeamCodes automatically attaches v02 copy codes to newly added/updated comps
        val uncodedComp = hyperpopComp.copy(id = "pbe_set19_newly_added", teamPlannerCode = "")
        val enriched = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.enrichCompsWithTeamCodes(
                context = context,
                comps = listOf(uncodedComp),
                environment = TftEnvironment.PBE,
                client = metaTftClient
            )
        }
        assertEquals(1, enriched.size)
        assertTrue(
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(
                enriched.first().teamPlannerCode,
                expectedSetNumber = 19
            )
        )

        // 4. Live TFTAcademy guide fallback: when CDragon & MetaTFT lack Set 19 v02 IDs, resolver extracts v02 code from comp guide URL
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }
        val tftAcademyGuideClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val (code, bodyStr) = when {
                    url.contains("tftacademy.com/tierlist/comps/set19/hyperpop-reroll") ->
                        200 to """<html><body><script>window.__COMP__={"title":"Hyperpop Reroll","teamPlanner":{"code":"$validV02Set19"}};</script></body></html>"""
                    url.endsWith("tftchampions-teamplanner.json") ->
                        200 to """{"TFTSet17":[],"TFTSet18":[{"character_id":"DA_18_Kobuko","display_name":"Kobuko","team_planner_code":1001}]}"""
                    else -> 404 to """{"error":"not found"}"""
                }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Not Found")
                    .body(okhttp3.ResponseBody.create(null, bodyStr))
                    .build()
            }
            .build()
        val academyResolved = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, hyperpopComp, tftAcademyGuideClient)
        }
        assertTrue(academyResolved.isSuccess)
        assertEquals("024b04b14b24b34b44b54b6000000000TFTSet19", academyResolved.code)
        assertEquals("TFTSet19", academyResolved.setKey)

        // 5. Never emits bogus v01 code when NO v02 source has Set 19 IDs
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }
        val noSet19Client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, """{"TFTSet18":[{"character_id":"DA_18_Kobuko","display_name":"Kobuko","team_planner_code":1001}]}"""))
                    .build()
            }
            .build()
        val unavailable = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, hyperpopComp, noSet19Client)
        }
        org.junit.Assert.assertFalse(unavailable.isSuccess)
        org.junit.Assert.assertFalse(unavailable.code.startsWith("01"))
    }

    @Test
    fun `tftacademy site code extractor decodes sveltekit data json and js chunks and prioritizes site copy code`() {
        val exactSiteCode = "024b04b14b24b34b44b54b6000000000TFTSet19"

        // 1. SvelteKit `/__data.json` devalue unflattening extracts exact site copy code per comp
        val devalueDataJson = """
            {
              "type": "data",
              "nodes": [
                {
                  "type": "data",
                  "data": [
                    {"guides": 1},
                    [2],
                    {"title": 3, "compSlug": 4, "teamCode": 5},
                    "Hyperpop Reroll",
                    "set19-hyperpop-reroll",
                    "$exactSiteCode"
                  ]
                }
              ]
            }
        """.trimIndent()
        val decodedNodes = com.example.data.teamplanner.TftAcademySiteCodeExtractor.decodeSvelteKitDataJsonNodes(devalueDataJson)
        assertEquals(1, decodedNodes.size)
        val extractedMap = LinkedHashMap<String, String>()
        com.example.data.teamplanner.TftAcademySiteCodeExtractor.extractDirectCompCodesFromJson(
            node = decodedNodes.first(),
            expectedSetNumber = 19,
            out = extractedMap
        )
        assertEquals(exactSiteCode, extractedMap["hyperpopreroll"])
        assertEquals(exactSiteCode, extractedMap["set19hyperpopreroll"])

        // 2. Minified SvelteKit `_app/immutable/chunks/*.js` champion map & object array extraction
        val minifiedJsChunk = """
            const SET_SUFFIX="TFTSet19";
            const PLANNER_MAP={TFT19_Ziggs:"4b0",TFT19_Lulu:"4b1",TFT19_Kobuko:"4b2",TFT19_Tristana:"4b3",TFT19_Seraphine_Indie:"4b4",TFT19_Swain:"4b5",TFT19_Teemo:"4b6"};
        """.trimIndent()
        val jsCatalog = com.example.data.teamplanner.TftAcademySiteCodeExtractor.extractCatalogFromJsText(
            jsText = minifiedJsChunk,
            expectedSetNumber = 19,
            detectedSetSuffix = "TFTSet19"
        )
        assertNotNull(jsCatalog)
        val pbeComp = MetaComp(
            id = "tftacademy_pbe_hyperpop_reroll",
            title = "Hyperpop Reroll",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            setName = "Set 19",
            patchTag = "19.1-PBE",
            playstyle = "1-Cost Reroll",
            avgPlacement = 3.45,
            top4Rate = 62.0,
            winRate = 18.0,
            pickRate = 4.2,
            trendDelta = "+0.3",
            isTrending = true,
            carryName = "Ziggs",
            coreUnits = listOf("Ziggs", "Lulu", "Kobuko", "Tristana", "Seraphine", "Swain", "Teemo"),
            traits = listOf("5 Hyperpop"),
            hexPlacements = emptyList(),
            augmentPriorities = emptyList(),
            levelingGuide = "Slow roll at 5",
            notes = "",
            sourceUrl = "https://tftacademy.com/tierlist/comps/set19/set19-hyperpop-reroll"
        )
        val encodedFromJs = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(pbeComp, jsCatalog!!)
        assertTrue(encodedFromJs.isSuccess)
        assertEquals("024b04b14b24b34b44b54b6000000000TFTSet19", encodedFromJs.code)

        // 3. End-to-end scraper + site-first priority: pulls exact code from `/tierlist/comps/set19/__data.json`
        //    and preserves each comp's dedicated guide sourceUrl in enforceSetAlignment
        val apiPayload = """
            {
              "guides": [
                {
                  "title": "Hyperpop Reroll",
                  "compSlug": "set19-hyperpop-reroll",
                  "tier": "S",
                  "style": "1-Cost Reroll",
                  "set": 19,
                  "isPublic": true,
                  "finalComp": [
                    {"apiName": "TFT19_Kobuko", "boardIndex": 2},
                    {"apiName": "TFT19_Ziggs", "boardIndex": 21},
                    {"apiName": "TFT19_Lulu", "boardIndex": 22},
                    {"apiName": "TFT19_Tristana", "boardIndex": 23},
                    {"apiName": "TFT19_Seraphine_Indie", "boardIndex": 24},
                    {"apiName": "TFT19_Swain", "boardIndex": 3},
                    {"apiName": "TFT19_Teemo", "boardIndex": 25}
                  ]
                }
              ]
            }
        """.trimIndent()
        val envelope = org.json.JSONObject().apply {
            put("status", 200)
            put("body", apiPayload)
        }.toString()
        val pageHtml = """
            <html>
              <head>
                <link rel="modulepreload" href="/_app/immutable/chunks/planner.abc123.js">
              </head>
              <body>
                <h1>Set 19 Tier List</h1>
                <script type="application/json" data-sveltekit-fetched data-url="/api/tierlist/comps?set=19">$envelope</script>
              </body>
            </html>
        """.trimIndent()
        val siteClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val (code, bodyStr) = when {
                    url.endsWith("/tierlist/comps/set19/__data.json") -> 200 to devalueDataJson
                    url.endsWith("/tierlist/comps/set19") -> 200 to pageHtml
                    url.contains("planner.abc123.js") -> 200 to minifiedJsChunk
                    else -> 404 to "{}"
                }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Not Found")
                    .body(okhttp3.ResponseBody.create(null, bodyStr))
                    .build()
            }
            .build()

        val scraper = TftAcademyCompsScraper(siteClient)
        val fetched = scraper.fetchPbeTeamComps("19.1-PBE", defaultPbeSetNumber = 19)
        assertEquals(1, fetched.pbeComps.size)
        val scrapedHyperpop = fetched.pbeComps.first()
        assertEquals("024b04b14b24b34b44b54b6000000000TFTSet19", scrapedHyperpop.teamPlannerCode)
        assertEquals(
            "https://tftacademy.com/tierlist/comps/set19/set19-hyperpop-reroll",
            scrapedHyperpop.sourceUrl
        )
    }

    @Test
    fun `set 19 pbe team planner dynamically follows riot live pbe set key and re-encodes after game updates`() {
        val rawSet19FromSite = "0244442741043142d416434000000000Set19"
        val canonicalTftSet19 = "0244442741043142d416434000000000TFTSet19"
        val legacyPbeNone = "0244442741043142d416434000000000None"
        val liveSet18Code = "02010429fff00702a3f7000000000000TFTSet18"

        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }

        // 1. Validator accepts Set19, TFTSet19, and None for Set 19, rejects None for Set 18 LIVE
        assertTrue(com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(rawSet19FromSite, expectedSetNumber = 19))
        assertTrue(com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(canonicalTftSet19, expectedSetNumber = 19))
        assertTrue(com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(legacyPbeNone, expectedSetNumber = 19))
        org.junit.Assert.assertFalse(
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(legacyPbeNone, expectedSetNumber = 18)
        )

        // 2. Without cached Riot key, bare Set19 shorthand upgrades to TFTSet19 while explicit suffixes & LIVE Set 18 stay intact
        assertEquals(
            canonicalTftSet19,
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.normalizeCodeForTarget(
                rawCode = rawSet19FromSite,
                environment = TftEnvironment.PBE,
                targetSetNum = 19
            )
        )
        assertEquals(
            legacyPbeNone,
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.normalizeCodeForTarget(
                rawCode = legacyPbeNone,
                environment = TftEnvironment.PBE,
                targetSetNum = 19
            )
        )
        assertEquals(
            liveSet18Code,
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.normalizeCodeForTarget(
                rawCode = liveSet18Code,
                environment = TftEnvironment.LIVE,
                targetSetNum = 18
            )
        )

        val basePbeComp = MetaComp(
            id = "pbe_set19_dynamic_update_test",
            title = "Hyperpop Reroll",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            setName = "Set 19 (PBE)",
            patchTag = "19.1-PBE",
            playstyle = "1-Cost Reroll",
            avgPlacement = 3.45,
            top4Rate = 62.0,
            winRate = 18.0,
            pickRate = 4.2,
            trendDelta = "+0.3",
            isTrending = true,
            carryName = "Ziggs",
            coreUnits = listOf("Kobuko", "Ziggs", "Lulu", "Tristana", "Seraphine", "Swain", "Teemo"),
            traits = listOf("5 Hyperpop"),
            hexPlacements = emptyList(),
            augmentPriorities = emptyList(),
            levelingGuide = "Slow roll at 5",
            notes = "",
            teamPlannerCode = legacyPbeNone
        )

        // 3. When Riot PBE game build uses "None" key, resolver dynamically emits "...None"
        val pbeNoneBuildJson = """
            {
              "None": [
                {"character_id": "TFT19_Kobuko", "display_name": "Kobuko", "team_planner_code": 1092},
                {"character_id": "TFT19_Ziggs", "display_name": "Ziggs", "team_planner_code": 1063},
                {"character_id": "TFT19_Lulu", "display_name": "Lulu", "team_planner_code": 1040},
                {"character_id": "TFT19_Tristana", "display_name": "Tristana", "team_planner_code": 1073},
                {"character_id": "TFT19_Seraphine_Indie", "display_name": "Seraphine", "team_planner_code": 1069},
                {"character_id": "TFT19_Swain", "display_name": "Swain", "team_planner_code": 1046},
                {"character_id": "TFT19_Teemo", "display_name": "Teemo", "team_planner_code": 1076}
              ]
            }
        """.trimIndent()
        val pbeNoneClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, pbeNoneBuildJson))
                    .build()
            }
            .build()
        val resolvedUnderNoneBuild = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(
                context,
                basePbeComp.copy(teamPlannerCode = rawSet19FromSite),
                pbeNoneClient
            )
        }
        assertTrue(resolvedUnderNoneBuild.isSuccess)
        assertEquals(legacyPbeNone, resolvedUnderNoneBuild.code)
        assertEquals("None", resolvedUnderNoneBuild.setKey)

        // 4. After Riot updates PBE from "None" -> "TFTSet19" (same hex IDs), stale cached "...None" code automatically upgrades to "...TFTSet19"
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }
        val pbeUpdatedKeyJson = pbeNoneBuildJson.replace("\"None\"", "\"TFTSet19\"")
        val pbeUpdatedKeyClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, pbeUpdatedKeyJson))
                    .build()
            }
            .build()
        val resolvedAfterKeyUpdate = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(
                context,
                basePbeComp.copy(teamPlannerCode = legacyPbeNone),
                pbeUpdatedKeyClient
            )
        }
        assertTrue(resolvedAfterKeyUpdate.isSuccess)
        assertEquals(canonicalTftSet19, resolvedAfterKeyUpdate.code)
        assertEquals("TFTSet19", resolvedAfterKeyUpdate.setKey)

        // 5. After Riot pushes a PBE patch that renumbers champion hex IDs, stale pre-patch hex slots are detected and re-encoded fresh!
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }
        val pbeRenumberedIdsJson = """
            {
              "TFTSet19": [
                {"character_id": "TFT19_Ziggs", "display_name": "Ziggs", "team_planner_code": 1536},
                {"character_id": "TFT19_Kobuko", "display_name": "Kobuko", "team_planner_code": 1537},
                {"character_id": "TFT19_Lulu", "display_name": "Lulu", "team_planner_code": 1538},
                {"character_id": "TFT19_Tristana", "display_name": "Tristana", "team_planner_code": 1539},
                {"character_id": "TFT19_Seraphine_Indie", "display_name": "Seraphine", "team_planner_code": 1540},
                {"character_id": "TFT19_Swain", "display_name": "Swain", "team_planner_code": 1541},
                {"character_id": "TFT19_Teemo", "display_name": "Teemo", "team_planner_code": 1542}
              ]
            }
        """.trimIndent()
        val pbeRenumberedClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, pbeRenumberedIdsJson))
                    .build()
            }
            .build()
        val resolvedAfterIdRenumber = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(
                context,
                basePbeComp.copy(teamPlannerCode = canonicalTftSet19),
                pbeRenumberedClient
            )
        }
        assertTrue(resolvedAfterIdRenumber.isSuccess)
        // 1536=600, 1537=601, 1538=602, 1539=603, 1540=604, 1541=605, 1542=606
        assertEquals("02600601602603604605606000000000TFTSet19", resolvedAfterIdRenumber.code)
        assertEquals("TFTSet19", resolvedAfterIdRenumber.setKey)

        // 6. Offline fallback preserves valid direct code gracefully when CDragon is unreachable
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }
        val offlineClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { throw java.io.IOException("Offline") }
            .build()
        val resolvedOffline = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(
                context,
                basePbeComp.copy(teamPlannerCode = rawSet19FromSite),
                offlineClient
            )
        }
        assertTrue(resolvedOffline.isSuccess)
        assertEquals(canonicalTftSet19, resolvedOffline.code)
        assertEquals("TFTSet19", resolvedOffline.setKey)
    }

    @Test
    fun `mainViewModel activeEnvSnapshot switches atomically between LIVE and PBE without state tearing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = MainViewModel(
            currentUserId = null,
            metaRepository = MetaRepository(context),
            localStore = LocalPreferencesStore(context),
            firestoreRepo = FirestoreUserRepository(context),
            autoSyncOnInit = false,
            appContext = null
        )

        val liveSnap = viewModel.activeEnvSnapshot.value
        assertEquals(TftEnvironment.LIVE, liveSnap.environment)
        assertTrue(liveSnap.comps.all { it.environment == TftEnvironment.LIVE })

        viewModel.setEnvironment(TftEnvironment.PBE)
        val pbeSnap = viewModel.activeEnvSnapshot.value
        assertEquals(TftEnvironment.PBE, pbeSnap.environment)
        assertTrue(pbeSnap.comps.all { it.environment == TftEnvironment.PBE })
        assertTrue(pbeSnap.patches.all { it.environment == TftEnvironment.PBE })

        viewModel.setEnvironment(TftEnvironment.LIVE)
        val backToLive = viewModel.activeEnvSnapshot.value
        assertEquals(TftEnvironment.LIVE, backToLive.environment)
        assertTrue(backToLive.comps.all { it.environment == TftEnvironment.LIVE })
    }

    @Test
    fun `tfts helper rebrand split update button labels and riot legal disclaimer strings are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("TFT's Helper", context.getString(R.string.app_name))
        assertEquals("Check for Comp Updates", context.getString(R.string.check_for_comp_updates))
        assertEquals("Check for App Updates", context.getString(R.string.check_for_app_updates))
        val disclaimer = context.getString(R.string.riot_legal_disclaimer)
        assertTrue(disclaimer.contains("isn't endorsed by Riot Games"))
        assertTrue(disclaimer.contains("Riot Games, Inc."))
    }

    @Test
    fun `overlay bauble stays compact inside accessible touch target and transparency slider clamps and persists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = LocalPreferencesStore(context)
        kotlinx.coroutines.runBlocking { prefs.clearAllForTests() }

        // 1. Visual bauble size is 48dp (+30% from 37dp) inside a 58dp outer touch target (>= 48dp accessibility minimum)
        assertEquals(58, com.example.overlay.OVERLAY_BAUBLE_TOUCH_TARGET_DP)
        assertEquals(48, com.example.overlay.OVERLAY_BAUBLE_VISUAL_SIZE_DP)
        assertTrue(com.example.overlay.OVERLAY_BAUBLE_TOUCH_TARGET_DP >= 48)
        assertTrue(com.example.overlay.OVERLAY_BAUBLE_VISUAL_SIZE_DP < com.example.overlay.OVERLAY_BAUBLE_TOUCH_TARGET_DP)

        // 2. Opacity clamping enforces safe 25%..100% bounds and handles NaN
        assertEquals(com.example.overlay.MIN_OVERLAY_OPACITY, com.example.overlay.clampOverlayOpacity(0.05f), 0.0001f)
        assertEquals(com.example.overlay.MAX_OVERLAY_OPACITY, com.example.overlay.clampOverlayOpacity(1.45f), 0.0001f)
        assertEquals(com.example.overlay.DEFAULT_OVERLAY_OPACITY, com.example.overlay.clampOverlayOpacity(Float.NaN), 0.0001f)
        assertEquals(0.62f, com.example.overlay.clampOverlayOpacity(0.62f), 0.0001f)

        // 3. Bauble alpha gently dims when idle yet stays visible (>= 0.60f), and snaps to 1.0f when interactive
        assertEquals(com.example.overlay.MIN_BAUBLE_IDLE_ALPHA, com.example.overlay.resolveBaubleAlpha(0.25f, isInteractive = false), 0.0001f)
        assertEquals(1.0f, com.example.overlay.resolveBaubleAlpha(1.0f, isInteractive = false), 0.0001f)
        assertEquals(1.0f, com.example.overlay.resolveBaubleAlpha(0.25f, isInteractive = true), 0.0001f)

        // 4. LocalPreferencesStore persists and clamps overlay opacity synchronously
        assertEquals(LocalPreferencesStore.DEFAULT_OVERLAY_OPACITY, prefs.readOverlayOpacity(), 0.0001f)
        prefs.saveOverlayOpacity(0.55f)
        assertEquals(0.55f, prefs.readOverlayOpacity(), 0.0001f)
        prefs.saveOverlayOpacity(0.01f)
        assertEquals(LocalPreferencesStore.MIN_OVERLAY_OPACITY, prefs.readOverlayOpacity(), 0.0001f)

        // 5. OverlaySessionState toggles and resets opacity control bar visibility
        val session = com.example.overlay.OverlaySessionState()
        org.junit.Assert.assertFalse(session.isOpacityControlOpen)
        session.toggleOpacityControl()
        assertTrue(session.isOpacityControlOpen)
        session.reset()
        org.junit.Assert.assertFalse(session.isOpacityControlOpen)

        // 6. Opacity string resources exist
        assertEquals("Opacity", context.getString(R.string.overlay_opacity_label))
    }

    @Test
    fun `auto app update runs on open while comp updates stay manual and app complies with google play policies`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.update.AppUpdateCoordinator.resetAutoCheckThrottleForTests()

        // 1. MainViewModel defaults: App update auto-checks on open, Comp sync requires manual press
        val viewModel = MainViewModel(
            currentUserId = null,
            metaRepository = MetaRepository(context),
            localStore = LocalPreferencesStore(context),
            firestoreRepo = FirestoreUserRepository(context),
            appContext = null
        )
        assertFalse(viewModel.autoSyncCompsOnInitEnabled)
        assertTrue(viewModel.autoCheckAppUpdateOnInitEnabled)
        assertFalse(viewModel.isRefreshingVersions.value)

        // 2. AndroidManifest declares REQUEST_INSTALL_PACKAGES for requested in-app GitHub APK updates
        //    while never requesting broad external storage permissions
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val requestedPerms = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertTrue(requestedPerms.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES))
        assertFalse(requestedPerms.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(requestedPerms.contains(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))

        // 3. Play-compliant update intent uses ACTION_VIEW to market:// or https:// (never APK installer MIME type)
        val marketIntent = com.example.update.AppUpdateCoordinator.buildPlayStoreUpdateIntent(
            context = context,
            release = null,
            preferWebFallback = false
        )
        assertEquals(android.content.Intent.ACTION_VIEW, marketIntent.action)
        assertEquals("market://details?id=${context.packageName}", marketIntent.dataString)
        assertNotEquals("application/vnd.android.package-archive", marketIntent.type)

        val webIntent = com.example.update.AppUpdateCoordinator.buildPlayStoreUpdateIntent(
            context = context,
            release = null,
            preferWebFallback = true
        )
        assertEquals(android.content.Intent.ACTION_VIEW, webIntent.action)
        assertEquals("https://play.google.com/store/apps/details?id=${context.packageName}", webIntent.dataString)

        val githubLatestIntent = com.example.update.AppUpdateCoordinator.buildGitHubReleaseIntent(
            release = null,
            configuredRepo = "JSGamingggg/Tactician-s-Forge"
        )
        assertEquals(android.content.Intent.ACTION_VIEW, githubLatestIntent.action)
        assertEquals("https://github.com/JSGamingggg/Tactician-s-Forge/releases/latest", githubLatestIntent.dataString)
        assertNotEquals("application/vnd.android.package-archive", githubLatestIntent.type)

        val sampleRelease = com.example.update.GitHubReleaseInfo(
            releaseId = 1L,
            tagName = "v2.0.0",
            releaseTitle = "v2.0.0",
            body = "Release notes",
            htmlUrl = "https://github.com/JSGamingggg/Tactician-s-Forge/releases/tag/v2.0.0",
            publishedAtIso = "",
            publishedAtMs = 0L,
            parsedVersionCode = 200,
            parsedVersionName = "2.0.0",
            parsedCommitSha = null,
            isPrerelease = false,
            apkAssetName = "app-release.apk",
            apkAssetId = 10L,
            apkDownloadUrl = "https://github.com/JSGamingggg/Tactician-s-Forge/releases/download/v2.0.0/app-release.apk",
            apkSizeBytes = 1024L,
            assetUpdatedAtMs = 0L
        )
        val githubTagIntent = com.example.update.AppUpdateCoordinator.buildGitHubReleaseIntent(
            release = sampleRelease,
            configuredRepo = "JSGamingggg/Tactician-s-Forge"
        )
        assertEquals(android.content.Intent.ACTION_VIEW, githubTagIntent.action)
        assertEquals("https://github.com/JSGamingggg/Tactician-s-Forge/releases/tag/v2.0.0", githubTagIntent.dataString)
        assertFalse(com.example.update.AppUpdateCoordinator.isInstalledFromGooglePlay(context))

        // 4. App metadata & privacy / account deletion resources comply with store policies
        val appTitle = context.getString(R.string.app_name)
        assertTrue(appTitle.length <= 30)
        assertFalse(appTitle.uppercase() == appTitle)
        assertTrue(context.getString(R.string.privacy_data_title).isNotBlank())
        assertTrue(context.getString(R.string.privacy_policy_details).contains("Guest Mode"))
        assertTrue(context.getString(R.string.delete_account_data).contains("Delete"))
        assertTrue(context.getString(R.string.clear_local_data).contains("Clear"))

        // 5. Local data clearing removes saved user settings & favorites cleanly
        val prefs = LocalPreferencesStore(context)
        prefs.saveOverlayOpacity(0.50f)
        prefs.setGuestModeChoice(true)
        prefs.clearAllLocalData()
        assertEquals(LocalPreferencesStore.DEFAULT_OVERLAY_OPACITY, prefs.readOverlayOpacity(), 0.0001f)
    }

    @Test
    fun `tftninja scraper targets exact live and pbe urls extracts full comp details and champion recommendations`() = runBlocking {
        val requestedUrls = mutableListOf<String>()

        val liveRscHtml = """
            <html><head><title>TFT Ninja Comps</title></head><body>
            <script>self.__next_f.push([1,"1a:{\"set\":\"TFTSet18\",\"comps\":[{\"slug\":\"arcanist-ahri\",\"title\":\"6 Arcanist Ahri\",\"tier\":\"S\",\"playstyle\":\"Fast 8\",\"avgPlacement\":3.62,\"top4Rate\":64.5,\"winRate\":18.4,\"pickRate\":7.2,\"traits\":[{\"name\":\"Arcanist\",\"num_units\":6},{\"name\":\"Elderwood\",\"num_units\":4}],\"units\":[{\"name\":\"Ahri\",\"cost\":4,\"stars\":2,\"isCarry\":true,\"row\":3,\"col\":6,\"items\":[\"Blue Buff\",\"Jeweled Gauntlet\",\"Nashor's Tooth\"]},{\"name\":\"Ornn\",\"cost\":4,\"stars\":2,\"row\":0,\"col\":3,\"items\":[\"Warmog's Armor\",\"Gargoyle Stoneplate\",\"Dragon's Claw\"]},{\"name\":\"Lillia\",\"cost\":3,\"stars\":2,\"row\":0,\"col\":2,\"items\":[\"Ionic Spark\"]},{\"name\":\"Syndra\",\"cost\":3,\"stars\":2,\"row\":3,\"col\":0,\"items\":[\"Spear of Shojin\"]}],\"augments\":[\"Jeweled Lotus II\",\"Level Up!\"],\"levelingGuide\":\"Level 8 on 4-2 and roll for Ahri 2 and Ornn 2.\",\"notes\":\"Position Ahri opposite enemy assassins.\",\"teamPlannerCode\":\"023b83b93ba3bb000000000000000000TFTSet18\"}]}"])</script>
            </body></html>
        """.trimIndent()

        val pbeNextDataHtml = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {
              "props": {
                "pageProps": {
                  "set": 19,
                  "comps": [
                    {
                      "slug": "prismatic-jinx",
                      "title": "Prismatic Tour Jinx",
                      "tier": "S",
                      "playstyle": "Slow Roll",
                      "stats": { "avg": 3.48, "top4": 0.672, "win": 0.215, "pick": 0.088, "games": 4120 },
                      "traits": ["6 Popstar", "4 Sniper"],
                      "board": [
                        { "champion": "Jinx", "cost": 3, "threeStar": true, "isCarry": true, "hex": 27, "bestItems": ["Guinsoo's Rageblade", "Infinity Edge", "Last Whisper"] },
                        { "champion": "Vi", "cost": 3, "stars": 2, "hex": 3, "items": ["Warmog's Armor", "Bramble Vest"] },
                        { "champion": "Seraphine", "cost": 4, "stars": 2, "hex": 21, "items": ["Morellonomicon"] },
                        { "champion": "Ekko", "cost": 4, "stars": 2, "hex": 2 }
                      ],
                      "augments": ["Two Healthy", "Cybernetic Uplink"],
                      "earlyGame": "Play 3 Popstar opener with Guinsoo slam.",
                      "lateGame": "Slow roll at level 7 for Jinx 3-star."
                    }
                  ]
                }
              }
            }
            </script></head><body></body></html>
        """.trimIndent()

        val liveChampsJson = """
            {"champions":[{"champion":"Ahri","avg":3.60,"top4":65.0,"tier":"S","bestItems":["Blue Buff","Jeweled Gauntlet","Rabadon's Deathcap"]}]}
        """.trimIndent()

        val scraper = com.example.data.network.TftNinjaCompsScraper(
            urlFetcher = { url ->
                requestedUrls.add(url)
                when (url) {
                    "https://tft.ninja/comps" -> liveRscHtml
                    "https://tft.ninja/comps?e=pbe" -> pbeNextDataHtml
                    "https://tft.ninja/champions" -> liveChampsJson
                    else -> null
                }
            },
            rscFetcher = { null }
        )

        val liveResult = scraper.fetchLiveTeamComps(livePatchVersion = "18.8")
        val pbeResult = scraper.fetchPbeTeamComps(pbePatchVersion = "19.1-PBE")

        // 1. Exact URLs verified
        assertTrue(requestedUrls.contains("https://tft.ninja/comps"))
        assertTrue(requestedUrls.contains("https://tft.ninja/comps?e=pbe"))
        assertTrue(liveResult.fetchedFromNetwork)
        assertTrue(pbeResult.fetchedFromNetwork)

        // 2. Full LIVE composition details preserved
        assertEquals(1, liveResult.comps.size)
        val liveComp = liveResult.comps.first()
        assertEquals("6 Arcanist Ahri", liveComp.title)
        assertEquals(TierRank.S, liveComp.tier)
        assertEquals("Fast 8", liveComp.playstyle)
        assertEquals(3.62, liveComp.avgPlacement, 0.001)
        assertEquals(64.5, liveComp.top4Rate, 0.01)
        assertEquals(18.4, liveComp.winRate, 0.01)
        assertEquals(7.2, liveComp.pickRate, 0.01)
        assertEquals("Ahri", liveComp.carryName)
        assertTrue(liveComp.traits.contains("6 Arcanist"))
        assertEquals(listOf("Jeweled Lotus II", "Level Up!"), liveComp.augmentPriorities)
        assertTrue(liveComp.levelingGuide.contains("Level 8 on 4-2"))
        assertTrue(liveComp.notes.contains("opposite enemy assassins"))
        assertEquals("023b83b93ba3bb000000000000000000TFTSet18", liveComp.teamPlannerCode)
        assertTrue(liveComp.sourceUrl.startsWith("https://tft.ninja/comps"))
        val ornnPlacement = liveComp.hexPlacements.first { it.unitName == "Ornn" }
        assertEquals(listOf("Warmog's Armor", "Gargoyle Stoneplate", "Dragon's Claw"), ornnPlacement.items)

        // 3. Full PBE composition details preserved (including hex index 27 -> row 3, col 6 and fraction->% stats)
        assertEquals(1, pbeResult.comps.size)
        val pbeComp = pbeResult.comps.first()
        assertEquals("Prismatic Tour Jinx", pbeComp.title)
        assertEquals(TftEnvironment.PBE, pbeComp.environment)
        assertEquals(3.48, pbeComp.avgPlacement, 0.001)
        assertEquals(67.2, pbeComp.top4Rate, 0.01)
        assertEquals(21.5, pbeComp.winRate, 0.01)
        assertEquals(8.8, pbeComp.pickRate, 0.01)
        assertTrue(pbeComp.sourceUrl.contains("e=pbe"))
        val jinxPlacement = pbeComp.hexPlacements.first { it.unitName == "Jinx" }
        assertEquals(3, jinxPlacement.row)
        assertEquals(6, jinxPlacement.col)
        assertEquals(3, jinxPlacement.stars)
        assertEquals(listOf("Guinsoo's Rageblade", "Infinity Edge", "Last Whisper"), jinxPlacement.items)

        // 4. MetaRepository persists ONLY tft.ninja armies & updates champion recommendations
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        val offlineClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { throw java.io.IOException("No external calls in test") }
            .build()
        val offlineDdragon = retrofit2.Retrofit.Builder()
            .baseUrl("https://ddragon.leagueoflegends.com/")
            .client(offlineClient)
            .build()
            .create(com.example.data.network.DDragonApi::class.java)

        val repo = MetaRepository(
            cacheStore = cacheStore,
            tftipsScraper = TftipsPatchScraper(offlineClient),
            tftAcademyScraper = TftAcademyCompsScraper(offlineClient),
            tftNinjaScraper = scraper,
            ddragonApi = offlineDdragon,
            publicClient = offlineClient
        )
        val syncResult = repo.refreshAndPersistMeta()
        assertTrue(syncResult.syncedFromNetwork)

        val bundle = repo.loadOfflineBundle()
        assertEquals(listOf("6 Arcanist Ahri"), bundle.liveComps.map { it.title })
        assertEquals(listOf("Prismatic Tour Jinx"), bundle.pbeComps.map { it.title })
        assertTrue(bundle.liveComps.all { it.sourceUrl.contains("tft.ninja") })
        assertTrue(bundle.pbeComps.all { it.sourceUrl.contains("tft.ninja") })

        // 5. Clicking any individual champion shows tft.ninja recommended items & tft.ninja compositions only
        val ahriProfile = com.example.ui.components.resolveChampionDetailProfile(
            championName = "Ahri",
            environment = TftEnvironment.LIVE,
            champions = bundle.liveChampions,
            comps = bundle.liveComps,
            items = bundle.liveItems
        )
        val ahriItems = ahriProfile.optimalItems.map { it.itemName }
        assertTrue(ahriItems.contains("Blue Buff"))
        assertTrue(ahriItems.contains("Jeweled Gauntlet"))
        assertTrue(ahriItems.contains("Nashor's Tooth"))
        assertTrue(ahriItems.contains("Rabadon's Deathcap"))
        assertEquals(listOf("6 Arcanist Ahri"), ahriProfile.synergyComps.map { it.comp.title })
        assertTrue(ahriProfile.additionalSynergyCompNames.isEmpty())

        val ornnProfile = com.example.ui.components.resolveChampionDetailProfile(
            championName = "Ornn",
            environment = TftEnvironment.LIVE,
            champions = bundle.liveChampions,
            comps = bundle.liveComps,
            items = bundle.liveItems
        )
        assertTrue(ornnProfile.optimalItems.map { it.itemName }.containsAll(listOf("Warmog's Armor", "Gargoyle Stoneplate", "Dragon's Claw")))
        assertEquals(listOf("6 Arcanist Ahri"), ornnProfile.synergyComps.map { it.comp.title })
        assertEquals(listOf("Warmog's Armor", "Gargoyle Stoneplate", "Dragon's Claw"), ornnProfile.synergyComps.first().equippedItems)

        val jinxPbeProfile = com.example.ui.components.resolveChampionDetailProfile(
            championName = "Jinx",
            environment = TftEnvironment.PBE,
            champions = bundle.pbeChampions,
            comps = bundle.pbeComps,
            items = bundle.pbeItems
        )
        assertTrue(jinxPbeProfile.optimalItems.map { it.itemName }.containsAll(listOf("Guinsoo's Rageblade", "Infinity Edge", "Last Whisper")))
        assertEquals(listOf("Prismatic Tour Jinx"), jinxPbeProfile.synergyComps.map { it.comp.title })
        assertTrue(jinxPbeProfile.synergyComps.first().isCarry)
    }

    @Test
    fun `full board roster shows all units with comp items and other items plus level transition guide`() {
        val scraperJson = """
            {
              "comps": [
                {
                  "id": "fast9_flex",
                  "title": "Fast 9 Arcanist Cap",
                  "tier": "S",
                  "playstyle": "Fast 9",
                  "avgPlacement": 3.55,
                  "carry": "Ahri",
                  "traits": ["6 Arcanist", "3 Vanguard"],
                  "units": [
                    {"name": "Ahri", "cost": 4, "isCarry": true, "items": ["Blue Buff", "Jeweled Gauntlet", "Nashor's Tooth", "Rabadon's Deathcap"], "otherItems": ["Spear of Shojin", "Hextech Gunblade"]},
                    {"name": "Ornn", "cost": 4, "items": ["Warmog's Armor", "Gargoyle Stoneplate"], "flexItems": ["Sunfire Cape", "Dragon's Claw"]},
                    {"name": "Lillia", "cost": 3},
                    {"name": "Syndra", "cost": 3},
                    {"name": "Neeko", "cost": 2},
                    {"name": "Zoe", "cost": 2},
                    {"name": "Lux", "cost": 1},
                    {"name": "Poppy", "cost": 1}
                  ],
                  "flexUnits": ["Azir", "Lissandra", "Udyr"],
                  "earlyBoard": ["Lux", "Poppy", "Neeko", "Zoe"],
                  "earlyGame": "Play 2 Vanguard 2 Arcanist and hold AP items on Lux.",
                  "midBoard": ["Lux", "Neeko", "Zoe", "Lillia", "Syndra", "Ornn"],
                  "midGame": "Level to 7 on 3-5 and stabilize frontline while holding 50g.",
                  "lateGame": "Push Level 9 on 5-1, move items onto Ahri, and slot Azir & Lissandra."
                }
              ]
            }
        """.trimIndent()

        val scraper = com.example.data.network.TftNinjaCompsScraper(
            urlFetcher = { url -> if (url == "https://tft.ninja/comps") scraperJson else null },
            rscFetcher = { null }
        )
        val result = scraper.fetchLiveTeamComps(livePatchVersion = "18.8")
        assertEquals(1, result.comps.size)
        val comp = result.comps.first()

        // All 11 champions (8 primary + 3 flex) preserved without truncation
        assertEquals(11, comp.coreUnits.size)
        assertTrue(comp.coreUnits.containsAll(listOf("Ahri", "Ornn", "Azir", "Lissandra", "Udyr")))

        // Explicit level guide stages parsed from website
        val parsedStages = com.example.ui.components.resolveCompLevelGuideStages(comp)
        assertEquals(3, parsedStages.size)
        assertTrue(parsedStages[0].units.containsAll(listOf("Lux", "Poppy", "Neeko", "Zoe")))
        assertTrue(parsedStages[0].actionTip.contains("hold AP items on Lux"))
        assertTrue(parsedStages[2].actionTip.contains("Push Level 9"))

        // Full board roster resolves all 11 champions + Comp Items + Other Items (without duplicating primary items)
        val catalogChamps = listOf(
            com.example.data.model.ChampionTierEntry(
                id = "live_syndra",
                name = "Syndra",
                cost = 3,
                tier = TierRank.A,
                environment = TftEnvironment.LIVE,
                traits = listOf("Arcanist"),
                role = "Carry",
                avgPlacement = 4.1,
                top4Rate = 55.0,
                bestItems = listOf("Spear of Shojin", "Archangel's Staff"),
                synergyComps = listOf("Fast 9 Arcanist Cap")
            )
        )
        val roster = com.example.ui.components.resolveFullBoardRosterUnits(
            comp = comp,
            champions = catalogChamps,
            allComps = listOf(comp)
        )
        assertEquals(11, roster.size)
        val ahriRow = roster.first { it.unitName == "Ahri" }
        assertEquals(listOf("Blue Buff", "Jeweled Gauntlet", "Nashor's Tooth"), ahriRow.items)
        assertTrue(ahriRow.otherItems.contains("Rabadon's Deathcap"))
        assertTrue(ahriRow.otherItems.contains("Spear of Shojin"))
        assertTrue(ahriRow.otherItems.none { it in ahriRow.items })

        val ornnRow = roster.first { it.unitName == "Ornn" }
        assertEquals(listOf("Warmog's Armor", "Gargoyle Stoneplate"), ornnRow.items)
        assertTrue(ornnRow.otherItems.containsAll(listOf("Sunfire Cape", "Dragon's Claw")))

        val syndraRow = roster.first { it.unitName == "Syndra" }
        assertTrue(syndraRow.items.isEmpty())
        assertTrue(syndraRow.otherItems.isEmpty())

        // Fallback level guide derivation when website omits explicit stage boards
        val compWithoutExplicitStages = comp.copy(levelGuideStages = emptyList(), playstyle = "Fast 8")
        val derivedStages = com.example.ui.components.resolveCompLevelGuideStages(compWithoutExplicitStages)
        assertEquals(3, derivedStages.size)
        assertTrue(derivedStages.all { it.units.isNotEmpty() && it.actionTip.isNotBlank() })
        assertTrue(derivedStages.last().levelLabel.contains("Transition"))
    }

    @Test
    fun `html dom scraper never adds items as individual units and attaches items under carries only`() {
        val htmlWithNextJsItemImages = """
            <html><body>
              <div class="comp-card">
                <a href="/comps/arcanist-ahri"><h3>6 Arcanist Ahri</h3></a>
                <span>S Tier Avg: 3.72 Top 4: 64.5% Win: 18.2%</span>
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Ftraits%2FArcanist.png&w=32&q=75" alt="Arcanist" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Ahri.png&w=64&q=75" alt="Ahri" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fitems%2FTFT18_Item_BlueBuff.png&w=32&q=75" alt="Blue Buff" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fitems%2FTFT18_Item_JeweledGauntlet.png&w=32&q=75" alt="Jeweled Gauntlet" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fitems%2FGuinsoosRageblade.png&w=32&q=75" alt="Guinsoo's Rageblade" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Ornn.png&w=64&q=75" alt="Ornn" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fitems%2FWarmogsArmor.png&w=32&q=75" alt="Warmog's Armor" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Lillia.png&w=64&q=75" alt="Lillia" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Syndra.png&w=64&q=75" alt="Syndra" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Neeko.png&w=64&q=75" alt="Neeko" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Zoe.png&w=64&q=75" alt="Zoe" />
              </div>
            </body></html>
        """.trimIndent()

        val scraper = com.example.data.network.TftNinjaCompsScraper(
            urlFetcher = { url -> if (url == "https://tft.ninja/comps") htmlWithNextJsItemImages else null },
            rscFetcher = { null }
        )
        val result = scraper.fetchLiveTeamComps(livePatchVersion = "18.8")
        assertEquals(1, result.comps.size)
        val comp = result.comps.first()

        // Only the 6 real champions appear as units — ZERO items or traits leaked into coreUnits
        assertEquals(listOf("Ahri", "Ornn", "Lillia", "Syndra", "Neeko", "Zoe"), comp.coreUnits)
        assertTrue(comp.coreUnits.none { it.contains("Blue Buff") || it.contains("Rageblade") || it.contains("Warmog") || it.contains("Gauntlet") })

        // Items are attached strictly to their owning carry champions (Ahri and Ornn), NOT non-carries
        val ahri = comp.hexPlacements.first { it.unitName == "Ahri" }
        assertTrue(ahri.isCarry)
        assertEquals(listOf("Blue Buff", "Jeweled Gauntlet", "Guinsoo's Rageblade"), ahri.items)

        val ornn = comp.hexPlacements.first { it.unitName == "Ornn" }
        assertEquals(listOf("Warmog's Armor"), ornn.items)

        val nonCarries = comp.hexPlacements.filter { it.unitName in listOf("Lillia", "Syndra", "Neeko", "Zoe") }
        assertEquals(4, nonCarries.size)
        assertTrue(nonCarries.all { !it.isCarry && it.items.isEmpty() && it.otherItems.isEmpty() })

        // Defensive UI filtering also strips any legacy cached item names out of army card unit lists
        val dirtyComp = comp.copy(
            coreUnits = comp.coreUnits + listOf("Guinsoo's Rageblade", "TFT_Item_WarmogsArmor", "Arcanist Emblem")
        )
        val sortedCleanUnits = com.example.ui.components.sortCompUnitsByCostAscending(dirtyComp)
        assertEquals(6, sortedCleanUnits.size)
        assertTrue(sortedCleanUnits.none { it.contains("Rageblade") || it.contains("Warmog") || it.contains("Emblem") })
    }

    @Test
    fun `item and augment labels and icons resolve accurately for riot codenames across live and pbe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        val bundle = cacheStore.readOrSeedBundle()

        // 1. Riot internal item codenames must map to official modern TFT labels (never swapped RedBuff/Sunfire!)
        assertEquals("Sunfire Cape", com.example.ui.components.formatRiotAssetLabel("TFT_Item_RedBuff"))
        assertEquals("Red Buff", com.example.ui.components.formatRiotAssetLabel("TFT_Item_RapidFireCannon"))
        assertEquals("Nashor's Tooth", com.example.ui.components.formatRiotAssetLabel("TFT_Item_Leviathan"))
        assertEquals("Nashor's Tooth", com.example.ui.components.formatRiotAssetLabel("Leviathan"))
        assertEquals("Edge of Night", com.example.ui.components.formatRiotAssetLabel("TFT_Item_GuardianAngel"))
        assertEquals("Hand of Justice", com.example.ui.components.formatRiotAssetLabel("TFT_Item_UnstableConcoction"))
        assertEquals("Protector's Vow", com.example.ui.components.formatRiotAssetLabel("TFT_Item_FrozenHeart"))
        assertEquals("Steadfast Heart", com.example.ui.components.formatRiotAssetLabel("TFT_Item_NightHarvester"))
        assertEquals("Giant Slayer", com.example.ui.components.formatRiotAssetLabel("TFT_Item_MadredsBloodrazor"))
        assertEquals("Evenshroud", com.example.ui.components.formatRiotAssetLabel("TFT_Item_SpectralGauntlet"))
        assertEquals("Striker's Flail", com.example.ui.components.formatRiotAssetLabel("TFT_Item_PowerGauntlet"))
        assertEquals("Striker's Flail", com.example.ui.components.formatRiotAssetLabel("Guardbreaker"))
        assertEquals("Void Staff", com.example.ui.components.formatRiotAssetLabel("TFT_Item_StatikkShiv"))
        assertEquals("Spirit Visage", com.example.ui.components.formatRiotAssetLabel("TFT_Item_Redemption"))
        assertEquals("Kraken's Fury", com.example.ui.components.formatRiotAssetLabel("TFT_Item_RunaansHurricane"))
        assertEquals("B.F. Sword", com.example.ui.components.formatRiotAssetLabel("TFT_Item_BFSword"))
        assertEquals("B.F. Sword", com.example.ui.components.formatRiotAssetLabel("BFSword"))
        assertEquals("Tactician's Cape", com.example.ui.components.formatRiotAssetLabel("TFT_Item_TacticiansCape"))
        assertEquals("Tactician's Shield", com.example.ui.components.formatRiotAssetLabel("TFT_Item_TacticiansShield"))

        // 2. Preloaded LIVE and PBE items contain zero raw unformatted TFT_Item_ names
        assertTrue(bundle.liveItems.none { it.name.startsWith("TFT_Item_", ignoreCase = true) })
        assertTrue(bundle.pbeItems.none { it.name.startsWith("TFT_Item_", ignoreCase = true) })

        // 3. Sunfire Cape vs Red Buff and Tactician's Cape vs Shield resolve distinct accurate icon URLs
        val sunfireUrl = com.example.ui.components.TftAssetIconResolver.resolveItemIconUrl("TFT_Item_RedBuff", TftEnvironment.LIVE)
        val redBuffUrl = com.example.ui.components.TftAssetIconResolver.resolveItemIconUrl("TFT_Item_RapidFireCannon", TftEnvironment.LIVE)
        val capeUrl = com.example.ui.components.TftAssetIconResolver.resolveItemIconUrl("TFT_Item_TacticiansCape", TftEnvironment.LIVE)
        val shieldUrl = com.example.ui.components.TftAssetIconResolver.resolveItemIconUrl("TFT_Item_TacticiansShield", TftEnvironment.LIVE)
        assertNotNull(sunfireUrl)
        assertNotNull(redBuffUrl)
        assertNotNull(capeUrl)
        assertNotNull(shieldUrl)
        assertTrue("Sunfire Cape and Red Buff must use distinct icons", sunfireUrl != redBuffUrl)
        assertTrue("Tactician's Cape and Tactician's Shield must use distinct icons", capeUrl != shieldUrl)
        assertTrue(
            "Tactician's Cape should resolve to Cape/Ring icon (got $capeUrl)",
            capeUrl!!.contains("TacticiansRing", ignoreCase = true) || capeUrl.contains("TacticiansCape", ignoreCase = true)
        )
        assertTrue(
            "Tactician's Shield should resolve to Shield/Scepter icon (got $shieldUrl)",
            shieldUrl!!.contains("TacticiansScepter", ignoreCase = true) || shieldUrl.contains("TacticiansShield", ignoreCase = true)
        )

        // 4. Augment icons resolve accurately for LIVE and PBE (including + / tier suffixes & DA_ ids)
        val wandOverflowIcon = com.example.ui.components.TftAssetIconResolver.resolveAugmentIconUrl("Wand Overflow")
        val advancedLoanPlusIcon = com.example.ui.components.TftAssetIconResolver.resolveAugmentIconUrl("Advanced Loan+")
        assertNotNull(wandOverflowIcon)
        assertNotNull(advancedLoanPlusIcon)

        // 5. HTML DOM scraper extracts augments cleanly into augmentPriorities and maps TFT_Item_RedBuff -> Sunfire Cape
        val domWithAugments = """
            <html><body>
              <div class="comp-card">
                <a href="/comps/elderwood-ahri"><h3>Elderwood Ahri</h3></a>
                <span>S Tier Avg: 3.80</span>
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Ahri.png" alt="Ahri" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fitems%2FTFT_Item_RedBuff.png" alt="TFT_Item_RedBuff" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fitems%2FTFT_Item_RapidFireCannon.png" alt="TFT_Item_RapidFireCannon" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Ornn.png" alt="Ornn" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Fchampions%2FTFT18_Lillia.png" alt="Lillia" />
                <img src="/_next/image?url=https%3A%2F%2Fcdn.tft.ninja%2Faugments%2FDA_WandOverflow.png" alt="Wand Overflow" />
              </div>
            </body></html>
        """.trimIndent()
        val scraper = com.example.data.network.TftNinjaCompsScraper(
            urlFetcher = { url -> if (url == "https://tft.ninja/comps") domWithAugments else null },
            rscFetcher = { null }
        )
        val scraped = scraper.fetchLiveTeamComps("18.8").comps.first()
        assertEquals(listOf("Ahri", "Ornn", "Lillia"), scraped.coreUnits)
        assertEquals(listOf("Sunfire Cape", "Red Buff"), scraped.hexPlacements.first { it.unitName == "Ahri" }.items)
        assertEquals(listOf("Wand Overflow"), scraped.augmentPriorities)
    }

    @Test
    fun `copy code resolves valid v02 team planner codes for both LIVE and PBE tft ninja comps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }

        val liveCDragonJson = """
            {
              "TFTSet18": [
                {"character_id": "DA_18_Ahri", "display_name": "Ahri", "team_planner_code": 1001},
                {"character_id": "DA_18_Ornn", "display_name": "Ornn", "team_planner_code": 1002},
                {"character_id": "DA_18_Lillia", "display_name": "Lillia", "team_planner_code": 1003},
                {"character_id": "DA_18_Syndra", "display_name": "Syndra", "team_planner_code": 1004}
              ]
            }
        """.trimIndent()
        val pbeCDragonJson = """
            {
              "TFTSet19": [
                {"character_id": "TFT19_Ziggs", "display_name": "Ziggs", "team_planner_code": 1200},
                {"character_id": "TFT19_Kobuko", "display_name": "Kobuko", "team_planner_code": 1201},
                {"character_id": "TFT19_Lulu", "display_name": "Lulu", "team_planner_code": 1202},
                {"character_id": "TFT19_Seraphine_Indie", "display_name": "Seraphine", "team_planner_code": 1203}
              ]
            }
        """.trimIndent()

        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val bodyStr = when {
                    url.contains("/pbe/") && url.contains("tftchampions-teamplanner.json") -> pbeCDragonJson
                    url.contains("/latest/") && url.contains("tftchampions-teamplanner.json") -> liveCDragonJson
                    else -> """{}"""
                }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, bodyStr))
                    .build()
            }
            .build()

        val liveNinjaComp = MetaComp(
            id = "tftninja_live_arcanist_ahri",
            title = "Arcanist Ahri",
            tier = TierRank.S,
            environment = TftEnvironment.LIVE,
            setName = "Set 18",
            patchTag = "18.8",
            playstyle = "Fast 8",
            avgPlacement = 3.75,
            top4Rate = 64.0,
            winRate = 18.0,
            pickRate = 5.0,
            trendDelta = "+0.2",
            isTrending = true,
            carryName = "Ahri",
            coreUnits = listOf("Ahri", "Ornn", "Lillia", "Syndra"),
            traits = listOf("4 Arcanist"),
            hexPlacements = emptyList(),
            augmentPriorities = emptyList(),
            levelingGuide = "Fast 8",
            notes = "",
            sourceUrl = "https://tft.ninja/comps/arcanist-ahri"
        )
        val pbeNinjaComp = MetaComp(
            id = "tftninja_pbe_hyperpop_ziggs",
            title = "Hyperpop Ziggs",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            setName = "Set 19",
            patchTag = "19.1-PBE",
            playstyle = "Slow Roll",
            avgPlacement = 3.60,
            top4Rate = 66.0,
            winRate = 20.0,
            pickRate = 4.5,
            trendDelta = "+0.4",
            isTrending = true,
            carryName = "Ziggs",
            coreUnits = listOf("Ziggs", "Kobuko", "Lulu", "Seraphine"),
            traits = listOf("4 Hyperpop"),
            hexPlacements = emptyList(),
            augmentPriorities = emptyList(),
            levelingGuide = "Slow Roll",
            notes = "",
            sourceUrl = "https://tft.ninja/comps/hyperpop-ziggs?e=pbe"
        )

        val liveResolved = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, liveNinjaComp, client)
        }
        assertTrue("LIVE tft.ninja comp should resolve v02 code", liveResolved.isSuccess)
        assertEquals("TFTSet18", liveResolved.setKey)
        assertTrue(liveResolved.code.startsWith("02") && liveResolved.code.endsWith("TFTSet18"))
        assertEquals(4, liveResolved.matchedChampions.size)

        val pbeResolved = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, pbeNinjaComp, client)
        }
        assertTrue("PBE tft.ninja comp should resolve v02 code", pbeResolved.isSuccess)
        assertEquals("TFTSet19", pbeResolved.setKey)
        assertTrue(pbeResolved.code.startsWith("02") && pbeResolved.code.endsWith("TFTSet19"))
        assertEquals(4, pbeResolved.matchedChampions.size)

        // Batch enrichment attaches verified v02 codes to BOTH LIVE and PBE tft.ninja comps
        val enrichedLive = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.enrichCompsWithTeamCodes(
                context = context,
                comps = listOf(liveNinjaComp),
                environment = TftEnvironment.LIVE,
                client = client
            )
        }
        val enrichedPbe = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.enrichCompsWithTeamCodes(
                context = context,
                comps = listOf(pbeNinjaComp),
                environment = TftEnvironment.PBE,
                client = client
            )
        }
        assertEquals(liveResolved.code, enrichedLive.first().teamPlannerCode)
        assertEquals(pbeResolved.code, enrichedPbe.first().teamPlannerCode)
    }

    @Test
    fun `in-app github updater validates allowed https hosts confines apk cache and builds fileprovider install intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Strict HTTPS GitHub download host allowlist
        assertTrue(
            com.example.update.AppUpdateCoordinator.isAllowedGitHubDownloadUrl(
                "https://github.com/JSGamingggg/Tactician-s-Forge/releases/download/v2.0.0/app-release.apk"
            )
        )
        assertTrue(
            com.example.update.AppUpdateCoordinator.isAllowedGitHubDownloadUrl(
                "https://objects.githubusercontent.com/github-production-release-asset-2e65be/release.apk"
            )
        )
        assertFalse(
            com.example.update.AppUpdateCoordinator.isAllowedGitHubDownloadUrl(
                "http://github.com/JSGamingggg/Tactician-s-Forge/releases/download/v2.0.0/app-release.apk"
            )
        )
        assertFalse(
            com.example.update.AppUpdateCoordinator.isAllowedGitHubDownloadUrl(
                "https://evil.example.com/app-release.apk"
            )
        )
        assertFalse(
            com.example.update.AppUpdateCoordinator.isAllowedGitHubDownloadUrl(
                "https://127.0.0.1/app-release.apk"
            )
        )

        // 2. Confined cache file path blocks path traversal
        val safeApk = com.example.update.AppUpdateCoordinator.resolveSafeUpdateApkFile(
            context = context,
            rawFileName = "../../evil/../escape/app-release.apk"
        )
        val expectedDir = java.io.File(context.cacheDir, "app_updates").canonicalFile
        assertEquals(expectedDir, safeApk.parentFile?.canonicalFile)
        assertTrue(safeApk.name.endsWith(".apk"))
        assertFalse(safeApk.name.contains(".."))

        // 3. FileProvider install intent uses application/vnd.android.package-archive and read permission flag
        safeApk.parentFile?.mkdirs()
        safeApk.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00))
        val installIntent = com.example.update.AppUpdateCoordinator.buildInstallApkIntent(context, safeApk)
        assertEquals(android.content.Intent.ACTION_VIEW, installIntent.action)
        assertEquals("application/vnd.android.package-archive", installIntent.type)
        assertEquals("content", installIntent.data?.scheme)
        assertTrue((installIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        safeApk.delete()
    }

    @Test
    fun `comp item resolver maps official riot in-game item names artifacts radiants emblems slugs and fallback icons`() {
        // Standard items vs Artifacts vs Radiants vs Support vs Emblems vs Hyphenated slugs
        assertEquals("Red Buff", com.example.ui.components.formatRiotAssetLabel("TFT_Item_RapidFireCannon"))
        assertEquals("Rapid Firecannon", com.example.ui.components.formatRiotAssetLabel("Rapid Firecannon"))
        assertEquals("Flickerblades", com.example.ui.components.formatRiotAssetLabel("DA_Artifact_NavoriFlickerblade"))
        assertEquals("Manazane", com.example.ui.components.formatRiotAssetLabel("TFT4_Item_OrnnMuramana"))
        assertEquals("Gold Collector", com.example.ui.components.formatRiotAssetLabel("TFT4_Item_OrnnTheCollector"))
        assertEquals("Hullcrusher", com.example.ui.components.formatRiotAssetLabel("TFT9_Item_OrnnHullbreaker"))
        assertEquals("Shroud of Stillness", com.example.ui.components.formatRiotAssetLabel("TFT_Item_Shroud"))
        assertEquals("Radiant Red Buff", com.example.ui.components.formatRiotAssetLabel("TFT5_Item_RapidFirecannonRadiant"))
        assertEquals("Radiant Blue Buff", com.example.ui.components.formatRiotAssetLabel("DA_BlueBuffRadiant"))
        assertEquals("Elderwood Emblem", com.example.ui.components.formatRiotAssetLabel("TFT18_Item_ElderwoodEmblemItem"))
        assertEquals("Striker's Flail", com.example.ui.components.formatRiotAssetLabel("strikers-flail"))
        assertEquals("Jeweled Gauntlet", com.example.ui.components.formatRiotAssetLabel("jeweled-gauntlet"))
        assertEquals("B.F. Sword", com.example.ui.components.formatRiotAssetLabel("bf-sword"))

        // Scraped item icon registration & ordered fallback chain
        com.example.ui.components.TftAssetIconResolver.registerScrapedItemIcon(
            "Custom Artifact Blade",
            "https://tft.ninja/items/custom_artifact_blade.png"
        )
        val scrapedUrls = com.example.ui.components.TftAssetIconResolver.resolveItemIconFallbackUrls(
            "Custom Artifact Blade",
            TftEnvironment.PBE
        )
        assertTrue(scrapedUrls.contains("https://tft.ninja/items/custom_artifact_blade.png"))

        val ieUrls = com.example.ui.components.TftAssetIconResolver.resolveItemIconFallbackUrls(
            "Infinity Edge",
            TftEnvironment.PBE
        )
        assertTrue("Infinity Edge should have multiple ordered HTTPS icon fallbacks", ieUrls.size >= 2)
        assertTrue(ieUrls.all { it.startsWith("https://") })
    }

    @Test
    fun `pbe patch notes keep long prose diffs out of compact previous and new stat pills while preserving short stat diffs`() {
        val scraper = com.example.data.network.TftipsPatchScraper()
        val detailHtml = """
            <html><body>
              <h2>Modified</h2>
              <h3 class="text-sm font-bold leading-tight">Graves</h3>
              <p>Ability: <del class="tft-diff-del">Fires a narrow cone of bullets dealing physical damage to enemies in front of him.</del> <ins class="tft-diff-ins">Dashes toward targets and fires a wide cone of bullets dealing physical damage, empowering an extra attack on kill.</ins></p>
              <h2>Buffs</h2>
              <h3 class="text-sm font-bold leading-tight">Ziggs</h3>
              <p>Spell Damage: <del class="tft-diff-del">280/420/650</del> <ins class="tft-diff-ins">300/450/700</ins></p>
            </body></html>
        """.trimIndent()

        val details = scraper.parseDetailTargetChanges(detailHtml)
        val gravesDetail = details["graves"]
        assertNotNull("Graves long PBE prose change should still parse", gravesDetail)
        assertEquals("", gravesDetail!!.previousValue)
        assertEquals("", gravesDetail.newValue)
        assertTrue(gravesDetail.summary.contains("Dashes toward targets", ignoreCase = true))

        val ziggsDetail = details["ziggs"]
        assertNotNull("Ziggs short numeric PBE stat diff should parse", ziggsDetail)
        assertEquals("280/420/650", ziggsDetail!!.previousValue)
        assertEquals("300/450/700", ziggsDetail.newValue)
    }

    @Test
    fun `pbe team code rejects stale code that swaps Graves for Mordekaiser and re-encodes Graves from live Riot PBE mapping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }

        // Riot live PBE Set 19 mapping: Graves = 0x495 (1173), Mordekaiser = 0x480 (1152)
        val livePbeMappingJson = """
            {
              "TFTSet19": [
                {"character_id": "TFT19_Graves", "display_name": "Graves", "tier": 4, "traits": ["Beach Pop", "Renegade"], "team_planner_code": 1173},
                {"character_id": "TFT19_Mordekaiser", "display_name": "Mordekaiser", "tier": 5, "traits": ["Pentakill"], "team_planner_code": 1152},
                {"character_id": "TFT19_Renekton", "display_name": "Renekton", "tier": 2, "traits": ["Beach Pop", "Frontman"], "team_planner_code": 1160},
                {"character_id": "TFT19_Taric", "display_name": "Taric", "tier": 1, "traits": ["Beach Pop", "Juggernaut"], "team_planner_code": 1161},
                {"character_id": "TFT19_Syndra", "display_name": "Syndra", "tier": 3, "traits": ["Beach Pop", "Choreographer"], "team_planner_code": 1162}
              ]
            }
        """.trimIndent()

        // Stale/buggy code from a site or old PBE build that put Mordekaiser (480) instead of Graves (495)!
        val staleCodeWithMordekaiser = "0248048848948a000000000000000000TFTSet19"
        val gravesPbeComp = MetaComp(
            id = "tftninja_pbe_beach_pop_graves",
            title = "Beach Pop Graves",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            setName = "Set 19",
            patchTag = "19.1-PBE",
            playstyle = "Fast 8",
            avgPlacement = 3.55,
            top4Rate = 61.0,
            winRate = 17.5,
            pickRate = 5.2,
            trendDelta = "Top Meta",
            isTrending = true,
            carryName = "Graves",
            coreUnits = listOf("Graves", "Renekton", "Taric", "Syndra"),
            traits = listOf("Beach Pop", "Renegade"),
            hexPlacements = emptyList(),
            augmentPriorities = emptyList(),
            levelingGuide = "Fast 8",
            notes = "",
            sourceUrl = "https://tft.ninja/comps?e=pbe",
            teamPlannerCode = staleCodeWithMordekaiser
        )

        val pbeClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, livePbeMappingJson))
                    .build()
            }
            .build()

        // 1. resolveTeamCode rejects stale Mordekaiser slot (480) and encodes Graves (495)
        val resolved = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, gravesPbeComp, pbeClient)
        }
        assertTrue(resolved.isSuccess)
        val resolvedSlots = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.extractNonEmptySlotHexes(resolved.code)
        assertTrue("Encoded code must contain Graves (495)", resolvedSlots.contains("495"))
        assertFalse("Encoded code must NOT contain Mordekaiser (480)", resolvedSlots.contains("480"))

        // 2. enrichCompsWithTeamCodes also detects and repairs the stale Mordekaiser code automatically
        val enriched = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.enrichCompsWithTeamCodes(
                context = context,
                comps = listOf(gravesPbeComp),
                environment = TftEnvironment.PBE,
                client = pbeClient
            )
        }
        val enrichedSlots = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.extractNonEmptySlotHexes(enriched.first().teamPlannerCode)
        assertTrue("Enriched code must contain Graves (495)", enrichedSlots.contains("495"))
        assertFalse("Enriched code must NOT contain Mordekaiser (480)", enrichedSlots.contains("480"))
    }

    @Test
    fun `overlay precomputed search index filters champions items components and queries accurately without regex allocations`() {
        val comps = listOf(
            MetaComp(
                id = "c1",
                title = "Beach Pop Graves",
                tier = TierRank.S,
                environment = TftEnvironment.PBE,
                setName = "Set 19",
                patchTag = "19.1-PBE",
                playstyle = "Fast 8",
                avgPlacement = 3.5,
                top4Rate = 60.0,
                winRate = 17.0,
                pickRate = 5.0,
                trendDelta = "",
                isTrending = true,
                carryName = "Graves",
                coreUnits = listOf("Graves", "Renekton", "Taric"),
                traits = listOf("Beach Pop", "Renegade"),
                hexPlacements = listOf(
                    HexUnitPlacement(
                        unitName = "Graves",
                        row = 0,
                        col = 3,
                        cost = 4,
                        items = listOf("Bloodthirster", "Sterak's Gage"),
                        isCarry = true,
                        stars = 2
                    )
                ),
                augmentPriorities = emptyList(),
                levelingGuide = "Fast 8",
                notes = ""
            ),
            MetaComp(
                id = "c2",
                title = "Pentakill Mordekaiser",
                tier = TierRank.A,
                environment = TftEnvironment.PBE,
                setName = "Set 19",
                patchTag = "19.1-PBE",
                playstyle = "Fast 9",
                avgPlacement = 3.9,
                top4Rate = 54.0,
                winRate = 14.0,
                pickRate = 3.0,
                trendDelta = "",
                isTrending = false,
                carryName = "Mordekaiser",
                coreUnits = listOf("Mordekaiser", "Karthus", "Yorick"),
                traits = listOf("Pentakill"),
                hexPlacements = listOf(
                    HexUnitPlacement(
                        unitName = "Mordekaiser",
                        row = 0,
                        col = 3,
                        cost = 5,
                        items = listOf("Jeweled Gauntlet", "Hand of Justice"),
                        isCarry = true,
                        stars = 2
                    )
                ),
                augmentPriorities = emptyList(),
                levelingGuide = "Fast 9",
                notes = ""
            )
        )

        val index = com.example.overlay.OverlayArmySearchEngine.buildSearchIndex(comps, emptyList())
        assertEquals(2, index.entries.size)

        // Filter by champion
        val gravesMatches = com.example.overlay.OverlayArmySearchEngine.filterIndexedArmies(
            index = index,
            tierFilter = null,
            selectedChampions = setOf("Graves"),
            selectedItems = emptySet(),
            query = ""
        )
        assertEquals(listOf("c1"), gravesMatches.map { it.id })

        // Filter by base component (B.F. Sword builds Bloodthirster / Sterak's Gage)
        val bfSwordMatches = com.example.overlay.OverlayArmySearchEngine.filterIndexedArmies(
            index = index,
            tierFilter = null,
            selectedChampions = emptySet(),
            selectedItems = setOf("B.F. Sword"),
            query = ""
        )
        assertEquals(listOf("c1"), bfSwordMatches.map { it.id })

        // Filter by query + tier
        val pentakillMatches = com.example.overlay.OverlayArmySearchEngine.filterIndexedArmies(
            index = index,
            tierFilter = TierRank.A,
            selectedChampions = emptySet(),
            selectedItems = emptySet(),
            query = "pentakill"
        )
        assertEquals(listOf("c2"), pentakillMatches.map { it.id })
    }

    @Test
    fun `pbe patches deduplicate identical patch ids and punctuation variants to prevent lazy column duplicate key crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineMetaCacheStore(context)
        val seeded = store.readOrSeedBundle()

        val pbePatchA = com.example.data.model.PatchUpdateRecord(
            id = "tftips_pbe_19_1_PBE",
            environment = TftEnvironment.PBE,
            version = "19.1-PBE",
            title = "TFT Patch 19.1-PBE",
            releaseDate = "Latest",
            isMajorPatch = true,
            headline = "Set 19 PBE balance update",
            highlights = listOf("Buffs (2): Graves, Ziggs"),
            balanceChanges = emptyList(),
            trendingCompIds = emptyList(),
            sourceUrl = "https://tftips.app/en/patches/pbe"
        )
        val pbePatchDup = pbePatchA.copy(version = "19.1 PBE", title = "TFT Patch 19.1 PBE")

        assertTrue(store.writeBundle(seeded.copy(pbePatches = listOf(pbePatchA, pbePatchDup))))
        val reloaded = store.readOrSeedBundle()
        assertEquals(1, reloaded.pbePatches.count { it.id == "tftips_pbe_19_1_PBE" })

        val htmlWithDupPbe = """
            <html><body>
              <a href="/en/patches/pbe">19.1 PBE</a><time>Today</time>
              <a href="/en/patches/pbe-2">19.1-PBE</a><time>Yesterday</time>
            </body></html>
        """.trimIndent()
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, htmlWithDupPbe))
                    .build()
            }
            .build()
        val fetched = TftipsPatchScraper(mockClient).fetchPatchesFromTftips()
        assertEquals(1, fetched.pbePatchRecords.count { it.id == "tftips_pbe_19_1_PBE" })
        assertEquals(fetched.pbePatchRecords.size, fetched.pbePatchRecords.map { it.id }.distinct().size)
    }

    @Test
    fun `preloaded live and pbe catalogs have clean names descriptions and resolved icons for all champions items and augments`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineMetaCacheStore(context)
        val seeded = store.readOrSeedBundle()

        val rawTokenPattern = Regex("%i:[^%]+%|@[A-Za-z0-9_.*]+@|<br\\s*/?>", RegexOption.IGNORE_CASE)

        // LIVE Set 18 Champions
        assertEquals(74, seeded.liveChampions.size)
        seeded.liveChampions.forEach { champ ->
            assertFalse("Raw token in LIVE champ ${champ.name}: ${champ.abilityDescription}", rawTokenPattern.containsMatchIn(champ.abilityDescription))
            assertFalse("Raw ID in LIVE champ name: ${champ.name}", champ.name.startsWith("TFT_"))
            assertNotNull(
                "Missing icon for LIVE champ ${champ.name}",
                com.example.ui.components.TftAssetIconResolver.resolveChampionIconUrl(champ.name, TftEnvironment.LIVE)
            )
        }

        // PBE Set 19 Champions
        assertEquals(94, seeded.pbeChampions.size)
        seeded.pbeChampions.forEach { champ ->
            assertFalse("Raw token in PBE champ ${champ.name}: ${champ.abilityDescription}", rawTokenPattern.containsMatchIn(champ.abilityDescription))
            assertFalse("Raw ID in PBE champ name: ${champ.name}", champ.name.startsWith("TFT_"))
            assertNotNull(
                "Missing icon for PBE champ ${champ.name}",
                com.example.ui.components.TftAssetIconResolver.resolveChampionIconUrl(champ.name, TftEnvironment.PBE)
            )
        }

        // LIVE Set 18 Items
        assertTrue(seeded.liveItems.size >= 140)
        seeded.liveItems.forEach { item ->
            assertFalse("Raw ID in LIVE item name: ${item.name}", item.name.startsWith("TFT_"))
            assertFalse("Generic placeholder in LIVE item ${item.name}: ${item.statsSummary}", item.statsSummary.contains("Set 18 equipment item", ignoreCase = true))
            assertFalse("Raw token in LIVE item ${item.name}: ${item.statsSummary}", rawTokenPattern.containsMatchIn(item.statsSummary))
            assertNotNull(
                "Missing icon for LIVE item ${item.name}",
                com.example.ui.components.TftAssetIconResolver.resolveItemIconUrl(item.name, TftEnvironment.LIVE)
            )
        }
        val tacticiansCape = seeded.liveItems.firstOrNull { it.name == "Tactician's Cape" }
        val tacticiansShield = seeded.liveItems.firstOrNull { it.name == "Tactician's Shield" }
        assertNotNull("Tactician's Cape should be cleanly named", tacticiansCape)
        assertNotNull("Tactician's Shield should be cleanly named", tacticiansShield)
        assertTrue(seeded.liveItems.any { it.name == "Hand of Justice" })

        // PBE Set 19 Items
        assertTrue(seeded.pbeItems.size >= 80)
        seeded.pbeItems.forEach { item ->
            assertFalse("Raw ID in PBE item name: ${item.name}", item.name.startsWith("TFT_"))
            assertFalse("Raw token in PBE item ${item.name}: ${item.statsSummary}", rawTokenPattern.containsMatchIn(item.statsSummary))
            assertFalse("Lone dash in PBE item ${item.name}: ${item.statsSummary}", item.statsSummary.trim().endsWith("• -") || item.statsSummary.trim() == "-")
            assertNotNull(
                "Missing icon for PBE item ${item.name}",
                com.example.ui.components.TftAssetIconResolver.resolveItemIconUrl(item.name, TftEnvironment.PBE)
            )
        }

        // LIVE Set 18 Augments
        assertTrue(seeded.liveAugments.size >= 340)
        seeded.liveAugments.forEach { aug ->
            assertFalse("Raw token in LIVE augment name: ${aug.name}", rawTokenPattern.containsMatchIn(aug.name))
            assertFalse("Raw token in LIVE augment desc ${aug.name}: ${aug.description}", rawTokenPattern.containsMatchIn(aug.description))
            assertTrue(
                "Missing icon fallback for LIVE augment ${aug.name}",
                com.example.ui.components.TftAssetIconResolver.resolveAugmentIconFallbackUrls(aug.name, TftEnvironment.LIVE).isNotEmpty()
            )
        }
        assertTrue(seeded.liveAugments.any { it.name == "Gold Windfall" })
        assertTrue(com.example.ui.components.TftAssetIconResolver.resolveAugmentIconFallbackUrls("Stampede", TftEnvironment.LIVE).any { it.startsWith("https://") })

        // PBE Set 19 Augments + Preloaded PBE Augment Icons
        assertTrue(seeded.pbeAugments.size >= 240)
        assertTrue("Expected >= 240 preloaded PBE augment icons, got ${seeded.pbeAugmentIcons.size}", seeded.pbeAugmentIcons.size >= 240)
        seeded.pbeAugments.forEach { aug ->
            assertFalse("Raw token in PBE augment name: ${aug.name}", rawTokenPattern.containsMatchIn(aug.name))
            assertFalse("Raw token in PBE augment desc ${aug.name}: ${aug.description}", rawTokenPattern.containsMatchIn(aug.description))
            val urls = com.example.ui.components.TftAssetIconResolver.resolveAugmentIconFallbackUrls(aug.name, TftEnvironment.PBE)
            assertTrue("Missing icon fallback for PBE augment ${aug.name}", urls.any { it.startsWith("https://") })
        }
        assertTrue(
            com.example.ui.components.TftAssetIconResolver.resolveAugmentIconFallbackUrls("Bonus Gift", TftEnvironment.PBE)
                .any { it.startsWith("https://") }
        )

        // Runtime sanitizer converts Riot stat tokens into readable labels and strips raw placeholders
        assertEquals(
            "Deal 120 (AP) magic damage\nand gain 10 Gold.",
            com.example.ui.components.sanitizeTftDescription("Deal 120 %i:scaleAP% magic damage<br>and gain @Gold@ 10 Gold.")
        )
    }

    @Test
    fun `level guide produces distinct progressive early mid and final boards for live fast8 pbe fast9 reroll and duplicate stages`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        val seeded = cacheStore.readOrSeedBundle()
        assertTrue(seeded.liveChampions.isNotEmpty())
        assertTrue(seeded.pbeChampions.isNotEmpty())
        assertTrue(
            "LIVE catalog champions should be populated in TftAssetIconResolver",
            com.example.ui.components.TftAssetIconResolver.getCatalogChampions(TftEnvironment.LIVE).size >= 50
        )
        assertTrue(
            "PBE catalog champions should be populated in TftAssetIconResolver",
            com.example.ui.components.TftAssetIconResolver.getCatalogChampions(TftEnvironment.PBE).size >= 70
        )

        fun normSet(units: List<String>): Set<String> =
            units.map { it.lowercase().replace(Regex("[^a-z0-9]"), "") }.toSet()

        // 1. LIVE Set 18 Fast 8 comp with ONLY 4-cost and 5-cost units on the final board
        val liveFast8 = MetaComp(
            id = "live_fast8_ahri",
            title = "Blossom Spellweaver Ahri",
            environment = TftEnvironment.LIVE,
            setName = "Set 18",
            patchTag = "16.23",
            tier = TierRank.S,
            playstyle = "Fast 8",
            carryName = "Ahri",
            traits = listOf("4 Blossom", "2 Spellweaver", "2 Brawler"),
            coreUnits = listOf("Ahri", "Sett", "Zyra", "Lillia", "Sentinel", "Taric", "Ivern", "Alune"),
            hexPlacements = listOf(
                HexUnitPlacement("Ahri", 3, 6, 4, items = listOf("Spear of Shojin", "Jeweled Gauntlet", "Striker's Flail"), isCarry = true),
                HexUnitPlacement("Sett", 0, 3, 4),
                HexUnitPlacement("Zyra", 3, 5, 4),
                HexUnitPlacement("Lillia", 0, 4, 4),
                HexUnitPlacement("Sentinel", 0, 2, 4),
                HexUnitPlacement("Taric", 0, 5, 5),
                HexUnitPlacement("Ivern", 1, 3, 5),
                HexUnitPlacement("Alune", 3, 1, 5)
            ),
            avgPlacement = 3.72,
            top4Rate = 62.0,
            winRate = 17.5,
            pickRate = 1.2,
            trendDelta = "+0.2",
            isTrending = true,
            augmentPriorities = emptyList(),
            levelingGuide = "",
            notes = ""
        )
        val liveStages = com.example.ui.components.resolveCompLevelGuideStages(liveFast8, seeded.liveChampions)
        assertEquals(3, liveStages.size)
        val liveEarlySet = normSet(liveStages[0].units)
        val liveMidSet = normSet(liveStages[1].units)
        val liveFinalSet = normSet(liveStages[2].units)
        assertNotEquals("Early and Mid boards must differ", liveEarlySet, liveMidSet)
        assertNotEquals("Mid and Final boards must differ", liveMidSet, liveFinalSet)
        assertNotEquals("Early and Final boards must differ", liveEarlySet, liveFinalSet)
        assertTrue("Early board should have 4 low-cost units", liveStages[0].units.size == 4)
        liveStages[0].units.forEach { u ->
            val cost = com.example.ui.components.TftAssetIconResolver.getChampionCost(u, TftEnvironment.LIVE) ?: 0
            assertTrue("Early Lv 4-5 unit $u must be 1-2 cost, got $cost", cost in 1..2)
        }
        assertFalse("Ahri (4g carry) should not appear in Level 4-5 early board", "ahri" in liveEarlySet)
        assertEquals(normSet(liveFast8.coreUnits), liveFinalSet)

        // 2. PBE Set 19 Fast 9 comp with mostly 4-5 cost units
        val pbeFast9 = MetaComp(
            id = "pbe_fast9_morde",
            title = "Pentakill Mordekaiser Fast 9",
            environment = TftEnvironment.PBE,
            setName = "Set 19 PBE",
            patchTag = "PBE",
            tier = TierRank.S,
            playstyle = "Fast 9",
            carryName = "Mordekaiser",
            traits = listOf("5 Pentakill", "2 Juggernaut", "2 Swiftstriker"),
            coreUnits = listOf("Mordekaiser", "Janna", "Lee Sin", "Vladimir", "Kayle", "Yorick", "Graves", "Wukong"),
            hexPlacements = listOf(
                HexUnitPlacement("Mordekaiser", 0, 3, 5, items = listOf("Hand of Justice", "Sterak's Gage", "Titan's Resolve"), isCarry = true),
                HexUnitPlacement("Janna", 3, 6, 5),
                HexUnitPlacement("Lee Sin", 0, 2, 5),
                HexUnitPlacement("Vladimir", 1, 4, 5),
                HexUnitPlacement("Kayle", 3, 0, 4),
                HexUnitPlacement("Yorick", 0, 4, 4),
                HexUnitPlacement("Graves", 1, 2, 4),
                HexUnitPlacement("Wukong", 0, 5, 4)
            ),
            avgPlacement = 3.55,
            top4Rate = 65.0,
            winRate = 21.0,
            pickRate = 1.1,
            trendDelta = "+0.3",
            isTrending = true,
            augmentPriorities = emptyList(),
            levelingGuide = "",
            notes = ""
        )
        val pbeStages = com.example.ui.components.resolveCompLevelGuideStages(pbeFast9, seeded.pbeChampions)
        assertEquals(3, pbeStages.size)
        val pbeEarlySet = normSet(pbeStages[0].units)
        val pbeMidSet = normSet(pbeStages[1].units)
        val pbeFinalSet = normSet(pbeStages[2].units)
        assertNotEquals("PBE Early and Mid must differ", pbeEarlySet, pbeMidSet)
        assertNotEquals("PBE Mid and Final must differ", pbeMidSet, pbeFinalSet)
        assertNotEquals("PBE Early and Final must differ", pbeEarlySet, pbeFinalSet)
        pbeStages[0].units.forEach { u ->
            val cost = com.example.ui.components.TftAssetIconResolver.getChampionCost(u, TftEnvironment.PBE) ?: 0
            assertTrue("PBE Early unit $u must be 1-2 cost, got $cost", cost in 1..2)
        }
        pbeStages[1].units.forEach { u ->
            val cost = com.example.ui.components.TftAssetIconResolver.getChampionCost(u, TftEnvironment.PBE) ?: 0
            assertTrue("PBE Mid unit $u must be <= 4 cost (no 5-cost legendaries), got $cost", cost in 1..4)
        }
        assertEquals(normSet(pbeFast9.coreUnits), pbeFinalSet)

        // 3. Slow Roll 1-2 cost comp should progress from Early -> Mid -> Cap without repeating boards
        val slowRollComp = MetaComp(
            id = "live_slowroll_veigar",
            title = "Sprykin Veigar Reroll",
            environment = TftEnvironment.LIVE,
            setName = "Set 18",
            patchTag = "16.23",
            tier = TierRank.A,
            playstyle = "Slow Roll",
            carryName = "Veigar",
            traits = listOf("4 Sprykin", "3 Blackthorn", "2 Spellweaver"),
            coreUnits = listOf("Veigar", "Kobuko", "Rek'Sai", "Teemo", "Rammus", "Azir", "Malphite", "Gnar"),
            hexPlacements = emptyList(),
            avgPlacement = 3.95,
            top4Rate = 56.0,
            winRate = 14.0,
            pickRate = 0.8,
            trendDelta = "0.0",
            isTrending = false,
            augmentPriorities = emptyList(),
            levelingGuide = "",
            notes = ""
        )
        val slowRollStages = com.example.ui.components.resolveCompLevelGuideStages(slowRollComp, seeded.liveChampions)
        assertEquals(3, slowRollStages.size)
        assertTrue("Slow roll carry Veigar should appear in Early", "veigar" in normSet(slowRollStages[0].units))
        assertTrue("Slow roll board should grow Early < Mid < Final", slowRollStages[0].units.size < slowRollStages[1].units.size && slowRollStages[1].units.size < slowRollStages[2].units.size)
        assertEquals(3, slowRollStages.map { normSet(it.units) }.distinct().size)

        // 4. Degenerate scraped stages that repeat the exact same champions at all levels must be replaced
        val duplicateUnits = listOf("Ahri", "Sett", "Zyra", "Lillia", "Sentinel", "Taric")
        val degenerateComp = liveFast8.copy(
            levelGuideStages = listOf(
                com.example.data.model.LevelTransitionStage("Level 4-5", "Stage 2-1", duplicateUnits, "Early"),
                com.example.data.model.LevelTransitionStage("Level 6-7", "Stage 3-2", duplicateUnits, "Mid"),
                com.example.data.model.LevelTransitionStage("Level 8-9", "Stage 4-2", duplicateUnits, "Late")
            )
        )
        val repairedStages = com.example.ui.components.resolveCompLevelGuideStages(degenerateComp, seeded.liveChampions)
        assertEquals(3, repairedStages.size)
        assertEquals(
            "Degenerate identical stages must be replaced with 3 distinct progressive boards",
            3,
            repairedStages.map { normSet(it.units) }.distinct().size
        )
    }

    @Test
    fun `pbe Graves never maps to Mordekaiser via placeholder pathSlug, site fallback cache, decimal codes, stale disk cache, or flex slot overflow`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.clearMemoryCacheForTests()
        context.filesDir.listFiles()?.filter { it.name.startsWith("tft_teamplanner_") }?.forEach { it.delete() }

        // 1. Placeholder asset `path` on TFT19_Mordekaiser containing `graves` before TFT19_Graves MUST NOT hijack "graves"
        //    AND an unrelated string `"code": "promo"` on a prop MUST NOT flip decimal string codes ("1173") into hex!
        val pathCollisionJson = """
            {
              "TFTSet19": [
                {
                  "character_id": "TFT19_Mordekaiser",
                  "path": "/lol-game-data/assets/characters/tft19_mordekaiser/hud/tft19_graves_square.tft_set19.png",
                  "tier": 5,
                  "traits": ["Pentakill"],
                  "team_planner_code": "1152"
                },
                {
                  "character_id": "TFT19_TrainingDummy",
                  "code": "abcdef",
                  "tier": 0,
                  "traits": []
                },
                {
                  "character_id": "TFT19_Graves_Old",
                  "disabled": true,
                  "tier": 4,
                  "traits": ["Beach Pop"],
                  "team_planner_code": "1152"
                },
                {
                  "character_id": "TFT19_Graves",
                  "path": "/lol-game-data/assets/characters/tft19_graves/hud/tft19_graves_square.tft_set19.png",
                  "tier": 4,
                  "traits": ["Beach Pop", "Renegade"],
                  "team_planner_code": "1173"
                },
                {
                  "character_id": "TFT19_Renekton",
                  "tier": 2,
                  "traits": ["Beach Pop", "Frontman"],
                  "team_planner_code": "1160"
                },
                {
                  "character_id": "TFT19_Taric",
                  "tier": 1,
                  "traits": ["Beach Pop", "Juggernaut"],
                  "team_planner_code": "1161"
                },
                {
                  "character_id": "TFT19_Syndra",
                  "tier": 3,
                  "traits": ["Beach Pop", "Choreographer"],
                  "team_planner_code": "1162"
                }
              ]
            }
        """.trimIndent()

        val parsed = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(pathCollisionJson)
        assertNotNull("Catalog with placeholder path and decimal codes must parse cleanly", parsed)

        val beachPopGravesComp = MetaComp(
            id = "tftninja_pbe_beach_pop_graves_2",
            title = "Beach Pop Graves",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            setName = "Set 19",
            patchTag = "19.1-PBE",
            playstyle = "Fast 8",
            avgPlacement = 3.55,
            top4Rate = 61.0,
            winRate = 17.5,
            pickRate = 5.2,
            trendDelta = "Top Meta",
            isTrending = true,
            carryName = "Graves",
            coreUnits = listOf("Graves", "Renekton", "Taric", "Syndra"),
            traits = listOf("Beach Pop", "Renegade"),
            hexPlacements = emptyList(),
            augmentPriorities = emptyList(),
            levelingGuide = "Fast 8",
            notes = "",
            sourceUrl = "https://tft.ninja/comps?e=pbe",
            teamPlannerCode = "0248048848948a000000000000000000TFTSet19" // Stale Mordekaiser (480) instead of Graves (495)
        )

        val encodedDirect = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(beachPopGravesComp, parsed!!)
        assertTrue(encodedDirect.isSuccess)
        val directSlots = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.extractNonEmptySlotHexes(encodedDirect.code)
        assertTrue("Graves must encode to 0x495 (1173), got $directSlots", directSlots.contains("495"))
        assertFalse("Mordekaiser (0x480) must NEVER be encoded in place of Graves", directSlots.contains("480"))

        // 2. Third-party site catalog with wrong Graves=480 MUST NOT override or block Riot's official CDragon PBE fetch
        val poisonedSiteCatalog = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(
            rawJson = """
                {
                  "TFTSet19": [
                    {"character_id": "TFT19_Graves", "display_name": "Graves", "team_planner_code": 1152},
                    {"character_id": "TFT19_Renekton", "display_name": "Renekton", "team_planner_code": 1160},
                    {"character_id": "TFT19_Taric", "display_name": "Taric", "team_planner_code": 1161},
                    {"character_id": "TFT19_Syndra", "display_name": "Syndra", "team_planner_code": 1162}
                  ]
                }
            """.trimIndent(),
            isThirdPartySiteFallback = true
        )!!
        com.example.data.teamplanner.TftTeamPlannerCodeEncoder.cacheVerifiedCatalogFromSite(TftEnvironment.PBE, poisonedSiteCatalog)

        // Create a legacy v8 cache file to verify it gets purged automatically
        val legacyV8File = java.io.File(context.filesDir, "tft_teamplanner_pbe_v8.json")
        legacyV8File.writeText("""{"TFTSet19":[{"character_id":"TFT19_Graves","team_planner_code":1152}]}""", Charsets.UTF_8)
        assertTrue(legacyV8File.exists())

        val liveRiotClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, pathCollisionJson))
                    .build()
            }
            .build()

        val resolved = kotlinx.coroutines.runBlocking {
            com.example.data.teamplanner.TftTeamPlannerCodeEncoder.resolveTeamCode(context, beachPopGravesComp, liveRiotClient)
        }
        assertTrue(resolved.isSuccess)
        assertFalse("Legacy v8 PBE cache file must be purged", legacyV8File.exists())
        val resolvedSlots = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.extractNonEmptySlotHexes(resolved.code)
        assertTrue("Resolved PBE code must contain Graves (495), got $resolvedSlots", resolvedSlots.contains("495"))
        assertFalse("Resolved PBE code must NOT contain Mordekaiser (480)", resolvedSlots.contains("480"))

        // 3. Primary board 4-cost Graves (within first 10 hex placements) MUST NOT be pushed out by a late-appended 5-cost flex Mordekaiser
        val elevenUnitJson = """
            {
              "TFTSet19": [
                {"character_id": "TFT19_Taric", "team_planner_code": 1161},
                {"character_id": "TFT19_Zoe", "team_planner_code": 1162},
                {"character_id": "TFT19_Ashe", "team_planner_code": 1163},
                {"character_id": "TFT19_Renekton", "team_planner_code": 1164},
                {"character_id": "TFT19_Syndra", "team_planner_code": 1165},
                {"character_id": "TFT19_Zac", "team_planner_code": 1166},
                {"character_id": "TFT19_Taliyah", "team_planner_code": 1167},
                {"character_id": "TFT19_Kaisa", "team_planner_code": 1168},
                {"character_id": "TFT19_Akali", "team_planner_code": 1169},
                {"character_id": "TFT19_Graves", "team_planner_code": 1173},
                {"character_id": "TFT19_Mordekaiser", "team_planner_code": 1152}
              ]
            }
        """.trimIndent()
        val elevenCatalog = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.parseCommunityDragonJson(elevenUnitJson)!!
        val elevenPlacements = listOf(
            HexUnitPlacement("Taliyah", 3, 6, cost = 5, isCarry = true, items = listOf("Jeweled Gauntlet")),
            HexUnitPlacement("Taric", 0, 1, cost = 1),
            HexUnitPlacement("Zoe", 3, 0, cost = 1),
            HexUnitPlacement("Ashe", 3, 1, cost = 2),
            HexUnitPlacement("Renekton", 0, 2, cost = 2),
            HexUnitPlacement("Syndra", 3, 2, cost = 3),
            HexUnitPlacement("Zac", 0, 3, cost = 4),
            HexUnitPlacement("Kai'Sa", 3, 4, cost = 4),
            HexUnitPlacement("Akali", 1, 5, cost = 4),
            HexUnitPlacement("Graves", 1, 2, cost = 4),
            // Late-appended 5-cost flex unit at index 10 (11th unit)
            HexUnitPlacement("Mordekaiser", 0, 5, cost = 5)
        )
        val overflowComp = beachPopGravesComp.copy(
            carryName = "Taliyah",
            coreUnits = elevenPlacements.map { it.unitName },
            hexPlacements = elevenPlacements,
            teamPlannerCode = ""
        )
        val overflowRes = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.encodeCompWithCatalog(overflowComp, elevenCatalog)
        assertTrue(overflowRes.isSuccess)
        val overflowSlots = com.example.data.teamplanner.TftTeamPlannerCodeEncoder.extractNonEmptySlotHexes(overflowRes.code)
        assertTrue("Primary board Graves (495) must stay in the 10 planner slots", overflowSlots.contains("495"))
        assertFalse("11th flex unit Mordekaiser (480) must NOT bump core Graves out", overflowSlots.contains("480"))
    }
}


