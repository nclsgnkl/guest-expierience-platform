package de.gxp.system;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity — state only, no persistence methods (Panache repository pattern).
 * Package-private: entities never leave their package (spec §4).
 * Schema and seed come from Flyway {@code V1__foundation_baseline.sql}.
 */
@Entity
@Table(name = "instance_info")
class InstanceInfo {

    @Id
    Short id;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    Instant createdAt;
}
