import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("net.neoforged.moddev") version "2.0.1-beta"
}


// Toolchain versions
val minecraftVersion: String = "1.21"
val neoForgeVersion: String = "21.0.167"
val parchmentVersion: String = "2024.07.07"
val parchmentMinecraftVersion: String = "1.21"

val modId: String = "betterfoliage"
val modVersion: String = System.getenv("VERSION") ?: "1.0.0"
val modJavaVersion: String = "21"
val modIsInCI: Boolean = !modVersion.contains("-indev")


val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val modReplacementProperties = mapOf(
        "modId" to modId,
        "modVersion" to modVersion,
        "minecraftVersionRange" to "[$minecraftVersion,)",
        "neoForgeVersionRange" to "[$neoForgeVersion,)"
    )
    inputs.properties(modReplacementProperties)
    expand(modReplacementProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}


base {
    archivesName.set("BetterFoliageRenewed-NeoForge-$minecraftVersion")
    group = "com.eerussianguy.betterfoliage"
    version = modVersion
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
}

repositories {
    mavenLocal()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://www.cursemaven.com") }
        filter { includeGroup("curse.maven") }
    }
}

sourceSets {
    main {
        resources {
            srcDir(generateModMetadata)
        }
    }
}

dependencies {
    // Ecliptic Seasons 1.21.1 / 0.15.0-rc-3. Optional client bridge; never bundled or added to runtime.
    compileOnly("maven.modrinth:ecliptic-seasons:Tok0V0sp") { isTransitive = false }
}

neoForge {
    version.set(neoForgeVersion)
    validateAccessTransformers = true

    parchment {
        minecraftVersion.set(parchmentMinecraftVersion)
        mappingsVersion.set(parchmentVersion)
    }

    runs {
        configureEach {
            // Only JBR allows enhanced class redefinition, so ignore the option for any other JDKs
            jvmArguments.addAll("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AllowEnhancedClassRedefinition", "-ea")
            systemProperty("betterfoliage.enableDebugSelfTests", "true")
        }
        register("client") {
            client()
            gameDirectory = file("run/client")
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    ideSyncTask(generateModMetadata)
}

tasks {
    processResources {
    }

    jar {
        manifest {
            attributes["Implementation-Version"] = project.version
        }
    }

    named("neoForgeIdeSync") {
        dependsOn(generateModMetadata)
    }
}

