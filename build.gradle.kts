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
    archivesName.set("BetterFoliageReborn-NeoForge-$minecraftVersion")
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
    test {
        // ModDev exposes the mapped game API to main only; the headless geometry regression needs it too.
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().compileClasspath
    }
    main {
        resources {
            srcDir(generateModMetadata)
        }
    }
}

dependencies {
    // NativeImage regression tests need native memory allocation, but no window or OpenGL context.
    val testOs = providers.systemProperty("os.name").get().lowercase()
    val testArch = providers.systemProperty("os.arch").get().lowercase()
    val testNativePlatform = when {
        testOs.contains("win") -> "windows"
        testOs.contains("mac") -> "macos"
        else -> "linux"
    }
    val testNativeArch = if (testArch == "aarch64" || testArch == "arm64") "-arm64" else ""
    testRuntimeOnly("org.lwjgl:lwjgl:3.3.3:natives-$testNativePlatform$testNativeArch")
    testRuntimeOnly("org.lwjgl:lwjgl-stb:3.3.3:natives-$testNativePlatform$testNativeArch")
    // Ecliptic Seasons 1.21.1 / 0.15.0-rc-3. Optional client bridge; never bundled or added to runtime.
    compileOnly("maven.modrinth:ecliptic-seasons:Tok0V0sp") { isTransitive = false }
    // Cull Leaves 4.1.1 / NeoForge 1.21–1.21.1. Compile-time only; no runtime dependency or bundling.
    compileOnly("maven.modrinth:cull-leaves:V7PU4g8I") { isTransitive = false }
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

val fluffRotationTest = tasks.register<JavaExec>("fluffRotationTest") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.eerussianguy.betterfoliage.model.FluffRotationTest")
}
tasks.named("check") { dependsOn(fluffRotationTest) }
val snowCompositeTest = tasks.register<JavaExec>("snowCompositeTest") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.eerussianguy.betterfoliage.model.SnowCompositeTest")
    workingDir(layout.buildDirectory.dir("snowCompositeTest").get().asFile)
    doFirst { workingDir.mkdirs() }
    providers.gradleProperty("snowTestPack").orNull?.let { args(it) }
}
tasks.named("check") { dependsOn(snowCompositeTest) }

tasks {
    processResources {
    }

    jar {
        manifest {
            attributes["Implementation-Version"] = project.version
            attributes["Implementation-Title"] = "Better Foliage Reborn"
        }
    }

    named("neoForgeIdeSync") {
        dependsOn(generateModMetadata)
    }
}

