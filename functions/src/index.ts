import { setGlobalOptions } from "firebase-functions/v2";
import { onCall, CallableRequest } from "firebase-functions/v2/https";
import { createUserHandler } from "./callables/createUser.js";
import { deleteUserAccountHandler } from "./callables/deleteUserAccount.js";
import { updateUserRoleHandler } from "./callables/updateUserRole.js";
import { toggleUserStatusHandler } from "./callables/toggleUserStatus.js";
import { updateUserProfileHandler } from "./callables/updateUserProfile.js";
import { syncLoginStateHandler } from "./callables/syncLoginState.js";
import { completePasswordChangeHandler } from "./callables/completePasswordChange.js";
import { logActivityHandler } from "./callables/logActivity.js";
export { onUserPresenceChange } from "./triggers/presence.js";

setGlobalOptions({
  region: "asia-southeast1",
  maxInstances: 10,
  timeoutSeconds: 30,
});

const callOptions = {
  enforceAppCheck: true,
};

export const createUser = onCall(callOptions, (req: CallableRequest) => createUserHandler(req));
export const deleteUserAccount = onCall(callOptions, (req: CallableRequest) => deleteUserAccountHandler(req));
export const updateUserRole = onCall(callOptions, (req: CallableRequest) => updateUserRoleHandler(req));
export const toggleUserStatus = onCall(callOptions, (req: CallableRequest) => toggleUserStatusHandler(req));
export const updateUserProfile = onCall(callOptions, (req: CallableRequest) => updateUserProfileHandler(req));
export const syncLoginState = onCall(callOptions, (req: CallableRequest) => syncLoginStateHandler(req));
export const completePasswordChange = onCall(callOptions, (req: CallableRequest) => completePasswordChangeHandler(req));
export const logActivity = onCall(callOptions, (req: CallableRequest) => logActivityHandler(req));
