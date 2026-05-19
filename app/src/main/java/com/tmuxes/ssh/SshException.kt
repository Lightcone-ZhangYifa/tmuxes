package com.tmuxes.ssh

/**
 * Wraps SSH-related errors with a meaningful message and optional cause.
 */
class SshException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
