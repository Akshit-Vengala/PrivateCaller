import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing credentials, loaded from a git-ignored keystore.properties.
// Absent on machines/CI without the keystore — release builds are then unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.privatecaller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.privatecaller"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // GitHub repo the in-app updater checks for new releases.
        // >>> Set these to your GitHub username and repo name. <<<
        buildConfigField("String", "GITHUB_OWNER", "\"Akshit-Vengala\"")
        buildConfigField("String", "GITHUB_REPO", "\"PrivateCaller\"")
    }

    // Two editions of the app:
    //  - full:     everything (SmartUnblock notification reading + in-app GitHub
    //              updater). Sideload only; Play Protect flags these features.
    //  - playstore: privacy-/policy-safe subset. NO SmartUnblock, NO in-app
    //              installer, so the manifest declares none of the permissions
    //              (notification listener / install packages) that get flagged.
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            // SmartUnblock + in-app updater live in src/full/ (code) and
            // src/full/AndroidManifest.xml (permissions/service).
            // Custom incoming-call overlay popup (needs SYSTEM_ALERT_WINDOW,
            // declared only in src/full/AndroidManifest.xml).
            buildConfigField("boolean", "HAS_CALL_OVERLAY", "true")
        }
        create("playstore") {
            dimension = "edition"
            // No SmartUnblock, no in-app updater — see src/playstore/.
            // No overlay either: keeps the Play APK free of SYSTEM_ALERT_WINDOW.
            buildConfigField("boolean", "HAS_CALL_OVERLAY", "false")
        }
    }

    signingConfigs {
        create("release") {
            val storeName = keystoreProps.getProperty("storeFile")
            if (storeName != null && rootProject.file(storeName).exists()) {
                storeFile = rootProject.file(storeName)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Non-debuggable, AOT-compiled build you can run straight from Android
        // Studio (uses the debug signing key, no minify). Use this to judge real
        // performance — the normal `debug` variant runs interpreted and is much
        // slower at startup and scrolling, which is a measurement artifact, not
        // the app's true speed.
        create("fast") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // The release-only "lintVital" pass is memory-heavy and unnecessary for
        // a personal sideload build — it was OOM-crashing the Gradle daemon.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.ui.tooling)
}
