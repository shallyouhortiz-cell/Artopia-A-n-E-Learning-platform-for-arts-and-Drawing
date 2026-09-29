# Security & Architecture Documentation - Artopia Control

## 1. Role Matrix & Access Control

| Feature / Action | Admin | Teacher | Student / Client |
| :--- | :---: | :---: | :---: |
| Access Control Panel App | ✅ | ✅ | ❌ Denied |
| Read Student Users | ✅ | ✅ | Self Only |
| Read Teacher / Admin Users | ✅ | ❌ Denied | Self Only |
| Create Accounts (Teachers/Students) | ✅ | ❌ Denied | ❌ Denied |
| Suspend / Unsuspend Student | ✅ | ✅ | ❌ Denied |
| Ban User | ✅ | ❌ Denied | ❌ Denied |
| Edit Roles | ✅ | ❌ Denied | ❌ Denied |
| Delete User Accounts | ✅ | ❌ Denied | ❌ Denied |
| Read System Audit Logs (`adminLogs`) | ✅ | ❌ Denied | ❌ Denied |
| Self Profile Updates | ✅ | ✅ | ✅ (Limited fields) |

---

## 2. First-Admin Bootstrap Procedure

To create the initial administrator account on a clean environment, use the CLI bootstrap script:

```bash
cd functions
npx ts-node scripts/bootstrap-admin.ts <admin_email> <admin_password> "Admin Name"
```

> **Note**: This script uses the Firebase Admin SDK locally to assign Custom User Claims (`{ role: "admin" }`) and create the primary Firestore and RTDB profile records. Never commit admin credentials or service account keys to git.

---

## 3. Deployment Order & Migration

To ensure zero downtime and prevent unauthorized client access during upgrades, deploy in this strict order:

1. **Deploy Security Rules & Cloud Functions**:
   ```bash
   firebase deploy --only firestore:rules,database,functions
   ```
2. **Deploy Android Control Panel App**:
   Deploy the updated Android APK/AAB to administrators and teachers.
3. **Run Legacy Status Normalization**:
   If upgrading an existing system where Firestore documents have `status: "offline"`, run the status normalization helper in `onUserPresenceChange` (automatically normalizes `"offline"` status to `"active"` upon connection).

---

## 4. Emulator & App Check Debug Setup

- **Local Emulators**:
  - Run `firebase emulators:start` to launch Auth (9099), Functions (5001), Firestore (8080), RTDB (9000), and UI (4000).
- **App Check Debug Token**:
  - In debug builds, `ArtopiaApplication` uses `DebugAppCheckProviderFactory`.
  - Check Logcat for `AppCheck debug token: <TOKEN>` and register it in the Firebase Console under **App Check > Apps > Manage debug tokens**.
