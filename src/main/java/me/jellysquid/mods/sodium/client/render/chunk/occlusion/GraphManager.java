package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.jellysquid.mods.sodium.client.render.chunk.LocalSectionIndex;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.util.BitwiseMath;
import me.jellysquid.mods.sodium.client.util.sorting.MergeSort;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayDeque;

public class GraphManager {
    private final RenderRegionManager regions;
    private final int boundsMinY, boundsMaxY;

    public GraphManager(RenderRegionManager regions, World world) {
        this.regions = regions;

        this.boundsMinY = world.getBottomSectionCoord();
        this.boundsMaxY = world.getTopSectionCoord() - 1;
    }

    public void findVisibleSections(GraphNodeVisitor visitor,
                                    Camera camera,
                                    Viewport viewport,
                                    float searchDistance,
                                    boolean useOcclusionCulling) {
        var graphOrigin = ChunkSectionPos.from(camera.getBlockPos());
        var graphQueue = new GlobalSearchQueue();

        this.init(graphQueue, viewport, camera, graphOrigin, searchDistance);

        GraphSearchQueueList searchQueues;

        while ((searchQueues = graphQueue.next()) != null) {
            final var searchOrigin = searchQueues.origin;
            final var searchRegion = this.regions.getRegion(searchOrigin.getKey());

            if (searchRegion != null) {
                traverseRegion(visitor, searchRegion, searchQueues, camera, viewport, graphOrigin, searchDistance, useOcclusionCulling);
            }
        }
    }


    private static void traverseRegion(GraphNodeVisitor visitor,
                                       RenderRegion searchRegion,
                                       GraphSearchQueueList searchQueues,
                                       Camera camera,
                                       Viewport viewport,
                                       ChunkSectionPos graphOriginCoord,
                                       float searchDistance,
                                       boolean useOcclusionCulling)
    {
        final var cameraPosition = getCameraOrigin(camera);
        final var searchDistanceSquared = MathHelper.square(searchDistance);

        final var queue = searchQueues.origin;

        for (int queueIndex = 0; queueIndex < queue.size(); queueIndex++) {
            var nodeIndex = queue.getNodeIndex(queueIndex);
            var nodeIncomingDirections = queue.getIncomingDirections(nodeIndex);

            long nodeConnections;
            int nodeFlags;

            {
                var nodeData = searchRegion.getGraphData()
                        .getNodeData(nodeIndex);

                // Do not traverse into unloaded parts of the world
                if (GraphNode.isEmpty(nodeData)) {
                    continue;
                }

                nodeConnections = GraphNode.unpackConnections(nodeData);
                nodeFlags = GraphNode.unpackFlags(nodeData);
            }

            // The position of the graph node in world-space.
            var nodeCoordX = searchRegion.getChunkX() + LocalSectionIndex.unpackX(nodeIndex);
            var nodeCoordY = searchRegion.getChunkY() + LocalSectionIndex.unpackY(nodeIndex);
            var nodeCoordZ = searchRegion.getChunkZ() + LocalSectionIndex.unpackZ(nodeIndex);

            // Only check whether the node is visible if it isn't the node we're starting in. This prevents two problems:
            //   a) The frustum check is not perfect, and floating-point precision issues can cause the starting node to
            // be *not quite* within the viewport.
            //   b) When the maximum search distance is <16.0 blocks, a problem can occur where none of the vertices to
            // the starting node's render bounds are within the render distance.
            if (graphOriginCoord.getX() != nodeCoordX || graphOriginCoord.getY() != nodeCoordY || graphOriginCoord.getZ() != nodeCoordZ) {
                var distance = getClosestVertexDistanceToCamera(cameraPosition, graphOriginCoord,
                        nodeCoordX, nodeCoordY, nodeCoordZ);

                if (distance > searchDistanceSquared) {
                    // Node is outside the search distance
                    continue;
                }

                if (isOutsideViewport(viewport, nodeCoordX, nodeCoordY, nodeCoordZ)) {
                    // Node is not within the viewport
                    continue;
                }
            }

            // The node was determined to be visible, so we are going to visit it and try to traverse into any neighbors
            visitor.visit(searchRegion, nodeIndex, nodeFlags);

            // The directions from this node which we can traverse into
            int outgoingDirections;

            if (useOcclusionCulling) {
                // We only traverse into neighboring nodes if there is a path through any *incoming* direction to the
                // given *outgoing* direction.
                outgoingDirections = VisibilityEncoding.getOutgoingConnections(nodeConnections, nodeIncomingDirections);
            } else {
                // When occlusion culling is disabled, we do not consider the connected-ness of two nodes.
                outgoingDirections = GraphDirection.ALL;
            }

            // We can only traverse *outwards* from the origin point of the graph search. In other words, the node we're
            // trying to traverse into must have a distance greater than the current node's distance. This prevents
            // back-tracking into nodes we have already visited.
            outgoingDirections &= getOutwardDirections(graphOriginCoord, nodeCoordX, nodeCoordY, nodeCoordZ);

            // Early-exit when there are no neighbors to search.
            if (outgoingDirections != GraphDirection.NONE) {
                searchNeighborNodes(searchQueues, nodeIndex, outgoingDirections);
            }
        }
    }

