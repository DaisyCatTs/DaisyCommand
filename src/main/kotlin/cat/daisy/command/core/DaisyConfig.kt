@file:Suppress("unused")

package cat.daisy.command.core

import java.time.Duration

class DaisyTheme {
    var commandColor: String = "gold"
    var descriptionColor: String = "gray"
    var accentColor: String = "white"
    var usageColor: String = "gray"
    var errorColor: String = "red"
    var warningColor: String = "yellow"
}

data class DaisyHelpEntryRenderContext(
    val parentPath: String,
    val childPath: String,
    val description: String,
)

data class DaisyArgumentErrorRenderContext(
    val usage: String,
    val message: String,
)

data class DaisyCooldownRenderContext(
    val commandPath: String,
    val remaining: Duration,
    val formattedRemaining: String,
)

fun interface DaisyHelpEntryRenderer {
    fun render(
        context: DaisyHelpEntryRenderContext,
        theme: DaisyTheme,
    ): String
}

fun interface DaisyArgumentErrorRenderer {
    fun render(
        context: DaisyArgumentErrorRenderContext,
        theme: DaisyTheme,
    ): String
}

fun interface DaisyCooldownRenderer {
    fun render(
        context: DaisyCooldownRenderContext,
        theme: DaisyTheme,
    ): String
}

class DaisyMessages {
    var prefix: String = "<gray>[<yellow>Daisy</yellow>]</gray> "
    var noPermission: String = "<red>You do not have permission to use this command."
    var playerOnly: String = "<red>This command can only be used by a player."
    var consoleOnly: String = "<red>This command can only be used from the console."
    var invalidState: String = "<red>You cannot use this command right now."
    var unknownSubcommand: String = "<red>Unknown subcommand <white>{input}</white>."
    var invalidArgument: String = "<red>{message}"
    var exception: String = "<red>An internal error occurred while executing this command."
    var usageLabel: String = "<gray>Usage: <white>{usage}"
    var helpFooter: String? = null
    var helpEntryRenderer: DaisyHelpEntryRenderer? = null
    var argumentErrorRenderer: DaisyArgumentErrorRenderer? = null
    var cooldownRenderer: DaisyCooldownRenderer? = null
}

data class DaisyConfig(
    val messages: DaisyMessages = DaisyMessages(),
    val theme: DaisyTheme = DaisyTheme(),
)

class DaisyConfigBuilder {
    private val messages = DaisyMessages()
    private val theme = DaisyTheme()

    fun messages(block: DaisyMessages.() -> Unit) {
        messages.apply(block)
    }

    fun theme(block: DaisyTheme.() -> Unit) {
        theme.apply(block)
    }

    internal fun build(): DaisyConfig = DaisyConfig(messages = messages, theme = theme)
}
