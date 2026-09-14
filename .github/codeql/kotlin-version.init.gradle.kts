// CodeQL-only Kotlin version pin.
//
// CodeQL's Kotlin extractor only understands releases up to a maximum version
// that lags a little behind the newest Kotlin. When the version catalog is
// ahead of what the installed CodeQL bundle supports, the traced
// `compileDebugKotlin` step aborts with:
//
//   Kotlin version <x.y.z> is too recent. CodeQL currently supports versions below <x.y.z>
//
// and the whole `Analyze (java-kotlin)` job fails as a "configuration error",
// even though the regular Android CI build compiles the same sources fine.
//
// This init script is applied via `--init-script` from the CodeQL workflow
// ONLY. It overrides the `kotlin` version in the `libs` version catalog (the
// single lever that drives the Kotlin compiler and the lockstep-versioned
// Compose compiler plugin in this project) down to the newest release the
// extractor supports, so CodeQL can build and analyse the code. Regular
// builds, tests and releases keep the catalog version untouched.
//
// Bump `codeqlKotlinVersion` toward the catalog's `kotlin` as CodeQL adds
// support; once the installed bundle supports the catalog version this pin is
// unnecessary but harmless, and the file can be removed.
val codeqlKotlinVersion = "2.4.10"

settingsEvaluated {
    dependencyResolutionManagement.versionCatalogs
        .maybeCreate("libs")
        .version("kotlin", codeqlKotlinVersion)
}
