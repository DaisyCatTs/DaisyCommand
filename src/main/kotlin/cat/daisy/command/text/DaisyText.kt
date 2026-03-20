package cat.daisy.command.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage

object DaisyText {
    private val miniMessage = MiniMessage.miniMessage()

    fun String.mm(): Component =
        miniMessage
            .deserialize(this)
            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
}
