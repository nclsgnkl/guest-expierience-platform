# Specification: Remote Operations

**Status:** Proposed — not ratified, nothing here is approved for implementation
**Author:** drafted 2026-08-25 with the project owner
**Governs:** how a deployed instance is patched, and how a manager reaches it
from outside the building

---

## 0. Read this first: two blockers

**The constitution currently states the opposite of this document.**
`docs/constitution.md` principle 4 says:

> *"**It cannot be patched centrally.** Instances run in buildings we may have
> no access to, so an unpatched vulnerability can persist for a long time."*

That is written as a **statement of fact**, and the whole dependency-minimisation
posture is derived from it. This specification proposes turning it into a
**decision**: instances are patched through an outbound, signed update channel.
The dependency-minimisation posture stays — it is still right — but it stops
being a consequence of helplessness. Section 10 carries the proposed amendment;
it must be ratified before any of this enters implementation.

**Nothing in Parts B–D may ship before Part A.** Today's authentication posture
was designed for a LAN with a handful of devices: one shared admin password, a
minimum of eight characters, and — since 2026-08-24 — **no throttling of any
kind** on login. Exposing that to the internet would not be a new feature, it
would be a regression. Part A is not a nice-to-have that accompanies remote
access; it is its precondition. See §3.

---

## 1. Scope

**In scope**

| | |
|---|---|
| Part A | Authentication hardening: throttling, per-user accounts, second factor, audit trail |
| Part B | Certificates and names — a browser-trusted certificate without an inbound port |
| Part C | The update channel — how a new version reaches a deployed box |
| Part D | The relay — how a manager reaches the manager UI from outside the building. **Sold separately; every instance must work without it** (§7) |

**Deliberately out of scope**

- Anything the guest kiosks touch. Kiosks stay on the LAN, keep their device
  tokens and their existing pairing flow. No part of this specification changes
  `/api/guest/**` or `/api/pairing/**`.
- Central storage of any property's data. The relay carries bytes; it never
  reads, stores, or aggregates them (§7).
- Telemetry and analytics. Principle 4 forbids them and this does not reopen
  that. "Is the box reachable" is a property of the relay connection and needs
  no data from inside the application.
- Cross-property features (one dashboard over twenty apartments). Not built,
  not designed — but §7 notes the one thing worth not foreclosing.

---

## 2. The invariant that must survive

Everything below is measured against one sentence:

> **The product keeps working when everything we operate is gone.**

Concretely, and testably:

| If this is unreachable | Guest screens | Manager UI on the LAN | Manager UI remotely |
|---|---|---|---|
| The relay | work | works | **unavailable** |
| The update server | work | works | works |
| The internet entirely | work | works | unavailable |
| Our company, permanently | work | works | unavailable |

What gets centralised is **convenience**, never **function**. A reviewer should
be able to unplug the building's uplink and watch the property continue to
operate indefinitely. If a proposed design breaks that row, the design is wrong,
not the invariant.

A second invariant, inherited from principle 4 and unchanged: **the guest screen
renders from local state only.** Weather, booking imports and anything else
external is fetched by a background job into local storage; the screen shows the
last known value with a discreet staleness hint, never a spinner and never an
error. The existing `ONLINE / STALE / DISCONNECTED` machine in the guest
frontend is the pattern to copy, not to replace.

---

## 3. Dependency order

This order is not a preference. Each step is unsafe or impossible without the
one above it.

```
A1  Login throttling restored              ─┐
A2  Per-user accounts (replace the shared    │  must ALL be live before
    password)                                ├─ anything is reachable
A3  Second factor (TOTP)                     │  from outside the LAN
A4  Audit trail                            ─┘
         │
B   Certificate + stable name per instance
         │
C   Update channel  ────────────┐
         │                      │  C and D share the outbound connection
D   Relay for manager access ───┘  and the instance identity from B
```

**A1 in particular is a reversal.** `REQ-326` recorded, on 2026-08-24, that
login is deliberately not throttled, with the reason *"deferred until the
production hardware is known"*. That reason expires the moment the endpoint is
internet-reachable; the deferral was argued on a LAN threat model that no longer
applies. Whoever plans Part A must reopen REQ-326 explicitly rather than working
around it.

