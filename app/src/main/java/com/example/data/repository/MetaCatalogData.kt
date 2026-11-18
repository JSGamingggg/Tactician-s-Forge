package com.example.data.repository

import com.example.data.model.AugmentTier
import com.example.data.model.AugmentTierEntry
import com.example.data.model.BalanceChangeEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.DeltaType
import com.example.data.model.ItemCategory
import com.example.data.model.ItemTierEntry
import com.example.data.model.MetaComp
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank

object MetaCatalogData {

    /**
     * All legacy pre-seeded compositions have been removed.
     * Team compositions are fetched exclusively from https://tftacademy.com/tierlist/comps.
     */
    val liveComps: List<MetaComp> = emptyList()

    val pbeComps: List<MetaComp> = emptyList()

    val liveChampions: List<ChampionTierEntry> = listOf(
        ChampionTierEntry("live_ahri", "Ahri", 4, TierRank.S, TftEnvironment.LIVE, listOf("Elderwood", "Arcanist"), "AP Carry", 3.42, 66.8, listOf("Blue Buff", "Jeweled Gauntlet", "Nashor's Tooth"), listOf("Elderwood Arcanists"), DeltaType.BUFF, "18.6: Orb return damage +15%"),
        ChampionTierEntry("live_ornn", "Ornn", 4, TierRank.S, TftEnvironment.LIVE, listOf("Elderwood", "Bastion"), "Main Tank", 3.51, 64.9, listOf("Warmog's Armor", "Gargoyle Stoneplate", "Dragon's Claw"), listOf("Elderwood Arcanists", "Curios Wisp Flex"), DeltaType.BUFF, "18.6: Base armor/MR 55 -> 60"),
        ChampionTierEntry("live_jhin", "Jhin", 4, TierRank.S, TftEnvironment.LIVE, listOf("Blossom", "Sniper"), "AD Carry", 3.58, 63.4, listOf("Infinity Edge", "Last Whisper", "Giant Slayer"), listOf("Blossom Vanguard Marksman"), DeltaType.BUFF, "18.6: Blossom AD bonus +6%"),
        ChampionTierEntry("live_tristana", "Tristana", 2, TierRank.S, TftEnvironment.LIVE, listOf("Sprykin", "Artillery"), "Reroll Carry", 3.71, 60.2, listOf("Guinsoo's Rageblade", "Infinity Edge", "Last Whisper"), listOf("Sprykin Tristana Reroll"), DeltaType.ADJUST, "18.6b: Jump shield adjusted"),
        ChampionTierEntry("live_zilean", "Zilean", 5, TierRank.A, TftEnvironment.LIVE, listOf("Wisp", "Chrono", "Mystic"), "Utility Carry", 3.85, 58.0, listOf("Spear of Shojin", "Archangel's Staff", "Guinsoo's Rageblade"), listOf("Curios Wisp Flex"), DeltaType.BUFF, "18.6: Bomb stun +0.25s"),
        ChampionTierEntry("live_shen", "Shen", 4, TierRank.A, TftEnvironment.LIVE, listOf("Blossom", "Vanguard"), "Frontline Tank", 3.88, 57.6, listOf("Steadfast Heart", "Bramble Vest", "Redemption"), listOf("Blossom Vanguard Marksman"), null, ""),
        ChampionTierEntry("live_briar", "Briar", 5, TierRank.A, TftEnvironment.LIVE, listOf("Thornbound", "Slayer"), "Melee Fighter", 3.95, 55.9, listOf("Bloodthirster", "Titan's Resolve", "Sterak's Gage"), listOf("Briar Thornbound Slayers"), DeltaType.BUFF, "18.6: Frenzy omnivamp 18% -> 22%"),
        ChampionTierEntry("live_amumu", "Amumu", 3, TierRank.A, TftEnvironment.LIVE, listOf("Sprykin", "Brawler"), "3★ Tank", 3.92, 56.4, listOf("Warmog's Armor", "Bramble Vest", "Dragon's Claw"), listOf("Sprykin Tristana Reroll"), null, ""),
        ChampionTierEntry("live_syndra", "Syndra", 3, TierRank.B, TftEnvironment.LIVE, listOf("Elderwood", "Arcanist"), "Secondary AP", 4.12, 52.3, listOf("Statikk Shiv", "Morellonomicon", "Spear of Shojin"), listOf("Elderwood Arcanists"), null, ""),
        ChampionTierEntry("live_kogmaw", "Kog'Maw", 3, TierRank.B, TftEnvironment.LIVE, listOf("Enchanted", "Sniper"), "Hybrid Carry", 4.24, 50.1, listOf("Blue Buff", "Nashor's Tooth", "Guinsoo's Rageblade"), listOf("Mystic Bard & Kog'Maw"), DeltaType.NERF, "18.6: 3★ spell scaling -10%"),
        ChampionTierEntry("live_poppy", "Poppy", 1, TierRank.B, TftEnvironment.LIVE, listOf("Blossom", "Vanguard"), "Early Tank", 4.28, 49.5, listOf("Warmog's Armor", "Sunfire Cape"), listOf("Blossom Vanguard Marksman"), null, ""),
        ChampionTierEntry("live_corki", "Corki", 1, TierRank.C, TftEnvironment.LIVE, listOf("Sprykin", "Artillery"), "Item Holder", 4.55, 44.2, listOf("Infinity Edge", "Last Whisper"), listOf("Sprykin Tristana Reroll"), null, "")
    )

