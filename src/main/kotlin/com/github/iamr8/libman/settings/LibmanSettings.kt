package com.github.iamr8.libman.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** Application-level LibMan settings, editable in Settings | Tools | LibMan. */
@Service(Service.Level.APP)
@State(name = "LibmanGuiSettings", storages = [Storage("libman-gui.xml")])
class LibmanSettings : PersistentStateComponent<LibmanSettings.State> {

    data class State(
        var includePrereleases: Boolean = true,
        var checkOnOpen: Boolean = true,
        var cacheTtlMinutes: Int = 60,
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    var includePrereleases: Boolean
        get() = state.includePrereleases
        set(v) { state.includePrereleases = v }

    var checkOnOpen: Boolean
        get() = state.checkOnOpen
        set(v) { state.checkOnOpen = v }

    /** Cache lifetime in minutes; clamped to at least 1. */
    var cacheTtlMinutes: Int
        get() = state.cacheTtlMinutes.coerceAtLeast(1)
        set(v) { state.cacheTtlMinutes = v.coerceAtLeast(1) }

    companion object {
        fun getInstance(): LibmanSettings = service()
    }
}
