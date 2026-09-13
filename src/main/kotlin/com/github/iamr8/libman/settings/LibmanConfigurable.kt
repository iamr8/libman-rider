package com.github.iamr8.libman.settings

import com.github.iamr8.libman.cli.DotnetTool
import com.github.iamr8.libman.cli.LibmanLocator
import com.github.iamr8.libman.ui.LibmanInstall
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.io.File

/** Settings | Tools | LibMan. Persists to the application-level [LibmanSettings]. */
class LibmanConfigurable : BoundSearchableConfigurable("LibMan", "com.github.iamr8.libman.settings") {

    private val settings = LibmanSettings.getInstance()
    private val work: LibmanSettings.State = settings.getState().copy()

    override fun isModified(): Boolean = super.isModified() || work != settings.getState()

    override fun apply() {
        super.apply() // write UI -> work
        work.customLibmanPath = work.customLibmanPath.trim()
        work.cacheTtlMinutes = work.cacheTtlMinutes.coerceIn(1, 1440)
        settings.loadState(work.copy())
    }

    override fun reset() {
        work.assignFrom(settings.getState()) // in place: keeps bindings valid
        super.reset()
    }

    override fun createPanel(): DialogPanel = panel {
        group("Updates") {
            row { checkBox("Include pre-release versions").bindSelected(work::includePrereleases) }
            row { checkBox("Check for updates when libman.json opens").bindSelected(work::checkOnOpen) }
            row("Cache expiry (minutes):") {
                textField()
                    .bindText(
                        { work.cacheTtlMinutes.toString() },
                        { work.cacheTtlMinutes = it.trim().toIntOrNull()?.coerceIn(1, 1440) ?: 60 },
                    )
                    .columns(6)
                    .comment("How long provider version lookups are cached. Check for updates forces a refresh.")
            }
        }
        group("LibMan CLI") {
            row("Executable path:") {
                textField()
                    .bindText(work::customLibmanPath)
                    .columns(40)
                    .comment("Leave empty to auto-detect on PATH and in ~/.dotnet/tools.")
                    .validationOnApply { field ->
                        val path = field.text.trim()
                        if (path.isNotEmpty() && !File(path).canExecute()) {
                            error("Not an executable file. Leave empty to auto-detect.")
                        } else {
                            null
                        }
                    }
            }
            row("Output verbosity:") {
                comboBox(LibmanVerbosity.entries)
                    .bindItem({ work.verbosity }, { work.verbosity = it ?: LibmanVerbosity.NORMAL })
            }
            row {
                val cell = button("Install LibMan CLI") {}
                val btn = cell.component
                // Enabled only when libman is not already found (custom path, PATH, or ~/.dotnet/tools);
                // a global install needs no project, so nothing to choose.
                fun refresh() { btn.isEnabled = LibmanLocator.resolveExisting(work.customLibmanPath.trim()) == null }
                refresh()
                btn.addActionListener {
                    btn.isEnabled = false
                    LibmanInstall.installCli(null) { refresh() }
                }
                cell.comment(
                    "Installs the global .NET tool: <code>dotnet tool install -g ${DotnetTool.LIBMAN_CLI_PACKAGE}</code>. " +
                        "Disabled when libman is already found.",
                )
            }
        }
    }
}
