package dev.laveenh.jazzanalyzer.storage;

/**
 * Where uploaded charts are kept. The app only knows this interface; local disk is used in development
 * and S3 in production (selected by Spring profile), so nothing else changes between the two.
 */
public interface FileStorage {

    /** Stores the content under a freshly generated key and returns that key. File names are never used. */
    String store(byte[] content);

    /** @throws StorageException if the key does not exist or cannot be read */
    byte[] read(String key);

    /** Deletes the file; deleting a key that is already gone is not an error. */
    void delete(String key);
}
