package com.kganesh.ecommerce.labels;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI settings under {@code app.ai} (spec: AI assist, D19).
 *
 * @param provider "disabled" (default) or "bedrock"
 * @param modelId  Bedrock model or inference-profile id
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String provider, String modelId) {
}
