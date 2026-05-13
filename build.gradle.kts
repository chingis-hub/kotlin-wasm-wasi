@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.internal.os.OperatingSystem
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.internal.file.archive.compression.*
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.io.*
import java.net.*
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.undercouchDownload) apply false
}

buildscript {
    dependencies {
        // to extract `tar.xz`
        classpath("org.tukaani:xz:1.10")
    }
}

repositories {
    mavenCentral()
}

kotlin {
    wasmWasi {
        nodejs()
        binaries.executable()
    }
}

enum class OsName { WINDOWS, MAC, LINUX, UNKNOWN }
enum class OsArch { X86_32, X86_64, ARM64, UNKNOWN }
data class OsType(val name: OsName, val arch: OsArch)

val currentOsType = run {
    val gradleOs = OperatingSystem.current()
    val osName = when {
        gradleOs.isMacOsX -> OsName.MAC
        gradleOs.isWindows -> OsName.WINDOWS
        gradleOs.isLinux -> OsName.LINUX
        else -> OsName.UNKNOWN
    }

    val osArch = when (providers.systemProperty("sun.arch.data.model").get()) {
        "32" -> OsArch.X86_32
        "64" -> when (providers.systemProperty("os.arch").get().lowercase(Locale.getDefault())) {
            "aarch64" -> OsArch.ARM64
            else -> OsArch.X86_64
        }
        else -> OsArch.UNKNOWN
    }

    OsType(osName, osArch)
}

// Wasmtime tasks
val wasmtimeVersion = "40.0.0"

val wasmtimeSuffix = when (currentOsType) {
    OsType(OsName.LINUX, OsArch.X86_64)   -> "x86_64-linux"
    OsType(OsName.LINUX, OsArch.ARM64)    -> "aarch64-linux"
    OsType(OsName.MAC, OsArch.X86_64)     -> "x86_64-macos"
    OsType(OsName.MAC, OsArch.ARM64)      -> "aarch64-macos"
    OsType(OsName.WINDOWS, OsArch.X86_32),
    OsType(OsName.WINDOWS, OsArch.X86_64) -> "x86_64-windows"

    else                                  -> error("unsupported os type $currentOsType")
}

val wasmtimeArtifactName = "wasmtime-v$wasmtimeVersion-$wasmtimeSuffix"

val unzipWasmtime = run {
    val wasmtimeDirectory = "https://github.com/bytecodealliance/wasmtime/releases/download/v$wasmtimeVersion"
    val archiveType = if (currentOsType.name == OsName.WINDOWS) "zip" else "tar.xz"
    val wasmtimeArchiveName = "$wasmtimeArtifactName.$archiveType"
    val wasmtimeLocation = "$wasmtimeDirectory/$wasmtimeArchiveName"

    val downloadedTools = File(layout.buildDirectory.asFile.get(), "tools")

    val downloadWasmtime = tasks.register("wasmtimeDownload", Download::class) {
        src(wasmtimeLocation)
        dest(File(downloadedTools, wasmtimeArchiveName))
        overwrite(false)
    }

    tasks.register("wasmtimeUnzip", Copy::class) {
        dependsOn(downloadWasmtime)

        val archive = downloadWasmtime.get().dest

        from(if (archive.extension == "zip") zipTree(archive) else tarTree(XzArchiver(archive)))

        into(downloadedTools.resolve(wasmtimeArtifactName))
    }
}

private class XzArchiver(private val file: File) : CompressedReadableResource {
    override fun read(): InputStream = org.tukaani.xz.XZInputStream(file.inputStream().buffered())
    override fun getURI(): URI = URIBuilder(file.toURI()).schemePrefix("xz:").build()
    override fun getBackingFile(): File = file
    override fun getBaseName(): String = file.name
    override fun getDisplayName(): String = file.path
}

// Compiles the WASI binary and runs it with Wasmtime, forwarding stdin/stdout.
// WASI _start entrypoint (= main()) is called directly.
tasks.register("runWasm", Exec::class) {
    group = "run"
    description = "Compiles and runs the Wasm binary with Wasmtime (stdin echo loop)"

    dependsOn(unzipWasmtime, "compileDevelopmentExecutableKotlinWasmWasi")

    val wasmtimeDir = unzipWasmtime.get().destinationDir.resolve(wasmtimeArtifactName)
    executable = wasmtimeDir.resolve(
        if (currentOsType.name == OsName.WINDOWS) "wasmtime.exe" else "wasmtime"
    ).absolutePath

    args(
        "-W", "function-references,gc,exceptions",
        layout.buildDirectory.file(
            "compileSync/wasmWasi/main/developmentExecutable/kotlin/${rootProject.name}.wasm"
        ).get().asFile.absolutePath
    )

    standardInput = System.`in`
    environment("RUST_BACKTRACE", "full")
}
