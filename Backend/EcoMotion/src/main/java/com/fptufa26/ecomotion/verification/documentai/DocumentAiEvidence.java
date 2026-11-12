package com.fptufa26.ecomotion.verification.documentai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded evidence projection; never an authenticity or approval decision. */
public record DocumentAiEvidence(
        String requestId, String schemaVersion, String pipelineVersion, String readingStatus,
        String qrStatus, String authenticityStatus, String calibrationStatus,
        List<String> statusReasons, List<String> warnings, List<String> missingFields,
        List<String> inconsistentFields, List<FieldEvidence> fields, List<CheckEvidence> checks,
        DocumentCompleteness completeness) {

    public record FieldEvidence(String name, String source, String status, Double confidence, boolean needsReview) {}
    public record CheckEvidence(String id, String side, String status, String sourceStatus,
                                List<String> reasonCodes, String scope) {}

    private static final Set<String> FIELD_NAMES = Set.of("documentNumber", "fullName", "dateOfBirth",
            "expiryDate", "sex", "address", "licenseClass", "issueDate");

    public static DocumentAiEvidence from(Map<String, Object> raw) {
        var fields = new ArrayList<FieldEvidence>();
        for (var entry : object(raw.get("fields")).entrySet()) {
            if (!(entry.getKey() instanceof String name) || !FIELD_NAMES.contains(name)) continue;
            var field = object(entry.getValue());
            var ocr = object(field.get("ocr"));
            String status = token(field.get("status"));
            Double confidence = confidence(field.get("confidence"));
            if (confidence == null) confidence = confidence(ocr.get("confidence"));
            boolean needsReview = Boolean.TRUE.equals(field.get("needsReview"))
                    || Boolean.TRUE.equals(field.get("ocrAmbiguous"))
                    || Set.of("OCR_AMBIGUOUS", "QR_ONLY", "MISMATCH", "MISSING").contains(status == null ? "" : status);
            fields.add(new FieldEvidence(name, token(field.get("source")), status, confidence, needsReview));
        }
        fields.sort(java.util.Comparator.comparing(FieldEvidence::name));
        var checks = new ArrayList<CheckEvidence>();
        appendChecks(checks, raw.get("checks"), false);
        appendChecks(checks, object(raw.get("imageAnalysis")).get("checks"), true);
        return new DocumentAiEvidence(token(raw.get("requestId")), token(raw.get("schemaVersion")),
                token(object(raw.get("pipelineReport")).get("pipelineVersion")), token(raw.get("status")),
                token(object(raw.get("qrData")).get("status")),
                token(object(object(raw.get("assessment")).get("documentAuthenticity")).get("status")),
                // The upstream contract does not provide calibrated per-field probabilities.
                "NOT_ESTABLISHED", codes(raw.get("statusReasons")), codes(raw.get("warnings")),
                codes(raw.get("missingFields")), codes(raw.get("inconsistentFields")),
                List.copyOf(fields), List.copyOf(checks), DocumentCompleteness.from(raw, fields));
    }

    private static void appendChecks(List<CheckEvidence> target, Object value, boolean image) {
        if (!(value instanceof List<?> rows)) return;
        for (Object item : rows) {
            if (target.size() >= 100) break;
            var row = object(item);
            String id = token(row.get(image ? "id" : "step"));
            if (id == null) continue;
            String source = token(row.get("status"));
            target.add(new CheckEvidence(id, token(row.get("side")), normalize(source), source,
                    codes(row.get("reasons")), image ? "IMAGE_SIGNAL_ONLY" : "READING_AND_CONSISTENCY"));
        }
    }

    private static String normalize(String status) {
        if (status == null) return "INCONCLUSIVE";
        return switch (status) {
            case "PASS", "LOCATED", "IDENTIFIED", "MATCH", "RESOLVED", "NO_CONFLICT_OBSERVED" -> "PASS";
            case "FAIL", "CONFLICT", "MISMATCH", "REJECTED_INPUT", "NOT_FOUND" -> "FLAGGED";
            case "SKIPPED", "NOT_RUN", "DISABLED" -> "NOT_RUN";
            case "NOT_IMPLEMENTED", "NOT_SUPPORTED" -> "NOT_SUPPORTED";
            case "NOT_APPLICABLE" -> "NOT_APPLICABLE";
            case "ERROR" -> "ERROR";
            default -> "INCONCLUSIVE";
        };
    }

    private static Double confidence(Object value) {
        if (!(value instanceof Number number)) return null;
        double score = number.doubleValue();
        return Double.isFinite(score) && score >= 0 && score <= 1 ? score : null;
    }

    private static Map<?, ?> object(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static String token(Object value) {
        if (!(value instanceof String text)) return null;
        text = text.trim();
        return !text.isEmpty() && text.length() <= 150 && text.matches("[A-Za-z0-9_.:/-]+") ? text : null;
    }
    private static List<String> codes(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().limit(50).map(DocumentAiEvidence::token).filter(java.util.Objects::nonNull).distinct().toList();
    }
}
