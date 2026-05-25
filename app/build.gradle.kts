import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localReleaseProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningValue(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: providers.environmentVariable("TMUXES_$name").orNull
        ?: localReleaseProperties.getProperty(name)

fun String?.hasText(): Boolean = !isNullOrBlank()

val releaseStoreFilePath = releaseSigningValue("RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD")
val releaseStoreFile = releaseStoreFilePath?.let { file(it) }
val hasReleaseSigning =
    releaseStoreFilePath.hasText() &&
        releaseStorePassword.hasText() &&
        releaseKeyAlias.hasText() &&
        releaseKeyPassword.hasText() &&
        releaseStoreFile?.isFile == true

android {
    namespace = "com.tmuxes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tmuxes"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = checkNotNull(releaseStoreFile)
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    testOptions {
        // Return default values from Android framework stubs instead of
        // throwing "Method X in android.util.Log not mocked". This is
        // the standard way to allow JVM unit tests to call into code
        // that touches android.util.Log or other stubbed Android APIs
        // without pulling in Robolectric. Our AppLogger is a thin
        // wrapper over android.util.Log — tests that exercise flows
        // or classes that log on error would otherwise need to avoid
        // the error path entirely.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Core
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // SSH - SSHJ
    implementation("com.hierynomus:sshj:0.40.0")

    // Bouncy Castle for crypto
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // YAML config
    implementation("org.yaml:snakeyaml:2.6")

    // Sora Editor - code editor component (LGPL-2.1)
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.6"))
    implementation("io.github.Rosemoe.sora-editor:editor")
    implementation("io.github.Rosemoe.sora-editor:language-textmate")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// =============================================================================
// Design-rule CI gates. These tasks protect the project contracts that are easy
// to break with local edits: UI token discipline, i18n ownership, logging cost,
// settings registry usage, coroutine boundaries, package layering, and import
// hygiene. Each script reports the exact offending file and line.
//
// Design-rule categories: A=appearance, B=token-discipline, C=settings-
// registry/config-color, D=logging, E=concurrency, F=architecture-layering, G+H=misc,
// I=i18n.
// =============================================================================

fun designRuleTask(taskName: String, scriptName: String, summary: String, root: String): TaskProvider<Exec> =
    tasks.register<Exec>(taskName) {
        description = summary
        group = "verification"
        workingDir = rootProject.projectDir
        commandLine("bash", "gradle/scripts/$scriptName")
        environment("ROOT", root)
    }

val checkNoHardcodedStyles = designRuleTask(
    "checkNoHardcodedStyles", "check-no-hardcoded-styles.sh",
    "A1-A4: Forbid raw Color(0x..) / RoundedCornerShape(N.dp) / fontSize=N.sp / raw TopAppBar/AlertDialog/FAB in screens.",
    "app/src/main/java/com/tmuxes/ui"
)
val checkTokenDiscipline = designRuleTask(
    "checkTokenDiscipline", "check-token-discipline.sh",
    "B1-B6: Forbid MaterialTheme.* reads / .copy(alpha)/.copy(font*) / raw M3 form primitives in screens.",
    "app/src/main/java/com/tmuxes/ui"
)
val checkSettingsRegistry = designRuleTask(
    "checkSettingsRegistry", "check-settings-registry.sh",
    "C1-C4: Forbid SharedPreferences / string-keyed yamlConfig / preferences.X(\"literal\") / numeric YAML colors.",
    "app/src/main/java/com/tmuxes"
)
val checkLogging = designRuleTask(
    "checkLogging", "check-logging.sh",
    "D1-D6: AppLogger discipline — no raw Log/println, lambda-only calls, silent-catch must log/cleanup/bypass, Category.valueOf only in logger.",
    "app/src/main/java/com/tmuxes"
)
val checkConcurrency = designRuleTask(
    "checkConcurrency", "check-concurrency.sh",
    "E1-E2: Forbid GlobalScope; runBlocking allowed only in HostKeyVerifier.kt.",
    "app/src/main/java/com/tmuxes"
)
val checkArchitectureLayers = designRuleTask(
    "checkArchitectureLayers", "check-architecture-layers.sh",
    "F1-F4: ui/screens cannot import data.db / ssh.internal; data and ssh cannot import ui.",
    "app/src/main/java/com/tmuxes"
)
val checkMiscDiscipline = designRuleTask(
    "checkMiscDiscipline", "check-misc-discipline.sh",
    "G1, H1: Forbid /sdcard external paths; forbid wildcard imports.",
    "app/src/main/java/com/tmuxes"
)
val checkI18nDiscipline = designRuleTask(
    "checkI18nDiscipline", "check-i18n.sh",
    "I1-I5: Forbid unmanaged UI copy; forbid split t(...) keys; forbid duplicate managed catalog keys.",
    "app/src/main/java/com/tmuxes"
)

val checkDesignRules = tasks.register("checkDesignRules") {
    description = "Run all design-rule gates (A-I, including strict i18n)."
    group = "verification"
    dependsOn(
        checkNoHardcodedStyles,
        checkTokenDiscipline,
        checkSettingsRegistry,
        checkLogging,
        checkConcurrency,
        checkArchitectureLayers,
        checkMiscDiscipline,
        checkI18nDiscipline
    )
}

tasks.named("check") {
    dependsOn(checkDesignRules)
}
