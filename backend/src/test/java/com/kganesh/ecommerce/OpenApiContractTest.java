package com.kganesh.ecommerce;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guards the API contract in docs/openapi.json, which the frontend's TypeScript
 * client is generated from. Fails when the running backend's spec drifts from the
 * checked-in contract.
 *
 * <p>After an intended API change: {@code make openapi} (runs this test with
 * {@code -Dopenapi.update=true}) and commit the regenerated files.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("..", "docs", "openapi.json");
    private static final Path GENERATED = Path.of("target", "openapi.generated.json");

    @Value("${local.server.port}")
    int port;

    @Test
    void generatedSpecMatchesCheckedInContract() throws Exception {
        String generated = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build(),
                        HttpResponse.BodyHandlers.ofString())
                .body();
        if (!generated.endsWith("\n")) {
            generated = generated + "\n";
        }
        Files.createDirectories(GENERATED.getParent());
        Files.writeString(GENERATED, generated);

        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(CONTRACT, generated);
            return;
        }

        String contract = Files.readString(CONTRACT);
        JSONAssert.assertEquals(
                "Backend API drifted from docs/openapi.json. If intended, run `make openapi` and commit. "
                        + "Generated spec: backend/" + GENERATED,
                contract, generated, JSONCompareMode.NON_EXTENSIBLE);
    }
}
