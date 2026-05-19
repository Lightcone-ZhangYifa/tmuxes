package com.tmuxes.ssh

import java.io.Closeable

interface PortForwardHandle : Closeable {
    val localPort: Int
    val remoteHost: String
    val remotePort: Int
    val isActive: Boolean
}
