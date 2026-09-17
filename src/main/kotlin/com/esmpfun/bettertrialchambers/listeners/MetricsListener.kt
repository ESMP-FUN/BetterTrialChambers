package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.api.events.ChamberClearedEvent
import com.esmpfun.bettertrialchambers.api.events.ChamberResetCompleteEvent
import com.esmpfun.bettertrialchambers.api.events.VaultOpenedEvent
import com.esmpfun.bettertrialchambers.integrations.MetricsService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Feeds the three activity counters in [MetricsService] from BTC's own public
 * events. Registered only when `metrics.enabled` is true. MONITOR priority so a
 * count reflects work that actually happened. Handlers only touch an
 * `AtomicLong`, so the async [VaultOpenedEvent] is safe here.
 */
class MetricsListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onReset(event: ChamberResetCompleteEvent) = MetricsService.recordReset()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onVault(event: VaultOpenedEvent) = MetricsService.recordVaultOpened()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onCleared(event: ChamberClearedEvent) = MetricsService.recordChamberCleared()
}