    val pbeChampions: List<ChampionTierEntry> = listOf(
        ChampionTierEntry("pbe_xerath", "Xerath", 5, TierRank.S, TftEnvironment.PBE, listOf("Grove", "Ascendant"), "5-Cost Carry", 3.18, 71.2, listOf("Spear of Shojin", "Jeweled Gauntlet", "Rabadon's Deathcap"), listOf("Grove Ascendant Xerath"), DeltaType.BUFF, "18.7 PBE: True dmg +20%, mana 120 -> 105"),
        ChampionTierEntry("pbe_akali", "Akali", 4, TierRank.S, TftEnvironment.PBE, listOf("Nightshade", "Slayer"), "Backline Diver", 3.49, 64.9, listOf("Hand of Justice", "Jeweled Gauntlet", "Edge of Night"), listOf("Nightshade Akali Blitz"), DeltaType.BUFF, "18.7 PBE: Dash animation 0.25s faster"),
        ChampionTierEntry("pbe_ornn", "Ornn", 4, TierRank.S, TftEnvironment.PBE, listOf("Elderwood", "Bastion"), "Core Frontline", 3.55, 63.8, listOf("Warmog's Armor", "Protector's Vow", "Dragon's Claw"), listOf("Grove Ascendant Xerath"), null, ""),
        ChampionTierEntry("pbe_ziggs", "Ziggs", 1, TierRank.A, TftEnvironment.PBE, listOf("Starlight", "Arcanist"), "1-Cost Reroll", 3.79, 59.0, listOf("Spear of Shojin", "Nashor's Tooth", "Archangel's Staff"), listOf("Starlight Ziggs Hyper"), DeltaType.BUFF, "18.7 PBE: Starlight mana refund +8"),
        ChampionTierEntry("pbe_karma", "Karma", 4, TierRank.A, TftEnvironment.PBE, listOf("Grove", "Chrono"), "AP Support", 3.81, 58.6, listOf("Blue Buff", "Morellonomicon"), listOf("Grove Ascendant Xerath"), DeltaType.NEW, "18.7 PBE: Added Grove trait tag"),
        ChampionTierEntry("pbe_diana", "Diana", 4, TierRank.A, TftEnvironment.PBE, listOf("Nightshade", "Bastion"), "AP Bruiser", 3.84, 57.9, listOf("Ionic Spark", "Crownguard", "Bloodthirster"), listOf("Nightshade Akali Blitz"), DeltaType.BUFF, "18.7 PBE: Shield ratio +12% AP"),
        ChampionTierEntry("pbe_jhin", "Jhin", 4, TierRank.B, TftEnvironment.PBE, listOf("Blossom", "Sniper"), "AD Carry", 4.18, 51.2, listOf("Infinity Edge", "Last Whisper", "Red Buff"), listOf("Blossom Tempo (PBE Adjusted)"), DeltaType.NERF, "18.7 PBE: 4th shot AD 380% -> 340%"),
        ChampionTierEntry("pbe_tristana", "Tristana", 2, TierRank.B, TftEnvironment.PBE, listOf("Sprykin", "Artillery"), "Reroll Carry", 4.25, 49.8, listOf("Guinsoo's Rageblade", "Infinity Edge"), listOf("Sprykin Reroll"), DeltaType.NERF, "18.7 PBE: 3★ Attack Speed bonus nerfed")
    )

