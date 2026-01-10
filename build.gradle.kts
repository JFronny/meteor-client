import com.github.jengelman.gradle.plugins.shadow.transformers.PreserveFirstFoundResourceTransformer
import net.fabricmc.loom.task.prod.ClientProductionRunTask

plugins {
    alias(libs.plugins.fabric.loom)
    id("maven-publish")
    alias(libs.plugins.shadow)
}

base {
    archivesName = properties["archives_base_name"] as String
    group = properties["maven_group"] as String
    version = libs.versions.fabric.api.get()
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
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    // Fabric API
    val fapiVersion = libs.versions.fabric.api.get()
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fapiVersion")
    shadow(fabricApi.module("fabric-api-base", fapiVersion) as ModuleDependency) {
        isTransitive = false
    }
    productionRuntimeMods("maven.modrinth:fabric-api:$fapiVersion")

    // Compat fixes
    modCompileOnly(fabricApi.module("fabric-renderer-indigo", fapiVersion))
    modCompileOnly(libs.sodium) { isTransitive = false }
    modCompileOnly(libs.lithium) { isTransitive = false }
    modCompileOnly(libs.iris) { isTransitive = false }
    modCompileOnly(libs.viafabricplus) { isTransitive = false }
    modCompileOnly(libs.viafabricplus.api) { isTransitive = false }

    modCompileOnly(libs.baritone)
    modCompileOnly(libs.modmenu)

    // Libraries (JAR-in-JAR)
    jij(libs.orbit)
    jij(libs.starscript)
    jij(libs.reflections)
    jij(libs.netty.handler.proxy) { isTransitive = false }
    jij(libs.netty.codec.socks) { isTransitive = false }
    jij(libs.waybackauthlib)

    modInclude(libs.libjf.base)
    modInclude(libs.libjf.unsafe.v0)
    modLocalRuntime(libs.libjf.devutil)
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
            "minecraft_version" to libs.versions.minecraft.get(),
            "loader_version"    to libs.versions.fabric.loader.get()
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

            version = libs.versions.minecraft.get() + "-SNAPSHOT"
        }
    }
}
