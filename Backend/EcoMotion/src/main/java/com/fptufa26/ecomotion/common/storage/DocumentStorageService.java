package com.fptufa26.ecomotion.common.storage;

public interface DocumentStorageService {

    String store(
            String entity,
            Long userId,
            String filename,
            byte[] content,
            String contentType);

    byte[] read(String storedPath);
}