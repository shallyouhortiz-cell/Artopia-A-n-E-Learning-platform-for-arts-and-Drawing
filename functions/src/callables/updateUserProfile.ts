import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { UpdateUserProfileSchema } from "../types/index.js";
import {
  requireRole,
  assertPasswordChanged,
  assertTeacherPermissions,
} from "../guards/index.js";
import {
  getAdminDb,
  getAdminRtdb,
  writeAuditLog,
} from "../services/index.js";

export async function updateUserProfileHandler(req: CallableRequest) {
  const caller = requireRole(req, ["admin", "teacher", "student"]);
  await assertPasswordChanged(caller.uid);

  const parseResult = UpdateUserProfileSchema.safeParse(req.data);
  if (!parseResult.success) {
    throw new HttpsError("invalid-argument", "Invalid input parameters.");
  }

  const { uid: targetUid, nickname, age, profileImageUrl } = parseResult.data;

  const db = getAdminDb();
  const rtdb = getAdminRtdb();

  const userDocRef = db.collection("users").doc(targetUid);
  const userSnap = await userDocRef.get();
  if (!userSnap.exists) {
    throw new HttpsError("not-found", "Target user does not exist.");
  }

  const metaSnap = await userDocRef.collection("private").doc("meta").get();
  const targetRole = metaSnap.data()?.role || "student";

  const isSelf = caller.uid === targetUid;
  if (!isSelf && caller.role !== "admin") {
    assertTeacherPermissions(caller.role, targetRole);
  }

  const updatePayload: Record<string, unknown> = {};
  if (nickname !== undefined) updatePayload.nickname = nickname;
  if (age !== undefined) updatePayload.age = age;
  if (profileImageUrl !== undefined) updatePayload.profileImageUrl = profileImageUrl;

  if (Object.keys(updatePayload).length === 0) {
    return { success: true };
  }

  await userDocRef.update(updatePayload);

  if (nickname !== undefined) {
    await rtdb.ref(`users/${targetUid}/nickname`).set(nickname);
  }

  await writeAuditLog({
    actorUid: caller.uid,
    actorEmail: caller.email,
    action: "UPDATE_USER_PROFILE",
    message: `Updated profile for user ${targetUid}`,
    targetUid,
    details: updatePayload,
  });

  return { success: true };
}
