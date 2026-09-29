import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { ClientLogActivitySchema } from "../types/index.js";
import { requireAuth, assertPasswordChanged } from "../guards/index.js";
import { getAdminDb } from "../services/index.js";

export async function logActivityHandler(req: CallableRequest) {
  // Always pin actorUid strictly from req.auth.uid (server-side authenticated context)
  const actorUid = requireAuth(req);
  await assertPasswordChanged(actorUid);

  const parseResult = ClientLogActivitySchema.safeParse(req.data);
  if (!parseResult.success) {
    throw new HttpsError(
      "invalid-argument",
      parseResult.error.issues[0]?.message || "Invalid activity log data."
    );
  }

  const { action, message, targetUid, source, details } = parseResult.data;

  await getAdminDb().collection("adminLogs").add({
    action, // Enforced via ClientLogActionSchema allowlist
    message,
    actorUid, // Server-pinned actor UID
    actorEmail: (req.auth?.token?.email as string) || null,
    targetUid: targetUid || "",
    source: source || "android-app",
    timestamp: FieldValue.serverTimestamp(),
    details: details || null,
  });

  return { success: true };
}