    private static void searchNeighborNodes(GraphSearchQueueList queues, int sectionIndex, int connections) {
        // -X
        if (GraphDirection.contains(connections, GraphDirection.NEG_X)) {
            var neighborSectionIndex = LocalSectionIndex.decX(sectionIndex);
            (neighborSectionIndex > sectionIndex ? queues.negX /* wrapped */ : queues.origin)
                    .add(neighborSectionIndex, 1 << GraphDirection.POS_X);
        }

        // +X
        if (GraphDirection.contains(connections, GraphDirection.POS_X)) {
            var neighborSectionIndex = LocalSectionIndex.incX(sectionIndex);
            (neighborSectionIndex < sectionIndex ? queues.posX /* wrapped */ : queues.origin)
                    .add(neighborSectionIndex, 1 << GraphDirection.NEG_X);
        }

        // -Y
        if (GraphDirection.contains(connections, GraphDirection.NEG_Y)) {
            var neighborSectionIndex = LocalSectionIndex.decY(sectionIndex);
            (neighborSectionIndex > sectionIndex ? queues.negY /* wrapped */ : queues.origin)
                    .add(neighborSectionIndex, 1 << GraphDirection.POS_Y);
        }

        // +Y
        if (GraphDirection.contains(connections, GraphDirection.POS_Y)) {
            var neighborSectionIndex = LocalSectionIndex.incY(sectionIndex);
            (neighborSectionIndex < sectionIndex ? queues.posY /* wrapped */ : queues.origin)
                    .add(neighborSectionIndex, 1 << GraphDirection.NEG_Y);
        }

        // -Z
        if (GraphDirection.contains(connections, GraphDirection.NEG_Z)) {
            var neighborSectionIndex = LocalSectionIndex.decZ(sectionIndex);
            (neighborSectionIndex > sectionIndex ? queues.negZ /* wrapped */ : queues.origin)
                    .add(neighborSectionIndex, 1 << GraphDirection.POS_Z);
        }

        // +Z
        if (GraphDirection.contains(connections, GraphDirection.POS_Z)) {
            var neighborSectionIndex = LocalSectionIndex.incZ(sectionIndex);
            (neighborSectionIndex < sectionIndex ? queues.posZ /* wrapped */ : queues.origin)
                    .add(neighborSectionIndex, 1 << GraphDirection.NEG_Z);
        }
    }

    private static int getOutwardDirections(ChunkSectionPos origin, int x, int y, int z) {
        int directions = 0;

        directions |=    BitwiseMath.lessThanOrEqual(x, origin.getX()) << GraphDirection.NEG_X;
        directions |= BitwiseMath.greaterThanOrEqual(x, origin.getX()) << GraphDirection.POS_X;

        directions |=    BitwiseMath.lessThanOrEqual(y, origin.getY()) << GraphDirection.NEG_Y;
        directions |= BitwiseMath.greaterThanOrEqual(y, origin.getY()) << GraphDirection.POS_Y;

        directions |=    BitwiseMath.lessThanOrEqual(z, origin.getZ()) << GraphDirection.NEG_Z;
        directions |= BitwiseMath.greaterThanOrEqual(z, origin.getZ()) << GraphDirection.POS_Z;

        return directions;
    }

