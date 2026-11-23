import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Supabase URL + anon key + developer email come from android-admin/local.properties (git-ignored).
// Never put the service_role key here.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun localProp(name: String): String? = localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

fun quoted(value: String?): String =
    "\"" + (value ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseStoreFile = localProp("release.storeFile")
val releaseStorePassword = localProp("release.storePassword")
val releaseKeyAlias = localProp("release.keyAlias")
val releaseKeyPassword = localProp("release.keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { it != null }

android {
    namespace = "com.crispyburger.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crispyburger.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.6.0"
        buildConfigField("String", "SUPABASE_URL", quoted(localProp("supabase.url")))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoted(localProp("supabase.anonKey")))
        // Optional sign-in email prefill. Supabase profile roles remain the real gate.
        buildConfigField("String", "DEVELOPER_EMAIL", quoted(localProp("developer.email")))
        // Public customer website address, used by the Website tab's live preview.
        buildConfigField("String", "SITE_URL", quoted(localProp("site.url")))
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
