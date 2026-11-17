package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.MatchAnalysisSummary
import com.example.data.model.PlayerProfileResult
import com.example.data.model.RiotPlatform
import com.example.data.model.RiotRegion
import com.example.data.model.TftMatchDto
import com.example.data.network.RiotApiClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed class RiotLookupError(val userMessage: String, val isMissingApiKey: Boolean = false) : Exception(userMessage) {
    class MissingApiKey : RiotLookupError(
        "RIOT_API_KEY is not configured yet. Add your Riot Games Developer API key in AI Studio Secrets to enable live Riot ID lookups.",
        isMissingApiKey = true
    )
    class InvalidApiKey : RiotLookupError(
        "Riot API rejected the token (401/403). Your Riot development key may have expired—refresh RIOT_API_KEY in AI Studio Secrets.",
        isMissingApiKey = false
    )
    class PlayerNotFound(riotId: String) : RiotLookupError(
        "Could not find Riot ID '$riotId' in that region. Verify the Name#TAG and regional cluster."
    )
    class RateLimited : RiotLookupError(
        "Riot API rate limit reached (429). Wait a few seconds and retry."
    )
    class NetworkFailure(detail: String) : RiotLookupError(
        "Network error contacting Riot servers: $detail"
    )
}

class RiotTftRepository {

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.RIOT_API_KEY
        return key.isNotBlank() && key != "MY_RIOT_API_KEY"
    }

    suspend fun lookupPlayerAndAnalyze(
        gameName: String,
        tagLine: String,
        platform: RiotPlatform
    ): Result<PlayerProfileResult> = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            return@withContext Result.failure(RiotLookupError.MissingApiKey())
        }

        val cleanName = gameName.trim()
        val cleanTag = tagLine.trim().removePrefix("#")
        if (cleanName.isBlank() || cleanTag.isBlank()) {
            return@withContext Result.failure(RiotLookupError.PlayerNotFound("$cleanName#$cleanTag"))
        }

        val region = platform.region
        try {
            val regionalApi = RiotApiClientFactory.createRegionalApi(region)
            val platformApi = RiotApiClientFactory.createPlatformApi(platform)

            val accountResp = regionalApi.getAccountByRiotId(cleanName, cleanTag)
            if (!accountResp.isSuccessful) {
                return@withContext Result.failure(mapHttpError(accountResp.code(), "$cleanName#$cleanTag"))
            }
            val account = accountResp.body()
                ?: return@withContext Result.failure(RiotLookupError.PlayerNotFound("$cleanName#$cleanTag"))

            coroutineScope {
                val summonerDeferred = async {
                    runCatching { platformApi.getSummonerByPuuid(account.puuid) }.getOrNull()
                }
                val leagueByPuuidDeferred = async {
                    runCatching { platformApi.getLeagueEntriesByPuuid(account.puuid) }.getOrNull()
                }
                val matchIdsDeferred = async {
                    regionalApi.getMatchIdsByPuuid(account.puuid, start = 0, count = 8)
                }

                val summoner = summonerDeferred.await()?.body()
                var leagueEntries = leagueByPuuidDeferred.await()?.body()
                if (leagueEntries.isNullOrEmpty() && summoner != null && summoner.id.isNotBlank()) {
                    leagueEntries = runCatching {
                        platformApi.getLeagueEntriesBySummoner(summoner.id).body()
                    }.getOrNull()
                }

                val rankedEntry = leagueEntries?.firstOrNull { it.queueType == "RANKED_TFT" }
                    ?: leagueEntries?.firstOrNull()

                val matchIdsResp = matchIdsDeferred.await()
                val matchIds = if (matchIdsResp.isSuccessful) {
                    matchIdsResp.body().orEmpty().take(8)
                } else {
                    emptyList()
                }

                val matchDetails = matchIds.map { matchId ->
                    async {
                        runCatching {
                            val resp = regionalApi.getMatchById(matchId)
                            if (resp.isSuccessful) resp.body() else null
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()

                val analyses = matchDetails.mapNotNull { match ->
                    analyzeMatchForPlayer(match, account.puuid)
                }.sortedByDescending { it.gameDatetime }

                val totalMatches = analyses.size.coerceAtLeast(1)
                val avgPlace = if (analyses.isEmpty()) 0.0 else analyses.map { it.placement }.average()
                val top4 = if (analyses.isEmpty()) 0.0 else (analyses.count { it.placement <= 4 } * 100.0 / totalMatches)
                val winRate = if (analyses.isEmpty()) 0.0 else (analyses.count { it.placement == 1 } * 100.0 / totalMatches)

                Result.success(
                    PlayerProfileResult(
                        account = account,
                        region = region,
                        platform = platform,
                        summonerLevel = summoner?.summonerLevel ?: 1L,
                        rankedEntry = rankedEntry,
                        matches = analyses,
                        avgPlacement = avgPlace,
                        top4Rate = top4,
                        winRate = winRate
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(RiotLookupError.NetworkFailure(e.localizedMessage ?: "Unknown error"))
        }
    }

    private fun mapHttpError(code: Int, riotId: String): RiotLookupError = when (code) {
        401, 403 -> RiotLookupError.InvalidApiKey()
        404 -> RiotLookupError.PlayerNotFound(riotId)
        429 -> RiotLookupError.RateLimited()
        else -> RiotLookupError.NetworkFailure("HTTP $code")
    }

    private fun analyzeMatchForPlayer(match: TftMatchDto, puuid: String): MatchAnalysisSummary? {
        val player = match.info.participants.firstOrNull { it.puuid == puuid } ?: return null
        val activeTraits = player.traits.filter { it.tierCurrent > 0 }.sortedByDescending { it.style }
        val goldOrPrismTraits = activeTraits.count { it.style >= 3 }
        val threeStars = player.units.count { it.tier >= 3 }

        val maxPossibleItems = (player.units.size.coerceAtLeast(1) * 3).coerceAtMost(18)
        val equippedItems = player.units.sumOf { it.itemNames.size }
        val itemPct = ((equippedItems.toFloat() / maxPossibleItems.toFloat()) * 100f)
            .roundToInt().coerceIn(0, 100)

        val insights = buildList {
            when (player.placement) {
                1 -> add("1st Place Victory — capped board at Level ${player.level} with ${player.playersEliminated} eliminations.")
                in 2..4 -> add("Top-4 LP Gain — stabilized at Level ${player.level} with ${activeTraits.size} active synergies.")
                else -> add("Bottom-4 Finish (#${player.placement}) — ended with ${player.goldLeft}g unspent at Level ${player.level}.")
            }
            if (threeStars > 0) {
                add("Reroll Power Spike: Hit $threeStars 3-star unit(s) carrying late-game fights.")
            }
            if (goldOrPrismTraits > 0) {
                add("Vertical Synergy Depth: Activated $goldOrPrismTraits Gold/Prismatic trait tier(s).")
            }
            if (itemPct < 55 && player.placement > 4) {
                add("Item Efficiency Tip: Only $itemPct% core item slots filled; prioritize early carousel components.")
            }
        }

        return MatchAnalysisSummary(
            matchId = match.metadata.matchId,
            gameDatetime = match.info.gameDatetime,
            durationMinutes = (match.info.gameLength / 60f).roundToInt().coerceAtLeast(1),
            gameVersion = match.info.gameVersion,
            setNumber = match.info.tftSetNumber,
            placement = player.placement,
            levelReached = player.level,
            playersEliminated = player.playersEliminated,
            totalDamageToPlayers = player.totalDamageToPlayers,
            goldLeft = player.goldLeft,
            activeTraits = activeTraits,
            units = player.units.sortedByDescending { it.rarity },
            augments = player.augments,
            goldOrPrismTraitsCount = goldOrPrismTraits,
            threeStarCount = threeStars,
            itemCompletionPercent = itemPct,
            tacticalInsights = insights
        )
    }
}
