// Pure Java Android build — no Kotlin plugin, no kotlin-stdlib, no kotlin-reflect.
//
// ACTUAL BUILD: run  bash build-java.sh  at the repo root.
//   This file is for IDE reference only.
//   AGP (com.android.application) requires Google Maven which may be unavailable.
//
// Equivalent Gradle config (for reference):
//
// plugins {
//     id("com.android.application")
// }
// android {
//     namespace = "com.example.eyetab"
//     compileSdk = 34
//     defaultConfig {
//         applicationId = "com.example.eyetab"
//         minSdk = 26
//         targetSdk = 34
//         versionCode = 6
//         versionName = "1.5"
//     }
//     compileOptions {
//         sourceCompatibility = JavaVersion.VERSION_1_8
//         targetCompatibility = JavaVersion.VERSION_1_8
//     }
// }
// dependencies {
//     // No Kotlin. No kotlin-stdlib. No kotlin-reflect.
// }
