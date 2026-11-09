plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.xatune.app.performance"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        create("benchmark") { isDebuggable = true; signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "benchmark" }
    }
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
