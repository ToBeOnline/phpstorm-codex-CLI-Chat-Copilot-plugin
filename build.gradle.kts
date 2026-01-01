import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
}

intellij {
    version.set(providers.gradleProperty("platformVersion").get())
    type.set(providers.gradleProperty("platformType").get())
    plugins.set(
        providers.gradleProperty("platformPlugins")
            .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
    )
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = providers.gradleProperty("javaVersion").get()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("javaVersion").get()))
    }
}

tasks.patchPluginXml {
    version.set(providers.gradleProperty("pluginVersion").get())
    sinceBuild.set("233")
    untilBuild.set("")
}

tasks.test {
    useJUnitPlatform()
}
