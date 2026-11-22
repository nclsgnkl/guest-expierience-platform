package de.gxp.system.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity — state only, no persistence methods (Panache repository pattern).
 * Schema and seed come from Flyway {@code V1__foundation_baseline.sql}.
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.system.control.InstanceInfoRepository} names it
 * (spec §4, constitution v1.6.0). Referencing it from outside
 * {@code de.gxp.system} is a violation — caught by
 * {@code FeatureInternalsImportTest} and {@code EntityExposureTest}, not by the
 * compiler.
 *
 * <p>The FIELDS stay package-private on purpose: nothing outside
 * {@code de.gxp.system.entity} reads or writes them — {@link InstanceInfoMapper}
 * is the only accessor, and it lives here. Widening is done per field, only
 * where a Control genuinely mutates one across the sub-package line.
 */
@Entity
@Table(name = "instance_info")
public class InstanceInfo {

    @Id
    Short id;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    Instant createdAt;
}
