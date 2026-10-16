# Merry-Go-Round

Android group-savings and rotating-payout MVP (Kotlin · Jetpack Compose · Material 3),
backed by **Firebase Authentication + Cloud Firestore**, with **Firebase Hosting**
serving the product landing page.

> **MVP scope:** contribution *tracking* and payout *scheduling* (brief "Mode A").
> The app does **not** move money. Self-reported contributions are shown as
> *Pending* until a group admin verifies them; confirmed records are never
> silently edited — corrections are audited reversals.

## Features (implemented end-to-end)

| Workflow | Where |
|---|---|
| Register, login (email **or** phone), logout, persisted session | `ui/auth`, `core/datastore` |
| Create a group (amount, frequency, member limit, start date, join policy, payout-order method) + generated invitation code | `ui/groups/CreateGroupScreen`, `FirebaseBackend.createGroup` |
| Join with an invitation code (validation, expiry, capacity, duplicate, approval policy) | `ui/groups/JoinGroupScreen`, `FirebaseBackend.joinGroup` |
| Dashboard: expected/confirmed/pending/outstanding totals, cycle deadline, next recipient, Contribute Now | `ui/dashboard` |
| Record a contribution idempotently (retry-safe) + admin verify / fail / reverse (state machine) | `ui/contributions`, `FirebaseBackend.record/confirm/reverse` |
| Group detail: members, roles, obligations, invite sharing, payout-order editing, admin approvals, start rotation | `ui/groups/GroupDetailScreen` |
| Rotating payout schedule: one recipient per cycle, order locked at start, admin-guarded completion | `ui/payouts`, `CyclePlanner`, `firestore.rules` |
| Activity: cross-group contributions, payouts, group events with group/status filters | `ui/activity` |
| Profile & settings: edit name/phone, notification preference, help/privacy, logout | `ui/profile` |
| In-app notifications for joins, verifications, payouts, schedule changes | `ui/profile/NotificationsScreen` |

## Architecture

```
Jetpack Compose UI  →  ViewModel (StateFlow, validation, loading/error states)
                    →  Repositories (thin, session-aware)
                    →  BackendApi  ← THE integration boundary
                          ├─ FirebaseBackend (default)  : Firebase Auth + Cloud Firestore
                          │     └─ firestore.rules re-enforce every authorization rule
                          │        server-side; transactions prevent duplicate payouts
                          └─ LocalBackend (mgr.backendMode=local) : Room-backed offline store
Room  = cache / local-mode store.  DataStore = session token reference only.
```

Both backends implement the same `BackendApi` and the same domain rules
(`CyclePlanner`, `ContributionStateMachine`, `Validators`, money in **integer
minor units** with ISO currency codes). The backend is the source of truth:
the client never mutates balances or statuses directly.

## Firebase project

- **Project id:** `mt-project-ce4c0` (see `.firebaserc`, `app/google-services.json`)
- Auth: **Email/Password** provider (enable it in the Firebase console → Authentication → Sign-in method)
- Firestore: database in production mode guarded by `firestore.rules`
- Hosting: `firebase deploy --only hosting` publishes `web/index.html` (landing page)
- Rules + indexes: `firebase deploy --only firestore`

### Firebase versions (important)

`app/build.gradle.kts` pins **Firebase BOM 33.12.0** (firebase-auth 23.2.0).
Firebase BOM 35.x ships libraries compiled with **Kotlin metadata 2.4**, which this
project's Kotlin 2.0.21 compiler cannot read — and no KSP release exists for
Kotlin 2.4 yet (KSP tops out at 2.3.12), so the Kotlin/KSP/Room trio cannot be
upgraded in lockstep today. To move to BOM 35.x later: upgrade Kotlin + KSP + Room
together (README-tested combo is Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / Room 2.6.1),
then remove the pin.

## Prerequisites

- JDK 17, Android SDK (platform 34/35), Gradle wrapper included (Gradle 8.12)
- For Firebase deploys: Node + Firebase CLI (`npm i -g firebase-tools`) and `firebase login`
- Optional for local backend work: Android emulator or physical device

## Build, test, run

```bash
./gradlew :app:assembleDebug            # build the debug APK
./gradlew :app:testDebugUnitTest        # run all unit tests (64 tests)
./gradlew :app:installDebug             # install on a connected device/emulator
# or from Android Studio: Run ▶
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

```bash
# Local Firebase stack (Firestore emulator + rules):
firebase emulators:start --only firestore --project mt-project-ce4c0

