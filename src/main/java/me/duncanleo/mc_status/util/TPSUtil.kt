package me.duncanleo.mc_status.util

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

object TPSUtil : BukkitRunnable() {
    private var lastTick: Long = 0
    private var tickIntervals: Deque<Long>? = null
    var resolution = 40

    fun registerTask(plugin: Plugin?) {
        lastTick = System.currentTimeMillis()
        tickIntervals = ArrayDeque(Collections.nCopies(resolution, 50L))
        runTaskTimer(plugin!!, 1, 1)
    }

    override fun run() {
        val curr = System.currentTimeMillis()
        val delta = curr - lastTick
        lastTick = curr
        tickIntervals!!.removeFirst()
        tickIntervals!!.addLast(delta)
    }

    val tps: Double
        get() {
            var base = 0
            for (delta in tickIntervals!!) {
                base += delta.toInt()
            }
            return 1000.0 / (base.toDouble() / resolution)
        }
}