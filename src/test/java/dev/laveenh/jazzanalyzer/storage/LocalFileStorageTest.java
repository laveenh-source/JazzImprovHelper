package dev.laveenh.jazzanalyzer.storage;

import dev.laveenh.jazzanalyzer.config.JazzProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    private Path uploads;
    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        uploads = root.resolve("uploads");   // does not exist yet: the storage must create it
        storage = new LocalFileStorage(new JazzProperties(new JazzProperties.Upload(1), new JazzProperties.Storage(uploads)));
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void createsItsDirectoryAndStoresUnderAGeneratedKey() throws IOException {
        String key = storage.store(bytes("<xml/>"));

        assertThat(key).matches("[0-9a-f-]{36}\\.xml");
        assertThat(Files.readAllBytes(uploads.resolve(key))).isEqualTo(bytes("<xml/>"));
    }

    @Test
    void everyStoreGetsAFreshKey() {
        assertThat(storage.store(bytes("a"))).isNotEqualTo(storage.store(bytes("a")));
    }

    @Test
    void readsBackWhatWasStored() {
        String key = storage.store(bytes("hello"));
        assertThat(storage.read(key)).isEqualTo(bytes("hello"));
    }

    @Test
    void readingAMissingKeyFails() {
        assertThatThrownBy(() -> storage.read("nope.xml")).isInstanceOf(StorageException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteRemovesTheFileAndIsHarmlessTwice() {
        String key = storage.store(bytes("x"));
        storage.delete(key);
        storage.delete(key);
        assertThat(uploads.resolve(key)).doesNotExist();
    }

    @Test
    void refusesKeysThatEscapeTheDirectory() throws IOException {
        Path outside = root.resolve("secret.txt");
        Files.writeString(outside, "secret");

        assertThatThrownBy(() -> storage.read("../secret.txt")).isInstanceOf(StorageException.class)
                .hasMessageContaining("Invalid storage key");
        assertThatThrownBy(() -> storage.delete("../secret.txt")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.read(outside.toString())).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.read("")).isInstanceOf(StorageException.class);
        assertThat(outside).exists();
    }
}
