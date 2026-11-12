package com.fptufa26.ecomotion.verification.identity.service.implement;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAiAssessmentResponse.DocumentRead;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAttemptResponse;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityVideoResult;
import com.fptufa26.ecomotion.verification.identity.entity.IdentityVerificationAttempt;
import com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.repository.IdentityVerificationAttemptRepository;
import com.fptufa26.ecomotion.verification.identity.service.IdentityAiAssessmentService;
import com.fptufa26.ecomotion.verification.identity.service.IdentityEvidenceStorageService;
import com.fptufa26.ecomotion.verification.identity.service.IdentityVerificationAttemptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus.*;

@Service
@Slf4j
public class IdentityVerificationAttemptServiceImpl implements IdentityVerificationAttemptService {

    private final IdentityVerificationAttemptRepository attempts;
    private final UserService users;
    private final IdentityEvidenceStorageService storage;
    private final IdentityAiAssessmentService ai;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final Duration lifetime;
    private final Duration processingTimeout;
    private final int maxImageBytes;
    private final int maxVideoBytes;
    private com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService pipelineRuns;
    @Value("${app.identity.acceptance-threshold:}") private String acceptanceThreshold;

    @org.springframework.beans.factory.annotation.Autowired
    public IdentityVerificationAttemptServiceImpl(
            IdentityVerificationAttemptRepository attempts, UserService users,
            IdentityEvidenceStorageService storage, IdentityAiAssessmentService ai,
            ObjectMapper mapper, Clock clock, PlatformTransactionManager transactionManager,
            @Value("${app.identity.attempt-ttl:24h}") Duration lifetime,
            @Value("${app.identity.processing-timeout:10m}") Duration processingTimeout,
            @Value("${app.identity.ai.max-image-bytes:8388608}") int maxImageBytes,
            @Value("${app.identity.ai.max-video-bytes:20971520}") int maxVideoBytes,
            com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService pipelineRuns) {
        this(attempts, users, storage, ai, mapper, clock, transactionManager, lifetime, processingTimeout, maxImageBytes, maxVideoBytes);
        this.pipelineRuns=pipelineRuns;
    }

    public IdentityVerificationAttemptServiceImpl(
            IdentityVerificationAttemptRepository attempts, UserService users,
            IdentityEvidenceStorageService storage, IdentityAiAssessmentService ai,
            ObjectMapper mapper, Clock clock, PlatformTransactionManager transactionManager,
            @Value("${app.identity.attempt-ttl:24h}") Duration lifetime,
            @Value("${app.identity.processing-timeout:10m}") Duration processingTimeout,
            @Value("${app.identity.ai.max-image-bytes:8388608}") int maxImageBytes,
            @Value("${app.identity.ai.max-video-bytes:20971520}") int maxVideoBytes) {
        this.attempts = attempts;
        this.users = users;
        this.storage = storage;
        this.ai = ai;
        this.mapper = mapper;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.lifetime = lifetime;
        this.processingTimeout = processingTimeout;
        this.maxImageBytes = maxImageBytes;
        this.maxVideoBytes = maxVideoBytes;
        if (lifetime.isNegative() || lifetime.isZero() || processingTimeout.isNegative()
                || processingTimeout.isZero() || maxImageBytes < 1 || maxVideoBytes < 1
                || maxImageBytes == Integer.MAX_VALUE || maxVideoBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Identity attempt limits are invalid");
        }
    }

    @Override
    public IdentityAttemptResponse createOrResume(Long userId) {
        User user = authorize(userId);
        try {
            return transactions.execute(transaction -> {
                var active = attempts.findActiveForUpdate(userId).orElse(null);
                if (active != null) {
                    recover(active);
                    attempts.saveAndFlush(active);
                    if (active.getActiveUserId() != null) {
                        return view(active);
                    }
                }
                var attempt = new IdentityVerificationAttempt();
                attempt.setPublicId(UUID.randomUUID());
                attempt.setUser(user);
                attempt.setActiveUserId(userId);
                attempt.setRole(user.getRole());
                attempt.setStatus(DRAFT);
                attempt.setExpiresAt(clock.instant().plus(lifetime));
                return view(attempts.saveAndFlush(attempt));
            });
        } catch (DataIntegrityViolationException exception) {
            return transactions.execute(transaction -> attempts.findActiveForUpdate(userId)
                    .map(this::view).orElseThrow(() -> exception));
        }
    }

