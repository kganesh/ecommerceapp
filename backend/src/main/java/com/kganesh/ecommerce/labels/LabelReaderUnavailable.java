package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.common.ApiExceptions;
import org.springframework.http.HttpStatus;

/** The AI service could not give an answer. Manual allergen entry still works (FR-20). */
public class LabelReaderUnavailable extends ApiExceptions.ApiException {

    public LabelReaderUnavailable(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    static LabelReaderUnavailable disabled() {
        return new LabelReaderUnavailable("AI_DISABLED",
                "Label reading is turned off. Enter the allergens from the label by hand.");
    }

    static LabelReaderUnavailable failed() {
        return new LabelReaderUnavailable("AI_UNAVAILABLE",
                "Label reading is unavailable right now. Enter the allergens from the label by hand, or try again.");
    }
}
