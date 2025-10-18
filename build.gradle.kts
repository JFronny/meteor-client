import com.github.jengelman.gradle.plugins.shadow.transformers.PreserveFirstFoundResourceTransformer
import net.fabricmc.loom.task.prod.ClientProductionRunTask

plugins {
    id("fabric-loom") version "1.11-SNAPSHOT"
    id("maven-publish")
    id("com.gradleup.shadow") version "9.2.2"
}

base {
    archivesName = properties["archives_base_name"] as String
    group = properties["maven_group"] as String
    version = properties["fapi_version"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        url = uri("https://maven.frohnmeyer-wds.de/artifacts")
    }
    maven {
        name = "Terraformers"
        url = uri("https://maven.terraformersmc.com")
    }
    maven {
        name = "ViaVersion"
        url = uri("https://repo.viaversion.com")
    }
    mavenCentral()

    exclusiveContent {
        forRepository {
            maven {
                name = "modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

val modInclude: Configuration by configurations.creating
val jij: Configuration by configurations.creating

configurations {
    // include mods
    modImplementation.configure {
        extendsFrom(modInclude)
    }
    include.configure {
        extendsFrom(modInclude)
    }

    // include libraries (jar-in-jar)
    implementation.configure {
        extendsFrom(jij)
    }
    include.configure {
        extendsFrom(jij)
    }
}

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${properties["fapi_version"] as String}")
    shadow(fabricApi.module("fabric-api-base", properties["fapi_version"] as String) as ModuleDependency) {
        isTransitive = false
    }

    // Compat fixes
    modCompileOnly(fabricApi.module("fabric-renderer-indigo", properties["fapi_version"] as String))
    modCompileOnly("maven.modrinth:sodium:${properties["sodium_version"] as String}") { isTransitive = false }
    modCompileOnly("maven.modrinth:lithium:${properties["lithium_version"] as String}") { isTransitive = false }
    modCompileOnly("maven.modrinth:iris:${properties["iris_version"] as String}") { isTransitive = false }
    modCompileOnly("com.viaversion:viafabricplus:${properties["viafabricplus_version"] as String}") { isTransitive = false }
    modCompileOnly("com.viaversion:viafabricplus-api:${properties["viafabricplus_version"] as String}") { isTransitive = false }

    // Baritone (https://github.com/MeteorDevelopment/baritone)
    modCompileOnly("meteordevelopment:baritone:${properties["baritone_version"] as String}-SNAPSHOT")
    // ModMenu (https://github.com/TerraformersMC/ModMenu)
    modCompileOnly("com.terraformersmc:modmenu:${properties["modmenu_version"] as String}")

    // Libraries
    jij("meteordevelopment:orbit:${properties["orbit_version"] as String}")
    jij("org.meteordev:starscript:${properties["starscript_version"] as String}")
    jij("org.reflections:reflections:${properties["reflections_version"] as String}")
    jij("io.netty:netty-handler-proxy:${properties["netty_version"] as String}") { isTransitive = false }
    jij("io.netty:netty-codec-socks:${properties["netty_version"] as String}") { isTransitive = false }
    jij("de.florianmichael:WaybackAuthLib:${properties["waybackauthlib_version"] as String}")

    modInclude("io.gitlab.jfronny.libjf:libjf-unsafe-v0:${properties["libjf_version"] as String}")
    modInclude("io.gitlab.jfronny.libjf:libjf-base:${properties["libjf_version"] as String}")
    modLocalRuntime("io.gitlab.jfronny.libjf:libjf-devutil:${properties["libjf_version"] as String}")
}

// Handle transitive dependencies for jar-in-jar
// Based on implementation from BaseProject by FlorianMichael/EnZaXD
// Source: https://github.com/FlorianMichael/BaseProject/blob/main/src/main/kotlin/de/florianmichael/baseproject/Fabric.kt
// Licensed under Apache License 2.0
afterEvaluate {
    val jijConfig = configurations.findByName("jij") ?: return@afterEvaluate

    // Dependencies to exclude from jar-in-jar
    val excluded = setOf(
        "org.slf4j",    // Logging provided by Minecraft
        "jsr305"        // Compile time annotations only
    )


    jijConfig.incoming.resolutionResult.allDependencies.forEach { dep ->
        val requested = dep.requested.displayName

        if (excluded.any { requested.contains(it) }) return@forEach

        val compileOnlyDep = dependencies.create(requested) {
            isTransitive = false
        }

        val implDep = dependencies.create(compileOnlyDep)

        dependencies.add("compileOnlyApi", compileOnlyDep)
        dependencies.add("implementation", implDep)
        dependencies.add("include", compileOnlyDep)
    }
}

loom {
    accessWidenerPath = file("src/main/resources/meteor-client.accesswidener")
}

val prodClient by tasks.registering(ClientProductionRunTask::class)

afterEvaluate {
    tasks.migrateMappings.configure {
        outputDir.set(project.file("src/main/java"))
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version"           to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version")
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21

        withSourcesJar()
        withJavadocJar()
    }

    withType<JavaCompile> {
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }

    shadowJar {
        configurations = listOf(project.configurations.shadow.get())

        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}-${inputs.properties["archivesName"]}" }
        }

        destinationDirectory.set(layout.buildDirectory.dir("devlibs"))

        duplicatesStrategy = DuplicatesStrategy.FAIL
        filesMatching("fabric.mod.json") {
            duplicatesStrategy = DuplicatesStrategy.WARN
        }
        transform<PreserveFirstFoundResourceTransformer> {
            resources.add("fabric.mod.json")
        }
    }

    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
    }

    javadoc {
        with(options as StandardJavadocDocletOptions) {
            addStringOption("Xdoclint:none", "-quiet")
            addStringOption("encoding", "UTF-8")
            addStringOption("charSet", "UTF-8")
        }
    }

    build {
        dependsOn("javadocJar")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "meteor-client"

            version = properties["minecraft_version"] as String + "-SNAPSHOT"
        }
    }
}
