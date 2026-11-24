plugins {
    id("java")
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.0.0"
}

group = "com.accenture"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.3.4")
        bundledPlugin("com.intellij.java")
        instrumentationTools()
    }
    implementation("com.github.javaparser:javaparser-core:3.27.0")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    // This plugin has no settings pages to index.
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }
    }
}

tasks {
    val copyPluginZipToRoot by registering {
        val archiveName = "accent-java-formatter-${project.version}.zip"
        val archive = layout.buildDirectory.file("distributions/$archiveName")
        val destination = layout.projectDirectory.file(archiveName)
        dependsOn("buildPlugin")
        inputs.file(archive)
        // Declare only the ZIP as output, not the entire source/test directory.
        outputs.file(destination)
        doLast {
            archive.get().asFile.copyTo(destination.asFile, overwrite = true)
        }
    }

    named("buildPlugin") {
        finalizedBy(copyPluginZipToRoot)
    }

    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    test {
        useJUnit()
    }
}
