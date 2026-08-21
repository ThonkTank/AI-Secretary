import java.util.Properties

plugins {
    id("com.android.application")
}

val releaseContract = Properties().apply {
    rootProject.file("release/release.properties").inputStream().use(::load)
}
val configuredVersionCode = providers.gradleProperty("versionCode").orElse("2").get().toInt()
val configuredVersionName = providers.gradleProperty("versionName").orElse("0.2.0").get()
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning")
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
    compileSdk = 35

    defaultConfig {
        applicationId = "de.thonktank.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        getByName("release") {
            // CI supplies this signing configuration. A local release stays unsigned for testing.
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        getByName("androidTest").assets.srcDir(rootProject.file("release/upgrade-fixtures"))
        getByName("test").resources.srcDir(rootProject.file("release/upgrade-fixtures"))
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
    //noinspection GradleDependency -- newer Core releases require a newer compile SDK.
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.customview:customview:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.11.0")
    // Room migration tests require JSON 1.8.1; align SavedState's serialization core with it.
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1"))
    //noinspection GradleDependency -- 1.11+ requires compileSdk 36 and AGP 8.9.1.
    implementation("androidx.activity:activity:1.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}
