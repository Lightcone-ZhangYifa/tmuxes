package com.tmuxes.tmux

/**
 * Represents a single tmux session on a remote server.
 *
 * @param name The tmux session name.
 * @param windowCount Number of windows in the session.
 * @param createdAt Human-readable creation timestamp.
 * @param attached Whether a client is currently attached to this session.
 * @param serverId The database ID of the server this session belongs to.
 * @param serverName The friendly display name of the server.
 * @param serverColor The server identity color, or 0 for default.
 */
data class TmuxSession(
    val name: String,
    val windowCount: Int,
    val createdAt: String,
    val attached: Boolean,
    val serverId: Long,
    val serverName: String,
    val serverColor: Int = 0
)
