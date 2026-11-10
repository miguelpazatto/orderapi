package com.miguelpazatto.orderapi.integration.infra;

import com.stripe.Stripe;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

public final class StripeWebhookFixtures {
    private StripeWebhookFixtures() { }

    public static String payload(String type, String object, String metadata) {
        return """
                {"id":"evt_test","object":"event","api_version":"%s","type":"%s",
                 "data":{"object":{"id":"obj_test","object":"%s","metadata":%s}}}
                """.formatted(Stripe.API_VERSION, type, object, metadata);
    }

    public static String signature(String payload, String secret, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar fixture", e);
        }
    }

    public static String signature(String payload) {
        return signature(payload, "whsec_integration_test", Instant.now().getEpochSecond());
    }
}
