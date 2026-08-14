import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    id("com.android.application")
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val releaseContract = Properties().apply {
    rootProject.file("release/release.properties").inputStream().use(::load)
}
val releaseVersionCode = providers.gradleProperty("versionCode")
    .orElse(versionProperties.getProperty("VERSION_CODE")).get().toInt()
val releaseVersionName = providers.gradleProperty("versionName")
    .orElse(versionProperties.getProperty("VERSION_NAME")).get()
val productionKeystore = providers.gradleProperty("productionKeystore").orNull
    ?: System.getenv("PRODUCTION_KEYSTORE")
val productionStorePassword = providers.gradleProperty("productionStorePassword").orNull
    ?: System.getenv("PRODUCTION_STORE_PASSWORD")
val productionKeyAlias = providers.gradleProperty("productionKeyAlias").orNull
    ?: System.getenv("PRODUCTION_KEY_ALIAS")
val productionKeyPassword = providers.gradleProperty("productionKeyPassword").orNull
    ?: System.getenv("PRODUCTION_KEY_PASSWORD")
val productionSigningReady = listOf(
    productionKeystore,
    productionStorePassword,
    productionKeyAlias,
    productionKeyPassword,
).all { !it.isNullOrBlank() }
val requireProductionSigning = providers.gradleProperty("requireProductionSigning")
    .map(String::toBoolean).orElse(false).get()

if (requireProductionSigning && !productionSigningReady) {
    throw GradleException(
        "Production signing was required, but its four credentials are incomplete",
    )
}

extensions.configure<ApplicationExtension> {
    compileSdk = 35
    defaultConfig {
        applicationId = "com.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Auto Secretary"
        buildConfigField(
            "String", "REPOSITORY_OWNER",
            "\"${releaseContract.getProperty("repositoryOwner")}\"",
        )
        buildConfigField(
            "String", "REPOSITORY_NAME",
            "\"${releaseContract.getProperty("repositoryName")}\"",
        )
        buildConfigField(
            "String", "UPDATE_APK_ASSET",
            "\"${releaseContract.getProperty("apkAsset")}\"",
        )
        buildConfigField(
            "String", "UPDATE_METADATA_ASSET",
            "\"${releaseContract.getProperty("metadataAsset")}\"",
        )
    }
    signingConfigs {
        if (productionSigningReady) {
            create("production") {
                storeFile = file(productionKeystore!!)
                storePassword = productionStorePassword
                keyAlias = productionKeyAlias
                keyPassword = productionKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (productionSigningReady) {
                signingConfig = signingConfigs.getByName("production")
            }
            vcsInfo.include = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
