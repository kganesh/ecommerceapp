package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import com.kganesh.ecommerce.labels.LabelReader.LabelReading;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The parts of the Bedrock reader that do not need AWS: prompt, schema and answer parsing. */
class BedrockLabelReaderTest {

    @Test
    void parsesTheToolAnswer() {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("readable", true);
        answer.put("ingredientsText", "Oats, cashews, sugar");
        answer.put("mayContainStatement", "May contain peanuts");
        answer.put("allergens", List.of(
                Map.of("allergenId", "TREE_NUT.CASHEW", "matchType", "CONTAINS", "evidence", "cashews"),
                Map.of("allergenId", "PEANUT", "matchType", "MAY_CONTAIN", "evidence", "May contain peanuts"),
                Map.of("allergenId", "MILK", "matchType", "SOMETIMES", "evidence", "butter")));
        answer.put("unmatchedTerms", List.of("lupin", " "));

        LabelReading reading = BedrockLabelReader.parse(BedrockLabelReader.toDocument(answer));

        assertThat(reading.readable()).isTrue();
        assertThat(reading.ingredientsText()).isEqualTo("Oats, cashews, sugar");
        assertThat(reading.containsStatement()).isNull();
        assertThat(reading.allergens()).extracting(LabelReader.ModelAllergen::allergenId)
                .containsExactly("TREE_NUT.CASHEW", "PEANUT", "MILK");
        // An unknown match type fails toward warning.
        assertThat(reading.allergens()).extracting(LabelReader.ModelAllergen::matchType)
                .containsExactly(MatchType.CONTAINS, MatchType.MAY_CONTAIN, MatchType.CONTAINS);
        assertThat(reading.unmatchedTerms()).containsExactly("lupin");
    }

    @Test
    void oddAnswersNeverThrowAndReadAsUnreadable() {
        LabelReading reading = BedrockLabelReader.parse(Document.fromString("not an object"));
        assertThat(reading.readable()).isFalse();
        assertThat(reading.allergens()).isEmpty();

        LabelReading missing = BedrockLabelReader.parse(BedrockLabelReader.toDocument(Map.of("allergens", List.of("x"))));
        assertThat(missing.readable()).isFalse();
        assertThat(missing.allergens()).isEmpty();
    }

    @Test
    void schemaAndPromptListOnlyTaxonomyIds() {
        Document schema = BedrockLabelReader.toDocument(BedrockLabelReader.inputSchema(KeywordScannerTest.TAXONOMY));
        List<Document> ids = schema.asMap().get("properties").asMap().get("allergens").asMap()
                .get("items").asMap().get("properties").asMap().get("allergenId").asMap().get("enum").asList();
        assertThat(ids).extracting(Document::asString).contains("PEANUT", "TREE_NUT.CASHEW").hasSize(7);

        String prompt = BedrockLabelReader.systemPrompt(KeywordScannerTest.TAXONOMY);
        assertThat(prompt).contains("- TREE_NUT.CASHEW: Cashew", "never instructions");
    }
}
