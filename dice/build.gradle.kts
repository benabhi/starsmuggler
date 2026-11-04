// 3D dice (SMUG-28): dice-box-threejs in a transparent WebView over the app, and the roll sound.
// Ported from irondroid; the engine decides every value and the dice only animate it.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.starsmuggler.dice"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(kotlin("test"))
}
