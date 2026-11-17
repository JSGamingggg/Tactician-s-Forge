package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MatchAnalysisSummary
import com.example.data.model.PlayerProfileResult
import com.example.data.model.RiotPlatform
import com.example.data.model.TrackedPlayerDoc
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.RiotLookupError
import com.example.data.repository.RiotTftRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlayerLookupUiState {
    data object Idle : PlayerLookupUiState
    data object Loading : PlayerLookupUiState
    data class Success(val profile: PlayerProfileResult) : PlayerLookupUiState
    data class Error(val message: String, val isMissingApiKey: Boolean = false) : PlayerLookupUiState
}

class PlayerViewModel(
    private val currentUserId: String?,
    private val riotRepo: RiotTftRepository,
    private val localStore: LocalPreferencesStore,
    private val firestoreRepo: FirestoreUserRepository
) : ViewModel() {

    private val _lookupState = MutableStateFlow<PlayerLookupUiState>(PlayerLookupUiState.Idle)
    val lookupState: StateFlow<PlayerLookupUiState> = _lookupState.asStateFlow()

    private val _selectedPlatform = MutableStateFlow(RiotPlatform.NA1)
    val selectedPlatform: StateFlow<RiotPlatform> = _selectedPlatform.asStateFlow()

    private val _selectedMatch = MutableStateFlow<MatchAnalysisSummary?>(null)
    val selectedMatch: StateFlow<MatchAnalysisSummary?> = _selectedMatch.asStateFlow()

    val isApiKeyConfigured: Boolean get() = riotRepo.isApiKeyConfigured()

    val trackedPlayers: StateFlow<List<TrackedPlayerDoc>> =
        if (currentUserId.isNullOrBlank()) {
            localStore.localTrackedPlayersFlow.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )
        } else {
            firestoreRepo.observeTrackedPlayers(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w("PlayerViewModel", "Error observing tracked players", firestoreError ?: error)
                    emit(emptyList())
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = emptyList()
                )
        }

    fun selectPlatform(platform: RiotPlatform) {
        _selectedPlatform.value = platform
    }

    fun searchPlayer(riotIdInput: String, platform: RiotPlatform = _selectedPlatform.value) {
        val parts = riotIdInput.trim().split("#")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            _lookupState.value = PlayerLookupUiState.Error("Enter a valid Riot ID in Name#TAG format (e.g., Tactician#NA1).")
            return
        }
        val gameName = parts[0].trim()
        val tagLine = parts[1].trim()

        viewModelScope.launch {
            _lookupState.value = PlayerLookupUiState.Loading
            val result = riotRepo.lookupPlayerAndAnalyze(gameName, tagLine, platform)
            result.onSuccess { profile ->
                _lookupState.value = PlayerLookupUiState.Success(profile)
            }.onFailure { err ->
                val riotErr = err as? RiotLookupError
                _lookupState.value = PlayerLookupUiState.Error(
                    message = riotErr?.userMessage ?: (err.localizedMessage ?: "Lookup failed"),
                    isMissingApiKey = riotErr?.isMissingApiKey == true
                )
            }
        }
    }

    fun bookmarkCurrentPlayer(profile: PlayerProfileResult) {
        val tierLabel = profile.rankedEntry?.let { "${it.tier} ${it.rank}".trim() } ?: "UNRANKED"
        val lp = (profile.rankedEntry?.leaguePoints ?: 0).toLong()
        val playerId = "${profile.platform.code}_${profile.account.gameName}_${profile.account.tagLine}".lowercase()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

        viewModelScope.launch {
            if (currentUserId.isNullOrBlank()) {
                localStore.saveLocalTrackedPlayer(
                    playerId = playerId,
                    puuid = profile.account.puuid,
                    gameName = profile.account.gameName,
                    tagLine = profile.account.tagLine,
                    region = profile.region,
                    platform = profile.platform,
                    lastTier = tierLabel,
                    lastLp = lp
                )
            } else {
                firestoreRepo.saveTrackedPlayer(
                    puuid = profile.account.puuid,
                    gameName = profile.account.gameName,
                    tagLine = profile.account.tagLine,
                    region = profile.region,
                    platform = profile.platform,
                    lastTier = tierLabel,
                    lastLp = lp
                )
            }
        }
    }

    fun removeTrackedPlayer(playerId: String) {
        viewModelScope.launch {
            if (currentUserId.isNullOrBlank()) {
                localStore.removeLocalTrackedPlayer(playerId)
            } else {
                firestoreRepo.removeTrackedPlayer(playerId)
            }
        }
    }

    fun openMatchAnalysis(match: MatchAnalysisSummary) {
        _selectedMatch.value = match
    }

    fun closeMatchAnalysis() {
        _selectedMatch.value = null
    }
}
