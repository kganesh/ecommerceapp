package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import com.kganesh.ecommerce.taxonomy.Allergen;

import java.util.List;

/**
 * Reads a product label photo. Implementations only report what is printed; they never
 * decide whether a product is safe and never write to the catalog (spec: AI assist).
 */
public interface LabelReader {

    /** Model id recorded with each extraction, or "disabled". */
    String modelId();

    boolean enabled();

    /**
     * @param taxonomy the allergens the reader may map to, by id
     * @throws LabelReaderUnavailable when the model cannot be reached or gives no usable answer
     */
    LabelReading read(LabelImage image, List<Allergen> taxonomy);

    enum ImageType {
        JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp");

        private final String mediaType;

        ImageType(String mediaType) {
            this.mediaType = mediaType;
        }

        public String mediaType() {
            return mediaType;
        }

        /** Identifies the image from its first bytes; the declared content type is not trusted. */
        static ImageType sniff(byte[] b) {
            if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return JPEG;
            }
            if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
                return PNG;
            }
            if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
                return WEBP;
            }
            return null;
        }
    }

    record LabelImage(byte[] bytes, ImageType type) {
    }

    /** An allergen the model says the label shows, with the printed words it relied on. */
    record ModelAllergen(String allergenId, MatchType matchType, String evidence) {
    }

    /**
     * What the reader found. Text fields hold label wording as printed; any may be null.
     *
     * @param readable false when the label could not be read reliably
     */
    record LabelReading(boolean readable, String ingredientsText, String containsStatement,
                        String mayContainStatement, List<ModelAllergen> allergens,
                        List<String> unmatchedTerms, String notes) {
    }
}
