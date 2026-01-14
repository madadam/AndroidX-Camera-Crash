plugins {
    val kotlinVersion = "2.3.0"

    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion apply false
}