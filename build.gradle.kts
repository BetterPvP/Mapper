import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.component.AdhocComponentWithVariants

plugins {
    id("java")
    // java-library so the libraries Mapper exposes through its own public API (Cloud's command
    // manager, InvUI-backed GUIs, Jackson-backed region models) can be declared `api` and therefore
    // reach a consumer's compile classpath. The fat jar used to supply them by accident.
    id("java-library")
    id("io.freefair.lombok") version "9.8.2"
    id("com.gradleup.shadow") version "9.4.1"
    id("maven-publish")
}

group = "dev.brauw.mapper"
version = "2.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    // Optional: the selection bridge only loads when WorldEdit or FastAsyncWorldEdit is installed.
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.15") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.15") { isTransitive = false }
    // api, not implementation: these types appear in Mapper's own public signatures, so a consumer
    // compiling against Mapper needs them on its compile classpath too.
    api("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    api("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    api("xyz.xenondevs.invui:invui:2.5.1")
    api("org.incendo:cloud-core:2.0.0")
    api("org.incendo:cloud-annotations:2.0.0")
    api("org.incendo:cloud-paper:2.0.1")

    // MockBukkit has no 26.x build. The tests only use locations, worlds and vectors, which 1.21 provides alike.
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.105.0") {
        exclude(group = "org.junit")
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
    }
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}

// Standalone plugin artifact: a runnable, self-contained plugin with its libraries relocated.
tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName = "mapper-plugin"
    archiveVersion = ""
    archiveClassifier = ""
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Relocate bundled libraries so they can't collide with copies the host (e.g. StudioEngine)
    // already provides when Mapper is shaded into another plugin. See loader-constraint errors.
    relocate("xyz.xenondevs", "dev.brauw.mapper.libs.xenondevs")
    relocate("com.fasterxml.jackson", "dev.brauw.mapper.libs.jackson")
    relocate("org.incendo", "dev.brauw.mapper.libs.incendo")
}

// Embeddable bundle artifact: classes to be shaded into a HOST plugin.
// InvUI is not bundled here and xyz.xenondevs stays un-relocated, so the host supplies the one copy
// of InvUI that both use.
val bundleJar = tasks.register<ShadowJar>("bundleJar") {
    archiveBaseName = "mapper"
    archiveVersion = ""
    archiveClassifier = "bundle"
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependencies {
        exclude(dependency("xyz.xenondevs.invui:.*"))
    }
    // Relocate the other libraries to avoid host collisions, but not xyz.xenondevs:
    // its references must resolve to the host's InvUI classes.
    relocate("com.fasterxml.jackson", "dev.brauw.mapper.libs.jackson")
    relocate("org.incendo", "dev.brauw.mapper.libs.incendo")
}

tasks.named("build") {
    dependsOn(bundleJar)
}

// The published library is the THIN jar plus a POM that declares Mapper's real dependencies.
//
// It used to be the fat shadowJar with a dependency-less POM, which meant every consumer resolved
// Jackson, Cloud and InvUI *through* Mapper under their original package names - without ever
// declaring them. Relocating those libraries in 1.0.18 removed them from the classpath that
// consumers had been silently relying on, breaking any tool that compiles against a subset of the
// module classpath. Publishing thin puts the dependencies where they belong: in the POM.
//
// The fat jar is still published, under the "plugin" classifier - it is the artifact you install on
// a server, not the one you compile against.

// The fat jar is published deliberately below under its own classifier, not as a second unclassified jar.
shadow {
    addShadowVariantIntoJavaComponent = false
}

publishing {
    publications {
        create<MavenPublication>("Mapper") {
            from(components["java"])

            // Self-contained runnable plugin (bundles relocated InvUI, Jackson and Cloud).
            // Consume as com.github.BetterPvP:Mapper:<version>:plugin
            //
            // Published from the task's output file rather than the task itself: shadowJar keeps an
            // empty archiveClassifier so it builds as `mapper-plugin.jar` for direct server installs,
            // and publishing the task would inherit that empty classifier and collide with the thin jar.
            artifact(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }) {
                classifier = "plugin"
                builtBy(tasks.named("shadowJar"))
            }

            // Embeddable bundle (no InvUI) for shading into a host plugin. Consume with the
            // "bundle" classifier, e.g. com.github.BetterPvP:Mapper:<version>:bundle
            artifact(bundleJar)

            pom {
                name.set("Mapper")
                description.set("Mapper plugin")
            }
        }
    }
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build {
    dependsOn("shadowJar")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.test {
    useJUnitPlatform()
}
