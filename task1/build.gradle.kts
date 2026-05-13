@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    wasmWasi {
        nodejs()
        binaries.executable()
    }
}

tasks.register("runWasm", Exec::class) {
    group = "run"
    description = "Compiles and runs Main.kt with Wasmtime (stdin echo loop)"

    dependsOn(":wasmtimeUnzip", "compileDevelopmentExecutableKotlinWasmWasi")

    val wasmtimeExecutable: File by rootProject.extra
    executable = wasmtimeExecutable.absolutePath

    args(
        "-W", "function-references,gc,exceptions",
        layout.buildDirectory.file(
            "compileSync/wasmWasi/main/developmentExecutable/kotlin/${rootProject.name}-${project.name}.wasm"
        ).get().asFile.absolutePath
    )

    standardInput = System.`in`
    environment("RUST_BACKTRACE", "full")
}
