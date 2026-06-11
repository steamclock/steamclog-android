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
                version = "v2.5"
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
}

dependencies {
    // https://blog.jetbrains.com/kotlin/2020/07/kotlin-1-4-rc-released/
    // No longer need to include kotlin stdlib dependency
    implementation(libs.kotlin.reflect)

    implementation(libs.timber)
    // https://github.com/getsentry/sentry-java/releases
    implementation(libs.sentry.android)

    testImplementation(libs.junit)
}
