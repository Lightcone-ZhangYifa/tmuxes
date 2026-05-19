package com.tmuxes.tmux

import com.tmuxes.ssh.SshConnection
import com.tmuxes.ssh.SshSession
import com.tmuxes.ssh.shellEscape
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages tmux sessions on a remote server via SSH.
 *
 * All methods are suspend functions that execute on [Dispatchers.IO].
 * The caller is responsible for providing an authenticated [SshConnection].
 */
object TmuxManager {

    /**
     * Lists all tmux sessions on the remote server.
     *
     * Executes `tmux list-sessions` and parses the structured output.
     * Returns an empty list only when tmux reports there are no sessions,
     * or if the output is empty.
     *
     * @param connection An authenticated SSH connection to the server.
     * @param serverId The database ID of the server (attached to each result).
     * @param serverName The display name of the server (attached to each result).
     * @param serverColor The server identity color (attached to each result).
     * @return A list of [TmuxSession] objects representing remote sessions.
     */
    suspend fun listSessions(
        connection: SshConnection,
        serverId: Long = 0,
        serverName: String = "",
        serverColor: Int = 0
    ): List<TmuxSession> = withContext(Dispatchers.IO) {
        AppLogger.d(Category.TMUX) { "Listing tmux sessions on server '$serverName' (id=$serverId)" }
        val output = try {
            connection.executeCommand(
                "tmux list-sessions -F \"#{session_name}|#{session_windows}|#{session_created}|#{session_attached}\""
            )
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            if (message.contains("no server running", ignoreCase = true) ||
                message.contains("no sessions", ignoreCase = true)
            ) {
                AppLogger.d(Category.TMUX) { "No tmux sessions found on server '$serverName': $message" }
                return@withContext emptyList()
            }
            throw e
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split("|")
                if (parts.size < 4) return@mapNotNull null

                val name = parts[0]
                val windowCount = parts[1].toIntOrNull() ?: 0
                val createdEpoch = parts[2].toLongOrNull() ?: 0L
                val attachedCount = parts[3].toIntOrNull() ?: 0

                val createdAt = if (createdEpoch > 0) {
                    dateFormat.format(Date(createdEpoch * 1000L))
                } else {
                    "unknown"
                }

                TmuxSession(
                    name = name,
                    windowCount = windowCount,
                    createdAt = createdAt,
                    attached = attachedCount > 0,
                    serverId = serverId,
                    serverName = serverName,
                    serverColor = serverColor
                )
            }
    }

    /**
     * Attaches to an existing tmux session (or creates it if it does not exist)
     * and returns an interactive [SshSession] with a PTY.
     *
     * Uses `tmux new-session -A -s <name>` which attaches if the session exists
     * or creates a new one otherwise.
     *
     * @param connection An authenticated SSH connection.
     * @param sessionName The tmux session name to attach to.
     * @param rows Terminal height in rows.
     * @param cols Terminal width in columns.
     * @return An [SshSession] connected to the tmux session.
     */
    suspend fun attachSession(
        connection: SshConnection,
        sessionName: String,
        rows: Int,
        cols: Int
    ): SshSession = withContext(Dispatchers.IO) {
        AppLogger.i(Category.TMUX) { "Attaching to tmux session '$sessionName'" }
        // Allocate a PTY and exec tmux directly — no login shell wrapper.
        // When tmux exits, the channel closes; the user is never dropped
        // into a free shell (the whole app is tmux-only by design).
        val command = "tmux new-session -A -s ${shellEscape(sessionName)}"
        connection.openSession(rows, cols, command)
    }

    /**
     * Creates a detached tmux session and returns the actual session name.
     *
     * Uses tmux's print mode so blank names are resolved by tmux itself and the
     * caller always receives the final created session name.
     */
    suspend fun createDetachedSession(
        connection: SshConnection,
        requestedName: String
    ): String = withContext(Dispatchers.IO) {
        val normalizedName = requestedName.trim()
        AppLogger.i(Category.TMUX) { "Creating detached tmux session '${normalizedName.ifBlank { "<auto>" }}'" }
        val command = if (normalizedName.isBlank()) {
            "tmux new-session -d -P -F \"#{session_name}\""
        } else {
            "tmux new-session -d -P -F \"#{session_name}\" -s ${shellEscape(normalizedName)}"
        }
        connection.executeCommand(command).trim().lineSequence().firstOrNull { it.isNotBlank() }
            ?: throw IllegalStateException("tmux did not return a session name")
    }

