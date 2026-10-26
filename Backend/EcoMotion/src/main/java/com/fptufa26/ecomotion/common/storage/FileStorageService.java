package com.fptufa26.ecomotion.common.storage;

public interface FileStorageService {

    String store(String entity, Long userId, String filename, byte[] content, String contentType);

    byte[] read(String storedPath);

    void delete(String storedPath);
}
