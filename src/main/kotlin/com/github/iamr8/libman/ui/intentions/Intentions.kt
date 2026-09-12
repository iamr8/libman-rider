package com.github.iamr8.libman.ui.intentions

import com.github.iamr8.libman.ui.LibmanOps
import com.github.iamr8.libman.ui.LibraryEntryContext
import com.intellij.openapi.project.Project

/** "Check for updates" — shows the latest version and offers to apply it. */
class CheckForUpdatesIntention : LibraryEntryIntention() {
    override fun getText(): String = "Check for updates (LibMan)"
    override fun act(project: Project, ctx: LibraryEntryContext) =
        LibmanOps.checkForUpdates(project, ctx.manifestDir, ctx.id.name, pre = false)
}

/** "Update to latest version". */
class UpdateToLatestIntention : LibraryEntryIntention() {
    override fun getText(): String = "Update to latest version (LibMan)"
    override fun act(project: Project, ctx: LibraryEntryContext) =
        LibmanOps.update(project, ctx.manifestDir, ctx.id.name, pre = false)
}

/** "Update to latest prerelease". */
class UpdateToPrereleaseIntention : LibraryEntryIntention() {
    override fun getText(): String = "Update to latest prerelease (LibMan)"
    override fun act(project: Project, ctx: LibraryEntryContext) =
        LibmanOps.update(project, ctx.manifestDir, ctx.id.name, pre = true)
}

/** "Uninstall" — removes the library's files and its manifest entry. Valid for any provider. */
class UninstallLibraryIntention : LibraryEntryIntention() {
    override val filesystemApplicable: Boolean = true
    override fun getText(): String = "Uninstall this library (LibMan)"
    override fun act(project: Project, ctx: LibraryEntryContext) =
        LibmanOps.uninstall(project, ctx.manifestDir, ctx.id.name)
}