    /**
     * Creates a new detached tmux session and then attaches to it.
     *
     * First creates the session with `tmux new-session -d -s <name>`, then
     * opens an interactive shell and attaches via `tmux attach -t <name>`.
     *
     * @param connection An authenticated SSH connection.
     * @param sessionName The name for the new tmux session.
     * @param rows Terminal height in rows.
     * @param cols Terminal width in columns.
     * @return An [SshSession] connected to the newly created tmux session.
     */
    suspend fun createSession(
        connection: SshConnection,
        sessionName: String,
        rows: Int,
        cols: Int
    ): SshSession = withContext(Dispatchers.IO) {
        if (sessionName.isNotBlank()) {
            // Named session: attach directly (creates if absent thanks to -A flag)
            attachSession(connection, sessionName.trim(), rows, cols)
        } else {
            // Blank name: let tmux auto-name via detached create, then attach
            val resolvedName = createDetachedSession(connection, sessionName)
            connection.openSession(rows, cols, "tmux attach-session -t ${shellEscape(resolvedName)}")
        }
    }

    /**
     * Reads recent command history from the remote shell's history file.
     *
     * This intentionally reads persisted shell history instead of terminal input
     * or pane scrollback so the UI shows actual commands submitted by the shell.
     */
    suspend fun readShellHistory(
        connection: SshConnection,
        sessionName: String,
        maxEntries: Int = 50
    ): List<String> = withContext(Dispatchers.IO) {
        val limit = maxEntries.coerceIn(1, 500)
        val targetSession = shellEscape(sessionName)
        val script = """
            pane_info=$(tmux list-panes -t $targetSession -F '#{pane_active}|#{pane_pid}|#{pane_current_command}' 2>/dev/null | awk -F'|' '$1 == 1 { print; found=1; exit } END { if (!found) print "" }')
            if [ -z "${'$'}pane_info" ]; then
              pane_info=$(tmux list-panes -t $targetSession -F '#{pane_active}|#{pane_pid}|#{pane_current_command}' 2>/dev/null | head -n 1)
            fi
            if [ -z "${'$'}pane_info" ]; then
              exit 0
            fi

            pane_pid=$(printf '%s' "${'$'}pane_info" | cut -d'|' -f2)
            pane_cmd=$(printf '%s' "${'$'}pane_info" | cut -d'|' -f3)

            find_shell_pid() {
              current_pid="${'$'}1"
              while [ -n "${'$'}current_pid" ] && [ "${'$'}current_pid" != "0" ] && [ -r "/proc/${'$'}current_pid/status" ]; do
                proc_name=$(sed -n 's/^Name:\t//p' "/proc/${'$'}current_pid/status" | head -n 1)
                case "${'$'}proc_name" in
                  bash|zsh|fish|sh|dash|ash)
                    printf '%s' "${'$'}current_pid"
                    return 0
                    ;;
                esac
                current_pid=$(sed -n 's/^PPid:\t//p' "/proc/${'$'}current_pid/status" | head -n 1)
              done
              return 1
            }

            shell_pid=$(find_shell_pid "${'$'}pane_pid" || true)
            info_pid="${'$'}{shell_pid:-${'$'}pane_pid}"
            info_env="/proc/${'$'}info_pid/environ"
            home_dir="${'$'}HOME"
            shell_path=""
            histfile=""
            env_dump=""

            if [ -r "${'$'}info_env" ]; then
              env_dump=$(tr '\0' '\n' < "${'$'}info_env")
              histfile=$(printf '%s\n' "${'$'}env_dump" | sed -n 's/^HISTFILE=//p' | head -n 1)
              home_dir=$(printf '%s\n' "${'$'}env_dump" | sed -n 's/^HOME=//p' | head -n 1)
              shell_path=$(printf '%s\n' "${'$'}env_dump" | sed -n 's/^SHELL=//p' | head -n 1)
            fi

            if [ -z "${'$'}shell_path" ] && [ -n "${'$'}shell_pid" ] && [ -r "/proc/${'$'}shell_pid/exe" ]; then
              shell_path=$(readlink -f "/proc/${'$'}shell_pid/exe" 2>/dev/null || true)
            fi
            if [ -z "${'$'}shell_path" ]; then
              shell_path=$(command -v "${'$'}pane_cmd" 2>/dev/null || true)
            fi
            shell_name=$(basename "${'$'}shell_path")

            case "${'$'}histfile" in
              "")
                ;;
              /*)
                ;;
              *)
                histfile="${'$'}home_dir/${'$'}histfile"
                ;;
            esac

            if [ -z "${'$'}histfile" ] || [ ! -f "${'$'}histfile" ]; then
              case "${'$'}shell_name" in
                zsh)
                  histfile="${'$'}home_dir/.zsh_history"
                  ;;
                fish)
                  histfile="${'$'}home_dir/.local/share/fish/fish_history"
                  if [ ! -f "${'$'}histfile" ]; then
                    histfile="${'$'}home_dir/.config/fish/fish_history"
                  fi
                  ;;
                *)
                  histfile="${'$'}home_dir/.bash_history"
                  ;;
              esac
            fi

            if [ -z "${'$'}histfile" ] || [ ! -f "${'$'}histfile" ]; then
              exit 0
            fi

            case "${'$'}histfile" in
              *.zsh_history)
                tail -n $limit "${'$'}histfile" | sed 's/^: [0-9][0-9]*:[0-9][0-9]*;//'
                ;;
              *fish_history)
                if command -v tac >/dev/null 2>&1; then
                  tac "${'$'}histfile" | awk -v limit=$limit '
                    /^- cmd: / {
                      sub(/^- cmd: /, "")
                      buffer[count % limit] = $0
                      count++
                      if (count >= limit) exit
                    }
                    END {
                      size = count < limit ? count : limit
                      for (i = size - 1; i >= 0; i--) print buffer[i]
                    }
                  '
                else
                  awk -v limit=$limit '
                    /^- cmd: / {
                      sub(/^- cmd: /, "")
                      buffer[count % limit] = $0
                      count++
                    }
                    END {
                      start = count > limit ? count % limit : 0
                      size = count < limit ? count : limit
                      for (i = 0; i < size; i++) print buffer[(start + i) % limit]
                    }
                  ' "${'$'}histfile"
                fi
                ;;
              *)
                tail -n $limit "${'$'}histfile"
                ;;
            esac
        """.trimIndent()

        runCatching {
            connection.executeCommand(script)
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
        }.getOrDefault(emptyList())
    }

