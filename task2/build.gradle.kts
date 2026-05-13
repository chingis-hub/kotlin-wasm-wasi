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

tasks.register("runTask2", Exec::class) {
    group = "run"
    description = "Compiles and runs Task2.kt with Wasmtime (file copy: dummy_file.pdf -> output.txt)"

    dependsOn(":wasmtimeUnzip", "compileDevelopmentExecutableKotlinWasmWasi")

    val wasmtimeExecutable: File by rootProject.extra
    executable = wasmtimeExecutable.absolutePath

    args(
        "--dir=C:\\kotlin",
        "-W", "function-references,gc,exceptions",
        layout.buildDirectory.file(
            "compileSync/wasmWasi/main/developmentExecutable/kotlin/${rootProject.name}-${project.name}.wasm"
        ).get().asFile.absolutePath
    )
}
