import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
}

// Kredensial keystore release dibaca dari local.properties (git-ignored),
// BUKAN di-hardcode di sini — file ini ikut ter-commit ke repo, keystore-nya
// tidak boleh. Lihat local.properties untuk cara isinya.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile = localProps.getProperty("RELEASE_STORE_FILE")
val releaseSigningProperties = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD"
)
val hasReleaseSigning = releaseSigningProperties.all { key ->
    !localProps.getProperty(key).isNullOrBlank()
} && !releaseStoreFile.isNullOrBlank() && rootProject.file(releaseStoreFile).isFile

android {
    namespace = "com.sukashawarma.pos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sukashawarma.pos"
        minSdk = 26
        targetSdk = 34
        versionCode = 67
        versionName = "1.0.66"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SUPABASE_URL", "\"https://khpkoreaaucvyqfhynfq.supabase.co\"")
        buildConfigField("String", "STOK_WEB_URL", "\"https://stok.sukashawarma.com\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtocGtvcmVhYXVjdnlxZmh5bmZxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA5NjMyOTIsImV4cCI6MjA5NjUzOTI5Mn0.RdsvP6OKs6aiRnqqd02BYiv5gzbh4uGqO88dapo0Gso\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // APK rilis wajib memakai keystore permanen yang sama agar Android
            // menerima update tanpa uninstall. Task release diblokir di bawah
            // bila konfigurasi signing belum lengkap; debug build tetap tersedia.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Strong skipping. Tanpa ini, composable dengan parameter "unstable" tidak
        // pernah boleh di-skip: OrderCard menerima Order yang berisi List<OrderItem>
        // (List = unstable), plus 6 lambda yang dibuat inline di DashboardScreen dan
        // tidak ter-memoize — jadi SETIAP kartu pesanan disusun ulang dari nol di
        // setiap recomposition, termasuk di tiap frame animasi buka/tutup sidebar.
        // Flag ini membandingkan parameter unstable per-instance dan otomatis
        // me-remember lambda, sehingga kartu yang datanya tidak berubah benar-benar
        // di-skip. Murni perilaku compiler — tidak ada satu pun kode UI yang berubah.
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Hentikan task release sebelum ada artefak unsigned/debug-signed yang berisiko
// terdistribusi. Pemeriksaan task graph tidak mengganggu build variant debug.
gradle.taskGraph.whenReady {
    val requestsReleaseArtifact = allTasks.any { task ->
        task.name.contains("release", ignoreCase = true)
    }
    if (requestsReleaseArtifact && !hasReleaseSigning) {
        throw GradleException(
            "Release build diblokir: lengkapi RELEASE_STORE_FILE, " +
                "RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, dan " +
                "RELEASE_KEY_PASSWORD di local.properties serta pastikan " +
                "keystore release tersedia. Jangan memakai debug signing untuk APK rilis."
        )
    }
}

dependencies {
    // AndroidX & Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose & Material Design 3 (Tablet UI)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Room Database (Offline-First)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Networking (Retrofit + OkHttp)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // File-by-file APK delta (Google Archive Patcher, maintained Apache-2.0 fork).
    // Only the patch crosses the network; the signed APK is reconstructed and
    // SHA-256 verified locally before PackageInstaller sees it.
    // 3.0.0 targets Java 8/Android. 3.0.1 was published as Java 21 bytecode.
    implementation("com.eidu:archive-patcher:3.0.0")

    // Image Loading (Coil for Compose)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // QR pairing kiosk dirender lokal; tautan login tidak dikirim ke layanan QR pihak ketiga.
    implementation("com.google.zxing:core:3.5.3")

    // Firebase Cloud Messaging (push notifications for background/killed-app orders)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // Confetti Animation
    implementation("nl.dionsegijn:konfetti-compose:2.0.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
