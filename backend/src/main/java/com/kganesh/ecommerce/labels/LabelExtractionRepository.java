package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.labels.LabelDtos.LabelExtraction;
import com.kganesh.ecommerce.labels.LabelDtos.SuggestedAllergen;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append-only record of extractions and their suggestions (V7). */
@Repository
class LabelExtractionRepository {

    private final JdbcClient jdbc;

    LabelExtractionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(LabelExtraction extraction, String actor, String imageSha256, String mediaType, int imageBytes) {
        jdbc.sql("""
                        INSERT INTO label_extraction (id, actor, model_id, image_sha256, media_type, image_bytes,
                            readable, ingredients_text, contains_statement, may_contain_statement,
                            unmatched_terms, notes)
                        VALUES (:id, :actor, :model, :sha, :mediaType, :bytes, :readable, :ingredients,
                            :contains, :mayContain, :unmatched, :notes)
                        """)
                .param("id", extraction.extractionId())
                .param("actor", actor)
                .param("model", extraction.model())
                .param("sha", imageSha256)
                .param("mediaType", mediaType)
                .param("bytes", imageBytes)
                .param("readable", extraction.readable())
                .param("ingredients", extraction.ingredientsText(), java.sql.Types.VARCHAR)
                .param("contains", extraction.containsStatement(), java.sql.Types.VARCHAR)
                .param("mayContain", extraction.mayContainStatement(), java.sql.Types.VARCHAR)
                .param("unmatched", extraction.unmatchedTerms().toArray(String[]::new))
                .param("notes", extraction.notes(), java.sql.Types.VARCHAR)
                .update();
        for (SuggestedAllergen item : extraction.allergens()) {
            jdbc.sql("""
                            INSERT INTO label_extraction_item (extraction_id, allergen_id, match_type, source, evidence)
                            VALUES (:id, :allergenId, :matchType, :source, :evidence)
                            """)
                    .param("id", extraction.extractionId())
                    .param("allergenId", item.allergenId())
                    .param("matchType", item.matchType().name())
                    .param("source", item.source().name())
                    .param("evidence", item.evidence(), java.sql.Types.VARCHAR)
                    .update();
        }
    }
}
