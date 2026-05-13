My modifications for assignment

Template: https://github.com/Kotlin/kotlin-wasm-wasi-template

## Structure

Two independent subprojects, each compiled to its own `.wasm` binary:

| Subproject | Source | What it does |
|------------|--------|--------------|
| `task1` | `Main.kt` | Reads lines from stdin and echoes each back with a prefix |
| `task2` | `Task2.kt` | Copies any file using raw WASI file I/O; dir, input and output are runtime arguments |

Wasmtime is downloaded once by the root project and shared between both tasks.

## Task 1 — stdin echo loop

Implemented using raw WASI `fd_read` syscalls (byte-by-byte) because `readln()` is not available in the Kotlin/Wasm WASI target.

```
Wasm received: <your input>
```

**Build:**
```bash
./gradlew :task1:compileDevelopmentExecutableKotlinWasmWasi
```

**Run** (type lines, press Enter; Ctrl+D to stop):
```bash
./gradlew :task1:runWasm --console=plain
```

## Task 2 — file copy

Copies any file using raw WASI syscalls: `path_open`, `fd_read`, `fd_write`, `fd_close`. Reads in 4 KB chunks.

All three parameters are passed at runtime via Gradle properties and forwarded to the wasm binary as `argv[1..3]`, read with the `args_sizes_get` and `args_get` WASI syscalls. The directory is also passed as `--dir` to wasmtime to grant sandbox access to that path.

**Build:**
```text
./gradlew :task2:compileDevelopmentExecutableKotlinWasmWasi
```

**Run**
```
./gradlew :task2:runTask2 "-Pwasmdir=C:\kotlin" "-Pinput=dummy_file.pdf" "-Poutput=output.pdf"
```
