plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of((findProperty("javaVersion") as String).toInt()))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    implementation(project(":common"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.jar {
    val baseName = (findProperty("pluginName") as String?) ?: "OminousChestLock"
    archiveFileName.set("${baseName}-${project.version}_Paper.jar")
}
