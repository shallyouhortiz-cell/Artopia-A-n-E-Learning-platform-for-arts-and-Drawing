/**
 * 🚨 CRITICAL DANGER CLI UTILITY: Delete All Auth Users
 *
 * This script will bulk-delete users from Firebase Authentication (and option to preserve admins).
 *
 * Usage:
 *   1. Dry Run (Preview users to be deleted WITHOUT modifying data):
 *      npx ts-node scripts/delete-all-auth-users.ts --dry-run
 *
 *   2. Execute Deletion (Deletes users from Auth, Firestore, and RTDB):
 *      npx ts-node scripts/delete-all-auth-users.ts --confirm-delete-all
 *
 *   3. Execute Deletion while keeping Admins safe:
 *      npx ts-node scripts/delete-all-auth-users.ts --confirm-delete-all --keep-admins
 */

import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import * as fs from "fs";
import * as path from "path";

const isConfirmMode = process.argv.includes("--confirm-delete-all");
const keepAdmins = process.argv.includes("--keep-admins");
const DRY_RUN = !isConfirmMode;

const DB_URL = "https://artopia-54e07-default-rtdb.asia-southeast1.firebasedatabase.app";

function initAdminSDK() {
  if (getApps().length > 0) return;

  const localKeyPath = path.join(__dirname, "service-account.json");
  const randomFolderKeyPath = "E:\\random\\artopia-54e07-firebase-adminsdk-fbsvc-5704151513.json";

  if (fs.existsSync(localKeyPath)) {
    console.log(`Using service account key: ${localKeyPath}`);
    const serviceAccount = JSON.parse(fs.readFileSync(localKeyPath, "utf8"));
    initializeApp({ credential: cert(serviceAccount), projectId: "artopia-54e07", databaseURL: DB_URL });
  } else if (fs.existsSync(randomFolderKeyPath)) {
    console.log(`Using service account key: ${randomFolderKeyPath}`);
    const serviceAccount = JSON.parse(fs.readFileSync(randomFolderKeyPath, "utf8"));
    initializeApp({ credential: cert(serviceAccount), projectId: "artopia-54e07", databaseURL: DB_URL });
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    console.log(`Using GOOGLE_APPLICATION_CREDENTIALS: ${process.env.GOOGLE_APPLICATION_CREDENTIALS}`);
    initializeApp({ projectId: "artopia-54e07", databaseURL: DB_URL });
  } else {
    console.log("Initializing Admin SDK with default environment credentials...");
    initializeApp({ projectId: "artopia-54e07", databaseURL: DB_URL });
  }
}

initAdminSDK();

const auth = getAuth();
const db = getFirestore();

async function processUserBatch(nextPageToken?: string): Promise<{ totalFound: number; totalDeleted: number }> {
  const result = await auth.listUsers(1000, nextPageToken);

  if (result.users.length === 0) {
    return { totalFound: 0, totalDeleted: 0 };
  }

  let targetUsers = result.users;

  if (keepAdmins) {
    targetUsers = targetUsers.filter((u) => u.customClaims?.role !== "admin");
  }

  const targetUids = targetUsers.map((u) => u.uid);

  let deletedCount = 0;

  if (targetUids.length > 0) {
    if (DRY_RUN) {
      console.log(`\n[DRY RUN] Would delete ${targetUids.length} user(s) on this page:`);
      targetUsers.forEach((u) => {
        const role = u.customClaims?.role || "none";
        console.log(`  - UID: ${u.uid} | Email: ${u.email || "anonymous"} | Role: ${role}`);
      });
    } else {
      console.log(`\nDeleting ${targetUids.length} user(s)...`);
      const deleteResult = await auth.deleteUsers(targetUids);
      deletedCount = deleteResult.successCount;
      console.log(`Successfully deleted ${deleteResult.successCount} user(s). Failed: ${deleteResult.failureCount}`);

      if (deleteResult.errors.length > 0) {
        console.error("Deletion errors:", deleteResult.errors);
      }

      // Cleanup Firestore & RTDB docs for deleted users
      const firestoreBatch = db.batch();
      targetUids.forEach((uid) => {
        firestoreBatch.delete(db.collection("users").doc(uid));
      });
      await firestoreBatch.commit().catch(() => {});

      try {
        const rtdb = getDatabase();
        const rtdbUpdates: Record<string, null> = {};
        targetUids.forEach((uid) => {
          rtdbUpdates[`users/${uid}`] = null;
        });
        await rtdb.ref().update(rtdbUpdates).catch(() => {});
      } catch (e) {
        console.warn("RTDB cleanup warning:", e);
      }
    }
  }

  let totalFound = targetUids.length;
  let totalDeleted = deletedCount;

  if (result.pageToken) {
    const nextPage = await processUserBatch(result.pageToken);
    totalFound += nextPage.totalFound;
    totalDeleted += nextPage.totalDeleted;
  }

  return { totalFound, totalDeleted };
}

async function main() {
  console.log("==================================================");
  console.log("  ⚠️ FIREBASE AUTH BULK USER DELETION UTILITY");
  console.log(`  Project: artopia-54e07`);
  console.log(`  Mode: ${DRY_RUN ? "🔍 DRY RUN (Preview only)" : "🚨 PERMANENT DELETION"}`);
  if (keepAdmins) console.log("  Protection: Admin accounts will be PRESERVED.");
  console.log("==================================================");

  try {
    const summary = await processUserBatch();
    console.log("\n--------------------------------------------------");
    if (DRY_RUN) {
      console.log(`Dry run complete. Found ${summary.totalFound} user(s) targeted.`);
      console.log(`To perform actual deletion, run:\n  npx ts-node scripts/delete-all-auth-users.ts --confirm-delete-all`);
      if (!keepAdmins) {
        console.log(`To preserve admin accounts, append --keep-admins:\n  npx ts-node scripts/delete-all-auth-users.ts --confirm-delete-all --keep-admins`);
      }
    } else {
      console.log(`Bulk deletion complete. Total deleted: ${summary.totalDeleted} / ${summary.totalFound}`);
    }
    console.log("--------------------------------------------------");
    process.exit(0);
  } catch (error) {
    console.error("\nExecution failed:", error);
    process.exit(1);
  }
}

main();
