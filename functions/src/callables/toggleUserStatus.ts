import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { ToggleUserStatusSchema } from "../types/index.js";
import {
  requireRole,
  assertPasswordChanged,
  assertNotSelf,
  assertTeacherPermissions,
} from "../guards/index.js";
import {
  getAdminAuth,
  getAdminDb,
  getAdminRtdb,
  writeAuditLog,
} from "../services/index.js";

export async function toggleUserStatusHandler(req: CallableRequest) {
  const caller = requireRole(req, ["admin", "teacher"]);
  await assertPasswordChanged(caller.uid);

  const parseResult = ToggleUserStatusSchema.safeParse(req.data);
  if (!parseResult.success) {
    throw new HttpsError("invalid-argument", "Invalid input parameters.");
  }

  const { uid: targetUid, status: newStatus } = parseResult.data;

  assertNotSelf(caller.uid, targetUid, "change status");

  const db = getAdminDb();
  const auth = getAdminAuth();
  const rtdb = getAdminRtdb();

  const metaRef = db.collection("users").doc(targetUid).collection("private").doc("meta");
  const metaSnap = await metaRef.get();

  if (!metaSnap.exists) {
    throw new HttpsError("not-found", "Target user metadata does not exist.");
  }

  const targetData = metaSnap.data();
  // Server-side re-derived target role:
  const targetRole = targetData?.role || "student";
  const oldStatus = targetData?.status || "active";

  // If caller is teacher, server rejects if target is not a student
  assertTeacherPermissions(caller.role, targetRole, newStatus);

  // Last-admin check if suspending/banning
  if (newStatus !== "active" && targetRole === "admin") {
    const adminQuery = db.collectionGroup("private").where("role", "==", "admin");
    const adminDocs = await adminQuery.get();
    const activeAdmins = adminDocs.docs.filter((d) => {
      const status = d.data().status;
      return status !== "banned" && status !== "suspended";
    });

    if (activeAdmins.length <= 1 && activeAdmins.some((d) => d.ref.parent.parent?.id === targetUid)) {
      throw new HttpsError(
        "failed-precondition",
        "Cannot suspend or ban the last admin account."
      );
    }
  }

  const disabled = newStatus !== "active";
  await auth.updateUser(targetUid, { disabled });

  if (disabled) {
    await auth.revokeRefreshTokens(targetUid);
  }

  await Promise.all([
    metaRef.update({ status: newStatus }),
    rtdb.ref(`users/${targetUid}/status`).set(newStatus),
  ]);

  await writeAuditLog({
    actorUid: caller.uid,
    actorEmail: caller.email,
    action: "TOGGLE_USER_STATUS",
    message: `Changed status from ${oldStatus} to ${newStatus} for user ${targetUid}`,
    targetUid,
    details: { oldStatus, newStatus, targetRole },
  });

  return { success: true };
}
