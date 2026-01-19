/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.monitoring

import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.management.ManagementFactory

/**
 * Monitors system resources (CPU and Memory usage) for the application.
 */
class SystemResourceMonitor {
    private val _memoryUsageMB = MutableStateFlow(0L)
    val memoryUsageMB: StateFlow<Long> = _memoryUsageMB.asStateFlow()

    private val _cpuUsagePercent = MutableStateFlow(0.0)
    val cpuUsagePercent: StateFlow<Double> = _cpuUsagePercent.asStateFlow()

    private val runtime = Runtime.getRuntime()
    private val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean

    /**
     * Updates the current memory usage in megabytes.
     */
    fun updateMemoryUsage() {
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        _memoryUsageMB.value = usedMemory
    }

    /**
     * Updates the current CPU usage percentage.
     * Returns process CPU load if available, otherwise returns 0.0.
     */
    fun updateCpuUsage() {
        val cpuLoad = osBean?.processCpuLoad ?: 0.0
        // Convert to percentage and ensure it's a valid number
        val cpuPercent = if (cpuLoad >= 0.0) cpuLoad * 100.0 else 0.0
        _cpuUsagePercent.value = cpuPercent
    }

    /**
     * Updates both memory and CPU usage.
     */
    fun updateAll() {
        updateMemoryUsage()
        updateCpuUsage()
    }

    /**
     * Starts monitoring resources at the specified interval.
     */
    suspend fun startMonitoring(intervalMillis: Long = 2000) {
        while (true) {
            updateAll()
            delay(intervalMillis)
        }
    }
}
