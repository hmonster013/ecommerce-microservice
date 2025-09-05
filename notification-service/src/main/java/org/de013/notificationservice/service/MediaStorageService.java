package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Service for managing media storage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaStorageService {

    @Value("${notification.media.storage.path:/tmp/notification-media}")
    private String storagePath;

    @Value("${notification.media.base-url:http://localhost:8080/api/v1/media}")
    private String baseUrl;

    /**
     * Upload file to storage
     */
    public String uploadFile(MultipartFile file) throws IOException {
        log.info("Uploading file: {}", file.getOriginalFilename());

        try {
            // Create storage directory if it doesn't exist
            Path storageDir = Paths.get(storagePath);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            // Save file
            Path targetPath = storageDir.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Return URL
            String fileUrl = baseUrl + "/" + uniqueFilename;
            log.info("File uploaded successfully: {} -> {}", originalFilename, fileUrl);
            
            return fileUrl;

        } catch (IOException e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete file from storage
     */
    public void deleteFile(String fileUrl) {
        try {
            String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            Path filePath = Paths.get(storagePath, filename);
            
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("File deleted successfully: {}", filename);
            }

        } catch (IOException e) {
            log.error("Error deleting file: {}", e.getMessage(), e);
        }
    }
}
