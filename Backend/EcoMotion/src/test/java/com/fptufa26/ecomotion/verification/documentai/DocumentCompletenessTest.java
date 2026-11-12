package com.fptufa26.ecomotion.verification.documentai;

import org.junit.jupiter.api.Test;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class DocumentCompletenessTest {
    Map<String,Object> document(String type, Map<String,Object> data) {
        var raw=new HashMap<String,Object>();raw.put("documentType",type);raw.put("status","READ_SUCCESS");raw.put("data",data);
        var fields=new HashMap<String,Object>();data.forEach((key,value)->fields.put(key,Map.of("status","OCR_ONLY","source","OCR","confidence",.99)));
        raw.put("fields",fields);return raw;
    }
    @Test void countsOnlyRequiredValidFieldsWithoutReviewAndPreservesLegacySnapshots() {
        var data=new HashMap<String,Object>(Map.of("documentNumber","001234567890","fullName","Test Person","dateOfBirth","1990-01-01"));
        var raw=document("VN_CITIZEN_ID",data);
        var report=DocumentAiEvidence.from(raw);
        assertEquals(3,report.completeness().completedCount());assertEquals(4,report.completeness().totalCount());assertEquals(75,report.completeness().percentage());
        data.put("expiryDate","2035-01-01");raw=document("VN_CITIZEN_ID",data);
        raw.put("inconsistentFields",List.of("fullName"));
        assertEquals(75,DocumentAiEvidence.from(raw).completeness().percentage());
        raw.put("inconsistentFields",List.of());raw.put("statusReasons",List.of("REQUIRED_FIELDS_NEED_REVIEW"));
        assertEquals(0,DocumentAiEvidence.from(raw).completeness().percentage());
        var mapper=new ObjectMapper();var stored=mapper.readValue(mapper.writeValueAsString(report),DocumentAiEvidence.class);
        assertEquals(report.completeness(),stored.completeness());
        assertNull(mapper.readValue("{\"readingStatus\":\"READ_SUCCESS\"}",DocumentAiEvidence.class).completeness());
    }
    @Test void completeDataDoesNotSuppressImageFlagAndUnlimitedIsOnlyAReadingResult() {
        var raw=document("VN_DRIVING_LICENSE",Map.of("documentNumber","TEST123","fullName","Test Person","dateOfBirth","1990-01-01","licenseClass","B2","issueDate","2015-01-01","expiryDate","UNLIMITED"));
        raw.put("imageAnalysis",Map.of("checks",List.of(Map.of("id","digital_tampering","side","FRONT","status","FAIL"))));
        var evidence=DocumentAiEvidence.from(raw);
        assertEquals(100,evidence.completeness().percentage());assertEquals("FLAGGED",evidence.checks().getFirst().status());
        assertFalse(DrivingLicenseEligibilityPolicy.isEligibleForYearlyRental("B2",null,null,true));
        var invalid=document("VN_CITIZEN_ID",Map.of("documentNumber","bad","fullName","Test Person","dateOfBirth","1990-14-01","expiryDate","2035-01-01"));
        assertEquals(50,DocumentAiEvidence.from(invalid).completeness().percentage());
        assertNull(DocumentAiEvidence.from(Map.of("documentType","UNKNOWN")).completeness());
    }
}
