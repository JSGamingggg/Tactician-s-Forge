package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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
    val assetUpdatedAtMs: Long
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
        get() = "${releaseId}_${apkAssetId}_${parsedCommitSha ?: assetUpdatedAtMs}"
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
        val needsInstallPermission: Boolean
    ) : AppUpdateStatus
    data class Error(val message: String, val release: GitHubReleaseInfo? = null) : AppUpdateStatus
}

data class AppUpdateUiState(
    val configuredRepo: String? = null,
    val installedVersionLabel: String = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
    val installedCommitShort: String? = BuildConfig.GIT_COMMIT_SHA.takeIf { it.isNotBlank() }?.take(7),
    val appUpdateAlertsEnabled: Boolean = true,
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

    fun normalizeRepoSlug(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.equals("MY_GITHUB_OWNER_REPO", ignoreCase = true)) return null
        val cleaned = trimmed
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
                    if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
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
                apkAssetId = selectedAsset.optLong("id", 0L),
                apkDownloadUrl = selectedAsset.optString("browser_download_url", ""),
                apkSizeBytes = selectedAsset.optLong("size", 0L),
                assetUpdatedAtMs = assetUpdatedAtMs
            )
        }

        return results.sortedWith(
            compareByDescending<GitHubReleaseInfo> { it.parsedVersionCode ?: -1 }
                .thenByDescending { maxOf(it.assetUpdatedAtMs, it.publishedAtMs) }
                .thenByDescending { it.releaseId }
        )
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

        return remote.apkAssetId > 0L
    }
}

