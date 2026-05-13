My modifications for assignment

Template: https://github.com/Kotlin/kotlin-wasm-wasi-template

## Structure

Two independent subprojects, each compiled to its own `.wasm` binary:

| Subproject | Source | What it does |
|------------|--------|--------------|
| `task1` | `Main.kt` | Reads lines from stdin and echoes each back with a prefix |
| `task2` | `Task2.kt` | Copies `dummy_file.pdf` → `output.txt` using raw WASI file I/O |

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

Uses raw WASI syscalls: `path_open`, `fd_read`, `fd_write`, `fd_close`. Reads in 4 KB chunks. The `--dir=C:\kotlin` flag grants the sandbox access to that directory.

**Build:**
```bash
./gradlew :task2:compileDevelopmentExecutableKotlinWasmWasi
```

**Run:**
```bash
./gradlew :task2:runTask2
```
