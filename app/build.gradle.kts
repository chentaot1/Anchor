plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.anchor.adhd"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = if (providers.gradleProperty("anchorUiPreview").isPresent) "com.anchor.adhd.uipreview" else "com.anchor.adhd"
        minSdk = 28
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.0-preview.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf(providers.gradleProperty("anchorAbi").getOrElse("arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -O3"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DGGML_OPENMP=OFF",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DGGML_NATIVE=OFF",
                    "-DANCHOR_ENABLE_OPENCL=OFF"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "gguf"
        // Small, isolated emulator build for layout checks; phone releases always bundle the model.
        if (providers.gradleProperty("anchorUiPreview").isPresent) ignoreAssetsPattern = "*.gguf"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.named("preBuild").configure {
    doFirst {
        val version = JavaVersion.current()
        if (version < JavaVersion.VERSION_17) {
            throw GradleException(
                "Anchor requires JDK 17 or newer (detected $version). " +
                    "Set JAVA_HOME to JDK 17 before running Gradle."
            )
        }
        if (version > JavaVersion.VERSION_21) {
            logger.warn(
                "JDK $version detected. Anchor is tested with JDK 17–21; " +
                    "use JDK 17 or 21 locally if the build fails (CI uses JDK 17)."
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.browser:browser:1.8.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation(files("libs/samsung-health-data-api-1.1.0.aar"))
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("io.github.sceneview:sceneview:2.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}


