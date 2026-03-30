plugins {
    base
}

allprojects {
    group = (findProperty("pluginGroup") as String?) ?: "com.cevapi"
    version = (findProperty("pluginVersion") as String?) ?: "1.1.0"
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io")
    }
}

tasks.named("build") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}
