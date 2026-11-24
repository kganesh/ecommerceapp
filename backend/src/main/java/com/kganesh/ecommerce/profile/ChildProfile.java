package com.kganesh.ecommerce.profile;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** A child's profile: a display label and the allergens to avoid (FR-2, FR-3). */
@Entity
@Table(name = "child_profile")
public class ChildProfile {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    @Column(name = "avoid_list_version", nullable = false)
    private int avoidListVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "avoid_list_entry", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "allergen_id", nullable = false, length = 64)
    private Set<String> avoidList = new TreeSet<>();

    protected ChildProfile() {
        // for JPA
    }

    ChildProfile(UUID userId, String label) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.label = label;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void rename(String newLabel) {
        this.label = newLabel;
        this.updatedAt = Instant.now();
    }

    /** Replaces the avoid list; bumps the version only when the set actually changes. */
    boolean replaceAvoidList(Set<String> allergenIds) {
        if (avoidList.equals(allergenIds)) {
            return false;
        }
        avoidList.clear();
        avoidList.addAll(allergenIds);
        avoidListVersion++;
        updatedAt = Instant.now();
        return true;
    }

    void softDelete() {
        Instant now = Instant.now();
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public int getAvoidListVersion() {
        return avoidListVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Set<String> getAvoidList() {
        return Set.copyOf(avoidList);
    }
}
