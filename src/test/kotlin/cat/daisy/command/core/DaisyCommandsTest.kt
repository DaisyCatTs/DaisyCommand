package cat.daisy.command.core

import cat.daisy.command.arguments.DaisyParser
import cat.daisy.command.arguments.DaisyPlatform
import cat.daisy.command.arguments.ParseContext
import cat.daisy.command.arguments.ParseResult
import cat.daisy.command.arguments.SuggestContext
import cat.daisy.command.arguments.optional
import cat.daisy.command.cooldown.DaisyCooldowns
import cat.daisy.command.dsl.command
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.UUID
import java.util.logging.Logger

class DaisyCommandsTest {
    private val plainSerializer = PlainTextComponentSerializer.plainText()

    @AfterEach
    fun tearDown() {
        DaisyCooldowns.clearAll()
        messageStore.clear()
    }

    @Test
    fun `typed arguments are resolved through argument refs`() {
        val alice = player("Alice")
        val bob = player("Bob")
        val runtime = runtime(players = listOf(alice, bob))

        var inviterName = ""
        var targetName = ""

        val spec =
            command("island") {
                sub("invite") {
                    val target = player("target")

                    executePlayer {
                        inviterName = player.name
                        targetName = target().name
                    }
                }
            }

        spec.compiled.execute(alice, "island", listOf("invite", "Bob"), runtime)

        assertEquals("Alice", inviterName)
        assertEquals("Bob", targetName)
    }

    @Test
    fun `help output hides subcommands without permission`() {
        val sender = sender("Console")
        val runtime = runtime()

        val spec =
            command("team") {
                sub("open") {
                    description("Open to everyone")
                    execute { }
                }

                sub("secret") {
                    description("Hidden command")
                    permission("team.secret")
                    execute { }
                }
            }

        spec.compiled.execute(sender, "team", emptyList(), runtime)

        val messages = sentMessages(sender)
        assertTrue(messages.any { it.contains("/team open") })
        assertFalse(messages.any { it.contains("/team secret") })
    }

    @Test
    fun `cooldown only applies after successful execution`() {
        val player = player("Alice")
        val runtime = runtime(players = listOf(player))

        var failedExecutions = 0
        val failing =
            command("heal") {
                cooldown(java.time.Duration.ofSeconds(30))
                executePlayer {
                    failedExecutions++
                    error("Nope")
                }
            }

        failing.compiled.execute(player, "heal", emptyList(), runtime)
        failing.compiled.execute(player, "heal", emptyList(), runtime)
        assertEquals(2, failedExecutions)

        var successfulExecutions = 0
        val successful =
            command("warp") {
                cooldown(java.time.Duration.ofSeconds(30))
                executePlayer {
                    successfulExecutions++
                }
            }

        successful.compiled.execute(player, "warp", emptyList(), runtime)
        successful.compiled.execute(player, "warp", emptyList(), runtime)
        assertEquals(1, successfulExecutions)
        assertTrue(sentMessages(player).any { it.contains("wait", ignoreCase = true) })
    }

    @Test
    fun `custom parser suggestions can see previous parsed arguments`() {
        val player = player("Alice")
        val runtime = runtime(players = listOf(player))

        val dynamicParser =
            object : DaisyParser<String> {
                override val displayName: String = "name"

                override fun parse(
                    input: String,
                    context: ParseContext,
                ): ParseResult<String> = ParseResult.success(input)

                override fun suggest(context: SuggestContext): List<String> =
                    when (context.previousArguments["scope"]) {
                        "public" -> listOf("spawn", "market")
                        "private" -> listOf("vault", "chest")
                        else -> emptyList()
                    }
            }

        val spec =
            command("warp") {
                sub("set") {
                    choice("scope", "public", "private")
                    argument("name", dynamicParser)
                    executePlayer { }
                }
            }

        val suggestions = spec.compiled.suggest(player, listOf("set", "public", ""), runtime)
        assertEquals(listOf("spawn", "market"), suggestions)
    }

    @Test
    fun `paper adapter delegates execution and suggestions`() {
        val alice = player("Alice")
        val bob = player("Bob")
        val runtime = runtime(players = listOf(alice, bob))

        var targetName = ""
        val spec =
            command("island") {
                sub("invite") {
                    val target = player("target")
                    executePlayer {
                        targetName = target().name
                    }
                }
            }

        val adapter = PaperCommandAdapter(spec.compiled, runtime)
        val stack = mock(io.papermc.paper.command.brigadier.CommandSourceStack::class.java)
        `when`(stack.sender).thenReturn(alice)

        adapter.execute(stack, arrayOf("invite", "Bob"))
        val suggestions = adapter.suggest(stack, arrayOf("inv"))

        assertEquals("Bob", targetName)
        assertTrue(suggestions.contains("invite"))
    }

