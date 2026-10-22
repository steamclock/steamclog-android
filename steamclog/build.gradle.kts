plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // Note, do not apply sentry plugins here; must be done in application module
    `maven-publish`
}

// Because the components are created only during the afterEvaluate phase, you must
// configure your publications using the afterEvaluate() lifecycle method.
afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                // Applies the component for the release build variant.
                from(components["release"])

                // You can then customize attributes of the publication as shown below.
                groupId = "com.steamclock.steamclog"
                artifactId = "release"
                // #157: JitPack passes the release tag as -Pversion; don't hard-code it here.
                version = project.version.toString()
            }
        }
    }
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    publishing {
        // Publish the release variant as an AAR
        singleVariant("release")
    }

    buildFeatures {
        buildConfig = true
    }

    kotlin {
        jvmToolchain(17)
    }

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }
    namespace = "com.steamclock.steamclog"

    testOptions {
        // #168: ExternalLogFileDestination calls android.os.Process.myPid() for each line, and
        // the android.jar stubs throw in JVM unit tests unless they return defaults.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // https://blog.jetbrains.com/kotlin/2020/07/kotlin-1-4-rc-released/
    // No longer need to include kotlin stdlib dependency
    implementation(libs.kotlin.reflect)

    // #150: api, because the public API exposes Timber types (addCustomTree takes a Timber.Tree).
    api(libs.timber)
    // https://github.com/getsentry/sentry-java/releases
    // #150: api, so an app can call Sentry directly without its own Sentry dependency; SteamcLog
    // provides Sentry for the apps that use it (see the README).
    api(libs.sentry.android)

    testImplementation(libs.junit)
    // Instrumented tests only (steamclog/src/androidTest); not part of the published library.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
