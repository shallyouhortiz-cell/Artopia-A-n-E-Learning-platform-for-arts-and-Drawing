const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const fs = require("fs");

describe("ArtopiaControl Security Rules", () => {
  let testEnv;

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "artopia-control-test",
      firestore: {
        rules: fs.readFileSync("firestore.rules", "utf8"),
      },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  it("denies unauthenticated read/write to users", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(db.collection("users").doc("user1").get());
  });

  it("allows user to read their own profile", async () => {
    const db = testEnv.authenticatedContext("user1").firestore();
    await assertSucceeds(db.collection("users").doc("user1").get());
  });

  it("prevents user from escalating role to admin", async () => {
    const db = testEnv.authenticatedContext("user1", { role: "student" }).firestore();
    await assertFails(
      db.collection("users").doc("user1").update({ role: "admin" })
    );
  });
});