    /**
     * Kills (destroys) a tmux session on the remote server.
     *
     * @param connection An authenticated SSH connection.
     * @param sessionName The name of the session to kill.
     */
    suspend fun killSession(
        connection: SshConnection,
        sessionName: String
    ) = withContext(Dispatchers.IO) {
        AppLogger.i(Category.TMUX) { "Killing tmux session '$sessionName'" }
        connection.executeCommand("tmux kill-session -t ${shellEscape(sessionName)}")
    }

    /**
     * Renames an existing tmux session.
     *
     * @param connection An authenticated SSH connection.
     * @param oldName The current session name.
     * @param newName The desired new session name.
     */
    suspend fun renameSession(
        connection: SshConnection,
        oldName: String,
        newName: String
    ) = withContext(Dispatchers.IO) {
        connection.executeCommand(
            "tmux rename-session -t ${shellEscape(oldName)} ${shellEscape(newName)}"
        )
    }

    /**
     * Detaches from a tmux session by sending the tmux prefix key followed
     * by 'd' to the interactive shell session.
     *
     * The prefix byte must match the user's tmux config; resolve it via
     * `tmuxPrefixByteFor(preferences.get(Settings.tmuxPrefixKey))` from the
     * caller (this class has no access to AppPreferences).
     *
     * @param session The active [SshSession] attached to a tmux session.
     * @param prefixByte The tmux prefix byte (e.g. 0x02 for Ctrl-B).
     */
    suspend fun detachSession(session: SshSession, prefixByte: Byte = 0x02) = withContext(Dispatchers.IO) {
        session.write(byteArrayOf(prefixByte))
        // Small delay to let tmux process the prefix before sending the command key
        kotlinx.coroutines.delay(50)
        session.write(byteArrayOf('d'.code.toByte()))
    }

    /**
     * Checks whether tmux is installed and available on the remote server.
     *
     * @param connection An authenticated SSH connection.
     * @return `true` if tmux is found in the PATH, `false` otherwise.
     */
    suspend fun hasTmux(connection: SshConnection): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = connection.executeCommand("command -v tmux")
            output.trim().isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

}
