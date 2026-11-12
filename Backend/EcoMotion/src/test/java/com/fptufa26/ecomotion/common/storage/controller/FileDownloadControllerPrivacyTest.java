package com.fptufa26.ecomotion.common.storage.controller;

import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileDownloadControllerPrivacyTest {
    @Test void licenseEvidenceCannotBeReadThroughPublicFileEndpointIncludingDownloadAlias() {
        var storage=mock(FileStorageService.class);
        var controller=new FileDownloadController(storage);
        for(String name: java.util.List.of("driving-license-front-test.png","driving-license-back-test.jpg",
                "license-review-front-test.png","license-review-back-test.jpg")) {
            for(String alias:java.util.List.of("","download/")) {
                var request=new MockHttpServletRequest("GET","/api/files/"+alias+"renter/1/"+name);
                assertEquals(HttpStatus.NOT_FOUND,controller.viewFile(request).getStatusCode());
            }
        }
        verifyNoInteractions(storage);
        when(storage.read("vehicle/1/public.png")).thenReturn(new byte[]{1,2,3});
        assertEquals(HttpStatus.OK,controller.viewFile(new MockHttpServletRequest("GET","/api/files/vehicle/1/public.png")).getStatusCode());
    }
}
