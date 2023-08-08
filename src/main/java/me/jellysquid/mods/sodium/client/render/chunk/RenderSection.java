package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.GraphNodeFlags;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.ChunkSectionPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The render state object for a chunk section. This contains all the graphics state for each render pass along with
 * data about the render in the chunk visibility graph.
 */
public class RenderSection {
    // Render Region State
    private final RenderRegion region;
    private final int sectionIndex;

    // Chunk Section State
    private final int chunkX, chunkY, chunkZ;

    // Rendering State
    private boolean built = false; // merge with the flags?
    private int flags = GraphNodeFlags.NONE;
    private BlockEntity @Nullable[] globalBlockEntities;
    private BlockEntity @Nullable[] culledBlockEntities;
    private Sprite @Nullable[] animatedSprites;

    private int lastBuiltTime;

    // Lifetime state
    private boolean disposed;

    public RenderSection(RenderRegion region, int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        int rX = this.getChunkX() & (RenderRegion.REGION_WIDTH - 1);
        int rY = this.getChunkY() & (RenderRegion.REGION_HEIGHT - 1);
        int rZ = this.getChunkZ() & (RenderRegion.REGION_LENGTH - 1);

        this.sectionIndex = LocalSectionIndex.fromGlobal(rX, rY, rZ);

        this.region = region;
    }

    /**
     * Deletes all data attached to this render and drops any pending tasks. This should be used when the render falls
     * out of view or otherwise needs to be destroyed. After the render has been destroyed, the object can no longer
     * be used.
     */
    public void delete() {
        this.clearRenderState();
        this.disposed = true;
    }

    public void setInfo(@Nullable BuiltSectionInfo info) {
        if (info != null) {
            this.setRenderState(info);
        } else {
            this.clearRenderState();
        }
    }

    private void setRenderState(@NotNull BuiltSectionInfo info) {
        this.built = true;
        this.flags = info.flags;
        this.globalBlockEntities = info.globalBlockEntities;
        this.culledBlockEntities = info.culledBlockEntities;
        this.animatedSprites = info.animatedSprites;

        var region = this.region;
        region.setRenderState(this.sectionIndex, info);
    }

    private void clearRenderState() {
        this.built = false;
        this.flags = GraphNodeFlags.NONE;
        this.globalBlockEntities = null;
        this.culledBlockEntities = null;
        this.animatedSprites = null;

        var region = this.region;
        region.clearRenderState(this.sectionIndex);
    }

    /**
     * Returns the chunk section position which this render refers to in the world.
     */
    public ChunkSectionPos getChunkPos() {
        return ChunkSectionPos.from(this.chunkX, this.chunkY, this.chunkZ);
    }

    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public int getOriginX() {
        return this.chunkX << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public int getOriginY() {
        return this.chunkY << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public int getOriginZ() {
        return this.chunkZ << 4;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkY() {
        return this.chunkY;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    @Override
    public String toString() {
        return String.format("RenderSection at chunk (%d, %d, %d) from (%d, %d, %d) to (%d, %d, %d)",
                this.chunkX, this.chunkY, this.chunkZ,
                this.getOriginX(), this.getOriginY(), this.getOriginZ(),
                this.getOriginX() + 15, this.getOriginY() + 15, this.getOriginZ() + 15);
    }

    public boolean isBuilt() {
        return this.built;
    }

    public int getSectionIndex() {
        return this.sectionIndex;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    /**
     * Returns a bitfield containing the {@link GraphNodeFlags} for this built section.
     */
    public int getFlags() {
        return this.flags;
    }

    /**
     * Returns the collection of animated sprites contained by this rendered chunk section.
     */
    public Sprite @Nullable[] getAnimatedSprites() {
        return this.animatedSprites;
    }

    /**
     * Returns the collection of block entities contained by this rendered chunk.
     */
    public BlockEntity @Nullable[] getCulledBlockEntities() {
        return this.culledBlockEntities;
    }

    /**
     * Returns the collection of block entities contained by this rendered chunk, which are not part of its culling
     * volume. These entities should always be rendered regardless of the render being visible in the frustum.
     */
    public BlockEntity @Nullable[] getGlobalBlockEntities() {
        return this.globalBlockEntities;
    }

    public long getGlobalCoord() {
        return ChunkSectionPos.asLong(this.getChunkX(), this.getChunkY(), this.getChunkZ());
    }

    public int getLastBuiltFrame() {
        return this.lastBuiltTime;
    }

    public void setLastBuiltTime(int time) {
        this.lastBuiltTime = time;
    }
}
