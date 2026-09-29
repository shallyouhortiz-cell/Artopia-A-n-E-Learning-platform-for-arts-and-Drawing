import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { CreateUserSchema } from "../types/index.js";
import { requireRole, assertPasswordChanged } from "../guards/index.js";
import {
  getAdminAuth,
  getAdminDb,
  getAdminRtdb,
  generateSecurePassword,
  writeAuditLog,
  maskEmail,
} from "../services/index.js";

export async function createUserHandler(req: CallableRequest) {
  const caller = requireRole(req, ["admin"]);
  await assertPasswordChanged(caller.uid);

  const parseResult = CreateUserSchema.safeParse(req.data);
  if (!parseResult.success) {
    throw new HttpsError(
      "invalid-argument",
      parseResult.error.issues[0]?.message || "Invalid input."
    );
  }

  const { email, nickname, role, age } = parseResult.data;
  const tempPassword = generateSecurePassword(24);

  const auth = getAdminAuth();
  const db = getAdminDb();
  const rtdb = getAdminRtdb();

  let newUser;
  try {
    newUser = await auth.createUser({
      email,
      password: tempPassword,
      displayName: nickname,
    });
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    logger.error("Auth creation failed:", error.code || error.message);
    if (error.code === "auth/email-already-exists") {
      throw new HttpsError("already-exists", "An account with this email address already exists.");
    }
    throw new HttpsError("invalid-argument", "Failed to create user account.");
  }

  try {
    await auth.setCustomUserClaims(newUser.uid, { role });

    const setupLink = await auth.generatePasswordResetLink(email);

    const emailContent = `
      <p>Dear ${nickname},</p>
      <p>Your ${role} account has been created for Artopia.</p>
      <p>Please set up your account password and verify your email by clicking the link below:</p>
      <p><a href="${setupLink}">${setupLink}</a></p>
      <p>If you did not request this account, please contact support immediately.</p>
      <p>Thank you,<br>Artopia Support</p>
    `.trim();

    const userRef = db.collection("users").doc(newUser.uid);
    const metaRef = userRef.collection("private").doc("meta");

    await Promise.all([
      rtdb.ref(`users/${newUser.uid}`).set({
        email,
        nickname,
        role,
        status: "active",
        presence: "offline",
      }),
      userRef.set({
        uid: newUser.uid,
        email,
        nickname,
        age: age ?? 0,
      }),
      metaRef.set({
        uid: newUser.uid,
        email,
        role,
        status: "active",
        isVerified: false,
        mustChangePassword: false,
        createdAt: FieldValue.serverTimestamp(),
      }),
      db.collection("mail").add({
        to: email,
        message: {
          subject: "Welcome to Artopia - Account Setup",
          html: emailContent,
        },
      }),
    ]);

    await writeAuditLog({
      actorUid: caller.uid,
      actorEmail: caller.email,
      action: "CREATE_USER",
      message: `Created ${role} account for ${maskEmail(email)}`,
      targetUid: newUser.uid,
      details: { role, age: age ?? 0 },
    });

    return { uid: newUser.uid };
  } catch (err: unknown) {
    logger.error("Database initialization failed, rolling back Auth user:", newUser.uid, err);
    try {
      await auth.deleteUser(newUser.uid);
    } catch (rollbackErr) {
      logger.error("Failed to rollback Auth user:", newUser.uid, rollbackErr);
    }
    throw new HttpsError("internal", "Failed to complete account setup.");
  }
}
