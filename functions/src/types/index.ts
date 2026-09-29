import { z } from "zod";

export const UserRoleSchema = z.enum(["admin", "teacher", "student"]);
export type UserRole = z.infer<typeof UserRoleSchema>;

export const UserStatusSchema = z.enum(["active", "suspended", "banned"]);
export type UserStatus = z.infer<typeof UserStatusSchema>;

export const CreateUserSchema = z.object({
  email: z.string().email().trim().lowercase(),
  nickname: z.string().min(1).max(50).trim(),
  role: UserRoleSchema,
  age: z.number().int().min(3).max(120).optional(),
}).strict();

export const DeleteUserSchema = z.object({
  uid: z.string().min(1).trim(),
}).strict();

export const UpdateUserRoleSchema = z.object({
  uid: z.string().min(1).trim(),
  newRole: UserRoleSchema,
}).strict();

export const ToggleUserStatusSchema = z.object({
  uid: z.string().min(1).trim(),
  status: UserStatusSchema,
}).strict();

export const UpdateUserProfileSchema = z.object({
  uid: z.string().min(1).trim(),
  nickname: z.string().min(1).max(50).trim().optional(),
  age: z.number().int().min(3).max(120).optional(),
  profileImageUrl: z.string().url().trim().nullable().optional(),
}).strict();

export const ClientLogActionSchema = z.enum([
  "USER_LOGIN",
  "LOGIN_ATTEMPT_DENIED",
  "VERIFICATION_EMAIL_SENT"
]);

export const ClientLogActivitySchema = z.object({
  action: ClientLogActionSchema,
  message: z.string().min(1).max(500).trim(),
  targetUid: z.string().max(128).optional().default(""),
  source: z.literal("android-app").optional().default("android-app"),
  details: z.record(z.string(), z.unknown()).optional(),
}).strict();
