#!/usr/bin/env bash
# Writes and signs a release manifest (update-channel REQ-702, REQ-725, D4).
#
#   sign-manifest.sh <private-key.pem> <out-dir> \
#       --channel stable --version 1.0.1 --artifact-ref ghcr.io/x/gxp-backend:1.0.1 \
#       --sha256 <registry manifest digest, 64 hex> --published-at 2026-09-01T10:00:00Z \
#       [--minimum-from-version 1.0.0] [--trusted-keys <public-keys.pem>]
#
# --trusted-keys ROTATES the trust set (review A-1): the PEM's PUBLIC KEY
# blocks (1..4, e.g. the repo resource of the release being signed) become
# the set every box trusts from this version on — it must include the key
# that signs (the signer's own public half), or both verifiers refuse the
# manifest as malformed. Sign a rotation with the DORMANT key when the
# active one is lost or stolen; the retired key is refused afterwards.
#
# Produces <out-dir>/<channel>.json in the FIXED line layout every reader
# (de.gxp.update.control.ManifestParser, the updater's sed readers) expects,
# and <out-dir>/<channel>.json.sig = base64 of the raw 64-byte Ed25519
# signature over the exact file bytes. Every field is validated against the
# regex table of the design (§2) before anything is written; the output is
# verified with the key's own public half before exit 0.
#
# --sha256 is the REGISTRY MANIFEST DIGEST of the pushed artifact (review
# R3-6): the updater pulls `<artifact-ref>@sha256:<digest>`, so Docker itself
# refuses any other content. The ONE source (review A-7) is
#   docker buildx imagetools inspect <artifact-ref> --format '{{println .Manifest.Digest}}'
# (the `println` is load-bearing: a bare `{{.Manifest.Digest}}` makes buildx
# 0.29 print its default table instead of the template)
# — "whatever the tag resolves to": the index digest for a multi-arch push,
# the manifest digest for a single-arch push (the recommended release shape
# while the fleet is arm64-only). Strip the "sha256:". NOT the local image ID
# (`{{.Id}}`) and NOT `RepoDigests` — both depend on the image store of the
# box that reads them.
set -euo pipefail
export LC_ALL=C   # bash =~ character classes are locale-dependent; pin them

usage() {
    sed -n '2,24p' "$0" >&2
    exit 2
}

[[ $# -ge 2 ]] || usage
key="$1"
out="$2"
shift 2

channel="" version="" artifact="" sha256="" published="" minimum="" trusted=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --channel) channel="${2:-}"; shift 2 ;;
        --version) version="${2:-}"; shift 2 ;;
        --artifact-ref) artifact="${2:-}"; shift 2 ;;
        --sha256) sha256="${2:-}"; shift 2 ;;
        --published-at) published="${2:-}"; shift 2 ;;
        --minimum-from-version) minimum="${2:-}"; shift 2 ;;
        --trusted-keys) trusted="${2:-}"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; usage ;;
    esac
done

readonly RE_VERSION='^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]{1,32})?$'
readonly RE_ARTIFACT='^[a-z0-9][a-z0-9._-]{0,127}(:[0-9]{1,5})?(/[a-z0-9][a-z0-9._-]{0,127}){0,7}(:[A-Za-z0-9_][A-Za-z0-9._-]{0,127})?(@sha256:[0-9a-f]{64})?$'
readonly RE_SHA256='^[0-9a-f]{64}$'
readonly RE_INSTANT='^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$'
readonly RE_CHANNEL='^[a-z0-9-]{1,32}$'
readonly RE_KEY_TOKEN='^[A-Za-z0-9+/]{59}=$'

