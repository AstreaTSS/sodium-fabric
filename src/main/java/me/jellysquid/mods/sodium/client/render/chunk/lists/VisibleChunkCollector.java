package me.jellysquid.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.GraphNodeFlags;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.GraphNodeVisitor;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;

public class VisibleChunkCollector implements GraphNodeVisitor {
    private final ObjectArrayList<ChunkRenderList> renderLists = new ObjectArrayList<>();
    private final int frame;

    public VisibleChunkCollector(int frame) {
        this.frame = frame;
    }

    @Override
    public void visit(RenderRegion region, int sectionIndex, int sectionFlags) {
        if (GraphNodeFlags.containsRenderData(sectionFlags)) {
            this.addToRenderLists(region, sectionIndex, sectionFlags);
        }
    }

    private void addToRenderLists(RenderRegion region, int sectionIndex, int sectionFlags) {
        ChunkRenderList list = region.getRenderList();

        if (list.getLastVisibleFrame() != this.frame) {
            this.prepareList(list);
        }

        list.add(sectionIndex, sectionFlags);
    }

    private void prepareList(ChunkRenderList list) {
        list.reset(this.frame);

        this.renderLists.add(list);
    }

    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.renderLists);
    }
}