    val livePatches: List<PatchUpdateRecord> = listOf(
        PatchUpdateRecord(
            id = "tftips_live_18_7",
            environment = TftEnvironment.LIVE,
            version = "18.7",
            title = "TFT Patch 18.7 Changes",
            releaseDate = "Latest • Wed, 11/18",
            isMajorPatch = true,
            headline = "20 Buffs, 11 Nerfs, 2 Adjustments — Aphelios, Elise, Kha'Zix, Krug, Deepwood Protector, Elderwood",
            highlights = listOf(
                "Buffs (20): Aphelios, Elise, Kha'Zix, Kayle, Kobuko, Cinderling, Scuttlecrab, Teemo, Leona, Warwick +10 more",
                "Nerfs (11): Krug, Deepwood Protector, Elderwood, Sprykin, Riftbeast, Fae, We Stick Together, Focused Fire +3 more",
                "Modified (2): Ivern (Trait Changes: +Elderwood), Kennen (Trait Changes: +Sprykin)"
            ),
            balanceChanges = listOf(
                BalanceChangeEntry("tftips_bc_live_18_7_1", "Aphelios", "Champion", DeltaType.BUFF, "Blast Damage: 490/735 → 540/810", "490/735", "540/810"),
                BalanceChangeEntry("tftips_bc_live_18_7_2", "Elise", "Champion", DeltaType.BUFF, "Spider Form bonus magic damage: 35/50/80 → 40/60/90", "35/50/80", "40/60/90"),
                BalanceChangeEntry("tftips_bc_live_18_7_3", "Kha'Zix", "Champion", DeltaType.BUFF, "Leap magic damage: 230/310/450 → 240/325/480 | Isolated: 250/350/530 → 260/360/540", "230/310/450", "240/325/480"),
                BalanceChangeEntry("tftips_bc_live_18_7_4", "Kayle", "Champion", DeltaType.BUFF, "1st Ascension bonus magic damage: 65/97/110 → 68/102/115", "65/97/110", "68/102/115"),
                BalanceChangeEntry("tftips_bc_live_18_7_5", "Kobuko", "Champion", DeltaType.BUFF, "Bash magic damage: 140/231/377 → 185/309/520", "140/231/377", "185/309/520"),
                BalanceChangeEntry("tftips_bc_live_18_7_6", "Cinderling", "Champion", DeltaType.BUFF, "Leaves physical damage: 340/510/770 → 360/540/815", "340/510/770", "360/540/815"),
                BalanceChangeEntry("tftips_bc_live_18_7_7", "Scuttlecrab", "Champion", DeltaType.BUFF, "AD & Dance damage: 50/75/113 → 60/90/135", "50/75/113", "60/90/135"),
                BalanceChangeEntry("tftips_bc_live_18_7_8", "Teemo", "Champion", DeltaType.BUFF, "Forage trigger chance: 10%/12%/15% → 12%/15%/18%", "10%/12%/15%", "12%/15%/18%"),
                BalanceChangeEntry("tftips_bc_live_18_7_9", "Leona", "Champion", DeltaType.BUFF, "Active magic damage: 60/108/162 → 72/120/174", "60/108/162", "72/120/174"),
                BalanceChangeEntry("tftips_bc_live_18_7_10", "Warwick", "Champion", DeltaType.BUFF, "3★ Bite physical damage: 225/335/535 → 225/335/560", "225/335/535", "225/335/560"),
                BalanceChangeEntry("tftips_bc_live_18_7_11", "Pebbles", "Champion", DeltaType.BUFF, "Laser magic damage: 160/240/360 → 170/255/385", "160/240/360", "170/255/385"),
                BalanceChangeEntry("tftips_bc_live_18_7_12", "Inferno", "Trait", DeltaType.BUFF, "(5)/(7) Burn max Health: 3%/4% → 4%/5%", "3% / 4%", "4% / 5%"),
                BalanceChangeEntry("tftips_bc_live_18_7_13", "Eclipse", "Trait", DeltaType.BUFF, "Execute start: 8s → 9s | Repeat interval: 3.5s → 3s", "3.5s", "3s"),
                BalanceChangeEntry("tftips_bc_live_18_7_14", "Summoner", "Trait", DeltaType.BUFF, "Summon bonus: +Krugs (+15% Health)", "Base", "+15% Health"),
                BalanceChangeEntry("tftips_bc_live_18_7_15", "Coven", "Trait", DeltaType.BUFF, "Leader bonus AP: 60 → 70", "60", "70"),
                BalanceChangeEntry("tftips_bc_live_18_7_16", "Pandora's Items II++", "Augment", DeltaType.BUFF, "Gold granted: 6 → 7", "6", "7"),
                BalanceChangeEntry("tftips_bc_live_18_7_17", "Comeback Story", "Augment", DeltaType.BUFF, "Bonus per missing player HP: 0.3% → 0.4%", "0.3%", "0.4%"),
                BalanceChangeEntry("tftips_bc_live_18_7_18", "Krug", "Champion", DeltaType.NERF, "Kruglette Health: 182/338/608 → 182/313/578", "182/338/608", "182/313/578"),
                BalanceChangeEntry("tftips_bc_live_18_7_19", "Deepwood Protector", "Champion", DeltaType.NERF, "Health: 450/810/1458 → 375/675/1215 | Passive heal: 54/97/175 → 45/81/145.8", "450/810/1458", "375/675/1215"),
                BalanceChangeEntry("tftips_bc_live_18_7_20", "Elderwood", "Trait", DeltaType.NERF, "Stonebark Tree bonus: 200 → 125", "200", "125"),
                BalanceChangeEntry("tftips_bc_live_18_7_21", "Sprykin", "Trait", DeltaType.NERF, "Bonus scaling: 5% → 4%", "5%", "4%"),
                BalanceChangeEntry("tftips_bc_live_18_7_22", "Riftbeast", "Trait", DeltaType.NERF, "Bonus scaling: 5% → 4%", "5%", "4%"),
                BalanceChangeEntry("tftips_bc_live_18_7_23", "We Stick Together", "Augment", DeltaType.NERF, "Shield & bonus value: 30% → 25%", "30%", "25%"),
                BalanceChangeEntry("tftips_bc_live_18_7_24", "Ivern", "Champion", DeltaType.ADJUST, "Trait Changes: +Elderwood", "Traits", "+Elderwood"),
                BalanceChangeEntry("tftips_bc_live_18_7_25", "Kennen", "Champion", DeltaType.ADJUST, "Trait Changes: +Sprykin", "Traits", "+Sprykin")
            ),
            trendingCompIds = emptyList(),
            sourceUrl = "https://tftips.app/en/patches/18.7"
        ),
        PatchUpdateRecord(
            id = "tftips_live_18_6",
            environment = TftEnvironment.LIVE,
            version = "18.6",
            title = "TFT Patch 18.6 Changes",
            releaseDate = "Wed, 11/4",
            isMajorPatch = true,
            headline = "10 Buffs, 3 Nerfs — Kobuko, Nidalee, Murkwolf, Malphite, Rek'Sai, Sentinel, Blossom",
            highlights = listOf(
                "Buffs (10): Kobuko, Nidalee (AD Form), Nidalee (AP Form), Murkwolf, Malphite, Rek'Sai, Sentinel, Blossom, Clockwork Accelerator +2 more",
                "Nerfs (3): Gromp (AD Form), Elderwood, Embiggen"
            ),
            balanceChanges = listOf(
                BalanceChangeEntry("tftips_bc_live_18_6_1", "Kobuko", "Champion", DeltaType.BUFF, "Active shield: 325/400/500 → 350/435/550", "325/400/500", "350/435/550"),
                BalanceChangeEntry("tftips_bc_live_18_6_2", "Nidalee (AD Form)", "Champion", DeltaType.BUFF, "Swipe physical damage: 220/330/500 → 240/360/550", "220/330/500", "240/360/550"),
                BalanceChangeEntry("tftips_bc_live_18_6_3", "Nidalee (AP Form)", "Champion", DeltaType.BUFF, "Javelin magic damage: 260/390/600 → 285/430/660", "260/390/600", "285/430/660"),
                BalanceChangeEntry("tftips_bc_live_18_6_4", "Murkwolf", "Champion", DeltaType.BUFF, "Pack strike damage buffed in Patch 18.6", "180/270/405", "200/300/450"),
                BalanceChangeEntry("tftips_bc_live_18_6_5", "Malphite", "Champion", DeltaType.BUFF, "Base Armor & Slam scaling buffed", "45", "55"),
                BalanceChangeEntry("tftips_bc_live_18_6_6", "Rek'Sai", "Champion", DeltaType.BUFF, "Furious Bite true damage buffed", "210/315/475", "235/350/525"),
                BalanceChangeEntry("tftips_bc_live_18_6_7", "Blossom", "Trait", DeltaType.BUFF, "Petal healing & bonus scaling increased", "14%", "18%"),
                BalanceChangeEntry("tftips_bc_live_18_6_8", "Gromp (AD Form)", "Champion", DeltaType.NERF, "Tongue Lash AD scaling reduced", "280%", "255%"),
                BalanceChangeEntry("tftips_bc_live_18_6_9", "Elderwood", "Trait", DeltaType.NERF, "Resistances per stack reduced", "12", "10")
            ),
            trendingCompIds = emptyList(),
            sourceUrl = "https://tftips.app/en/patches/18.6"
        ),
        PatchUpdateRecord(
            id = "tftips_live_18_5c",
            environment = TftEnvironment.LIVE,
            version = "18.5c",
            title = "TFT Patch 18.5c Changes",
            releaseDate = "Micro patch • Wed, 10/28",
            isMajorPatch = false,
            headline = "4 Nerfs — Kha'Zix, Brambleback, Hand Of Justice, Radiant Hand of Justice",
            highlights = listOf(
                "Nerfs (4): Kha'Zix, Brambleback, Hand Of Justice, Radiant Hand of Justice"
            ),
            balanceChanges = listOf(
                BalanceChangeEntry("tftips_bc_live_18_5c_1", "Kha'Zix", "Champion", DeltaType.NERF, "Isolated Leap scaling toned down in 18.5c micro-patch", "275/380/560", "250/350/530"),
                BalanceChangeEntry("tftips_bc_live_18_5c_2", "Brambleback", "Champion", DeltaType.NERF, "Cinder Volley burn damage reduced", "18%", "15%"),
                BalanceChangeEntry("tftips_bc_live_18_5c_3", "Hand Of Justice", "Item", DeltaType.NERF, "Base AD/AP & Omnivamp roll toned down", "15% / 30%", "12% / 24%"),
                BalanceChangeEntry("tftips_bc_live_18_5c_4", "Radiant Hand of Justice", "Item", DeltaType.NERF, "Radiant Omnivamp & AD/AP reduced", "50%", "42%")
            ),
            trendingCompIds = emptyList(),
            sourceUrl = "https://tftips.app/en/patches/18.5c"
        )
    )