    @Override
    public IdentityAttemptResponse current(Long userId) {
        authorize(userId);
        return transactions.execute(transaction -> {
            var attempt = attempts.findActiveForUpdate(userId).orElseThrow(this::notFound);
            recover(attempt);
            return view(attempts.saveAndFlush(attempt));
        });
    }

    @Override
    public IdentityAttemptResponse get(Long userId, UUID attemptId) {
        authorize(userId);
        return transactions.execute(transaction -> {
            var attempt = owned(userId, attemptId);
            recover(attempt);
            return view(attempts.saveAndFlush(attempt));
        });
    }

    @Override
    public IdentityAttemptResponse uploadDocuments(
            Long userId, UUID attemptId, long version, MultipartFile front, MultipartFile back) {
        requireEditable(get(userId, attemptId), version);
        String frontKey = null;
        String backKey = null;
        Operation operation;
        try {
            frontKey = storage.store(attemptId, front, maxImageBytes);
            backKey = storage.store(attemptId, back, maxImageBytes);
            byte[] frontBytes = storage.read(frontKey, maxImageBytes);
            byte[] backBytes = storage.read(backKey, maxImageBytes);
            String storedFront = frontKey;
            String storedBack = backKey;
            operation = transactions.execute(transaction -> {
                var attempt = owned(userId, attemptId);
                requireEditable(view(attempt), version);
                var obsolete = attempt.isEvidenceRetained() ? java.util.List.<String>of() : keys(attempt);
                begin(attempt, "DOCUMENT");
                attempt.setDocumentRevision(attempt.getDocumentRevision() + 1);
                attempt.setFrontKey(storedFront);
                attempt.setBackKey(storedBack);
                attempt.setFrontSha256(digest(frontBytes));
                attempt.setBackSha256(digest(backBytes));
                attempt.setVideoKey(null);
                attempt.setDocumentJson(null);
                attempt.setVideoJson(null);
                attempts.saveAndFlush(attempt);
                return new Operation(attempt.getOperationId(), obsolete);
            });
        } catch (RuntimeException exception) {
            cleanup(frontKey);
            cleanup(backKey);
            throw exception;
        }
        operation.obsoleteKeys().forEach(this::cleanup);
        try (var progress = com.fptufa26.ecomotion.verification.progress.AiProgressContext.open(operation.id(), snapshot -> { if(pipelineRuns!=null) pipelineRuns.checkpoint(userId,operation.id(),snapshot); })) {
            byte[] frontBytes = storage.read(frontKey, maxImageBytes);
            byte[] backBytes = storage.read(backKey, maxImageBytes);
            DocumentRead result = ai.readDocuments(userId, frontBytes, backBytes);
            if (result == null) {
                throw failure(HttpStatus.BAD_GATEWAY, "DOCUMENT_AI_INVALID_RESPONSE", "Kết quả đọc CCCD rỗng.");
            }
            String documentJson = mapper.writeValueAsString(result);
            return transactions.execute(transaction -> {
                var attempt = requireOperation(userId, attemptId, operation.id());
                if (!finishable(attempt)) {
                    return view(attempts.saveAndFlush(attempt));
                }
                attempt.setDocumentJson(documentJson);
                attempt.setStatus(result.requiredFieldsReadable() ? DOCUMENT_READY : DRAFT);
                attempt.setLastErrorCode(result.requiredFieldsReadable() ? null : "DOCUMENT_RETAKE_REQUIRED");
                clearOperation(attempt);
                return view(attempts.saveAndFlush(attempt));
            });
        } catch (RuntimeException exception) {
            recordFailure(userId, attemptId, operation.id(), exception);
            throw exception;
        }
    }

