plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("maven-publish")
    id("com.gradleup.shadow") version "9.0.0-beta11"
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

configurations {
    // include mods
    modImplementation.configure {
        extendsFrom(modInclude)
    }
    include.configure {
        extendsFrom(modInclude)
    }
}

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")

    // Fabric API
    shadow(modImplementation(platform("net.fabricmc.fabric-api:fabric-api-bom:${properties["fapi_version"] as String}"))!!)
    modImplementation("net.fabricmc.fabric-api:fabric-api")
    shadow("net.fabricmc.fabric-api:fabric-api-base") {
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
    modInclude("meteordevelopment:orbit:${properties["orbit_version"] as String}")
    modInclude("meteordevelopment:starscript:${properties["starscript_version"] as String}")
    modInclude("org.reflections:reflections:${properties["reflections_version"] as String}")
    modInclude("io.netty:netty-handler-proxy:${properties["netty_version"] as String}") { isTransitive = false }
    modInclude("io.netty:netty-codec-socks:${properties["netty_version"] as String}") { isTransitive = false }
    modInclude("de.florianmichael:WaybackAuthLib:${properties["waybackauthlib_version"] as String}")

    modInclude("io.gitlab.jfronny.libjf:libjf-unsafe-v0:${properties["libjf_version"] as String}")
    include("io.gitlab.jfronny.libjf:libjf-base:${properties["libjf_version"] as String}")
    modLocalRuntime("io.gitlab.jfronny.libjf:libjf-devutil:${properties["libjf_version"] as String}")
}

loom {
    accessWidenerPath = file("src/main/resources/meteor-client.accesswidener")
}

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
        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
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

        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
        }

        destinationDirectory.set(layout.buildDirectory.dir("devlibs"))

        dependencies {
            exclude {
                it.moduleGroup == "org.slf4j"
            }
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
