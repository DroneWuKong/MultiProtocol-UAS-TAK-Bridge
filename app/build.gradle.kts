plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dronewukong.takbridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dronewukong.takbridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.0"
    }

    providers.environmentVariable("TAK_DEBUG_KEYSTORE").orNull?.let { path ->
        signingConfigs.getByName("debug").storeFile = file(path)
    }

    val releaseKeyPath = providers.environmentVariable("TAK_RELEASE_KEYSTORE").orNull
    if (releaseKeyPath != null) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeyPath)
            storePassword = providers.environmentVariable("TAK_RELEASE_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("TAK_RELEASE_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("TAK_RELEASE_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            if (releaseKeyPath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true
    // Robolectric/Conscrypt's JVM TLS adapter needs access to the JDK socket address.
    testOptions.unitTests.all { it.jvmArgs("--add-opens=java.base/java.net=ALL-UNNAMED") }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    // USB Serial - for drone telemetry
    implementation("com.github.mik3y:usb-serial-for-android:3.7.3")

    // NGA MGRS library - the real deal from National Geospatial-Intelligence Agency
    implementation("mil.nga.mgrs:mgrs-android:2.2.2")

    // OSMDroid - open source map (no API key, works offline)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}

// Never silently distribute an unsigned release or substitute a disposable debug key.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }) {
        listOf("TAK_RELEASE_KEYSTORE", "TAK_RELEASE_STORE_PASSWORD", "TAK_RELEASE_KEY_ALIAS", "TAK_RELEASE_KEY_PASSWORD")
            .forEach { check(!System.getenv(it).isNullOrBlank()) { "Release signing requires $it; see docs/V1_READINESS.md" } }
    }
}
