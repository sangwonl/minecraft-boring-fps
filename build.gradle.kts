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

application {
    mainClass.set("dev.pineple.minecraft.HelloMinestom")
}
