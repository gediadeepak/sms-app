plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.sandesh.watch"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.sandesh.app"
        minSdk = 23
        targetSdk = 23 // Sony SWR50 / Android Wear 1.5, not a modern Play Store watch release.
        versionCode = 14
        versionName = "0.8.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    lint { disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi") }
    val keys = listOf("SANDESH_KEYSTORE_FILE", "SANDESH_KEYSTORE_PASSWORD", "SANDESH_KEY_ALIAS", "SANDESH_KEY_PASSWORD").associateWith { providers.environmentVariable(it).orNull }
    if (keys.values.all { !it.isNullOrBlank() }) {
        val ownerKey = signingConfigs.create("ownerKey") {
            storeFile = file(keys.getValue("SANDESH_KEYSTORE_FILE")!!)
            storePassword = keys.getValue("SANDESH_KEYSTORE_PASSWORD")
            keyAlias = keys.getValue("SANDESH_KEY_ALIAS")
            keyPassword = keys.getValue("SANDESH_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = ownerKey
    }
}
dependencies {
    implementation(project(":watch-protocol"))
    // Deliberate compatibility pin: the connected Sony watch has Google Play services 8.7.
    implementation("com.google.android.gms:play-services-wearable:8.4.0")
}
