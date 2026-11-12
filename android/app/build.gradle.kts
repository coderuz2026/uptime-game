plugins { id("com.android.application") }

val vc = (project.findProperty("vc") as String?)?.toInt() ?: 1
val ksPass: String? = System.getenv("KS_PASS")

android {
    namespace = "uz.defactum.uptime"
    compileSdk = 34
    defaultConfig {
        applicationId = "uz.defactum.uptime"
        minSdk = 24
        targetSdk = 34
        versionCode = vc
        versionName = "1.0.$vc"
    }
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KS_PATH") ?: "release.jks")
            storePassword = ksPass
            keyAlias = System.getenv("KS_ALIAS") ?: "uptime"
            keyPassword = ksPass
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksPass != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
