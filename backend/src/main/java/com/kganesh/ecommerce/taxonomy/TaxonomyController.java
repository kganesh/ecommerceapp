package com.kganesh.ecommerce.taxonomy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/allergens")
@Tag(name = "allergens", description = "Allergen taxonomy for avoid lists and product tagging")
class TaxonomyController {

    private final TaxonomyService taxonomy;

    TaxonomyController(TaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listAllergens", summary = "The full allergen taxonomy, roots before children")
    List<Allergen> list() {
        return taxonomy.listAll();
    }
}
