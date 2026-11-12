package com.fptufa26.ecomotion.verification.license;

import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.verification.documentai.DocumentAiReadResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequiredArgsConstructor
public class DrivingLicenseReviewController {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(DrivingLicenseReviewController.class);

    private final DrivingLicenseReviewService reviews;
    private final LicenseProcessingStore processing;
    @GetMapping("/api/renter/driving-license/submissions/current") @PreAuthorize("hasRole('RENTER')")
    public DrivingLicenseReviewService.View current(@AuthenticationPrincipal SecurityUser user){return reviews.current(user.getUserId());}
    @GetMapping("/api/renter/driving-license/submissions") @PreAuthorize("hasRole('RENTER')")
    public List<DrivingLicenseReviewService.View> history(@AuthenticationPrincipal SecurityUser user, @RequestParam(defaultValue="0") int page) { return reviews.history(user.getUserId(), page); }
    @PostMapping("/api/renter/driving-license/submissions") @PreAuthorize("hasRole('RENTER')")
    public DrivingLicenseReviewService.View prepare(@AuthenticationPrincipal SecurityUser user,@RequestParam MultipartFile front,@RequestParam MultipartFile back,@RequestParam(defaultValue="false") boolean noQrDeclared,
            @RequestHeader(value="X-Processing-ID",required=false) UUID processingId) {
        UUID id=processingId==null?UUID.randomUUID():processingId;
        Long owner=user.getUserId();processing.begin(owner,id);
        try (var context=com.fptufa26.ecomotion.verification.progress.AiProgressContext.open(id,snapshot -> processing.update(owner,id,snapshot))) {
            var result=reviews.prepare(owner,front,back,noQrDeclared);
            try { processing.finish(owner,id,null); }
            catch(RuntimeException traceError) { LOG.error("License result saved but progress finalization failed: operation={}, type={}",id,traceError.getClass().getSimpleName()); }
            return result;
        } catch (RuntimeException error) {
            String code=error instanceof com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException ai ? ai.getErrorCode()
                : error instanceof com.fptufa26.ecomotion.verification.documentai.DocumentAiUnavailableException ? "DOCUMENT_AI_UNAVAILABLE"
                : "LICENSE_PROCESSING_FAILED";
            var last=com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(id);
            if(last==null) { try { last=processing.get(owner,id).aiProgress(); } catch(RuntimeException unavailable) { error.addSuppressed(unavailable); } }
            LOG.error("License preparation failed: operation={}, code={}, stage={}, exception={}, cause={}, location={}",id,code,last==null?null:last.stage(),error.getClass().getName(),error.getCause()==null?null:error.getCause().getClass().getName(),error.getStackTrace().length==0?null:error.getStackTrace()[0]);
            try { processing.finish(owner,id,code); } catch(RuntimeException traceError) { error.addSuppressed(traceError); }
            throw error;
        }
    }
    @GetMapping("/api/renter/driving-license/submissions/processing/current") @PreAuthorize("hasRole('RENTER')")
    public LicenseProcessingStore.View processingCurrent(@AuthenticationPrincipal SecurityUser user){return processing.current(user.getUserId());}
    @GetMapping("/api/renter/driving-license/submissions/processing/{id}") @PreAuthorize("hasRole('RENTER')")
    public LicenseProcessingStore.View progress(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id){return processing.get(user.getUserId(),id);}
    @GetMapping("/api/renter/driving-license/submissions/processing/history") @PreAuthorize("hasRole('RENTER')")
    public java.util.List<com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService.View> pipelineHistory(@AuthenticationPrincipal SecurityUser user,@RequestParam(defaultValue="0") int page){return processing.history(user.getUserId(),page);}
    @GetMapping("/api/admin/driving-license-submissions/{id}/identity-images/{side}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> identityImage(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id,@PathVariable String side){return image(reviews.applicantIdentityImage(user.getUserId(),id,side));}
    public record Submit(@NotNull @Min(0) Long version,boolean confirmed){}
    @PostMapping("/api/renter/driving-license/submissions/{id}/submit") @PreAuthorize("hasRole('RENTER')")
    public DrivingLicenseReviewService.View submit(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id,@Valid @RequestBody Submit request){return reviews.submit(user.getUserId(),id,request.version(),request.confirmed());}
    @PostMapping("/api/renter/driving-license/submissions/{id}/cancel") @PreAuthorize("hasRole('RENTER')")
    public DrivingLicenseReviewService.View cancel(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id,@Valid @RequestBody Submit request){return reviews.cancel(user.getUserId(),id,request.version());}
    @GetMapping("/api/admin/driving-license-submissions") @PreAuthorize("hasRole('ADMIN')")
    public List<DrivingLicenseReviewService.View> queue(@AuthenticationPrincipal SecurityUser user,@RequestParam(defaultValue="SUBMITTED")String status,@RequestParam(defaultValue="0")int page){return reviews.pending(user.getUserId(),status,page);}
    @GetMapping("/api/admin/driving-license-submissions/{id}") @PreAuthorize("hasRole('ADMIN')")
    public DrivingLicenseReviewService.View detail(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id){return reviews.detail(user.getUserId(),id);}
    @GetMapping("/api/admin/driving-license-submissions/{id}/applicant") @PreAuthorize("hasRole('ADMIN')")
    public DrivingLicenseReviewService.Applicant applicant(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id){return reviews.applicant(user.getUserId(),id);}
    public record Decision(@NotNull @Min(0)Long version,@NotBlank String action,String reason,@Size(max=1000)String note,DocumentAiReadResult document){}
    @PostMapping("/api/admin/driving-license-submissions/{id}/review") @PreAuthorize("hasRole('ADMIN')")
    public DrivingLicenseReviewService.View review(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id,@Valid @RequestBody Decision request){return reviews.review(user.getUserId(),id,request.version(),request.action(),request.reason(),request.note(),request.document());}
    @GetMapping("/api/renter/driving-license/submissions/{id}/images/{side}") @PreAuthorize("hasRole('RENTER')")
    public ResponseEntity<byte[]> ownImage(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id,@PathVariable String side){return image(reviews.evidence(user.getUserId(),id,side,false));}
    @GetMapping("/api/admin/driving-license-submissions/{id}/images/{side}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> adminImage(@AuthenticationPrincipal SecurityUser user,@PathVariable UUID id,@PathVariable String side){return image(reviews.evidence(user.getUserId(),id,side,true));}
    private ResponseEntity<byte[]> image(DrivingLicenseReviewService.Image image){return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.type())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(image.content());}
}
