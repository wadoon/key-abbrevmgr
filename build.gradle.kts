import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"

    id("org.jetbrains.dokka") version "2.1.0"
    `java-library`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "io.github.wadoon.key"
version = "0.2.0-SNAPSHOT"
description = "Manager for term abbreviation for the KeY theorem prover."

val keyVersion = System.getenv("KEY_VERSION") ?: "2.12.4-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots")}
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    compileOnly("org.key-project:key.core:${keyVersion}")
    compileOnly("org.key-project:key.ui:${keyVersion}")
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    testImplementation(kotlin("test"))
    testImplementation("com.google.truth:truth:1.4.5")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}


tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.withType<JavaCompile> {
    options.release = 21
}

tasks.withType<Test> {
    useJUnitPlatform()
    reports.html.required.set(false)
    reports.junitXml.required.set(true)
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Javadoc> {
    isFailOnError = false
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
//            from(components["kotlin"])

            repositories {
                maven {
                    name = "folder"
                    url = uri("$rootDir/release")
                }
            }


            pom {
                name = "key-abbrevmgr"
                description = project.description
                url = "https://github.com/wadoon/key-citool"
                licenses {
                    license {
                        name = "GNU General Public License (GPL), Version 2"
                        url = "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
                    }
                }
                developers {
                    developer {
                        id = "wadoon"
                        name = "Alexander Weigl"
                        email = "weigl@kit.edu"
                    }
                }
                scm {
                    connection = "git@github.com:wadoon/key-abbrevmgr.git"
                    url = "https://github.com/wadoon/key-abbrevmgr"
                }
            }
        }
    }
}

nexusPublishing {
    repositories {
        create("central") {
            nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
            snapshotRepositoryUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")

            stagingProfileId.set("io.github.wadoon")
            val user: String = project.properties.getOrDefault("ossrhUsername", "").toString()
            val pwd: String = project.properties.getOrDefault("ossrhPassword", "").toString()

            username.set(user)
            password.set(pwd)
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}


fun makeDependencyUrl(group: String, name: String, version: String): URI {
    val url = project.repositories.mavenCentral().url.toString().trimEnd('/')
    val g = group.replace('.', '/')
    return uri("$url/$g/$name/$version/$name-$version.jar")
}

fun makeDependencyUrl(it: Dependency): URI {
    return makeDependencyUrl(it.group ?: "", it.name, it.version ?: "")
}

fun dependenciesURLs(): Sequence<URI> {
    val dependencies = project.configurations.getByName("implementation").dependencies.asSequence()
    return dependencies.map(::makeDependencyUrl) + sequenceOf(
        makeDependencyUrl(
            project.group.toString(),
            project.name,
            project.version.toString()
        )
    )
}

tasks.register("makeDownloadScript") {
    outputs.file("download.sh")

    doLast {
        val dependenciesURLs = dependenciesURLs()
        dependenciesURLs.forEach {
            println(it)
        }

        file("download.sh").bufferedWriter().use { out ->
            fun URI.filename(): String = this.path.takeLastWhile { it != '/' }
            val downloadedJars = dependenciesURLs.map {
                $$"$TARGET/" + it.filename()
            }

            val downloads = dependenciesURLs.zip(downloadedJars)
                .joinToString("") { (u, f) ->
                    "|download '$u'\\\n" +
                            "|         \"$f\"\n"
                }

            out.write(
                $$"""
                |#!/bin/sh
                
                |TARGET=$(readlink -f "$${project.name}-$${project.version}")
                
                |mkdir -p $TARGET
                |function download() {
                |    # Use curl or wget
                |    if command -v wget >/dev/null 2>&1; then
                |        wget -timestamping -O    "$2" "$1"
                |    elif command -v curl >/dev/null 2>&1; then
                |        curl -L -o "$2" "$1"
                |    else
                |        echo "Error: Neither curl nor wget is installed."
                |        exit 1
                |    fi
                |}
            
                $$downloads
                 
                |echo "Extend your classpath by following Jars, either using the whole folder, or by single Jars."
                |echo "java -cp key-2.14.4-dev.jar:$TARGET/*"
                |echo "or: java -cp key-2.14.4-dev.jar:$${downloadedJars.joinToString(":")}"                
                """.trimMargin()
            )


        }
    }
}






/*dokka {
    moduleName.set("key-citool")
    dokkaSourceSets.main {
        includes.from("README.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/wadoon/key-citool/blob/main")
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration.html {
        //customStyleSheets.from("styles.css")
        //customAssets.from("logo.png")
        footerMessage.set("GPL-v2-only")
    }
}
*/