package com.github.iamr8.libman.ui.intentions

import com.github.iamr8.libman.ui.LibmanOps
import com.github.iamr8.libman.ui.LibraryEntryContext
import com.intellij.openapi.project.Project

/**
 * "Uninstall" - removes the library's files and its manifest entry. Valid for any provider.
 * (Update/check are handled inline by the version chips and the update annotator.)
 */
class UninstallLibraryIntention : LibraryEntryIntention() {
    override val filesystemApplicable: Boolean = true
    override fun getText(): String = "Uninstall this library (LibMan)"
    override fun act(project: Project, ctx: LibraryEntryContext) =
        LibmanOps.uninstall(project, ctx.manifestDir, ctx.id.name)
}
