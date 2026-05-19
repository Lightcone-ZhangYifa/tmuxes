package com.tmuxes.ssh

fun shellEscape(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"
