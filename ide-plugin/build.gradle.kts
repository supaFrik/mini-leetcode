import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "jsd.toiyeujava.mini_leetcode"
version = providers.gradleProperty("pluginVersion").get()

java {
    // IntelliJ Platform >= 2024.2 bắt buộc bytecode Java 21.
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // Platform test runtime vẫn nạp lớp JUnit 4 dù test chỉ dùng JUnit 5.
    testRuntimeOnly("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    // Plugin Verifier chỉ chạy khi gọi verifyPlugin (tải thêm IDE), không nằm trong build thường.
    pluginVerification { ides { recommended() } }
}

tasks.test {
    useJUnitPlatform()
}
