plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.starsmuggler"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.starsmuggler"
        minSdk = 26
        targetSdk = 36
        versionCode = 34
        versionName = "0.4.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates the development-only screens, such as the component gallery (SMUG-4).
        buildConfig = true
    }

    // Safeguards to localize later without rework (SMUG-6, PRD section 10).
    lint {
        error += "HardcodedText"
        error += "MissingTranslation"
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":content"))
    implementation(project(":data"))
    implementation(project(":dice"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Plain JVM checks of the sources and resources, such as no visible text in the code (SMUG-6).
    testImplementation(libs.junit4)
    testImplementation(kotlin("test"))
}
