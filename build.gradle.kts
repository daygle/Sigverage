// Top-level build file. AGP, Kotlin, and KSP are declared here with `apply false`
// so they can be reused in :app via the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
