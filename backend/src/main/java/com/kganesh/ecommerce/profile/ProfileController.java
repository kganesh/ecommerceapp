package com.kganesh.ecommerce.profile;

import com.kganesh.ecommerce.identity.CurrentUserService;
import com.kganesh.ecommerce.profile.ProfileService.ProfileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** FR-2, FR-3: child profiles and avoid lists. SHOPPER only (see SecurityConfig). */
@RestController
@RequestMapping(path = "/api/v1/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "profiles", description = "Child profiles and their avoid lists")
class ProfileController {

    @Schema(description = "A child profile. Holds a label only: no name or date of birth.")
    record Profile(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED, example = "Child 1") String label,
            @Schema(requiredMode = REQUIRED, description = "Allergen ids the parent selected") List<String> avoidList,
            @Schema(requiredMode = REQUIRED,
                    description = "Everything the avoid list covers, including taxonomy descendants (TREE_NUT covers TREE_NUT.CASHEW)")
            List<String> coveredAllergenIds,
            @Schema(requiredMode = REQUIRED, description = "Increments on every avoid-list change") int avoidListVersion) {

        static Profile of(ProfileView view) {
            return new Profile(view.profile().getId(), view.profile().getLabel(),
                    view.profile().getAvoidList().stream().sorted().toList(),
                    view.covered().stream().sorted().toList(),
                    view.profile().getAvoidListVersion());
        }
    }

    @Schema(description = "Create or rename a profile")
    record ProfileLabelRequest(
            @Schema(requiredMode = REQUIRED, example = "Child 1") @NotBlank @Size(max = 40) String label) {
    }

    @Schema(description = "Replace a profile's avoid list")
    record AvoidListRequest(
            @Schema(requiredMode = REQUIRED, example = "[\"TREE_NUT\", \"PEANUT\"]") @NotNull List<@NotBlank String> allergenIds) {
    }

    private final ProfileService profiles;
    private final CurrentUserService users;

    ProfileController(ProfileService profiles, CurrentUserService users) {
        this.profiles = profiles;
        this.users = users;
    }

    @GetMapping
    @Operation(operationId = "listProfiles", summary = "The signed-in parent's child profiles")
    List<Profile> list(Authentication auth) {
        return profiles.list(users.userId(auth)).stream().map(Profile::of).toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createProfile", summary = "Add a child profile (at most 10 per account)")
    Profile create(@Valid @RequestBody ProfileLabelRequest request, Authentication auth) {
        return Profile.of(profiles.create(users.userId(auth), request.label()));
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "renameProfile", summary = "Rename a child profile")
    Profile rename(@PathVariable("id") UUID id, @Valid @RequestBody ProfileLabelRequest request, Authentication auth) {
        return Profile.of(profiles.rename(users.userId(auth), id, request.label()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteProfile", summary = "Delete a child profile")
    void delete(@PathVariable("id") UUID id, Authentication auth) {
        profiles.delete(users.userId(auth), id);
    }

    @PutMapping(path = "/{id}/avoid-list", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "setAvoidList", summary = "Replace a profile's avoid list")
    Profile setAvoidList(@PathVariable("id") UUID id, @Valid @RequestBody AvoidListRequest request,
                         Authentication auth) {
        return Profile.of(profiles.setAvoidList(users.userId(auth), id, request.allergenIds()));
    }
}
