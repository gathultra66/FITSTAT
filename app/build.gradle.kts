import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.fitstat"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.fitstat"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "1.9.2"
    }

    // La llave de firma NO está en el repositorio. Para firmar, coloca fitstat.keystore
    // en la raíz del proyecto y define la variable de entorno FITSTAT_KEY_PASSWORD.
    // Sin ambas cosas, la compilación sigue funcionando pero sin la firma de FITSTAT.
    val fitstatKeystore = file("${rootDir}/fitstat.keystore")
    val fitstatKeyPassword: String? = System.getenv("FITSTAT_KEY_PASSWORD")
    val canSign = fitstatKeystore.exists() && !fitstatKeyPassword.isNullOrEmpty()

    signingConfigs {
        create("fitstat") {
            if (canSign) {
                storeFile = fitstatKeystore
                storePassword = fitstatKeyPassword
                keyAlias = "fitstat"
                keyPassword = fitstatKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (canSign) {
                signingConfig = signingConfigs.getByName("fitstat")
            }
        }
        debug {
            if (canSign) {
                signingConfig = signingConfigs.getByName("fitstat")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // lint-gradle no se puede bajar sin conexión; el APK se valida con aapt2 y apksigner.
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    androidResources {
        noCompress += listOf("ttf", "otf", "jpg")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.jvmArgs("-Xss4m")
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("fitstat.shots", System.getProperty("fitstat.shots") ?: "${rootDir}/shots")
                // Los jars de Android que usa Robolectric se bajan una vez a esta carpeta (el proxy bloquea repo1.maven.org).
                val roboDeps = file("/home/claude/robolectric-deps")
                if (roboDeps.isDirectory) {
                    it.systemProperty("robolectric.offline", "true")
                    it.systemProperty("robolectric.dependency.dir", roboDeps.absolutePath)
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.ui.text.ExperimentalTextApi",
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Conector de sueño: lee las noches que guardan el reloj o la pulsera (solo lectura).
    implementation("androidx.health.connect:connect-client:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
