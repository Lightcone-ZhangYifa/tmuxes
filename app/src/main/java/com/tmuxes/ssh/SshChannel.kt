package com.tmuxes.ssh

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

interface SshChannel : Closeable {
    val inputStream: InputStream
    val outputStream: OutputStream
}
