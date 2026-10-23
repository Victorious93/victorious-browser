plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.victorious.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.victorious.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing is provided by CI secrets — do not commit a release keystore.
        // create("release") {
        //     storeFile = file(System.getenv("RELEASE_KEYSTORE_PATH") ?: "release.keystore")
        //     storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        //     keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        //     keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        // }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Netty / BouncyCastle ship metadata that collides when merged.
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/native-image/**"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            pickFirsts += "/META-INF/MANIFEST.MF"
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.webkit)

    // Network inspection layer (localhost proxy + local MITM certs)
    implementation(libs.littleproxy)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.bouncycastle.bcprov)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // WireGuard VPN client (GoBackend/VpnService) — see NOTICE
    implementation(libs.wireguard.tunnel)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    // com.wireguard.config.* (Config/Interface/Peer/Key) has no Android or native-code
    // dependency, so it's usable directly in a plain JUnit test — see WireGuardConfigBuilderTest.
    testImplementation(libs.wireguard.tunnel)
}