    // Picks the closest vertex to the camera of the chunk render bounds, and returns the distance of the vertex from
    // the camera position. This is useful for determining whether the section is within the render distance, since it
    // holds that if the closest vertex is not within the view distance, none of the further-away vertices can be within
    // the view distance either.
    private static float getClosestVertexDistanceToCamera(Vector3f camera, ChunkSectionPos origin, int x, int y, int z) {
        // The offset from the center of chunk section (in model space) which is used to create the vertex
        // NOTE: We use bit-shifting magic here to avoid some branching. The distance of the chunk from the graph origin
        // is used to create a signed value of either (-1, 0, or 1), which is then shifted left to create (-8, 0, 8). If
        // the compiler was smarter, this would likely be faster to implement as conditional-moves.
        int ox = 8 + (Integer.signum(origin.getX() - x) * 8); // (chunk.x > center.x) ? -8 : +8
        int oy = 8 + (Integer.signum(origin.getY() - y) * 8); // (chunk.y > center.y) ? -8 : +8
        int oz = 8 + (Integer.signum(origin.getZ() - z) * 8); // (chunk.z > center.z) ? -8 : +8

        // The position of the vertex to use in the distance comparison (in world space.)
        // This will be the vertex of the chunk section's render bounds which are closest to the camera...
        int px = (x << 4) + ox;
        int py = (y << 4) + oy;
        int pz = (z << 4) + oz;

        // The distance of the vertex to the camera
        // NOTE: We also convert the vertex coordinates from int->double, which shouldn't have any precision loss. This
        // is because our bit-shifting magic doesn't work on floating point values.
        float dx = camera.x - (float) px;
        float dy = camera.y - (float) py;
        float dz = camera.z - (float) pz;

        // The weighted distance of the vertex to the camera (which accounts for fog effects being cylindrical)
        //   max(length(distance.xz), abs(distance.y))
        return Math.max((dx * dx) + (dz * dz), dy * dy);
    }

    private void init(GlobalSearchQueue queue,
                      Viewport viewport,
                      Camera camera,
                      ChunkSectionPos origin,
                      float searchDistance)
    {
        if (origin.getY() < this.boundsMinY) {
            // below the world
            this.initOutsideWorldHeight(queue, viewport, camera, origin, searchDistance,
                    this.boundsMinY, GraphDirection.NEG_Y);
        } else if (origin.getY() > this.boundsMaxY) {
            // above the world
            this.initOutsideWorldHeight(queue, viewport, camera, origin, searchDistance,
                    this.boundsMaxY, GraphDirection.POS_Y);
        } else {
            // within the world bounds
            var nodeData = this.getNodeData(origin.getX(), origin.getY(), origin.getZ());

            if (!GraphNode.isEmpty(nodeData)) {
                // within a loaded section
                queue.enqueue(origin.getX(), origin.getY(), origin.getZ(), GraphDirection.ALL);
            }

            // TODO: If the camera is within world boundaries but not a loaded chunk, we should still render something
            // It's not clear what the correct approach for doing this is...
        }
    }

