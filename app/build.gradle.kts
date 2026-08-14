import java.util.Properties

plugins { id("com.android.application") }

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
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
    productionKeystore, productionStorePassword, productionKeyAlias, productionKeyPassword
).all { !it.isNullOrBlank() }
val requireProductionSigning = providers.gradleProperty("requireProductionSigning")
    .map(String::toBoolean).orElse(false).get()
if (requireProductionSigning && !productionSigningReady) {
    throw GradleException("Production signing was required, but its four credentials are incomplete")
}

android {
    namespace = "com.autosecretary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Auto Secretary"
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
        release {
            isMinifyEnabled = false
            if (productionSigningReady) signingConfig = signingConfigs.getByName("production")
            vcsInfo { include = false }
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
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":infrastructure"))
    implementation(project(":presentation"))
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.work:work-runtime:2.10.1")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.work:work-testing:2.10.1")
    testImplementation("com.tngtech.archunit:archunit-junit4:1.4.1")
    testImplementation("org.robolectric:robolectric:4.14.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
