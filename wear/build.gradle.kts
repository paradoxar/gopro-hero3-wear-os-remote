plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.wear"
    //noinspection GradleDependency
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.wear"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)

    // This is the only core library needed for the Ambient Lifecycle Observer!
    implementation(libs.wear)

    //noinspection UseTomlInstead
    compileOnly("com.google.android.wearable:wearable:2.9.0")
}