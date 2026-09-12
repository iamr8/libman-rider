package com.github.iamr8.libman.provider

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Caches provider catalog lookups per project so versions aren't re-fetched on every highlight pass.
 * Entries expire after [TTL_MS]; the "Check for updates" chip forces a refresh.
 *
 * `getOrFetch` is blocking and must be called off the EDT (the annotator's `doAnnotate`).
 * When fresh data arrives, a debounced daemon restart re-renders the highlight and inlays.
 */
@Service(Service.Level.PROJECT)
class LibmanCatalogService(private val project: Project) {

    private data class Entry(val info: LibInfo?, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val refreshPending = AtomicBoolean(false)

    /** Fresh cached info, or null if missing/expired (never triggers a fetch). */
    fun getCached(provider: String?, name: String): LibInfo? =
        cache[key(provider, name)]?.takeIf { fresh(it) }?.info

    /**
     * Cached-if-fresh, else fetch now (blocking). Off-EDT only. Does NOT trigger a UI refresh -
     * the caller (the annotator) renders this pass's result and calls [requestRefresh] once at the
     * end if anything was freshly fetched, avoiding a restart per library.
     */
    fun getOrFetch(provider: String?, name: String): LibInfo? {
        val k = key(provider, name)
        cache[k]?.let { if (fresh(it)) return it.info }
        val info = ProviderCatalog.fetch(provider, name)
        cache[k] = Entry(info, System.currentTimeMillis())
        return info
    }

    /** Drop the cached entry and re-fetch on a background thread (the manual "Check for updates"). */
    fun refreshInBackground(provider: String?, name: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Checking $name for updates", true) {
            override fun run(indicator: ProgressIndicator) {
                cache.remove(key(provider, name))
                getOrFetch(provider, name)
                requestRefresh()
            }
        })
    }

    /** Debounced daemon restart so a freshly filled cache re-renders the highlight and inlays. */
    fun requestRefresh() {
        if (!refreshPending.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater({
            refreshPending.set(false)
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart()
        }, ModalityState.any())
    }

    private fun fresh(e: Entry): Boolean = System.currentTimeMillis() - e.at < TTL_MS

    private fun key(provider: String?, name: String): String =
        "${provider?.trim()?.lowercase().orEmpty()}::$name"

    companion object {
        private const val TTL_MS = 60 * 60 * 1000L // 1 hour

        fun getInstance(project: Project): LibmanCatalogService = project.service()
    }
}
