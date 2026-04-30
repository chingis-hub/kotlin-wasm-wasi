My modifications for assignment

Template: https://github.com/Kotlin/kotlin-wasm-wasi-template

## What it does

Reads lines from stdin in a loop and echoes each one back to stdout with a prefix:

```
Wasm received: <input>
```

Implemented using raw WASI `fd_read` syscalls because `readln()` is not available in the Kotlin/Wasm WASI target.

## Build

```bash
./gradlew compileKotlinWasmWasi
```

## Run

Compiles if needed, then runs the binary with Wasmtime. Type lines and press Enter; Ctrl+D to stop.

```bash
./gradlew runWasm --console=plain
```