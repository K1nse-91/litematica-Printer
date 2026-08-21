package me.K1nse_.litematica.printer.handler.scan;

import me.K1nse_.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AsyncScanCandidatePlanner {
    private static final int MAX_PLANNED_CANDIDATES = 2048;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "litematica-printer-scan-planner");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, List<ScanSnapshot>> completed = new ConcurrentHashMap<>();

    void submit(String ownerKey, List<ScanSnapshot> snapshots, double eyeX, double eyeY, double eyeZ) {
        if (snapshots.isEmpty() || !this.running.add(ownerKey)) {
            return;
        }
        List<ScanSnapshot> copy = new ArrayList<>(snapshots);
        this.executor.execute(() -> {
            try {
                copy.sort(Comparator.comparingDouble(snapshot -> snapshot.distanceToSqr(eyeX, eyeY, eyeZ)));
                List<ScanSnapshot> planned = copy;
                if (copy.size() > MAX_PLANNED_CANDIDATES) {
                    planned = new ArrayList<>(copy.subList(0, MAX_PLANNED_CANDIDATES));
                }
                this.completed.put(ownerKey, planned);
            } finally {
                this.running.remove(ownerKey);
            }
        });
    }

    List<BlockPos> take(String ownerKey, PrinterBox box, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<ScanSnapshot> snapshots = this.completed.remove(ownerKey);
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<BlockPos> positions = new ArrayList<>(Math.min(limit, snapshots.size()));
        for (ScanSnapshot snapshot : snapshots) {
            if (positions.size() >= limit) {
                break;
            }
            if (box.contains(snapshot.x(), snapshot.y(), snapshot.z())) {
                positions.add(snapshot.blockPos());
            }
        }
        return positions;
    }

    void invalidateSection(int sectionX, int sectionY, int sectionZ) {
        for (Map.Entry<String, List<ScanSnapshot>> entry : this.completed.entrySet()) {
            List<ScanSnapshot> snapshots = entry.getValue();
            List<ScanSnapshot> filtered = new ArrayList<>(snapshots.size());
            for (ScanSnapshot snapshot : snapshots) {
                if (sectionCoord(snapshot.x()) != sectionX
                        || sectionCoord(snapshot.y()) != sectionY
                        || sectionCoord(snapshot.z()) != sectionZ) {
                    filtered.add(snapshot);
                }
            }
            if (filtered.isEmpty()) {
                this.completed.remove(entry.getKey(), snapshots);
            } else if (filtered.size() != snapshots.size()) {
                this.completed.replace(entry.getKey(), snapshots, filtered);
            }
        }
    }

    void clear() {
        this.completed.clear();
        this.running.clear();
    }

    private static int sectionCoord(int blockCoord) {
        return blockCoord >> 4;
    }
}
