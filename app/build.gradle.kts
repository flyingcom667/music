plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tidalmusic.player"

    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.tidalmusic.player"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        // `lintVital` is wired into the release build. The lint tool is resolved as a Maven
        // artifact that is not part of this offline build environment, so enabling it would
        // make `assembleRelease` fail on dependency resolution rather than on a real problem.
        // Lint is run from Android Studio's inspection engine instead.
        checkReleaseBuilds = false
    }
}

configurations.configureEach {
    resolutionStrategy {
        // This build resolves from a pre-populated Gradle cache with no network access. A few
        // transitive modules are only cached at other, binary-compatible versions, so they are
        // pinned here instead of failing the build:
        //  - kotlin-stdlib-jdk7 is an empty relocation shim since Kotlin 1.8, so any 1.9+ copy
        //    is equivalent to the 1.8.22 copy that kotlin-stdlib requests;
        //  - okio and exifinterface are patch-compatible within their major/minor version.
        val pins = mapOf(
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7" to "1.9.20",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8" to "1.9.20",
            "org.jetbrains.kotlin:kotlin-stdlib-common" to "2.4.21",
            "com.squareup.okio:okio" to "3.18.1",
            "com.squareup.okio:okio-jvm" to "3.18.1",
            "androidx.exifinterface:exifinterface" to "1.4.2",
        )
        eachDependency {
            pins["${requested.group}:${requested.name}"]?.let { useVersion(it) }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Material 3 1.4.0 targets ripple 1.8.x; the version resolved transitively has no cached
    // artifact, so the compatible 1.10.4 build is requested explicitly.
    implementation("androidx.compose.material:material-ripple:1.10.4")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
