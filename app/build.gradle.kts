plugins {
    id("com.android.application")
}

android {
    namespace = "dev.opd2515.refreshmanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.opd2515.refreshmanager"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
