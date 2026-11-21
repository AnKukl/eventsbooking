package com.example.eventsbooking.cleanup;

import com.example.eventsbooking.storage.EventImageRepository;
import com.example.eventsbooking.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Orphan cleanup against a real PostgreSQL and a real directory, with a clock the test controls. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrphanFileCleanupIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Duration GRACE = Duration.ofHours(24);
    private static final Instant OLD = NOW.minus(GRACE).minus(Duration.ofHours(1));
    private static final Instant FRESH = NOW.minus(Duration.ofHours(1));

    private static final String OLD_ORPHAN = "11111111-1111-1111-1111-111111111111.png";
    private static final String FRESH_ORPHAN = "22222222-2222-2222-2222-222222222222.png";
    private static final String LIVE = "33333333-3333-3333-3333-333333333333.jpg";
    private static final String OLD_PENDING = "44444444-4444-4444-4444-444444444444.webp";
    private static final String FRESH_PENDING = "55555555-5555-5555-5555-555555555555.png";
    private static final String MISSING = "66666666-6666-6666-6666-666666666666.png";
    private static final String OLD_TEMPORARY = "77777777-7777-7777-7777-777777777777.tmp";

    @TempDir
    Path root;

    private final EventImageRepository images;
    private final JdbcTemplate jdbc;
    private final MutableClock clock = new MutableClock(NOW);
    private SimpleMeterRegistry meters;

    OrphanFileCleanupIntegrationTest(EventImageRepository images, JdbcTemplate jdbc) {
        this.images = images;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clean() {
        jdbc.update("delete from event_images");
        meters = new SimpleMeterRegistry();
    }

    private OrphanFileCleanup cleanup(boolean dryRun) {
        CleanupProperties.Orphans settings = new CleanupProperties.Orphans("0 30 3 * * *", GRACE, dryRun, 2);
        return new OrphanFileCleanup(images, root, clock, settings, meters);
    }

    @Test
    void anOldFileWithoutARowIsRemovedAndAFreshOneStays() throws IOException {
        file(OLD_ORPHAN, OLD);
        file(FRESH_ORPHAN, FRESH);

        OrphanFileCleanup.Result result = cleanup(false).run();

        assertThat(root.resolve(OLD_ORPHAN)).doesNotExist();
        assertThat(root.resolve(FRESH_ORPHAN)).exists();
        assertThat(result.orphanFiles()).isEqualTo(1);
        assertThat(meters.counter("cleanup.orphans.files.deleted").count()).isEqualTo(1.0);
    }

    @Test
    void anOldFileWithALiveRowStays() throws IOException {
        file(LIVE, OLD);
        row(LIVE, "ATTACHED", OLD);

        cleanup(false).run();

        assertThat(root.resolve(LIVE)).exists();
        assertThat(keys()).containsExactly(LIVE);
    }

    @Test
    void aPendingRowOlderThanTheGraceGoesTogetherWithItsFile() throws IOException {
        file(OLD_PENDING, OLD);
        row(OLD_PENDING, "PENDING", OLD);
        file(FRESH_PENDING, FRESH);
        row(FRESH_PENDING, "PENDING", FRESH);

        OrphanFileCleanup.Result result = cleanup(false).run();

        assertThat(root.resolve(OLD_PENDING)).doesNotExist();
        assertThat(root.resolve(FRESH_PENDING)).exists();
        assertThat(keys()).containsExactly(FRESH_PENDING);
        assertThat(result.pendingRows()).isEqualTo(1);
    }

    @Test
    void manyOldPendingRowsAreRemovedAcrossBatches() throws IOException {
        for (int i = 0; i < 5; i++) {
            String key = "8888888" + i + "-8888-8888-8888-888888888888.png";
            file(key, OLD);
            row(key, "PENDING", OLD);
        }

        OrphanFileCleanup.Result result = cleanup(false).run();   // batch size 2

        assertThat(result.pendingRows()).isEqualTo(5);
        assertThat(keys()).isEmpty();
        assertThat(filesIn(root)).isZero();
    }

    @Test
    void anOldTemporaryFileOfAnInterruptedUploadIsRemoved() throws IOException {
        file(OLD_TEMPORARY, OLD);

        cleanup(false).run();

        assertThat(root.resolve(OLD_TEMPORARY)).doesNotExist();
    }

    @Test
    void anAttachedRowWithoutAFileIsOnlyReported() {
        row(MISSING, "ATTACHED", OLD);

        OrphanFileCleanup.Result result = cleanup(false).run();

        assertThat(keys()).containsExactly(MISSING);
        assertThat(result.attachedWithoutFile()).isEqualTo(1);
    }

    @Test
    void aDryRunCountsButRemovesNothing() throws IOException {
        file(OLD_ORPHAN, OLD);
        file(OLD_PENDING, OLD);
        row(OLD_PENDING, "PENDING", OLD);

        OrphanFileCleanup.Result result = cleanup(true).run();

        assertThat(result.orphanFiles()).isEqualTo(1);
        assertThat(result.pendingRows()).isEqualTo(1);
        assertThat(root.resolve(OLD_ORPHAN)).exists();
        assertThat(root.resolve(OLD_PENDING)).exists();
        assertThat(keys()).containsExactly(OLD_PENDING);
        assertThat(meters.counter("cleanup.orphans.files.deleted").count()).isZero();
    }

    @Test
    void filesInSubdirectoriesAreCheckedByTheirPathInsideTheStorage() throws IOException {
        Files.createDirectories(root.resolve("2030"));
        file("2030/" + OLD_ORPHAN, OLD);
        file("2030/" + LIVE, OLD);
        row("2030/" + LIVE, "ATTACHED", OLD);

        cleanup(false).run();

        assertThat(root.resolve("2030/" + OLD_ORPHAN)).doesNotExist();
        assertThat(root.resolve("2030/" + LIVE)).exists();
    }

    @Test
    void aSymbolicLinkIsNeitherFollowedNorRemoved(@TempDir Path outside) throws IOException {
        Path target = outside.resolve("precious.png");
        Files.write(target, new byte[] {1});
        Files.setLastModifiedTime(target, FileTime.from(OLD));
        Path link = root.resolve(OLD_ORPHAN);
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symbolic links are not available here: " + e.getMessage());
        }

        cleanup(false).run();

        assertThat(target).exists();
        assertThat(Files.isSymbolicLink(link)).isTrue();
    }

    @Test
    void anEmptyStorageAndAnEmptyTableAreFine() {
        OrphanFileCleanup.Result result = cleanup(false).run();

        assertThat(result.orphanFiles()).isZero();
        assertThat(result.pendingRows()).isZero();
    }

    @Test
    void aStorageDirectoryThatDoesNotExistYetIsFine() {
        CleanupProperties.Orphans settings = new CleanupProperties.Orphans("0 30 3 * * *", GRACE, false, 2);
        OrphanFileCleanup missing = new OrphanFileCleanup(images, root.resolve("not-created"), clock, settings, meters);

        assertThat(missing.run().orphanFiles()).isZero();
    }

    private void file(String key, Instant modified) throws IOException {
        Path file = root.resolve(key);
        Files.write(file, new byte[] {1, 2, 3});
        Files.setLastModifiedTime(file, FileTime.from(modified));
    }

    private void row(String key, String status, Instant createdAt) {
        jdbc.update("insert into event_images (storage_key, status, created_at) values (?, ?, ?)",
                key, status, Timestamp.from(createdAt));
    }

    private java.util.List<String> keys() {
        return jdbc.queryForList("select storage_key from event_images order by storage_key", String.class);
    }

    private static long filesIn(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.count();
        }
    }
}