    @Test
    fun `invalid command structures fail fast at compile time`() {
        val duplicateAlias =
            command("root") {
                sub("create") {
                    execute { }
                }
                sub("make") {
                    aliases("create")
                    execute { }
                }
            }

        assertThrows<IllegalArgumentException> {
            duplicateAlias.compiled
        }

        val invalidArgs =
            command("bad") {
                text("message").optional()
                string("required")
                execute { }
            }

        assertThrows<IllegalStateException> {
            invalidArgs.compiled
        }
    }

    @Test
    fun `readme style commands compile`() {
        val spec =
            command("island") {
                description("Island management")
                aliases("is")

                sub("create") {
                    playerOnly()
                    cooldown(java.time.Duration.ofSeconds(30))
                    executePlayer { }
                }

                sub("invite") {
                    permission("island.invite")
                    val target = player("target")
                    executePlayer {
                        target().name
                    }
                }
            }

        assertEquals("island", spec.compiled.name)
        assertEquals(listOf("is"), spec.aliases)
    }

    private fun runtime(
        players: List<Player> = emptyList(),
        worlds: List<World> = emptyList(),
        offlinePlayers: List<OfflinePlayer> = emptyList(),
    ): CommandRuntime =
        CommandRuntime(
            logger = Logger.getLogger("DaisyCommandsTest"),
            platform =
                object : DaisyPlatform {
                    private val playerMap = players.associateBy { it.name.lowercase() }
                    private val worldMap = worlds.associateBy { it.name.lowercase() }
                    private val offlineMap = offlinePlayers.associateBy { it.name!!.lowercase() }

                    override fun findPlayer(name: String): Player? = playerMap[name.lowercase()]

                    override fun onlinePlayers(): Collection<Player> = players

                    override fun findOfflinePlayer(name: String): OfflinePlayer? = offlineMap[name.lowercase()]

                    override fun findWorld(name: String): World? = worldMap[name.lowercase()]

                    override fun worlds(): Collection<World> = worlds
                },
        )

    private fun player(
        name: String,
        permissions: Set<String> = emptySet(),
    ): Player {
        val player = mock(Player::class.java)
        val messages = mutableListOf<String>()

        `when`(player.name).thenReturn(name)
        `when`(player.uniqueId).thenReturn(UUID.nameUUIDFromBytes(name.toByteArray()))
        `when`(player.hasPermission(any(String::class.java))).thenAnswer { invocation ->
            permissions.contains(invocation.getArgument(0))
        }

        doAnswer { invocation ->
            messages += plainSerializer.serialize(invocation.getArgument<Component>(0))
            null
        }.`when`(player).sendMessage(any(Component::class.java))

        player.setMetadataMessages(messages)
        return player
    }

    private fun sender(
        name: String,
        permissions: Set<String> = emptySet(),
    ): CommandSender {
        val sender = mock(CommandSender::class.java)
        val messages = mutableListOf<String>()

        `when`(sender.name).thenReturn(name)
        `when`(sender.hasPermission(any(String::class.java))).thenAnswer { invocation ->
            permissions.contains(invocation.getArgument(0))
        }
        doAnswer { invocation ->
            messages += plainSerializer.serialize(invocation.getArgument<Component>(0))
            null
        }.`when`(sender).sendMessage(any(Component::class.java))

        sender.setMetadataMessages(messages)
        return sender
    }

    private fun sentMessages(sender: Any): List<String> =
        when (sender) {
            is Player -> sender.metadataMessages()
            is CommandSender -> sender.metadataMessages()
            else -> emptyList()
        }

    private fun CommandSender.metadataMessages(): List<String> = messageStore[this] ?: emptyList()

    private fun CommandSender.setMetadataMessages(messages: MutableList<String>) {
        messageStore[this] = messages
    }

    private fun Player.metadataMessages(): List<String> = messageStore[this] ?: emptyList()

    private fun Player.setMetadataMessages(messages: MutableList<String>) {
        messageStore[this] = messages
    }

    companion object {
        private val messageStore = mutableMapOf<Any, MutableList<String>>()
    }
}
