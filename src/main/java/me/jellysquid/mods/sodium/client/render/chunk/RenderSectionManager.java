package me.jellysquid.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.GraphManager;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import me.jellysquid.mods.sodium.client.render.chunk.scheduler.ChunkUpdateScheduler;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RenderSectionManager {
    private final ChunkBuilder builder;

    private final RenderRegionManager regions;
    private final ClonedChunkSectionCache sectionCache;

    private final Long2ReferenceMap<RenderSection> sectionByPosition = new Long2ReferenceOpenHashMap<>();

    private final ConcurrentLinkedDeque<ChunkJobResult<ChunkBuildOutput>> buildResults = new ConcurrentLinkedDeque<>();

    private final ChunkRenderer chunkRenderer;

    private final ClientWorld world;

    private final ReferenceSet<RenderSection> sectionsWithGlobalEntities = new ReferenceOpenHashSet<>();

    private final GraphManager graphManager;

    private final int renderDistance;

    private final ChunkUpdateScheduler updateScheduler = new ChunkUpdateScheduler();

    @NotNull
    private SortedRenderLists renderLists;


    private int lastUpdatedFrame;

    private boolean needsUpdate;

    public RenderSectionManager(ClientWorld world, int renderDistance, CommandList commandList) {
        this.chunkRenderer = new DefaultChunkRenderer(RenderDevice.INSTANCE, ChunkMeshFormats.COMPACT);

        this.world = world;
        this.builder = new ChunkBuilder(world, ChunkMeshFormats.COMPACT);

        this.needsUpdate = true;
        this.renderDistance = renderDistance;

        this.regions = new RenderRegionManager(commandList);
        this.sectionCache = new ClonedChunkSectionCache(this.world);

        this.renderLists = SortedRenderLists.empty();
        this.graphManager = new GraphManager(this.regions, this.world);
    }

    public void updateRenderLists(Camera camera, Viewport viewport, int frame, boolean spectator) {
        this.createTerrainRenderList(camera, viewport, frame, spectator);

        this.needsUpdate = false;
        this.lastUpdatedFrame = frame;
    }

    private void createTerrainRenderList(Camera camera, Viewport viewport, int frame, boolean spectator) {
        this.renderLists = SortedRenderLists.empty();

        final var searchDistance = this.getSearchDistance();
        final var useOcclusionCulling = this.shouldUseOcclusionCulling(camera, spectator);

        var visitor = new VisibleChunkCollector(frame);

        this.graphManager.findVisibleSections(visitor, camera, viewport, searchDistance, useOcclusionCulling);

        this.renderLists = visitor.createRenderLists();
    }

    private float getSearchDistance() {
        float distance;

        if (SodiumClientMod.options().performance.useFogOcclusion) {
            distance = this.getEffectiveRenderDistance();
        } else {
            distance = this.renderDistance * 16.0f;
        }

        return distance;
    }

    private boolean shouldUseOcclusionCulling(Camera camera, boolean spectator) {
        final boolean useOcclusionCulling;
        BlockPos origin = camera.getBlockPos();

        if (spectator && this.world.getBlockState(origin)
                .isOpaqueFullCube(this.world, origin))
        {
            useOcclusionCulling = false;
        } else {
            useOcclusionCulling = MinecraftClient.getInstance().chunkCullingEnabled;
        }
        return useOcclusionCulling;
    }

    public void onSectionAdded(int x, int y, int z) {
        long key = ChunkSectionPos.asLong(x, y, z);

        if (this.sectionByPosition.containsKey(key)) {
            return;
        }

        RenderRegion region = this.regions.createForChunk(x, y, z);

        RenderSection renderSection = new RenderSection(region, x, y, z);
        region.addSection(renderSection);

        this.sectionByPosition.put(key, renderSection);

        Chunk chunk = this.world.getChunk(x, z);
        ChunkSection section = chunk.getSectionArray()[this.world.sectionCoordToIndex(y)];

        if (section.isEmpty()) {
            this.updateSectionInfo(renderSection, BuiltSectionInfo.EMPTY, this.lastUpdatedFrame);
        } else {
            this.updateScheduler.markForUpdate(renderSection, ChunkUpdateType.INITIAL_BUILD, this.lastUpdatedFrame);
        }

        this.needsUpdate = true;
    }

    public void onSectionRemoved(int x, int y, int z) {
        var key = ChunkSectionPos.asLong(x, y, z);
        var section = this.sectionByPosition.remove(key);

        if (section == null) {
            return;
        }

        var region = section.getRegion();

        if (region != null) {
            region.removeSection(section);
        }

        this.updateSectionInfo(section, null, this.lastUpdatedFrame);
        this.updateScheduler.cancelScheduledBuild(section);

        section.delete();

        this.needsUpdate = true;
    }

    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.chunkRenderer.render(matrices, commandList, this.renderLists, pass, new ChunkCameraContext(x, y, z));

        commandList.flush();
    }

    public void tickVisibleRenders() {
        Iterator<ChunkRenderList> it = this.renderLists.iterator();

        while (it.hasNext()) {
            ChunkRenderList renderList = it.next();

            var region = renderList.getRegion();
            var iterator = renderList.sectionsWithSpritesIterator();

            if (iterator == null) {
                continue;
            }

            while (iterator.hasNext()) {
                var section = region.getSection(iterator.next());

                if (section == null) {
                    continue;
                }

                var sprites = section.getAnimatedSprites();

                if (sprites == null) {
                    continue;
                }

                for (Sprite sprite : sprites) {
                    SpriteUtil.markSpriteActive(sprite);
                }
            }
        }
    }

    public boolean isSectionVisible(int x, int y, int z) {
        RenderSection render = this.getRenderSection(x, y, z);

        if (render == null) {
            return false;
        }

        RenderRegion region = render.getRegion();
        ChunkRenderList renderList = region.getRenderList();

        return renderList.isSectionVisible(LocalSectionIndex.fromGlobal(x, y, z));
    }

    public void updateChunks(Camera camera, boolean updateImmediately) {
        this.sectionCache.cleanup();
        this.regions.update();

        this.submitRebuildTasks(ChunkUpdateType.IMPORTANT_REBUILD, camera, false);
        this.submitRebuildTasks(ChunkUpdateType.REBUILD, camera, !updateImmediately);
        this.submitRebuildTasks(ChunkUpdateType.INITIAL_BUILD, camera, !updateImmediately);
    }

    public void uploadChunks() {
        this.waitForBlockingTasks();

        var results = this.collectChunkBuildResults();

        if (!results.isEmpty()) {
            this.processChunkBuildResults(results);

            for (var result : results) {
                result.delete();
            }

            this.needsUpdate = true;
        }
    }

    private void processChunkBuildResults(ArrayList<ChunkBuildOutput> results) {
        var filtered = filterChunkBuildResults(results);

        this.regions.uploadMeshes(RenderDevice.INSTANCE.createCommandList(), filtered);

        for (var result : filtered) {
            this.updateSectionInfo(result.render, result.info, result.buildTime);
            this.updateScheduler.onSectionUploaded(result.render, result.buildTime);
        }
    }

    private void updateSectionInfo(RenderSection render, @Nullable BuiltSectionInfo info, int timestamp) {
        render.setInfo(info);

        if (info == null || ArrayUtils.isEmpty(info.globalBlockEntities)) {
            this.sectionsWithGlobalEntities.remove(render);
        } else {
            this.sectionsWithGlobalEntities.add(render);
        }

        render.setLastBuiltTime(timestamp);
    }

    private static List<ChunkBuildOutput> filterChunkBuildResults(ArrayList<ChunkBuildOutput> outputs) {
        var map = new Reference2ReferenceLinkedOpenHashMap<RenderSection, ChunkBuildOutput>();

        for (var output : outputs) {
            if (output.render.isDisposed() || output.render.getLastBuiltFrame() > output.buildTime) {
                continue;
            }

            var render = output.render;
            var previous = map.get(render);

            if (previous == null || previous.buildTime < output.buildTime) {
                map.put(render, output);
            }
        }

        return new ArrayList<>(map.values());
    }

    private ArrayList<ChunkBuildOutput> collectChunkBuildResults() {
        ArrayList<ChunkBuildOutput> results = new ArrayList<>();
        ChunkJobResult<ChunkBuildOutput> result;

        while ((result = this.buildResults.poll()) != null) {
            results.add(result.unwrap());
        }

        return results;
    }

    private void waitForBlockingTasks() {
        boolean shouldContinue;

        do {
            shouldContinue = this.builder.stealBlockingTask();
        } while (shouldContinue);
    }

    private void submitRebuildTasks(ChunkUpdateType updateType, Camera camera, boolean asynchronous) {
        var budget = asynchronous ? this.builder.getSchedulingBudget() : Integer.MAX_VALUE;
        var iterator = this.updateScheduler.getSortedEntries(updateType, camera);

        while (budget > 0 && iterator.hasNext()) {
            var sectionKey = iterator.nextLong();
            var section = this.sectionByPosition.get(sectionKey);

            if (section == null) {
                throw new IllegalStateException();
            }

            this.submitRebuildTask(section, asynchronous);
            budget--;
        }
    }

    private void submitRebuildTask(RenderSection section, boolean asynchronous) {
        int frame = this.lastUpdatedFrame;
        ChunkBuilderMeshingTask task = this.createRebuildTask(section, frame);

        CancellationToken token = null;

        if (task != null) {
            token = this.builder.scheduleTask(task, asynchronous, this.buildResults::add);
        }

        this.updateScheduler.onSectionSubmitted(section, token, frame);

        if (token == null) {
            var empty = new ChunkBuildOutput(section, BuiltSectionInfo.EMPTY, Collections.emptyMap(), frame);
            var result = ChunkJobResult.successfully(empty);

            this.buildResults.add(result);
        }
    }

    public @Nullable ChunkBuilderMeshingTask createRebuildTask(RenderSection render, int frame) {
        ChunkRenderContext context = WorldSlice.prepare(this.world, render.getChunkPos(), this.sectionCache);

        if (context == null) {
            return null;
        }

        return new ChunkBuilderMeshingTask(render, context, frame);
    }

    public void markGraphDirty() {
        this.needsUpdate = true;
    }

    public boolean needsUpdate() {
        return this.needsUpdate;
    }

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    public void destroy() {
        this.builder.shutdown(); // stop all the workers, and cancel any tasks

        for (var result : this.collectChunkBuildResults()) {
            result.delete(); // delete resources for any pending tasks (including those that were cancelled)
        }

        this.sectionsWithGlobalEntities.clear();
        this.renderLists = SortedRenderLists.empty();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.regions.delete(commandList);
            this.chunkRenderer.delete(commandList);
        }

        this.updateScheduler.destroy();
    }

    public int getTotalSections() {
        return this.sectionByPosition.size();
    }

    public int getVisibleChunkCount() {
        var sections = 0;
        var iterator = this.renderLists.iterator();

        while (iterator.hasNext()) {
            var renderList = iterator.next();
            sections += renderList.getSectionsWithGeometryCount();
        }

        return sections;
    }

    public void scheduleRebuild(int x, int y, int z, boolean important) {
        this.sectionCache.invalidate(x, y, z);

        RenderSection section = this.sectionByPosition.get(ChunkSectionPos.asLong(x, y, z));

        if (section != null && section.isBuilt()) {
            ChunkUpdateType updateType;

            // TODO: Fix me
            if (false && important) {
                updateType = ChunkUpdateType.IMPORTANT_REBUILD;
            } else {
                updateType = ChunkUpdateType.REBUILD;
            }

            this.updateScheduler.markForUpdate(section, updateType, this.lastUpdatedFrame);
        }

        this.needsUpdate = true;
    }

    private float getEffectiveRenderDistance() {
        var color = RenderSystem.getShaderFogColor();
        var distance = RenderSystem.getShaderFogEnd();

        // The fog must be fully opaque in order to skip rendering of chunks behind it
        if (!MathHelper.approximatelyEquals(color[3], 1.0f)) {
            return this.renderDistance;
        }

        return Math.max(16.0f, distance);
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sectionByPosition.get(ChunkSectionPos.asLong(x, y, z));
    }

    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>();

        int count = 0;

        long deviceUsed = 0;
        long deviceAllocated = 0;

        for (var region : this.regions.getLoadedRegions()) {
            var resources = region.getResources();

            if (resources == null) {
                continue;
            }

            var buffer = resources.getGeometryArena();

            deviceUsed += buffer.getDeviceUsedMemory();
            deviceAllocated += buffer.getDeviceAllocatedMemory();

            count++;
        }

        list.add(String.format("Device buffer objects: %d", count));
        list.add(String.format("Device memory: %d/%d MiB", MathUtil.toMib(deviceUsed), MathUtil.toMib(deviceAllocated)));
        list.add(String.format("Staging buffer: %s", this.regions.getStagingBuffer().toString()));

        list.add(String.format("Chunk builder: P=%02d | A=%02d | I=%02d",
                this.builder.getScheduledJobCount(), this.builder.getBusyThreadCount(), this.builder.getTotalThreadCount())
        );

        list.add(String.format("Chunk updates: U=%02d (%s)", this.buildResults.size(), this.updateScheduler.getDebugString()));

        return list;
    }

    public @NotNull SortedRenderLists getRenderLists() {
        return this.renderLists;
    }

    public boolean isSectionBuilt(int x, int y, int z) {
        var section = this.getRenderSection(x, y, z);
        return section != null && section.isBuilt();
    }

    public void onChunkAdded(int x, int z) {
        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            this.onSectionAdded(x, y, z);
        }
    }

    public void onChunkRemoved(int x, int z) {
        for (int y = this.world.getBottomSectionCoord(); y < this.world.getTopSectionCoord(); y++) {
            this.onSectionRemoved(x, y, z);
        }
    }

    public Collection<RenderSection> getSectionsWithGlobalEntities() {
        return ReferenceSets.unmodifiable(this.sectionsWithGlobalEntities);
    }
}
