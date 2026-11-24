package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.taxonomy.Allergen;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Default: no AI. The admin UI hides the label-photo control. */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "disabled", matchIfMissing = true)
class DisabledLabelReader implements LabelReader {

    @Override
    public String modelId() {
        return "disabled";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public LabelReading read(LabelImage image, List<Allergen> taxonomy) {
        throw LabelReaderUnavailable.disabled();
    }
}
