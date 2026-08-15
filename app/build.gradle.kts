plugins {
    id("com.android.application")
}

android {
    namespace = "de.thonktank.autosecretary"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.thonktank.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("SIGNING_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // CI supplies this signing configuration. A local release can still be built for testing.
            if (!System.getenv("SIGNING_STORE_FILE").isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }
}
