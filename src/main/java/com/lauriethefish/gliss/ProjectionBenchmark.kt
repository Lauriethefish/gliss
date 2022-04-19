package com.lauriethefish.gliss

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

class ProjectionBenchmark(private val logger: Logger,
                          private val settings: BlockProjectionSettings,
                          private val projection: BlockProjection,
                          private val ticksToComplete: Int,
                          ticksPerRotation: Int,
                          private val pl: JavaPlugin,
                          private val onFinish: Runnable,
                            cfg: Config): Runnable {
    // Degrees per tick to rotate by
    private val step = 360.0 / ticksPerRotation
    // Current rotation angle around the portal
    private var currentAngle = 0.0
    // Distance at which to orbit around the portal
    private val radius = cfg.rotationRadius
    private var taskId = -1

    private var totalMillisTaken = 0.0
    private var totalInvocations = 0

    fun start() {
        taskId = Bukkit.getScheduler().runTaskTimer(pl, this, 0L, 1L).taskId
    }

    /**
     * Gets an instant using System.nanoTime for more precision
     * @return The instant using nanosecond resolution time
     */
    private fun getNowPrecise(): Instant? {
        val timeNano = System.nanoTime()
        val leftOverNano = timeNano % 1_000_000_000
        val seconds = (timeNano - leftOverNano) / 1_000_000_000
        return Instant.ofEpochSecond(seconds, leftOverNano)
    }

    override fun run() {
        // Calculate the position on a radius from the portal's centre at a particular angle using trig
        val xAmount = Math.sin(Math.toRadians(currentAngle)) * radius
        val zAmount = Math.cos(Math.toRadians(currentAngle)) * radius

        val playerPos = settings.fromCentreBlock.clone().add(xAmount, 0.0, zAmount)
        playerPos.direction = settings.player.location.direction // Don't reset the player's location every time

        settings.player.teleport(playerPos)

        val before = getNowPrecise()
        projection.update()
        val after = getNowPrecise()

        totalMillisTaken += Duration.between(before, after).nano.toDouble() / 1_000_000.0
        totalInvocations++

        currentAngle += step // Move us around the circle for next invocation

        // If the player logs out, or we've finished the benchmark, log the average milliseconds per render
        if(totalInvocations == ticksToComplete || !settings.player.isOnline) {
            val avgMillisTaken = totalMillisTaken / totalInvocations
            logger.info("Avg. millis taken: $avgMillisTaken")

            Bukkit.getScheduler().cancelTask(taskId)
            onFinish.run()
        }
    }

}