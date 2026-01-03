
plugins {

    id("com.android.application")

    id("org.jetbrains.kotlin.android")

}



android {

    namespace = "com.example.game2048"

    compileSdk = 33



    defaultConfig {

        applicationId = "com.example.game2048"

        minSdk = 21

        targetSdk = 33

        versionCode = 1

        versionName = "1.0"

    }



    buildFeatures {

        compose = true

    }



    composeOptions {

        kotlinCompilerExtensionVersion = "1.4.3"

    }



    kotlinOptions {

        jvmTarget = "1.8"

    }



    packagingOptions {

        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    }

}



dependencies {

    implementation("androidx.core:core-ktx:1.10.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

    implementation("androidx.activity:activity-compose:1.7.2")

    implementation("androidx.compose.ui:ui:1.4.3")

    implementation("androidx.compose.ui:ui-tooling-preview:1.4.3")

    implementation("androidx.compose.material3:material3:1.1.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.4.3")

}

