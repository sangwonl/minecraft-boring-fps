plugins {
    java
    application
}

repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
        content { includeModule("net.minestom", "minestom") }
    }
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom:26_3-SNAPSHOT")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

// The client resource pack (weapon models, sounds) ships inside the jar and is served by the game server.
val pack by tasks.registering(Zip::class) {
    from("resourcepack")
    archiveFileName.set("pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.processResources { from(pack) }

application {
    mainClass.set("dev.pineple.minecraft.HelloMinestom")
}
