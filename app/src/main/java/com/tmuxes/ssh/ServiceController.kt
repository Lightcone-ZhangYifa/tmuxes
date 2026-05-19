package com.tmuxes.ssh

interface ServiceController {
    fun ensureServiceRunning()
    fun stopService()
}
