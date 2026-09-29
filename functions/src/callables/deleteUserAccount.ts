import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { DeleteUserSchema } from "../types/index.js";
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

export async function deleteUserAccountHandler(req: CallableRequest) {
  const caller = requireRole(req, ["admin"]);
  await assertPasswordChanged(caller.uid);

  const parseResult = DeleteUserSchema.safeParse(req.data);
  if (!parseResult.success) {
    throw new HttpsError("invalid-argument", "Invalid input parameters.");
  }

  const { uid: targetUid } = parseResult.data;

  assertNotSelf(caller.uid, targetUid, "delete user");

  const db = getAdminDb();
  const auth = getAdminAuth();
  const rtdb = getAdminRtdb();

  const userDocRef = db.collection("users").doc(targetUid);
  const metaDocRef = userDocRef.collection("private").doc("meta");

  // Last-admin check inside transaction
  try {
    await db.runTransaction(async (transaction) => {
      const metaSnap = await transaction.get(metaDocRef);
      if (metaSnap.exists && metaSnap.data()?.role === "admin") {
        const adminQuery = db.collectionGroup("private").where("role", "==", "admin");
        const adminDocs = await transaction.get(adminQuery);
        const activeAdmins = adminDocs.docs.filter((d) => {
          const status = d.data().status;
          return status !== "banned" && status !== "suspended";
        });

        if (activeAdmins.length <= 1 && activeAdmins.some((d) => d.ref.parent.parent?.id === targetUid)) {
          throw new HttpsError(
            "failed-precondition",
            "Cannot remove or delete the last admin account."
          );
        }
      }

      transaction.delete(metaDocRef);
      transaction.delete(userDocRef);
    });
  } catch (err: unknown) {
    if (err instanceof HttpsError) throw err;
    logger.error("Transaction failed during user deletion:", targetUid, err);
    throw new HttpsError("internal", "Failed to delete user record.");
  }

  try {
    await auth.revokeRefreshTokens(targetUid);
  } catch (err) {
    logger.warn("Revoking tokens failed (user may already be deleted):", targetUid, err);
  }

  try {
    await auth.deleteUser(targetUid);
  } catch (err: unknown) {
    const error = err as { code?: string };
    if (error.code !== "auth/user-not-found") {
      logger.error("Auth user deletion error:", targetUid, err);
    }
  }

  await Promise.all([
    rtdb.ref(`users/${targetUid}`).remove(),
    writeAuditLog({
      actorUid: caller.uid,
      actorEmail: caller.email,
      action: "DELETE_USER",
      message: `Deleted user account ${targetUid}`,
      targetUid,
    }),
  ]);

  return { success: true };
}
