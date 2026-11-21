package com.example.eventsbooking.cleanup;

import com.example.eventsbooking.storage.EventImageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds uploads that never became part of an event and files nothing points to.
 *
 * <ol>
 *   <li>Database pass: PENDING rows older than the grace period. The row is removed first, and only while it is still
 *       PENDING; its file goes only after that, so an upload attached at the same moment keeps its file.</li>
 *   <li>File pass: files older than the grace period (by modification time) that have no row, checked by key in
 *       batches. Symbolic links are skipped, not followed, and every path must lie inside the storage directory.</li>
 *   <li>ATTACHED rows whose file is gone are only reported: an event refers to them, so a person has to decide.</li>
 * </ol>
 *
 * In dry-run mode every pass only counts.
 */
public class OrphanFileCleanup {

    private static final Logger log = LoggerFactory.getLogger(OrphanFileCleanup.class);

    /** What one run found (in dry-run mode: what it would have removed). */
    public record Result(int pendingRows, int orphanFiles, int attachedWithoutFile, boolean dryRun) {
    }

    private final EventImageRepository images;
    private final Path root;
    private final Clock clock;
    private final CleanupProperties.Orphans settings;
    private final Counter pendingDeleted;
    private final Counter filesDeleted;
    private final Counter attachedMissing;

    public OrphanFileCleanup(EventImageRepository images, Path root, Clock clock, CleanupProperties.Orphans settings,
            MeterRegistry meters) {
        this.images = images;
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
        this.settings = settings;
        this.pendingDeleted = Counter.builder("cleanup.orphans.pending.deleted")
                .description("PENDING uploads older than the grace period removed with their files").register(meters);
        this.filesDeleted = Counter.builder("cleanup.orphans.files.deleted")
                .description("Files without a row removed by the orphan cleanup").register(meters);
        this.attachedMissing = Counter.builder("cleanup.orphans.attached.missing")
                .description("ATTACHED rows found without their file (reported, not removed)").register(meters);
    }

    public Result run() {
        Instant cutoff = clock.instant().minus(settings.grace());
        int pending = removeOldPendingRows(cutoff);
        int files = removeFilesWithoutRows(cutoff);
        int missing = reportAttachedWithoutFile();
        log.info("Orphan cleanup finished{}: {} old PENDING uploads, {} files without a row, {} ATTACHED rows without a file",
                settings.dryRun() ? " (dry run, nothing removed)" : "", pending, files, missing);
        return new Result(pending, files, missing, settings.dryRun());
    }

    private int removeOldPendingRows(Instant cutoff) {
        int count = 0;
        long afterId = 0;
        List<EventImageRepository.Row> batch;
        do {
            batch = images.findPendingCreatedBefore(cutoff, afterId, settings.batchSize());
            for (EventImageRepository.Row row : batch) {
                afterId = row.id();
                if (settings.dryRun()) {
                    count++;
                } else if (images.deleteIfPending(row.id())) {
                    count++;
                    pendingDeleted.increment();
                    resolve(row.storageKey()).ifPresent(this::deleteQuietly);
                }
            }
        } while (batch.size() == settings.batchSize());
        return count;
    }

    private int removeFilesWithoutRows(Instant cutoff) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        int[] count = {0};
        List<Path> candidates = new ArrayList<>(settings.batchSize());
        // Files.walk does not follow symbolic links unless asked to; the stream is consumed lazily
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isInsideRoot)
                    .filter(path -> modifiedBefore(path, cutoff))
                    .forEach(path -> {
                        candidates.add(path);
                        if (candidates.size() == settings.batchSize()) {
                            count[0] += removeUnknown(candidates);
                            candidates.clear();
                        }
                    });
        } catch (IOException | UncheckedIOException e) {
            log.error("Orphan cleanup could not walk {}", root, e);
        }
        count[0] += removeUnknown(candidates);
        return count[0];
    }

    private int removeUnknown(List<Path> candidates) {
        if (candidates.isEmpty()) {
            return 0;
        }
        List<String> keys = candidates.stream().map(this::keyOf).toList();
        Set<String> known = images.existingKeys(keys);
        int count = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (known.contains(keys.get(i))) {
                continue;
            }
            count++;
            if (settings.dryRun()) {
                log.debug("Would remove orphan file {}", keys.get(i));
            } else if (deleteQuietly(candidates.get(i))) {
                filesDeleted.increment();
            }
        }
        return count;
    }

    private int reportAttachedWithoutFile() {
        int count = 0;
        long afterId = 0;
        List<EventImageRepository.Row> batch;
        do {
            batch = images.findAttached(afterId, settings.batchSize());
            for (EventImageRepository.Row row : batch) {
                afterId = row.id();
                boolean present = resolve(row.storageKey())
                        .map(path -> Files.exists(path, LinkOption.NOFOLLOW_LINKS)).orElse(false);
                if (!present) {
                    count++;
                    attachedMissing.increment();
                    log.warn("Attached image {} (row {}) has no file; left as is", row.storageKey(), row.id());
                }
            }
        } while (batch.size() == settings.batchSize());
        return count;
    }

    /** The file of a key, or empty if the key would point outside the storage directory. */
    private java.util.Optional<Path> resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            log.warn("Storage key {} points outside the storage directory; skipped", key);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(path);
    }

    private boolean isInsideRoot(Path path) {
        return path.toAbsolutePath().normalize().startsWith(root);
    }

    private String keyOf(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static boolean modifiedBefore(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;   // vanished or unreadable: not ours to judge this time
        }
    }

    private boolean deleteQuietly(Path path) {
        try {
            if (Files.isSymbolicLink(path)) {
                return false;
            }
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove {}", path, e);
            return false;
        }
    }
}