    @Override
    public IdentityAttemptResponse uploadVideo(Long userId, UUID attemptId, long version, MultipartFile video) {
        var initial = get(userId, attemptId);
        requireVideoReady(initial, version);
        String videoKey = storage.store(attemptId, video, maxVideoBytes);
        Operation operation;
        try {
            operation = transactions.execute(transaction -> {
                var attempt = owned(userId, attemptId);
                requireVideoReady(view(attempt), version);
                var obsolete = new ArrayList<String>();
                if (attempt.getVideoKey() != null && !attempt.isEvidenceRetained()) {
                    obsolete.add(attempt.getVideoKey());
                }
                begin(attempt, "VIDEO");
                attempt.setVideoKey(videoKey);
                attempt.setVideoJson(null);
                attempts.saveAndFlush(attempt);
                return new Operation(attempt.getOperationId(), obsolete);
            });
        } catch (RuntimeException exception) {
            cleanup(videoKey);
            throw exception;
        }
        operation.obsoleteKeys().forEach(this::cleanup);
        try (var progress = com.fptufa26.ecomotion.verification.progress.AiProgressContext.open(operation.id(), snapshot -> { if(pipelineRuns!=null) pipelineRuns.checkpoint(userId,operation.id(),snapshot); })) {
            var reference = transactions.execute(transaction -> {
                var attempt = requireOperation(userId, attemptId, operation.id());
                return new Reference(attempt.getFrontKey(), attempt.getFrontSha256());
            });
            byte[] frontBytes = storage.read(reference.key(), maxImageBytes);
            if (!Objects.equals(reference.digest(), digest(frontBytes))) {
                throw failure(HttpStatus.CONFLICT, "IDENTITY_DOCUMENT_CHANGED", "Ảnh tham chiếu không còn khớp hồ sơ.");
            }
            var result = ai.assessVideo(userId, frontBytes, storage.read(videoKey, maxVideoBytes));
            if (result == null || result.workflow() == null) {
                throw failure(HttpStatus.BAD_GATEWAY, "IDENTITY_AI_INVALID_RESPONSE", "Kết quả video không hợp lệ.");
            }
            String videoJson = mapper.writeValueAsString(result);
            return transactions.execute(transaction -> {
                var attempt = requireOperation(userId, attemptId, operation.id());
                if (!finishable(attempt)) {
                    return view(attempts.saveAndFlush(attempt));
                }
                attempt.setVideoJson(videoJson);
                attempt.setVideoSha256(digest(storage.read(videoKey, maxVideoBytes)));
                boolean ready = "READY_FOR_REVIEW".equals(result.workflow().status());
                attempt.setStatus(ready ? READY_TO_SUBMIT : DOCUMENT_READY);
                attempt.setLastErrorCode(ready ? null : "VIDEO_RETAKE_REQUIRED");
                clearOperation(attempt);
                return view(attempts.saveAndFlush(attempt));
            });
        } catch (RuntimeException exception) {
            recordFailure(userId, attemptId, operation.id(), exception);
            throw exception;
        }
    }

    @Override
    public IdentityAttemptResponse cancel(Long userId, UUID attemptId, long version) {
        authorize(userId);
        return transactions.execute(transaction -> {
            var attempt = owned(userId, attemptId);
            requireVersion(attempt.getVersion(), version);
            if (Set.of(SUBMITTED, APPROVED, REJECTED).contains(attempt.getStatus())) {
                throw failure(HttpStatus.CONFLICT, "IDENTITY_ATTEMPT_STATE_INVALID", "Submitted decisions cannot be cancelled.");
            }
            if (attempt.getStatus() != CANCELLED && attempt.getStatus() != EXPIRED) {
                attempt.setStatus(CANCELLED);
                attempt.setActiveUserId(null);
                clearOperation(attempt);
            }
            return view(attempts.saveAndFlush(attempt));
        });
    }

