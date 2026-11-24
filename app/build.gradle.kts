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

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Opt-in lifecycle regression runs stay isolated from the daily app.
            if (providers.gradleProperty("diagnosticsLifecycleTest").isPresent) {
                applicationIdSuffix = ".testing"
            }
        }
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
    if (!providers.gradleProperty("diagnosticsLifecycleTest").isPresent) {
        defaultConfig.testInstrumentationRunnerArguments["notClass"] = "com.example.budgetapp.capture.notification.DiagnosticsLifecycleTest"
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    testBuildType = if (providers.gradleProperty("diagnosticsLifecycleTest").isPresent) "debug" else "qa"
    testOptions {
        animationsDisabled = true
    }
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
    constraints {
        implementation(libs.serialization.core) {
            because("Room migration testing 2.8.5 needs core 1.8.1; instrumentation inherits app runtime versions")
        }
    }
    implementation(libs.work.runtime)
    androidTestImplementation(libs.work.testing)
    implementation(libs.fragment)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.savedstate)
    implementation(libs.recyclerview)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    androidTestImplementation(libs.room.testing)
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
