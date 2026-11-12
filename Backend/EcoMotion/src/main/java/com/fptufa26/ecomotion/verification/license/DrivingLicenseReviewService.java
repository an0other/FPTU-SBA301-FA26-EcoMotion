package com.fptufa26.ecomotion.verification.license;

import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.documentai.*;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.format.*;
import java.text.Normalizer;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;

@Service @Transactional
public class DrivingLicenseReviewService {
    private final DrivingLicenseSubmissionRepository submissions;
    private final RenterRepository renters;
    private final UserService users;
    private final DocumentAiClient ai;
    private final FileStorageService storage;
    private final ObjectMapper mapper;
    private final Clock clock;
    private com.fptufa26.ecomotion.verification.identity.repository.IdentityVerificationAttemptRepository identityAttempts;
    private com.fptufa26.ecomotion.verification.identity.service.IdentityEvidenceStorageService identityStorage;
    @org.springframework.beans.factory.annotation.Autowired
    public DrivingLicenseReviewService(DrivingLicenseSubmissionRepository submissions, RenterRepository renters,
            UserService users, DocumentAiClient ai, FileStorageService storage, ObjectMapper mapper, Clock clock,
            com.fptufa26.ecomotion.verification.identity.repository.IdentityVerificationAttemptRepository identityAttempts,
            com.fptufa26.ecomotion.verification.identity.service.IdentityEvidenceStorageService identityStorage) {
        this(submissions, renters, users, ai, storage, mapper, clock);this.identityAttempts=identityAttempts;this.identityStorage=identityStorage;
    }
    public DrivingLicenseReviewService(DrivingLicenseSubmissionRepository submissions, RenterRepository renters,
            UserService users, DocumentAiClient ai, FileStorageService storage, ObjectMapper mapper, Clock clock) {
        this.submissions=submissions; this.renters=renters; this.users=users; this.ai=ai; this.storage=storage; this.mapper=mapper; this.clock=clock;
    }

    public record Event(String action, Long actorId, String reason, String note, Instant at, DocumentAiReadResult approvedData) {}
    public record View(UUID id, Long version, String status, boolean noQrDeclared, Long userId, String email,
            DocumentAiReadResult document, DocumentAiEvidence evidence, List<Event> history, Instant createdAt) {}

    public View current(Long userId) {
        var renter=renter(userId, false);
        return submissions.findFirstByRenter_IdOrderByIdDesc(renter.getId()).map(this::view).orElse(null);
    }

    public List<View> history(Long userId, int page) {
        var owner = renter(userId, false);
        if (page < 0) throw error("LICENSE_FILTER_INVALID", "Trang không hợp lệ.");
        return submissions.findByRenter_Id(owner.getId(), PageRequest.of(page, 10, Sort.by("id").descending()))
                .map(this::view).getContent();
    }

    public View prepare(Long userId, MultipartFile front, MultipartFile back, boolean noQr) {
        // A candidate never changes the effective license. Serialize candidate creation with the renter lock.
        var renter=renter(userId, true);
        if (submissions.existsByRenter_IdAndStatusIn(renter.getId(), List.of("READY_TO_SUBMIT", "SUBMITTED")))
            throw error("LICENSE_ACTIVE_SUBMISSION", "Đã có hồ sơ đang chuẩn bị/chờ duyệt. Hãy hủy bản chuẩn bị hoặc đợi xét duyệt.");
        byte[] f=image(front), b=image(back);
        var raw=ai.readDrivingLicense(f,"front.png",b,"back.png");
        if (raw == null) throw error("DOCUMENT_AI_INVALID_RESPONSE", "Không nhận được kết quả đọc giấy tờ.");
        String status=text(raw.get("status"));
        if (!"PROCESSED".equals(text(raw.get("code"))) || !"VN_DRIVING_LICENSE".equals(text(raw.get("documentType")))
                || !List.of("READ_SUCCESS","REVIEW_REQUIRED","PARTIALLY_READ","INCONSISTENT_DATA").contains(status == null ? "" : status))
            throw error("LICENSE_RETAKE_REQUIRED", "Chưa nhận được ảnh GPLX sử dụng được. Hãy kiểm tra hai mặt và chụp lại.");
        Map<?,?> data=raw.get("data") instanceof Map<?,?> m ? m : Map.of();
        var document=new DocumentAiReadResult(text(data.get("documentNumber")),text(data.get("fullName")),
                dateOrNull(text(data.get("dateOfBirth"))),text(data.get("licenseClass")),dateOrNull(text(data.get("issueDate"))),
                dateOrNull(text(data.get("expiryDate"))),"UNLIMITED".equals(text(data.get("expiryDate"))),text(raw.get("requestId")));
        var row=new DrivingLicenseSubmission();
        row.setRenter(renter); row.setPublicId(UUID.randomUUID()); row.setStatus("READY_TO_SUBMIT");
        row.setNoQrDeclared(noQr); row.setCreatedAt(clock.instant());
        row.setDocumentJson(mapper.writeValueAsString(document)); row.setEvidenceJson(mapper.writeValueAsString(DocumentAiEvidence.from(raw)));
        row.setHistoryJson("[]");
        row.setFrontPath(store(userId,"front-"+row.getPublicId(),f));
        row.setBackPath(store(userId,"back-"+row.getPublicId(),b));
        return view(submissions.saveAndFlush(row));
    }

