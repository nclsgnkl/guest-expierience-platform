#!/usr/bin/env bash
# rehearse-rollback.sh — the rollback ladder, rehearsed against a real
# compose unit (update-channel T-17, REQ-724/722/719/721/723, D15).
#
#   sudo backend/updater/test/rehearse-rollback.sh A|B|all
#
# A  a derived image whose entrypoint never becomes ready (`sleep infinity`)
#    → health-timeout → the previous image is restarted and is healthy
#    → outcome rolled-back, rolledBack true, message health-timeout, the
#    marker intact, exactly TWO app starts (new image, old image).
# B  a second tag of the good image, and a hook (GXP_REHEARSAL_AFTER_DUMP)
#    that plants a `success = false` row in flyway_schema_history AFTER the
#    dump → the new image refuses (flyway-failed) → the old image refuses
#    too (same row) → the schema is wiped and the dump restored (review
#    A-6: a table `leftover_v999` planted after the dump must be GONE) → the
#    old image boots → outcome rolled-back-with-restore, message
#    flyway-failed, marker intact, the planted row gone, THREE app starts.
#
# Throwaway compose project, cleaned up afterwards (see rehearsal-lib.sh);
# the derived image is removed as well.
# shellcheck source=rehearsal-lib.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/rehearsal-lib.sh"

scenario="${1:-all}"
case "$scenario" in
    A|B|all) ;;
    *) echo "usage: rehearse-rollback.sh A|B|all" >&2; exit 2 ;;
esac

scenario_a() {
    say "scenario A — an image that never becomes ready"
    prepare_project
    mkdir -p -- "$WORK/context-a"
    printf 'FROM %s\nENTRYPOINT ["sleep","infinity"]\n' "$BASE_IMAGE" > "$WORK/context-a/Dockerfile"
    docker build -q -t gxp-backend:rehearsal-a -- "$WORK/context-a" >/dev/null
    DERIVED_IMAGES+=(gxp-backend:rehearsal-a)
    ID="$(image_id gxp-backend:rehearsal-a)"
    create_marker
    write_desired 9.9.9 gxp-backend:rehearsal-a "$ID" "$(utc -60)" "$(utc 0)"
    sleep 1
    T0="$(date +%s)"
    GXP_HEALTH_TIMEOUT=40 run_updater
    assert_eq "updater exit code" 0 "$UPDATER_RC"
    assert_eq "state.outcome" rolled-back "$(state outcome)"
    assert_eq "state.rolledBack" true "$(state_bool rolledBack)"
    assert_eq "state.message" health-timeout "$(state message)"
    assert_eq "state.attemptedVersion" 9.9.9 "$(state attemptedVersion)"
    assert_eq "state.currentImage (the old one)" "$BASE_IMAGE" "$(state currentImage)"
    assert_eq "state.previousImage" "$BASE_IMAGE" "$(state previousImage)"
    assert_eq "state.currentVersion (unknown before the first applied update)" "" "$(state currentVersion)"
    assert_eq ".env GXP_IMAGE back to the old image" "$BASE_IMAGE" "$(env_image)"
    assert_eq "running container image" "$BASE_IMAGE" "$(docker inspect -f '{{.Config.Image}}' -- "$(compose ps -q app)")"
    assert_eq "/api/system/ready (old image serving)" 200 "$(ready_status)"
    assert_eq "marker intact" written-before-the-update "$(marker)"
    assert_eq "app starts (new image, then the old one)" 2 "$(app_starts "$T0")"
    assert_eq "dump was taken" 1 "$(find "$BACKUPS" -maxdepth 1 -type f -name 'pre-update-9.9.9-*.dump' | wc -l)"
    say "the hold rule on the host: the same request again → no second attempt"
    before="$(cat "$EXCHANGE/state.json")"
    run_updater
    assert_eq "state unchanged" "$before" "$(cat "$EXCHANGE/state.json")"
    assert_eq "still two app starts" 2 "$(app_starts "$T0")"
    cleanup
}

scenario_b() {
    say "scenario B — a failed migration row planted after the dump"
    prepare_project
    TAG="gxp-backend:rehearsal-b"
    tag_base "$TAG"
    ID="$(image_id "$TAG")"
    create_marker
    cat > "$WORK/after-dump.sh" <<EOF
#!/usr/bin/env bash
# rehearsal hook: plant a failed migration AFTER the dump (scenario B)
set -euo pipefail
docker compose --project-directory "$COMPOSE_DIR" --project-name "$PROJECT" exec -T db psql -U gxp -d gxp -tAc \\
  "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (999, '999', 'rehearsal', 'SQL', 'V999__rehearsal.sql', 0, 'gxp', now(), 0, false)" >/dev/null
# and an object a SUCCEEDED migration would have created (A-6): not in the dump, must not survive the restore
docker compose --project-directory "$COMPOSE_DIR" --project-name "$PROJECT" exec -T db psql -U gxp -d gxp -tAc \\
  "CREATE TABLE leftover_v999(v int)" >/dev/null
EOF
    write_desired 9.9.9 "$TAG" "$ID" "$(utc -60)" "$(utc 0)"
    sleep 1
    T0="$(date +%s)"
    GXP_HEALTH_TIMEOUT=120 run_updater GXP_REHEARSAL_AFTER_DUMP="$WORK/after-dump.sh"
    assert_eq "updater exit code" 0 "$UPDATER_RC"
    assert_eq "state.outcome" rolled-back-with-restore "$(state outcome)"
    assert_eq "state.rolledBack" true "$(state_bool rolledBack)"
    assert_eq "state.message (why the new image failed)" flyway-failed "$(state message)"
    assert_eq "updater log names both failed boots" 2 "$(printf '%s\n' "$UPDATER_LOG" | grep -c 'flyway-failed')"
    assert_eq "state.currentImage (the old one)" "$BASE_IMAGE" "$(state currentImage)"
    assert_eq ".env GXP_IMAGE back to the old image" "$BASE_IMAGE" "$(env_image)"
    assert_eq "running container image" "$BASE_IMAGE" "$(docker inspect -f '{{.Config.Image}}' -- "$(compose ps -q app)")"
    assert_eq "/api/system/ready (old image serving on the restored database)" 200 "$(ready_status)"
    assert_eq "marker intact (it was in the dump)" written-before-the-update "$(marker)"
    assert_eq "planted row gone" 0 "$(sql "SELECT count(*) FROM flyway_schema_history WHERE installed_rank = 999")"
    assert_eq "schema wipe ran (A-6)" 1 "$(printf '%s\n' "$UPDATER_LOG" | grep -c 'schema wipe exit=0')"
    assert_eq "leftover table (created after the dump) is gone (A-6)" 0 "$(sql "SELECT count(*) FROM pg_tables WHERE tablename = 'leftover_v999'")"
    assert_eq "public schema owner is gxp again" gxp "$(sql "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'public'")"
    assert_eq "newest flyway row succeeded" t "$(sql 'SELECT success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1')"
    assert_eq "app starts (new image, old image, old image after restore)" 3 "$(app_starts "$T0")"
    cleanup
}

trap cleanup EXIT
case "$scenario" in
    A) scenario_a ;;
    B) scenario_b ;;
    all) scenario_a; scenario_b ;;
esac
trap - EXIT
verdict "rehearse-rollback $scenario"
