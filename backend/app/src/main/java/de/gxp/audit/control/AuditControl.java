package de.gxp.audit.control;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.gxp.api.model.AuditPageDto;
import de.gxp.audit.entity.AuditEntry;
import de.gxp.audit.entity.AuditEntryMapper;
import de.gxp.infrastructure.http.ClientAttribution;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Control — the append-only audit trail (auth-hardening A4, REQ-620..624;
 * design D17–D19, D22). This is the feature's one public type; every
 * mutating Control calls {@link #record} explicitly — no JPA listener, no
 * interceptor — so every call site is greppable (D17) and pinned by a text
 * test in {@code de.gxp.audit}.
 *
 * <p><b>Never a value</b> (principle 3): a row carries who / when / attributed
 * address / action / entity / entity id / changed field NAME. The table has
 * no value column at all (V7); {@link #changedFields} compares two DTOs and
 * returns property names only — it never returns, stringifies or logs a value.
 *
 * <p><b>Transactions</b> (D19, REQ-624): {@link #record} joins the mutation's
 * transaction, so a rolled-back change leaves no row claiming it happened.
 * The login outcomes use {@code REQUIRES_NEW}, so a failed login's row
 * survives the {@code 401}. A throttled {@code 429} is never recorded — it
 * must stay free of database work (D20).
 *
 * <p>Logs nothing.
 */
@ApplicationScoped
public class AuditControl {

    // actions
    public static final String ACTION_LOGIN_SUCCESS = "login-success";
    public static final String ACTION_LOGIN_FAILURE = "login-failure";
    /** A via-proxy login that carried no code (D6): nothing verified, answered 401 before the password work. */
    public static final String ACTION_CREATE = "create";
    public static final String ACTION_UPDATE = "update";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_REPLACE = "replace";
    public static final String ACTION_CONFIRM = "confirm";
    public static final String ACTION_RENAME = "rename";
    public static final String ACTION_REVOKE = "revoke";
    public static final String ACTION_USER_CREATE = "user-create";
    public static final String ACTION_USER_DEACTIVATE = "user-deactivate";
    public static final String ACTION_USER_REACTIVATE = "user-reactivate";
    public static final String ACTION_PASSWORD_RESET = "password-reset";
    public static final String ACTION_PASSWORD_CHANGE = "password-change";
    public static final String ACTION_SECOND_FACTOR_RESET = "second-factor-reset";
    public static final String ACTION_SECOND_FACTOR_ENROLL = "second-factor-enroll";
    public static final String ACTION_SECOND_FACTOR_CONFIRM = "second-factor-confirm";
    public static final String ACTION_SECOND_FACTOR_DISABLE = "second-factor-disable";
    public static final String ACTION_RECOVERY_CODES_REGENERATE = "recovery-codes-regenerate";
    /** update-channel D20: a manual apply-now / check-now (the settings change reuses {@link #ACTION_UPDATE}). */
    public static final String ACTION_UPDATE_APPLY = "update-apply";
    public static final String ACTION_UPDATE_CHECK = "update-check";

    // entities
    public static final String ENTITY_USER = "user";
    public static final String ENTITY_PROPERTY = "property";
    public static final String ENTITY_ROOM = "room";
    public static final String ENTITY_STAY = "stay";
    public static final String ENTITY_ASSET = "asset";
    public static final String ENTITY_MENU_CONFIG = "menu-config";
    public static final String ENTITY_ACTION_CARDS = "action-cards";
    public static final String ENTITY_DEVICE = "device";
    public static final String ENTITY_UPDATE = "update";
    public static final String ENTITY_UPDATE_SETTINGS = "update-settings";
    /** The entity id of the property scope for {@code menu-config} / {@code action-cards} (rooms use their id). */
    public static final String PROPERTY_SCOPE = "property";

    /** D22: default 50, at most 200 — the contract carries the same bounds. */
    static final int DEFAULT_PAGE_SIZE = 50;

    private final AuditEntryRepository entries;
    private final ClientAttribution clientAttribution;
    private final SecurityIdentity identity;

    AuditControl(AuditEntryRepository entries, ClientAttribution clientAttribution, SecurityIdentity identity) {
        this.entries = entries;
        this.clientAttribution = clientAttribution;
        this.identity = identity;
    }

    // ------------------------------------------------------------ writing (REQ-621, REQ-624)

    /**
     * Records a manager mutation IN THE CALLER'S TRANSACTION (D19): one row
     * per changed field, or one field-less row when the list is null or
     * empty (create / delete / replace — D18). Actor is the principal name
     * (null when anonymous); address and via-proxy come from the request's
     * {@link ClientAttribution}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public void record(String action, String entity, String entityId, List<String> changedFields) {
        String actor = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        ClientAttribution.Attribution attribution = clientAttribution.attribution();
        if (changedFields == null || changedFields.isEmpty()) {
            entries.persist(row(actor, attribution, action, entity, entityId, null));
            return;
        }
        for (String field : changedFields) {
            entries.persist(row(actor, attribution, action, entity, entityId, field));
        }
    }

    /** A successful login — its own transaction (REQUIRES_NEW); actor and entity id are the username. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordLoginSuccess(String username, ClientAttribution.Attribution attribution) {
        entries.persist(row(username, attribution, ACTION_LOGIN_SUCCESS, ENTITY_USER, username, null));
    }

    /**
     * A failed login — its own transaction, so the row survives the request's
     * {@code 401} (REQ-624). Nobody was authenticated, so the actor is null;
     * {@code matchedUsername} is the canonical name of the account the
     * submitted name resolved to, or null (REQ-620) — the submitted text
     * itself is never stored (principle 3: it may be a password).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordLoginFailure(String matchedUsername, ClientAttribution.Attribution attribution) {
        entries.persist(row(null, attribution, ACTION_LOGIN_FAILURE, ENTITY_USER, matchedUsername, null));
    }


    private static AuditEntry row(String actor, ClientAttribution.Attribution attribution, String action,
            String entity, String entityId, String field) {
        AuditEntry entry = new AuditEntry();
        entry.occurredAt = Instant.now();
        entry.actor = actor;
        entry.address = attribution.address();
        entry.viaProxy = attribution.viaTrustedProxy();
        entry.action = action;
        entry.entity = entity;
        entry.entityId = entityId;
        entry.field = field;
        return entry;
    }

    /**
     * The NAMES of the JavaBean properties whose values differ between two
     * generated DTOs of the same class ({@code before} = the current DTO,
     * {@code after} = the incoming one), sorted. Values are compared with
     * {@link Objects#equals} and never returned, stringified or logged —
     * this method is the only place an audit call ever sees a value, and it
     * lets none out (principle 3).
     */
    public static List<String> changedFields(Object before, Object after) {
        if (before == null || after == null || before.getClass() != after.getClass()) {
            throw new IllegalArgumentException("changedFields compares two DTOs of the same class");
        }
        List<String> changed = new ArrayList<>();
        for (Method getter : before.getClass().getMethods()) {
            String property = propertyOf(getter);
            if (property == null) {
                continue;
            }
            try {
                if (!Objects.equals(getter.invoke(before), getter.invoke(after))) {
                    changed.add(property);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("not a readable DTO property: " + property);
            }
        }
        changed.sort(null);
        return changed;
    }

    /** {@code getX()} / {@code isX()} without parameters, non-static, not {@code getClass} → {@code x}; else null. */
    private static String propertyOf(Method method) {
        if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())
                || method.getDeclaringClass() == Object.class) {
            return null;
        }
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return Introspector.decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2 && method.getReturnType() == boolean.class) {
            return Introspector.decapitalize(name.substring(2));
        }
        return null;
    }

    // ------------------------------------------------------------ reading (REQ-622)

    /** Newest first; {@code page} defaults to 0 and {@code size} to 50 (the contract bounds size to 1..200, D22). */
    public AuditPageDto page(Integer page, Integer size) {
        int pageIndex = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        return new AuditPageDto()
                .entries(entries.newestFirst(pageIndex, pageSize).stream().map(AuditEntryMapper::toDto).toList())
                .page(pageIndex)
                .size(pageSize)
                .total(entries.count());
    }

    /**
     * The audit sequence — {@code max(id)}, {@code 0} on an empty table.
     *
     * <p>Public so the guest ETag may lean on it: constitution v1.8.1 makes
     * "every manager mutation writes an audit row" an obligation, and
     * {@code AuditCoverageTest} derives every {@code @Transactional public}
     * Control method from the sources and fails the build unless it records or
     * is pinned as not-a-manager-mutation. So a monotonically rising sequence
     * here IS a write counter for everything a kiosk renders, without a second
     * counter to keep in step.
     *
     * <p>{@code max(id)}, not {@code count(*)}: {@code pruneOlderThan} runs
     * daily, so the count is not monotonic — the sequence is, as long as any
     * row survives the prune, because ids are never reused and pruning removes
     * the oldest rows. An emptied table restarts at {@code 0}, which costs one
     * extra 200 and never a stale 304.
     *
     * <p>Deliberately NOT {@code @Transactional} — like {@link #page} — so it
     * never enters {@code AuditCoverageTest}'s derived set of mutations. It
     * reads the trail and never writes it.
     */
    public long latestSequence() {
        return entries.maxId();
    }

    // ------------------------------------------------------------ retention (REQ-623)

    /**
     * Deletes every row older than {@code retention} and returns the count —
     * the ONLY deletion that exists. Public because
     * {@code de.gxp.infrastructure.scheduler.AuditScheduler} calls it daily.
     */
    @Transactional
    public long pruneOlderThan(Duration retention) {
        return entries.deleteOlderThan(Instant.now().minus(retention));
    }
}
