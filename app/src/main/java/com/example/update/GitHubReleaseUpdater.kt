package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.example.BuildConfig
import com.example.data.network.RiotApiClientFactory
import com.example.data.repository.LocalPreferencesStore
import com.example.notifications.TftNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class GitHubReleaseInfo(
    val releaseId: Long,
    val tagName: String,
    val releaseTitle: String,
    val body: String,
    val htmlUrl: String,
    val publishedAtIso: String,
    val publishedAtMs: Long,
    val parsedVersionCode: Int?,
    val parsedVersionName: String?,
    val parsedCommitSha: String?,
    val isPrerelease: Boolean,
    val apkAssetName: String,
    val apkAssetId: Long,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val assetUpdatedAtMs: Long,
    val apkApiAssetUrl: String = ""
) {
    val displayVersionLabel: String
        get() = when {
            !parsedVersionName.isNullOrBlank() && parsedVersionCode != null ->
                "v$parsedVersionName (Build $parsedVersionCode)"
            !parsedVersionName.isNullOrBlank() ->
                "v$parsedVersionName"
            tagName.isNotBlank() -> tagName
            else -> releaseTitle.ifBlank { "Latest Build" }
        }

    val releaseKey: String
        get() {
            val stableTag = tagName.trim().takeIf { it.isNotBlank() } ?: releaseId.toString()
            val versionPart = parsedVersionCode?.let { "b$it" }
                ?: parsedVersionName?.takeIf { it.isNotBlank() }
                ?: "v0"
            val commitPart = parsedCommitSha?.trim()?.take(12).orEmpty()
            val assetPart = apkAssetName.trim().lowercase(Locale.US)
            return listOf(stableTag, versionPart, commitPart, assetPart)
                .filter { it.isNotBlank() }
                .joinToString("_")
        }
}

data class InstalledAppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val gitCommitSha: String,
    val buildTimestampMs: Long,
    val isDebugBuild: Boolean
)

sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus
    data object Checking : AppUpdateStatus
    data object RepoNotConfigured : AppUpdateStatus
    data class UpToDate(val currentVersionLabel: String, val checkedAtMillis: Long) : AppUpdateStatus
    data class UpdateAvailable(val release: GitHubReleaseInfo) : AppUpdateStatus
    data class Downloading(
        val release: GitHubReleaseInfo,
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : AppUpdateStatus
    data class ReadyToInstall(
        val release: GitHubReleaseInfo,
        val apkFilePath: String,
        val needsInstallPermission: Boolean,
        val signatureMismatchDetected: Boolean = false,
        val savedDownloadsFileName: String? = null
    ) : AppUpdateStatus
    data class Error(
        val message: String,
        val release: GitHubReleaseInfo? = null,
        val needsTokenOrPublicRepo: Boolean = false
    ) : AppUpdateStatus
}

data class AppUpdateUiState(
    val configuredRepo: String? = null,
    val configuredToken: String? = null,
    val hasGithubToken: Boolean = false,
    val installedVersionLabel: String = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
    val installedCommitShort: String? = BuildConfig.GIT_COMMIT_SHA.takeIf { it.isNotBlank() }?.take(7),
    val appUpdateAlertsEnabled: Boolean = true,
    val installedFromGooglePlay: Boolean = false,
    val status: AppUpdateStatus = AppUpdateStatus.Idle,
    val showPromptModal: Boolean = false,
    val pendingAutoInstallAfterPermission: Boolean = false
)

data class AppUpdateCheckOutcome(
    val updateAvailable: Boolean,
    val release: GitHubReleaseInfo? = null,
    val osNotificationPosted: Boolean = false,
    val inAppNotificationTriggered: Boolean = false
)

object GitHubReleaseParser {
    private val repoSlugRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    private val buildCodeRegex = Regex("(?i)BuildCode\\s*[:=]\\s*(\\d+)")
    private val versionRegex = Regex("(?i)Version\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)+(?:[-+._A-Za-z0-9]*)?)")
    private val commitRegex = Regex("(?i)Commit\\s*[:=]\\s*([0-9a-f]{7,40})")
    private val semverTagRegex = Regex("^[vV]?([0-9]+(?:\\.[0-9]+)+)(?:[-+._A-Za-z0-9]*)?$")
    private val htmlApkHrefRegex = Regex(
        """href=["']([^"']*?/releases/download/([^/"']+)/([^"'?]+\.apk))["']""",
        RegexOption.IGNORE_CASE
    )
    private val htmlTagHrefRegex = Regex(
        """href=["'][^"']*?/releases/tag/([^"'?#]+)["']""",
        RegexOption.IGNORE_CASE
    )

