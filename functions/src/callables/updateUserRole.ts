import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { UpdateUserRoleSchema } from "../types/index.js";
import {
  requireRole,
  assertPasswordChanged,
  assertNotSelf,
} from "../guards/index.js";
import {
  getAdminAuth,
  getAdminDb,
  getAdminRtdb,
  writeAuditLog,
} from "../services/index.js";

export async function updateUserRoleHandler(req: CallableRequest) {
  const caller = requireRole(req, ["admin"]);
  await assertPasswordChanged(caller.uid);

  const parseResult = UpdateUserRoleSchema.safeParse(req.data);
  if (!parseResult.success) {
    throw new HttpsError("invalid-argument", "Invalid input parameters.");
  }

  const { uid: targetUid, newRole } = parseResult.data;

  assertNotSelf(caller.uid, targetUid, "change role");

  const db = getAdminDb();
  const auth = getAdminAuth();
  const rtdb = getAdminRtdb();

  // 1. Idempotency Check: if claims & meta doc already match newRole, return success
  let targetUserAuth;
  try {
    targetUserAuth = await auth.getUser(targetUid);
  } catch {
    throw new HttpsError("not-found", "Target user does not exist in Auth.");
  }

  const metaRef = db.collection("users").doc(targetUid).collection("private").doc("meta");
  const currentMetaSnap = await metaRef.get();
  if (!currentMetaSnap.exists) {
    throw new HttpsError("not-found", "Target user metadata does not exist.");
  }

  const currentMetaRole = currentMetaSnap.data()?.role;
  const currentClaimRole = targetUserAuth.customClaims?.role;

  if (currentClaimRole === newRole && currentMetaRole === newRole) {
    logger.info(`updateUserRole idempotency check: ${targetUid} is already ${newRole}. No-op.`);
    return { success: true };
  }

  // 2. Transactional Firestore Update + Last-Admin Check
  try {
    await db.runTransaction(async (transaction) => {
      const metaDoc = await transaction.get(metaRef);
      if (!metaDoc.exists) {
        throw new HttpsError("not-found", "Target user metadata does not exist.");
      }

      const existingRole = metaDoc.data()?.role;

      // Last admin race condition guard inside transaction
      if (existingRole === "admin" && newRole !== "admin") {
        const adminQuery = db.collectionGroup("private").where("role", "==", "admin");
        const adminDocs = await transaction.get(adminQuery);
        const activeAdmins = adminDocs.docs.filter((d) => {
          const status = d.data().status;
          return status !== "banned" && status !== "suspended";
        });

        if (activeAdmins.length <= 1 && activeAdmins.some((d) => d.ref.parent.parent?.id === targetUid)) {
          throw new HttpsError(
            "failed-precondition",
            "Cannot remove or demote the last admin account."
          );
        }
      }

      transaction.update(metaRef, {
        role: newRole,
        tokensValidAfterTime: FieldValue.serverTimestamp(),
      });
    });
  } catch (err: unknown) {
    if (err instanceof HttpsError) throw err;
    logger.error("Transaction failed during role update:", targetUid, err);
    throw new HttpsError("internal", "Failed to update user role in database.");
  }

  // 3. Set Custom Claims AFTER Firestore transaction commits successfully
  try {
    await auth.setCustomUserClaims(targetUid, { role: newRole });
    await auth.revokeRefreshTokens(targetUid);
  } catch (err: unknown) {
    logger.error("Claims setting failed post-Firestore commit!", targetUid, err);
    await writeAuditLog({
      actorUid: caller.uid,
      actorEmail: caller.email,
      action: "ROLE_RECONCILIATION_NEEDED",
      message: `CRITICAL: Firestore role updated to ${newRole} for ${targetUid}, but Custom Claim update failed.`,
      targetUid,
      details: { RECONCILIATION_NEEDED: true, newRole, targetUid },
    });
    throw new HttpsError(
      "internal",
      "Role updated in database, but failed to refresh security claims. Reconciliation required."
    );
  }

  // 4. Update RTDB mirror & write audit log
  await Promise.all([
    rtdb.ref(`users/${targetUid}/role`).set(newRole),
    writeAuditLog({
      actorUid: caller.uid,
      actorEmail: caller.email,
      action: "UPDATE_USER_ROLE",
      message: `Updated role from ${currentMetaRole} to ${newRole} for user ${targetUid}`,
      targetUid,
      details: { oldRole: currentMetaRole, newRole },
    }),
  ]);

  return { success: true };
}
