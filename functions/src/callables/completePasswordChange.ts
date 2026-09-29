import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { requireAuth } from "../guards/index.js";
import { getAdminDb, writeAuditLog } from "../services/index.js";

export async function completePasswordChangeHandler(req: CallableRequest) {
  const uid = requireAuth(req);

  const authTime = req.auth?.token?.auth_time as number | undefined;
  const now = Math.floor(Date.now() / 1000);

  // Allow 5 minutes + 10s clock skew buffer (310 seconds total)
  const MAX_AUTH_AGE = 310;

  if (!authTime || now - authTime > MAX_AUTH_AGE) {
    throw new HttpsError(
      "failed-precondition",
      "Reauthenticate and try again."
    );
  }

  await getAdminDb()
    .collection("users")
    .doc(uid)
    .collection("private")
    .doc("meta")
    .update({
      mustChangePassword: false,
    });

  await writeAuditLog({
    actorUid: uid,
    actorEmail: req.auth?.token?.email as string | undefined,
    action: "PASSWORD_CHANGED",
    message: "User successfully changed temporary password.",
    targetUid: uid,
  });

  return { success: true };
}
