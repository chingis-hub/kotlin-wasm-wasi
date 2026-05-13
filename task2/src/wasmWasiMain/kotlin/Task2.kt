@file:OptIn(kotlin.wasm.ExperimentalWasmInterop::class)

import kotlin.wasm.WasmExport
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

private const val DIR_FD = 3      // first --dir pre-opened directory fd
private const val CHUNK_SIZE = 4096

fun main() {
    copyFile("dummy_file.pdf", "output.txt")
}

// opens a file or directory relative to a pre-opened directory fd
@WasmImport("wasi_snapshot_preview1", "path_open")
private external fun wasiRawPathOpen(
    fd: Int, dirflags: Int,
    pathPtr: Int, pathLen: Int,
    oflags: Int,
    fsRightsBase: Long, fsRightsInheriting: Long,
    fdflags: Int,
    openedFdPtr: Int  // out: new fd written here
): Int

@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasiRawFdRead(fd: Int, iovPtr: Int, iovCount: Int, nReadPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun wasiRawFdWrite(fd: Int, iovPtr: Int, iovCount: Int, nWrittenPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_close")
private external fun wasiRawFdClose(fd: Int): Int

@WasmExport
fun dummy() {}

@OptIn(UnsafeWasmMemoryApi::class)
private fun copyFile(inputPath: String, outputPath: String) {
    withScopedMemoryAllocator { allocator ->

        // --- open input file ---

        val inputBytes = inputPath.encodeToByteArray()
        val inputPathMem = allocator.allocate(inputBytes.size)
        for (i in inputBytes.indices) {
            Pointer(inputPathMem.address + i.toUInt()).storeByte(inputBytes[i])
        }

        val inputFdPtr = allocator.allocate(4)
        val r1 = wasiRawPathOpen(
            DIR_FD, 0,
            inputPathMem.address.toInt(), inputBytes.size,
            0,       // oflags: open existing, no create
            2L, 0L,  // rights: fd_read (bit 1)
            0,
            inputFdPtr.address.toInt()
        )
        check(r1 == 0) { "path_open input failed: errno $r1" }
        val inputFd = inputFdPtr.loadInt()

        // --- open output file ---

        val outputBytes = outputPath.encodeToByteArray()
        val outputPathMem = allocator.allocate(outputBytes.size)
        for (i in outputBytes.indices) {
            Pointer(outputPathMem.address + i.toUInt()).storeByte(outputBytes[i])
        }

        val outputFdPtr = allocator.allocate(4)
        val r2 = wasiRawPathOpen(
            DIR_FD, 0,
            outputPathMem.address.toInt(), outputBytes.size,
            9,        // oflags: O_CREAT(1) | O_TRUNC(8)
            64L, 0L,  // rights: fd_write (bit 6)
            0,
            outputFdPtr.address.toInt()
        )
        check(r2 == 0) { "path_open output failed: errno $r2" }
        val outputFd = outputFdPtr.loadInt()

        // --- copy loop ---

        val buf = allocator.allocate(CHUNK_SIZE)
        val readIov = allocator.allocate(8)   // iovec { buf_ptr: i32, buf_len: i32 }
        val writeIov = allocator.allocate(8)
        val nBytes = allocator.allocate(4)

        readIov.storeInt(buf.address.toInt())
        Pointer(readIov.address + 4u).storeInt(CHUNK_SIZE)
        writeIov.storeInt(buf.address.toInt())

        var total = 0
        while (true) {
            val rr = wasiRawFdRead(inputFd, readIov.address.toInt(), 1, nBytes.address.toInt())
            if (rr != 0) break
            val n = nBytes.loadInt()
            if (n <= 0) break

            // last chunk may be smaller than CHUNK_SIZE — pass exact count to fd_write
            Pointer(writeIov.address + 4u).storeInt(n)
            wasiRawFdWrite(outputFd, writeIov.address.toInt(), 1, nBytes.address.toInt())
            total += n
        }

        wasiRawFdClose(inputFd)
        wasiRawFdClose(outputFd)

        println("Copied $total bytes: $inputPath -> $outputPath")
    }
}
