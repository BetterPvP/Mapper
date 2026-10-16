import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.component.AdhocComponentWithVariants

plugins {
    id("java")
    // java-library so the libraries Mapper exposes through its own public API (Cloud's command
    // manager, InvUI-backed GUIs, Jackson-backed region models) can be declared `api` and therefore
    // reach a consumer's compile classpath. The fat jar used to supply them by accident.
    id("java-library")
    id("io.freefair.lombok") version "8.12.2.1"
    id("com.gradleup.shadow") version "8.3.6"
    id("maven-publish")
}

group = "dev.brauw.mapper"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Optional: the selection bridge only loads when WorldEdit or FastAsyncWorldEdit is installed.
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.15") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.15") { isTransitive = false }
    // api, not implementation: these types appear in Mapper's own public signatures, so a consumer
    // compiling against Mapper needs them on its compile classpath too.
    api("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    api("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    api("xyz.xenondevs.invui:invui:1.49")
    api("org.incendo:cloud-core:2.0.0")
    api("org.incendo:cloud-annotations:2.0.0")
    api("org.incendo:cloud-paper:2.0.0-beta.10")

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

// Standalone plugin artifact: a runnable, self-contained plugin. Its legacy plugin.yml makes it a
// spigot-namespace plugin, so Paper reobf-remaps it at load and the bundled (spigot-mapped) InvUI works.
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
// InvUI is deliberately NOT bundled here. InvUI's inventory-access carries Spigot-mapped NMS
// reflection that only works after Paper's reobf remap, and that remap is driven by the *top-level*
// plugin's mappings namespace - which the host owns, not Mapper. A Mojang-namespace host (e.g.
// Mineplexlobby) never remaps it, so a bundled copy throws NoClassDefFoundError: EntityHuman.
// Instead we leave xyz.xenondevs un-relocated and let the host supply a single remapped copy
// (StudioEngine provides one on the shared classpath via join-classpath).
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
    // Relocate mapping-agnostic libs to avoid host collisions, but NOT xyz.xenondevs:
    // its references must resolve to the host-provided (remapped) InvUI classes.
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
// Shadow contributes shadowRuntimeElements to the java component, which would publish the fat jar as
// a second unclassified artifact and collide with the thin one. Skip it - the fat jar is published
// deliberately below, under its own classifier.
(components["java"] as AdhocComponentWithVariants)
        .withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) { skip() }

publishing {
    publications {
        create<MavenPublication>("Mapper") {
            from(components["java"])

            // Self-contained runnable plugin (bundles relocated InvUI/Jackson/Cloud).
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
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
}
