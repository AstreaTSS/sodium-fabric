package me.jellysquid.mods.sodium.client.render.chunk.scheduler;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;
import net.minecraft.client.render.Camera;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public class ChunkUpdateScheduler {
    private final EnumMap<ChunkUpdateType, ChunkUpdateQueue> queues = new EnumMap<>(ChunkUpdateType.class);
    private final Reference2ReferenceMap<RenderSection, ScheduledEntry> scheduled = new Reference2ReferenceOpenHashMap<>();

    public ChunkUpdateScheduler() {
        for (ChunkUpdateType type : ChunkUpdateType.values()) {
            this.queues.put(type, new ChunkUpdateQueue());
        }
    }

    public void markForUpdate(RenderSection section, ChunkUpdateType type, int frame) {
        var entry = this.scheduled.get(section);

        if (entry == null) {
            this.scheduled.put(section, entry = new ScheduledEntry());
        }

        // only place it into a queue if not already building
        if (entry.buildCancellationToken == null) {
            // remove it from the queue it was already in, if any
            if (entry.pendingUpdateType != null) {
                this.queues.get(entry.pendingUpdateType)
                        .remove(section.getGlobalCoord());
            }

            // add it to the queue for the new type
            this.queues.get(type)
                    .add(section.getGlobalCoord());
        }

        entry.pendingUpdateType = type;
        entry.lastScheduledTime = frame;
    }

    public void cancelScheduledBuild(RenderSection section) {
        var entry = this.scheduled.remove(section);

        if (entry == null) {
            return;
        }

        // remove it from the existing queue, if any
        if (entry.pendingUpdateType != null) {
            this.queues.get(entry.pendingUpdateType)
                    .remove(section.getGlobalCoord());
            entry.pendingUpdateType = null;
        }

        // cancel the executing build task, if any
        if (entry.buildCancellationToken != null) {
            entry.buildCancellationToken.setCancelled();
            entry.buildCancellationToken = null;
        }
    }

    public LongIterator getSortedEntries(ChunkUpdateType type, Camera camera) {
        return this.queues.get(type)
                .getSortedList(camera);
    }

    public void destroy() {
        for (var entry : this.scheduled.values()) {
            if (entry.buildCancellationToken != null) {
                entry.buildCancellationToken.setCancelled();
                entry.buildCancellationToken = null;
            }
        }

        this.scheduled.clear();

        for (var queue : this.queues.values()) {
            queue.clear();
        }
    }

    public String getDebugString() {
        return String.format("P0=%03d | P1=%03d | P2=%05d",
                this.getQueueSize(ChunkUpdateType.IMPORTANT_REBUILD),
                this.getQueueSize(ChunkUpdateType.REBUILD),
                this.getQueueSize(ChunkUpdateType.INITIAL_BUILD));
    }

    private int getQueueSize(ChunkUpdateType type) {
        return this.queues.get(type)
                .size();
    }

    public void onSectionUploaded(RenderSection render, int frame) {
        var entry = this.scheduled.get(render);

        if (entry == null) {
            throw new IllegalStateException("Build task was uploaded, but it wasn't scheduled");
        }

        if (frame >= entry.lastSubmittedTime) {
            entry.buildCancellationToken = null;

            if (frame >= entry.lastScheduledTime) {
                // Another update was scheduled since this rebuild was submitted
                if (entry.pendingUpdateType != null) {
                    this.queues.get(entry.pendingUpdateType)
                            .add(render.getGlobalCoord());
                } else {
                    this.scheduled.remove(render);
                }
            }
        }
    }

    public void onSectionSubmitted(RenderSection section, @Nullable CancellationToken token, int frame) {
        var entry = this.scheduled.get(section);

        if (entry == null || entry.pendingUpdateType == null) {
            throw new IllegalStateException("The render section has no rebuilds scheduled");
        }

        if (entry.buildCancellationToken != null) {
            throw new IllegalStateException("The render section already has a rebuild which is executing");
        }

        if (entry.lastSubmittedTime > frame) {
            throw new IllegalStateException("Tried to submit a late build task");
        }

        entry.buildCancellationToken = token;
        entry.lastSubmittedTime = frame;

        this.queues.get(entry.pendingUpdateType)
                .remove(section.getGlobalCoord());
        entry.pendingUpdateType = null;
    }

    private static class ScheduledEntry {
        @Nullable
        private ChunkUpdateType pendingUpdateType; // the queue which we are currently in

        @Nullable
        private CancellationToken buildCancellationToken; // the cancellation token for the currently executing build

        private int lastScheduledTime = -1; // the last time this chunk was scheduled for updating
        private int lastSubmittedTime = -1; // the last time a build task was created
    }
}
