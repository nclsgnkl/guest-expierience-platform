#!/bin/bash
# TLS first-start init (device-pairing-and-guest-api, REQ-427/430/433 —
# resolved decisions #1/#2, 2026-08-19): generate an instance CA and a
# CA-signed server certificate with keytool (ships in this JVM image — no
# new dependency, no second image) onto the named volume mounted at /tls,
# then exec the stock run-java launcher.
#
#   first start        : CA (EC P-256, self-signed, CA:true, 20 y) +
#                        server keypair/CSR signed by the CA
#                        (EC P-256, SAN = GXP_TLS_HOST, 10 y),
#                        CA certificate exported as PEM (served by
#                        GET /api/pairing/ca for kiosk provisioning)
#   restart            : material present, host unchanged -> reuse
#   GXP_TLS_HOST change: server certificate re-issued under the UNCHANGED
#                        CA -> paired kiosks keep trusting, no re-pairing
#
# Security posture: no key material or passphrase is ever echoed; files are
# created 0600 (umask). The keystore passphrase is generated once, stored on
# the volume and handed to Quarkus via environment variables (never argv —
# argv is world-readable through /proc).

set -euo pipefail

TLS_DIR="${GXP_TLS_DIR:-/tls}"
CA_KEYSTORE="$TLS_DIR/ca.p12"
SERVER_KEYSTORE="$TLS_DIR/server.p12"
CA_PEM="$TLS_DIR/ca.pem"
PASS_FILE="$TLS_DIR/keystore-pass"
HOST_FILE="$TLS_DIR/tls-host"

if [ -z "${GXP_TLS_HOST:-}" ]; then
    echo "FATAL: GXP_TLS_HOST is not set. Set it to the host name or IP the kiosks use" >&2
    echo "       to reach this instance - the server certificate is issued for it." >&2
    exit 1
fi

umask 077

# SAN type: an IPv4 literal must be an ip: SAN, anything else a dns: SAN.
if [[ "$GXP_TLS_HOST" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    SAN="ip:$GXP_TLS_HOST"
else
    SAN="dns:$GXP_TLS_HOST"
fi

issue_server_certificate() {
    local pass="$1"
    rm -f "$SERVER_KEYSTORE"
    keytool -genkeypair -alias server -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA \
        -validity 3650 -dname "CN=$GXP_TLS_HOST" \
        -keystore "$SERVER_KEYSTORE" -storetype PKCS12 -storepass "$pass"
    keytool -certreq -alias server \
        -keystore "$SERVER_KEYSTORE" -storepass "$pass" -file "$TLS_DIR/server.csr"
    keytool -gencert -alias ca -keystore "$CA_KEYSTORE" -storepass "$pass" \
        -infile "$TLS_DIR/server.csr" -outfile "$TLS_DIR/server-cert.pem" -rfc \
        -validity 3650 -ext "san=$SAN" -ext "ku:c=digitalSignature" -ext "eku=serverAuth"
    keytool -importcert -noprompt -alias gxp-ca -file "$CA_PEM" \
        -keystore "$SERVER_KEYSTORE" -storepass "$pass"
    keytool -importcert -noprompt -alias server -file "$TLS_DIR/server-cert.pem" \
        -keystore "$SERVER_KEYSTORE" -storepass "$pass"
    rm -f "$TLS_DIR/server.csr" "$TLS_DIR/server-cert.pem"
    printf '%s' "$GXP_TLS_HOST" > "$HOST_FILE"
}

if [ ! -f "$CA_KEYSTORE" ] || [ ! -f "$PASS_FILE" ]; then
    echo "gxp-tls: first start - generating instance CA and server certificate for $GXP_TLS_HOST"
    rm -f "$CA_KEYSTORE" "$SERVER_KEYSTORE" "$CA_PEM" "$PASS_FILE" "$HOST_FILE"
    head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' > "$PASS_FILE"
    PASS="$(cat "$PASS_FILE")"
    keytool -genkeypair -alias ca -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA \
        -validity 7300 -dname "CN=GXP Instance CA" \
        -ext "bc:c=ca:true" -ext "ku:c=keyCertSign,cRLSign" \
        -keystore "$CA_KEYSTORE" -storetype PKCS12 -storepass "$PASS"
    keytool -exportcert -alias ca -keystore "$CA_KEYSTORE" -storepass "$PASS" -rfc -file "$CA_PEM"
    issue_server_certificate "$PASS"
elif [ ! -f "$SERVER_KEYSTORE" ] || [ "$(cat "$HOST_FILE" 2>/dev/null || true)" != "$GXP_TLS_HOST" ]; then
    echo "gxp-tls: host changed - re-issuing the server certificate for $GXP_TLS_HOST under the existing CA"
    PASS="$(cat "$PASS_FILE")"
    issue_server_certificate "$PASS"
else
    echo "gxp-tls: reusing existing TLS material for $GXP_TLS_HOST"
fi

# The CA PEM is PUBLIC material (kiosks install it); the pairing endpoint
# reads it - make sure a pre-existing volume's copy is readable.
chmod 644 "$CA_PEM"

# Hand the material to Quarkus (application.properties %prod interpolates
# these - missing variables refuse the boot, the GXP_ADMIN_PASSWORD posture).
export GXP_TLS_KEYSTORE_PATH="$SERVER_KEYSTORE"
export GXP_TLS_KEYSTORE_PASSWORD="$(cat "$PASS_FILE")"
export GXP_TLS_CA_PEM_PATH="$CA_PEM"

exec /opt/jboss/container/java/run/run-java.sh "$@"
