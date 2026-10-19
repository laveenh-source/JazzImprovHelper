package dev.laveenh.jazzanalyzer.storage;

/**
 * Where uploaded charts are kept. The rest of the app only knows this interface, so the storage could change
 * (for example to a cloud bucket) without touching anything else. Today there is one implementation: local disk.
 */
public interface FileStorage {

    /** Stores the content under a freshly generated key and returns that key. File names are never used. */
    String store(byte[] content);

    /** @throws StorageException if the key does not exist or cannot be read */
    byte[] read(String key);

    /** Deletes the file; deleting a key that is already gone is not an error. */
    void delete(String key);
}
