plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val ernNamesOwner = providers.gradleProperty("ernNames.owner").getOrElse("SEParsons1")
val ernNamesRepo = providers.gradleProperty("ernNames.repo").getOrElse("ern-names")
val ernNamesToken = providers.gradleProperty("ernNames.token")
    .orElse(providers.gradleProperty("gpr.key"))
    .getOrElse("")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.vicarriers.maxicodescanner"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vicarriers.maxicodescanner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ERN_NAMES_OWNER", "\"$ernNamesOwner\"")
        buildConfigField("String", "ERN_NAMES_REPO", "\"$ernNamesRepo\"")
        buildConfigField("String", "ERN_NAMES_TOKEN", "\"$ernNamesToken\"")
    }

    flavorDimensions += "device"
    productFlavors {
        create("zebra") {
            dimension = "device"
        }
        create("phone") {
            dimension = "device"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    add("phoneImplementation", libs.androidx.camera.core)
    add("phoneImplementation", libs.androidx.camera.camera2)
    add("phoneImplementation", libs.androidx.camera.lifecycle)
    add("phoneImplementation", libs.androidx.camera.view)
    add("phoneImplementation", libs.mlkit.barcode)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