---

## 4. Part A — Authentication hardening

### A1. Login throttling (reopens REQ-326)

Two mechanisms were built and withdrawn on 2026-08-24, both because they were
themselves denial-of-service vectors:

- a **global** failed-attempt lockout — five wrong guesses from anyone answered
  the manager's *correct* password with 429;
- a **global** concurrency gate — a sustained flood still shed arriving callers.

Both failed for the same reason: **they were global.** The shape that does not
share the defect is a **budget per source address** that slows the source and
never locks the account. A legitimate manager is only affected if the flood
comes from their own address.

Requirements:

1. Failed attempts are counted per source address, not globally. A correct
   password from an address with no failures is never refused.
2. Exhausting a budget delays or rejects **that address**; every other address
   is unaffected.
3. The CPU cost of PBKDF2 is itself the amplifier — the throttle must reject
   *before* deriving, or the rejection costs as much as the attempt.
4. **The relay breaks per-address throttling unless the real client address
   survives.** Behind the relay every request appears to originate from the
   relay. The relay must forward the originating address (PROXY protocol, or a
   header plus an explicit trusted-proxy configuration), and the application
   must trust that header **only** from the relay. Getting this wrong yields a
   throttle that is either useless (one bucket for the world) or a weapon (a
   spoofed header locks out someone else). This is the single most likely
   defect in Part A.

Not a replacement for A2/A3 — an ≥ 8 character shared password remains weak
against a distributed attacker even when throttled per address.

### A2. Per-user accounts