check() {
    local name="$1" value="$2" regex="$3"
    if ! [[ "$value" =~ $regex ]]; then
        echo "invalid --$name" >&2
        exit 1
    fi
}
[[ -r "$key" ]] || { echo "private key not readable: $key" >&2; exit 1; }
check channel "$channel" "$RE_CHANNEL"
check version "$version" "$RE_VERSION"
check artifact-ref "$artifact" "$RE_ARTIFACT"
check sha256 "$sha256" "$RE_SHA256"
check published-at "$published" "$RE_INSTANT"
if [[ -n "$minimum" ]]; then
    check minimum-from-version "$minimum" "$RE_VERSION"
fi
tokens=""
if [[ -n "$trusted" ]]; then
    [[ -r "$trusted" ]] || { echo "--trusted-keys not readable: $trusted" >&2; exit 1; }
    # split the PEM into blocks, each → base64 of the DER (the token form both verifiers read)
    n=0
    while IFS= read -r -d '' block; do
        token="$(printf '%s' "$block" | openssl pkey -pubin -outform DER 2>/dev/null | base64 -w0)" || token=""
        [[ "$token" =~ $RE_KEY_TOKEN ]] || { echo "--trusted-keys: a block is not an Ed25519 public key" >&2; exit 1; }
        [[ ",$tokens," != *",$token,"* ]] || { echo "--trusted-keys: duplicated key" >&2; exit 1; }
        n=$((n + 1))
        tokens="${tokens:+$tokens,}$token"
    done < <(awk '/-----BEGIN PUBLIC KEY-----/{b=""} {b=b $0 "\n"} /-----END PUBLIC KEY-----/{printf "%s%c", b, 0}' "$trusted")
    (( n >= 1 && n <= 4 )) || { echo "--trusted-keys must hold 1..4 keys" >&2; exit 1; }
    signer="$(openssl pkey -in "$key" -pubout -outform DER 2>/dev/null | base64 -w0)"
    [[ ",$tokens," == *",$signer,"* ]] || { echo "--trusted-keys does not contain the signing key's public half — a set that retires its own signer is refused by every box" >&2; exit 1; }
fi

mkdir -p -- "$out"
manifest="$out/$channel.json"
signature="$manifest.sig"

# From here on, any non-zero exit removes BOTH outputs: a failing `openssl
# pkeyutl -sign` must not leave a manifest with an empty .sig behind.
tmp="$(mktemp -d)"
trap 'rc=$?; rm -rf -- "$tmp"; [[ $rc -eq 0 ]] || rm -f -- "$manifest" "$signature"' EXIT

{
    printf '{\n'
    printf '  "schemaVersion": 1,\n'
    printf '  "channel": "%s",\n' "$channel"
    printf '  "version": "%s",\n' "$version"
    printf '  "artifactRef": "%s",\n' "$artifact"
    printf '  "sha256": "%s",\n' "$sha256"
    # the optional lines come last, in this order: minimumFromVersion, trustedKeys
    printf '  "publishedAt": "%s"' "$published"
    if [[ -n "$minimum" ]]; then
        printf ',\n  "minimumFromVersion": "%s"' "$minimum"
    fi
    if [[ -n "$tokens" ]]; then
        printf ',\n  "trustedKeys": "%s"' "$tokens"
    fi
    printf '\n}\n'
} > "$manifest"

openssl pkeyutl -sign -rawin -inkey "$key" -in "$manifest" | base64 -w0 > "$signature"

# self-verify with the derived public key before claiming success
openssl pkey -in "$key" -pubout -out "$tmp/public.pem"
base64 -d "$signature" > "$tmp/sig.bin"
if ! openssl pkeyutl -verify -pubin -inkey "$tmp/public.pem" -rawin -in "$manifest" -sigfile "$tmp/sig.bin" >/dev/null; then
    echo "self-verification FAILED — output removed" >&2
    exit 1
fi

echo "wrote $manifest and $signature"
if [[ -n "$tokens" ]]; then
    echo "this release ROTATES the trust set to $n key(s); the signing key stays in it"
fi
echo "the updater will pull: $artifact@sha256:$sha256 (the registry manifest digest — not the local image ID)"
