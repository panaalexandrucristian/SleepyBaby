plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.firebasePerformance)
}

android {
    namespace = "ro.pana.sleepybaby"
    compileSdk = 36

    val releaseStoreFilePath = project.findProperty("sleepybabyReleaseStoreFile") as String?
    val releaseStorePassword = project.findProperty("sleepybabyReleaseStorePassword") as String?
    val releaseKeyAlias = project.findProperty("sleepybabyReleaseKeyAlias") as String?
    val releaseKeyPassword = project.findProperty("sleepybabyReleaseKeyPassword") as String?
    val isReleaseSigningConfigured = !releaseStoreFilePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    defaultConfig {
        applicationId = "ro.pana.sleepybaby"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (isReleaseSigningConfigured) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                project.logger.warn(
                    "SleepyBaby release signing config missing. " +
                        "Add sleepybabyReleaseStoreFile, sleepybabyReleaseStorePassword, " +
                        "sleepybabyReleaseKeyAlias and sleepybabyReleaseKeyPassword to gradle.properties."
                )
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            versionNameSuffix = "-debug"
            manifestPlaceholders["crashlyticsCollectionEnabled"] = false
            manifestPlaceholders["performanceCollectionEnabled"] = false
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "false")
            buildConfigField("Boolean", "ENABLE_CRASHLYTICS", "false")
            buildConfigField("Boolean", "ENABLE_PERFORMANCE_MONITORING", "false")
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["crashlyticsCollectionEnabled"] = true
            manifestPlaceholders["performanceCollectionEnabled"] = true
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "true")
            buildConfigField("Boolean", "ENABLE_CRASHLYTICS", "true")
            buildConfigField("Boolean", "ENABLE_PERFORMANCE_MONITORING", "true")
            if (isReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.perf.ktx)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)

    implementation(libs.datastore.preferences)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
