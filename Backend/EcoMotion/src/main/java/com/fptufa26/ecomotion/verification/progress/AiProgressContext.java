package com.fptufa26.ecomotion.verification.progress;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AiProgressContext implements AutoCloseable {
    public record Snapshot(String stage, String side, Instant updatedAt,
            Long sampledFrames, Double videoSeconds, Instant lastObservedAt, java.util.List<Step> steps) {}
    public record Step(String stage, String side, Double elapsedMs) {}
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private static final Map<UUID, Snapshot> VALUES = new ConcurrentHashMap<>();
    private final UUID id;
    private static final ThreadLocal<java.util.function.Consumer<Snapshot>> LISTENER = new ThreadLocal<>();
    private AiProgressContext(UUID id) { this.id = id; CURRENT.set(id); }
    public static AiProgressContext open(UUID id) { return new AiProgressContext(id); }
    public static AiProgressContext open(UUID id, java.util.function.Consumer<Snapshot> listener) {
        var context = open(id); LISTENER.set(listener); return context;
    }
    public static boolean active() { return CURRENT.get() != null; }
    public static void update(Map<?, ?> progress) {
        UUID id = CURRENT.get();
        if (id == null || progress == null) return;
        Object stage = progress.get("stage"), side = progress.get("side");
        if (!(stage instanceof String s) || !s.matches("[A-Z_]{1,80}")) return;
        VALUES.entrySet().removeIf(e -> e.getValue().updatedAt().isBefore(Instant.now().minusSeconds(600)));
        String safeSide = side instanceof String t && t.matches("[A-Z_]{1,20}") ? t : null;
        Long frames = progress.get("sampledFrames") instanceof Number n && Double.isFinite(n.doubleValue())
                && n.doubleValue() >= 0 && n.doubleValue() <= 3600 && n.doubleValue() == n.longValue() ? n.longValue() : null;
        Double seconds = progress.get("videoSeconds") instanceof Number n && Double.isFinite(n.doubleValue())
                && n.doubleValue() >= 0 && n.doubleValue() <= 30 ? n.doubleValue() : null;
        var steps = new java.util.ArrayList<Step>();
        if (progress.get("events") instanceof java.util.List<?> events) {
            for (Object item : events.subList(Math.max(0, events.size()-80), events.size())) {
                if (!(item instanceof Map<?,?> event) || !(event.get("stage") instanceof String step) || !step.matches("[A-Z_]{1,80}")) continue;
                String stepSide=event.get("side") instanceof String t && t.matches("[A-Z_]{1,20}") ? t : null;
                Double elapsed=event.get("elapsedMs") instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue()>=0 ? n.doubleValue() : null;
                if (steps.isEmpty() || !steps.getLast().stage().equals(step) || !java.util.Objects.equals(steps.getLast().side(),stepSide))
                    steps.add(new Step(step,stepSide,elapsed));
            }
        }
        Instant observed = Instant.now();
        VALUES.compute(id, (key, old) -> new Snapshot(s, safeSide,
                old != null && old.stage().equals(s) && java.util.Objects.equals(old.side(), safeSide)
                    ? old.updatedAt() : observed, frames, seconds, observed, java.util.List.copyOf(steps)));
        if (LISTENER.get() != null) LISTENER.get().accept(VALUES.get(id));
    }
    public static Snapshot get(UUID id) { return id == null ? null : VALUES.get(id); }
    @Override public void close() { VALUES.remove(id); CURRENT.remove(); LISTENER.remove(); }
}
