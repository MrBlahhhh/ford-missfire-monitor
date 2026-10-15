plugins { id("com.android.application") }
android {
    namespace = "com.geekopolis.fordmonitor"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.geekopolis.fordmonitor"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.4"
        testInstrumentationRunner = "com.geekopolis.fordmonitor.GraphSmokeInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation(project(":core")); testImplementation("junit:junit:4.13.2") }
