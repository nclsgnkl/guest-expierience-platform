package de.gxp.devseed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * {@code %dev}-only convenience seed (guest-frontend-integration, REQ-503;
 * 02-design.md §7, decision D2 approved 2026-08-20): a fresh
 * {@code quarkus:dev} database gets one paired device, an active stay and the
 * example-property content, so the Angular dev server renders a fully paired,
 * occupied kiosk with zero manual pairing.
 *
 * <p><b>{@code %prod} inertness is build-time</b> (REQ-504):
 * {@code @IfBuildProfile("dev")} removes this bean from the container at
 * augmentation — it does not exist in the {@code %prod} or {@code %test}
 * artifact. The dev token literal below is deliberately public knowledge
 * (documented in {@code backend/README.md}); its safety derives from its hash
 * never existing in a production database, never from secrecy.
 *
 * <p>Seeding is plain SQL against the migrated schema (the
 * {@code de.gxp.testsupport.DeviceFixtures} precedent): the packages' write
 * paths are admin-role Controls that a startup observer has no identity to
 * call, and their invariants are re-enforced here by the same DB constraints
 * the migrations installed (hex-shape checks, sort-index bounds, scope
 * uniqueness). SHA-256-hex is the identical algorithm
 * {@code de.gxp.device.PairingCodes}/{@code de.gxp.auth.TokenStore} use; both
 * are package-private, hence the local three-line JDK call (same reasoning as
 * {@code DeviceFixtures}).
 *
 * <p>Idempotent: when the dev device row already exists (a live-reload
 * restart against the same Dev Services database) the whole seed is a no-op,
 * so nothing a developer edited via the manager API is ever overwritten.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevDataSeeder {

    /** Public, dev-only (D2/D3) — see the class comment and backend/README.md. */
    public static final String DEV_DEVICE_TOKEN = "gxp-dev-device-token";

    private static final Map<String, Map<String, String>> PROPERTY_TRANSLATIONS = Map.of(
            "de", Map.of(
                    "hero_subtitle", "Schön, dass ihr da seid",
                    "headline", "Willkommen im Airbnb von {stay.displayName}!",
                    "paragraph", "Entspannt euch, genießt die Zeit und fühlt euch wie zuhause.",
                    "standby_headline", "{property.name}",
                    "standby_message", "Herzlich willkommen"),
            "en", Map.of(
                    "hero_subtitle", "Great to have you here",
                    "headline", "Welcome to {stay.displayName}'s Airbnb!",
                    "paragraph", "Relax, enjoy your stay and make yourself at home.",
                    "standby_headline", "{property.name}",
                    "standby_message", "Welcome"));

    /** sortIndex, icon, targetKey, de title, de description, en title, en description. */
    private record Card(int sortIndex, String icon, String targetKey,
            String titleDe, String descriptionDe, String titleEn, String descriptionEn) {
    }

    private static final List<Card> CARDS = List.of(
            new Card(0, "phosphorKey", "info",
                    "Check-Out", "Bis 11:00 Uhr am Abreisetag",
                    "Check-out", "By 11:00 on departure day"),
            new Card(1, "phosphorLightbulbFilament", "smart-home",
                    "Smart Home", "Alles im Haus auf einen Blick",
                    "Smart Home", "Everything at a glance"),
            new Card(2, "phosphorMapPin", "umgebung",
                    "Umgebung", "Tipps, Restaurants & Aktivitäten",
                    "Nearby", "Tips, restaurants & activities"),
            new Card(3, "phosphorFileText", "info",
                    "Wichtige Infos", "Hausregeln, WLAN, Parken & mehr",
                    "Key info", "House rules, Wi-Fi, parking & more"));

    private final EntityManager entityManager;

    DevDataSeeder(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        seedIfMissing();
    }

    void seedIfMissing() {
        if (devDeviceExists()) {
            return; // seeded before — never touch what a developer changed since
        }
        List<?> defaultRoom = defaultRoomIds();
        if (defaultRoom.isEmpty()) {
            return; // room '1' renamed or deleted via the manager API — see below
        }
        long roomId = ((Number) defaultRoom.get(0)).longValue();
        seedExampleContent();
        seedActiveStay(roomId);
        seedDevice(roomId);
    }

    private boolean devDeviceExists() {
        Number count = (Number) entityManager
                .createNativeQuery("select count(*) from device where token_hash = :hash")
                .setParameter("hash", sha256Hex(DEV_DEVICE_TOKEN))
                .getSingleResult();
        return count.longValue() > 0;
    }

    /**
     * The V2-seeded default room ('Zimmer 1', code '1') — as a list, so its
     * absence is a fact and not an exception. A developer who deletes or
     * renames that room through the manager API would otherwise turn the next
     * {@code quarkus:dev} start into a {@code NoResultException} thrown from a
     * {@code StartupEvent} observer, i.e. an app that will not boot, with a
     * stack trace pointing at seeding rather than at the cause (review finding
     * 🟡-8). The seed is convenience and never a hard dependency: no room, no
     * seed, boot continues.
     */
    private List<?> defaultRoomIds() {
        return entityManager
                .createNativeQuery("select id from room where code = '1'")
                .setMaxResults(1)
                .getResultList();
    }

    /**
     * The previous static frontend config (`example-property.data.ts`,
     * deleted by D6) moved here verbatim, with the one contract-mandated
     * adaptation: the client-side {@code {guestName}} placeholder becomes the
     * server template {@code {stay.displayName}} (the only greeting
     * placeholder the template grammar knows — REQ-511).
     */
    private void seedExampleContent() {
        entityManager.createNativeQuery("update property set"
                + " brand_primary = '#f45aa8',"
                + " brand_primary_deep = '#cc49af',"
                + " brand_secondary = '#8a5cf6'"
                + " where id = 1")
                .executeUpdate();
        for (Map.Entry<String, Map<String, String>> language : PROPERTY_TRANSLATIONS.entrySet()) {
            Map<String, String> fields = language.getValue();
            entityManager.createNativeQuery("insert into property_translation"
                    + " (property_id, language, hero_subtitle, headline, paragraph,"
                    + "  standby_headline, standby_message)"
                    + " values (1, :language, :hero, :headline, :paragraph, :standbyHeadline, :standbyMessage)"
                    + " on conflict (property_id, language) do update set"
                    + " hero_subtitle = excluded.hero_subtitle,"
                    + " headline = excluded.headline,"
                    + " paragraph = excluded.paragraph,"
                    + " standby_headline = excluded.standby_headline,"
                    + " standby_message = excluded.standby_message")
                    .setParameter("language", language.getKey())
                    .setParameter("hero", fields.get("hero_subtitle"))
                    .setParameter("headline", fields.get("headline"))
                    .setParameter("paragraph", fields.get("paragraph"))
                    .setParameter("standbyHeadline", fields.get("standby_headline"))
                    .setParameter("standbyMessage", fields.get("standby_message"))
                    .executeUpdate();
        }
        seedActionCards();
    }

    /** Property-scope cards, only when none exist (0 cards is a valid config). */
    private void seedActionCards() {
        Number existing = (Number) entityManager
                .createNativeQuery("select count(*) from action_card_config where room_id is null")
                .getSingleResult();
        if (existing.longValue() > 0) {
            return;
        }
        for (Card card : CARDS) {
            Number cardId = (Number) entityManager.createNativeQuery("insert into action_card_config"
                    + " (room_id, sort_index, icon, target_menu_item_key)"
                    + " values (null, :sortIndex, :icon, :targetKey) returning id")
                    .setParameter("sortIndex", card.sortIndex())
                    .setParameter("icon", card.icon())
                    .setParameter("targetKey", card.targetKey())
                    .getSingleResult();
            insertCardTranslation(cardId.longValue(), "de", card.titleDe(), card.descriptionDe());
            insertCardTranslation(cardId.longValue(), "en", card.titleEn(), card.descriptionEn());
        }
    }

    private void insertCardTranslation(long cardId, String language, String title, String description) {
        entityManager.createNativeQuery("insert into action_card_config_translation"
                + " (card_id, language, title_template, description_template)"
                + " values (:cardId, :language, :title, :description)")
                .setParameter("cardId", cardId)
                .setParameter("language", language)
                .setParameter("title", title)
                .setParameter("description", description)
                .executeUpdate();
    }

    /** An active stay (checked in yesterday, out in 30 days) → OCCUPIED. */
    private void seedActiveStay(long roomId) {
        Number active = (Number) entityManager
                .createNativeQuery("select count(*) from stay where room_id = :roomId"
                        + " and status = 'BOOKED' and check_in <= now() and now() < check_out")
                .setParameter("roomId", roomId)
                .getSingleResult();
        if (active.longValue() > 0) {
            return;
        }
        entityManager.createNativeQuery("insert into stay"
                + " (room_id, display_name, check_in, check_out, status)"
                + " values (:roomId, 'Anna & Tom', :checkIn, :checkOut, 'BOOKED')")
                .setParameter("roomId", roomId)
                .setParameter("checkIn", Instant.now().minus(1, ChronoUnit.DAYS))
                .setParameter("checkOut", Instant.now().plus(30, ChronoUnit.DAYS))
                .executeUpdate();
    }

    /** Hash-only insert — the raw token never touches the database (REQ-408 posture). */
    private void seedDevice(long roomId) {
        entityManager.createNativeQuery("insert into device (room_id, name, token_hash, created_at)"
                + " values (:roomId, 'Dev-Kiosk', :hash, now())")
                .setParameter("roomId", roomId)
                .setParameter("hash", sha256Hex(DEV_DEVICE_TOKEN))
                .executeUpdate();
    }

    private static String sha256Hex(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
