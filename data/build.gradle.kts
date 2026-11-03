// Persistence layer: save slots and game history.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.starsmuggler.data"
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
