plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.budgetapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.budgetapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".testing"
            matchingFallbacks += listOf("debug")
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    testBuildType = "qa"
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
}
