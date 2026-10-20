package com.victorious.browser.export

import com.victorious.browser.scanner.Finding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory hand-off between result screens (scanner / recon) and the Export
 * screen: a screen stages the findings/targets it wants to push, then navigates
 * to Export. Captured endpoints are read live from CaptureStore by the Export
 * screen, so only findings/targets need staging here.
 */
object FleetExportStaging {
    private val _findings = MutableStateFlow<List<Finding>>(emptyList())
    val findings: StateFlow<List<Finding>> = _findings.asStateFlow()

    private val _targets = MutableStateFlow<List<String>>(emptyList())
    val targets: StateFlow<List<String>> = _targets.asStateFlow()

    fun stage(findings: List<Finding> = emptyList(), targets: List<String> = emptyList()) {
        if (findings.isNotEmpty()) _findings.value = findings
        if (targets.isNotEmpty()) _targets.value = targets
    }

    fun clear() {
        _findings.value = emptyList()
        _targets.value = emptyList()
    }
}
