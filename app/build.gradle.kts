plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.runconnect.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.runconnect.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        // Mapbox public token — replace with your token from account.mapbox.com
        manifestPlaceholders["MAPBOX_ACCESS_TOKEN"] =
            project.findProperty("MAPBOX_ACCESS_TOKEN") as String? ?: "YOUR_MAPBOX_PUBLIC_TOKEN"

        // Garmin OAuth credentials — add to local.properties or CI secrets
        buildConfigField("String", "GARMIN_CONSUMER_KEY",
            "\"${project.findProperty("GARMIN_CONSUMER_KEY") ?: ""}\"")
        buildConfigField("String", "GARMIN_CONSUMER_SECRET",
            "\"${project.findProperty("GARMIN_CONSUMER_SECRET") ?: ""}\"")
        buildConfigField("int", "DATABASE_VERSION", "1")
    }

    signingConfigs {
        create("release") {
            storeFile = (project.findProperty("RELEASE_KEYSTORE_PATH") as String?)?.let { file(it) }
            storePassword = project.findProperty("RELEASE_KEYSTORE_PASSWORD") as String? ?: ""
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String? ?: ""
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String? ?: ""
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
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Health Connect
    implementation(libs.health.connect)

    // Mapbox
    implementation(libs.mapbox.maps)

    // Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Image loading
    implementation(libs.coil.compose)

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
