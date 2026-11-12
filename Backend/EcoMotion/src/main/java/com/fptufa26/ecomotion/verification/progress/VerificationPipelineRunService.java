package com.fptufa26.ecomotion.verification.progress;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@Service
public class VerificationPipelineRunService {
    public record View(UUID id,String kind,UUID attemptId,int revision,String status,Instant startedAt,Instant finishedAt,AiProgressContext.Snapshot progress,String errorCode) {}
    private final VerificationPipelineRunRepository runs;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;
    public VerificationPipelineRunService(VerificationPipelineRunRepository runs,ObjectMapper mapper,Clock clock) {this(runs,mapper,clock,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public VerificationPipelineRunService(VerificationPipelineRunRepository runs,ObjectMapper mapper,Clock clock,jakarta.persistence.EntityManager entityManager) {this.runs=runs;this.mapper=mapper;this.clock=clock;this.entityManager=entityManager;}
    private VerificationPipelineRun currentForUpdate(Long owner,UUID id) {
        if(entityManager==null)return runs.lockOwned(owner,id).orElse(null);
        var row=runs.findByIdAndOwnerUserId(id,owner).orElse(null);
        if(row!=null)entityManager.refresh(row,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return row;
    }
    @Transactional
    public void start(Long owner,UUID id,UUID attempt,String kind,int revision) {
        if(!Set.of("DOCUMENT","VIDEO","LICENSE").contains(kind)) throw new IllegalArgumentException("Unsupported pipeline kind");
        if(runs.existsById(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Pipeline operation already exists");
        var row=new VerificationPipelineRun();row.setId(id);row.setOwnerUserId(owner);row.setAttemptId(attempt);row.setKind(kind);row.setRevision(revision);row.setStatus("PROCESSING");row.setStartedAt(clock.instant());runs.saveAndFlush(row);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void checkpoint(Long owner,UUID id,AiProgressContext.Snapshot progress) {
        var row=currentForUpdate(owner,id);
        if(row==null || !"PROCESSING".equals(row.getStatus()) || progress==null) return;
        row.setProgressJson(mapper.writeValueAsString(progress));runs.saveAndFlush(row);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(Long owner,UUID id,String status,String error) {
        if(!Set.of("COMPLETED","ERROR","CANCELLED").contains(status)) throw new IllegalArgumentException("Unsupported pipeline terminal status");
        var row=currentForUpdate(owner,id);
        if(row==null || !"PROCESSING".equals(row.getStatus())) return;
        row.setStatus(status);row.setFinishedAt(clock.instant());row.setErrorCode(error);runs.saveAndFlush(row);
    }
    @Transactional(readOnly=true)
    public View owned(Long owner,UUID id) {return view(runs.findByIdAndOwnerUserId(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pipeline operation not found")));}
    @Transactional(readOnly=true)
    public List<View> forAttempt(Long owner,UUID id) {return runs.findByOwnerUserIdAndAttemptIdOrderByStartedAtAsc(owner,id).stream().map(this::view).toList();}
    @Transactional(readOnly=true)
    public List<View> history(Long owner,String kind,int page) {if(page<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");return runs.findByOwnerUserIdAndKindOrderByStartedAtDesc(owner,kind,PageRequest.of(page,10)).stream().map(this::view).toList();}
    private View view(VerificationPipelineRun row) {return new View(row.getId(),row.getKind(),row.getAttemptId(),row.getRevision(),row.getStatus(),row.getStartedAt(),row.getFinishedAt(),row.getProgressJson()==null?null:mapper.readValue(row.getProgressJson(),AiProgressContext.Snapshot.class),row.getErrorCode());}
}
