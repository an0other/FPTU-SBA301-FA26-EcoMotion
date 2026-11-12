package com.fptufa26.ecomotion.verification.documentai;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Data completeness for presentation only. Never an identity or fraud score. */
public record DocumentCompleteness(String policyVersion, int completedCount, int totalCount,
        int percentage, List<Item> fields) {
    public record Item(String name, String status) {}
    private static final List<String> ID_FIELDS = List.of("documentNumber", "fullName", "dateOfBirth", "expiryDate");
    private static final List<String> LICENSE_FIELDS = List.of("documentNumber", "fullName", "dateOfBirth", "licenseClass", "issueDate", "expiryDate");
    private static final Set<String> READ_STATES = Set.of("EXACT_MATCH", "UNACCENTED_MATCH", "OCR_ONLY", "OCR_ONLY_UNACCENTED");

    public static DocumentCompleteness from(Map<String, Object> raw, List<DocumentAiEvidence.FieldEvidence> observed) {
        String type = string(raw.get("documentType"));
        List<String> required;
        if ("VN_CITIZEN_ID".equals(type)) required = ID_FIELDS;
        else if ("VN_DRIVING_LICENSE".equals(type)) required = LICENSE_FIELDS;
        else return null;
        Map<?, ?> data = raw.get("data") instanceof Map<?, ?> map ? map : Map.of();
        var missing = keys(raw.get("missingFields"));
        var inconsistent = keys(raw.get("inconsistentFields"));
        var flagged = keys(raw.get("optionalReviewFields"));
        observed.stream().filter(DocumentAiEvidence.FieldEvidence::needsReview).map(DocumentAiEvidence.FieldEvidence::name).forEach(flagged::add);
        var metadata = new HashMap<String, DocumentAiEvidence.FieldEvidence>();
        observed.forEach(field -> metadata.put(field.name(), field));
        // Some upstream versions only identify uncertainty at document level. Do not invent which field is reliable.
        boolean unresolvedReview = keys(raw.get("statusReasons")).contains("REQUIRED_FIELDS_NEED_REVIEW") && flagged.isEmpty();
        var items = new ArrayList<Item>();
        for (String key : required) {
            String value = string(data.get(key));
            String state;
            if (value == null || missing.contains(key)) state = "MISSING";
            else if (!valid(key, value, type)) state = "INVALID";
            else if (inconsistent.contains(key) || flagged.contains(key) || unresolvedReview) state = "REVIEW";
            else {
                var field = metadata.get(key);
                state = field != null && READ_STATES.contains(field.status() == null ? "" : field.status()) ? "READ" : "REVIEW";
            }
            items.add(new Item(key, state));
        }
        int count = (int) items.stream().filter(item -> "READ".equals(item.status())).count();
        return new DocumentCompleteness("document-completeness/v1", count, required.size(),
                (int) Math.round(count * 100.0 / required.size()), List.copyOf(items));
    }

    private static boolean valid(String key, String value, String type) {
        return switch (key) {
            case "documentNumber" -> "VN_CITIZEN_ID".equals(type) ? value.matches("[0-9]{12}") : value.matches("[A-Za-z0-9-]{1,20}");
            case "fullName" -> value.length() <= 150 && value.codePoints().anyMatch(Character::isLetter);
            case "licenseClass" -> value.matches("[A-Za-z0-9]{1,10}");
            case "dateOfBirth", "issueDate", "expiryDate" -> validDate(value) || "expiryDate".equals(key) && "VN_DRIVING_LICENSE".equals(type) && "UNLIMITED".equals(value);
            default -> false;
        };
    }
    private static boolean validDate(String text) {
        try { LocalDate.parse(text); return true; } catch (DateTimeParseException ignored) { return false; }
    }
    private static String string(Object value) { return value instanceof String s && !s.isBlank() ? s.trim() : null; }
    private static Set<String> keys(Object value) {
        var result = new HashSet<String>();
        if (value instanceof List<?> values) values.stream().filter(String.class::isInstance).map(String.class::cast).limit(50).forEach(result::add);
        return result;
    }
}
