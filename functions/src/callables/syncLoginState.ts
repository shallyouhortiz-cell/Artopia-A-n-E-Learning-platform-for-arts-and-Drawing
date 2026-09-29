import { CallableRequest } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { requireAuth } from "../guards/index.js";
import { getAdminDb } from "../services/index.js";

export async function syncLoginStateHandler(req: CallableRequest) {
  const uid = requireAuth(req);
  const isEmailVerified = Boolean(req.auth?.token?.email_verified);

  const metaRef = getAdminDb().collection("users").doc(uid).collection("private").doc("meta");
  const metaSnap = await metaRef.get();

  if (metaSnap.exists) {
    await metaRef.update({
      lastLogin: FieldValue.serverTimestamp(),
      isVerified: isEmailVerified,
    });
  }

  return { success: true };
}
