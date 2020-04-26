package me.duncanleo.mc_status

import me.duncanleo.mc_status.util.TPSUtil
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.DisplaySlot
import java.lang.Exception
import kotlin.math.abs

const val TIME_NIGHT = 13000

class App : JavaPlugin(), Listener {
  override fun onEnable() {
    logger.info("Hello there!")

    server.pluginManager.registerEvents(this, this)
    TPSUtil.registerTask(this)

    object: BukkitRunnable() {
      override fun run() {
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
          val tpsScore = objective?.getScore("${ChatColor.DARK_AQUA}Tick/sec")
          tpsScore?.score = tps.toInt()

          // Server Free RAM score
          val freeRAMScore = objective?.getScore("${ChatColor.DARK_AQUA}Free RAM (MB)")
          freeRAMScore?.score = (freeMemory / 1024L/ 1024L).toInt()

          // Server Total RAM score
          val totalRAMScore = objective?.getScore("${ChatColor.DARK_AQUA}Total RAM (MB)")
          totalRAMScore?.score = (totalMemory / 1024L / 1024L).toInt()

          // Time to day/night
          val isDay = it.world.time < TIME_NIGHT
          val entryToSet = "${ChatColor.GREEN}Time to ${if (isDay) "Night" else "Day"} (s)"
          val entryToRemove = "${ChatColor.GREEN}Time to ${if (isDay) "Day" else "Night"} (s)"
          val duration = if (isDay) TIME_NIGHT - it.world.time  else 24000 - it.world.time + 1000L
          scoreboard?.resetScores(entryToRemove)

          val timeScore = objective?.getScore(entryToSet)
          timeScore?.score = (duration / 20L).toInt()

          // Ping score
          val pingScore = objective?.getScore("${ChatColor.AQUA}Ping (ms)")
          pingScore?.score = it.ping

          if (scoreboard != null) {
            it.scoreboard = scoreboard
          }
        }
      }
    }.runTaskTimer(this, 20 * 3, 20 * 5)
  }

  fun playerJoined(event: PlayerJoinEvent) {

  }

  fun playerLeft(event: PlayerQuitEvent) {

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
}