    private void initOutsideWorldHeight(GlobalSearchQueue queue,
                                        Viewport viewport,
                                        Camera camera,
                                        ChunkSectionPos origin,
                                        float searchDistance,
                                        int height,
                                        int direction)
    {
        var radius = MathHelper.ceil(searchDistance / 16.0f);
        var positions = new LongArrayList(((radius * 2) + 1) * 2);

        // The origin of the graph search
        int originX = origin.getX();
        int originZ = origin.getZ();

        // Iterate the loaded view distance around the camera
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                // Get the node at the relative offset from the camera origin
                int x = originX + offsetX;
                int y = height;
                int z = originZ + offsetZ;

                var nodeData = this.getNodeData(x, y, z);

                // Skip the node if it isn't loaded or is outside the viewport
                if (!GraphNode.isEmpty(nodeData) || isOutsideViewport(viewport, x, y, z)) {
                    continue;
                }

                positions.add(ChunkSectionPos.asLong(x, y, z));
            }
        }

        if (!positions.isEmpty()) {
            enqueueAll(queue, positions, camera, direction);
        }
    }

    private static void enqueueAll(GlobalSearchQueue queue,
                                   LongArrayList positions,
                                   Camera camera,
                                   int direction)
    {
        final var distance = new float[positions.size()];
        final var origin = getCameraOrigin(camera);

        for (int index = 0; index < positions.size(); index++) {
            var position = positions.getLong(index);

            var x = ChunkSectionPos.unpackX(position);
            var y = ChunkSectionPos.unpackY(position);
            var z = ChunkSectionPos.unpackZ(position);

            distance[index] = -getNodeSquaredDistance(origin, x, y, z); // sort by closest to camera
        }

        for (int index : MergeSort.mergeSort(distance)) {
            var position = positions.getLong(index);

            queue.enqueue(
                    ChunkSectionPos.unpackX(position),
                    ChunkSectionPos.unpackY(position),
                    ChunkSectionPos.unpackZ(position),
                    1 << direction);
        }
    }

    private static final double CHUNK_RENDER_BOUNDS_EPSILON = 1.0D / 32.0D;
    private static final double CHUNK_RENDER_BOUNDS_SIZE = 8.0D;

    private static boolean isOutsideViewport(Viewport viewport, int nodeCoordX, int nodeCoordY, int nodeCoordZ) {
        double centerX = (nodeCoordX << 4) + 8;
        double centerY = (nodeCoordY << 4) + 8;
        double centerZ = (nodeCoordZ << 4) + 8;

        return !viewport.isBoxVisible(centerX, centerY, centerZ,
                CHUNK_RENDER_BOUNDS_SIZE + CHUNK_RENDER_BOUNDS_EPSILON);
    }

    private static float getNodeSquaredDistance(Vector3f origin, int chunkX, int chunkY, int chunkZ) {
        float dx = origin.x - ((chunkX << 4) + 8);
        float dy = origin.y - ((chunkY << 4) + 8);
        float dz = origin.z - ((chunkZ << 4) + 8);

        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private static Vector3f getCameraOrigin(Camera camera) {
        return new Vector3f((float) camera.getPos().x, (float) camera.getPos().y, (float) camera.getPos().z);
    }

    private long getNodeData(int x, int y, int z) {
        var region = this.regions.getRegionForSection(x, y, z);

        if (region != null) {
            return region.getGraphData()
                    .getNodeData(LocalSectionIndex.fromGlobal(x, y, z));
        }

        return GraphNode.empty();
    }

    private static class GlobalSearchQueue {
        private final ArrayDeque<GraphSearchQueue> queue = new ArrayDeque<>();
        private final Long2ReferenceOpenHashMap<GraphSearchQueue> searches = new Long2ReferenceOpenHashMap<>();

        public void enqueue(int x, int y, int z, int directions) {
            var key = ChunkSectionPos.asLong(
                    x >> RenderRegion.REGION_WIDTH_SH,
                    y >> RenderRegion.REGION_HEIGHT_SH,
                    z >> RenderRegion.REGION_LENGTH_SH);

            var queue = this.searches.get(key);

            if (queue == null) {
                queue = this.createSearchQueue(key);
            }

            queue.add(LocalSectionIndex.fromGlobal(x, y, z), directions);
        }

        public GraphSearchQueueList next() {
            GraphSearchQueue origin;

            do {
                if (this.queue.isEmpty()) {
                    return null;
                }

                origin = this.queue.remove();
            } while (origin.isEmpty());

            GraphSearchQueue negX = this.prepareNeighborQueue(origin, Direction.WEST);
            GraphSearchQueue posX = this.prepareNeighborQueue(origin, Direction.EAST);
            GraphSearchQueue negY = this.prepareNeighborQueue(origin, Direction.DOWN);
            GraphSearchQueue posY = this.prepareNeighborQueue(origin, Direction.UP);
            GraphSearchQueue negZ = this.prepareNeighborQueue(origin, Direction.NORTH);
            GraphSearchQueue posZ = this.prepareNeighborQueue(origin, Direction.SOUTH);

            return new GraphSearchQueueList(origin, negX, posX, negY, posY, negZ, posZ);
        }

        private GraphSearchQueue prepareNeighborQueue(GraphSearchQueue queue, Direction direction) {
            var neighborKey = ChunkSectionPos.offset(queue.getKey(), direction);
            var neighborQueue = this.searches.get(neighborKey);

            if (neighborQueue == null) {
                neighborQueue = this.createSearchQueue(neighborKey);
            }

            return neighborQueue;
        }

        private GraphSearchQueue createSearchQueue(long key) {
            GraphSearchQueue queue = new GraphSearchQueue(key);

            this.searches.put(key, queue);
            this.queue.add(queue);

            return queue;
        }
    }

}
