/**
 * CLI Utility: Cleanup Anonymous / Unlinked Guest Users
 *
 * Usage:
 *   1. Dry Run (preview users to be deleted without modifying data):
 *      npx ts-node scripts/cleanup-anonymous-users.ts --dry-run
 *
 *   2. Commit Deletion (permanently delete anonymous users):
 *      npx ts-node scripts/cleanup-anonymous-users.ts --commit
 *
 * Prerequisites:
 *   Set GOOGLE_APPLICATION_CREDENTIALS to your downloaded service account key path:
 *     $env:GOOGLE_APPLICATION_CREDENTIALS="C:\path\to\service-account.json"
 *   OR ensure service account key is at functions/scripts/service-account.json (git-ignored).
 */

import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import * as fs from "fs";
import * as path from "path";

const isCommitMode = process.argv.includes("--commit");
const DRY_RUN = !isCommitMode;

function initAdminSDK() {
  if (getApps().length > 0) return;

  const localServiceAccountPath = path.join(__dirname, "service-account.json");
  if (fs.existsSync(localServiceAccountPath)) {
    console.log(`Using local service account key: ${localServiceAccountPath}`);
    const serviceAccount = JSON.parse(fs.readFileSync(localServiceAccountPath, "utf8"));
    initializeApp({
      credential: cert(serviceAccount),
    });
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    console.log(`Using GOOGLE_APPLICATION_CREDENTIALS: ${process.env.GOOGLE_APPLICATION_CREDENTIALS}`);
    initializeApp();
  } else {
    console.log("Initializing Admin SDK with default environment credentials / emulator...");
    initializeApp();
  }
}

initAdminSDK();

const auth = getAuth();
const db = getFirestore();
const rtdb = getDatabase();

async function deleteAnonymousUsers(nextPageToken?: string): Promise<{ totalFound: number; totalDeleted: number }> {
  const result = await auth.listUsers(1000, nextPageToken);

  // Anonymous users have no linked sign-in providers in providerData
  const anonUsers = result.users.filter((u) => u.providerData.length === 0);
  const anonUids = anonUsers.map((u) => u.uid);

  let deletedCount = 0;

  if (anonUids.length > 0) {
    if (DRY_RUN) {
      console.log(`\n[DRY RUN] Would delete ${anonUids.length} anonymous user(s) on this page:`);
      anonUsers.forEach((u) => {
        console.log(`  - UID: ${u.uid} | Created: ${u.metadata.creationTime} | Last Login: ${u.metadata.lastSignInTime}`);
      });
    } else {
      console.log(`\nDeleting ${anonUids.length} anonymous user(s)...`);
      const deleteResult = await auth.deleteUsers(anonUids);
      deletedCount = deleteResult.successCount;
      console.log(`Successfully deleted ${deleteResult.successCount} user(s). Failed: ${deleteResult.failureCount}`);

      if (deleteResult.errors.length > 0) {
        console.error("Deletion errors:", deleteResult.errors);
      }

      // Cleanup associated user records from Firestore and RTDB for deleted UIDs
      const firestoreBatch = db.batch();
      anonUids.forEach((uid) => {
        firestoreBatch.delete(db.collection("users").doc(uid));
      });
      await firestoreBatch.commit().catch(() => {});

      const rtdbUpdates: Record<string, null> = {};
      anonUids.forEach((uid) => {
        rtdbUpdates[`users/${uid}`] = null;
      });
      await rtdb.ref().update(rtdbUpdates).catch(() => {});
    }
  }

  let totalFound = anonUids.length;
  let totalDeleted = deletedCount;

  if (result.pageToken) {
    const nextPageResults = await deleteAnonymousUsers(result.pageToken);
    totalFound += nextPageResults.totalFound;
    totalDeleted += nextPageResults.totalDeleted;
  }

  return { totalFound, totalDeleted };
}

async function main() {
  console.log("==================================================");
  console.log(`  Firebase Anonymous User Cleanup Tool`);
  console.log(`  Mode: ${DRY_RUN ? "🔍 DRY RUN (Preview only)" : "⚠️ COMMIT (Deleting users)"}`);
  console.log("==================================================");

  try {
    const summary = await deleteAnonymousUsers();
    console.log("\n--------------------------------------------------");
    if (DRY_RUN) {
      console.log(`Dry run complete. Total anonymous user(s) identified: ${summary.totalFound}`);
      console.log(`To perform actual deletion, run:\n  npx ts-node scripts/cleanup-anonymous-users.ts --commit`);
    } else {
      console.log(`Cleanup complete. Total deleted: ${summary.totalDeleted} / ${summary.totalFound}`);
    }
    console.log("--------------------------------------------------");
    process.exit(0);
  } catch (error) {
    console.error("\nExecution failed:", error);
    process.exit(1);
  }
}

main();
