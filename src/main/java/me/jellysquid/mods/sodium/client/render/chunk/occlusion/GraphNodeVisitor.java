package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;

public interface GraphNodeVisitor {
    void visit(RenderRegion region, int sectionIndex, int sectionFlags);
}
