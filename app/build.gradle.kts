plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
// Account features stay unavailable, without fake sign-in, until the owner's Firebase config is present.
if (file("google-services.json").exists()) { apply(plugin = "com.google.gms.google-services") }

android {
    namespace = "com.gediadeepak.smsapp"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.sandesh.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 12
        versionName = "0.6.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    val signingValues = listOf("SANDESH_KEYSTORE_FILE", "SANDESH_KEYSTORE_PASSWORD", "SANDESH_KEY_ALIAS", "SANDESH_KEY_PASSWORD").associateWith { providers.environmentVariable(it).orNull }
    if (signingValues.values.all { !it.isNullOrBlank() }) {
        val releaseKey = signingConfigs.create("releaseKey") {
            storeFile = file(signingValues.getValue("SANDESH_KEYSTORE_FILE")!!)
            storePassword = signingValues.getValue("SANDESH_KEYSTORE_PASSWORD")
            keyAlias = signingValues.getValue("SANDESH_KEY_ALIAS")
            keyPassword = signingValues.getValue("SANDESH_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = releaseKey
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.compose)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.integrity)
    implementation(libs.credentials)
    implementation(libs.credentials.google)
    // Google sign-in pulls in an older Fragment; Activity Result requires a modern version.
    implementation(libs.fragment)
    implementation(libs.google.id)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
}
