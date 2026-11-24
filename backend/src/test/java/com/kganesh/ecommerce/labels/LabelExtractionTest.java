package com.kganesh.ecommerce.labels;

import com.jayway.jsonpath.JsonPath;
import com.kganesh.ecommerce.IntegrationTest;
import com.kganesh.ecommerce.ShopFixtures;
import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import com.kganesh.ecommerce.labels.LabelReader.LabelReading;
import com.kganesh.ecommerce.labels.LabelReader.ModelAllergen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static com.kganesh.ecommerce.ShopFixtures.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-16 to FR-20: label photos become suggestions an admin reviews; nothing is saved by the AI. */
class LabelExtractionTest extends IntegrationTest {

    /** Smallest bytes that sniff as JPEG; the reader is mocked, so the pixels never matter. */
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @MockitoBean
    LabelReader reader;

    private ShopFixtures shop;

    @BeforeEach
    void setUp() {
        shop = new ShopFixtures(mvc);
        when(reader.enabled()).thenReturn(true);
        when(reader.modelId()).thenReturn("test-model");
    }

    private void modelSays(LabelReading reading) {
        when(reader.read(any(), any())).thenReturn(reading);
    }

    private ResultActions upload(byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/v1/admin/label-extractions")
                .file(new MockMultipartFile("image", "label.jpg", "image/jpeg", bytes))
                .with(ADMIN).with(csrf()));
    }

    private long extractionRows(String id) {
        return jdbc.sql("SELECT count(*) FROM label_extraction WHERE id = :id")
                .param("id", UUID.fromString(id)).query(Long.class).single();
    }

    @Test
    void statusReportsTheModel() throws Exception {
        mvc.perform(get("/api/v1/admin/label-extractions/status").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.model").value("test-model"));
    }

    @Test
    void mapsModelSuggestionsToTheTaxonomyAndRecordsThem() throws Exception {
        modelSays(new LabelReading(true, "Oats, cashews, sugar", "Contains: cashew", "May contain peanuts",
                List.of(new ModelAllergen("TREE_NUT.CASHEW", MatchType.CONTAINS, "cashews"),
                        new ModelAllergen("PEANUT", MatchType.MAY_CONTAIN, "May contain peanuts")),
                List.of(), null));

        String body = upload(JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.readable").value(true))
                .andExpect(jsonPath("$.allergens[*].allergenId", contains("PEANUT", "TREE_NUT.CASHEW")))
                .andExpect(jsonPath("$.allergens[0].matchType").value("MAY_CONTAIN"))
                .andExpect(jsonPath("$.allergens[0].source").value("MODEL_AND_KEYWORD_SCAN"))
                .andExpect(jsonPath("$.allergens[1].matchType").value("CONTAINS"))
                .andExpect(jsonPath("$.allergens[1].displayName").value("Cashew"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.extractionId");
        assertThat(extractionRows(id)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT actor FROM label_extraction WHERE id = :id").param("id", UUID.fromString(id))
                .query(String.class).single()).isEqualTo("admin1");
        assertThat(jdbc.sql("SELECT count(*) FROM label_extraction_item WHERE extraction_id = :id")
                .param("id", UUID.fromString(id)).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void unknownIdsAreNeverSuggested() throws Exception {
        modelSays(new LabelReading(true, "Lupin flour", null, null,
                List.of(new ModelAllergen("LUPIN", MatchType.CONTAINS, "Lupin flour")), List.of("mustard"), null));

        upload(JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allergens", hasSize(0)))
                .andExpect(jsonPath("$.unmatchedTerms", contains("mustard", "Lupin flour")));
    }

    @Test
    void keywordBackstopCatchesWhatTheModelMissed() throws Exception {
        // The model read the text but left out the almonds and called the hazelnut a "may contain".
        modelSays(new LabelReading(true, "Sugar, ALMONDS, hazelnut paste", null, "Made in a facility with sesame",
                List.of(new ModelAllergen("TREE_NUT.HAZELNUT", MatchType.MAY_CONTAIN, "hazelnut")), List.of(), null));

        upload(JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allergens[?(@.allergenId == 'TREE_NUT.ALMOND')].source").value(contains("KEYWORD_SCAN")))
                .andExpect(jsonPath("$.allergens[?(@.allergenId == 'TREE_NUT.ALMOND')].matchType").value(contains("CONTAINS")))
                .andExpect(jsonPath("$.allergens[?(@.allergenId == 'TREE_NUT.ALMOND')].evidence").value(contains("ALMONDS")))
                .andExpect(jsonPath("$.allergens[?(@.allergenId == 'TREE_NUT.HAZELNUT')].matchType").value(contains("CONTAINS")))
                .andExpect(jsonPath("$.allergens[?(@.allergenId == 'SESAME')].matchType").value(contains("MAY_CONTAIN")));
    }

    @Test
    void unreadablePhotoIsReportedNotGuessed() throws Exception {
        modelSays(new LabelReading(false, null, null, null, List.of(), List.of(), "Ingredients are cut off"));

        upload(JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.readable").value(false))
                .andExpect(jsonPath("$.allergens", hasSize(0)))
                .andExpect(jsonPath("$.notes").value("Ingredients are cut off"));
    }

    @Test
    void modelOutageIs503AndRecordsNothing() throws Exception {
        when(reader.read(any(), any())).thenThrow(LabelReaderUnavailable.failed());
        long before = jdbc.sql("SELECT count(*) FROM label_extraction").query(Long.class).single();

        upload(JPEG)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

        assertThat(jdbc.sql("SELECT count(*) FROM label_extraction").query(Long.class).single()).isEqualTo(before);
    }

    @Test
    void disabledIs503() throws Exception {
        when(reader.enabled()).thenReturn(false);
        upload(JPEG)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));
        verify(reader, never()).read(any(), any());
    }

    @Test
    void rejectsFilesThatAreNotSupportedImages() throws Exception {
        upload("%PDF-1.7".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        upload(new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_IMAGE"));
        byte[] huge = new byte[LabelExtractionService.MAX_IMAGE_BYTES + 1];
        System.arraycopy(JPEG, 0, huge, 0, JPEG.length);
        upload(huge)
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        verify(reader, never()).read(any(), any());
    }

    @Test
    void onlyCatalogAdminsCanUseIt() throws Exception {
        mvc.perform(multipart("/api/v1/admin/label-extractions")
                        .file(new MockMultipartFile("image", "label.jpg", "image/jpeg", JPEG))
                        .with(ShopFixtures.newShopper()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/label-extractions/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void savingReviewedAllergensLinksTheExtractionInTheAudit() throws Exception {
        modelSays(new LabelReading(true, "Cashews", null, null,
                List.of(new ModelAllergen("TREE_NUT.CASHEW", MatchType.CONTAINS, "Cashews")), List.of(), null));
        String extractionId = JsonPath.read(
                upload(JPEG).andReturn().getResponse().getContentAsString(), "$.extractionId");
        String product = shop.product("Trail Mix", null);

        // The admin reviewed the suggestion and saved what the label shows.
        mvc.perform(put("/api/v1/admin/products/{id}/allergens", product).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"allergens": [{"allergenId": "TREE_NUT.CASHEW", "matchType": "CONTAINS"}],
                                 "labelExtractionId": "%s"}
                                """.formatted(extractionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergenVersion").value(1));

        mvc.perform(get("/api/v1/admin/products/{id}/audit", product).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].action").value("ALLERGENS_CHANGED"))
                .andExpect(jsonPath("$[1].labelExtractionId").value(extractionId))
                .andExpect(jsonPath("$[0].labelExtractionId").doesNotExist());
    }

    @Test
    void unknownOrMisusedExtractionIdsAreRejected() throws Exception {
        String product = shop.product("Seed Bar", null);
        mvc.perform(put("/api/v1/admin/products/{id}/allergens", product).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allergens\": [], \"labelExtractionId\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_LABEL_EXTRACTION"));

        mvc.perform(post("/api/v1/admin/products").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": \"T-%s\", \"name\": \"No Data\", \"priceCents\": 100, \"labelExtractionId\": \"%s\"}"
                                .formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LABEL_EXTRACTION_WITHOUT_ALLERGENS"));
    }
}