    val pbePatches: List<PatchUpdateRecord> = listOf(
        PatchUpdateRecord(
            id = "tftips_pbe_19_1_PBE",
            environment = TftEnvironment.PBE,
            version = "19.1-PBE",
            title = "TFT Patch 19.1 (PBE) Changes — Set 18 → Set 19 Changes",
            releaseDate = "PBE Cycle • Mon, 11/23",
            isMajorPatch = true,
            headline = "7 Buffs, 7 Nerfs, 7 Adjustments — Ionic Spark, Titan's Resolve, Blue Buff, Sterak's Gage, Bloodthirster, Crownguard",
            highlights = listOf(
                "Buffs (7): Ionic Spark, Titan's Resolve, Radiant Deathblade, Radiant Ionic Spark, Radiant Rabadon's Deathcap, Radiant Titan's Resolve, One Twos Three",
                "Nerfs (7): Blue Buff, Sterak's Gage, Juggernaut Emblem, Radiant Sterak's Gage, Blighting Jewel, Swordsmith, Staffsmith",
                "Adjustments (7): Bloodthirster, Crownguard, Deathblade, Rabadon's Deathcap, Prismatic Crownguard, Radiant Bloodthirster, Radiant Blue Buff"
            ),
            balanceChanges = listOf(
                BalanceChangeEntry("tftips_bc_pbe_19_1_1", "Ionic Spark", "Item", DeltaType.BUFF, "Health: 250 → 350", "250", "350"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_2", "Titan's Resolve", "Item", DeltaType.BUFF, "At max stacks, gain +15% Damage Amp (was 10%): 10% → 15%", "10%", "15%"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_3", "Radiant Deathblade", "Item", DeltaType.BUFF, "Passive: +30% Bonus Attack Damage", "Base", "+30% Bonus AD"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_4", "Radiant Ionic Spark", "Item", DeltaType.BUFF, "Health: 500 → 700", "500", "700"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_5", "Radiant Rabadon's Deathcap", "Item", DeltaType.BUFF, "Ability Power: 100 → 115 | Passive: +30% Bonus AP", "100", "115"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_6", "Radiant Titan's Resolve", "Item", DeltaType.BUFF, "At max stacks, gain +30% Damage Amp (was 20%): 20% → 30%", "20%", "30%"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_7", "One Twos Three", "Augment", DeltaType.BUFF, "Augment gold & unit grant buffed in Set 19 PBE", "2x 2-cost", "3x 2-cost"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_8", "Blue Buff", "Item", DeltaType.NERF, "Mana Regen: 5 → 6 | +10% Bonus AD & AP removed", "+10% AD/AP", "6 Mana Regen"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_9", "Sterak's Gage", "Item", DeltaType.NERF, "Shield trigger threshold: At 60% Health → At 40% Health", "60%", "40%"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_10", "Blighting Jewel", "Item", DeltaType.NERF, "Magic damage Mana grant on 0 MR target: 4 → 2", "4", "2"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_11", "Bloodthirster", "Item", DeltaType.ADJUST, "Omnivamp: 18% → 15% | Shield triggers at 60% Health (was 40%)", "18%", "15%"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_12", "Crownguard", "Item", DeltaType.ADJUST, "Ability Power: 20 → 10 | Combat start Shield: 25% → 20% max Health", "25%", "20%"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_13", "Deathblade", "Item", DeltaType.ADJUST, "Attack Damage reworked with +15% Bonus Attack Damage passive", "55% AD", "+15% Bonus AD"),
                BalanceChangeEntry("tftips_bc_pbe_19_1_14", "Rabadon's Deathcap", "Item", DeltaType.ADJUST, "Ability Power reworked with +15% Bonus Ability Power passive", "50 AP", "+15% Bonus AP")
            ),
            trendingCompIds = emptyList(),
            sourceUrl = "https://tftips.app/en/patches/pbe"
        )
    )

    val liveItems: List<ItemTierEntry> = listOf(
        ItemTierEntry(
            id = "live_item_blue_buff",
            name = "Blue Buff",
            tier = TierRank.S,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.AP,
            components = listOf("Tear of the Goddess", "Tear of the Goddess"),
            statsSummary = "+20 Mana, +20 AP, -10 Max Mana & +8% Dmg on Takedown",
            avgPlacement = 3.54,
            top4Rate = 63.8,
            bestHolders = listOf("Ahri", "Kog'Maw", "Syndra"),
            patchDelta = DeltaType.BUFF,
            patchNote = "18.6: Takedown bonus damage window extended to 8s"
        ),
        ItemTierEntry(
            id = "live_item_jeweled_gauntlet",
            name = "Jeweled Gauntlet",
            tier = TierRank.S,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.AP,
            components = listOf("Needlessly Large Rod", "Sparring Gloves"),
            statsSummary = "+35 AP, +35% Crit Chance, Ability Can Crit",
            avgPlacement = 3.59,
            top4Rate = 62.5,
            bestHolders = listOf("Ahri", "Veigar", "Gwen")
        ),
        ItemTierEntry(
            id = "live_item_warmogs",
            name = "Warmog's Armor",
            tier = TierRank.S,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.TANK,
            components = listOf("Giant's Belt", "Giant's Belt"),
            statsSummary = "+600 Bonus HP, +12% Max Health",
            avgPlacement = 3.62,
            top4Rate = 61.9,
            bestHolders = listOf("Ornn", "Shen", "Amumu")
        ),
        ItemTierEntry(
            id = "live_item_infinity_edge",
            name = "Infinity Edge",
            tier = TierRank.S,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.AD,
            components = listOf("B.F. Sword", "Sparring Gloves"),
            statsSummary = "+35% AD, +35% Crit Chance, Ability Can Crit",
            avgPlacement = 3.66,
            top4Rate = 61.1,
            bestHolders = listOf("Jhin", "Tristana", "Corki")
        ),
        ItemTierEntry(
            id = "live_item_guinsoos",
            name = "Guinsoo's Rageblade",
            tier = TierRank.A,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.ATTACK_SPEED,
            components = listOf("Recurve Bow", "Needlessly Large Rod"),
            statsSummary = "+10 AP, +15% AS, +5% Stacking AS per attack",
            avgPlacement = 3.81,
            top4Rate = 58.4,
            bestHolders = listOf("Tristana", "Zilean", "Kog'Maw", "Bard")
        ),
        ItemTierEntry(
            id = "live_item_shojin",
            name = "Spear of Shojin",
            tier = TierRank.A,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.AP,
            components = listOf("B.F. Sword", "Tear of the Goddess"),
            statsSummary = "+20% AD, +20 AP, +15 Mana, +5 Mana per attack",
            avgPlacement = 3.84,
            top4Rate = 57.9,
            bestHolders = listOf("Zilean", "Veigar", "Syndra")
        ),
        ItemTierEntry(
            id = "live_item_gargoyle",
            name = "Gargoyle Stoneplate",
            tier = TierRank.A,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.TANK,
            components = listOf("Chain Vest", "Negatron Cloak"),
            statsSummary = "+30 Armor, +30 MR, +10 Armor/MR per targeting enemy",
            avgPlacement = 3.86,
            top4Rate = 57.5,
            bestHolders = listOf("Ornn", "Neeko", "Shen")
        ),
        ItemTierEntry(
            id = "live_item_nashors",
            name = "Nashor's Tooth",
            tier = TierRank.A,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.ATTACK_SPEED,
            components = listOf("Recurve Bow", "Giant's Belt"),
            statsSummary = "+150 HP, +15% AS, +40% AS for 5s after cast",
            avgPlacement = 3.89,
            top4Rate = 56.8,
            bestHolders = listOf("Ahri", "Kog'Maw")
        ),
        ItemTierEntry(
            id = "live_item_last_whisper",
            name = "Last Whisper",
            tier = TierRank.A,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.AD,
            components = listOf("Recurve Bow", "Sparring Gloves"),
            statsSummary = "+20% AS, +20% Crit, 30% Sunder on physical dmg",
            avgPlacement = 3.91,
            top4Rate = 56.2,
            bestHolders = listOf("Jhin", "Tristana", "Ashe")
        ),
        ItemTierEntry(
            id = "live_item_bloodthirster",
            name = "Bloodthirster",
            tier = TierRank.B,
            environment = TftEnvironment.LIVE,
            category = ItemCategory.FIGHTER,
            components = listOf("B.F. Sword", "Negatron Cloak"),
            statsSummary = "+20% Omnivamp, +25% Max HP Shield below 40% HP",
            avgPlacement = 4.08,
            top4Rate = 53.1,
            bestHolders = listOf("Briar", "Aatrox", "Kayn")
        )
    )

    val pbeItems: List<ItemTierEntry> = listOf(
        ItemTierEntry(
            id = "pbe_item_shojin",
            name = "Spear of Shojin",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            category = ItemCategory.AP,
            components = listOf("B.F. Sword", "Tear of the Goddess"),
            statsSummary = "+20% AD, +25 AP, +15 Mana, +5 Mana per attack",
            avgPlacement = 3.29,
            top4Rate = 68.7,
            bestHolders = listOf("Xerath", "Ziggs", "Zilean"),
            patchDelta = DeltaType.BUFF,
            patchNote = "18.7 PBE: Bonus AP raised 20 -> 25"
        ),
        ItemTierEntry(
            id = "pbe_item_jeweled_gauntlet",
            name = "Jeweled Gauntlet",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            category = ItemCategory.AP,
            components = listOf("Needlessly Large Rod", "Sparring Gloves"),
            statsSummary = "+35 AP, +35% Crit Chance, Ability Can Crit",
            avgPlacement = 3.41,
            top4Rate = 65.4,
            bestHolders = listOf("Xerath", "Akali")
        ),
        ItemTierEntry(
            id = "pbe_item_edge_of_night",
            name = "Edge of Night",
            tier = TierRank.S,
            environment = TftEnvironment.PBE,
            category = ItemCategory.FIGHTER,
            components = listOf("B.F. Sword", "Chain Vest"),
            statsSummary = "+10% AD, +20 Armor, Stealth & Aggro Drop at 60% HP",
            avgPlacement = 3.52,
            top4Rate = 63.9,
            bestHolders = listOf("Akali", "Kayn"),
            patchDelta = DeltaType.BUFF,
            patchNote = "18.7 PBE: Grants +15% Attack Speed on stealth exit"
        ),
        ItemTierEntry(
            id = "pbe_item_hoj",
            name = "Hand of Justice",
            tier = TierRank.A,
            environment = TftEnvironment.PBE,
            category = ItemCategory.FIGHTER,
            components = listOf("Tear of the Goddess", "Sparring Gloves"),
            statsSummary = "+15% AD/AP & +15% Omnivamp (doubled dynamically)",
            avgPlacement = 3.68,
            top4Rate = 60.4,
            bestHolders = listOf("Akali", "Kayn", "Diana")
        ),
        ItemTierEntry(
            id = "pbe_item_protectors_vow",
            name = "Protector's Vow",
            tier = TierRank.A,
            environment = TftEnvironment.PBE,
            category = ItemCategory.TANK,
            components = listOf("Chain Vest", "Tear of the Goddess"),
            statsSummary = "+30 Starting Mana, 25% Max HP Shield + 20 Armor/MR at 40% HP",
            avgPlacement = 3.74,
            top4Rate = 59.1,
            bestHolders = listOf("Ornn", "Shen")
        ),
        ItemTierEntry(
            id = "pbe_item_red_buff",
            name = "Red Buff",
            tier = TierRank.B,
            environment = TftEnvironment.PBE,
            category = ItemCategory.ATTACK_SPEED,
            components = listOf("Recurve Bow", "Recurve Bow"),
            statsSummary = "+40% AS, +6% Bonus Dmg, 1% Burn & 33% Wound",
            avgPlacement = 4.11,
            top4Rate = 52.3,
            bestHolders = listOf("Jhin", "Ashe", "Tristana"),
            patchDelta = DeltaType.NERF,
            patchNote = "18.7 PBE: Attack Speed 45% -> 40%"
        )
    )

    val liveAugments: List<AugmentTierEntry> = listOf(
        AugmentTierEntry(
            id = "live_aug_elderwood_crest",
            name = "Elderwood Crest",
            tier = TierRank.S,
            augmentTier = AugmentTier.GOLD,
            environment = TftEnvironment.LIVE,
            category = "Trait Emblem",
            avgPlacement = 3.31,
            top4Rate = 68.4,
            description = "Gain an Elderwood Emblem and a Lillia. Unlocks 6 Elderwood at Level 7.",
            synergyComps = listOf("Elderwood Arcanists"),
            patchDelta = DeltaType.BUFF,
            patchNote = "18.6: Now grants Lillia instead of Gnar"
        ),
        AugmentTierEntry(
            id = "live_aug_jeweled_lotus_2",
            name = "Jeweled Lotus II",
            tier = TierRank.S,
            augmentTier = AugmentTier.GOLD,
            environment = TftEnvironment.LIVE,
            category = "Combat",
            avgPlacement = 3.55,
            top4Rate = 63.1,
            description = "Your team gains +35% Crit Chance and abilities can critically strike.",
            synergyComps = listOf("Elderwood Arcanists", "Curios Wisp Flex")
        ),
        AugmentTierEntry(
            id = "live_aug_blossom_crown",
            name = "Blossom Crown",
            tier = TierRank.S,
            augmentTier = AugmentTier.PRISMATIC,
            environment = TftEnvironment.LIVE,
            category = "Trait Emblem",
            avgPlacement = 3.44,
            top4Rate = 65.8,
            description = "Gain a Blossom Emblem, Redemption, and Shen. Accelerates 4/6 Blossom breakpoints.",
            synergyComps = listOf("Blossom Vanguard Marksman")
        ),
        AugmentTierEntry(
            id = "live_aug_level_up",
            name = "Level Up!",
            tier = TierRank.A,
            augmentTier = AugmentTier.PRISMATIC,
            environment = TftEnvironment.LIVE,
            category = "Economy / XP",
            avgPlacement = 3.74,
            top4Rate = 59.6,
            description = "When you buy XP, gain an additional +2 XP. Gain 12 XP immediately.",
            synergyComps = listOf("Curios Wisp Flex", "Elderwood Arcanists")
        ),
        AugmentTierEntry(
            id = "live_aug_two_healthy",
            name = "Two Healthy",
            tier = TierRank.A,
            augmentTier = AugmentTier.GOLD,
            environment = TftEnvironment.LIVE,
            category = "Reroll Combat",
            avgPlacement = 3.78,
            top4Rate = 58.9,
            description = "Your units gain +95 Health for each distinct 2-cost champion fielded.",
            synergyComps = listOf("Sprykin Tristana Reroll")
        ),
        AugmentTierEntry(
            id = "live_aug_mana_flow",
            name = "Mana Flow",
            tier = TierRank.A,
            augmentTier = AugmentTier.SILVER,
            environment = TftEnvironment.LIVE,
            category = "Utility",
            avgPlacement = 3.92,
            top4Rate = 56.1,
            description = "Back-two-row allies gain +2 mana per basic attack.",
            synergyComps = listOf("Elderwood Arcanists", "Mystic Bard & Kog'Maw")
        ),
        AugmentTierEntry(
            id = "live_aug_tiny_titans",
            name = "Tiny Titans",
            tier = TierRank.B,
            augmentTier = AugmentTier.SILVER,
            environment = TftEnvironment.LIVE,
            category = "Tactician HP",
            avgPlacement = 4.15,
            top4Rate = 51.8,
            description = "Heal 30 Tactician Health, grow larger, and increase max Health to 130.",
            synergyComps = listOf("Sprykin Tristana Reroll", "Curios Wisp Flex")
        )
    )

    val pbeAugments: List<AugmentTierEntry> = listOf(
        AugmentTierEntry(
            id = "pbe_aug_ascendant_emblem",
            name = "Ascendant Emblem",
            tier = TierRank.S,
            augmentTier = AugmentTier.PRISMATIC,
            environment = TftEnvironment.PBE,
            category = "Trait Emblem",
            avgPlacement = 3.12,
            top4Rate = 72.1,
            description = "Gain an Ascendant Emblem, Spear of Shojin, and Karma. Accelerates 4 Ascendant.",
            synergyComps = listOf("Grove Ascendant Xerath"),
            patchDelta = DeltaType.NEW,
            patchNote = "18.7 PBE: New Prismatic Emblem package"
        ),
        AugmentTierEntry(
            id = "pbe_aug_grove_blessing",
            name = "Grove Blessing",
            tier = TierRank.S,
            augmentTier = AugmentTier.GOLD,
            environment = TftEnvironment.PBE,
            category = "Economy / Combat",
            avgPlacement = 3.28,
            top4Rate = 68.5,
            description = "Grove XP orbs grant +1 extra gold and Grove units gain +15% Attack Speed.",
            synergyComps = listOf("Grove Ascendant Xerath"),
            patchDelta = DeltaType.BUFF,
            patchNote = "18.7 PBE: Added +15% AS aura"
        ),
        AugmentTierEntry(
            id = "pbe_aug_nightshade_crest",
            name = "Nightshade Crest",
            tier = TierRank.S,
            augmentTier = AugmentTier.GOLD,
            environment = TftEnvironment.PBE,
            category = "Trait Emblem",
            avgPlacement = 3.45,
            top4Rate = 65.2,
            description = "Gain a Nightshade Emblem and a Diana. Unlocks 5 Nightshade execute threshold.",
            synergyComps = listOf("Nightshade Akali Blitz")
        ),
        AugmentTierEntry(
            id = "pbe_aug_starry_night",
            name = "Starry Night",
            tier = TierRank.A,
            augmentTier = AugmentTier.GOLD,
            environment = TftEnvironment.PBE,
            category = "Reroll Economy",
            avgPlacement = 3.74,
            top4Rate = 59.8,
            description = "1-cost and 2-cost champions in your shop have a high chance to appear as 2-star.",
            synergyComps = listOf("Starlight Ziggs Hyper"),
            patchDelta = DeltaType.BUFF,
            patchNote = "18.7 PBE: 2-star odds raised at Level 5"
        ),
        AugmentTierEntry(
            id = "pbe_aug_patient_study",
            name = "Patient Study",
            tier = TierRank.A,
            augmentTier = AugmentTier.SILVER,
            environment = TftEnvironment.PBE,
            category = "Economy / XP",
            avgPlacement = 3.85,
            top4Rate = 57.7,
            description = "After player combat, gain 2 XP if you won or 3 XP if you lost.",
            synergyComps = listOf("Grove Ascendant Xerath")
        )
    )
}
