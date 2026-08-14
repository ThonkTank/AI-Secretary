plugins { id("autosecretary.android-library") }

android {
    namespace = "com.autosecretary.infrastructure"
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = rootProject.file("schemas").absolutePath
                arguments["room.incremental"] = "true"
            }
        }
    }

}

dependencies {
    api(project(":core"))
    implementation("androidx.core:core:1.12.0")
    api("androidx.room:room-runtime:2.6.1")
    api("com.google.mediapipe:tasks-genai:0.10.27")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