    public View submit(Long userId, UUID id, long version, boolean confirmed) {
        var row=owned(userId,id); version(row,version);
        if (!confirmed) throw error("LICENSE_CONFIRM_REQUIRED","Hãy xác nhận giấy tờ là của bạn trước khi gửi.");
        if ("SUBMITTED".equals(row.getStatus())) return view(row);
        if (!"READY_TO_SUBMIT".equals(row.getStatus())) throw error("LICENSE_STATE_INVALID","Hồ sơ không ở bước xác nhận gửi.");
        row.setStatus("SUBMITTED"); row.setSubmittedAt(clock.instant());
        event(row,new Event("SUBMIT",userId,null,null,clock.instant(),null));
        return view(submissions.saveAndFlush(row));
    }
    public View cancel(Long userId, UUID id, long version) {
        var row=owned(userId,id); version(row,version);
        if (!"READY_TO_SUBMIT".equals(row.getStatus())) throw error("LICENSE_STATE_INVALID","Chỉ hủy được bản đang chuẩn bị.");
        row.setStatus("CANCELLED"); event(row,new Event("CANCEL",userId,null,null,clock.instant(),null));
        return view(submissions.saveAndFlush(row));
    }
    public List<View> pending(Long adminId,String status,int page) {
        admin(adminId);
        if (page<0 || !List.of("SUBMITTED","APPROVED","NEEDS_CHANGES","REJECTED").contains(status)) throw error("LICENSE_FILTER_INVALID","Bộ lọc không hợp lệ.");
        return submissions.findByStatus(status,PageRequest.of(page,20,Sort.by("id").ascending())).map(this::view).getContent();
    }
    public record Applicant(Long userId, String email, String accountName, String accountStatus,
            String identityName, LocalDate identityBirthDate, String identityNumber, String identityStatus, LocalDate identityExpiry) {}
    public Applicant applicant(Long adminId,UUID id) {
        admin(adminId);
        var user=row(id).getRenter().getUser();
        var identity=user.getIdentity();
        return new Applicant(user.getId(),user.getEmail(),user.getFullName(),user.getStatus().name(),
                identity==null?null:identity.getFullName(),identity==null?null:identity.getDateOfBirth(),
                identity==null?null:identity.getCccdNo(),identity==null?null:identity.getStatus().name(),identity==null?null:identity.getDocumentExpiry());
    }
    public View detail(Long adminId,UUID id) { admin(adminId); return view(row(id)); }
    public Image applicantIdentityImage(Long adminId,UUID id,String side) {
        admin(adminId);var owner=row(id).getRenter().getUser();var identity=owner.getIdentity();
        if(identity==null || identityAttempts==null || identityStorage==null) throw error("IDENTITY_EVIDENCE_UNAVAILABLE","Hồ sơ này chưa có ảnh CCCD đã lưu.");
        var approved=identityAttempts.findByUser_IdOrderByIdDesc(owner.getId(),PageRequest.of(0,20)).stream()
                .filter(a->a.getStatus()==com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus.APPROVED && Objects.equals(a.getResultingIdentityId(),identity.getId())).findFirst()
                .orElseGet(()->identity.getApprovedAttemptId()==null?null:identityAttempts.findById(identity.getApprovedAttemptId()).filter(a->Objects.equals(a.getResultingIdentityId(),identity.getId()) && a.getStatus()==com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus.APPROVED).orElse(null));
        if(approved==null) throw error("IDENTITY_EVIDENCE_UNAVAILABLE","Danh tính này chưa có ảnh CCCD đã duyệt, ví dụ dữ liệu seed.");
        String key=switch(side){case "front"->approved.getFrontKey();case "back"->approved.getBackKey();default->throw error("LICENSE_SIDE_INVALID","Mặt giấy tờ không hợp lệ.");};
        if(key==null) throw error("IDENTITY_EVIDENCE_UNAVAILABLE","Ảnh CCCD không còn trong hồ sơ.");
        byte[] bytes=identityStorage.read(key,8388608);
        return new Image(bytes,bytes.length>0 && (bytes[0]&255)==137?"image/png":"image/jpeg");
    }
    public record Image(byte[] content,String type) {}
    public Image evidence(Long actorId,UUID id,String side,boolean admin) {
        var row=admin ? rowForAdmin(actorId,id) : owned(actorId,id);
        String path=switch(side) {case "front" -> row.getFrontPath();case "back" -> row.getBackPath();default -> throw error("LICENSE_SIDE_INVALID","Mặt giấy tờ không hợp lệ.");};
        return new Image(storage.read(path),path.endsWith(".png") ? "image/png" : "image/jpeg");
    }
    public View review(Long adminId,UUID id,long version,String action,String reason,String note,DocumentAiReadResult corrected) {
        admin(adminId); var row=row(id); version(row,version);
        if (!"SUBMITTED".equals(row.getStatus())) throw error("LICENSE_STATE_INVALID","Hồ sơ không còn chờ duyệt.");
        if (!List.of("APPROVE","NEEDS_CHANGES","REJECT").contains(action)) throw error("LICENSE_ACTION_INVALID","Quyết định không hợp lệ.");
        note=note==null ? null : note.trim();
        if (note!=null && note.length()>1000 || !"APPROVE".equals(action) && (note==null || note.length()<5 || reason==null || !reason.matches("[A-Z][A-Z0-9_]{2,99}")))
            throw error("LICENSE_REVIEW_NOTE_REQUIRED","Cần lý do và hướng dẫn cụ thể 5–1.000 ký tự.");
        DocumentAiReadResult approved=null;
        if ("APPROVE".equals(action)) {
            if (corrected==null) throw error("LICENSE_DATA_REQUIRED","Hãy kiểm tra đầy đủ dữ liệu từ ảnh GPLX.");
            var renter=renter(row.getRenter().getUser().getId(),true);
            approved=validateApproval(corrected,renter);
            if (renters.existsByLicenseNoAndIdNot(approved.documentNumber(),renter.getId())) throw error("LICENSE_DUPLICATE","Số GPLX đã được sử dụng.");
            renter.setLicenseNo(approved.documentNumber()); renter.setLicenseFullName(approved.fullName());
            renter.setLicenseDateOfBirth(approved.dateOfBirth()); renter.setLicenseClass(approved.licenseClass());
            renter.setLicenseIssueDate(approved.issueDate()); renter.setLicenseExpiry(approved.expiryDate());
            renter.setLicenseExpiryUnlimited(approved.expiryUnlimited()); renter.setLicenseVerified(true);
            renter.setLicenseFrontImagePath(row.getFrontPath()); renter.setLicenseBackImagePath(row.getBackPath());
            renters.flush(); row.setStatus("APPROVED");
        } else row.setStatus("NEEDS_CHANGES".equals(action) ? "NEEDS_CHANGES" : "REJECTED");
        event(row,new Event(action,adminId,reason,note,clock.instant(),approved));
        return view(submissions.saveAndFlush(row));
    }
    private DocumentAiReadResult validateApproval(DocumentAiReadResult d,Renter renter) {
        LocalDate today=LocalDate.ofInstant(clock.instant(),ZoneId.of("Asia/Ho_Chi_Minh"));
        if (d.documentNumber()==null || !d.documentNumber().trim().matches("[A-Za-z0-9-]{1,20}")
                || d.fullName()==null || d.fullName().isBlank() || d.fullName().length()>150
                || d.licenseClass()==null || !d.licenseClass().trim().matches("[A-Za-z0-9]{1,10}")
                || d.dateOfBirth()==null || d.dateOfBirth().isAfter(today) || d.issueDate()==null
                || d.issueDate().isAfter(today) || d.issueDate().isBefore(d.dateOfBirth())
                || !d.expiryUnlimited() && (d.expiryDate()==null || d.expiryDate().isBefore(today) || d.expiryDate().isBefore(d.issueDate())))
            throw error("LICENSE_DATA_INVALID","Thông tin GPLX thiếu, ngày tháng chưa hợp lệ hoặc giấy tờ đã hết hạn.");
        var identity=renter.getUser().getIdentity();
        if (identity==null || identity.getStatus()!=PersonIdentityStatus.VERIFIED
                || identity.getDocumentExpiry()!=null && identity.getDocumentExpiry().isBefore(today))
            throw error("LICENSE_IDENTITY_REQUIRED","Cần danh tính đã được chấp nhận và còn hiệu lực trước khi duyệt GPLX.");
        if (!Objects.equals(identity.getDateOfBirth(),d.dateOfBirth()) || !name(identity.getFullName()).equals(name(d.fullName())))
            throw error("LICENSE_IDENTITY_MISMATCH","Họ tên/ngày sinh GPLX chưa khớp danh tính. Hãy đối chiếu ảnh, sửa sai OCR hoặc yêu cầu bổ sung.");
        return new DocumentAiReadResult(d.documentNumber().trim().toUpperCase(Locale.ROOT),d.fullName().trim(),d.dateOfBirth(),
                d.licenseClass().trim().toUpperCase(Locale.ROOT),d.issueDate(),d.expiryUnlimited()?null:d.expiryDate(),d.expiryUnlimited(),d.requestId());
    }
    private static String name(String s) {return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ','d').replace('Đ','D').trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);}
    private Renter renter(Long id,boolean lock) {
        var user=users.findById(id).orElseThrow(()->new AccessDeniedException("Account not found"));
        if (user.getRole()!=Role.RENTER || user.getStatus()!=UserStatus.ACTIVE) throw new AccessDeniedException("Active renter required");
        return (lock?renters.findRenterByUser_Id(id):renters.findByUser_Id(id)).orElseThrow(()->error("LICENSE_PROFILE_REQUIRED","Chưa có hồ sơ renter."));
    }
    private void admin(Long id) {var u=users.findById(id).orElseThrow(()->new AccessDeniedException("Admin required"));if(u.getRole()!=Role.ADMIN || u.getStatus()!=UserStatus.ACTIVE) throw new AccessDeniedException("Admin required");}
    private DrivingLicenseSubmission row(UUID id) {return submissions.lock(id).orElseThrow(()->error("LICENSE_NOT_FOUND","Không tìm thấy hồ sơ."));}
    private DrivingLicenseSubmission rowForAdmin(Long id,UUID key) {admin(id);return row(key);}
    private DrivingLicenseSubmission owned(Long id,UUID key) {renter(id,false);var s=row(key);if(!s.getRenter().getUser().getId().equals(id)) throw new AccessDeniedException("Not owner");return s;}
    private void version(DrivingLicenseSubmission row,long v) {if(!Objects.equals(row.getVersion(),v)) throw error("LICENSE_STALE","Hồ sơ đã thay đổi. Hãy cập nhật lại.");}
    private List<Event> history(DrivingLicenseSubmission row) {return new ArrayList<>(Arrays.asList(mapper.readValue(row.getHistoryJson(),Event[].class)));}
    private void event(DrivingLicenseSubmission row,Event event) {var log=history(row);log.add(event);row.setHistoryJson(mapper.writeValueAsString(log));}
    private View view(DrivingLicenseSubmission row) {return new View(row.getPublicId(),row.getVersion(),row.getStatus(),row.isNoQrDeclared(),row.getRenter().getUser().getId(),row.getRenter().getUser().getEmail(),mapper.readValue(row.getDocumentJson(),DocumentAiReadResult.class),mapper.readValue(row.getEvidenceJson(),DocumentAiEvidence.class),history(row),row.getCreatedAt());}
    private static String text(Object v) {return v instanceof String s && !s.isBlank()?s.trim():null;}
    private static LocalDate dateOrNull(String s) {if(s==null)return null;for(var f:List.of(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT))){try{return LocalDate.parse(s,f);}catch(DateTimeParseException ignored){}}return null;}
    private byte[] image(MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>8388608) throw error("LICENSE_IMAGE_INVALID","Cần ảnh JPEG/PNG, tối đa 8 MB mỗi ảnh.");
        try {
            byte[] bytes;try(var in=file.getInputStream()){bytes=in.readNBytes(8388609);}if(bytes.length>8388608)throw new IOException();
            try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();var reader=readers.next();
                try{reader.setInput(input);var format=reader.getFormatName();if(!List.of("JPEG","PNG").contains(format.toUpperCase(Locale.ROOT)) || (long)reader.getWidth(0)*reader.getHeight(0)>20000000 || reader.read(0)==null)throw new IOException();}finally{reader.dispose();}
            }return bytes;
        } catch(IOException e){throw error("LICENSE_IMAGE_INVALID","Không thể đọc ảnh GPLX hợp lệ hoặc ảnh vượt độ phân giải cho phép.");}
    }
    private String store(Long userId,String name,byte[] bytes) {
        boolean png=(bytes[0]&255)==137;String path=storage.store("renter",userId,"license-review-"+name+(png?".png":".jpg"),bytes,png?"image/png":"image/jpeg");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK){try{storage.delete(path);}catch(RuntimeException ignored){}}}});
        return path;
    }
    private IdentityAiException error(String code,String message){return new IdentityAiException(HttpStatus.CONFLICT,code,message);}
}
