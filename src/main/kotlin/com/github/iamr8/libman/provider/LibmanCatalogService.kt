package com.github.iamr8.libman.provider

import com.github.iamr8.libman.settings.LibmanSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Caches provider catalog lookups per project so versions aren't re-fetched on every highlight pass.
 * Entries expire after the configured TTL; the "Check for updates" link and opening the file force a
 * refresh. The annotator/inlays render from the cache only (never fetch).
 *
 * `getOrFetch`/`refreshNow` are blocking and run only inside the visible, cancellable background
 * tasks below ([sweepOnOpen] and [refreshInBackground]), never on the EDT or the highlighting thread.
 * When fresh data arrives, a debounced daemon restart re-renders the highlight and inlays.
 */
@Service(Service.Level.PROJECT)
class LibmanCatalogService(private val project: Project) {

    private data class Entry(val info: LibInfo?, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    // Completion and `files` checks: search results per (provider, query), file lists per version.
    private data class Timed<T>(val value: T, val at: Long)
    private val searches = ConcurrentHashMap<String, Timed<List<LibrarySuggestion>>>()
    private val fileLists = ConcurrentHashMap<String, Timed<List<String>?>>()
    private val filesInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val refreshPending = AtomicBoolean(false)

    // One open-file sweep at a time per manifest: the entry both guards against a close+reopen
    // double-check and holds the indicator so a file close can cancel its in-flight sweep.
    private val sweeps = ConcurrentHashMap<VirtualFile, ProgressIndicator>()

    /** Fresh cached info, or null if missing/expired (never triggers a fetch). */
    fun getCached(provider: String?, name: String): LibInfo? =
        cache[key(provider, name)]?.takeIf { fresh(it) }?.info

    /**
     * Cached-if-fresh, else fetch now (blocking). Off-EDT only. Does NOT trigger a UI refresh -
     * the calling task calls [requestRefresh] once it has filled the cache.
     */
    fun getOrFetch(provider: String?, name: String): LibInfo? {
        val k = key(provider, name)
        cache[k]?.let { if (fresh(it)) return it.info }
        val info = ProviderCatalog.fetch(provider, name)
        cache[k] = Entry(info, System.currentTimeMillis())
        return info
    }

    /** Drop the cached entry and fetch again (blocking). Off-EDT only. */
    fun refreshNow(provider: String?, name: String): LibInfo? {
        cache.remove(key(provider, name))
        return getOrFetch(provider, name)
    }

    /** Drop the cached entry and re-fetch on a background thread (the manual "Check for updates"). */
    fun refreshInBackground(provider: String?, name: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Checking $name for updates", true) {
            override fun run(indicator: ProgressIndicator) {
                refreshNow(provider, name)
                requestRefresh()
            }
        })
    }

    /**
     * Force-refresh the given `(provider, name)` libraries of [file] on a background thread when the
     * manifest is opened. A second call for the same file while a sweep is running is ignored (no
     * double-check on close+reopen); [cancelSweep] stops the running one when the file closes.
     */
    fun sweepOnOpen(file: VirtualFile, entries: List<Pair<String?, String>>) {
        if (entries.isEmpty()) return
        val task = object : Task.Backgroundable(project, "Checking client-side libraries", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    for ((provider, name) in entries) {
                        indicator.checkCanceled()
                        refreshNow(provider, name)
                    }
                    requestRefresh()
                } finally {
                    // Remove only if this sweep is still the registered one (a reopen may have
                    // replaced it), so we never evict a newer sweep's indicator.
                    sweeps.remove(file, indicator)
                }
            }
        }
        val indicator = BackgroundableProcessIndicator(task)
        // Already sweeping this file -> drop the duplicate (no double-check on close+reopen).
        if (sweeps.putIfAbsent(file, indicator) != null) return
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
    }

    /** Cancel the in-flight open-sweep for [file] (called when the file closes). */
    fun cancelSweep(file: VirtualFile) {
        sweeps.remove(file)?.cancel()
    }

    /**
     * Library names matching [query] from the provider's search (blocking, cached for the TTL).
     * Off-EDT only. Failures are not cached, so the next completion tries again.
     */
    fun search(provider: String?, query: String): List<LibrarySuggestion>? {
        val k = key(provider, query)
        searches[k]?.takeIf { fresh(it.at) }?.let { return it.value }
        val hits = ProviderCatalog.search(provider, query) ?: return null
        searches[k] = Timed(hits, System.currentTimeMillis())
        return hits
    }

    /** Fresh cached file list of a library version, or null if not fetched, expired, or failed. Never fetches. */
    fun getCachedFiles(ref: LibraryVersionRef): List<String>? =
        fileLists[filesKey(ref)]?.takeIf { fresh(it.at) }?.value

    /**
     * Cached-if-fresh, else fetch now (blocking). Off-EDT only. A failed fetch is cached as null for
     * the TTL, so the annotator does not retry in a loop; [retryFailed] (completion) tries again.
     */
    fun getOrFetchFiles(ref: LibraryVersionRef, retryFailed: Boolean = false): List<String>? {
        val k = filesKey(ref)
        fileLists[k]?.takeIf { fresh(it.at) && (it.value != null || !retryFailed) }?.let { return it.value }
        val files = ProviderCatalog.files(ref.provider, ref.name, ref.version)
        fileLists[k] = Timed(files, System.currentTimeMillis())
        return files
    }

    /**
     * Fetch the file lists of [refs] that are not cached yet, in one visible, cancellable
     * background task, then re-render (the annotator checks `files` from the cache only).
     */
    fun prefetchFiles(refs: Collection<LibraryVersionRef>) {
        val todo = refs.distinct().filter { r ->
            fileLists[filesKey(r)]?.takeIf { fresh(it.at) } == null && filesInFlight.add(filesKey(r))
        }
        if (todo.isEmpty()) return
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) {
                todo.forEach { filesInFlight.remove(filesKey(it)) }
                return@invokeLater
            }
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching library file lists", true) {
                override fun run(indicator: ProgressIndicator) {
                    for (r in todo) {
                        indicator.checkCanceled()
                        getOrFetchFiles(r)
                    }
                }

                // Not on cancel: a re-render would start the fetch again right away.
                override fun onSuccess() = requestRefresh()

                override fun onFinished() {
                    todo.forEach { filesInFlight.remove(filesKey(it)) }
                }
            })
        }, ModalityState.any())
    }

    /** Debounced daemon restart so a freshly filled cache re-renders the highlight and inlays. */
    fun requestRefresh() {
        if (!refreshPending.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater({
            refreshPending.set(false)
            if (!project.isDisposed) {
                // The inlay pass skips work when the PSI modification stamp is unchanged (see
                // InlayHintsPassFactoryInternal.createHighlightingPass). Our fetch fills the cache
                // without editing the file, so a plain daemon restart re-runs the annotator (recolors
                // the version) but leaves the "Update to X" inlay stale. Clearing the stamp in the
                // same EDT transaction as the restart forces the inlay to recompute too.
                InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }, ModalityState.any())
    }

    private fun fresh(e: Entry): Boolean = fresh(e.at)

    private fun fresh(at: Long): Boolean =
        System.currentTimeMillis() - at < LibmanSettings.getInstance().cacheTtlMinutes * 60_000L

    private fun filesKey(ref: LibraryVersionRef): String = key(ref.provider, "${ref.name}@${ref.version}")

    private fun key(provider: String?, name: String): String =
        "${provider?.trim()?.lowercase().orEmpty()}::$name"

    companion object {
        fun getInstance(project: Project): LibmanCatalogService = project.service()
    }
}
