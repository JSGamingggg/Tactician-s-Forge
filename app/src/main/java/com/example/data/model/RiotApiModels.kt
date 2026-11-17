package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class RiotRegion(val label: String, val host: String) {
    AMERICAS("Americas", "https://americas.api.riotgames.com/"),
    EUROPE("Europe", "https://europe.api.riotgames.com/"),
    ASIA("Asia", "https://asia.api.riotgames.com/"),
    SEA("SEA", "https://sea.api.riotgames.com/");

    companion object {
        fun fromRaw(raw: String?): RiotRegion =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AMERICAS
    }
}

enum class RiotPlatform(val code: String, val region: RiotRegion) {
    NA1("NA1", RiotRegion.AMERICAS),
    BR1("BR1", RiotRegion.AMERICAS),
    LA1("LA1", RiotRegion.AMERICAS),
    LA2("LA2", RiotRegion.AMERICAS),
    EUW1("EUW1", RiotRegion.EUROPE),
    EUN1("EUN1", RiotRegion.EUROPE),
    TR1("TR1", RiotRegion.EUROPE),
    KR("KR", RiotRegion.ASIA),
    JP1("JP1", RiotRegion.ASIA),
    OC1("OC1", RiotRegion.SEA),
    SG2("SG2", RiotRegion.SEA),
    TW2("TW2", RiotRegion.SEA);

    val host: String get() = "https://${code.lowercase()}.api.riotgames.com/"

    companion object {
        fun fromRaw(raw: String?): RiotPlatform =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: NA1
    }
}

@JsonClass(generateAdapter = true)
data class RiotAccountDto(
    val puuid: String = "",
    val gameName: String = "",
    val tagLine: String = ""
)

@JsonClass(generateAdapter = true)
data class TftSummonerDto(
    val id: String = "",
    val accountId: String = "",
    val puuid: String = "",
    val profileIconId: Int = 0,
    val summonerLevel: Long = 1L
)

@JsonClass(generateAdapter = true)
data class TftLeagueEntryDto(
    val queueType: String = "",
    val tier: String = "UNRANKED",
    val rank: String = "",
    val leaguePoints: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val hotStreak: Boolean = false
)

@JsonClass(generateAdapter = true)
data class TftMatchDto(
    val metadata: TftMatchMetadataDto = TftMatchMetadataDto(),
    val info: TftMatchInfoDto = TftMatchInfoDto()
)

@JsonClass(generateAdapter = true)
data class TftMatchMetadataDto(
    @Json(name = "match_id") val matchId: String = "",
    val participants: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TftMatchInfoDto(
    @Json(name = "game_datetime") val gameDatetime: Long = 0L,
    @Json(name = "game_length") val gameLength: Float = 0f,
    @Json(name = "game_version") val gameVersion: String = "",
    @Json(name = "tft_set_number") val tftSetNumber: Int = 18,
    @Json(name = "tft_set_core_name") val tftSetCoreName: String = "",
    val participants: List<TftParticipantDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TftParticipantDto(
    val puuid: String = "",
    val placement: Int = 8,
    val level: Int = 7,
    @Json(name = "gold_left") val goldLeft: Int = 0,
    @Json(name = "last_round") val lastRound: Int = 0,
    @Json(name = "players_eliminated") val playersEliminated: Int = 0,
    @Json(name = "total_damage_to_players") val totalDamageToPlayers: Int = 0,
    @Json(name = "time_eliminated") val timeEliminated: Float = 0f,
    val augments: List<String> = emptyList(),
    val traits: List<TftTraitDto> = emptyList(),
    val units: List<TftUnitDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TftTraitDto(
    val name: String = "",
    @Json(name = "num_units") val numUnits: Int = 0,
    val style: Int = 0,
    @Json(name = "tier_current") val tierCurrent: Int = 0,
    @Json(name = "tier_total") val tierTotal: Int = 0
)

@JsonClass(generateAdapter = true)
data class TftUnitDto(
    @Json(name = "character_id") val characterId: String = "",
    val name: String = "",
    val rarity: Int = 0,
    val tier: Int = 1,
    val itemNames: List<String> = emptyList()
)

data class MatchAnalysisSummary(
    val matchId: String,
    val gameDatetime: Long,
    val durationMinutes: Int,
    val gameVersion: String,
    val setNumber: Int,
    val placement: Int,
    val levelReached: Int,
    val playersEliminated: Int,
    val totalDamageToPlayers: Int,
    val goldLeft: Int,
    val activeTraits: List<TftTraitDto>,
    val units: List<TftUnitDto>,
    val augments: List<String>,
    val goldOrPrismTraitsCount: Int,
    val threeStarCount: Int,
    val itemCompletionPercent: Int,
    val tacticalInsights: List<String>
)

data class PlayerProfileResult(
    val account: RiotAccountDto,
    val region: RiotRegion,
    val platform: RiotPlatform,
    val summonerLevel: Long,
    val rankedEntry: TftLeagueEntryDto?,
    val matches: List<MatchAnalysisSummary>,
    val avgPlacement: Double,
    val top4Rate: Double,
    val winRate: Double
)
