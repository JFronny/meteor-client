pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:9.4.1")
    }
}

rootProject.name = "meteor-client"
