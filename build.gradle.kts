plugins {
    java
    id("com.github.johnrengelman.shadow").version("6.1.0")
    `maven-publish`
}

allprojects {
    version = "2.0.0"
    group = "us.ajg0702"

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        ignoreFailures = false
        failFast = true
        maxParallelForks = (Runtime.getRuntime().availableProcessors() - 1).takeIf { it > 0 } ?: 1

        reports.html.isEnabled = false
        reports.junitXml.isEnabled = false
    }
}

repositories {
    mavenCentral()
    mavenLocal()

    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://gitlab.com/api/v4/projects/19978391/packages/maven") }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://repo.codemc.org/repository/maven-public") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven { url = uri("https://repo.codemc.io/repository/nms/") }
    maven { url = uri("https://repo.ajg0702.us") }
}

dependencies {
    testImplementation("junit:junit:4.12")

    compileOnly("me.clip:placeholderapi:2.10.4")
    compileOnly("net.kyori:adventure-text-serializer-bungeecord:4.0.0-SNAPSHOT")

    implementation("us.ajg0702:ajUtils:1.0.4")
    implementation("net.kyori:adventure-text-serializer-plain:4.0.0-SNAPSHOT")
}


tasks.withType<ProcessResources> {
    include("**/*.yml")
    filter<org.apache.tools.ant.filters.ReplaceTokens>(
            "tokens" to mapOf(
                    "VERSION" to project.version.toString()
            )
    )
}

tasks.shadowJar {
    relocate("us.ajg0702.utils", "us.ajg0702.queue.utils")
    relocate("org.bstats", "us.ajg0702.bstats")
    relocate("net.kyori", "us.ajg0702.queue.kyori")
    archiveFileName.set("${archiveBaseName.get()}-${archiveVersion.get()}.${archiveExtension.get()}")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks["jar"])
        }
    }

    repositories {

        val mavenUrl = "https://repo.ajg0702.us/releases"

        if(!System.getenv("REPO_TOKEN").isNullOrEmpty()) {
            maven {
                url = uri(mavenUrl)
                name = "ajRepo"

                credentials {
                    username = "plugins"
                    password = System.getenv("REPO_TOKEN")
                }
            }
        }
    }
}
