import org.gradle.internal.os.OperatingSystem
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.internal.file.archive.compression.*
import java.io.*
import java.net.*
import java.util.Locale

// `apply false` puts the plugin JAR on the buildscript classpath without applying it to this
// project. This lets us use the Download task type below, and lets subprojects apply the plugin
// themselves if they ever need it — without forcing it on anyone.
plugins {
    alias(libs.plugins.undercouchDownload) apply false
}

// The XZ decompression library is needed at build time (not at runtime) to unpack the
// wasmtime tar.xz archive on Linux/Mac. On Windows wasmtime ships as a .zip so this is a no-op,
// but it must be on the buildscript classpath unconditionally because the class is referenced
// in XzArchiver below regardless of OS.
buildscript {
    dependencies {
        classpath("org.tukaani:xz:1.10")
    }
}

// Make mavenCentral available to every subproject (:task1, :task2) so they can resolve
// Kotlin stdlib and other dependencies without repeating repositories {} in each build file.
allprojects {
    repositories {
        mavenCentral()
    }
}

// ---------------------------------------------------------------------------
// OS detection
// Wasmtime releases ship separate binaries per OS + architecture, so we need
// to know exactly what platform we're building on to pick the right download URL.
// ---------------------------------------------------------------------------

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

    // sun.arch.data.model is "32" or "64" (pointer width).
    // On 64-bit we then check os.arch to distinguish x86_64 from ARM64 (Apple Silicon / AWS Graviton).
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

// ---------------------------------------------------------------------------
// Wasmtime download
// Wasmtime is the WASI runtime that executes the compiled .wasm binaries.
// It is downloaded once into build/tools/ and shared by both :task1 and :task2.
// ---------------------------------------------------------------------------

val wasmtimeVersion = "40.0.0"

// Maps the detected OS+arch to the suffix used in the wasmtime release filename,
// e.g. OsType(WINDOWS, X86_64) -> "x86_64-windows" -> "wasmtime-v40.0.0-x86_64-windows.zip"
val wasmtimeSuffix = when (currentOsType) {
    OsType(OsName.LINUX, OsArch.X86_64)   -> "x86_64-linux"
    OsType(OsName.LINUX, OsArch.ARM64)    -> "aarch64-linux"
    OsType(OsName.MAC, OsArch.X86_64)     -> "x86_64-macos"
    OsType(OsName.MAC, OsArch.ARM64)      -> "aarch64-macos"
    OsType(OsName.WINDOWS, OsArch.X86_32),
    OsType(OsName.WINDOWS, OsArch.X86_64) -> "x86_64-windows"

    else                                  -> error("unsupported os type $currentOsType")
}

// Full artifact name, e.g. "wasmtime-v40.0.0-x86_64-windows".
// Used both as the archive filename stem and as the directory name inside the archive.
val wasmtimeArtifactName = "wasmtime-v$wasmtimeVersion-$wasmtimeSuffix"

// Gradle's CompressedReadableResource interface wraps a compressed file so that tarTree()
// can decompress it on the fly. The standard Gradle tarTree() handles .gz but not .xz,
// so we provide our own wrapper backed by the tukaani XZ library added in buildscript above.
private class XzArchiver(private val file: File) : CompressedReadableResource {
    override fun read(): InputStream = org.tukaani.xz.XZInputStream(file.inputStream().buffered())
    override fun getURI(): URI = URIBuilder(file.toURI()).schemePrefix("xz:").build()
    override fun getBackingFile(): File = file
    override fun getBaseName(): String = file.name
    override fun getDisplayName(): String = file.path
}

// Root directory for all downloaded tooling. Kept inside build/ so it is cleaned by `clean`.
val downloadedTools = File(layout.buildDirectory.asFile.get(), "tools")
// Windows uses .zip; Linux/Mac use .tar.xz.
val archiveExt = if (currentOsType.name == OsName.WINDOWS) "zip" else "tar.xz"

// Task 1 of 2: download the archive from GitHub releases.
// `overwrite(false)` means re-running the build won't re-download if the file is already there.
val downloadWasmtime = tasks.register("wasmtimeDownload", Download::class) {
    src("https://github.com/bytecodealliance/wasmtime/releases/download/v$wasmtimeVersion/$wasmtimeArtifactName.$archiveExt")
    dest(File(downloadedTools, "$wasmtimeArtifactName.$archiveExt"))
    overwrite(false)
}

// Task 2 of 2: extract the archive into build/tools/<artifactName>/.
// The archive itself contains a top-level directory also named <artifactName>, so the final
// wasmtime binary ends up at:
//   build/tools/<artifactName>/<artifactName>/wasmtime[.exe]
// That double-nesting is intentional and reflected in wasmtimeExecutable below.
tasks.register("wasmtimeUnzip", Copy::class) {
    dependsOn(downloadWasmtime)
    val archive = downloadWasmtime.get().dest
    from(if (archive.extension == "zip") zipTree(archive) else tarTree(XzArchiver(archive)))
    into(downloadedTools.resolve(wasmtimeArtifactName))
}

// Expose the wasmtime executable path to subprojects through Gradle's extra properties mechanism.
// In task1/build.gradle.kts and task2/build.gradle.kts, retrieve it with:
//   val wasmtimeExecutable: File by rootProject.extra
// The variable name used in `by extra(...)` becomes the lookup key, so the name must match exactly.
val wasmtimeExecutable: File by extra(
    downloadedTools
        .resolve(wasmtimeArtifactName) // into() destination of wasmtimeUnzip
        .resolve(wasmtimeArtifactName) // top-level dir inside the archive
        .resolve(if (currentOsType.name == OsName.WINDOWS) "wasmtime.exe" else "wasmtime")
)
