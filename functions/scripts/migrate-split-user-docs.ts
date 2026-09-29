/**
 * CLI Migration Script: Split Existing User Documents
 * Moves privileged fields (role, status, email, isVerified, mustChangePassword, etc.)
 * from users/{uid} into the new private subcollection doc users/{uid}/private/meta.
 *
 * Usage:
 *   npx ts-node scripts/migrate-split-user-docs.ts --dry-run
 *   npx ts-node scripts/migrate-split-user-docs.ts --commit
 */

import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import * as fs from "fs";
import * as path from "path";

const isCommitMode = process.argv.includes("--commit");
const DRY_RUN = !isCommitMode;

function initAdminSDK() {
  if (getApps().length > 0) return;

  const localKeyPath = path.join(__dirname, "service-account.json");
  const randomFolderKeyPath = "E:\\random\\artopia-54e07-firebase-adminsdk-fbsvc-5704151513.json";

  if (fs.existsSync(localKeyPath)) {
    console.log(`Using service account key: ${localKeyPath}`);
    const serviceAccount = JSON.parse(fs.readFileSync(localKeyPath, "utf8"));
    initializeApp({ credential: cert(serviceAccount), projectId: "artopia-54e07" });
  } else if (fs.existsSync(randomFolderKeyPath)) {
    console.log(`Using service account key: ${randomFolderKeyPath}`);
    const serviceAccount = JSON.parse(fs.readFileSync(randomFolderKeyPath, "utf8"));
    initializeApp({ credential: cert(serviceAccount), projectId: "artopia-54e07" });
  } else {
    initializeApp({ projectId: "artopia-54e07" });
  }
}

initAdminSDK();

const db = getFirestore();

async function migrate() {
  console.log("==================================================");
  console.log("  Firestore User Document Split Migration");
  console.log(`  Mode: ${DRY_RUN ? "🔍 DRY RUN (Preview)" : "🚀 COMMIT (Migrating)"}`);
  console.log("==================================================");

  const usersSnap = await db.collection("users").get();
  console.log(`\nFound ${usersSnap.size} user document(s) in 'users' collection.`);

  let migratedCount = 0;

  for (const doc of usersSnap.docs) {
    const data = doc.data();
    const uid = doc.id;

    const metaRef = doc.ref.collection("private").doc("meta");
    const metaSnap = await metaRef.get();

    if (metaSnap.exists) {
      console.log(`  - User ${uid} already has private/meta doc. Skipping.`);
      continue;
    }

    const metaData = {
      uid,
      email: data.email || "",
      role: data.role || "student",
      status: data.status === "offline" ? "active" : (data.status || "active"),
      isVerified: data.isVerified ?? false,
      mustChangePassword: data.mustChangePassword ?? false,
      createdAt: data.createdAt || FieldValue.serverTimestamp(),
      lastLogin: data.lastLogin || null,
      lastSeen: data.lastSeen || null,
    };

    const updatedPublicData = {
      uid,
      email: data.email || "",
      nickname: data.nickname || "",
      age: data.age ?? 0,
      profileImageUrl: data.profileImageUrl || null,
    };

    if (DRY_RUN) {
      console.log(`  - [DRY RUN] Would split doc for ${uid} (${data.email}):`);
      console.log(`      Public doc:`, updatedPublicData);
      console.log(`      Private/meta doc:`, metaData);
    } else {
      console.log(`  - Migrating ${uid} (${data.email})...`);
      const batch = db.batch();
      batch.set(metaRef, metaData);
      batch.set(doc.ref, updatedPublicData);
      await batch.commit();
      migratedCount++;
    }
  }

  console.log("\n--------------------------------------------------");
  if (DRY_RUN) {
    console.log("Dry run complete. To execute migration, run:\n  npx ts-node scripts/migrate-split-user-docs.ts --commit");
  } else {
    console.log(`Migration complete! Successfully migrated ${migratedCount} user document(s).`);
  }
  console.log("--------------------------------------------------");
  process.exit(0);
}

migrate().catch((err) => {
  console.error("Migration failed:", err);
  process.exit(1);
});
