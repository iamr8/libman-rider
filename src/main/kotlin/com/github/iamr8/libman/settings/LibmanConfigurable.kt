package com.github.iamr8.libman.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.SpinnerNumberModel
import javax.swing.JSpinner

/** Settings | Tools | LibMan. */
class LibmanConfigurable : Configurable {

    private val includePrereleases = JBCheckBox("Include pre-release versions")
    private val checkOnOpen = JBCheckBox("Check for updates when libman.json opens")
    private val cacheTtl = JSpinner(SpinnerNumberModel(60, 1, 1440, 5))

    private val settings get() = LibmanSettings.getInstance()

    override fun getDisplayName(): String = "LibMan"

    override fun createComponent(): JComponent {
        reset()
        return FormBuilder.createFormBuilder()
            .addComponent(includePrereleases)
            .addComponent(checkOnOpen)
            .addLabeledComponent(JBLabel("Cache expiry (minutes):"), cacheTtl)
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(10) }
    }

    override fun isModified(): Boolean =
        includePrereleases.isSelected != settings.includePrereleases ||
            checkOnOpen.isSelected != settings.checkOnOpen ||
            (cacheTtl.value as Int) != settings.cacheTtlMinutes

    override fun apply() {
        settings.includePrereleases = includePrereleases.isSelected
        settings.checkOnOpen = checkOnOpen.isSelected
        settings.cacheTtlMinutes = cacheTtl.value as Int
    }

    override fun reset() {
        includePrereleases.isSelected = settings.includePrereleases
        checkOnOpen.isSelected = settings.checkOnOpen
        cacheTtl.value = settings.cacheTtlMinutes
    }
}
