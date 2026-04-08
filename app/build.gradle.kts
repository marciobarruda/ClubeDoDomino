plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.marcioarruda.clubedodomino"
    compileSdk = 34

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.marcioarruda.clubedodomino"
        minSdk = 26
        targetSdk = 34
        versionCode = 46
        versionName = "1.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val projectDir = rootProject.projectDir
            val apksDir = File(projectDir, "apks")
            
            val taskName = "copyApk${variant.name.capitalize()}"
            
            // Create a task for this variant
            val copyTask = tasks.create(taskName) {
                doLast {
                    if (!apksDir.exists()) {
                        apksDir.mkdirs()
                    }
                    
                    val version = variant.versionCode
                    val newName = "clube_v_$version.apk"
                    val destination = File(apksDir, newName)
                    
                    println("Copying APK to: ${destination.absolutePath}")
                    
                    // The output file might not exist yet if this runs too early, 
                    // but typically doLast on a task dependent on assemble works.
                    // Ideally hooks into assemble.
                    
                    val sourceApk = output.outputFile
                    if (sourceApk != null && sourceApk.exists()) {
                         sourceApk.copyTo(destination, overwrite = true)
                         println("APK Copied successfully.")
                    } else {
                        println("Source APK not found: ${sourceApk?.absolutePath}")
                    }
                }
            }
            
            // Make sure the copy task runs after the package task
            variant.assembleProvider.configure {
                 finalizedBy(copyTask)
            }
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1") // Downgrade para versão compatível
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Retrofit & Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Jetpack DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
