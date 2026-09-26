package com.github.iamr8.libman.cli

import com.github.iamr8.libman.model.PendingChange

/**
 * The outcome of an applied queue, as one line per library: [applied] for an info notification,
 * [failed] for a warning, and [details] (the raw output of the failed ones) behind "Copy Details".
 *
 * Pure and platform-free for unit testing.
 */
data class PendingSummary(val applied: List<String>, val failed: List<String>, val details: String) {

    companion object {
        fun of(results: List<Pair<PendingChange, LibmanResult>>): PendingSummary {
            val applied = mutableListOf<String>()
            val failed = mutableListOf<String>()
            val details = StringBuilder()
            for ((change, r) in results) {
                val (ok, message) = describe(change, r)
                if (ok) {
                    applied += "${change.name}: $message"
                } else {
                    failed += "${change.name}: $message"
                    if (details.isNotEmpty()) details.append("\n\n")
                    details.append("${change.name}:\n").append(r.combinedOutput())
                }
            }
            return PendingSummary(applied, failed, details.toString())
        }

        // libman exits 0 even for a no-op or a missing library, so read the outcome from stdout.
        private fun describe(change: PendingChange, r: LibmanResult): Pair<Boolean, String> = when (change) {
            is PendingChange.Update -> if (!r.ok) {
                false to CliFailures.describe("update", r.exitCode, r.stdout, r.stderr, r.timedOut)
            } else when (val outcome = OpResultParser.parseUpdate(r.stdout)) {
                is UpdateOutcome.Updated -> true to "Updated to ${outcome.version}."
                UpdateOutcome.AlreadyLatest -> true to "Already up to date."
                UpdateOutcome.NotFound -> false to "No library named \"${change.name}\" in the manifest."
                UpdateOutcome.Unknown -> false to "Update result was unclear."
            }
            is PendingChange.Remove -> if (!r.ok) {
                false to CliFailures.describe("uninstall", r.exitCode, r.stdout, r.stderr, r.timedOut)
            } else when (OpResultParser.parseUninstall(r.stdout)) {
                UninstallOutcome.Uninstalled -> true to "Uninstalled."
                UninstallOutcome.NotInstalled -> false to "\"${change.name}\" is not installed."
                UninstallOutcome.Unknown -> false to "Uninstall result was unclear."
            }
        }
    }
}
