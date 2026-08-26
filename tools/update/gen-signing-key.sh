#!/usr/bin/env bash
# Generates the release-signing Ed25519 key pair (update-channel REQ-725, D17).
#
#   gen-signing-key.sh [dir] [suffix]     default: $HOME/.gxp/keys, no suffix
#
# Writes <dir>/signing-private-key[-suffix].pem (0600) and
# <dir>/signing-public-key[-suffix].pem, refuses to overwrite an existing
# private key, and refuses a directory inside a git work tree — the private
# key never enters a repository. Prints the public key's SHA-256 fingerprint
# (of the DER encoding) for the handoff/README. The suffix names a further
# key of the trust set (review A-1: key 1 active, key 2 dormant —
# `gen-signing-key.sh ~/.gxp/keys 2`); the .gitignore pattern
# `*signing-private-key*` covers every suffix. Only the PUBLIC keys are
# concatenated into backend/app/src/main/resources/update/signing-public-keys.pem.
set -euo pipefail
export LC_ALL=C

dir="${1:-$HOME/.gxp/keys}"
suffix="${2:-}"
if [[ -n "$suffix" ]]; then
    [[ "$suffix" =~ ^[A-Za-z0-9]{1,16}$ ]] || { echo "suffix must match ^[A-Za-z0-9]{1,16}$" >&2; exit 2; }
    suffix="-$suffix"
fi
private="$dir/signing-private-key$suffix.pem"
public="$dir/signing-public-key$suffix.pem"

if [[ -e "$private" ]]; then
    echo "refusing: $private already exists" >&2
    exit 1
fi
# check the location BEFORE creating anything: a refused run leaves no directory behind
probe="$dir"
while [[ ! -d "$probe" && "$probe" != "/" && -n "$probe" ]]; do probe="$(dirname -- "$probe")"; done
if git -C "$probe" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "refusing: $dir is inside a git work tree — the private key must live outside every repository" >&2
    exit 1
fi
mkdir -p -- "$dir"
chmod 700 -- "$dir"

umask 077
openssl genpkey -algorithm ed25519 -out "$private"
umask 022
openssl pkey -in "$private" -pubout -out "$public"
chmod 600 -- "$private"
chmod 644 -- "$public"

fingerprint="$(openssl pkey -pubin -in "$public" -outform DER | openssl dgst -sha256 | sed 's/^.*= //')"
echo "private key: $private (0600, never copy it anywhere tracked)"
echo "public key:  $public"
echo "public key SHA-256 (DER): $fingerprint"
