package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundProofResponse;
import com.fptufa26.ecomotion.finance.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/v1/payment-transactions", "/api/payment-transactions"})
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class RefundController {

    private final RefundService refundService;

    @PostMapping(value = "/{id}/refund-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ApiResponse<RefundProofResponse> completeRefund(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam String bankReference,
            @RequestParam(required = false) String note) {
        return ApiResponse.success(refundService.completeRefund(principal.getUserId(), id, file, bankReference, note));
    }

    @GetMapping("/{id}/refund-proof")
    public ResponseEntity<byte[]> viewRefundProof(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id) {
        PaymentEvidenceFileResponse file = refundService.getProof(principal.getUserId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
