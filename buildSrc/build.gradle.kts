repositories {
  mavenCentral()
  google()
  jcenter()
  gradlePluginPortal()
  maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
  maven(url = "https://plugins.gradle.org/m2/")
}

plugins {
  `kotlin-dsl`
  `kotlin-dsl-precompiled-script-plugins`
}

internal object PluginVersions {
  const val kotlin = "1.4.0"
  const val androidGradle = "4.0.1"
  const val navigationSafeArgs = "2.3.0"
  const val googleServices = "4.3.3"
  const val crashlytics = "2.2.1"
  const val spotless = "5.3.0"
}

internal object Plugins {
  const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${PluginVersions.kotlin}"
  const val androidGradle = "com.android.tools.build:gradle:${PluginVersions.androidGradle}"
  const val navigationSafeArgs = "androidx.navigation:navigation-safe-args-gradle-plugin:${PluginVersions.navigationSafeArgs}"
  const val googleServices = "com.google.gms:google-services:${PluginVersions.googleServices}"
  const val crashlytics = "com.google.firebase:firebase-crashlytics-gradle:${PluginVersions.crashlytics}"
  const val spotless = "com.diffplug.spotless:spotless-plugin-gradle:${PluginVersions.spotless}"
}

dependencies {
  implementation(Plugins.androidGradle)
  implementation(Plugins.kotlin)

  implementation(Plugins.spotless)
  implementation(Plugins.navigationSafeArgs)

  implementation(Plugins.googleServices)
  implementation(Plugins.crashlytics)

  implementation("com.github.ben-manes:gradle-versions-plugin:0.31.0")
}