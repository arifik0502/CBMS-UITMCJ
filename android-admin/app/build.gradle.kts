import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Supabase URL + anon key come from android-admin/local.properties (git-ignored). Never the service_role key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val releaseStoreFile = localProps.getProperty("release.storeFile")
val releaseStorePassword = localProps.getProperty("release.storePassword")
val releaseKeyAlias = localProps.getProperty("release.keyAlias")
val releaseKeyPassword = localProps.getProperty("release.keyPassword")

android {
    namespace = "com.crispyburger.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crispyburger.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.2.9"
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("supabase.anonKey", "")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile?.let { rootProject.file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation("junit:junit:4.13.2")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
