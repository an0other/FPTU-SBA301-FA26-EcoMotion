package com.fptufa26.ecomotion.finance.service.gateway;

import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.config.VnpayUtils;
import com.fptufa26.ecomotion.finance.service.gateway.implement.VnpayPaymentGatewayStrategy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class VnpayReconciliationTest {
    private final String secret = "TEST_SECRET";

    private Map<String, String> response(String txnStatus) {
        Map<String, String> response = new HashMap<>();
        response.put("vnp_ResponseId", "response-id");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Success");
        response.put("vnp_TmnCode", "TEST");
        response.put("vnp_TxnRef", "ref");
        response.put("vnp_Amount", "144000000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261029140000");
        response.put("vnp_TransactionNo", "123456");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", txnStatus);
        response.put("vnp_OrderInfo", "Query payment ref");
        String payload = "response-id|querydr|00|Success|TEST|ref|144000000|NCB|20261029140000|123456|01|"
                + txnStatus + "|Query payment ref||";
        response.put("vnp_SecureHash", VnpayUtils.hmacSHA512(secret, payload));
        return response;
    }

    private java.util.Optional<Map<String, String>> query(Map<String, String> response) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requestBody = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/transaction", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String json = response.entrySet().stream().map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            VnpayConfig config = new VnpayConfig();
            config.setReconciliationEnabled(true);
            config.setHashSecret(secret);
            config.setTmnCode("TEST");
            config.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/transaction");
            var result = new VnpayPaymentGatewayStrategy(config).queryTransaction("ref", Instant.parse("2026-10-29T06:00:00Z"), Instant.now());
            assertThat(requestBody.get()).contains("vnp_TransactionDate", "20261029130000", "vnp_SecureHash", "querydr");
            return result;
        } finally {
            server.stop(0);
        }
    }

    @Test
    void verifiesSignedSuccessAndFailureResponses() throws Exception {
        assertThat(query(response("00"))).isPresent();
        assertThat(query(response("02"))).isPresent();
    }

    @Test
    void unfinishedQueryMustRemainPending() throws Exception {
        assertThat(query(response("01"))).isEmpty();
    }

    @Test
    void rejectsTamperedQueryResponse() throws Exception {
        var response = response("00");
        response.put("vnp_Amount", "1");
        assertThat(query(response)).isEmpty();
    }

    @Test
    void unreachableGatewayNeverManufacturesSuccess() {
        var config = new VnpayConfig();
        config.setHashSecret(secret);
        config.setTmnCode("TEST");
        config.setReconciliationEnabled(true);
        config.setApiUrl("http://127.0.0.1:1/transaction");
        config.setQueryTimeout(Duration.ofMillis(100));
        assertThat(new VnpayPaymentGatewayStrategy(config).queryTransaction("ref", Instant.now(), Instant.now())).isEmpty();
    }
}
