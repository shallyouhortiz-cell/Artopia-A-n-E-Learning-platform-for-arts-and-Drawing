/**
 * CLI Script: Bootstrap Initial Admin Account
 * Usage:
 *   npx ts-node scripts/bootstrap-admin.ts <email> <password> <nickname>
 *
 * Requirements:
 *   GOOGLE_APPLICATION_CREDENTIALS path to service account key OR running under Firebase Emulator.
 */

import { initializeApp, cert } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";

const email = process.argv[2];
const password = process.argv[3];
const nickname = process.argv[4] || "Admin";

if (!email || !password) {
  console.error("Usage: npx ts-node scripts/bootstrap-admin.ts <email> <password> [nickname]");
  process.exit(1);
}

initializeApp();

const auth = getAuth();
const db = getFirestore();
const rtdb = getDatabase();

async function bootstrapAdmin() {
  console.log(`Bootstrapping admin account for ${email}...`);

  let user;
  try {
    user = await auth.getUserByEmail(email);
    console.log(`Found existing user with UID ${user.uid}. Upgrading to admin...`);
  } catch {
    user = await auth.createUser({
      email,
      password,
      displayName: nickname,
      emailVerified: true,
    });
    console.log(`Created new user with UID ${user.uid}.`);
  }

  await auth.setCustomUserClaims(user.uid, { role: "admin" });

  await Promise.all([
    rtdb.ref(`users/${user.uid}`).set({
      email,
      nickname,
      role: "admin",
      status: "active",
      presence: "offline",
    }),
    db.collection("users").doc(user.uid).set({
      uid: user.uid,
      email,
      nickname,
      role: "admin",
      status: "active",
      isVerified: true,
      mustChangePassword: false,
      createdAt: FieldValue.serverTimestamp(),
    }),
  ]);

  console.log(`Successfully bootstrapped admin account for ${email} (${user.uid})!`);
  process.exit(0);
}

bootstrapAdmin().catch((err) => {
  console.error("Bootstrap failed:", err);
  process.exit(1);
});
