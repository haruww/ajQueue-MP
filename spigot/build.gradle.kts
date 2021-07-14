plugins {
    `java-library`
    `maven-publish`
}

group = "us.ajg0702.queue.spigot"

repositories {
    mavenCentral()

    maven { url = uri("https://repo.ajg0702.us") }
}

dependencies {
    implementation("net.kyori:adventure-api:4.8.1")
    compileOnly("com.google.guava:guava:30.1.1-jre")

    compileOnly("us.ajg0702:ajUtils:1.1.6")
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