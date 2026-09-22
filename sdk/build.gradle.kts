plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.central.publishing)
}

android {
    namespace = "tj.behruz.devicesignals.sdk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.core.ktx)
    compileOnly(libs.play.integrity)
    implementation(libs.bouncycastle)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    coordinates(
        groupId = property("SDK_GROUP_ID").toString(),
        artifactId = property("SDK_ARTIFACT_ID").toString(),
        version = property("SDK_VERSION").toString(),
    )

    pom {
        name.set("DeviceSignals SDK")
        description.set("Android device fingerprinting and fraud detection SDK")
        url.set("https://github.com/behruz/DeviceSignals")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("behruz")
                name.set("Behruz")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/behruz/DeviceSignals.git")
            developerConnection.set("scm:git:ssh://github.com/behruz/DeviceSignals.git")
            url.set("https://github.com/behruz/DeviceSignals")
        }
    }

    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}
