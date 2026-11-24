package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.labels.LabelDtos.LabelExtraction;
import com.kganesh.ecommerce.labels.LabelDtos.LabelReadingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** AI label reading for catalog admins (FR-16 to FR-20). CATALOG_ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping(path = "/api/v1/admin/label-extractions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "admin-label-extractions", description = "Suggest allergens from a label photo; an admin reviews before saving")
class LabelExtractionController {

    private final LabelExtractionService service;

    LabelExtractionController(LabelExtractionService service) {
        this.service = service;
    }

    @GetMapping("/status")
    @Operation(operationId = "adminLabelReadingStatus", summary = "Whether label reading is turned on, and which model")
    LabelReadingStatus status() {
        return service.status();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "adminExtractLabel",
            summary = "Read a label photo and suggest allergens. Saves nothing to the catalog.")
    LabelExtraction extract(@RequestPart("image") MultipartFile image, Authentication auth) throws IOException {
        return service.extract(image.getBytes(), auth.getName());
    }
}
