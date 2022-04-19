package com.lauriethefish.gliss

import com.lauriethefish.gliss.projections.NaiveBlockProjection
import com.lauriethefish.gliss.projections.NaiveNativeBlockProjection
import com.lauriethefish.gliss.projections.NativeBlockMapProjection
import com.lauriethefish.gliss.projections.NativeManualAccessBlockProjection
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.logging.Logger

class BenchmarkRunner(private val pl: JavaPlugin, private val logger: Logger, private val cfg: Config, private val player: Player) : Runnable {
    private val projections: MutableMap<String, (BlockProjectionSettings) -> BlockProjection> = HashMap()

    private val settings = BlockProjectionSettings(
            player = player,
            fromCentreBlock = cfg.originCenterPos,
            toCentreBlock = cfg.destCenterPos,
            windowWidth = cfg.portalWidth,
            windowHeight = cfg.portalHeight,
            projectionWidth = cfg.projectionWidth,
            projectionHeight = cfg.projectionHeight,
            cfg.wideningAmount
    )

    init {
        // Basic implementation of block projection, with no other optimisations
        projections.put("Naive Bukkit implementation") { s -> NaiveBlockProjection(s) }
        // The above but using NMS to get the block states & send the packets
        projections.put("Naive NMS implementation") { s -> NaiveNativeBlockProjection(s) }

        // The above but manually storing the data palettes before rendering to make accessing the block states faster
        projections.put("Manual access NMS implementation") { s -> NativeManualAccessBlockProjection(s) }

        // The above but pre-generates a map of which blocks are actually visible from the destination of the portal (via a flood fill)
        projections.put("Block map NMS implementation") { s -> NativeBlockMapProjection(s) }
    }

    private val projectionIterator = projections.entries.iterator()

    override fun run() {
        // If benchmarked all projections, or player has logged out, quit
        if(!projectionIterator.hasNext() || !player.isOnline) {
            return
        }

        val entry = projectionIterator.next()
        val name = entry.key
        val projection = entry.value(settings)

        logger.info("Benchmarking $name")
        // Run the benchmark
        ProjectionBenchmark(logger,
                settings,
                projection,
                cfg.ticksPerBenchmark,
                cfg.ticksPerRotation,
                pl,
                this, // Call back to this runner once the benchmark is done
                cfg).start()
    }


}