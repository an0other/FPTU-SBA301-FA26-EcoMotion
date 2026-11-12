package com.fptufa26.ecomotion.verification.progress;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.Map;
import java.time.Duration;

public final class AiJobPolling {
    private static final ParameterizedTypeReference<Map<String, Object>> TYPE = new ParameterizedTypeReference<>() {};
    private AiJobPolling() {}
    public static Map<String, Object> run(RestClient client, String url, String key, Object body, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Map<String, Object> accepted = client.post().uri(url).header("X-API-Key", key)
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(TYPE);
        Object id = accepted == null ? null : accepted.get("jobId");
        if (!(id instanceof String job) || !job.matches("[a-f0-9]{32}")) throw new RestClientException("Invalid AI job id");
        while (System.nanoTime() < deadline) {
            Map<String, Object> state = client.get().uri(url + "/" + job).header("X-API-Key", key).retrieve().body(TYPE);
            if (state == null) throw new RestClientException("Empty AI job response");
            if (state.get("progress") instanceof Map<?, ?> progress) {
                Map<String,Object> snapshot=new java.util.LinkedHashMap<>();
                progress.forEach((k,v)->snapshot.put(String.valueOf(k),v));
                snapshot.put("events",state.get("events"));
                AiProgressContext.update(snapshot);
            }
            if ("COMPLETED".equals(state.get("state"))) {
                if (!(state.get("result") instanceof Map<?, ?> result)) throw new RestClientException("Missing AI job result");
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                result.forEach((k, v) -> response.put(String.valueOf(k), v));
                return response;
            }
            if ("FAILED".equals(state.get("state"))) {
                if (state.get("error") instanceof Map<?, ?> error && Integer.valueOf(422).equals(error.get("status")))
                    throw org.springframework.web.client.HttpClientErrorException.create(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                            "AI input rejected", new org.springframework.http.HttpHeaders(), new byte[0], java.nio.charset.StandardCharsets.UTF_8);
                throw new RestClientException("AI job failed");
            }
            if (!("QUEUED".equals(state.get("state")) || "RUNNING".equals(state.get("state")))) throw new RestClientException("Invalid AI job state");
            try { Thread.sleep(1000); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RestClientException("AI polling interrupted", error); }
        }
        throw new org.springframework.web.client.ResourceAccessException("AI job timeout", new java.net.SocketTimeoutException());
    }
}
