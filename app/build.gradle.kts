plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.y20k.escapepod"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.y20k.escapepod"
        minSdk = 25
        targetSdk = 37
        versionCode = 54
        versionName = "1.6.8"
    }

    androidResources {
        // Languages listed here must also be listed in
        // /app/src/main/res/xml/locales_config.xml — the two lists are synced by hand.
        localeFilters += listOf("en", "ca", "da", "de", "es", "et", "eu", "fi", "fr", "fr-rCA", "he", "hr", "it", "nb-rNO", "nl", "pl", "pt-rBR", "pt", "ru", "sv", "ta", "tr", "uk", "zh-rCN")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }


    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
        debug {
        }
    }

    lint {
        disable += "MissingTranslation"
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.material)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.media)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.gson)
    implementation(libs.volley)

    implementation(libs.glide)

}
