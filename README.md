# Gliss

This repository is a collection of different implementations of block projection ([see-through portals](https://github.com/Lauriethefish/BetterPortals)) for spigot/paper servers.
It contains benchmarks for the different projection types in an attempt to find more efficient implementations.

## How block projection works

Block projection is what I call the process BetterPortals uses to allow you to see blocks through portals. The main steps to this are as follows:

- Loop through the blocks around the origin of the portal
- Find the blocks which are within the view frustum from the player's position to the 4 corners of the portal frame
- Replace those blocks with the destination blocks

Optimising these three steps is responsible for a large proportion of the BetterPortals code.

The below is a summary of the different ways we can do block projection, and the improvements made over time.

## Rendering was slow, to begin with

The most basic implementation of block projection looks something like this (kotlin-like pseudocode)

```kotlin
val playerEyePos = ...
        
// Loop through blocks around portal
for(x in -20..20) {
    for(y in -20..20) {
        for(z in -20..20) {
            // If the line from the portal to the player's position intersects the portal
            val renderedBlockPos = if(intersects(playerEyePos, portal, x, y, z)) {
                portalDestination.add(x, y, z) // Use the destination block
            }   else    {
                portalOrigin.add(x, y, z) // Use the origin block
            }
            
            // Update the block that the player can see
            player.sendBlockChange(portalOrigin.add(x, y, z), renderedBlockPos.block.blockData)
        }
    }
}
```

Now this is **bad**, ***really bad***. Why? Because block projection has to be updated every tick.

- This will send 68,921 unique block change packets to the player if updated every tick.
- This will allocate 69,921 instances of `BlockData` every tick.
- This will crash the player. (see previous comment about 68,921 unique block change packets)

The first step to improve this is to **not** send 68,921 packets per tick.
We can do this by creating a map which stores the currently sent state of each block around the player.

```kotlin
/* Somewhere else, shared between multiple updates */
val blockStates: MutableMap<Vector, BlockData> = HashMap()

/* When we update our block state */
val position = ...
val renderedState = renderedBlockPos.block.blockData
val existingState = blockStates.get(position)

if(existingState != renderedState) {
    // Store this state so it won't be sent again
    blockStates.put(position, renderedState)
    
    /* Send the block update  ... */
}
```

This still isn't great but now the projection can be viewed without crashing the player! We can make a few other improvements to this step, such as using an `Array<BlockData>` instead of the map to avoid allocating tons of `Vector`s.

We're not out of the water yet though, this implementation still has some serious problems.

- Previously mentioned 69,921 allocations of `BlockData` per tick.
- We are sending a lot of blocks to the player that can't actually be seen - this implementation will change all the blocks visible in a certain range around the portal without first checking if they are fully covered by other opaque blocks.

## Dodging the Bukkit API

The next thing we'll do is optimise the `BlockData` allocations.

This is pretty easy, we can simply get the underlying NMS world for the origin and destination, and use that to get us an NMS `BlockState`. The minecraft server is actually pretty clever, and each possible block state is immutable and shared throughout the whole server, sort of like an enum variant.

```kotlin
val nmsWorld = (originWorld as CraftWorld).handle
val playerConnection = (player as CraftPlayer).handle.playerConnection

/* When rendering */
var exampleState = nmsWorld.getBlockState(BlockPos(x, y, z))

// Send a block update packet for this state
playerConnection.send(ClientboundBlockUpdatePacket(/* origin block position */, exampleState))
```

With no other changes, using NMS to grab the states and sends the packets more than halves rendering times. (all results are at the bottom)

But we can do better! It turns out, that `ServerLevel#getBlockState(BlockPos)` does a fair bit of extra work fetching each block, as it has to find the chunk the block is in, find the correct chunk section, *then* grab the `BlockState` from the underlying data palette - the data structure actually used to store blocks. That's a lot of method calls and as far as I know, they aren't inlined as much as I'd like them to be. 
 
To improve this a little a
further, we can create an array of the data palettes we will need to render a particular portal, then index into that array with some quick bit shifting and multiplication. I won't bore you with the details of this, but the results are all below. (The difference was actually much bigger than I thought it would be)


(results gathered with implementations in this repository, and a 13x13x7 render distance)

|Implementation|Average Render Time (ms, 2 dp.)|
|-----------|-----------|
|Basic Bukkit|1.69|
|Basic NMS|0.62|
|Manual block fetching NMS|0.44|

## Efficiently skipping occluded blocks