    @Override
    public byte[] evidence(Long userId, UUID attemptId, String kind) {
        authorize(userId);
        String key = transactions.execute(transaction -> {
            var attempt = owned(userId, attemptId);
            return switch (kind) {
                case "front" -> attempt.getFrontKey();
                case "back" -> attempt.getBackKey();
                case "video" -> attempt.getVideoKey();
                default -> throw notFound();
            };
        });
        if (key == null) {
            throw notFound();
        }
        return storage.read(key, "video".equals(kind) ? maxVideoBytes : maxImageBytes);
    }

    @Override
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityProcessingResponse progress(Long userId, UUID id) {
        authorize(userId);
        return transactions.execute(tx -> {
            var attempt = owned(userId, id);
            recover(attempt);
            attempts.saveAndFlush(attempt);
            var started = attempt.getOperationStartedAt();
            var liveProgress = com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(attempt.getOperationId());
            if(liveProgress==null && pipelineRuns!=null && attempt.getOperationId()!=null) liveProgress=pipelineRuns.owned(userId,attempt.getOperationId()).progress();
            return new com.fptufa26.ecomotion.verification.identity.dto.response.IdentityProcessingResponse(
                    attempt.getPublicId(), attempt.getStatus(), attempt.getProcessingStage(), started, clock.instant(),
                    started == null ? null : started.plus(processingTimeout), attempt.getLastErrorCode(),
                    attempt.getVersion(), attempt.getDocumentRevision(),
                    liveProgress);
        });
    }

    @Override
    public java.util.List<IdentityAttemptResponse> history(Long userId, int page) {
        authorize(userId);
        if (page < 0) throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_PAGE_INVALID", "Trang không hợp lệ.");
        return transactions.execute(tx -> attempts.findByUser_IdOrderByIdDesc(userId,
                org.springframework.data.domain.PageRequest.of(page, 10)).map(this::view).getContent());
    }

    @Override
    public byte[] historyEvidence(Long userId, UUID id, Long eventId, String kind) {
        authorize(userId);
        String key = transactions.execute(tx -> {
            var attempt = owned(userId, id);
            var event = attempt.getReviewHistory().stream().filter(row -> Objects.equals(row.getId(), eventId))
                    .findFirst().orElseThrow(this::notFound);
            return switch (kind) {
                case "front" -> event.getFrontKey(); case "back" -> event.getBackKey(); case "video" -> event.getVideoKey();
                default -> throw notFound();
            };
        });
        if (key == null) throw notFound();
        return storedEvidence(key, kind);
    }

    private User authorize(Long userId) {
        var user = users.findById(userId).orElseThrow(() -> new AccessDeniedException("Account not found"));
        if ((user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING)
                || (user.getRole() != Role.RENTER && user.getRole() != Role.PROVIDER)) {
            throw new AccessDeniedException("Account cannot access identity attempts");
        }
        return user;
    }

    private IdentityVerificationAttempt owned(Long userId, UUID attemptId) {
        return attempts.findOwnedForUpdate(attemptId, userId).orElseThrow(this::notFound);
    }

