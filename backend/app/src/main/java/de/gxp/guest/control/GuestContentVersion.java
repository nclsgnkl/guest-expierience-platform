package de.gxp.guest.control;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import de.gxp.api.model.StayDto;
import de.gxp.audit.control.AuditControl;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control collaborator (package-private, REQ-946/REQ-947) — the guest
 * content version stamp: a 64-hex value that changes whenever what THIS room
 * renders could have changed, computed WITHOUT composing or serializing the
 * payload. That is the whole point: the retired response filter that used to
 * tag every guest JSON 200 hashed the response BYTES, so every steady-state
 * {@code 304} still cost a full resolve plus a serialize.
 *
 * <p>Five terms, all cheap:
 * <ul>
 * <li><b>bootId</b> — a {@link UUID} fixed at construction. Covers everything
 * that changes content without writing a row: migrations, seeders, the
 * server-side settings-chrome constants, and any release delivered by the
 * signed update channel.</li>
 * <li><b>audit sequence</b> — {@link AuditControl#latestSequence()}. Every
 * manager mutation writes an audit row (constitution v1.8.1) and
 * {@code AuditCoverageTest} enforces it, so this is a write counter for
 * content with no second counter to keep in step.</li>
 * <li><b>roomId</b> — two rooms must never share a stamp (REQ-947), even when
 * their content is identical.</li>
 * <li><b>stay fingerprint</b> — covers the two invalidations that write
 * nothing at all: the CLOCK-driven occupancy transitions (check-in and
 * check-out just happen) and the guest's own preference write, which is
 * legitimately not a manager mutation.</li>
 * <li><b>refresh bucket</b> — see {@link #STAMP_REFRESH_SECONDS}.</li>
 * </ul>
 */
@ApplicationScoped
class GuestContentVersion {

    /**
     * The stamp changes at least this often, and that is <b>not</b> padding —
     * it is the bound on every invalidation the other four terms can miss.
     *
     * <p>Two holes exist and neither closes by inspection:
     * <ol>
     * <li><b>{@code max(id)} is allocated at INSERT, not at COMMIT.</b> Two
     * overlapping audit writers can commit out of order: a manager edit
     * inserts id 100 and stays open, the scheduled update check inserts 101
     * and commits, a poll lands, reads {@code max = 101}, composes the
     * PRE-edit payload and answers 200 + ETag(101) — then the edit commits.
     * {@code max} is still 101, so every later poll is a 304 and the edit
     * never reaches the kiosk. Permanently, until some unrelated row is
     * written.</li>
     * <li><b>Not every content-affecting write is a manager mutation.</b>
     * {@code AuditCoverageTest}'s {@code NOT_A_MANAGER_MUTATION} list
     * legitimately holds guest-side writes, so "everything is audited" is a
     * statement about manager writes only.</li>
     * </ol>
     *
     * <p>Bucketing turns both from unbounded silent staleness into at most
     * five minutes of latency. At the kiosk's 30 s poll that is nine 304s and
     * one 200 — REQ-946 wins ~90% of polls rather than 100%, and REQ-947 is
     * robust instead of exhaustive-by-inspection. <b>Do not delete this term.</b>
     */
    static final long STAMP_REFRESH_SECONDS = 300;

    private static final String VACANT = "vacant";

    /** Fixed for the life of the process — see the class comment's bootId term. */
    private final String bootId = UUID.randomUUID().toString();

    private final AuditControl auditControl;

    GuestContentVersion(AuditControl auditControl) {
        this.auditControl = auditControl;
    }

    /** The stamp for one room's content, {@code stay} being its active stay or null. */
    String stampFor(long roomId, StayDto stay) {
        String stamp = bootId
                + '|' + auditControl.latestSequence()
                + '|' + roomId
                + '|' + fingerprintOf(stay)
                + '|' + Instant.now().getEpochSecond() / STAMP_REFRESH_SECONDS;
        return sha256Hex(stamp);
    }

    /**
     * {@code vacant}, or the stay fields a kiosk can see rendered. Written
     * out rather than hashed from the DTO: only these fields reach the guest
     * payload, and an unrelated stay column must not churn every kiosk ETag.
     */
    private static String fingerprintOf(StayDto stay) {
        if (stay == null) {
            return VACANT;
        }
        return stay.getId()
                + ":" + stay.getDisplayName()
                + ":" + stay.getCheckIn().toInstant().toEpochMilli()
                + ":" + stay.getCheckOut().toInstant().toEpochMilli()
                + ":" + stay.getLanguage()
                + ":" + stay.getAppearance();
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