# Publish landing page + rules:
firebase deploy --only hosting,firestore
```

### Backend modes

| Mode | Enable with | Data store |
|---|---|---|
| **firebase** (default) | nothing to do | Firebase Auth + Firestore (`mt-project-ce4c0`) |
| **local** (offline demo/tests) | `mgr.backendMode=local` in `gradle.properties` | Room (device-local) |


## Testing

```bash
./gradlew :app:testDebugUnitTest        # 64 JVM tests, all green
```

| Suite | Coverage |
|---|---|
| `MoneyTest` | KES minor-unit parse/format, bounds, round-trips |
| `ValidatorsTest` | Kenyan phone/email/password/amount/limit/date/invite-code rules |
| `PasswordHasherTest` | PBKDF2 verify, per-user salts, no plaintext at rest |
| `CyclePlannerTest` | one cycle per member, no recipient repeats, totals, intervals, payout-after-deadline |
| `ContributionStateMachineTest` | allowed/terminal transitions, only CONFIRMED counts as funds |
| `LocalBackendTest` (Robolectric) | 18 end-to-end scenarios: auth, join rules, capacity, approval flow, schedule lock, idempotency, verify/reverse, payout guards, unauthorized access |
| `FirebaseBackendTest` | 9 scenarios proving `FirebaseBackend` enforces **identical** rules through the gateway fakes (auth, join/expiry/capacity, rotation, idempotent contributions, state machine, dashboard math, payout guards) |
| `AuthViewModelTest` | ViewModel validation, submission, conflict mapping (Robolectric + real backend) |

Not included (honest limitations): **instrumented/Compose UI tests** — this
environment has no emulator or system images, so `androidTest` sources were not
added rather than shipping unverifiable ones. Recommended additions once a device
exists: welcome/register/login/group-flow tests per brief §12.

## Security notes

- **Server-side gate:** `firestore.rules` (validated against the Firestore
  emulator) enforces: sign-in required; group reads member-only; invitation
  creation owner-only (verified with `get()`); join requires a live invitation
  matching the group & its join policy; contribution amount must equal the group
  amount and land only on an ACTIVE cycle; contribution state machine
  (PENDING→CONFIRMED/FAILED, CONFIRMED→REVERSED) admin-only; payout completion
  admin-only `SCHEDULED→COMPLETED`; audit trail append-only.
- **Idempotency:** contribution doc ids are `idem-<sha256(key)>` — retries structurally
  cannot double-record; payout doc id = cycle id — one payout per cycle.
- Passwords: Firebase Auth (never stored by the app). Local mode: PBKDF2-HMAC-SHA256,
  per-user salt, 120k iterations.
- Session token lives in DataStore (not logs); HTTPS enforced by Firebase;
  cleartext blocked for release builds (`network_security_config.xml`).
- `google-services.json` contains only public client identifiers (package name,
  API key restricted per Firebase docs) — standard to commit.

## Remaining work / integration requirements

1. **Enable providers in the Firebase console:** Email/Password auth; Firestore
   database; (optional) Firebase Hosting target `mt-project-ce4c0`.
2. **Deploy rules & hosting:** `firebase deploy --only firestore,hosting`.
3. **Payment provider (Mode B):** M-Pesa/statement reconciliation — requires a
   provider account + backend callback service. Until then the app stays in
   tracking mode and labels entries accordingly.
4. **Notifications:** in-app only. Push (FCM) and Cloud-Function-generated
   notifications are the next step; rules currently allow client notification
   creates (documented in `firestore.rules`).
5. **Instrumented Compose UI tests** (device required, see Testing).
6. **Firebase BOM 35.x upgrade** once Kotlin/KSP/Room can move together (see above).
7. **Multi-tenant hardening:** move join/capacity validation into a Cloud
   Function if targeting adversarial usage at scale (documented in `docs/FIREBASE.md`).

## Repository layout

See `app/src/main/java/com/example/merrygoround/` — `navigation/`, `ui/`,
`domain/`, `data/` (backend + local + repository), `core/`, `di/`.
Firebase project files: `firebase.json`, `.firebaserc`, `firestore.rules`,
`firestore.indexes.json`, `web/` (hosting content).

