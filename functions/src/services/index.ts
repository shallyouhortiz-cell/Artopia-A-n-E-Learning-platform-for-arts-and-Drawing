import { initializeApp, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import * as crypto from "crypto";
import * as logger from "firebase-functions/logger";

function ensureApp() {
  if (getApps().length === 0) {
    initializeApp();
  }
}

export const getAdminAuth = () => {
  ensureApp();
  return getAuth();
};

export const getAdminDb = () => {
  ensureApp();
  return getFirestore();
};

export const getAdminRtdb = () => {
  ensureApp();
  return getDatabase();
};

export function maskEmail(email: string | undefined): string {
  if (!email) return "anonymous";
  const [local, domain] = email.split("@");
  if (!domain) return "***";
  if (local.length <= 2) return `${local[0]}***@${domain}`;
  return `${local[0]}***${local[local.length - 1]}@${domain}`;
}

export function generateSecurePassword(length = 24): string {
  return crypto.randomBytes(length).toString("base64").slice(0, length);
}

export async function writeAuditLog(params: {
  actorUid: string;
  actorEmail?: string;
  action: string;
  message: string;
  targetUid?: string;
  details?: Record<string, unknown>;
}): Promise<void> {
  try {
    await getAdminDb().collection("adminLogs").add({
      action: params.action,
      message: params.message,
      actorUid: params.actorUid,
      actorEmail: params.actorEmail || null,
      targetUid: params.targetUid || "",
      source: "cloud-function",
      timestamp: FieldValue.serverTimestamp(),
      details: params.details || null,
    });
  } catch (err) {
    logger.error("Failed to write audit log:", err);
  }
}