Today: one row in `admin_credential`, one shared password, role `admin`.
`docs/backend/specification.md` already anticipates this change — it is the
reason `de.gxp.auth` is isolated ("the later per-user swap touches no other
Boundary or Control").

Requirements:

1. Named accounts. An owner, a co-host, a cleaner and a property manager are
   different people and must be distinguishable — an audit trail (A4) over a
   shared login records nothing useful.
2. First-start seeding still works: the box must come up usable with no prior
   state, and must still refuse to start rather than invent a default account.
3. Deactivating an account takes effect on the next request, like device
   revocation does — the in-memory token store must drop that user's tokens.
4. Whether roles beyond `admin` are introduced (read-only? cleaner sees the
   calendar but not door codes?) is **an open question, not a given** — see §10.
   The token/identity mechanism already carries roles and does not need to
   change for it.

### A3. Second factor (TOTP)

With a single password reachable from the internet, the second factor is what
actually carries the security, not the password policy.

Requirements:

1. RFC 6238 TOTP. The algorithm is an HMAC — the JDK covers it, no dependency.
2. Enrollment produces an `otpauth://` URI. **Rendering it as a QR code is the
   one place a dependency may sneak in** (see §9) — showing the secret as text
   works and needs nothing.
3. Recovery codes, or an explicitly documented recovery path. A locked-out
   owner with no path back means a site visit; that must be a considered
   decision, not an oversight.
4. Required for remote sessions. Whether it is also required on the LAN is a
   usability call for the owner (§10).

### A4. Audit trail

Requirements:

1. Record: who, when, from which address, which entity, which field changed.
2. **Never record the value** of a guest-sensitive field. Door codes, key-box
   codes, alarm codes and WiFi passwords are exactly what an audit trail is
   tempting to capture and exactly what constitution principle 3 forbids. The
   entry says *that* `doorCode` changed on room 12, never from what to what.
   An audit trail that violates principle 3 is worse than none, because it
   concentrates the secrets it was meant to protect.
3. Append-only from the application's perspective; no update or delete endpoint.
4. Retention is a decision, not a default (§10) — an unbounded table on a box
   nobody backs up is its own problem.

---

## 5. Part B — Certificate and instance name

Today each instance generates its own CA and a server certificate, and a person
installs that CA on every kiosk by hand (`backend/README.md`, "Pairing a
kiosk"). That works for kiosks — installed once per device — but not for a
manager arriving on an arbitrary laptop or phone, where a browser warning is
both a bad experience and a training in clicking warnings away.

**Proposal: a stable name and a publicly trusted certificate per instance,
obtained via ACME DNS-01.**

- Each instance gets a name under a domain we control, e.g.
  `<instance-id>.props.example.com`.
- The certificate is obtained and renewed over **DNS-01**, which needs no
  inbound port and no reachable HTTP endpoint — only outbound access and a DNS
  API credential.
- **Beneficial side effect worth stating explicitly:** kiosks would then trust
  the certificate natively, which **removes the manual CA-installation step and
  the trust-on-first-use window at pairing** — a known weakness since the
  beginning, resolved as a by-product rather than as its own project.

**Two modes must coexist.** An instance with no internet keeps today's
self-signed CA and its manual kiosk step. The ACME path is the default for
connected instances; the offline path stays supported and tested, and becomes
the special case rather than the norm.

**How the name resolves.** Only one machine in the whole system needs to be
reachable, and it is not a box:

```
*.props.example.com.  A  <relay-ip>      one wildcard record, forever
relay.example.com.    A  <relay-ip>      boxes dial this, never a literal IP
```

A wildcard means a new instance needs **no DNS work at all** — a new name simply
resolves. Instance names are deliberately opaque (`k3f9`, not
`ferienhaus-mueller`): certificates appear in public Certificate Transparency
logs, so a descriptive name would publish the existence and identity of a
customer's property to anyone who looks.

**Where the DNS credential lives — recommended answer (resolves §10 item 7).**
DNS-01 requires writing `_acme-challenge.<name>.props.example.com`. Two obvious
approaches are wrong:

- *a DNS API token on each box* — providers scope tokens per zone, not per
  record, so a stolen box could issue certificates for every sibling;
- *a central wildcard certificate distributed to boxes* — every box would hold a
  key valid for all of them.

Instead the box **asks a small service of ours to publish its challenge**,
authenticated with the same instance identity the relay uses. The DNS
credential stays with us, a box can only ever influence its own name, and no
new identity mechanism is invented. One endpoint.

**Availability, measured against §2:** if DNS is unreachable, *new* remote
sessions fail; established ones, the LAN manager UI and every guest screen are
unaffected. Resolver caching absorbs short outages.

Still open: what an instance identity is and who assigns it (§10 item 6).

---

## 6. Part C — Update channel

**Direction: outbound only. The box asks; nothing ever connects to the box.**

```
Box                                          Update server
 │  periodically: GET /manifest/<channel>  ──────▶
 │  ◀── { version, artifact-url, sha256, signature }
 │
 │  verify signature against a public key compiled into the image
 │  fetch artifact, verify sha256
 │  apply → health check → keep, or roll back
```

Requirements:

1. **Signing happens offline.** The private key never exists on the update
   server. A compromised update server can then serve nothing the boxes will
   accept — it becomes a denial-of-service problem, not a fleet-compromise
   problem.
2. **Verify before apply**, without exception. This is the whole security
   property; an unverified path defeats every other measure in this document.
3. **The updater is not the application.** The Quarkus process cannot replace
   its own container. The component that applies an update lives beside it (a
   host-side timer and script, or a supervisor container). Its privileges are
   the highest on the box and its surface must be correspondingly small.
4. **Rollback must work, and must be exercised.** Health-check after apply;
   revert on failure. A box that bricks itself in a building we cannot reach is
   the worst outcome in this entire specification — worse than staying
   unpatched.
5. **A migration must survive failing.** Flyway is versioned, which helps, but
   "migration fails on a customer box" needs to be a rehearsed scenario, not a
   discovered one. Whether the update is refused when the migration cannot be
   rolled back is an owner decision (§10).
6. Update timing must not surprise a guest. A box that restarts at 19:00 while
   someone is reading the house rules is a support call.

**Recorded consequence:** the update channel becomes the highest-value target
associated with this product. Whoever controls it controls every deployed
instance. It deserves more scrutiny than any single application endpoint, and
its key handling should be reviewed independently.

---

## 7. Part D — Relay for manager access

**Direction: the box dials out and holds the connection. The relay never
initiates.** This works behind NAT and CGNAT, requires no router configuration
by the customer, and opens no port on the property's network.

```
Manager's browser ──HTTPS──▶ relay ──existing outbound tunnel──▶ box
                                    (TCP only — see below)
```

Requirements:

1. **The relay is a dumb pipe. TLS terminates on the box, not on the relay.**
   This is the load-bearing requirement of Part D, and it was re-examined on
   2026-08-25 against the obvious alternative — a normal reverse proxy that
   terminates TLS and handles rate limiting, WAF rules and blocklists at the
   edge. **Rejected, and it should not be re-proposed without reopening this
   paragraph.** The reasoning is not primarily technical:

   - To rate-limit HTTP you must see HTTP; to see HTTP you must terminate TLS.
     A terminating proxy therefore sees, for **every** property, the login
     passwords, the bearer tokens, and the door codes, alarm codes, WiFi
     passwords and guest names in every manager response. Nothing is stored
     there — but one access-log setting, one debug flag or one compromised host
     turns "sees" into "has".
   - That re-creates the central target this architecture exists to avoid. The
     blast-radius asymmetry (one box compromised = one property; the centre
     compromised = all of them) is the single strongest argument for the
     on-premises model, and terminating TLS at the edge silently gives it up.
   - **It would also make the product's central claim untrue.** "Guest data
     never leaves the building" cannot be said while all manager traffic flows
     in plaintext through infrastructure we operate — and it would make us a
     processor of that data, which is exactly the compliance surface the
     on-premises model removes.

   A compromised dumb pipe can deny service. It cannot read.
2. The relay authenticates instances and routes to the right tunnel. It must not
   be possible for one instance to receive another's traffic, nor to enumerate
   siblings.
3. **The originating client address must reach the application** — see A1.4.
4. **Transport-level limits belong on the relay, because they need no
   plaintext**: connections per source address, new-connection rate, bandwidth,
   and an operator blocklist. These are worth having and cost nothing, but they
   are **defence in depth, not a substitute for A1** — a relay cannot tell
   `POST /api/manager/login` from `GET /api/manager/rooms`, so it cannot see a
   *failed login* and cannot stop a patient attacker at one request per second.
   Only the application knows the difference.
5. The application is unchanged by the relay's existence. **No "remote mode", no
   second authentication path, no `if (remote)`.** The transport is a deployment
   concern beside the application; the moment the application knows where a
   request came from, there are two security models and one of them is weaker.
6. Relay unavailability degrades to "no remote access" and nothing else (§2).
7. Enrollment — how a box obtains its relay identity — should reuse the instance
   identity from Part B rather than inventing a second one.

**Remote access is an optional, separately sold capability** (owner decision
2026-08-25), and that has two binding consequences:

1. **A box must be fully functional having never enrolled**, and enrollment must
   be possible later without a reinstall, and reversible without loss. A
   customer who stops paying loses remote access and nothing else. This is §2
   restated as a commercial requirement, which is the strongest kind.
2. **Part C must not be gated behind Part D.** They share an outbound
   connection, but their *availability must be independent*: an instance that
   never bought remote access must still receive security updates. Tying them
   together would leave unpatched copies of our software running in the field
   because someone declined an upsell — a liability for us, not just for the
   customer. Plan the two channels so that D can be switched off per instance
   while C keeps running.

**Not foreclosing the hybrid:** if cross-property features are ever needed (one
host, twenty apartments, one overview), the aggregation layer would read through
this same channel. Nothing here should assume a single manager per instance in a
way that makes that impossible later. Building it now is out of scope.

---

## 8. What each part defends against

| Part | Defends against | Residual risk after it |
|---|---|---|
| A1 | Online password guessing; unauthenticated CPU exhaustion via PBKDF2 | A distributed attacker across many addresses |
| A2 | Shared-credential blast radius; unattributable changes | Only as good as the weakest account |
| A3 | Password guessing and credential stuffing outright | Lost device without a recovery path |
| A4 | Undetected or undisputable changes | Only useful if someone reads it |
| B | Browser-warning fatigue; the pairing trust-on-first-use window | DNS credential compromise |
| C | Permanently unpatched instances | The update channel itself becomes the target (§6) |
| D | Exposing N boxes directly on the internet; crude flooding (transport limits, D4) | Relay outage removes remote access for everyone; a patient low-rate attacker is A1's problem, not the relay's |

**Explicitly not solved here:** backups (still open, `specification.md` §11
item 7), a broken box nobody notices, and physical access to a kiosk in a guest
room.

---

## 9. New dependencies — approval required

The allow-list in `docs/constitution.md` is closed; each of these needs the
owner's approval before it enters `pom.xml`, with the lightweight option
preferred and the heavy alternative recorded as rejected.

| For | Likely need | Lighter alternative first |
|---|---|---|
| TOTP (A3) | none — `javax.crypto.Mac` covers RFC 6238 | — |
| QR rendering for TOTP enrollment | a QR encoder | show the `otpauth://` secret as text; render the QR in the frontend, or not at all |
| ACME DNS-01 (B) | an ACME client (e.g. acme4j) | the entrypoint shells out to an existing small ACME client; nothing enters `pom.xml` |
| Update channel (C) | none in the application — the updater lives outside it | — |
| Relay client (D) | possibly none — an outbound TLS connection is plain JDK | evaluate before reaching for a tunnelling library |

`quarkus-scheduler` (constitution v1.7.0) already covers the periodic work in
Parts B and C.

---

## 10. Open items — owner decisions, do not guess

| # | Item |
|---|---|
| 1 | **Constitution amendment.** Principle 4's *"It cannot be patched centrally"* becomes *"Instances are patched through an outbound, signed update channel; the dependency-minimisation posture stays regardless."* Needs ratification before implementation |
| 2 | **Roles beyond `admin`** (A2) — is a read-only or cleaner role in scope, or are all accounts equal for now? |
| 3 | **Is the second factor required on the LAN too**, or only for sessions arriving through the relay? (A3) |
| 4 | **TOTP recovery path** — recovery codes, or an accepted site visit? (A3) |
| 5 | **Audit retention** — how long, and what prunes it on a box nobody backs up? (A4) |
| 6 | **Instance identity** — what is an instance called, who assigns it, and what happens when hardware is replaced? Shared by B, C and D |
| 7 | **Where the DNS credential for ACME lives** — explicitly not on the boxes. *§5 records a recommendation (a challenge-publishing service authenticated by instance identity); it needs ratifying, not inventing* (B) |
| 8 | **Update cadence and consent** — automatic, or manager-approved? What time of day? (C) |
| 9 | **Migration failure policy** — refuse the update, or apply and risk a partial schema? (C) |
| 10 | **Who operates the relay**, and what its availability target is (D) |
| 11 | **Client certificates at the relay** — should the relay require a client certificate before opening a tunnel at all? It gates *who may reach a box* without seeing any content, so `/api/manager/login` would not be reachable by anonymous callers on the internet at all — a bigger change to the threat model than any throttle. The cost is a certificate on every manager device, i.e. the VPN trade-off one layer down. Does not remove the need for A1 (the LAN path stays open) |
| 12 | **Booking/Airbnb import** — iCal URLs (outbound, read-only, no account credentials on the box) versus a partner API (credentials on every box). Recommendation: **iCal**, precisely because it keeps the credential count at zero |

---

## 11. Suggested feature cut for the pipeline

Four features in dependency order, each small enough to survive `04_analyze`.

| Slug | Scope |
|---|---|
| `auth-hardening` | A1–A4. Ships entirely on the LAN, valuable on its own, and is the precondition for everything below. Reopens REQ-326 |
| `instance-identity-and-certs` | B. Instance identity, ACME DNS-01, both modes coexisting; retires the manual CA step for connected instances |
| `update-channel` | C. Signed manifest, verification, the out-of-application updater, rollback, rehearsed migration failure |
| `remote-manager-access` | D. The relay, enrollment, client-address forwarding, and the end-to-end proof that a relay outage costs nothing but remote access |

`auth-hardening` is worth starting even if the rest is deferred indefinitely:
every requirement in it improves the product on a LAN-only deployment too.
