/*
 *    Copyright 2021 Plank
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

@file:Suppress("DSL_SCOPE_VIOLATION")

import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.lang.System.getenv
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

plugins {
  alias(libs.plugins.kotlin)
  alias(libs.plugins.detekt)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.artifactory)
  `maven-publish`
}

repositories {
  mavenCentral()
}

allprojects {
  apply<DetektPlugin>()

  group = "org.plank.llvm4k"
  version = "dev"

  repositories {
    mavenCentral()
  }

  configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false

    config = files("${rootProject.projectDir}/config/detekt.yml")
    baseline = file("${rootProject.projectDir}/config/baseline.xml")
  }
}

val localProperties: Properties = rootProject.file("local.properties").let { file ->
  val properties = Properties()

  if (file.exists()) {
    properties.load(file.inputStream())
  }

  properties
}

val artifactoryUsername: String = localProperties.getProperty("artifactory.username")
  ?: getenv("ARTIFACTORY_USERNAME").orEmpty()

val artifactoryPassword: String = localProperties.getProperty("artifactory.password")
  ?: getenv("ARTIFACTORY_PASSWORD").orEmpty()

artifactory {
  setContextUrl("https://plank.jfrog.io/artifactory")

  publish {
    repository {
      setRepoKey("default-gradle-dev-local")
      setUsername(artifactoryUsername)
      setPassword(artifactoryPassword)
      setMavenCompatible(true)
    }

    defaults {
      setPublishArtifacts(true)
      setPublishPom(true)
      publications("jvm", "linuxX64", "mingwX64", "js", "kotlinMultiplatform")
    }
  }
}

fun locateLlvmConfig(): File {
  return getenv("PATH").split(File.pathSeparatorChar)
    .map { path ->
      if (path.startsWith("'") || path.startsWith("\"")) {
        path.substring(1, path.length - 1)
      } else {
        path
      }
    }
    .map(Paths::get)
    .singleOrNull { path -> Files.exists(path.resolve("llvm-config")) }
    ?.resolve("llvm-config")
    ?.toFile()
    ?: error("No suitable version of LLVM was found.")
}

val llvmConfig = localProperties.getProperty("llvm.config")?.let(::File)
  ?: getenv("LLVM4K_CONFIG")?.let(::File)
  ?: locateLlvmConfig()

fun cmd(vararg args: String): String {
  val command = "${llvmConfig.absolutePath} ${args.joinToString(" ")}"
  val process = Runtime.getRuntime().exec(command)
  val output = process.inputStream.bufferedReader().readText()

  val exitCode = process.waitFor()
  if (exitCode != 0) {
    error("Command `$command` failed with status code: $exitCode")
  }

  return output.replace("\n", "")
}

fun String.absolutePath(): String {
  return Paths.get(this).toAbsolutePath().toString().replace("\n", "")
}

configure<KotlinMultiplatformExtension> {
  explicitApi()

  jvm {
    withJava()
    compilations.all {
      kotlinOptions.jvmTarget = "17"
    }
    testRuns["test"].executionTask.configure {
      useJUnitPlatform()
    }
  }

  js(IR) {
    nodejs()
  }

  linuxX64("linuxX64") {
    val main by compilations.getting
    val llvm by main.cinterops.creating {
      includeDirs(cmd("--includedir").absolutePath())
    }
  }

  mingwX64("mingwX64") {
    val main by compilations.getting
    val llvm by main.cinterops.creating {
      includeDirs(cmd("--includedir").absolutePath())
    }
  }

  sourceSets {
    val commonMain by getting

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }

    val linuxX64Main by getting {
      kotlin.srcDir("src/nativeMain/kotlin")
    }

    val mingwX64Main by getting {
      kotlin.srcDir("src/nativeMain/kotlin")
    }
  }
}
