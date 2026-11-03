// Game content (systems, tiles, tables, events) loaded from versioned JSON.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.starsmuggler.content"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":engine"))
}
