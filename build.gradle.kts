import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion = ext.get("ktorVersion").toString()
val dusseldorfKtorVersion = "1.2.2.8f413ad"
val kafkaEmbeddedEnvVersion = "2.1.1"
val kafkaVersion = "2.0.1" // Aliigned med version fra kafka-embedded-env
val wiremockVersion = "2.19.0"

val mainClass = "no.nav.helse.PleiepengesoknadMottakKt"


plugins {
    kotlin("jvm") version "1.3.40"
    id("com.github.johnrengelman.shadow") version "5.0.0"
}


buildscript {
    apply("https://raw.githubusercontent.com/navikt/dusseldorf-ktor/8f413ad909a79e6f5e5897f43f009152ab2f0f35/gradle/dusseldorf-ktor.gradle.kts")
}


repositories {
    maven("http://packages.confluent.io/maven/")
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

    // Kafka
    compile("org.apache.kafka:kafka-clients:$kafkaVersion")

    // Test
    testCompile ("no.nav:kafka-embedded-env:$kafkaEmbeddedEnvVersion")
    testCompile("no.nav.security:oidc-test-support:0.2.18")
    testCompile ("com.github.tomakehurst:wiremock:$wiremockVersion")
    testCompile("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testCompile("org.skyscreamer:jsonassert:1.5.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}


tasks.withType<ShadowJar> {
    archiveBaseName.set("app")
    archiveClassifier.set("")
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