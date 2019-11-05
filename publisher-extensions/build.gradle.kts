import org.ostelco.prime.gradle.Version

plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":prime-modules"))

  implementation("com.google.cloud:google-cloud-pubsub:${Version.googleCloudPubSub}")
  implementation("com.google.code.gson:gson:${Version.gson}")
  implementation("com.google.guava:guava:${Version.guava}")
}