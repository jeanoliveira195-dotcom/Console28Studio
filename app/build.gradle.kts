plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jean.console28"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.jean.console28"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
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
    }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/rustJniLibs"))
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
}

// Android Studio / Gradle primeiro compila as bibliotecas nativas e as empacota no APK.
val buildRustNative by tasks.registering(Exec::class) {
    group = "build"
    description = "Compila motor DSP Rust e ponte JNI para Android ARM64/x86_64"
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/build-android-rust.sh",
        layout.buildDirectory.dir("generated/rustJniLibs").get().asFile.absolutePath)
    inputs.dir(rootProject.file("rust/src"))
    inputs.file(rootProject.file("rust/Cargo.toml"))
    inputs.file(rootProject.file("native/console28_jni.c"))
    inputs.file(rootProject.file("scripts/build-android-rust.sh"))
    outputs.dir(layout.buildDirectory.dir("generated/rustJniLibs"))
}
tasks.named("preBuild") { dependsOn(buildRustNative) }

// Garante que o merge de JNI aguarde os .so nativos, mesmo com tarefas paralelas.
tasks.configureEach {
    if (name.matches(Regex("merge.*(JniLibFolders|NativeLibs)"))) {
        dependsOn(buildRustNative)
    }
}
