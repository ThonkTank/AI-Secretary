import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseContract = Properties().apply {
    rootProject.file("release/release.properties").inputStream().use(::load)
}
val configuredVersionCode = providers.gradleProperty("versionCode").orElse("2").get().toInt()
val configuredVersionName = providers.gradleProperty("versionName").orElse("0.2.0").get()
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning")
    .map(String::toBoolean).orElse(false).get()
val useUpgradeProbeRunner = providers.gradleProperty("upgradeProbeRunner")
    .map(String::toBoolean).orElse(false).get()
val signingStoreFile = System.getenv("SIGNING_STORE_FILE")
val signingStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")
val signingReady = listOf(signingStoreFile, signingStorePassword, signingKeyAlias,
    signingKeyPassword).all { !it.isNullOrBlank() }

if (requireReleaseSigning && !signingReady) {
    throw GradleException("Release signing was required, but its credentials are incomplete")
}

android {
    namespace = "de.thonktank.autosecretary"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.thonktank.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = if (useUpgradeProbeRunner) {
            "de.thonktank.autosecretary.UpgradeProbeInstrumentation"
        } else {
            "androidx.test.runner.AndroidJUnitRunner"
        }
        buildConfigField("String", "UPDATE_REPOSITORY_OWNER",
            "\"${releaseContract.getProperty("repositoryOwner")}\"")
        buildConfigField("String", "UPDATE_REPOSITORY_NAME",
            "\"${releaseContract.getProperty("repositoryName")}\"")
        buildConfigField("String", "UPDATE_APK_ASSET",
            "\"${releaseContract.getProperty("apkAsset")}\"")
        buildConfigField("String", "UPDATE_METADATA_ASSET",
            "\"${releaseContract.getProperty("metadataAsset")}\"")
        buildConfigField("String", "UPDATE_TAG_PREFIX",
            "\"${releaseContract.getProperty("tagPrefix")}\"")
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            if (signingReady) {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Keep the debuggable product surface readable while trimming unused library code.
            // Compose Foundation otherwise pushes the unshrunk APK beyond the roadmap budget.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-debug.pro",
            )
        }
        create("instrumentation") {
            // Instrumented tests execute library code from a separate APK. Its complete runtime
            // graph is deliberately not inferred while shrinking the compact debug product.
            initWith(getByName("debug"))
            isMinifyEnabled = false
            matchingFallbacks += listOf("debug")
        }
        getByName("release") {
            // CI supplies this signing configuration. A local release stays unsigned for testing.
            if (signingReady) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-release.pro",
            )
        }
    }

    testBuildType = "instrumentation"

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        // The test target uses the product renderer plus debug-only harnesses without shrinking.
        getByName("instrumentation").setRoot("src/debug")
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        getByName("androidTest").assets.directories.add(
            rootProject.file("release/upgrade-fixtures").absolutePath,
        )
        getByName("test").resources.directories.add(
            rootProject.file("release/upgrade-fixtures").absolutePath,
        )
    }
}

val robolectricTempDir = layout.buildDirectory.dir("tmp/robolectric")
tasks.withType<Test>().configureEach {
    doFirst { robolectricTempDir.get().asFile.mkdirs() }
    systemProperty("java.io.tmpdir", robolectricTempDir.get().asFile.absolutePath)
    System.getProperty("woodgrain.benchmark")?.let {
        systemProperty("woodgrain.benchmark", it)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":today-core"))
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.customview:customview:1.1.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.11.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.11.0")
    implementation("androidx.work:work-runtime:2.11.2")
    // Room migration tests require JSON 1.8.1; align SavedState's serialization core with it.
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1"))
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