object AppUpdateCoordinator {
    private val checkMutex = Mutex()
    private val downloadMutex = Mutex()

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var lastAutoCheckMillis = 0L

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
        val defaultSlug = GitHubReleaseParser.normalizeRepoSlug(BuildConfig.GITHUB_REPO)
        if (defaultSlug != null) return defaultSlug
        return "js96-mk/tacticians-forge"
    }

    suspend fun refreshSettingsSnapshot(context: Context) {
        val prefsStore = LocalPreferencesStore(context.applicationContext)
        val repo = resolveConfiguredRepo(prefsStore)
        val alertsEnabled = prefsStore.isAppUpdateAlertsEnabled()
        val installed = currentInstalledInfo(context)
        _uiState.update { state ->
            state.copy(
                configuredRepo = repo,
                installedVersionLabel = "v${installed.versionName} (Build ${installed.versionCode})",
                installedCommitShort = installed.gitCommitSha.takeIf { it.isNotBlank() }?.take(7),
                appUpdateAlertsEnabled = alertsEnabled,
                status = if (repo == null && state.status is AppUpdateStatus.Idle) {
                    AppUpdateStatus.RepoNotConfigured
                } else {
                    state.status
                }
            )
        }
    }

    suspend fun saveGithubRepoSlug(context: Context, rawInput: String): String? {
        val normalized = GitHubReleaseParser.normalizeRepoSlug(rawInput)
        val prefsStore = LocalPreferencesStore(context.applicationContext)
        prefsStore.setGithubRepoOverride(normalized.orEmpty())
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
        val alertsEnabled = prefsStore.isAppUpdateAlertsEnabled()
        val installed = currentInstalledInfo(appContext)
        val installedLabel = "v${installed.versionName} (Build ${installed.versionCode})"

        if (repoSlug == null) {
            _uiState.update {
                it.copy(
                    configuredRepo = null,
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
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
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
                    status = AppUpdateStatus.Checking
                )
            }
        }

        val releasesResult = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$repoSlug/releases?per_page=8")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "TacticiansForge-Android/${installed.versionName}")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("GitHub API returned HTTP ${resp.code}")
                    }
                    val body = resp.body?.string().orEmpty()
                    GitHubReleaseParser.parseGitHubReleases(body, installed.isDebugBuild)
                }
            }
        }

        val releases = releasesResult.getOrElse { err ->
            _uiState.update {
                it.copy(
                    configuredRepo = repoSlug,
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
                    status = if (userInitiated) {
                        AppUpdateStatus.Error(err.localizedMessage ?: "Failed to reach GitHub Releases")
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
            val dismissedKey = prefsStore.getDismissedAppReleaseKey()
            val isFreshReleaseNotification = lastNotifiedKey != candidate.releaseKey

            var osPosted = false
            if (dispatchOsNotification && alertsEnabled && (isFreshReleaseNotification || userInitiated)) {
                osPosted = TftNotificationHelper.postAppUpdateNotification(
                    context = appContext,
                    release = candidate
                )
                if (osPosted || isFreshReleaseNotification) {
                    prefsStore.setLastNotifiedAppReleaseKey(candidate.releaseKey)
                }
            }

            val shouldShowModal = userInitiated ||
                (showInAppModalOnLaunch && dismissedKey != candidate.releaseKey)

            _uiState.update {
                it.copy(
                    configuredRepo = repoSlug,
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
                    status = AppUpdateStatus.UpdateAvailable(candidate),
                    showPromptModal = it.showPromptModal || shouldShowModal
                )
            }

            return AppUpdateCheckOutcome(
                updateAvailable = true,
                release = candidate,
                osNotificationPosted = osPosted,
                inAppNotificationTriggered = isFreshReleaseNotification || shouldShowModal
            )
        } else {
            _uiState.update {
                it.copy(
                    configuredRepo = repoSlug,
                    installedVersionLabel = installedLabel,
                    appUpdateAlertsEnabled = alertsEnabled,
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

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildInstallPermissionSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    suspend fun downloadAndInstallUpdate(
        context: Context,
        release: GitHubReleaseInfo,
        httpClient: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ) = downloadMutex.withLock {
        val appContext = context.applicationContext
        _uiState.update {
            it.copy(
                status = AppUpdateStatus.Downloading(
                    release = release,
                    progress = 0f,
                    bytesDownloaded = 0L,
                    totalBytes = release.apkSizeBytes
                ),
                showPromptModal = true
            )
        }

        val downloadResult = withContext(Dispatchers.IO) {
            runCatching {
                val updatesDir = appContext.cacheDir.resolve("updates").apply { mkdirs() }
                updatesDir.listFiles()?.forEach { existing ->
                    if (existing.isFile) existing.delete()
                }
                val partFile = updatesDir.resolve("tacticians-forge-${release.apkAssetId}.apk.part")
                val finalApk = updatesDir.resolve("tacticians-forge-${release.apkAssetId}.apk")

                val request = Request.Builder()
                    .url(release.apkDownloadUrl)
                    .header("User-Agent", "TacticiansForge-Android/${BuildConfig.VERSION_NAME}")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed with HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty APK download stream")
                    val total = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes
                    var downloaded = 0L
                    var lastEmitMs = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastEmitMs >= 80L || (total > 0L && downloaded >= total)) {
                                    lastEmitMs = now
                                    val progress = if (total > 0L) {
                                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    _uiState.update { state ->
                                        state.copy(
                                            status = AppUpdateStatus.Downloading(
                                                release = release,
                                                progress = progress,
                                                bytesDownloaded = downloaded,
                                                totalBytes = total
                                            )
                                        )
                                    }
                                }
                            }
                            output.fd.sync()
                        }
                    }
                }

                if (!isValidApkZipHeader(partFile)) {
                    partFile.delete()
                    throw IllegalStateException("Downloaded file is not a valid Android APK package.")
                }
                if (finalApk.exists()) finalApk.delete()
                if (!partFile.renameTo(finalApk)) {
                    partFile.copyTo(finalApk, overwrite = true)
                    partFile.delete()
                }
                finalApk
            }
        }

        val apkFile = downloadResult.getOrElse { err ->
            _uiState.update {
                it.copy(
                    status = AppUpdateStatus.Error(
                        message = err.localizedMessage ?: "Failed to download update APK",
                        release = release
                    )
                )
            }
            return@withLock
        }

        val hasInstallPerm = canRequestPackageInstalls(appContext)
        _uiState.update {
            it.copy(
                status = AppUpdateStatus.ReadyToInstall(
                    release = release,
                    apkFilePath = apkFile.absolutePath,
                    needsInstallPermission = !hasInstallPerm
                ),
                pendingAutoInstallAfterPermission = !hasInstallPerm
            )
        }

        if (hasInstallPerm) {
            launchPackageInstaller(appContext, apkFile, release)
        }
    }

    suspend fun resumePendingInstallIfPermitted(context: Context): Boolean {
        val status = _uiState.value.status as? AppUpdateStatus.ReadyToInstall ?: return false
        val apkFile = File(status.apkFilePath)
        if (!apkFile.exists()) {
            _uiState.update {
                it.copy(
                    status = AppUpdateStatus.UpdateAvailable(status.release),
                    pendingAutoInstallAfterPermission = false
                )
            }
            return false
        }
        val canInstall = canRequestPackageInstalls(context)
        if (!canInstall) return false

        _uiState.update {
            it.copy(
                status = status.copy(needsInstallPermission = false),
                pendingAutoInstallAfterPermission = false
            )
        }
        return launchPackageInstaller(context, apkFile, status.release)
    }

    suspend fun launchPackageInstaller(
        context: Context,
        apkFile: File,
        release: GitHubReleaseInfo
    ): Boolean {
        if (!apkFile.exists()) return false
        LocalPreferencesStore(context.applicationContext).setLastInstalledApkAssetId(release.apkAssetId)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(installIntent)
            true
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    status = AppUpdateStatus.Error(
                        message = err.localizedMessage ?: "Unable to open Android package installer.",
                        release = release
                    )
                )
            }
        }.getOrDefault(false)
    }

    internal fun isValidApkZipHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 1024L) return false
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(2)
                stream.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
        }.getOrDefault(false)
    }

    fun formatMegabytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
