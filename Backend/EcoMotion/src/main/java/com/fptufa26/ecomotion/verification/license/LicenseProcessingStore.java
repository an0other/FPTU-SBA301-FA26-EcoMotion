package com.fptufa26.ecomotion.verification.license;

import com.fptufa26.ecomotion.verification.progress.AiProgressContext.Snapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Component
public class LicenseProcessingStore {
    public record View(UUID attemptId, String status, String processingStage, Instant startedAt,
            Instant observedAt, Instant deadlineAt, Snapshot aiProgress, String errorCode) {}
    private static class Entry {
        UUID id; Long owner; Instant started, finished; String status="PROCESSING", error;
        Snapshot progress;
    }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Clock clock;
    private com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService durable;
    public LicenseProcessingStore(Clock clock) { this.clock=clock; }
    @org.springframework.beans.factory.annotation.Autowired
    public LicenseProcessingStore(Clock clock, com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService durable) {this(clock);this.durable=durable;}
    private View durableView(Long owner,com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService.View value) {
        if("PROCESSING".equals(value.status()) && !value.startedAt().plusSeconds(600).isAfter(clock.instant())) {
            durable.finish(owner,value.id(),"ERROR","LICENSE_PROCESSING_TIMEOUT");
            value=durable.owned(owner,value.id());
        }
        return new View(value.id(),value.status(),"DOCUMENT",value.startedAt(),clock.instant(),value.startedAt().plusSeconds(600),value.progress(),value.errorCode());
    }
    private void purge() {
        Instant now=clock.instant();
        entries.values().removeIf(e -> e.finished != null && e.finished.plusSeconds(600).isBefore(now));
        for (var e:entries.values()) if ("PROCESSING".equals(e.status) && e.started.plusSeconds(600).isBefore(now)) {
            e.status="ERROR"; e.error="LICENSE_PROCESSING_TIMEOUT"; e.finished=now;
        }
    }
    public synchronized void begin(Long owner, UUID id) {
        if(durable!=null) {
            if(current(owner)!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"A license operation is already processing");
            durable.start(owner,id,null,"LICENSE",0);return;
        }
        purge();
        if (entries.containsKey(id) || entries.values().stream().anyMatch(e -> e.owner.equals(owner) && "PROCESSING".equals(e.status)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A license operation already exists; refresh its progress");
        if (entries.size()>=200) {
            var completed=entries.values().stream().filter(e -> e.finished!=null).min(Comparator.comparing(e -> e.finished));
            if (completed.isPresent()) entries.remove(completed.get().id);
            else throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Progress capacity reached");
        }
        var e=new Entry(); e.id=id;e.owner=owner;e.started=clock.instant();entries.put(id,e);
    }
    public synchronized void update(Long owner,UUID id,Snapshot snapshot) {
        if(durable!=null) {durable.checkpoint(owner,id,snapshot);return;}
        var e=owned(owner,id); if ("PROCESSING".equals(e.status)) e.progress=snapshot;
    }
    public synchronized void finish(Long owner,UUID id,String error) {
        if(durable!=null) {durable.finish(owner,id,error==null?"COMPLETED":"ERROR",error);return;}
        var e=owned(owner,id); if (!"PROCESSING".equals(e.status)) return;
        e.status=error==null?"COMPLETED":"ERROR";e.error=error;e.finished=clock.instant();
    }
    public synchronized View get(Long owner,UUID id) { if(durable!=null)return durableView(owner,durable.owned(owner,id));purge();return view(owned(owner,id)); }
    public synchronized View current(Long owner) {
        if(durable!=null) return durable.history(owner,"LICENSE",0).stream().filter(v->"PROCESSING".equals(v.status())).map(v->durableView(owner,v)).filter(v->"PROCESSING".equals(v.status())).findFirst().orElse(null);
        purge();return entries.values().stream().filter(e -> e.owner.equals(owner) && "PROCESSING".equals(e.status))
            .findFirst().map(this::view).orElse(null);
    }
    public java.util.List<com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService.View> history(Long owner,int page) {
        return durable==null?java.util.List.of():durable.history(owner,"LICENSE",page);
    }
    private Entry owned(Long owner,UUID id) {
        var e=entries.get(id);if(e==null || !e.owner.equals(owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Processing operation not found");return e;
    }
    private View view(Entry e) { return new View(e.id,e.status,"DOCUMENT",e.started,clock.instant(),e.started.plusSeconds(600),e.progress,e.error); }
}
