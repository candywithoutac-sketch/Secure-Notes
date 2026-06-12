// 1. Open your Android Studio project.
// 2. Open the 'app' level build.gradle.kts file.
// 3. Add these dependencies inside the dependencies { ... } block.

dependencies {
    // Default Android and Compose dependencies (usually already there)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 🔒 Security: Biometrics for App Lock
    implementation("androidx.biometric:biometric:1.1.0")

    // ✍️ Handwriting Recognition: Google ML Kit Digital Ink
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
}
