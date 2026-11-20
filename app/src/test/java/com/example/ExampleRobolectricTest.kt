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
        // Preloaded real TFTAcademy Set 18 LIVE (35) & empty cold-start PBE until TFTAcademy Set 19 sync
        assertEquals(35, seeded.liveComps.size)
        assertTrue(seeded.liveComps.all { it.sourceUrl == TftAcademyCompsScraper.TFTACADEMY_COMPS_URL })
        assertTrue(seeded.liveComps.any { it.tier == TierRank.S })
        assertTrue(seeded.liveComps.any { it.tier == TierRank.X })
        assertTrue(seeded.liveComps.all { it.traits.isNotEmpty() })
        assertTrue(seeded.pbeComps.isEmpty())
        assertEquals(35, seeded.toCacheStatus(environment = TftEnvironment.LIVE).compsCount)
        assertEquals(0, seeded.toCacheStatus(environment = TftEnvironment.PBE).compsCount)

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
        val academyPbe = sampleTftAcademyComp("tftacademy_pbe_ionic_spark_hitmakers", "Ionic Spark Hitmakers", TftEnvironment.PBE, carry = "Jinx")
            .copy(sourceUrl = TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL)
        val legacyComp = academyLive.copy(id = "legacy_comp", sourceUrl = "https://legacy.example/comps")
        val legacyPbeComp = academyPbe.copy(id = "legacy_tftactics_pbe", sourceUrl = "https://tftactics.gg/tierlist/team-comps/")

        val updated = seeded.copy(
            liveVersionInfo = seeded.liveVersionInfo.copy(
                livePatchVersion = "18.8",
                pbePatchVersion = "19.1-PBE"
            ),
            liveComps = listOf(academyLive, legacyComp),
            pbeComps = listOf(academyPbe, legacyPbeComp)
        )
        assertTrue(store.writeBundle(updated))

        val reloaded = store.readOrSeedBundle()
        assertEquals("18.8", reloaded.liveVersionInfo.livePatchVersion)
        assertEquals("19.1-PBE", reloaded.liveVersionInfo.pbePatchVersion)
        // Non-TFTAcademy live & legacy PBE comps are stripped automatically
        assertEquals(listOf("tftacademy_live_ahri"), reloaded.liveComps.map { it.id })
        assertEquals(listOf("tftacademy_pbe_ionic_spark_hitmakers"), reloaded.pbeComps.map { it.id })
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
        val academyLive = sampleTftAcademyComp("tftacademy_live_ahri", "Elderwood Ahri", TftEnvironment.LIVE)
        val academyPbe = sampleTftAcademyComp("tftacademy_pbe_vayne", "Cyber Vayne", TftEnvironment.PBE, carry = "Vayne")
            .copy(sourceUrl = TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL)
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

        // Mutate and persist an updated cached snapshot as if synced online earlier
        val syncedComp = sampleTftAcademyComp(
            id = "tftacademy_live_cached_offline",
            title = "Cached Offline Live Comp",
            environment = TftEnvironment.LIVE
        )
        val syncedPbeComp = sampleTftAcademyComp(
            id = "tftacademy_pbe_cached_offline",
            title = "Cached Offline PBE Comp",
            environment = TftEnvironment.PBE,
            carry = "Jinx"
        ).copy(sourceUrl = TftAcademyCompsScraper.TFTACADEMY_PBE_COMPS_URL)

        val persisted = seeded.copy(
            liveComps = listOf(syncedComp) + seeded.liveComps.take(5),
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
        assertTrue(reloaded.pbeComps.any { it.id == "tftacademy_pbe_cached_offline" })
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
        val bundle = OfflineMetaCacheStore(context).readOrSeedBundle()

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
        // 1280=500, 1281=501, 1282=502
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
        assertEquals(validV02Set19, academyResolved.code)
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
        assertEquals(exactSiteCode, encodedFromJs.code)

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
        assertEquals(exactSiteCode, scrapedHyperpop.teamPlannerCode)
        assertEquals(
            "https://tftacademy.com/tierlist/comps/set19/set19-hyperpop-reroll",
            scrapedHyperpop.sourceUrl
        )
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
}


