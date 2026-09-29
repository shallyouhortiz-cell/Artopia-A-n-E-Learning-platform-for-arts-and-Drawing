import { onValueWritten } from "firebase-functions/v2/database";
import { FieldValue } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { getAdminDb } from "../services/index.js";

export const onUserPresenceChange = onValueWritten(
  "/users/{uid}/presence",
  async (event) => {
    const uid = event.params.uid;
    const rawVal = event.data.after.val();
    const presence = rawVal === "online" ? "online" : "offline";

    const metaRef = getAdminDb().collection("users").doc(uid).collection("private").doc("meta");
    const metaSnap = await metaRef.get();

    if (!metaSnap.exists) {
      logger.info(`Presence skipped: user meta doc ${uid} does not exist.`);
      return;
    }

    const data = metaSnap.data();
    const updateData: Record<string, unknown> = {
      presence,
      lastSeen: FieldValue.serverTimestamp(),
    };

    if (data?.status === "offline") {
      updateData.status = "active";
    }

    await metaRef.update(updateData);
  }
);
