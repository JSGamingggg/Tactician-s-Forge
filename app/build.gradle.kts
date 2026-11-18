import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Base64
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val googleServicesFile = file("google-services.json")
if (!googleServicesFile.exists() || googleServicesFile.length() == 0L) {
  val defaultGsB64 = "ewogICJwcm9qZWN0X2luZm8iOiB7CiAgICAicHJvamVjdF9udW1iZXIiOiAiNTIyNDI1OTkwNzQ3IiwKICAgICJwcm9qZWN0X2lkIjogImdlbi1sYW5nLWNsaWVudC0wNTA4NzM2NTI4IiwKICAgICJzdG9yYWdlX2J1Y2tldCI6ICJnZW4tbGFuZy1jbGllbnQtMDUwODczNjUyOC5maXJlYmFzZXN0b3JhZ2UuYXBwIgogIH0sCiAgImNsaWVudCI6IFsKICAgIHsKICAgICAgImNsaWVudF9pbmZvIjogewogICAgICAgICJtb2JpbGVzZGtfYXBwX2lkIjogIjE6NTIyNDI1OTkwNzQ3OmFuZHJvaWQ6MTJkN2Q4ODA1ZThhNmJmZWIwODk3ZSIsCiAgICAgICAgImFuZHJvaWRfY2xpZW50X2luZm8iOiB7CiAgICAgICAgICAicGFja2FnZV9uYW1lIjogImNvbS5haXN0dWRpby5hZmZpbGlhdGVwaWxvdC5reG1wenEiCiAgICAgICAgfQogICAgICB9LAogICAgICAib2F1dGhfY2xpZW50IjogWwogICAgICAgIHsKICAgICAgICAgICJjbGllbnRfaWQiOiAiNTIyNDI1OTkwNzQ3LWhjbmJjZnZyNDJ0MDRxOGVjaHZhZmU5ZjdxZDJtb2ZuLmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwKICAgICAgICAgICJjbGllbnRfdHlwZSI6IDEsCiAgICAgICAgICAiYW5kcm9pZF9pbmZvIjogewogICAgICAgICAgICAicGFja2FnZV9uYW1lIjogImNvbS5haXN0dWRpby5hZmZpbGlhdGVwaWxvdC5reG1wenEiLAogICAgICAgICAgICAiY2VydGlmaWNhdGVfaGFzaCI6ICI0MDA2MTE4Zjk1ZTcyM2MxN2ViYmVmNDI3NWY2OWE2NDVkZGFkYmNkIgogICAgICAgICAgfQogICAgICAgIH0sCiAgICAgICAgewogICAgICAgICAgImNsaWVudF9pZCI6ICI1MjI0MjU5OTA3NDctNjhpbWRvMTcwcTRtOW9qNjVza2FpbGVuY3NwMTBpMjkuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLAogICAgICAgICAgImNsaWVudF90eXBlIjogMwogICAgICAgIH0KICAgICAgXSwKICAgICAgImFwaV9rZXkiOiBbCiAgICAgICAgewogICAgICAgICAgImN1cnJlbnRfa2V5IjogIkFJemFTeURNdzA1TC1EYnBvd01RN21hUXpIN2VidlA2TnFGVm54QSIKICAgICAgICB9CiAgICAgIF0sCiAgICAgICJzZXJ2aWNlcyI6IHsKICAgICAgICAiYXBwaW52aXRlX3NlcnZpY2UiOiB7CiAgICAgICAgICAib3RoZXJfcGxhdGZvcm1fb2F1dGhfY2xpZW50IjogWwogICAgICAgICAgICB7CiAgICAgICAgICAgICAgImNsaWVudF9pZCI6ICI1MjI0MjU5OTA3NDctNjhpbWRvMTcwcTRtOW9qNjVza2FpbGVuY3NwMTBpMjkuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLAogICAgICAgICAgICAgICJjbGllbnRfdHlwZSI6IDMKICAgICAgICAgICAgfQogICAgICAgICAgXQogICAgICAgIH0KICAgICAgfQogICAgfSwKICAgIHsKICAgICAgImNsaWVudF9pbmZvIjogewogICAgICAgICJtb2JpbGVzZGtfYXBwX2lkIjogIjE6NTIyNDI1OTkwNzQ3OmFuZHJvaWQ6NDQ2MDY3YWRjNDMyYTM2YWIwODk3ZSIsCiAgICAgICAgImFuZHJvaWRfY2xpZW50X2luZm8iOiB7CiAgICAgICAgICAicGFja2FnZV9uYW1lIjogImNvbS5haXN0dWRpby5haWdhbWVsZWFybmVyLnF4dm5rcCIKICAgICAgICB9CiAgICAgIH0sCiAgICAgICJvYXV0aF9jbGllbnQiOiBbCiAgICAgICAgewogICAgICAgICAgImNsaWVudF9pZCI6ICI1MjI0MjU5OTA3NDctaDFscTB0cWV0NGplZXRkdGpiNjVia2h0azVxNWY1cWQuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLAogICAgICAgICAgImNsaWVudF90eXBlIjogMSwKICAgICAgICAgICJhbmRyb2lkX2luZm8iOiB7CiAgICAgICAgICAgICJwYWNrYWdlX25hbWUiOiAiY29tLmFpc3R1ZGlvLmFpZ2FtZWxlYXJuZXIucXh2bmtwIiwKICAgICAgICAgICAgImNlcnRpZmljYXRlX2hhc2giOiAiZjM5NjM2YzFkNGQ3MTAwZThmN2UxYTUwZmJhOTgyMDdjMGUyYzdhMyIKICAgICAgICAgIH0KICAgICAgICB9LAogICAgICAgIHsKICAgICAgICAgICJjbGllbnRfaWQiOiAiNTIyNDI1OTkwNzQ3LTY4aW1kbzE3MHE0bTlvajY1c2thaWxlbmNzcDEwaTI5LmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwKICAgICAgICAgICJjbGllbnRfdHlwZSI6IDMKICAgICAgICB9CiAgICAgIF0sCiAgICAgICJhcGlfa2V5IjogWwogICAgICAgIHsKICAgICAgICAgICJjdXJyZW50X2tleSI6ICJBSXphU3lETXcwNUwtRGJwb3dNUTdtYVF6SDdlYnZQNk5xRlZueEEiCiAgICAgICAgfQogICAgICBdLAogICAgICAic2VydmljZXMiOiB7CiAgICAgICAgImFwcGludml0ZV9zZXJ2aWNlIjogewogICAgICAgICAgIm90aGVyX3BsYXRmb3JtX29hdXRoX2NsaWVudCI6IFsKICAgICAgICAgICAgewogICAgICAgICAgICAgICJjbGllbnRfaWQiOiAiNTIyNDI1OTkwNzQ3LTY4aW1kbzE3MHE0bTlvajY1c2thaWxlbmNzcDEwaTI5LmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwKICAgICAgICAgICAgICAiY2xpZW50X3R5cGUiOiAzCiAgICAgICAgICAgIH0KICAgICAgICAgIF0KICAgICAgICB9CiAgICAgIH0KICAgIH0sCiAgICB7CiAgICAgICJjbGllbnRfaW5mbyI6IHsKICAgICAgICAibW9iaWxlc2RrX2FwcF9pZCI6ICIxOjUyMjQyNTk5MDc0NzphbmRvaWQ6MmEzZTkwZGQ0MmIwOGQ1M2IwODk3ZSIsCiAgICAgICAgImFuZHJvaWRfY2xpZW50X2luZm8iOiB7CiAgICAgICAgICAicGFja2FnZV9uYW1lIjogImNvbS5haXN0dWRpby5wbGF5cGlsb3QucXZ0enJtIgogICAgICAgIH0KICAgICAgfSwKICAgICAgIm9hdXRoX2NsaWVudCI6IFsKICAgICAgICB7CiAgICAgICAgICAiY2xpZW50X2lkIjogIjUyMjQyNTk5MDc0Ny1vdTJsOW5odWNkcjNzMTdranBkcTBhNjA2dDJxY21iYy5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsCiAgICAgICAgICAiY2xpZW50X3R5cGUiOiAxLAogICAgICAgICAgImFuZHJvaWRfaW5mbyI6IHsKICAgICAgICAgICAgInBhY2thZ2VfbmFtZSI6ICJjb20uYWlzdHVkaW8ucGxheXBpbG90LnF2dHpybSIsCiAgICAgICAgICAgICJjZXJ0aWZpY2F0ZV9oYXNoIjogIjY3YjA0YjYyYTBkMTY0NmE2OWQyMjNkMmNlM2JjNWFhZTBkNDU5NTgiCiAgICAgICAgICB9CiAgICAgICAgfSwKICAgICAgICB7CiAgICAgICAgICAiY2xpZW50X2lkIjogIjUyMjQyNTk5MDc0Ny02OGltZG8xNzBxNG05b2o2NXNrYWlsZW5jc3AxMGkyOS5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsCiAgICAgICAgICAiY2xpZW50X3R5cGUiOiAzCiAgICAgICAgfQogICAgICBdLAogICAgICAiYXBpX2tleSI6IFsKICAgICAgICB7CiAgICAgICAgICAiY3VycmVudF9rZXkiOiAiQUl6YVN5RE13MDVMLURicG93TVE3bWFRekg3ZWJ2UDZOcUZWbnhBIgogICAgICAgIH0KICAgICAgXSwKICAgICAgInNlcnZpY2VzIjogewogICAgICAgICJhcHBpbnZpdGVfc2VydmljZSI6IHsKICAgICAgICAgICJvdGhlcl9wbGF0Zm9ybV9vYXV0aF9jbGllbnQiOiBbCiAgICAgICAgICAgIHsKICAgICAgICAgICAgICAiY2xpZW50X2lkIjogIjUyMjQyNTk5MDc0Ny02OGltZG8xNzBxNG05b2o2NXNrYWlsZW5jc3AxMGkyOS5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsCiAgICAgICAgICAgICAgImNsaWVudF90eXBlIjogMwogICAgICAgICAgICB9CiAgICAgICAgICBdCiAgICAgICAgfQogICAgICB9CiAgICB9LAogICAgewogICAgICAiY2xpZW50X2luZm8iOiB7CiAgICAgICAgIm1vYmlsZXNka19hcHBfaWQiOiAiMTo1MjI0MjU5OTA3NDc6YW5kcm9pZDo1NzVkMTk4NTczZmFlZjc5YjA4OTdlIiwKICAgICAgICAiYW5kcm9pZF9jbGllbnRfaW5mbyI6IHsKICAgICAgICAgICJwYWNrYWdlX25hbWUiOiAiY29tLmFpc3R1ZGlvLnRmdGZvcmdlLmt4bXpwcSIKICAgICAgICB9CiAgICAgIH0sCiAgICAgICJvYXV0aF9jbGllbnQiOiBbCiAgICAgICAgewogICAgICAgICAgImNsaWVudF9pZCI6ICI1MjI0MjU5OTA3NDctN2p2bGNjYmI0bjQ4MXMyZTh0NG45ZnA0dmFtM20zZHMuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLAogICAgICAgICAgImNsaWVudF90eXBlIjogMSwKICAgICAgICAgICJhbmRyb2lkX2luZm8iOiB7CiAgICAgICAgICAgICJwYWNrYWdlX25hbWUiOiAiY29tLmFpc3R1ZGlvLnRmdGZvcmdlLmt4bXpwcSIsCiAgICAgICAgICAgICJjZXJ0aWZpY2F0ZV9oYXNoIjogIjM4MDk2ZTE1NzU5MWNhOWQzMTI2ZDFiMmFkNzliNDMyOGRiZGM4ZDMiCiAgICAgICAgICB9CiAgICAgICAgfSwKICAgICAgICB7CiAgICAgICAgICAiY2xpZW50X2lkIjogIjUyMjQyNTk5MDc0Ny02OGltZG8xNzBxNG05b2o2NXNrYWlsZW5jc3AxMGkyOS5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsCiAgICAgICAgICAiY2xpZW50X3R5cGUiOiAzCiAgICAgICAgfQogICAgICBdLAogICAgICAiYXBpX2tleSI6IFsKICAgICAgICB7CiAgICAgICAgICAiY3VycmVudF9rZXkiOiAiQUl6YVN5RE13MDVMLURicG93TVE3bWFRekg3ZWJ2UDZOcUZWbnhBIgogICAgICAgIH0KICAgICAgXSwKICAgICAgInNlcnZpY2VzIjogewogICAgICAgICJhcHBpbnZpdGVfc2VydmljZSI6IHsKICAgICAgICAgICJvdGhlcl9wbGF0Zm9ybV9vYXV0aF9jbGllbnQiOiBbCiAgICAgICAgICAgIHsKICAgICAgICAgICAgICAiY2xpZW50X2lkIjogIjUyMjQyNTk5MDc0Ny02OGltZG8xNzBxNG05b2o2NXNrYWlsZW5jc3AxMGkyOS5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsCiAgICAgICAgICAgICAgImNsaWVudF90eXBlIjogMwogICAgICAgICAgICB9CiAgICAgICAgICBdCiAgICAgICAgfQogICAgICB9CiAgICB9CiAgXSwKICAiY29uZmlndXJhdGlvbl92ZXJzaW9uIjogIjEiCn0="
  googleServicesFile.writeBytes(Base64.getDecoder().decode(defaultGsB64))
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    val ciVersionCode = (project.findProperty("APP_VERSION_CODE") as String?)?.toIntOrNull() ?: 1
    val ciVersionName = (project.findProperty("APP_VERSION_NAME") as String?)
      ?.takeIf { it.isNotBlank() }
      ?: "1.0"
    val ciGitCommitSha = (project.findProperty("GIT_COMMIT_SHA") as String?)
      ?.trim()
      ?.take(40)
      .orEmpty()
    val ciBuildTimestampMs = (project.findProperty("BUILD_TIMESTAMP_MS") as String?)?.toLongOrNull() ?: 0L

    val envProps = Properties().apply {
      val envFile = rootProject.file(".env")
      if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
      }
    }
    fun resolveBuildSecret(vararg keys: String, fallback: String = ""): String {
      for (k in keys) {
        val fromProp = (project.findProperty(k) as String?)?.trim()?.removeSurrounding("\"")
        if (!fromProp.isNullOrBlank() && !fromProp.startsWith("MY_")) return fromProp
        val fromEnvFile = envProps.getProperty(k)?.trim()?.removeSurrounding("\"")
        if (!fromEnvFile.isNullOrBlank() && !fromEnvFile.startsWith("MY_")) return fromEnvFile
        val fromSysEnv = System.getenv(k)?.trim()?.removeSurrounding("\"")
        if (!fromSysEnv.isNullOrBlank() && !fromSysEnv.startsWith("MY_")) return fromSysEnv
      }
      return fallback
    }
    val resolvedGithubRepo = resolveBuildSecret("GITHUB_REPO", "GITHUB_REPOSITORY", fallback = "JSGamingggg/Tactician-s-Forge")
    val resolvedGithubToken = resolveBuildSecret("GITHUB_TOKEN", "GH_RELEASE_TOKEN", "GITHUB_PAT", "GH_TOKEN", "GITHUB_SECRET")

    applicationId = "com.aistudio.tftforge.kxmzpq"
    minSdk = 24
    targetSdk = 36
    versionCode = ciVersionCode
    versionName = ciVersionName

    buildConfigField("String", "GIT_COMMIT_SHA", "\"$ciGitCommitSha\"")
    buildConfigField("long", "BUILD_TIMESTAMP_MS", "${ciBuildTimestampMs}L")
    buildConfigField("String", "RESOLVED_GITHUB_REPO", "\"$resolvedGithubRepo\"")
    buildConfigField("String", "RESOLVED_GITHUB_TOKEN", "\"$resolvedGithubToken\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.maxHeapSize = "2048m"
        it.testLogging {
          events("passed", "skipped", "failed")
        }
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.messaging)

  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
