@file:Suppress("unused")

package cat.daisy.command.cooldown

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DaisyCommand Cooldown Manager
 *
 * Thread-safe cooldown system with support for:
 * - Per-command cooldowns
 * - Per-player cooldowns
 * - Global cooldowns
 * - Cooldown bypass permissions
 * - Remaining time queries
 */
object DaisyCooldowns {
    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val globalCooldowns = ConcurrentHashMap<String, Long>()

    // ═══════════════════════════════════════════════════════════════════════════════
    // PLAYER COOLDOWNS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Get remaining cooldown time in seconds.
     * Returns 0 if not on cooldown.
     * Automatically sets the cooldown if not on cooldown.
     */
    fun getRemainingCooldown(
        player: Player,
        command: String,
        cooldownSeconds: Int,
    ): Long {
        val playerCooldowns = cooldowns.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
        val lastUsage = playerCooldowns[command] ?: 0L
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds.toLong())

        return if (currentTime - lastUsage < cooldownMillis) {
            (lastUsage + cooldownMillis - currentTime) / 1000
        } else {
            playerCooldowns[command] = currentTime
            0
        }
    }

    /**
     * Check if player is on cooldown without setting a new cooldown.
     */
    fun checkCooldown(
        player: Player,
        command: String,
        cooldownSeconds: Int,
    ): Long {
        val playerCooldowns = cooldowns[player.uniqueId] ?: return 0L
        val lastUsage = playerCooldowns[command] ?: return 0L
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds.toLong())

        return if (currentTime - lastUsage < cooldownMillis) {
            (lastUsage + cooldownMillis - currentTime) / 1000
        } else {
            0
        }
    }

    /**
     * Check if player is on cooldown.
     * @param bypassPermission Optional permission to bypass cooldown
     * @return true if on cooldown and should be blocked
     */
    fun isOnCooldown(
        player: Player,
        command: String,
        cooldownSeconds: Int,
        bypassPermission: String? = null,
    ): Boolean {
        if (bypassPermission != null && player.hasPermission(bypassPermission)) return false
        return getRemainingCooldown(player, command, cooldownSeconds) > 0
    }

    /**
     * Set a cooldown manually for a player.
     */
    fun setCooldown(
        player: Player,
        command: String,
    ) {
        val playerCooldowns = cooldowns.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
        playerCooldowns[command] = System.currentTimeMillis()
    }

    /**
     * Reset a specific cooldown for a player.
     */
    fun resetCooldown(
        uuid: UUID,
        command: String,
    ) {
        cooldowns[uuid]?.remove(command)
    }

    /**
     * Clear all cooldowns for a player.
     */
    fun clearCooldowns(uuid: UUID) {
        cooldowns.remove(uuid)
    }

    /**
     * Clear all player cooldowns.
     */
    fun clearAll() {
        cooldowns.clear()
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // GLOBAL COOLDOWNS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Check global cooldown (shared across all players).
     */
    fun getGlobalRemainingCooldown(
        key: String,
        cooldownSeconds: Int,
    ): Long {
        val lastUsage = globalCooldowns[key] ?: 0L
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds.toLong())

        return if (currentTime - lastUsage < cooldownMillis) {
            (lastUsage + cooldownMillis - currentTime) / 1000
        } else {
            globalCooldowns[key] = currentTime
            0
        }
    }

    /**
     * Check if a global cooldown is active.
     */
    fun isGlobalCooldown(
        key: String,
        cooldownSeconds: Int,
    ): Boolean = getGlobalRemainingCooldown(key, cooldownSeconds) > 0

    /**
     * Reset a global cooldown.
     */
    fun resetGlobalCooldown(key: String) {
        globalCooldowns.remove(key)
    }

    /**
     * Clear all global cooldowns.
     */
    fun clearGlobalCooldowns() {
        globalCooldowns.clear()
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // FORMATTING UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Format remaining seconds to human-readable string.
     */
    fun formatCooldown(seconds: Long): String =
        when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
}
