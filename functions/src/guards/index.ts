import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { UserRole } from "../types/index.js";
import { getAdminDb } from "../services/index.js";

export function requireAuth(req: CallableRequest): string {
  if (!req.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  return req.auth.uid;
}

export function requireRole(req: CallableRequest, allowedRoles: UserRole[]): { uid: string; role: UserRole; email?: string } {
  const uid = requireAuth(req);
  const tokenRole = req.auth?.token?.role as UserRole | undefined;

  if (!tokenRole || !allowedRoles.includes(tokenRole)) {
    throw new HttpsError("permission-denied", "Insufficient permissions.");
  }

  return { uid, role: tokenRole, email: req.auth?.token?.email as string | undefined };
}

export async function assertPasswordChanged(uid: string): Promise<void> {
  const metaDoc = await getAdminDb().collection("users").doc(uid).collection("private").doc("meta").get();
  if (metaDoc.exists && metaDoc.data()?.mustChangePassword === true) {
    throw new HttpsError(
      "failed-precondition",
      "User must change password before performing this action."
    );
  }
}

export function assertNotSelf(actorUid: string, targetUid: string, actionDescription: string): void {
  if (actorUid === targetUid) {
    throw new HttpsError("invalid-argument", `Cannot ${actionDescription} on your own account.`);
  }
}

export function assertTeacherPermissions(
  callerRole: UserRole,
  targetRole: string,
  attemptedStatus?: string
): void {
  if (callerRole === "teacher") {
    if (targetRole !== "student") {
      throw new HttpsError("permission-denied", "Teachers can only manage student accounts.");
    }
    if (attemptedStatus === "banned") {
      throw new HttpsError("permission-denied", "Teachers cannot ban users (only suspend/unsuspend).");
    }
  }
}
