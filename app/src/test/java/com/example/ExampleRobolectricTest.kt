package com.example

import android.content.Context
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
        trendDelta = "TFTAcademy ${environment.label}",
        traits = listOf("6 Elderwood", "4 Arcanist"),
        coreUnits = listOf("Ornn", "Lillia", carry, "Veigar", "Nami"),
        hexPlacements = listOf(
            HexUnitPlacement("Ornn", 0, 3, cost = 4),
            HexUnitPlacement(carry, 3, 6, cost = 4, isCarry = true, items = listOf("Blue Buff", "Jeweled Gauntlet"))
        ),
        augmentPriorities = listOf("Jeweled Lotus II", "Level Up!"),
        levelingGuide = "Fast 8 on 4-2",
        notes = "Synced from tftacademy.com/tierlist/comps",
        sourceUrl = TftAcademyCompsScraper.TFTACADEMY_COMPS_URL
    )

    @Test
    fun `offline cache store removes legacy comps and persists tftacademy comps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineMetaCacheStore(context)

        // All hardcoded legacy comps must be removed
        assertTrue(MetaCatalogData.liveComps.isEmpty())
        assertTrue(MetaCatalogData.pbeComps.isEmpty())

        val seeded = store.readOrSeedBundle()
        assertTrue(store.hasPersistedCache())
        // Preloaded real TFTAcademy Set 18 LIVE (35) & TFTactics Set 19 PBE (23) compositions
        assertEquals(35, seeded.liveComps.size)
        assertTrue(seeded.liveComps.all { it.sourceUrl == TftAcademyCompsScraper.TFTACADEMY_COMPS_URL })
        assertTrue(seeded.liveComps.any { it.tier == TierRank.S })
        assertTrue(seeded.liveComps.any { it.tier == TierRank.X })
        assertTrue(seeded.liveComps.all { it.traits.isNotEmpty() })
        assertEquals(23, seeded.pbeComps.size)
        assertTrue(seeded.pbeComps.all { it.sourceUrl == com.example.data.network.TftacticsCompsScraper.TFTACTICS_COMPS_URL })
        assertTrue(seeded.pbeComps.all { it.traits.isNotEmpty() && it.hexPlacements.isNotEmpty() })
        assertEquals(35, seeded.toCacheStatus(environment = TftEnvironment.LIVE).compsCount)
        assertEquals(23, seeded.toCacheStatus(environment = TftEnvironment.PBE).compsCount)

        // Complete Set 18 catalog preloaded with traits, descriptions, and icons
        assertEquals(74, seeded.liveChampions.size)
        assertTrue(seeded.liveChampions.all { it.traits.isNotEmpty() && it.abilityDescription.isNotBlank() })
        assertTrue(seeded.liveItems.size >= 130)
        assertTrue(seeded.liveItems.all { it.statsSummary.isNotBlank() })
        assertTrue(seeded.liveAugments.size >= 240)
        assertTrue(seeded.liveAugments.all { it.description.isNotBlank() })

        val academyLive = sampleTftAcademyComp("tftacademy_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val tftacticsPbe = sampleTftAcademyComp("tftactics_pbe_ionic_spark_hitmakers", "Ionic Spark Hitmakers", TftEnvironment.PBE, carry = "Jinx")
            .copy(sourceUrl = com.example.data.network.TftacticsCompsScraper.TFTACTICS_COMPS_URL)
        val legacyComp = academyLive.copy(id = "legacy_comp", sourceUrl = "https://legacy.example/comps")

        val updated = seeded.copy(
            liveVersionInfo = seeded.liveVersionInfo.copy(
                livePatchVersion = "18.8",
                pbePatchVersion = "19.1-PBE"
            ),
            liveComps = listOf(academyLive, legacyComp),
            pbeComps = listOf(tftacticsPbe)
        )
        assertTrue(store.writeBundle(updated))

        val reloaded = store.readOrSeedBundle()
        assertEquals("18.8", reloaded.liveVersionInfo.livePatchVersion)
        assertEquals("19.1-PBE", reloaded.liveVersionInfo.pbePatchVersion)
        // Non-matching source comps are stripped automatically
        assertEquals(listOf("tftacademy_live_ahri"), reloaded.liveComps.map { it.id })
        assertEquals(listOf("tftactics_pbe_ionic_spark_hitmakers"), reloaded.pbeComps.map { it.id })
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
        assertEquals(TftAcademyCompsScraper.TFTACADEMY_COMPS_URL, alignedPbe.first().sourceUrl)
    }

    @Test
    fun `main viewmodel loads guest mode offline catalog asynchronously with zero firestore access`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        val academyLive = sampleTftAcademyComp("tftacademy_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val academyPbe = sampleTftAcademyComp("tftacademy_pbe_vayne", "Cyber Vayne", TftEnvironment.PBE, carry = "Vayne")
        cacheStore.writeBundle(
            OfflineMetaBundle(
                liveComps = listOf(academyLive),
                pbeComps = listOf(academyPbe)
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
        val riftbeastTraitUrl = resolver.resolveTraitIconUrl("3 Riftbeast")
        assertTrue(levelUpUrl?.let { it.endsWith(".png") || it.endsWith(".webp") } == true)
        assertTrue(floraUrl?.contains("DA_18_FloraFatalisAugment") == true)
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Made By JS96 + MK", context.getString(R.string.made_by_credits))

        val prefsStore = LocalPreferencesStore(context)
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
    fun `overlay drawer dimensions adapt safely to both portrait and landscape viewports`() {
        val portrait = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 411, screenHeightDp = 915)
        assertFalse(portrait.isLandscape)
        assertTrue(portrait.widthDp.value in 260f..360f)
        assertTrue(portrait.maxHeightDp.value < 915f)

        val landscapePhone = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 915, screenHeightDp = 411)
        assertTrue(landscapePhone.isLandscape)
        assertTrue(landscapePhone.widthDp.value in 300f..440f)
        assertTrue(landscapePhone.maxHeightDp.value <= 411f - 36f)

        val compactLandscape = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 640, screenHeightDp = 360)
        assertTrue(compactLandscape.isLandscape)
        assertTrue(compactLandscape.maxHeightDp.value <= 360f - 36f)
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
    fun `tftactics scraper parses pbe set 19 comps positions synergy items and html fallback`() {
        val scraper = com.example.data.network.TftacticsCompsScraper()
        val html = """
            <html>
              <body>
                <a href="/set-update/" class="set-update-banner"><div class="update-title">Set 19: Prismatic Tour is now live on TFTactics!</div></a>
                <button class="active dropdown-item" set="18">Set 18</button>
                <button class="dropdown-item" set="19">Set 19</button>
              </body>
            </html>
        """.trimIndent()

        val compsJson = """
            [
              {
                "name": "Elderwood Spellweavers",
                "set": [18],
                "tier": 2,
                "playstyle": "Fast 8",
                "characters": [
                  {"name": "Ornn", "position": "p3"},
                  {"name": "Ahri", "position": "p28", "items": ["Blue Buff"]},
                  {"name": "Lillia", "position": "p2"},
                  {"name": "Syndra", "position": "p27"}
                ]
              },
              {
                "name": "Ionic Spark Hitmakers",
                "set": [19],
                "tier": 2,
                "tier_up": true,
                "playstyle": "Fast 8",
                "description": "Level 8 early in Stage 4 to find 4 cost units.",
                "synergy": [
                  {"name": "Ionic Spark", "count": 5, "rank": "silver"},
                  {"name": "Frontman", "count": 4, "rank": "silver"},
                  {"name": "Jazz", "count": 2, "rank": "bronze"}
                ],
                "characters": [
                  {"name": "Elise", "position": "p2"},
                  {"name": "Yunara", "position": "p22", "level": 3},
                  {"name": "Jinx", "position": "p28", "items": ["Deathblade", "Guinsoo's Rageblade", "Infinity Edge"]},
                  {"name": "Shyvana", "position": "p6", "items": ["Warmog's Armor", "Gargoyle Stoneplate", "Sunfire Cape"]}
                ],
                "carrousel": [{"item": "Infinity Edge", "component": "B.F. Sword"}],
                "mid": ["Yunara", "Ziggs"],
                "replacements": [{"out": [], "in": ["Bard"]}]
              }
            ]
        """.trimIndent().padEnd(600, ' ')

        val champsJson = """
            [
              {"name": "Jinx", "set": [19], "cost": 4, "health": 700, "origin": ["Ionic Spark"], "type": ["Hitmaker"]},
              {"name": "Shyvana", "set": [19], "cost": 4, "health": 1100, "origin": ["Ionic Spark"], "type": ["Frontman"]},
              {"name": "Yunara", "set": [19], "cost": 1, "health": 500, "origin": ["Ionic Spark"], "type": ["Swiftstriker"]},
              {"name": "Elise", "set": [19], "cost": 3, "health": 850, "origin": ["Ionic Spark"], "type": ["Frontman"]}
            ]
        """.trimIndent().padEnd(600, ' ')

        val traitsJson = """
            [
              {"name": "Ionic Spark", "set": [19], "Class": false, "bonus": "Bonus stats"}
            ]
        """.trimIndent().padEnd(600, ' ')

        val fakeJsBundle = "var a=JSON.parse('${compsJson.replace("'", "\\'").replace("\n", "")}');" +
            "var b=JSON.parse('${champsJson.replace("'", "\\'").replace("\n", "")}');" +
            "var c=JSON.parse('${traitsJson.replace("'", "\\'").replace("\n", "")}');"

        val result = scraper.parseBundleAndHtml(
            html = html,
            jsBundle = fakeJsBundle,
            pbePatchVersion = "19.1-PBE",
            liveSetNumber = 18
        )

        assertEquals(19, result.pbeSetNumber)
        assertEquals("Set 19: Prismatic Tour (PBE)", result.detectedPbeSet)
        assertEquals(1, result.pbeComps.size)

        val comp = result.pbeComps.first()
        assertEquals("Ionic Spark Hitmakers", comp.title)
        assertEquals(TierRank.A, comp.tier)
        assertEquals(TftEnvironment.PBE, comp.environment)
        assertEquals(com.example.data.network.TftacticsCompsScraper.TFTACTICS_COMPS_URL, comp.sourceUrl)
        assertEquals("Jinx", comp.carryName)
        assertEquals(listOf("5 Ionic Spark", "4 Frontman", "2 Jazz"), comp.traits)
        assertTrue(comp.levelingGuide.contains("Early/Mid: Yunara, Ziggs"))
        assertTrue(comp.notes.contains("Carousel: Infinity Edge (B.F. Sword)"))

        val jinxHex = comp.hexPlacements.first { it.unitName == "Jinx" }
        assertEquals(3, jinxHex.row)
        assertEquals(6, jinxHex.col)
        assertEquals(4, jinxHex.cost)
        assertTrue(jinxHex.isCarry)

        val yunaraHex = comp.hexPlacements.first { it.unitName == "Yunara" }
        assertEquals(3, yunaraHex.stars)
        assertEquals(1, yunaraHex.cost)

        assertEquals(
            "https://sunderarmor.com/characters/Skin/19/Jinx.png",
            result.pbeChampionIcons["Jinx"]
        )
        assertEquals(
            "https://sunderarmor.com/icons/ionicspark.png",
            result.pbeTraitIcons["Ionic Spark"]
        )
    }
}

