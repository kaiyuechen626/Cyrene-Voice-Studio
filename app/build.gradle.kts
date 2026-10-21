import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.cyrene.voice"
    compileSdk = 34
    defaultConfig {
        applicationId = "io.cyrene.voice"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "0.2.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets["main"].java.srcDir("../shared/src/main/java")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
val verifyNativeDependency by tasks.registering {
    doLast {
        val aar = rootProject.file("libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar")
        check(aar.isFile) { "Run python tools/fetch_dependency.py first." }
        val digest = MessageDigest.getInstance("SHA-256")
        aar.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } ==
            "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471") {
            "sherpa-onnx AAR differs from the pinned official release."
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyNativeDependency) }
dependencies {
    implementation(files("../libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"))
    testImplementation("junit:junit:4.13.2")
}
