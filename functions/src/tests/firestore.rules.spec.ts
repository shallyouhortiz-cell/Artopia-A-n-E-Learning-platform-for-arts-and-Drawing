import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import { resolve } from "path";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  const rulesPath = resolve(__dirname, "../../../firestore.rules");
  testEnv = await initializeTestEnvironment({
    projectId: "artopia-rules-test",
    firestore: {
      rules: readFileSync(rulesPath, "utf8"),
    },
  });
});

afterAll(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

describe("Firestore Security Rules Matrix", () => {
  describe("users collection & private/meta subcollection", () => {
    it("allows user to read their own public document", async () => {
      const db = testEnv.authenticatedContext("user1", { role: "student" }).firestore();
      await expect(db.collection("users").doc("user1").get()).resolves.toBeDefined();
    });

    it("allows authenticated user to read other user public documents", async () => {
      const db = testEnv.authenticatedContext("user1", { role: "student" }).firestore();
      await expect(db.collection("users").doc("user2").get()).resolves.toBeDefined();
    });

    it("denies unauthenticated read access to users", async () => {
      const db = testEnv.unauthenticatedContext().firestore();
      await expect(db.collection("users").doc("user1").get()).rejects.toThrow();
    });

    it("allows user to update allowed profile fields on self", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await context.firestore().collection("users").doc("user1").set({
          uid: "user1",
          email: "user1@example.com",
          nickname: "User One",
          age: 20,
        });
      });

      const db = testEnv.authenticatedContext("user1", { role: "student" }).firestore();
      await expect(
        db.collection("users").doc("user1").update({
          nickname: "User One Updated",
          age: 21,
        })
      ).resolves.not.toThrow();
    });

    it("denies client-side writes to private/meta subcollection for all clients", async () => {
      const adminDb = testEnv.authenticatedContext("admin1", { role: "admin" }).firestore();
      await expect(
        adminDb.collection("users").doc("user1").collection("private").doc("meta").set({
          role: "admin",
        })
      ).rejects.toThrow();
    });

    it("allows teacher to read private/meta of student", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await context.firestore().collection("users").doc("student1").collection("private").doc("meta").set({
          role: "student",
          status: "active",
        });
      });

      const teacherDb = testEnv.authenticatedContext("teacher1", { role: "teacher" }).firestore();
      await expect(
        teacherDb.collection("users").doc("student1").collection("private").doc("meta").get()
      ).resolves.toBeDefined();
    });

    it("denies teacher from reading private/meta of admin", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await context.firestore().collection("users").doc("admin1").collection("private").doc("meta").set({
          role: "admin",
          status: "active",
        });
      });

      const teacherDb = testEnv.authenticatedContext("teacher2", { role: "teacher" }).firestore();
      await expect(
        teacherDb.collection("users").doc("admin1").collection("private").doc("meta").get()
      ).rejects.toThrow();
    });

    it("denies all client-side document deletions", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await context.firestore().collection("users").doc("user1").set({
          uid: "user1",
        });
      });

      const adminDb = testEnv.authenticatedContext("admin1", { role: "admin" }).firestore();
      await expect(adminDb.collection("users").doc("user1").delete()).rejects.toThrow();
    });
  });

  describe("adminLogs & mail collections", () => {
    it("denies client writes to adminLogs for all roles", async () => {
      const adminDb = testEnv.authenticatedContext("admin1", { role: "admin" }).firestore();
      await expect(
        adminDb.collection("adminLogs").add({ action: "TEST", message: "illegal write" })
      ).rejects.toThrow();
    });

    it("allows admins to read adminLogs", async () => {
      const adminDb = testEnv.authenticatedContext("admin1", { role: "admin" }).firestore();
      await expect(adminDb.collection("adminLogs").get()).resolves.toBeDefined();
    });

    it("denies non-admins from reading adminLogs", async () => {
      const studentDb = testEnv.authenticatedContext("student1", { role: "student" }).firestore();
      await expect(studentDb.collection("adminLogs").get()).rejects.toThrow();
    });

    it("denies client access to mail collection", async () => {
      const adminDb = testEnv.authenticatedContext("admin1", { role: "admin" }).firestore();
      await expect(adminDb.collection("mail").get()).rejects.toThrow();
      await expect(adminDb.collection("mail").add({ to: "test@example.com" })).rejects.toThrow();
    });
  });
});
