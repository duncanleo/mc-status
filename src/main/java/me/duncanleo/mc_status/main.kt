package me.duncanleo.mc_status

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.squareup.moshi.Moshi
import me.duncanleo.mc_status.model.discord.DiscordWebhookPayload
import me.duncanleo.mc_status.model.discord.Embed
import me.duncanleo.mc_status.model.discord.EmbedField
import me.duncanleo.mc_status.util.TPSUtil
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.DisplaySlot
import java.util.*

const val TIME_NIGHT = 13000
const val CONFIG_KEY_WEBHOOK_URL = "webhook_url"

class App : JavaPlugin(), Listener {
  val playerLocationMap = mutableMapOf<UUID, Location>()

  override fun onEnable() {
    logger.info("Hello there!")

    server.pluginManager.registerEvents(this, this)
    TPSUtil.registerTask(this)

    saveDefaultConfig()

    object : BukkitRunnable() {
      override fun run() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
          return
        }

        val scoreboardManager = Bukkit.getScoreboardManager()
        val tps = TPSUtil.tps

        val freeMemory = Runtime.getRuntime().freeMemory()
        val totalMemory = Runtime.getRuntime().maxMemory()

        Bukkit.getOnlinePlayers().forEach {
          val scoreboard = scoreboardManager?.newScoreboard

          var objective = scoreboard?.getObjective("Status")
          if (objective == null) {
            objective = scoreboard?.registerNewObjective("Status", "dummy", "Status")
          }
          objective?.displaySlot = DisplaySlot.SIDEBAR

          // TPS score
          val tpsScore = objective?.getScore("${ChatColor.DARK_AQUA}TPS")
          tpsScore?.score = tps.toInt()

          // RAM usage score
          val ramUsageScore = objective?.getScore("${ChatColor.DARK_AQUA}RAM Usage (%)")
          ramUsageScore?.score = ((freeMemory.toDouble() / totalMemory.toDouble()) * 100.0).toInt()

          // Time to day/night
          val isDay = it.world.time < TIME_NIGHT
          val entryToSet = "${ChatColor.GREEN}Time to ${if (isDay) "Night" else "Day"} (s)"
          val entryToRemove = "${ChatColor.GREEN}Time to ${if (isDay) "Day" else "Night"} (s)"
          val duration = if (isDay) TIME_NIGHT - it.world.time else 24000 - it.world.time + 1000L
          scoreboard?.resetScores(entryToRemove)

          val timeScore = objective?.getScore(entryToSet)
          timeScore?.score = (duration / 20L).toInt()

          // Ping score
          val pingScore = objective?.getScore("${ChatColor.AQUA}Ping (ms)")
          pingScore?.score = it.ping

          // SCOREBOARD!
          if (scoreboard != null) {
            it.scoreboard = scoreboard
          }

          // Check location
          if (playerLocationMap[it.uniqueId]?.block?.biome != it.location.block.biome) {
            it.sendMessage("${ChatColor.DARK_AQUA}Welcome to the ${ChatColor.AQUA}${it.location.block.biome.name.capitalizeBukkitEnumName()} ${ChatColor.DARK_AQUA}")
          }
          playerLocationMap[it.uniqueId] = it.location
        }
      }
    }.runTaskTimer(this, 20 * 3, 20 * 5)
  }

  @EventHandler
  fun playerJoined(event: PlayerJoinEvent) {
    if (config.get(CONFIG_KEY_WEBHOOK_URL) == null) {
      return
    }

    val payload = DiscordWebhookPayload(
            username = "Minecraft",
            embeds = arrayOf(
                    Embed(
                            title = "A player joined the server",
                            description = ChatColor.stripColor(event.player.displayName) ?: event.player.displayName,
                            fields = arrayOf(
                                    EmbedField(
                                            name = "Current no. of players",
                                            value = Bukkit.getOnlinePlayers().size.toString()
                                    )
                            )
                    )
            )
    )

    object : BukkitRunnable() {
      override fun run() {
        publishDiscordWebhook(webhookURL = config.get(CONFIG_KEY_WEBHOOK_URL) as String, payload = payload)
      }
    }.run()
  }

  @EventHandler
  fun playerLeft(event: PlayerQuitEvent) {
    playerLocationMap.remove(event.player.uniqueId)
    if (config.get(CONFIG_KEY_WEBHOOK_URL) == null) {
      return
    }

    val payload = DiscordWebhookPayload(
            username = "Minecraft",
            embeds = arrayOf(
                    Embed(
                            title = "A player left the server",
                            description = ChatColor.stripColor(event.player.displayName) ?: event.player.displayName,
                            fields = arrayOf(
                                    EmbedField(
                                            name = "Current no. of players",
                                            value = (Bukkit.getOnlinePlayers().size - 1).toString()
                                    )
                            )
                    )
            )
    )

    object : BukkitRunnable() {
      override fun run() {
        publishDiscordWebhook(webhookURL = config.get(CONFIG_KEY_WEBHOOK_URL) as String, payload = payload)
      }
    }.run()
  }

  @EventHandler
  fun entityTarget(event: EntityTargetEvent) {
    if (event.target !is Player) {
      return
    }

    val player = event.target as Player

    when (event.entity) {
      is ExperienceOrb -> {
        return
      }
    }

    when (event.reason) {
      EntityTargetEvent.TargetReason.TARGET_DIED,
      EntityTargetEvent.TargetReason.TEMPT -> {
        return
      }
      EntityTargetEvent.TargetReason.FORGOT_TARGET -> {
        player.sendMessage("${ChatColor.DARK_AQUA}You are no longer being targeted by a ${ChatColor.AQUA}${event.entity.type.name.capitalizeBukkitEnumName()}.")
      }
      else -> {
        player.sendMessage("${ChatColor.DARK_AQUA}You are being ${ChatColor.RED}targeted ${ChatColor.DARK_AQUA}by a ${ChatColor.AQUA}${event.entity.type.name.capitalizeBukkitEnumName()}. ${ChatColor.DARK_AQUA}Reason: ${ChatColor.AQUA}${event.reason.name.capitalizeBukkitEnumName()}")
      }
    }
  }

  @EventHandler
  fun breedEntity(event: EntityBreedEvent) {
    if (event.breeder !is Player) {
      return
    }
    val player = event.breeder as Player
    Bukkit.broadcastMessage("${ChatColor.GREEN}${player.displayName} ${ChatColor.DARK_GREEN}just bred a ${ChatColor.GREEN}${event.entityType.name.capitalizeBukkitEnumName()} ${ChatColor.DARK_GREEN}and earned ${ChatColor.GREEN}${event.experience} exp")
  }
  private fun publishDiscordWebhook(webhookURL: String, payload: DiscordWebhookPayload) {
    val moshi = Moshi.Builder().build()
    val payloadEncoded = moshi
            .adapter(DiscordWebhookPayload::class.java)
            .toJson(payload)

    webhookURL
            .httpPost()
            .jsonBody(payloadEncoded)
            .header("User-Agent", "MCStatus ${description.version}")
            .also {
              logger.info(it.toString())
            }
            .response { result ->
              logger.info(result.toString())
            }
  }

  /**
   * Get a player's ping
   */
  val Player.ping: Int
    get() {
      try {
        val entityPlayer = this.javaClass.getMethod("getHandle").invoke(this)
        val ping = entityPlayer.javaClass.getField("ping").get(entityPlayer) as? Int

        if (ping != null) {
          return ping
        }
      } catch (e: Exception) {
        logger.warning(e.message)
      }

      return -1
    }

  private fun String.capitalizeBukkitEnumName(): String {
    return this.toLowerCase().replace("_", " ").capitalize()
  }
}