    private IdentityVerificationAttempt requireOperation(Long userId, UUID attemptId, UUID operationId) {
        authorize(userId);
        var attempt = owned(userId, attemptId);
        if (attempt.getStatus() != PROCESSING || !Objects.equals(attempt.getOperationId(), operationId)) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_ATTEMPT_STALE", "Lượt xử lý không còn hiệu lực.");
        }
        return attempt;
    }

    private void requireEditable(IdentityAttemptResponse attempt, long version) {
        requireVersion(attempt.version(), version);
        if (!attempt.expiresAt().isAfter(clock.instant())
                || Set.of(PROCESSING, CANCELLED, EXPIRED, SUBMITTED, APPROVED, REJECTED).contains(attempt.status())) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_ATTEMPT_STATE_INVALID", "Hồ sơ không thể sửa ở bước này.");
        }
    }

    private void requireVideoReady(IdentityAttemptResponse attempt, long version) {
        requireEditable(attempt, version);
        if (attempt.document() == null || !attempt.document().requiredFieldsReadable()
                || !attempt.hasFront() || !attempt.hasBack()) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_DOCUMENTS_REQUIRED", "Cần đọc đủ hai mặt CCCD trước khi gửi video.");
        }
    }

    private void requireVersion(Long actual, long expected) {
        if (actual == null || actual != expected) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_ATTEMPT_STALE", "Hồ sơ đã thay đổi, vui lòng tải lại.");
        }
    }

    private void begin(IdentityVerificationAttempt attempt, String stage) {
        attempt.setSubmittedAt(null);
        attempt.setDecidedAt(null);
        attempt.setDecisionActor(null);
        attempt.setReviewedBy(null);
        attempt.setDecisionReason(null);
        attempt.setDecisionPolicyVersion(null);
        attempt.setStatus(PROCESSING);
        attempt.setProcessingStage(stage);
        attempt.setOperationId(UUID.randomUUID());
        attempt.setOperationStartedAt(clock.instant());
        attempt.setLastErrorCode(null);
        if(pipelineRuns!=null) pipelineRuns.start(attempt.getUser().getId(),attempt.getOperationId(),attempt.getPublicId(),stage,attempt.getDocumentRevision()+("DOCUMENT".equals(stage)?1:0));
    }

    private void finishPipeline(IdentityVerificationAttempt attempt) {
        if(pipelineRuns!=null && attempt.getOperationId()!=null) pipelineRuns.finish(attempt.getUser().getId(),attempt.getOperationId(),
            attempt.getStatus()==ERROR || attempt.getStatus()==EXPIRED ? "ERROR" : attempt.getStatus()==CANCELLED ? "CANCELLED" : "COMPLETED",attempt.getLastErrorCode());
    }

    private void clearOperation(IdentityVerificationAttempt attempt) {
        finishPipeline(attempt);
        attempt.setOperationId(null);
        attempt.setOperationStartedAt(null);
        attempt.setProcessingStage(null);
    }

    private void recover(IdentityVerificationAttempt attempt) {
        if (Set.of(CANCELLED, EXPIRED, SUBMITTED, APPROVED, REJECTED).contains(attempt.getStatus())) {
            return;
        }
        if (!attempt.getExpiresAt().isAfter(clock.instant())) {
            attempt.setStatus(EXPIRED);
            attempt.setActiveUserId(null);
            clearOperation(attempt);
        } else if (attempt.getStatus() == PROCESSING && attempt.getOperationStartedAt() != null
                && !attempt.getOperationStartedAt().plus(processingTimeout).isAfter(clock.instant())) {
            attempt.setStatus(ERROR);
            attempt.setLastErrorCode("IDENTITY_PROCESSING_TIMEOUT");
            finishPipeline(attempt);
            attempt.setOperationId(null);
            attempt.setOperationStartedAt(null);
        }
    }

    private boolean finishable(IdentityVerificationAttempt attempt) {
        recover(attempt);
        return attempt.getStatus() == PROCESSING;
    }

    private void recordFailure(Long userId, UUID attemptId, UUID operationId, RuntimeException error) {
        transactions.executeWithoutResult(transaction -> {
            var attempt = attempts.findOwnedForUpdate(attemptId, userId).orElse(null);
            if (attempt == null || !Objects.equals(attempt.getOperationId(), operationId)) {
                return;
            }
            recover(attempt);
            if (attempt.getStatus() == PROCESSING) {
                attempt.setStatus(ERROR);
                attempt.setLastErrorCode(error instanceof IdentityAiException failure
                        ? failure.getErrorCode() : "IDENTITY_PROCESSING_FAILED");
                finishPipeline(attempt);
                attempt.setOperationId(null);
                attempt.setOperationStartedAt(null);
            }
            attempts.saveAndFlush(attempt);
        });
    }

    private IdentityAttemptResponse view(IdentityVerificationAttempt attempt) {
        DocumentRead document = decode(attempt.getDocumentJson(), DocumentRead.class);
        IdentityVideoResult video = decode(attempt.getVideoJson(), IdentityVideoResult.class);
        String nextAction = switch (attempt.getStatus()) {
            case DRAFT -> "UPLOAD_DOCUMENTS";
            case PROCESSING -> "WAIT_FOR_PROCESSING";
            case DOCUMENT_READY -> "UPLOAD_VIDEO";
            case READY_TO_SUBMIT -> attempt.getRole() == Role.PROVIDER ? "COMPLETE_PROVIDER_PROFILE" : "SUBMIT_IDENTITY";
            case ERROR -> "VIDEO".equals(attempt.getProcessingStage()) ? "RETRY_VIDEO" : "RETRY_DOCUMENTS";
            case CANCELLED, EXPIRED -> "CREATE_NEW_ATTEMPT";
            case SUBMITTED -> "WAIT_FOR_ADMIN";
            case APPROVED -> "IDENTITY_VERIFIED";
            case NEEDS_CHANGES -> "UPLOAD_DOCUMENTS";
            case REJECTED -> "CREATE_NEW_ATTEMPT";
        };
        return new IdentityAttemptResponse(attempt.getPublicId(), attempt.getVersion(), attempt.getRole(),
                attempt.getStatus(), attempt.getDocumentRevision(), attempt.getProcessingStage(), nextAction,
                attempt.getLastErrorCode(), attempt.getExpiresAt(), attempt.getFrontKey() != null,
                attempt.getBackKey() != null, attempt.getVideoKey() != null,
                document, video, attempt.getStatus() == APPROVED ? "ACCEPTED" : "NOT_EVALUATED",
                attempt.getStatus() == READY_TO_SUBMIT && attempt.getExpiresAt().isAfter(clock.instant())
                        && attempt.hasRequiredNewEvidence(),
                attempt.getStatus() == APPROVED && "SYSTEM".equals(attempt.getDecisionActor()),
                attempt.getUser().getId(), attempt.getUser().getEmail(), attempt.getUser().getFullName(),
                attempt.getDecisionActor(), attempt.getDecisionReason(), attempt.getDecisionPolicyVersion(),
                attempt.getSubmittedAt(), attempt.getDecidedAt(), attempt.getReviewedBy(), attempt.getResultingIdentityId(),
                attempt.getReviewHistory().stream().map(event -> new com.fptufa26.ecomotion.verification.identity.dto.response.IdentityReviewEventResponse(
                        event.getId(), event.getEventType(), event.getActorType(), event.getActorId(), event.getReasonCode(),
                        event.getNote(), event.getPolicyVersion(), event.getDocumentRevision(), event.getAttemptVersion(),
                        event.getOccurredAt(), decode(event.getDocumentJson(), DocumentRead.class),
                        decode(event.getVideoJson(), IdentityVideoResult.class))).toList(),
                pipelineRuns==null?java.util.List.of():pipelineRuns.forAttempt(attempt.getUser().getId(),attempt.getPublicId()));
    }

    private <T> T decode(String json, Class<T> type) {
        return json == null ? null : mapper.readValue(json, type);
    }

    private java.util.List<String> keys(IdentityVerificationAttempt attempt) {
        return Arrays.stream(new String[]{attempt.getFrontKey(), attempt.getBackKey(), attempt.getVideoKey()})
                .filter(Objects::nonNull).toList();
    }

    @Override
    public byte[] storedEvidence(String key, String kind) {
        if (!java.util.Set.of("front", "back", "video").contains(kind)) throw new IllegalArgumentException("Unknown evidence kind");
        return storage.read(key, "video".equals(kind) ? maxVideoBytes : maxImageBytes);
    }

    private void cleanup(String key) {
        if (key != null) {
            try {
                storage.delete(key);
            } catch (RuntimeException exception) {
                log.warn("Identity evidence cleanup could not complete");
            }
        }
    }

    private String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private IdentityAiException notFound() {
        return failure(HttpStatus.NOT_FOUND, "IDENTITY_ATTEMPT_NOT_FOUND", "Không tìm thấy hồ sơ xác minh.");
    }

    private IdentityAiException failure(HttpStatus status, String code, String message) {
        return new IdentityAiException(status, code, message);
    }

    private record Operation(UUID id, java.util.List<String> obsoleteKeys) {
    }

    private record Reference(String key, String digest) {
    }
}