    fun normalizeRepoSlug(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.equals("MY_GITHUB_OWNER_REPO", ignoreCase = true)) return null
        val cleaned = trimmed
            .removePrefix("https://api.github.com/repos/")
            .removePrefix("http://api.github.com/repos/")
            .removePrefix("api.github.com/repos/")
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val segments = cleaned.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val candidate = "${segments[0]}/${segments[1]}"
        return candidate.takeIf { repoSlugRegex.matches(it) }
    }

    fun normalizeGithubToken(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .removePrefix("token ")
            .removePrefix("Token ")
            .trim()
        if (trimmed.isBlank() ||
            trimmed.equals("MY_GITHUB_TOKEN", ignoreCase = true) ||
            trimmed.equals("MY_GH_RELEASE_TOKEN", ignoreCase = true) ||
            trimmed.equals("null", ignoreCase = true)
        ) {
            return null
        }
        return trimmed
    }

    fun parseIsoTimestampMs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val clean = raw.trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(clean)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    fun parseGitHubReleases(
        jsonText: String,
        isDebugBuild: Boolean
    ): List<GitHubReleaseInfo> {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) return emptyList()
        val array = when {
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
            trimmed.startsWith("{") -> runCatching { JSONArray().put(JSONObject(trimmed)) }.getOrNull() ?: return emptyList()
            else -> return emptyList()
        }

        val results = mutableListOf<GitHubReleaseInfo>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue

            val assets = obj.optJSONArray("assets") ?: continue
            val apkAssets = buildList {
                for (aIdx in 0 until assets.length()) {
                    val asset = assets.optJSONObject(aIdx) ?: continue
                    val assetName = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    val apiAssetUrl = asset.optString("url", "")
                    if (assetName.endsWith(".apk", ignoreCase = true) &&
                        (downloadUrl.isNotBlank() || apiAssetUrl.isNotBlank())
                    ) {
                        add(asset)
                    }
                }
            }
            if (apkAssets.isEmpty()) continue

            val selectedAsset = selectMatchingApkAsset(apkAssets, isDebugBuild) ?: continue
            val tagName = obj.optString("tag_name", "").trim()
            val body = obj.optString("body", "").trim()
            val releaseTitle = obj.optString("name", "").trim().ifBlank { tagName }
            val publishedAtIso = obj.optString("published_at", "").trim()
                .ifBlank { obj.optString("created_at", "").trim() }
            val publishedAtMs = parseIsoTimestampMs(publishedAtIso)
            val assetUpdatedIso = selectedAsset.optString("updated_at", "").trim()
            val assetUpdatedAtMs = parseIsoTimestampMs(assetUpdatedIso).takeIf { it > 0L } ?: publishedAtMs

            val parsedVersionCode = buildCodeRegex.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val bodyVersion = versionRegex.find(body)?.groupValues?.getOrNull(1)
            val tagVersion = semverTagRegex.find(tagName)?.groupValues?.getOrNull(1)
            val parsedVersionName = bodyVersion ?: tagVersion
            val parsedCommitSha = commitRegex.find(body)?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
            val assetId = selectedAsset.optLong("id", 0L)
            val apiAssetUrl = selectedAsset.optString("url", "").trim()
            val browserDownloadUrl = selectedAsset.optString("browser_download_url", "").trim()
                .ifBlank { apiAssetUrl }

            results += GitHubReleaseInfo(
                releaseId = obj.optLong("id", 0L),
                tagName = tagName,
                releaseTitle = releaseTitle,
                body = body,
                htmlUrl = obj.optString("html_url", ""),
                publishedAtIso = publishedAtIso,
                publishedAtMs = publishedAtMs,
                parsedVersionCode = parsedVersionCode,
                parsedVersionName = parsedVersionName,
                parsedCommitSha = parsedCommitSha,
                isPrerelease = obj.optBoolean("prerelease", false),
                apkAssetName = selectedAsset.optString("name", "app.apk"),
                apkAssetId = assetId,
                apkDownloadUrl = browserDownloadUrl,
                apkSizeBytes = selectedAsset.optLong("size", 0L),
                assetUpdatedAtMs = assetUpdatedAtMs,
                apkApiAssetUrl = apiAssetUrl
            )
        }

        return results.sortedWith(
            compareByDescending<GitHubReleaseInfo> { it.parsedVersionCode ?: -1 }
                .thenByDescending { maxOf(it.assetUpdatedAtMs, it.publishedAtMs) }
                .thenByDescending { it.releaseId }
        )
    }

    /**
     * Parses GitHub web releases HTML (and optional expanded_assets fragment) as a fallback
     * when api.github.com is rate-limited on a public repository.
     */
    fun parseGitHubHtmlReleasesFallback(
        releasesHtml: String,
        expandedAssetsHtml: String,
        repoSlug: String,
        isDebugBuild: Boolean
    ): List<GitHubReleaseInfo> {
        val combinedHtml = "$releasesHtml\n$expandedAssetsHtml"
        val apkMatches = htmlApkHrefRegex.findAll(combinedHtml).toList()
        if (apkMatches.isEmpty()) return emptyList()

        val chosenMatch = if (isDebugBuild) {
            apkMatches.firstOrNull { it.groupValues[3].contains("debug", ignoreCase = true) }
                ?: apkMatches.first()
        } else {
            apkMatches.firstOrNull {
                val n = it.groupValues[3]
                n.contains("release", ignoreCase = true) && !n.contains("unsigned", ignoreCase = true)
            } ?: apkMatches.first()
        }

        val rawHref = chosenMatch.groupValues[1]
        val tagName = chosenMatch.groupValues[2]
        val assetName = chosenMatch.groupValues[3]
        val fullDownloadUrl = if (rawHref.startsWith("http")) {
            rawHref
        } else {
            "https://github.com/${rawHref.trimStart('/')}"
        }

        val strippedBody = releasesHtml.replace(Regex("<[^>]+>"), " ")
        val parsedVersionCode = buildCodeRegex.find(strippedBody)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val bodyVersion = versionRegex.find(strippedBody)?.groupValues?.getOrNull(1)
        val tagVersion = semverTagRegex.find(tagName)?.groupValues?.getOrNull(1)
        val parsedVersionName = bodyVersion ?: tagVersion
        val parsedCommitSha = commitRegex.find(strippedBody)?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
        val syntheticId = kotlin.math.abs("$tagName/$assetName/${parsedVersionCode ?: 0}".hashCode().toLong()).coerceAtLeast(1L)

        return listOf(
            GitHubReleaseInfo(
                releaseId = syntheticId,
                tagName = tagName,
                releaseTitle = "TFT's Helper ${parsedVersionName?.let { "v$it" } ?: tagName}",
                body = buildString {
                    parsedVersionName?.let { appendLine("Version: $it") }
                    parsedVersionCode?.let { appendLine("BuildCode: $it") }
                    parsedCommitSha?.let { appendLine("Commit: $it") }
                }.trim(),
                htmlUrl = "https://github.com/$repoSlug/releases/tag/$tagName",
                publishedAtIso = "",
                publishedAtMs = 0L,
                parsedVersionCode = parsedVersionCode,
                parsedVersionName = parsedVersionName,
                parsedCommitSha = parsedCommitSha,
                isPrerelease = false,
                apkAssetName = assetName,
                apkAssetId = syntheticId,
                apkDownloadUrl = fullDownloadUrl,
                apkSizeBytes = 0L,
                assetUpdatedAtMs = 0L,
                apkApiAssetUrl = ""
            )
        )
    }

    fun extractFirstReleaseTagFromHtml(releasesHtml: String): String? {
        return htmlTagHrefRegex.find(releasesHtml)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun selectMatchingApkAsset(
        apkAssets: List<JSONObject>,
        isDebugBuild: Boolean
    ): JSONObject? {
        if (apkAssets.size == 1) return apkAssets.first()
        return if (isDebugBuild) {
            apkAssets.firstOrNull {
                it.optString("name", "").contains("debug", ignoreCase = true)
            } ?: apkAssets.firstOrNull()
        } else {
            apkAssets.firstOrNull {
                val name = it.optString("name", "")
                name.contains("release", ignoreCase = true) &&
                    !name.contains("unsigned", ignoreCase = true)
            } ?: apkAssets.firstOrNull {
                !it.optString("name", "").contains("debug", ignoreCase = true)
            } ?: apkAssets.firstOrNull()
        }
    }

    fun compareSemanticVersions(remoteRaw: String?, installedRaw: String?): Int {
        val remoteNums = extractNumericParts(remoteRaw)
        val installedNums = extractNumericParts(installedRaw)
        if (remoteNums.isEmpty() || installedNums.isEmpty()) return 0
        val count = maxOf(remoteNums.size, installedNums.size)
        for (i in 0 until count) {
            val r = remoteNums.getOrElse(i) { 0 }
            val l = installedNums.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    private fun extractNumericParts(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        val numericPrefix = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        val parts = numericPrefix.split('.').mapNotNull { it.toIntOrNull() }
        return if (parts.isEmpty()) emptyList() else parts
    }

    fun isRemoteReleaseNewer(
        remote: GitHubReleaseInfo,
        installed: InstalledAppVersionInfo,
        lastInstalledAssetId: Long? = null
    ): Boolean {
        val installedSha = installed.gitCommitSha.trim().lowercase(Locale.US)
        val remoteSha = remote.parsedCommitSha?.trim()?.lowercase(Locale.US).orEmpty()
        if (installedSha.length >= 7 && remoteSha.length >= 7 &&
            (remoteSha.startsWith(installedSha) || installedSha.startsWith(remoteSha))
        ) {
            return false
        }

        val remoteCode = remote.parsedVersionCode
        if (remoteCode != null) {
            return when {
                remoteCode > installed.versionCode -> true
                remoteCode < installed.versionCode -> false
                installedSha.isNotBlank() && remoteSha.isNotBlank() && remoteSha != installedSha -> true
                else -> false
            }
        }

        val semverCmp = compareSemanticVersions(remote.parsedVersionName, installed.versionName)
        if (semverCmp != 0) {
            return semverCmp > 0
        }

        if (lastInstalledAssetId != null && lastInstalledAssetId == remote.apkAssetId) {
            return false
        }

        if (installed.buildTimestampMs > 0L && remote.assetUpdatedAtMs > 0L) {
            return remote.assetUpdatedAtMs > installed.buildTimestampMs + 180_000L
        }

        return false
    }
}

object AppUpdateCoordinator {
    private val checkMutex = Mutex()

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var lastAutoCheckMillis = 0L

    internal fun resetAutoCheckThrottleForTests() {
        lastAutoCheckMillis = 0L
        _uiState.value = AppUpdateUiState()
    }

    fun currentInstalledInfo(context: Context): InstalledAppVersionInfo {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return InstalledAppVersionInfo(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            gitCommitSha = BuildConfig.GIT_COMMIT_SHA,
            buildTimestampMs = BuildConfig.BUILD_TIMESTAMP_MS,
            isDebugBuild = isDebuggable || BuildConfig.DEBUG
        )
    }

    suspend fun resolveConfiguredRepo(prefsStore: LocalPreferencesStore): String? {
        val override = GitHubReleaseParser.normalizeRepoSlug(prefsStore.getGithubRepoOverride())
        if (override != null) return override
        val resolvedBuildRepo = GitHubReleaseParser.normalizeRepoSlug(
            runCatching { BuildConfig.RESOLVED_GITHUB_REPO }.getOrNull()
        )
        if (resolvedBuildRepo != null) return resolvedBuildRepo
        val defaultSlug = GitHubReleaseParser.normalizeRepoSlug(
            runCatching { BuildConfig.GITHUB_REPO }.getOrNull()
        )
        if (defaultSlug != null) return defaultSlug
        return "JSGamingggg/Tactician-s-Forge"
    }

    suspend fun resolveConfiguredToken(prefsStore: LocalPreferencesStore): String? {
        val override = GitHubReleaseParser.normalizeGithubToken(prefsStore.getGithubTokenOverride())
        if (override != null) return override
        val fromResolvedBuildToken = GitHubReleaseParser.normalizeGithubToken(
            runCatching { BuildConfig.RESOLVED_GITHUB_TOKEN }.getOrNull()
        )
        if (fromResolvedBuildToken != null) return fromResolvedBuildToken
        val fromGithubToken = GitHubReleaseParser.normalizeGithubToken(
            runCatching { BuildConfig.GITHUB_TOKEN }.getOrNull()
        )
        if (fromGithubToken != null) return fromGithubToken
        return GitHubReleaseParser.normalizeGithubToken(
            runCatching { BuildConfig.GH_RELEASE_TOKEN }.getOrNull()
        )
    }

    suspend fun refreshSettingsSnapshot(context: Context) {
        val prefsStore = LocalPreferencesStore(context.applicationContext)
        val repo = resolveConfiguredRepo(prefsStore)
        val token = resolveConfiguredToken(prefsStore)
        val alertsEnabled = prefsStore.isAppUpdateAlertsEnabled()
        val installed = currentInstalledInfo(context)
        val fromPlay = isInstalledFromGooglePlay(context)
        _uiState.update { state ->
            state.copy(
                configuredRepo = repo,
                configuredToken = token,
                hasGithubToken = !token.isNullOrBlank(),
                installedVersionLabel = "v${installed.versionName} (Build ${installed.versionCode})",
                installedCommitShort = installed.gitCommitSha.takeIf { it.isNotBlank() }?.take(7),
                appUpdateAlertsEnabled = alertsEnabled,
                installedFromGooglePlay = fromPlay,
                status = if (repo == null && state.status is AppUpdateStatus.Idle) {
                    AppUpdateStatus.RepoNotConfigured
                } else {
                    state.status
                }
            )
        }
    }

    suspend fun saveGithubRepoSlug(
        context: Context,
        rawInput: String,
        rawTokenInput: String? = null
    ): String? {
        val normalized = GitHubReleaseParser.normalizeRepoSlug(rawInput)
        val prefsStore = LocalPreferencesStore(context.applicationContext)
        prefsStore.setGithubRepoOverride(normalized.orEmpty())
        if (rawTokenInput != null) {
            val cleanToken = GitHubReleaseParser.normalizeGithubToken(rawTokenInput).orEmpty()
            prefsStore.setGithubTokenOverride(cleanToken)
        }
        refreshSettingsSnapshot(context)
        return normalized
    }

    suspend fun setAppUpdateAlertsEnabled(context: Context, enabled: Boolean) {
        LocalPreferencesStore(context.applicationContext).setAppUpdateAlertsEnabled(enabled)
        _uiState.update { it.copy(appUpdateAlertsEnabled = enabled) }
    }

    fun openPromptModalIfAvailable() {
        val currentStatus = _uiState.value.status
        if (currentStatus is AppUpdateStatus.UpdateAvailable ||
            currentStatus is AppUpdateStatus.Downloading ||
            currentStatus is AppUpdateStatus.ReadyToInstall ||
            (currentStatus is AppUpdateStatus.Error && currentStatus.release != null)
        ) {
            _uiState.update { it.copy(showPromptModal = true) }
        }
    }

    suspend fun dismissPromptModal(context: Context, suppressThisReleaseOnLaunch: Boolean = false) {
        val releaseKey = when (val st = _uiState.value.status) {
            is AppUpdateStatus.UpdateAvailable -> st.release.releaseKey
            is AppUpdateStatus.ReadyToInstall -> st.release.releaseKey
            is AppUpdateStatus.Error -> st.release?.releaseKey
            else -> null
        }
        if (suppressThisReleaseOnLaunch && !releaseKey.isNullOrBlank()) {
            LocalPreferencesStore(context.applicationContext).setDismissedAppReleaseKey(releaseKey)
        }
        _uiState.update { it.copy(showPromptModal = false) }
    }

    private data class ReleaseFetchOutcome(
        val releases: List<GitHubReleaseInfo>,
        val needsTokenOrPublicRepo: Boolean = false
    )

    private fun executeApiReleasesCall(
        httpClient: OkHttpClient,
        repoSlug: String,
        versionName: String,
        githubToken: String?
    ): Response {
        val reqBuilder = Request.Builder()
            .url("https://api.github.com/repos/$repoSlug/releases?per_page=8")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "TacticiansForge-Android/$versionName")
            .get()
        if (!githubToken.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $githubToken")
        }
        return httpClient.newCall(reqBuilder.build()).execute()
    }

    private fun tryHtmlReleasesFallback(
        httpClient: OkHttpClient,
        repoSlug: String,
        versionName: String,
        isDebugBuild: Boolean
    ): List<GitHubReleaseInfo>? {
        return runCatching {
            val mainReq = Request.Builder()
                .url("https://github.com/$repoSlug/releases")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "TacticiansForge-Android/$versionName")
                .get()
                .build()
            val releasesHtml = httpClient.newCall(mainReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
            val firstTag = GitHubReleaseParser.extractFirstReleaseTagFromHtml(releasesHtml) ?: "latest-apk"
            val assetsReq = Request.Builder()
                .url("https://github.com/$repoSlug/releases/expanded_assets/$firstTag")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "TacticiansForge-Android/$versionName")
                .get()
                .build()
            val expandedHtml = runCatching {
                httpClient.newCall(assetsReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }
            }.getOrDefault("")

            GitHubReleaseParser.parseGitHubHtmlReleasesFallback(
                releasesHtml = releasesHtml,
                expandedAssetsHtml = expandedHtml,
                repoSlug = repoSlug,
                isDebugBuild = isDebugBuild
            )
        }.getOrNull()
    }

    suspend fun checkForUpdates(
        context: Context,
        userInitiated: Boolean = false,
        showInAppModalOnLaunch: Boolean = true,
        dispatchOsNotification: Boolean = true,
        httpClient: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): AppUpdateCheckOutcome = checkMutex.withLock {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        if (!userInitiated && now - lastAutoCheckMillis < 25_000L) {
            val currentRelease = (_uiState.value.status as? AppUpdateStatus.UpdateAvailable)?.release
            return AppUpdateCheckOutcome(updateAvailable = currentRelease != null, release = currentRelease)
        }

        // Don't clobber an active download or ready-to-install state during background polls
        val activeStatus = _uiState.value.status
        if (!userInitiated && (activeStatus is AppUpdateStatus.Downloading || activeStatus is AppUpdateStatus.ReadyToInstall)) {
            return AppUpdateCheckOutcome(updateAvailable = true)
        }

        val prefsStore = LocalPreferencesStore(appContext)
        val repoSlug = resolveConfiguredRepo(prefsStore)
        val githubToken = resolveConfiguredToken(prefsStore)
        val alertsEnabled = prefsStore.isAppUpdateAlertsEnabled()
        val installed = currentInstalledInfo(appContext)
        val installedLabel = "v${installed.versionName} (Build ${installed.versionCode})"
        val fromPlay = isInstalledFromGooglePlay(appContext)

        if (repoSlug == null) {
            _uiState.update {
                it.copy(
                    configuredRepo = null,
                    configuredToken = githubToken,
                    hasGithubToken = !githubToken.isNullOrBlank(),
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
                    installedFromGooglePlay = fromPlay,
                    status = AppUpdateStatus.RepoNotConfigured
                )
            }
            return AppUpdateCheckOutcome(updateAvailable = false)
        }

        lastAutoCheckMillis = now
        if (userInitiated || activeStatus is AppUpdateStatus.Idle || activeStatus is AppUpdateStatus.Error) {
            _uiState.update {
                it.copy(
                    configuredRepo = repoSlug,
                    configuredToken = githubToken,
                    hasGithubToken = !githubToken.isNullOrBlank(),
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
                    installedFromGooglePlay = fromPlay,
                    status = AppUpdateStatus.Checking
                )
            }
        }

        var needsTokenHint = false
        val releasesResult = withContext(Dispatchers.IO) {
            runCatching {
                var response = executeApiReleasesCall(httpClient, repoSlug, installed.versionName, githubToken)
                // If token was rejected with 401 on a potentially public repo, retry once without token
                if (response.code == 401 && !githubToken.isNullOrBlank()) {
                    response.close()
                    response = executeApiReleasesCall(httpClient, repoSlug, installed.versionName, null)
                }

                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        return@runCatching GitHubReleaseParser.parseGitHubReleases(body, installed.isDebugBuild)
                    }

                    // Try HTML releases fallback in case api.github.com is rate-limited on a public repo
                    val htmlFallback = tryHtmlReleasesFallback(
                        httpClient = httpClient,
                        repoSlug = repoSlug,
                        versionName = installed.versionName,
                        isDebugBuild = installed.isDebugBuild
                    )
                    if (htmlFallback != null) {
                        return@runCatching htmlFallback
                    }

                    when (resp.code) {
                        404 -> {
                            needsTokenHint = true
                            if (githubToken.isNullOrBlank()) {
                                throw IllegalStateException(
                                    "GitHub returned HTTP 404 for '$repoSlug'. Private GitHub repositories require a Personal Access Token (Contents: Read) — tap 'Add GitHub Token' below, or change '$repoSlug' visibility to Public in GitHub Settings."
                                )
                            } else {
                                throw IllegalStateException(
                                    "GitHub returned HTTP 404 for '$repoSlug' with the current token. Ensure your token has 'Contents: Read' permission for '$repoSlug' (or 'repo' scope)."
                                )
                            }
                        }
                        401, 403 -> {
                            needsTokenHint = true
                            throw IllegalStateException(
                                "GitHub API returned HTTP ${resp.code} for '$repoSlug'. Add a GitHub Personal Access Token (Contents: Read) below to authenticate."
                            )
                        }
                        else -> throw IllegalStateException("GitHub API returned HTTP ${resp.code}")
                    }
                }
            }
        }

        val releases = releasesResult.getOrElse { err ->
            _uiState.update {
                it.copy(
                    configuredRepo = repoSlug,
                    configuredToken = githubToken,
                    hasGithubToken = !githubToken.isNullOrBlank(),
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
                    installedFromGooglePlay = fromPlay,
                    status = if (userInitiated || needsTokenHint) {
                        AppUpdateStatus.Error(
                            message = err.localizedMessage ?: "Failed to reach GitHub Releases",
                            needsTokenOrPublicRepo = needsTokenHint
                        )
                    } else if (activeStatus is AppUpdateStatus.UpdateAvailable) {
                        activeStatus
                    } else {
                        AppUpdateStatus.Idle
                    }
                )
            }
            return AppUpdateCheckOutcome(updateAvailable = false)
        }

        val candidate = releases.firstOrNull()
        val lastInstalledAssetId = prefsStore.getLastInstalledApkAssetId()
        if (candidate != null && GitHubReleaseParser.isRemoteReleaseNewer(candidate, installed, lastInstalledAssetId)) {
            val lastNotifiedKey = prefsStore.getLastNotifiedAppReleaseKey()
            val lastPromptedKey = prefsStore.getLastPromptedAppReleaseKey()
            val dismissedKey = prefsStore.getDismissedAppReleaseKey()
            val isFreshReleaseNotification = lastNotifiedKey != candidate.releaseKey

            var osPosted = false
            if (dispatchOsNotification && alertsEnabled && (isFreshReleaseNotification || userInitiated)) {
                osPosted = TftNotificationHelper.postAppUpdateNotification(
                    context = appContext,
                    release = candidate
                )
                if (osPosted) {
                    prefsStore.setLastNotifiedAppReleaseKey(candidate.releaseKey)
                }
            }

            val shouldShowModal = userInitiated ||
                (showInAppModalOnLaunch && dismissedKey != candidate.releaseKey && lastPromptedKey != candidate.releaseKey)

            if (!userInitiated && shouldShowModal) {
                prefsStore.setLastPromptedAppReleaseKey(candidate.releaseKey)
                if (!dispatchOsNotification) {
                    prefsStore.setLastNotifiedAppReleaseKey(candidate.releaseKey)
                }
            }

            _uiState.update {
                it.copy(
                    configuredRepo = repoSlug,
                    configuredToken = githubToken,
                    hasGithubToken = !githubToken.isNullOrBlank(),
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
                    installedFromGooglePlay = fromPlay,
                    status = AppUpdateStatus.UpdateAvailable(candidate),
                    showPromptModal = it.showPromptModal || shouldShowModal
                )
            }

            return AppUpdateCheckOutcome(
                updateAvailable = true,
                release = candidate,
                osNotificationPosted = osPosted,
                inAppNotificationTriggered = shouldShowModal
            )
        } else {
            _uiState.update {
                it.copy(
                    configuredRepo = repoSlug,
                    configuredToken = githubToken,
                    hasGithubToken = !githubToken.isNullOrBlank(),
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
                    installedFromGooglePlay = fromPlay,
                    status = AppUpdateStatus.UpToDate(
                        currentVersionLabel = installedLabel,
                        checkedAtMillis = now
                    ),
                    showPromptModal = false
                )
            }
            return AppUpdateCheckOutcome(updateAvailable = false)
        }
    }

    /**
     * Detects whether the app was installed from Google Play Store (`com.android.vending`)
     * so update prompts can prioritize Play Store for Play installs and GitHub Releases
     * for GitHub/direct installs, while offering both links cleanly.
     */
    @Suppress("DEPRECATION")
    fun isInstalledFromGooglePlay(context: Context): Boolean {
        val installerPackage = runCatching {
            val pm = context.applicationContext.packageManager
            val pkg = context.applicationContext.packageName
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } else {
                pm.getInstallerPackageName(pkg)
            }
        }.getOrNull()
        return installerPackage == "com.android.vending"
    }

    /**
     * Builds a Google Play-compliant [Intent.ACTION_VIEW] pointing to the app's Google Play Store listing
     * (`market://` with HTTPS Play Store fallback). Never downloads or installs external APKs.
     */
    fun buildPlayStoreUpdateIntent(
        context: Context,
        release: GitHubReleaseInfo? = null,
        preferWebFallback: Boolean = false
    ): Intent {
        val packageName = context.packageName
        val uriString = if (!preferWebFallback) {
            "market://details?id=$packageName"
        } else {
            release?.htmlUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: "https://play.google.com/store/apps/details?id=$packageName"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Builds a browser [Intent.ACTION_VIEW] pointing to the official HTTPS GitHub Releases web page
     * so users can also update directly from GitHub without using `REQUEST_INSTALL_PACKAGES`
     * or in-app `.apk` sideloading.
     */
    fun buildGitHubReleaseIntent(
        release: GitHubReleaseInfo? = null,
        configuredRepo: String? = _uiState.value.configuredRepo
    ): Intent {
        val normalizedRepo = GitHubReleaseParser.normalizeRepoSlug(configuredRepo)
            ?: "JSGamingggg/Tactician-s-Forge"
        val releasePageUrl = release?.htmlUrl
            ?.trim()
            ?.takeIf { it.startsWith("https://github.com/", ignoreCase = true) }
            ?: "https://github.com/$normalizedRepo/releases/latest"
        return Intent(Intent.ACTION_VIEW, Uri.parse(releasePageUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens the official GitHub Releases web page in the device's browser.
     */
    fun openGitHubReleaseUpdate(
        context: Context,
        release: GitHubReleaseInfo? = null
    ): Boolean {
        val appContext = context.applicationContext
        val githubIntent = buildGitHubReleaseIntent(release, _uiState.value.configuredRepo)
        val launched = runCatching {
            appContext.startActivity(githubIntent)
            true
        }.getOrDefault(false)
        if (launched) {
            _uiState.update { it.copy(showPromptModal = false) }
        }
        return launched
    }

    /**
     * Explicitly opens Google Play Store (`market://`) with HTTPS Play Store fallback.
     */
    fun openPlayStoreUpdate(context: Context): Boolean {
        val appContext = context.applicationContext
        val marketIntent = buildPlayStoreUpdateIntent(appContext, release = null, preferWebFallback = false)
        val launchedPlay = runCatching {
            appContext.startActivity(marketIntent)
            true
        }.getOrDefault(false)
        if (launchedPlay) {
            _uiState.update { it.copy(showPromptModal = false) }
            return true
        }
        val webIntent = buildPlayStoreUpdateIntent(appContext, release = null, preferWebFallback = true)
        val launchedWeb = runCatching {
            appContext.startActivity(webIntent)
            true
        }.getOrDefault(false)
        if (launchedWeb) {
            _uiState.update { it.copy(showPromptModal = false) }
        }
        return launchedWeb
    }

    /**
     * Routes update taps to Google Play when installed from Google Play, or to the official
     * GitHub Releases web page when installed outside Google Play (direct/GitHub builds),
     * always via standard [Intent.ACTION_VIEW] with zero `REQUEST_INSTALL_PACKAGES` usage.
     */
    fun openStoreOrReleaseUpdate(
        context: Context,
        release: GitHubReleaseInfo? = null
    ): Boolean {
        return if (isInstalledFromGooglePlay(context)) {
            openPlayStoreUpdate(context)
        } else {
            openGitHubReleaseUpdate(context, release)
        }
    }

    fun formatMegabytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
