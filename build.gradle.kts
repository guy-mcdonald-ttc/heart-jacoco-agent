plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.nexusPublish)
}

group = "com.teamscale"

val appVersion by extra("31.0.0")

val snapshotVersion = appVersion + if (VersionUtils.isTaggedRelease()) "" else "-SNAPSHOT"

allprojects {
    version = snapshotVersion
}

// Installs all Maven artifacts to your local Maven repository
val publishToMavenLocal by tasks.registering

subprojects {
    // must be run after evaluation because the publishToMavenLocal tasks are generated by our plugin during
    // project evaluation
    afterEvaluate {
        val publishTask = tasks.findByPath("publishToMavenLocal")
        if (publishTask != null) {
            publishToMavenLocal.configure {
                dependsOn(publishTask)
            }
        }
    }
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

