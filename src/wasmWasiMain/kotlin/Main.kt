@file:OptIn(kotlin.wasm.ExperimentalWasmInterop::class)

import kotlin.wasm.WasmExport
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

fun main() {
    while (true) {
        val line = readStdinLine() ?: break
        println("Wasm received: $line")
    }
}

@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasiRawFdRead(fd: Int, iovPtr: Int, iovCount: Int, nReadPtr: Int): Int

@WasmExport
fun dummy() {}

@OptIn(UnsafeWasmMemoryApi::class)
private fun readOneByte(): Byte? = withScopedMemoryAllocator { allocator ->
    val buf = allocator.allocate(1)
    val iov = allocator.allocate(8) // iovec: { buf_ptr: i32, buf_len: i32 }
    val nread = allocator.allocate(4)

    iov.storeInt(buf.address.toInt())
    Pointer(iov.address + 4u).storeInt(1)

    val ret = wasiRawFdRead(0, iov.address.toInt(), 1, nread.address.toInt())
    if (ret != 0) return@withScopedMemoryAllocator null

    val bytesRead = nread.loadInt()
    if (bytesRead <= 0) return@withScopedMemoryAllocator null

    buf.loadByte()
}

private fun readStdinLine(): String? {
    val sb = StringBuilder()
    var hasData = false

    while (true) {
        val byte = readOneByte()
        if (byte == null) {
            return if (hasData) sb.toString() else null
        }
        hasData = true
        if (byte == '\n'.code.toByte()) break
        if (byte != '\r'.code.toByte()) {
            sb.append(byte.toInt().toChar())
        }
    }

    return sb.toString()
}
