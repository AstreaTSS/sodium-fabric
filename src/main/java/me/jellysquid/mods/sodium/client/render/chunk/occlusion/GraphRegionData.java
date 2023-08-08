package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;

import java.util.Arrays;

public class GraphRegionData {
    private final long[] nodeData = new long[RenderRegion.REGION_SIZE];

    public GraphRegionData() {
        Arrays.fill(this.nodeData, GraphNode.empty());
    }

    public void setNodeData(int sectionIndex, long connections, int flags) {
        this.nodeData[sectionIndex] = GraphNode.pack(connections, flags);
    }

    public void removeNodeData(int sectionIndex) {
        this.nodeData[sectionIndex] = GraphNode.empty();
    }

    public long getNodeData(int sectionIndex) {
        return this.nodeData[sectionIndex];
    }
}
