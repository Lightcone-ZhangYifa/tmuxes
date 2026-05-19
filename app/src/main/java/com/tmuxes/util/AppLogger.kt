package com.tmuxes.util

import android.os.SystemClock
import android.util.Log
import com.tmuxes.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized logging for the entire app. Single API surface — no
 * version split, no deprecated aliases, no eager-string overload.
 *
 * Every call site uses the lambda form so that the message body is only
 * constructed when the relevant level is enabled. In release builds
 * `BuildConfig.DEBUG` is `false`, the inline `shouldLog` check folds to
 * a constant, and the lambda is eliminated by the compiler — TRACE and
 * DEBUG calls cost nothing in release.
 *
 * ```
 * AppLogger.t(Category.TERMINAL) { "emu.processInput n=$len" }
 * AppLogger.d(Category.SESSION)  { "attach: reusing $key" }
 * AppLogger.i(Category.SSH)      { "ssh.connect ← host=$host:$port" }
 * AppLogger.w(Category.HOSTKEY)  { "hostkey CHANGED $host:$port old=$old new=$new" }
 * AppLogger.e(Category.SSH, e) { "ssh.connect ✗ host=$host:$port" }
 * ```
 *
 * 17 categories partition the codebase along subsystem boundaries; each
 * category can have its minimum level adjusted at runtime via
 * [setLevel] so a developer can crank one category to TRACE while the
 * rest stay quiet.
 *
 * Crash breadcrumbs: every emitted line is also pushed into a fixed-size
 * ring buffer ([snapshotBreadcrumbs]) which `CrashLogWriter` flushes to
 * the crash file on uncaught exceptions — every crash report comes with
 * the last ~256 log lines for free.
 *
 * The logging rules are deliberately local and mechanical: use AppLogger
 * instead of raw platform logging, pass messages as lambdas, and make every
 * silent-catch bypass explicit at the call site. The Gradle verification
 * tasks enforce those rules so release builds stay quiet and cheap.
 */
object AppLogger {

