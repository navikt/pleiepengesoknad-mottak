import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion = ext.get("ktorVersion").toString()
val dusseldorfKtorVersion = "1.2.2.8f413ad"
val mainClass = "no.nav.helse.PleiepengesoknadMottakKt"


plugins {
    kotlin("jvm") version "1.3.40"
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

buildscript {
    apply("https://raw.githubusercontent.com/navikt/dusseldorf-ktor/8f413ad909a79e6f5e5897f43f009152ab2f0f35/gradle/dusseldorf-ktor.gradle.kts")
}

repositories {
//    maven("https://dl.bintray.com/kotlin/ktor")
//    maven("https://kotlin.bintray.com/kotlinx")
//    maven("http://packages.confluent.io/maven/")
//
//    jcenter()
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Server
    compile ( "no.nav.helse:dusseldorf-ktor-core:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-metrics:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-health:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-auth:$dusseldorfKtorVersion")

    // Client
    compile ( "no.nav.helse:dusseldorf-ktor-client:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-oauth2-client:$dusseldorfKtorVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Jar> {
    manifest {
        attributes(
            mapOf(
                "Main-Class" to mainClass
            )
        )
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.5"
}