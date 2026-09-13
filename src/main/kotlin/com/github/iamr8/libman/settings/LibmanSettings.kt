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
        // Empty = auto-detect libman on PATH / in ~/.dotnet/tools. Set to override the executable.
        var customLibmanPath: String = "",
        var verbosity: LibmanVerbosity = LibmanVerbosity.NORMAL,
    ) {
        /** Copy every field from [other] in place (keeps UI-DSL bindings on this instance valid). */
        fun assignFrom(other: State) {
            includePrereleases = other.includePrereleases
            checkOnOpen = other.checkOnOpen
            cacheTtlMinutes = other.cacheTtlMinutes
            customLibmanPath = other.customLibmanPath
            verbosity = other.verbosity
        }
    }

    private var state = State()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    var includePrereleases: Boolean
        get() = state.includePrereleases
        set(v) { state.includePrereleases = v }

    var checkOnOpen: Boolean
        get() = state.checkOnOpen
        set(v) { state.checkOnOpen = v }

    /** Cache lifetime in minutes; clamped to the settings range (1..1440). */
    var cacheTtlMinutes: Int
        get() = state.cacheTtlMinutes.coerceIn(1, 1440)
        set(v) { state.cacheTtlMinutes = v.coerceIn(1, 1440) }

    /** Custom `libman` executable path, or empty to auto-detect. */
    var customLibmanPath: String
        get() = state.customLibmanPath.trim()
        set(v) { state.customLibmanPath = v.trim() }

    var verbosity: LibmanVerbosity
        get() = state.verbosity
        set(v) { state.verbosity = v }

    /** The `--verbosity` argument to pass to the CLI, or null for the default (no flag). */
    val verbosityArg: String? get() = state.verbosity.arg

    companion object {
        fun getInstance(): LibmanSettings = service()
    }
}