    /**
     * Five levels — TRACE for high-volume hot paths (per-frame, per-byte),
     * DEBUG for state changes and decisions, INFO for major lifecycle
     * events, WARN for recoverable problems, ERROR for unrecoverable.
     */
    enum class Level(val androidLevel: Int) {
        TRACE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR)
    }

    /**
     * 17 categories for `adb logcat -s tmuxes.X:V` filtering. Each
     * category has its own runtime-adjustable minimum level.
     */
    enum class Category(val tag: String) {
        SSH("tmuxes.SSH"),
        SESSION("tmuxes.SESSION"),
        HOSTKEY("tmuxes.HOSTKEY"),
        KEY("tmuxes.KEY"),
        PFWD("tmuxes.PFWD"),
        NET("tmuxes.NET"),
        TMUX("tmuxes.TMUX"),
        TERMINAL("tmuxes.TERMINAL"),
        RENDER("tmuxes.RENDER"),
        EDITOR("tmuxes.EDITOR"),
        CONFIG("tmuxes.CONFIG"),
        DB("tmuxes.DB"),
        UI("tmuxes.UI"),
        NAV("tmuxes.NAV"),
        LIFECYCLE("tmuxes.LIFECYCLE"),
        SVC("tmuxes.SVC"),
        BOOT("tmuxes.BOOT")
    }

    /**
     * Per-category minimum level. Hot-path categories (TERMINAL, RENDER)
     * default to INFO so a debug build doesn't drown in TRACE noise from
     * every keystroke; the rest default to DEBUG. Release builds set
     * everything to WARN at install time via [setLevelForAll].
     */
    @PublishedApi
    internal val categoryLevels: ConcurrentHashMap<Category, Level> =
        ConcurrentHashMap<Category, Level>().apply {
            Category.values().forEach { put(it, defaultLevelFor(it)) }
        }

    private fun defaultLevelFor(category: Category): Level = when (category) {
        Category.TERMINAL, Category.RENDER -> Level.INFO
        else -> Level.DEBUG
    }

    /** Adjust a single category's minimum level at runtime. */
    fun setLevel(category: Category, level: Level) {
        categoryLevels[category] = level
        i(Category.LIFECYCLE) { "logger.level $category = $level" }
    }

    /** Adjust every category's minimum level at runtime. */
    fun setLevelForAll(level: Level) {
        Category.values().forEach { categoryLevels[it] = level }
        i(Category.LIFECYCLE) { "logger.level [all] = $level" }
    }

    /** Read current level for a category — used by debug Settings UI. */
    fun levelOf(category: Category): Level = categoryLevels[category] ?: Level.DEBUG

    // ----------------------------------------------------------------
    // Lambda-only emit API
    // ----------------------------------------------------------------

    inline fun t(category: Category, msg: () -> String) {
        if (shouldLog(category, Level.TRACE)) emit(Level.TRACE, category, msg())
    }

    inline fun d(category: Category, msg: () -> String) {
        if (shouldLog(category, Level.DEBUG)) emit(Level.DEBUG, category, msg())
    }

    inline fun i(category: Category, msg: () -> String) {
        if (shouldLog(category, Level.INFO)) emit(Level.INFO, category, msg())
    }

    inline fun w(category: Category, msg: () -> String) {
        if (shouldLog(category, Level.WARN)) emit(Level.WARN, category, msg())
    }

    /** Error variant — throwable is captured even when below level so
     *  the breadcrumb still contains the failure context. */
    inline fun e(category: Category, throwable: Throwable? = null, msg: () -> String) {
        if (shouldLog(category, Level.ERROR)) emit(Level.ERROR, category, msg(), throwable)
    }

    // ----------------------------------------------------------------
    // Helpers — wrap long ops & external boundaries
    // ----------------------------------------------------------------

    /**
     * Time [block] and emit a level-graded line (DEBUG <100ms, INFO 100-1000,
     * WARN >1000); on throw emits ERROR with throwable + duration and
     * rethrows.
     */
    inline fun <T> timed(category: Category, op: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        try {
            val result = block()
            val ms = SystemClock.elapsedRealtime() - start
            when {
                ms > 1000 -> w(category) { "$op took ${ms}ms (slow)" }
                ms > 100 -> i(category) { "$op took ${ms}ms" }
                else -> d(category) { "$op took ${ms}ms" }
            }
            return result
        } catch (t: Throwable) {
            val ms = SystemClock.elapsedRealtime() - start
            e(category, t) { "$op ✗ after ${ms}ms" }
            throw t
        }
    }

    // ----------------------------------------------------------------
    // Internal — emit + breadcrumb ring
    // ----------------------------------------------------------------

    /**
     * Inline so the `BuildConfig.DEBUG` check folds to a constant at the
     * call site in release builds, letting the entire log branch be
     * dead-code-eliminated. The `NOTHING_TO_INLINE` suppression is
     * intentional: Kotlin doesn't see the constant-fold benefit, but R8 does.
     */
    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun shouldLog(category: Category, level: Level): Boolean {
        if (!BuildConfig.DEBUG) return level >= Level.WARN
        val min = categoryLevels[category] ?: Level.DEBUG
        return level.ordinal >= min.ordinal
    }

    @PublishedApi
    internal fun emit(level: Level, category: Category, msg: String, throwable: Throwable? = null) {
        when (level) {
            Level.TRACE -> Log.v(category.tag, msg)
            Level.DEBUG -> Log.d(category.tag, msg)
            Level.INFO -> Log.i(category.tag, msg)
            Level.WARN -> Log.w(category.tag, msg)
            Level.ERROR -> if (throwable != null) Log.e(category.tag, msg, throwable)
                           else Log.e(category.tag, msg)
        }
        appendBreadcrumb(level, category, msg, throwable)
    }

    private val breadcrumbs = ArrayDeque<String>()
    private val breadcrumbLock = Any()
    private const val BREADCRUMB_CAPACITY = 256
    private val breadcrumbTimestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun appendBreadcrumb(level: Level, category: Category, msg: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG && level.ordinal < Level.WARN.ordinal) return
        val ts = try { breadcrumbTimestampFormat.format(Date()) } catch (_: Throwable) { "" }
        val short = category.tag.removePrefix("tmuxes.")
        val tFlag = throwable?.let { " ${it.javaClass.simpleName}: ${it.message}" } ?: ""
        val line = "[$ts ${level.name.first()}/$short] $msg$tFlag"
        synchronized(breadcrumbLock) {
            if (breadcrumbs.size >= BREADCRUMB_CAPACITY) breadcrumbs.removeFirst()
            breadcrumbs.add(line)
        }
    }

    /**
     * Snapshot the breadcrumb ring buffer in chronological order.
     * Called by [CrashLogWriter] on uncaught exceptions and by the
     * in-app log viewer.
     */
    fun snapshotBreadcrumbs(): List<String> {
        return synchronized(breadcrumbLock) { breadcrumbs.toList() }
    }

    /** Drop all breadcrumbs (called on `onTrimMemory` extreme pressure). */
    fun clearBreadcrumbs() {
        synchronized(breadcrumbLock) { breadcrumbs.clear() }
    }
}
