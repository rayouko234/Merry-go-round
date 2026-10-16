# Firebase integration — Merry-Go-Round

Project: **`mt-project-ce4c0`** (`.firebaserc`), Android package `com.example.merrygoround`
(`app/google-services.json`).

## Components

| Piece | Config | Role |
|---|---|---|
| Firebase Auth (Email/Password) | google-services.json | Accounts, sessions, identity |
| Cloud Firestore | `firestore.rules`, `firestore.indexes.json` | Authoritative database |
| Firebase Hosting | `firebase.json` → `web/` | Product landing page |
| Google Services Gradle plugin 4.5.0 | root + app `build.gradle.kts` | Builds google_app_id into the APK |
| Firestore Emulator | `firebase.json` emulators section | Local development & rules validation |

## Firestore data model

All money fields are **integer minor units** (`*_minor`, KES has 2 decimals);
timestamps are **epoch milliseconds UTC**; date-only values are **epoch days**.

```
users/{uid}                full_name, email, phone, status, created_at
groups/{gid}               name, description, owner_id, currency,
                           contribution_amount_minor, frequency,
                           custom_interval_days, member_limit, member_count*,
                           start_epoch_day, status, payout_order_method,
                           join_policy, invitation_expiry_days, created_at
  members/{uid}**          group_id, user_id, role, status, payout_order,
                           joined_at, invite_code
  cycles/{cycleId}         cycle_number, start/end/deadline_epoch_day,
                           recipient_uid, expected_total_minor, status,
                           scheduled_payout_at, actual_payout_at
  contributions/{id}***    cycle_id, user_id, amount_minor, currency, status,
                           verification, reference, idempotency_key, note,
                           failure_reason, created_at, updated_at
  payouts/{cycleId}****    cycle_number, recipient_uid, amount_minor, status,
                           reference, scheduled_at, completed_at
  audit/{eventId}          entity_type, entity_id, event_type, actor_uid,
                           timestamp, metadata   (append-only)
invitations/{code}******   group_id, status, used_count, expires_at
notifications/{uid}/items/{id}   type, title, message, is_read, group_id, created_at
```

Structural guarantees from the document IDs:

- `*` `member_count` denormalized, only guarded writes (+1 join, +1 approve, −1 remove) → race-free capacity.
- `**` doc id = uid → **duplicate membership impossible**.
- `***` doc id = `idem-<sha256(idempotencyKey)>` → **contribution retries cannot duplicate**.
- `****` doc id = cycle id → **one payout per cycle; completion guarded by a transaction**.
- `*****` doc id = invitation code → **codes unique by construction**.

## Rule enforcement map (`firestore.rules`)

| Rule | Guards against |
|---|---|
| `signedIn()` everywhere | anonymous access |
| group read = active member | cross-group data leaks |
| group create = owner is caller, FORMING, amount > 0, 2 ≤ limit ≤ 50 | forged/malformed groups |
| group update = admin, **or** member_count +1 only | clients inflating capacity |
| member create = own uid + invitation points at this group + ACTIVE + unexpired + status matches join policy | invitation bypass/forgery |
| member update = admin only | self-promotion, self-approval |
| contribution create = own uid, exact group amount, ACTIVE cycle, PENDING/SELF_REPORTED, doc must not exist | fake amounts, off-cycle writes, duplicates |
| contribution update = admin + state machine (PENDING→CONFIRMED/FAILED, CONFIRMED→REVERSED) | client-side "mark myself paid", history erasure |
| payout update = admin + SCHEDULED→COMPLETED | unauthorised/raced payout completion |
| audit create = actor == uid; update/delete = false | audit tampering |
| invitation create = group owner (via `get()`) | forged invitation codes |
| notification read/update = own uid only | notification snooping |

`createGroup` runs in **two client calls** (group + owner membership, then the
## Deploy

```bash
firebase login                       # once, with the project owner account
firebase deploy --only firestore     # rules + indexes
firebase deploy --only hosting       # landing page from web/
firebase deploy                      # everything in firebase.json
```

## Local development with the emulator

```bash
firebase emulators:start --only firestore --project mt-project-ce4c0
```

Rules are loaded from `firestore.rules` at emulator start (verified in this
repo's environment: the emulator rejected an unauthenticated `users` create with
`403 false for 'create' @ L47`). To point the app at the emulator, call
`FirebaseFirestore.getInstance().useEmulator(...)` in a debug build — not wired
by default.

## Known MVP boundaries

1. **Notifications** are created client-side for other users. For production,
   move generation to Cloud Functions (trigger on contribution confirmation,
   payout completion, membership decisions) and disallow cross-uid creates.
2. **Join capacity:** rules validate the invitation fully; atomic capacity
   admission under adversarial concurrent joins belongs in a Cloud Function.
3. **FCM push** not configured — in-app notifications only.
4. **Firebase BOM** pinned to 33.12.0 (Kotlin 2.0.21 toolchain ceiling) —
   see README §Firebase versions.

invitation) — a single batched write cannot be validated with `get()` against its
own not-yet-existing group document.

