package io.github.darkstarworks.trialChamberPro.dungeon

import org.bukkit.block.BlockFace

/**
 * A connector (doorway) on a room piece: its local cell within the room's
 * footprint and the horizontal [facing] it opens toward (outward through the
 * wall). Read from a `minecraft:jigsaw` block's orientation at capture time.
 */
data class Connector(
    val x: Int,
    val y: Int,
    val z: Int,
    val facing: BlockFace,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * The geometry the stitcher needs about a room — no block data. [tags] carry
 * roles like `entrance` / `vault` / `boss` (set as a capture argument).
 */
data class RoomShape(
    val id: String,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val connectors: List<Connector>,
    val tags: Set<String> = emptySet(),
)
