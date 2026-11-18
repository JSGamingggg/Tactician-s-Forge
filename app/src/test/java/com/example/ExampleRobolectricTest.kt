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
        assertEquals(TftAcademyCompsScraper.TFTACADEMY_COMPS_URL, alignedPbe.first().sourceUrl)
    }

    @Test
    fun `main viewmodel loads guest mode offline catalog asynchronously with zero firestore access`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        val academyLive = sampleTftAcademyComp("tftacademy_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val tftacticsPbe = sampleTftAcademyComp("tftactics_pbe_vayne", "Cyber Vayne", TftEnvironment.PBE, carry = "Vayne")
            .copy(sourceUrl = com.example.data.network.TftacticsCompsScraper.TFTACTICS_COMPS_URL)
        cacheStore.writeBundle(
            OfflineMetaBundle(
                liveComps = listOf(academyLive),
                pbeComps = listOf(tftacticsPbe)
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
    fun `overlay drawer dimensions adapt to full device width and enable side detail pane in landscape`() {
        val portraitViewport = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 411, screenHeightDp = 915)
        assertFalse(portraitViewport.isLandscape)
        assertFalse(portraitViewport.useSideDetailPane)
        assertEquals((411 - 16).dp, portraitViewport.widthDp)
        assertTrue(portraitViewport.widthDp > portraitViewport.maxHeightDp)
        assertTrue(portraitViewport.maxHeightDp.value < 915f)

        val landscapePhone = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 915, screenHeightDp = 411)
        assertTrue(landscapePhone.isLandscape)
        assertTrue(landscapePhone.useSideDetailPane)
        assertTrue(landscapePhone.useTwoColumnCards)
        assertEquals((915 - 16).dp, landscapePhone.widthDp)
        assertTrue(landscapePhone.widthDp > landscapePhone.maxHeightDp)
        assertTrue(landscapePhone.maxHeightDp.value <= 411f - 20f)

        val compactLandscape = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 640, screenHeightDp = 360)
        assertTrue(compactLandscape.isLandscape)
        assertTrue(compactLandscape.useSideDetailPane)
        assertEquals((640 - 16).dp, compactLandscape.widthDp)
        assertTrue(compactLandscape.widthDp > compactLandscape.maxHeightDp)
        assertTrue(compactLandscape.maxHeightDp.value <= 360f - 20f)

        val tabletLandscape = com.example.overlay.computeOverlayDrawerDimensions(screenWidthDp = 1280, screenHeightDp = 800)
        assertTrue(tabletLandscape.isLandscape)
        assertTrue(tabletLandscape.useSideDetailPane)
        assertEquals((1280 - 16).dp, tabletLandscape.widthDp)
        assertTrue(tabletLandscape.maxHeightDp <= 480.dp)
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
        assertEquals(4, result.pbeChampionTiers.size)
        assertEquals(TierRank.S, result.pbeChampionTiers.first { it.name == "Jinx" }.tier)
    }

    @Test
    fun `meta repository serves cached snapshots and tier lists when completely offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheStore = OfflineMetaCacheStore(context)
        val seeded = cacheStore.readOrSeedBundle()

        // Mutate and persist an updated cached snapshot as if synced online earlier
        val syncedComp = sampleTftAcademyComp(
            id = "tftacademy_live_cached_offline",
            title = "Cached Offline Live Comp",
            environment = TftEnvironment.LIVE
        )
        val syncedPbeComp = sampleTftAcademyComp(
            id = "tftactics_pbe_cached_offline",
            title = "Cached Offline PBE Comp",
            environment = TftEnvironment.PBE,
            carry = "Jinx"
        ).copy(sourceUrl = com.example.data.network.TftacticsCompsScraper.TFTACTICS_COMPS_URL)

        val persisted = seeded.copy(
            liveComps = listOf(syncedComp) + seeded.liveComps.take(5),
            pbeComps = listOf(syncedPbeComp) + seeded.pbeComps.take(4)
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
            tftacticsScraper = com.example.data.network.TftacticsCompsScraper(offlineClient),
            ddragonApi = offlineDdragonApi,
            publicClient = offlineClient
        )

        val syncResult = offlineRepo.refreshAndPersistMeta()
        assertFalse(syncResult.syncedFromNetwork)
        assertTrue(syncResult.cacheStatus.isOfflineFallback)

        // Fresh cold-store instance reading solely from local storage must preserve all comps, tier lists, and PBE catalogs
        val reloadedStore = OfflineMetaCacheStore(context)
        val reloaded = reloadedStore.readOrSeedBundle()
        assertTrue(reloaded.liveComps.any { it.id == "tftacademy_live_cached_offline" })
        assertTrue(reloaded.pbeComps.any { it.id == "tftactics_pbe_cached_offline" })
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
}
