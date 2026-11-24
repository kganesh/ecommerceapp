package com.kganesh.ecommerce.profile;

import com.kganesh.ecommerce.common.ApiExceptions;
import com.kganesh.ecommerce.taxonomy.TaxonomyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Child profiles and their avoid lists (FR-2, FR-3). Every query is scoped to the owner. */
@Service
public class ProfileService {

    static final int MAX_PROFILES = 10;

    private final ChildProfileRepository profiles;
    private final TaxonomyService taxonomy;

    ProfileService(ChildProfileRepository profiles, TaxonomyService taxonomy) {
        this.profiles = profiles;
        this.taxonomy = taxonomy;
    }

    /** A profile with its avoid list and everything that avoid list covers in the taxonomy. */
    public record ProfileView(ChildProfile profile, Set<String> covered) {
    }

    @Transactional(readOnly = true)
    public List<ProfileView> list(UUID userId) {
        return profiles.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(userId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public ProfileView create(UUID userId, String label) {
        if (profiles.countByUserIdAndDeletedAtIsNull(userId) >= MAX_PROFILES) {
            throw new ApiExceptions.Conflict("TOO_MANY_PROFILES", "An account can have at most " + MAX_PROFILES + " profiles");
        }
        return view(profiles.save(new ChildProfile(userId, clean(label))));
    }

    @Transactional
    public ProfileView rename(UUID userId, UUID profileId, String label) {
        ChildProfile profile = owned(userId, profileId);
        profile.rename(clean(label));
        return view(profile);
    }

    @Transactional
    public void delete(UUID userId, UUID profileId) {
        owned(userId, profileId).softDelete();
    }

    /**
     * Replaces the avoid list. Unknown allergen ids are rejected, never dropped, so a
     * typo can't silently shrink what the allergen check protects against.
     */
    @Transactional
    public ProfileView setAvoidList(UUID userId, UUID profileId, List<String> allergenIds) {
        ChildProfile profile = owned(userId, profileId);
        Set<String> ids = new TreeSet<>(allergenIds);
        taxonomy.requireKnown(ids);
        profile.replaceAvoidList(ids);
        return view(profile);
    }

    private ChildProfile owned(UUID userId, UUID profileId) {
        // Another user's profile reads as "not found": its existence is not revealed.
        return profiles.findByIdAndUserIdAndDeletedAtIsNull(profileId, userId)
                .orElseThrow(() -> new ApiExceptions.NotFound("Profile " + profileId + " not found"));
    }

    private ProfileView view(ChildProfile profile) {
        return new ProfileView(profile, taxonomy.expand(profile.getAvoidList()));
    }

    private static String clean(String label) {
        String trimmed = label == null ? "" : label.strip();
        if (trimmed.isEmpty() || trimmed.length() > 40) {
            throw new ApiExceptions.BadRequest("BAD_LABEL", "Label must be 1 to 40 characters");
        }
        return trimmed;
    }
}
