plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.mindflow.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mindflow.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase configuration is injected at build time so no key is committed.
        //   -Pmindflow.supabaseUrl=https://<ref>.supabase.co -Pmindflow.supabaseAnonKey=<publishable key>
        // Both values are publishable (the app is protected by Row Level Security),
        // but they are still kept out of source control and supplied per-build / by CI.
        val supabaseUrl = (project.findProperty("mindflow.supabaseUrl") as String?) ?: ""
        val supabaseAnonKey = (project.findProperty("mindflow.supabaseAnonKey") as String?) ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    // Release signing is driven by Gradle properties so no secret is committed.
    // Provide -Pmindflow.storeFile=... -Pmindflow.storePassword=... -Pmindflow.keyAlias=... -Pmindflow.keyPassword=...
    // When absent, the release build falls back to the debug key (fine for a demo APK).
    signingConfigs {
        create("release") {
            val storePath = (project.findProperty("mindflow.storeFile") as String?)
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = project.findProperty("mindflow.storePassword") as String?
                keyAlias = project.findProperty("mindflow.keyAlias") as String?
                keyPassword = project.findProperty("mindflow.keyPassword") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (project.findProperty("mindflow.storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    lint {
        // The release build does not run lint-vital here: it is a very heavy analysis task
        // and is not needed to produce a signed APK. Run `./gradlew lint` separately in CI.
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Supabase Kotlin client: REST (PostgREST), Auth (GoTrue) and Realtime.
    val supabaseKt = "2.6.0"
    implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseKt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:$supabaseKt")
    implementation("io.github.jan-tennert.supabase:realtime-kt:$supabaseKt")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-core:2.3.12")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
