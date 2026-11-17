package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TftEnvironment
import com.example.data.network.TftacticsCompsScraper
import com.example.data.network.TftipsPatchScraper
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.MetaRepository
import com.example.data.repository.OfflineMetaBundle
import com.example.data.repository.OfflineMetaCacheStore
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Tactician's Forge", appName)
    }

    @Test
    fun `offline cache store persists and reloads live and pbe catalog`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineMetaCacheStore(context)

        val seeded = store.readOrSeedBundle()
        assertTrue(store.hasPersistedCache())
        assertTrue(seeded.liveComps.isNotEmpty())
        assertTrue(seeded.pbeComps.isNotEmpty())
        assertTrue(seeded.liveItems.isNotEmpty())
        assertTrue(seeded.liveAugments.isNotEmpty())

        val updated = seeded.copy(
            liveVersionInfo = seeded.liveVersionInfo.copy(
                livePatchVersion = "18.8",
                pbePatchVersion = "18.9-PBE"
            )
        )
        assertTrue(store.writeBundle(updated))

        val reloaded = store.readOrSeedBundle()
        assertEquals("18.8", reloaded.liveVersionInfo.livePatchVersion)
        assertEquals("18.9-PBE", reloaded.liveVersionInfo.pbePatchVersion)
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
    fun `tftactics scraper parses live and pbe comps and enforces matching set per environment`() {
        val scraper = TftacticsCompsScraper()
        val liveHtml = """
            <html>
              <body>
                <h1>TFT Team Comps Tier List - Set 18: Enchanted Wilds</h1>
                <script id="__NEXT_DATA__" type="application/json">
                {
                  "props": {
                    "pageProps": {
                      "liveComps": [
                        {
                          "title": "Elderwood Arcanists",
                          "tier": "S",
                          "playstyle": "Fast 8",
                          "set": "Set 18",
                          "carry": "Ahri",
                          "units": ["Ornn", "Lillia", "Ahri", "Veigar", "Nami"],
                          "traits": ["6 Elderwood", "4 Arcanist"]
                        },
                        {
                          "title": "Stale Wrong Set Comp",
                          "tier": "A",
                          "playstyle": "Reroll",
                          "set": "Set 19",
                          "carry": "Lux",
                          "units": ["Lux", "Garen", "Jarvan", "Sona"]
                        }
                      ]
                    }
                  }
                }
                </script>
              </body>
            </html>
        """.trimIndent()

        val pbeHtml = """
            <html>
              <body>
                <h1>TFT PBE Team Comps - Set 19: Cyber Horizon PBE</h1>
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
        assertEquals("Elderwood Arcanists", alignedLive.first().title)
        assertEquals(TftacticsCompsScraper.TFTACTICS_TEAM_COMPS_URL, alignedLive.first().sourceUrl)

        assertEquals(19, pbeDoc.detectedSetNumber)
        assertEquals(1, alignedPbe.size)
        assertEquals("Cybernetic Vayne", alignedPbe.first().title)
        assertEquals(TftEnvironment.PBE, alignedPbe.first().environment)
        assertEquals(TftacticsCompsScraper.TFTACTICS_TEAM_COMPS_URL, alignedPbe.first().sourceUrl)
    }

    @Test
    fun `main viewmodel loads guest mode offline catalog immediately with zero firestore access`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        cacheStore.writeBundle(OfflineMetaBundle())
        val localStore = LocalPreferencesStore(context)
        val metaRepo = MetaRepository(cacheStore)

        val vm = MainViewModel(
            currentUserId = null,
            metaRepository = metaRepo,
            localStore = localStore,
            firestoreRepo = FirestoreUserRepository(context),
            autoSyncOnInit = false
        )

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
    fun `asset icon resolver resolves champion item and augment ddragon urls and updates from json`() {
        val resolver = com.example.ui.components.TftAssetIconResolver
        val initialVersion = resolver.ddragonVersion
        val initialRevision = resolver.catalogRevision

        val ahriUrl = resolver.resolveChampionIconUrl("Ahri")
        val tahmUrl = resolver.resolveChampionIconUrl("Tahm Kench")
        val kogUrl = resolver.resolveChampionIconUrl("Kog'Maw")
        val ornnUrl = resolver.resolveChampionIconUrl("TFT18_Ornn")

        assertTrue(ahriUrl?.endsWith("/img/champion/Ahri.png") == true)
        assertTrue(tahmUrl?.endsWith("/img/champion/TahmKench.png") == true)
        assertTrue(kogUrl?.endsWith("/img/champion/KogMaw.png") == true)
        assertTrue(ornnUrl?.endsWith("/img/champion/Ornn.png") == true)

        val blueBuffUrl = resolver.resolveItemIconUrl("Blue Buff")
        val nashorsUrl = resolver.resolveItemIconUrl("Nashor's Tooth")
        val redBuffUrl = resolver.resolveItemIconUrl("Red Buff")
        val edgeUrl = resolver.resolveItemIconUrl("Edge of Night")
        val apiItemUrl = resolver.resolveItemIconUrl("TFT18_Item_JeweledGauntlet")

        assertTrue(blueBuffUrl?.endsWith("/img/tft-item/TFT_Item_BlueBuff.png") == true)
        assertTrue(nashorsUrl?.endsWith("/img/tft-item/TFT_Item_Leviathan.png") == true)
        assertTrue(redBuffUrl?.endsWith("/img/tft-item/TFT_Item_RapidFireCannon.png") == true)
        assertTrue(edgeUrl?.endsWith("/img/tft-item/TFT_Item_GuardianAngel.png") == true)
        assertTrue(apiItemUrl?.endsWith("/img/tft-item/TFT_Item_JeweledGauntlet.png") == true)

        val levelUpUrl = resolver.resolveAugmentIconUrl("Level Up!")
        val lotusUrl = resolver.resolveAugmentIconUrl("Jeweled Lotus II")
        assertTrue(levelUpUrl?.endsWith("/img/tft-augment/TFT_Augment_LevelUp.png") == true)
        assertTrue(lotusUrl?.endsWith("/img/tft-augment/TFT_Augment_JeweledLotus2.png") == true)

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
    fun `tftactics scraper extracts all dom team comps across tiers without truncation`() {
        val scraper = TftacticsCompsScraper()
        val cardsHtml = (1..15).joinToString("\n") { idx ->
            val tierChar = when {
                idx <= 5 -> "S"
                idx <= 10 -> "A"
                idx <= 13 -> "B"
                else -> "C"
            }
            """
            <div class="team-portrait">
              <div class="team-info">
                <div class="team-rank">$tierChar</div>
                <div class="team-name">Comp Archetype $idx</div>
                <div class="team-playstyle">Fast 8</div>
              </div>
              <div class="team-characters">
                <img alt="Ornn" /><img alt="Ahri" /><img alt="Lillia" /><img alt="Syndra" /><img alt="Rakan" />
              </div>
            </div>
            """.trimIndent()
        }
        val html = "<html><body><h1>Set 18: Enchanted Wilds</h1>$cardsHtml</body></html>"
        val parsed = scraper.parseCompsDocument(html, TftEnvironment.LIVE, "18.6b", isDedicatedEnvPage = true)
        assertEquals(15, parsed.comps.size)
        assertTrue(parsed.comps.any { it.tier == com.example.data.model.TierRank.C })

        val seededLive = com.example.data.repository.MetaCatalogData.liveComps
        val seededPbe = com.example.data.repository.MetaCatalogData.pbeComps
        assertTrue(seededLive.size >= 14)
        assertTrue(seededPbe.size >= 10)
    }

    @Test
    fun `made by credits string and meta update notifier diff detection work accurately`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Made By JS96 + MK", context.getString(R.string.made_by_credits))

        val prefsStore = LocalPreferencesStore(context)
        val initialBundle = OfflineMetaBundle()
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
        val newComp = initialBundle.liveComps.first().copy(
            id = "live_new_test_comp_99",
            title = "Arcanist Lux Reroll"
        )
        val updatedBundle = initialBundle.copy(
            liveVersionInfo = initialBundle.liveVersionInfo.copy(
                livePatchVersion = "18.9",
                pbePatchVersion = "19.2-PBE"
            ),
            liveComps = listOf(newComp) + initialBundle.liveComps
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
    fun `tftactics scraper extracts nested below-the-fold comp arrays from js bundles`() {
        val scraper = TftacticsCompsScraper()
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
                  {"name": "Kalista", "items": ["Guinsoo's Rageblade", "Runaan's Hurricane"]}
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
        assertTrue(parsed.comps.any { it.title == "Shadow Isles Kalista" && it.carryName == "Kalista" })
        assertTrue(parsed.comps.any { it.title == "Deep Scroll Bastion Aphelios" && it.tier == com.example.data.model.TierRank.C })
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
}
