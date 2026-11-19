package com.esmpfun.bettertrialchambers.models

import java.io.Serializable

/**
 * One block, as remembered for putting a chamber back the way it was.
 *
 * @property blockData the block's own description, e.g. which way a stair faces.
 * @property tileEntity the old way of remembering what a block was holding: a
 *   hand-built map, one shape per block type. Kept only so snapshots taken
 *   before [structure] existed still restore. See [structure].
 * @property structure the block exactly as the game itself would save it, in the
 *   same form a structure block uses. Where [tileEntity] understood eleven kinds
 *   of block and a fraction of what each held, this holds everything about any
 *   of them: a chest's name and lock as well as its contents, a mob spawner, a
 *   command block, a shelf, another plugin's data stored on the block, and
 *   whatever Minecraft adds next, with nothing to keep up to date. Takes
 *   precedence over [tileEntity] when both are present.
 */
data class BlockSnapshot(
    val blockData: String,
    val tileEntity: Map<String, Any>? = null,
    val structure: ByteArray? = null,
) : Serializable {

    /** True when this block was holding something worth putting back. */
    val hasContents: Boolean get() = structure != null || tileEntity != null

    // A ByteArray compares by identity, which would make two otherwise identical
    // snapshots unequal. These are written by hand so that does not happen; the
    // class is used as a map value where equality is rarely asked for, but it
    // should still be right when it is.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockSnapshot) return false
        return blockData == other.blockData &&
            tileEntity == other.tileEntity &&
            (structure?.contentEquals(other.structure) ?: (other.structure == null))
    }

    override fun hashCode(): Int {
        var result = blockData.hashCode()
        result = 31 * result + (tileEntity?.hashCode() ?: 0)
        result = 31 * result + (structure?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
