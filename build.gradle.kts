import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.shadow
import com.github.jengelman.gradle.plugins.shadow.tasks.InheritManifest
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.PreserveFirstFoundResourceTransformer
import net.fabricmc.loom.task.prod.ClientProductionRunTask
import kotlin.collections.listOf

plugins {
    alias(libs.plugins.fabric.loom)
    id("maven-publish")
}

apply<ShadowBasePlugin>()

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
    implementation.configure {
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
    implementation(libs.fabric.loader)

    // Fabric API
    val fapiVersion = libs.versions.fabric.api.get()
    implementation("net.fabricmc.fabric-api:fabric-api:$fapiVersion")
    "shadow"(fabricApi.module("fabric-api-base", fapiVersion) as ModuleDependency) {
        isTransitive = false
    }
    productionRuntimeMods("maven.modrinth:fabric-api:$fapiVersion")

    // Compat fixes
    compileOnly(fabricApi.module("fabric-renderer-indigo", fapiVersion))
    compileOnly(libs.sodium) { isTransitive = false }
    compileOnly(libs.lithium) { isTransitive = false }
    compileOnly(libs.iris) { isTransitive = false }
    compileOnly(libs.viafabricplus) { isTransitive = false }
    compileOnly(libs.viafabricplus.api) { isTransitive = false }

    compileOnly(libs.baritone)
    compileOnly(libs.modmenu)

    // Libraries (JAR-in-JAR)
    jij(libs.orbit)
    jij(libs.starscript)
    jij(libs.reflections)
    jij(libs.netty.handler.proxy) { isTransitive = false }
    jij(libs.netty.codec.socks) { isTransitive = false }
    jij(libs.waybackauthlib)

    modInclude(libs.libjf.base)
    localRuntime(libs.libjf.devutil)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }

    withSourcesJar()
    withJavadocJar()
}

// Handle transitive dependencies for jar-in-jar
// Based on implementation from BaseProject by florianreuth/EnZaXD
// Source: https://github.com/florianreuth/BaseProject/blob/main/src/main/kotlin/de/florianreuth/baseproject/Fabric.kt
// Licensed under Apache License 2.0
val jijExcluded = setOf("org.slf4j", "jsr305")
listOf("jij", "implementation", "include").forEach { configName ->
    configurations.named(configName).configure {
        defaultDependencies {
            configurations.getByName("jij").incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .forEach { id ->
                    val notation = "${id.group}:${id.module}:${id.version}"
                    if (jijExcluded.none { notation.contains(it) }) {
                        add(project.dependencies.create(notation) {
                            isTransitive = false
                        })
                    }
                }
        }
    }
}

loom {
    accessWidenerPath = file("src/main/resources/meteor-client.classtweaker")
}

fun toMinecraftCompat(version: String): String {
    val match = Regex("""^(\d{2})\.([1-9]\d*)(?:\.([1-9]\d*))?$""")
        .matchEntire(version)
        ?: error("Invalid Minecraft version format: $version. Expected YY.D or YY.D.H")

    val (year, drop, _) = match.destructured
    return "~$year.$drop"
}

val prodClient by tasks.registering(ClientProductionRunTask::class)

lateinit var tp: TaskProvider<ShadowJar>
tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "jdk_version" to libs.versions.jdk.get(),
            "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
            "loader_version" to libs.versions.fabric.loader.get()
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        destinationDirectory = layout.buildDirectory.dir("devlibs")
        archiveClassifier = "unshaded"
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }

    val shadowJar by registering(ShadowJar::class) {
        dependsOn(jar)
        configurations = listOf(project.configurations.shadow.get())
        from(zipTree(jar.get().archiveFile))

        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}-${inputs.properties["archivesName"]}" }
        }

        destinationDirectory.set(layout.buildDirectory.dir("libs"))

        duplicatesStrategy = DuplicatesStrategy.FAIL
        filesMatching("fabric.mod.json") {
            duplicatesStrategy = DuplicatesStrategy.WARN
        }
        transform<PreserveFirstFoundResourceTransformer> {
            resources.add("fabric.mod.json")
        }

        val baseManifest = jar.get().manifest
        manifest = object : InheritManifest, Manifest by baseManifest {
            override fun inheritFrom(
                vararg inheritPaths: Any,
                action: Action<ManifestMergeSpec>,
            ) {
                inheritPaths.forEach { from(it, action) }
            }
        }
    }
    tp = shadowJar

//    remapJar {
//        dependsOn(shadowJar)
//        inputFile.set(shadowJar.get().archiveFile)
//    }

    javadoc {
        with(options as StandardJavadocDocletOptions) {
            addStringOption("Xdoclint:none", "-quiet")
            addStringOption("encoding", "UTF-8")
            addStringOption("charSet", "UTF-8")
        }
    }

    build {
        dependsOn(shadowJar)
        dependsOn("javadocJar")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            setArtifacts(listOf(mapOf("source" to tp, "classifier" to null), tasks.named("sourcesJar")))
            artifactId = "meteor-client"

            version = "${libs.versions.minecraft.get()}-SNAPSHOT"
        }
    }
}
