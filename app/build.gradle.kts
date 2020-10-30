plugins {
    id("com.android.application")
    id("checkstyle")
    id("com.google.firebase.crashlytics")
    kotlin("android")
    kotlin("kapt")
    id("com.cookpad.android.plugin.license-tools") version "1.2.2"
    id("com.github.ben-manes.versions") version "0.33.0"
    id("com.vanniktech.android.junit.jacoco") version "0.16.0"
    id("dagger.hilt.android.plugin")
    id("com.huawei.agconnect")
}

repositories {
    jcenter()
    google()
    maven(url = "https://jitpack.io")
    maven(url = "https://developer.huawei.com/repo/") // HUAWEI Maven repository
}

android {
    val commonTest = "src/commonTest/java"
    sourceSets["test"].java.srcDir(commonTest)
    sourceSets["androidTest"].java.srcDirs("src/androidTest/java", commonTest)

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    lintOptions {
        disable("InvalidPeriodicWorkRequestInterval")
        lintConfig = file("lint.xml")
        textOutput("stdout")
        textReport = true
    }

    compileSdkVersion(Versions.targetSdk)

    defaultConfig {
        testApplicationId = "org.tasks.test"
        applicationId = "org.tasks"
        versionCode = 100501
        versionName = "10.5"
        targetSdkVersion(Versions.targetSdk)
        minSdkVersion(Versions.minSdk)
        testInstrumentationRunner = "org.tasks.TestRunner"

        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
                arg("room.incremental", "true")
            }
        }
    }

    signingConfigs {
        create("release") {
            val tasksKeyAlias: String? by project
            val tasksStoreFile: String? by project
            val tasksStorePassword: String? by project
            val tasksKeyPassword: String? by project

            keyAlias = tasksKeyAlias
            storeFile = file(tasksStoreFile ?: "none")
            storePassword = tasksStorePassword
            keyPassword = tasksKeyPassword
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    @Suppress("LocalVariableName")
    buildTypes {
        getByName("debug") {
            firebaseCrashlytics {
                mappingFileUploadEnabled = false
            }
            val tasks_mapbox_key_debug: String? by project
            val tasks_google_key_debug: String? by project
            val tasks_huawei_key_debug: String? by project
            resValue("string", "mapbox_key", tasks_mapbox_key_debug ?: "")
            resValue("string", "google_key", tasks_google_key_debug ?: "")
            resValue("string", "huawei_key", tasks_huawei_key_debug ?: "")
            isTestCoverageEnabled = project.hasProperty("coverage")
        }
        getByName("release") {
            val tasks_mapbox_key: String? by project
            val tasks_google_key: String? by project
            val tasks_huawei_key: String? by project
            resValue("string", "mapbox_key", tasks_mapbox_key ?: "")
            resValue("string", "google_key", tasks_google_key ?: "")
            resValue("string", "huawei_key", tasks_huawei_key ?: "")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions("store")

    productFlavors {
        create("generic") {
            dimension("store")
        }
        create("googleplay") {
            dimension("store")
        }
        create("huawei") {
            dimension("store")
            applicationId = "org.tasks.huawei"

            buildTypes {
                getByName("debug") {
                    applicationId = "org.tasks.huawei"
                    applicationIdSuffix = ""
                }
            }
        }
    }

    packagingOptions {
        exclude("META-INF/*.kotlin_module")
    }
}

configure<CheckstyleExtension> {
    configFile = project.file("google_checks.xml")
    toolVersion = "8.16"
}

configurations.all {
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
    exclude(group = "org.checkerframework")
    exclude(group = "com.google.code.findbugs")
    exclude(group = "com.google.errorprone")
    exclude(group = "com.google.j2objc")
}

val genericImplementation by configurations
val googleplayImplementation by configurations
val huaweiImplementation by configurations

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.0.10")
    implementation("com.gitlab.bitfireAT:dav4jvm:2.1.1")
    implementation("com.gitlab.bitfireAT:ical4android:e4c50b3485")
    implementation("com.gitlab.bitfireAT:cert4android:72cf235ad9")
    implementation("com.github.dmfs.opentasks:opentasks-provider:1.2.4") {
        exclude("com.github.dmfs.opentasks", "opentasks-contract")
    }

    implementation("com.google.dagger:hilt-android:${Versions.hilt}")
    kapt("com.google.dagger:hilt-compiler:${Versions.hilt}")
    kapt("androidx.hilt:hilt-compiler:${Versions.hilt_androidx}")
    implementation("androidx.hilt:hilt-work:${Versions.hilt_androidx}")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel:${Versions.hilt_androidx}")

    implementation("androidx.fragment:fragment-ktx:1.2.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle}")
    implementation("androidx.room:room-ktx:${Versions.room}")
    kapt("androidx.room:room-compiler:${Versions.room}")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("androidx.paging:paging-runtime:2.1.2")

    kapt("com.jakewharton:butterknife-compiler:${Versions.butterknife}")
    implementation("com.jakewharton:butterknife:${Versions.butterknife}")

    debugImplementation("com.facebook.flipper:flipper:${Versions.flipper}")
    debugImplementation("com.facebook.flipper:flipper-network-plugin:${Versions.flipper}")
    debugImplementation("com.facebook.soloader:soloader:0.9.0")

    debugImplementation("com.squareup.leakcanary:leakcanary-android:${Versions.leakcanary}")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.3")
    implementation("com.squareup.okhttp3:okhttp:${Versions.okhttp}")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.google.android.material:material:1.2.1")
    implementation("androidx.annotation:annotation:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference:1.1.1")
    implementation("com.jakewharton.timber:timber:4.7.1")
    implementation("com.google.android.apps.dashclock:dashclock-api:2.0.0")
    implementation("com.twofortyfouram:android-plugin-api-for-locale:1.0.2") {
        isTransitive = false
    }
    implementation("com.rubiconproject.oss:jchronic:0.2.6") {
        isTransitive = false
    }
    implementation("org.scala-saddle:google-rfc-2445:20110304") {
        isTransitive = false
    }
    implementation("com.wdullaer:materialdatetimepicker:4.2.3")
    implementation("me.leolin:ShortcutBadger:1.1.22@aar")
    implementation("com.google.apis:google-api-services-tasks:v1-rev20200905-1.30.10")
    implementation("com.google.apis:google-api-services-drive:v3-rev20200813-1.30.10")
    implementation("com.google.auth:google-auth-library-oauth2-http:0.21.1")
    implementation("androidx.work:work-runtime:${Versions.work}")
    implementation("androidx.work:work-runtime-ktx:${Versions.work}")
    implementation("com.mapbox.mapboxsdk:mapbox-android-core:3.0.0")
    implementation("com.mapbox.mapboxsdk:mapbox-sdk-services:5.3.0")
    implementation("com.etesync:journalmanager:1.1.1")
    implementation("com.github.QuadFlask:colorpicker:0.0.15")

    // https://github.com/mapbox/mapbox-gl-native-android/issues/316
    genericImplementation("com.mapbox.mapboxsdk:mapbox-android-sdk:7.4.1")

    googleplayImplementation("com.google.firebase:firebase-crashlytics:${Versions.crashlytics}")
    googleplayImplementation("com.google.firebase:firebase-analytics:${Versions.analytics}")
    googleplayImplementation("com.google.firebase:firebase-config-ktx:${Versions.remote_config}")
    googleplayImplementation("com.google.android.gms:play-services-location:17.1.0")
    googleplayImplementation("com.google.android.gms:play-services-maps:17.0.0")
    googleplayImplementation("com.google.android.libraries.places:places:2.4.0")
    googleplayImplementation("com.android.billingclient:billing:1.2.2")

    huaweiImplementation("com.huawei.agconnect:agconnect-core:1.4.1.300")
    huaweiImplementation("com.huawei.agconnect:agconnect-crash:1.4.1.300")
    huaweiImplementation("com.huawei.agconnect:agconnect-remoteconfig:1.4.1.300")
    huaweiImplementation("com.huawei.hms:hianalytics:5.0.4.203")
    huaweiImplementation("com.huawei.hms:iap:5.0.2.300")
    huaweiImplementation("com.huawei.hms:location:5.0.2.301")
    huaweiImplementation("com.huawei.hms:maps:5.0.2.300")
    huaweiImplementation("com.huawei.hms:site:5.0.2.300")
    huaweiImplementation("androidx.localbroadcastmanager:localbroadcastmanager:1.0.0")
    huaweiImplementation("androidx.documentfile:documentfile:1.0.1")

    androidTestImplementation("com.google.dagger:hilt-android-testing:${Versions.hilt}")
    kaptAndroidTest("com.google.dagger:hilt-compiler:${Versions.hilt}")
    kaptAndroidTest("androidx.hilt:hilt-compiler:${Versions.hilt_androidx}")
    kaptAndroidTest("com.jakewharton:butterknife-compiler:${Versions.butterknife}")
    androidTestImplementation("org.mockito:mockito-android:${Versions.mockito}")
    androidTestImplementation("com.natpryce:make-it-easy:${Versions.make_it_easy}")
    androidTestImplementation("androidx.test:runner:${Versions.androidx_test}")
    androidTestImplementation("androidx.test:rules:${Versions.androidx_test}")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.annotation:annotation:1.1.0")

    testImplementation("junit:junit:4.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.9")
    testImplementation("com.natpryce:make-it-easy:${Versions.make_it_easy}")
    testImplementation("androidx.test:core:${Versions.androidx_test}")
    testImplementation("org.mockito:mockito-core:${Versions.mockito}")
}

// apply(mapOf("plugin" to "com.google.gms.google-services"))
// apply(plugin = "com.huawei.agconnect")

agcp{
    mappingUpload = true 
    debug = false
    appVersion = "xxx"
}