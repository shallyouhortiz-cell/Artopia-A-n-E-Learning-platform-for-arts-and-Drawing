/**
 * CLI Utility: Clear Firestore Collections (e.g. adminLogs, users, mail)
 *
 * Usage:
 *   1. Preview count of documents (Dry Run):
 *      npx ts-node scripts/clear-admin-logs.ts --collection=users,mail,adminLogs --dry-run
 *
 *   2. Delete document collections (Commit):
 *      npx ts-node scripts/clear-admin-logs.ts --collection=users,mail,adminLogs --commit
 */

import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import * as fs from "fs";
import * as path from "path";

const isCommitMode = process.argv.includes("--commit");
const DRY_RUN = !isCommitMode;

const collectionArg = process.argv.find((arg) => arg.startsWith("--collection="));
const rawCollections = collectionArg ? collectionArg.split("=")[1] : "adminLogs";
const collections = rawCollections.split(",").map((s) => s.trim()).filter(Boolean);

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
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    console.log(`Using GOOGLE_APPLICATION_CREDENTIALS: ${process.env.GOOGLE_APPLICATION_CREDENTIALS}`);
    initializeApp({ projectId: "artopia-54e07" });
  } else {
    console.log("Initializing Admin SDK with default environment credentials...");
    initializeApp({ projectId: "artopia-54e07" });
  }
}

initAdminSDK();

const db = getFirestore();

async function main() {
  console.log("==================================================");
  console.log(`  Firestore Collections Cleanup Tool`);
  console.log(`  Project: artopia-54e07`);
  console.log(`  Target Collections: ${collections.join(", ")}`);
  console.log(`  Mode: ${DRY_RUN ? "🔍 DRY RUN (Preview only)" : "🚨 PERMANENT DELETION"}`);
  console.log("==================================================");

  for (const colName of collections) {
    try {
      const colRef = db.collection(colName);
      const countSnapshot = await colRef.count().get();
      const totalDocs = countSnapshot.data().count;

      console.log(`\nCollection '${colName}': Found ${totalDocs} document(s).`);

      if (totalDocs === 0) {
        console.log(`  -> Already empty.`);
        continue;
      }

      if (DRY_RUN) {
        console.log(`  -> [DRY RUN] Would delete ${totalDocs} document(s).`);
      } else {
        console.log(`  -> Deleting ${totalDocs} document(s)...`);
        await db.recursiveDelete(colRef);
        console.log(`  -> Successfully deleted all documents from '${colName}'.`);
      }
    } catch (error) {
      console.error(`  -> Failed to process '${colName}':`, error);
    }
  }

  console.log("\n--------------------------------------------------");
  if (DRY_RUN) {
    console.log(`Dry run complete. To execute deletion, run:\n`);
    console.log(`  npx ts-node scripts/clear-admin-logs.ts --collection=${collections.join(",")} --commit`);
  } else {
    console.log(`Cleanup complete for collections: ${collections.join(", ")}`);
  }
  console.log("--------------------------------------------------");
  process.exit(0);
}

main();
