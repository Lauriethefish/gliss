package com.lauriethefish.gliss.projections

import com.lauriethefish.gliss.BlockProjection
import com.lauriethefish.gliss.BlockProjectionSettings
import com.lauriethefish.gliss.ChunkAccessor
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.world.level.block.state.BlockState
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import org.bukkit.util.Vector
import java.lang.Math.abs

class NativeManualAccessBlockProjection(private val settings: BlockProjectionSettings) : BlockProjection {
    private val planeNormal: Vector = Vector(1, 0, 0)
    private val planeCentre: Vector = Vector(0.0, 0.0, 0.0)
    private val lateralMaxDeviation = (settings.windowWidth / 2.0) + settings.wideningAmount
    private val verticalMaxDeviation = (settings.windowHeight / 2.0) + settings.wideningAmount

    private val totalProjectionWidth = settings.projectionWidth * 2 + 1
    private val totalProjectionHeight = settings.projectionHeight * 2 + 1
    private val states: Array<BlockState?> = Array(totalProjectionWidth * totalProjectionWidth * totalProjectionHeight) { null }

    private val fromAccess: ChunkAccessor
    private val toAccess: ChunkAccessor

    init {
        val sizeVector = Vector(settings.projectionWidth, settings.projectionHeight, settings.projectionWidth)
        fromAccess = ChunkAccessor(settings.fromCentreBlock.clone().subtract(sizeVector), settings.fromCentreBlock.clone().add(sizeVector))
        toAccess = ChunkAccessor(settings.toCentreBlock.clone().subtract(sizeVector), settings.toCentreBlock.clone().add(sizeVector))
    }

    override fun update() {
        // Find the position of the player, relative to the centres of the blocks at the origin
        val playerRelativePos = settings.player
                .eyeLocation // Not feet location, eye location
                .toVector()
                // Offset by half a block so that we are relative to the centres of the blocks
                .subtract(settings.fromCentreBlock.clone().add(0.5, 0.5, 0.5).toVector())

        val connection = (settings.player as CraftPlayer).handle.connection

        var i = 0
        for(x in -settings.projectionWidth..settings.projectionWidth) {
            // Skip blocks directly in line with the portal
            if(x == 0) {
                continue
            }

            for(y in -settings.projectionHeight..settings.projectionHeight) {
                for(z in -settings.projectionWidth..settings.projectionWidth) {
                    val relativePos = Vector(x, y, z)
                    val originBlockPos = BlockPos(x + settings.fromCentreBlock.blockX, y + settings.fromCentreBlock.blockY, z + settings.fromCentreBlock.blockZ)

                    // Find if the line between the player's viewpoint and the block intersects the portal view-plane
                    val renderedState: BlockState = if(intersects(playerRelativePos, relativePos)) {
                        // If it does, then this block should be projected from the other side of the portal
                        toAccess.getState(x + settings.toCentreBlock.blockX,
                                y + settings.toCentreBlock.blockY,
                                z + settings.toCentreBlock.blockZ)
                    }   else {
                        // Otherwise the block should be that of the origin of the portal
                        fromAccess.getState(originBlockPos.x, originBlockPos.y, originBlockPos.z)
                    }

                    val existing = states[i]
                    if(renderedState != existing) { // Only send an update packet if the block actually changes
                        connection.send(ClientboundBlockUpdatePacket(originBlockPos, renderedState))
                        states[i] = renderedState
                    }

                    i++
                }
            }
        }
    }

    private fun intersects(playerPos: Vector, blockPos: Vector): Boolean {
        val direction: Vector = blockPos.clone().subtract(playerPos).normalize()

        // Find if we intersect the plane, and where
        val denominator: Double = planeNormal.dot(direction)
        if (abs(denominator) > 0.0001) {
            val difference: Vector = planeCentre.clone().subtract(playerPos)
            val t = difference.dot(planeNormal) / denominator

            // If the block was before the portal, return false
            if (playerPos.distance(blockPos) < t) {
                return false
            }
            if (t > 0.0001) {
                val portalIntersectPoint: Vector = playerPos.clone().add(direction.multiply(t))
                val distCenter = portalIntersectPoint.subtract(planeCentre)

                // Return true if the intersection point was close enough to the portal window
                return abs(distCenter.x) <= 0.001 && abs(distCenter.y) <= verticalMaxDeviation && abs(distCenter.z) <= lateralMaxDeviation
            }
        }

        return false
    }
}