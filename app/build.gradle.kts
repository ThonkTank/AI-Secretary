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
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

val robolectricTempDir = layout.buildDirectory.dir("tmp/robolectric")
tasks.withType<Test>().configureEach {
    doFirst { robolectricTempDir.get().asFile.mkdirs() }
    systemProperty("java.io.tmpdir", robolectricTempDir.get().asFile.absolutePath)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}

dependencies {
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.11.0")
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
