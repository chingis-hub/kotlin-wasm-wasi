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
    description = "Compiles and runs Task2.kt. Required: -Pwasmdir=<path> -Pinput=<file> -Poutput=<file>"

    dependsOn(":wasmtimeUnzip", "compileDevelopmentExecutableKotlinWasmWasi")

    val wasmtimeExecutable: File by rootProject.extra
    executable = wasmtimeExecutable.absolutePath

    // All three are required. They are appended after the wasm file path so Task2.kt
    // receives them as argv[1], argv[2], argv[3] via args_sizes_get + args_get.
    // wasmDir is also passed as --dir so wasmtime pre-opens that directory as fd 3.
    val wasmDir = (findProperty("wasmdir") as String?)
        ?: error("Provide -Pwasmdir=<path>")
    val input = (findProperty("input") as String?)
        ?: error("Provide -Pinput=<filename>")
    val output = (findProperty("output") as String?)
        ?: error("Provide -Poutput=<filename>")

    args(
        "--dir=$wasmDir",
        "-W", "function-references,gc,exceptions",
        layout.buildDirectory.file(
            "compileSync/wasmWasi/main/developmentExecutable/kotlin/${rootProject.name}-${project.name}.wasm"
        ).get().asFile.absolutePath,
        wasmDir,  // argv[1]
        input,    // argv[2]
        output    // argv[3]
    )
}
