package com.tmuxes.data.model

/**
 * SSH authentication method.
 *
 * Every server provides its own credentials. If a server has a `parentId`,
 * the parent acts as a TCP-level ProxyJump tunnel only — it does not supply
 * authentication for the child. This matches OpenSSH's `ProxyJump` model.
 */
enum class AuthMethod {
    PASSWORD,
    KEY,
    KEY_WITH_PASSPHRASE
}
