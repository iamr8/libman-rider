package com.github.iamr8.libman.ui

import com.github.iamr8.libman.model.ManifestRules
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

/**
 * Structural checks on `libman.json` (no network): the root schema `version` and provider names.
 * Warns on an unknown provider (`defaultProvider` or a library's `provider`) and on an unknown
 * schema version; informs when the schema `version` is older than the latest. Rules live in
 * [ManifestRules] (pure, unit-tested); this class only maps them onto PSI ranges.
 */
class ManifestValidationAnnotator : Annotator, DumbAware {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is JsonProperty) return
        val file = element.containingFile
        if (file !is JsonFile || !ManifestPsi.isManifest(file)) return
        val value = element.value as? JsonStringLiteral ?: return
        val root = file.topLevelValue as? JsonObject
        val atRoot = element.parent == root

        when (element.name) {
            "version" -> if (atRoot) {
                ManifestRules.versionProblem(value.value)?.let { (level, msg) ->
                    val severity = if (level == ManifestRules.Level.INFO) {
                        HighlightSeverity.WEAK_WARNING
                    } else {
                        HighlightSeverity.WARNING
                    }
                    holder.newAnnotation(severity, msg).range(value).create()
                }
            }
            "defaultProvider" -> if (atRoot) annotateProvider(value, holder)
            "provider" -> annotateProvider(value, holder) // only appears inside a library object
        }
    }

    private fun annotateProvider(value: JsonStringLiteral, holder: AnnotationHolder) {
        ManifestRules.providerProblem(value.value)?.let { msg ->
            holder.newAnnotation(HighlightSeverity.WARNING, msg).range(value).create()
        }
    }
}
