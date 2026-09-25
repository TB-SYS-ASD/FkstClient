plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tb.fkst"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tb.fkst"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "1.8.0"
        resourceConfigurations += listOf("zh", "en")
    }

    // 自签 keystore（android/app/dev.keystore）不入库。
    // 本地放了就用它，签名固定、方便覆盖升级；没放则回落到 debug 签名。
    signingConfigs {
        create("dev") {
            val ks = file("dev.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("FKST_STORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("FKST_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("FKST_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有自签 keystore 就用它（可直接安装 / 覆盖升级），否则用 debug 签名
            signingConfig = if (file("dev.keystore").exists()) {
                signingConfigs.getByName("dev")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME / VERSION_CODE（关于页、更新检查用）
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
