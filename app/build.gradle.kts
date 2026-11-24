plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gediadeepak.smsapp"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.gediadeepak.smsapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
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
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
}
