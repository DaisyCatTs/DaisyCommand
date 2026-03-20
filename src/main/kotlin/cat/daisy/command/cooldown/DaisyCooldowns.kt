@file:Suppress("unused")

package cat.daisy.command.cooldown

import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DaisyCooldowns {
    private val playerCooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Instant>>()
    private val globalCooldowns = ConcurrentHashMap<String, Instant>()

    fun remaining(
        player: Player,
        key: String,
        duration: Duration,
    ): Duration = remaining(player.uniqueId, key, duration)

    fun remaining(
        playerId: UUID,
        key: String,
        duration: Duration,
    ): Duration {
        val usedAt = playerCooldowns[playerId]?.get(key) ?: return Duration.ZERO
        val remaining = Duration.between(Instant.now(), usedAt.plus(duration))
        return remaining.takeIf { !it.isNegative && !it.isZero } ?: Duration.ZERO
    }

    fun set(
        player: Player,
        key: String,
    ) {
        set(player.uniqueId, key)
    }

    fun set(
        playerId: UUID,
        key: String,
    ) {
        playerCooldowns.computeIfAbsent(playerId) { ConcurrentHashMap() }[key] = Instant.now()
    }

    fun clear(playerId: UUID) {
        playerCooldowns.remove(playerId)
    }

    fun remainingGlobal(
        key: String,
        duration: Duration,
    ): Duration {
        val usedAt = globalCooldowns[key] ?: return Duration.ZERO
        val remaining = Duration.between(Instant.now(), usedAt.plus(duration))
        return remaining.takeIf { !it.isNegative && !it.isZero } ?: Duration.ZERO
    }

    fun setGlobal(key: String) {
        globalCooldowns[key] = Instant.now()
    }

    fun clearAll() {
        playerCooldowns.clear()
        globalCooldowns.clear()
    }

    fun format(duration: Duration): String {
        val totalSeconds = duration.seconds
        return when {
            totalSeconds < 60 -> "${totalSeconds}s"
            totalSeconds < 3600 -> "${totalSeconds / 60}m ${totalSeconds % 60}s"
            else -> "${totalSeconds / 3600}h ${(totalSeconds % 3600) / 60}m"
        }
    }
}
