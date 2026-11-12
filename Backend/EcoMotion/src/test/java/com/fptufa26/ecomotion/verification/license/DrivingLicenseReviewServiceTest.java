package com.fptufa26.ecomotion.verification.license;

import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import com.fptufa26.ecomotion.verification.documentai.*;
import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;
import com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DrivingLicenseReviewServiceTest {
    final DrivingLicenseSubmissionRepository submissions=mock(DrivingLicenseSubmissionRepository.class);
    final RenterRepository renters=mock(RenterRepository.class);
    final UserService users=mock(UserService.class);
    final DocumentAiClient ai=mock(DocumentAiClient.class);
    final FileStorageService storage=mock(FileStorageService.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC);
    final DrivingLicenseReviewService service=new DrivingLicenseReviewService(submissions,renters,users,ai,storage,new ObjectMapper(),clock);
    Renter renter; DrivingLicenseSubmission row;
    @BeforeEach void setup() {
        TransactionSynchronizationManager.initSynchronization();
        User user=new User();user.setId(1L);user.setRole(Role.RENTER);user.setStatus(UserStatus.ACTIVE);user.setEmail("renter@example.test");
        var identity=new PersonIdentity();identity.setStatus(PersonIdentityStatus.VERIFIED);identity.setFullName("Nguyễn Văn An");identity.setDateOfBirth(LocalDate.of(1990,1,1));user.setIdentity(identity);
        renter=new Renter();renter.setId(5L);renter.setUser(user);renter.setLicenseNo("OLD");renter.setLicenseVerified(true);
        User admin=new User();admin.setId(2L);admin.setRole(Role.ADMIN);admin.setStatus(UserStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(renters.findRenterByUser_Id(1L)).thenReturn(Optional.of(renter));when(renters.findByUser_Id(1L)).thenReturn(Optional.of(renter));
        when(storage.store(anyString(),anyLong(),anyString(),any(),anyString())).thenAnswer(i->i.getArgument(2));
        when(submissions.saveAndFlush(any())).thenAnswer(i->{row=i.getArgument(0);row.setId(10L);when(submissions.lock(row.getPublicId())).thenReturn(Optional.of(row));return row;});
        when(ai.readDrivingLicense(any(),anyString(),any(),anyString())).thenReturn(Map.of("documentType","VN_DRIVING_LICENSE","code","PROCESSED","status","PARTIALLY_READ",
                "data",Map.of("documentNumber","NEW","fullName","NGUYEN VAN AN"),"qrData",Map.of("status","DISABLED")));
    }
    @Test void finalProgressRefreshesAStaleManagedRowBeforeItsVersionedUpdate() {
        var repository=mock(com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunRepository.class);
        var entityManager=mock(jakarta.persistence.EntityManager.class);
        var progress=new com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService(repository,new ObjectMapper(),clock,entityManager);
        var operation=UUID.randomUUID();
        var trace=new com.fptufa26.ecomotion.verification.progress.VerificationPipelineRun();trace.setId(operation);trace.setOwnerUserId(1L);trace.setStatus("PROCESSING");trace.setVersion(0L);
        when(repository.findByIdAndOwnerUserId(operation,1L)).thenReturn(Optional.of(trace));
        doAnswer(invocation->{trace.setVersion(5L);return null;}).when(entityManager).refresh(trace,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        when(repository.saveAndFlush(trace)).thenAnswer(invocation->{assertEquals(5L,trace.getVersion());return trace;});
        progress.finish(1L,operation,"COMPLETED",null);
        assertEquals("COMPLETED",trace.getStatus());
        var order=inOrder(entityManager,repository);order.verify(repository).findByIdAndOwnerUserId(operation,1L);order.verify(entityManager).refresh(trace,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);order.verify(repository).saveAndFlush(trace);
    }

    @Test void savedLicenseIsReturnedEvenIfProgressFinalizationFails() {
        var reviews=mock(DrivingLicenseReviewService.class);
        var progress=mock(LicenseProcessingStore.class);
        var controller=new DrivingLicenseReviewController(reviews,progress);
        var owner=mock(com.fptufa26.ecomotion.common.security.SecurityUser.class);
        when(owner.getUserId()).thenReturn(1L);
        var id=UUID.randomUUID();
        var expected=new DrivingLicenseReviewService.View(UUID.randomUUID(),0L,"READY_TO_SUBMIT",false,1L,"demo@example.test",null,null,List.of(),clock.instant());
        when(reviews.prepare(eq(1L),any(),any(),eq(false))).thenReturn(expected);
        doThrow(new IllegalStateException("Trace unavailable")).when(progress).finish(1L,id,null);
        var file=new org.springframework.mock.web.MockMultipartFile("front","front.png","image/png",new byte[]{1});
        assertSame(expected,controller.prepare(owner,file,file,false,id));
        verify(progress,never()).finish(1L,id,"LICENSE_PROCESSING_FAILED");
    }

    @Test void liveProgressIsOwnerScopedAndCannotReplaceAnActiveOperation() {
        var progress=new LicenseProcessingStore(clock);
        var id=UUID.randomUUID();progress.begin(1L,id);
        assertEquals(id,progress.current(1L).attemptId());
        assertNull(progress.current(2L));
        var denied=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->progress.get(2L,id));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND,denied.getStatusCode());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->progress.begin(1L,UUID.randomUUID()));
        try(var context=com.fptufa26.ecomotion.verification.progress.AiProgressContext.open(id,snapshot->progress.update(1L,id,snapshot))) {
            com.fptufa26.ecomotion.verification.progress.AiProgressContext.update(Map.of("stage","READING_FIELDS","side","BACK"));
            assertEquals("BACK",progress.get(1L,id).aiProgress().side());
        }
        progress.finish(1L,id,null);
        assertEquals("COMPLETED",progress.get(1L,id).status());
        assertNull(progress.current(1L));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->progress.begin(1L,id));
    }
    @Test void onlyAdminCanReadApplicantIdentityForLicenseComparison() throws Exception {
        var candidate=service.prepare(1L,image(),image(),false);
        var applicant=service.applicant(2L,candidate.id());
        assertEquals("renter@example.test",applicant.email());
        assertEquals(LocalDate.of(1990,1,1),applicant.identityBirthDate());
        assertEquals("VERIFIED",applicant.identityStatus());
        assertThrows(AccessDeniedException.class,()->service.applicant(1L,candidate.id()));
    }
    @AfterEach void cleanup(){TransactionSynchronizationManager.clearSynchronization();}
    MockMultipartFile image() throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("front","front.png","image/png",out.toByteArray());}
    DocumentAiReadResult confirmed(){return new DocumentAiReadResult("NEW","NGUYEN VAN AN",LocalDate.of(1990,1,1),"B2",LocalDate.of(2015,1,1),LocalDate.of(2035,1,1),false,null);}
    @Test void noQrPartialOcrCanBeSubmittedWithoutReplacingEffectiveLicense() throws Exception {
        var ready=service.prepare(1L,image(),image(),true);
        assertEquals("READY_TO_SUBMIT",ready.status());assertTrue(ready.noQrDeclared());assertEquals("DISABLED",ready.evidence().qrStatus());
        assertEquals("OLD",renter.getLicenseNo());assertTrue(renter.isLicenseVerified());
        assertEquals("SUBMITTED",service.submit(1L,ready.id(),ready.version(),true).status());
        assertEquals("OLD",renter.getLicenseNo());verify(storage,never()).delete(anyString());
        when(submissions.findByRenter_Id(eq(5L),any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(row)));
        assertEquals(ready.id(),service.history(1L,0).getFirst().id());
        assertThrows(AccessDeniedException.class,()->service.history(2L,0));
        assertThrows(IdentityAiException.class,()->service.history(1L,-1));
        when(submissions.existsByRenter_IdAndStatusIn(5L,List.of("READY_TO_SUBMIT","SUBMITTED"))).thenReturn(true);
        assertThrows(IdentityAiException.class,()->service.prepare(1L,image(),image(),true));
    }
    @Test void adminCorrectionIsRecordedSeparatelyAndOnlyApprovalUpdatesEffectiveLicense() throws Exception {
        var ready=service.prepare(1L,image(),image(),true);service.submit(1L,ready.id(),0,true);
        var approved=service.review(2L,ready.id(),0,"APPROVE",null,null,confirmed());
        assertEquals("APPROVED",approved.status());assertEquals("NEW",renter.getLicenseNo());assertTrue(renter.isLicenseVerified());
        assertNull(approved.document().dateOfBirth());assertEquals(LocalDate.of(1990,1,1),approved.history().get(1).approvedData().dateOfBirth());
        assertEquals(ready.document(),approved.document());
        assertThrows(IdentityAiException.class,()->service.review(2L,ready.id(),0,"APPROVE",null,null,confirmed()));
    }
    @Test void invalidReviewStaleVersionAndNonOwnerCannotChangeLicense() throws Exception {
        var ready=service.prepare(1L,image(),image(),false);service.submit(1L,ready.id(),0,true);
        assertThrows(IdentityAiException.class,()->service.review(2L,ready.id(),1,"REJECT","DOCUMENT_UNREADABLE","Ảnh không rõ",null));
        assertThrows(IdentityAiException.class,()->service.review(2L,ready.id(),0,"NEEDS_CHANGES","DOCUMENT_UNREADABLE"," ",null));
        assertThrows(AccessDeniedException.class,()->service.detail(1L,ready.id()));
        assertThrows(AccessDeniedException.class,()->service.evidence(2L,ready.id(),"front",false));
        renter.getUser().getIdentity().setStatus(PersonIdentityStatus.SUSPENDED);
        assertThrows(IdentityAiException.class,()->service.review(2L,ready.id(),0,"APPROVE",null,null,confirmed()));
        assertEquals("SUBMITTED",row.getStatus());assertEquals("OLD",renter.getLicenseNo());
        var result=service.review(2L,ready.id(),0,"NEEDS_CHANGES","DOCUMENT_UNREADABLE","Vui lòng chụp rõ ngày hết hạn.",null);
        assertEquals("NEEDS_CHANGES",result.status());assertEquals(2,result.history().size());
    }
}
