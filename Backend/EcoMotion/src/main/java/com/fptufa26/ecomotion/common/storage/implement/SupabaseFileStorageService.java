package com.fptufa26.ecomotion.common.storage.implement;

import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.common.storage.StorageObjectPath;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "supabase")
public class SupabaseFileStorageService implements FileStorageService {

    private final RestClient restClient;
    private final String baseUrl;
    private final String bucket;
    private final String serviceKey;
    private final long maxBytes;

    public SupabaseFileStorageService(
            @Value("${app.upload.supabase.url:}") String baseUrl,
            @Value("${app.upload.supabase.bucket:}") String bucket,
            @Value("${app.upload.supabase.service-key:}") String serviceKey,
            @Value("${app.upload.max-file-size-bytes:8388608}") long maxBytes,
            @Value("${app.upload.supabase.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.upload.supabase.read-timeout:20s}") Duration readTimeout) {
        if (baseUrl == null || baseUrl.isBlank() || bucket == null || !bucket.matches("[a-zA-Z0-9._-]+")
                || serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("Supabase storage requires url, bucket and service-key");
        }
        URI uri = URI.create(baseUrl);
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp)
                || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Supabase storage URL must be an HTTPS project origin");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.bucket = bucket;
        this.serviceKey = serviceKey;
        this.maxBytes = maxBytes;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String store(String entity, Long userId, String filename, byte[] content, String contentType) {
        String key = StorageObjectPath.forUpload(entity, userId, filename, content, contentType, maxBytes);
        try {
            restClient.post()
                    .uri(objectUri(key, false))
                    .header("apikey", serviceKey)
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("x-upsert", "true")
                    .header("Content-Type", contentType)
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
            return key;
        } catch (RestClientException ex) {
            throw new FileStorageException("Could not upload the file to Supabase Storage", ex);
        }
    }

    @Override
    public byte[] read(String storedPath) {
        String key = StorageObjectPath.validateStoredPath(storedPath);
        try {
            byte[] content = restClient.get()
                    .uri(objectUri(key, true))
                    .header("apikey", serviceKey)
                    .header("Authorization", "Bearer " + serviceKey)
                    .retrieve()
                    .body(byte[].class);
            if (content == null) {
                throw new FileStorageException("Supabase Storage returned an empty file");
            }
            return content;
        } catch (RestClientException ex) {
            throw new FileStorageException("Could not read the file from Supabase Storage", ex);
        }
    }

    private URI objectUri(String key, boolean authenticated) {
        String route = authenticated ? "/storage/v1/object/authenticated/" : "/storage/v1/object/";
        return URI.create(baseUrl + route + bucket + "/" + key);
    }
}
