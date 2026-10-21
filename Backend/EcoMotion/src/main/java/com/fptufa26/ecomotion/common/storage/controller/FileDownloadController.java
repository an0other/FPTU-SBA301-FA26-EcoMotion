package com.fptufa26.ecomotion.common.storage.controller;

import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Download / Viewer", description = "Serves uploaded documents and images for browser viewing")
public class FileDownloadController {

    private final FileStorageService fileStorageService;

    @GetMapping("/**")
    @Operation(summary = "View uploaded document or image in browser tab")
    public ResponseEntity<byte[]> viewFile(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String prefix = "/api/files/";
        int index = requestUri.indexOf(prefix);
        if (index == -1) {
            return ResponseEntity.notFound().build();
        }

        String storedPath = requestUri.substring(index + prefix.length());
        if (storedPath.startsWith("download/")) {
            storedPath = storedPath.substring("download/".length());
        }
        if (storedPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] data = fileStorageService.read(storedPath);
            MediaType mediaType = determineMediaType(storedPath);
            String filename = storedPath.substring(storedPath.lastIndexOf('/') + 1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentDisposition(org.springframework.http.ContentDisposition.inline().filename(filename).build());
            headers.setCacheControl("public, max-age=86400");

            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (FileStorageException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private MediaType determineMediaType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        } else if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
