plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17.19"
    id("maven-publish")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of((findProperty("fabricJavaVersion") as String).toInt()))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabricApiVersion")}") {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-data-generation-api-v1")
    }
    implementation(project(":common"))
    implementation("org.yaml:snakeyaml:2.2")
    include("org.yaml:snakeyaml:2.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "mod_name" to ((findProperty("pluginName") as String?) ?: "OminousChestLock"),
                "minecraftVersion" to project.property("minecraftVersion"),
                "fabricLoaderVersion" to project.property("fabricLoaderVersion"),
                "fabricApiVersion" to project.property("fabricApiVersion")
            )
        )
    }
}

tasks.jar {
    archiveBaseName.set((findProperty("pluginName") as String?) ?: "OminousChestLock")
    val baseName = (findProperty("pluginName") as String?) ?: "OminousChestLock"
    archiveFileName.set("${baseName}-${project.version}_Fabric.jar")
}
