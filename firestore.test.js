const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_tft_001";
const BOB_UID = "bob_tft_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write favorites, tracked players, or preferences", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("favorites").doc("live_elderwood_ahri").set({
      userId: ALICE_UID,
      compId: "live_elderwood_ahri",
      environment: "LIVE",
      title: "Elderwood Arcanists",
      tier: "S",
      playstyle: "Fast 8",
      carryName: "Ahri",
      coreUnits: ["Ahri", "Lillia", "Ornn"],
      hexPositions: ["Ahri@3,6@Blue Buff,Jeweled Gauntlet"],
      pinnedToOverlay: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("preferences").doc("settings").get());
});

test("Owner can create, list, partially update, and delete favorites with exact Kotlin payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const compRef = aliceDb.collection("users").doc(ALICE_UID).collection("favorites").doc("live_elderwood_ahri");

  await assertSucceeds(
    compRef.set({
      userId: ALICE_UID,
      compId: "live_elderwood_ahri",
      environment: "LIVE",
      title: "Elderwood Arcanists",
      tier: "S",
      playstyle: "Fast 8",
      carryName: "Ahri",
      coreUnits: ["Ahri", "Lillia", "Ornn", "Syndra"],
      hexPositions: ["Ahri@3,6@Blue Buff,Jeweled Gauntlet"],
      pinnedToOverlay: true,
      notes: "Prioritize Mana items on stage 2 carousel",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("favorites").get());

  await assertSucceeds(
    compRef.update({
      pinnedToOverlay: false,
      notes: "Flex into PBE variant if uncontested",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    compRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / @DocumentId id field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").doc("pbe_sprykin_reroll").set({
      id: "pbe_sprykin_reroll",
      userId: ALICE_UID,
      compId: "pbe_sprykin_reroll",
      environment: "PBE",
      title: "Sprykin Reroll",
      tier: "S",
      playstyle: "Reroll Lv 6",
      carryName: "Tristana",
      coreUnits: ["Tristana", "Teemo"],
      hexPositions: ["Tristana@3,0@Guinsoo,Infinity Edge"],
      pinnedToOverlay: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(compRef.delete());
});

test("Cross-user isolation: Alice cannot read, list, update, or delete Bob's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const bobDb = ctx.firestore();
    const created = new Date(Date.now() - 10000);
    await bobDb.collection("users").doc(BOB_UID).collection("favorites").doc("pbe_blossom_comp").set({
      userId: BOB_UID,
      compId: "pbe_blossom_comp",
      environment: "PBE",
      title: "Blossom Vanguard",
      tier: "A",
      playstyle: "Fast 8",
      carryName: "Jhin",
      coreUnits: ["Jhin", "Shen"],
      hexPositions: ["Jhin@3,6@Last Whisper"],
      pinnedToOverlay: true,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("favorites").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("favorites").doc("pbe_blossom_comp").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("favorites").doc("pbe_blossom_comp").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("favorites").doc("pbe_blossom_comp").delete());
});

test("Owner can manage tracked players and preferences with strict schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  const playerRef = aliceDb.collection("users").doc(ALICE_UID).collection("tracked_players").doc("na1_k3soju");
  await assertSucceeds(
    playerRef.set({
      userId: ALICE_UID,
      puuid: "sample_puuid_001",
      gameName: "k3soju",
      tagLine: "NA1",
      region: "AMERICAS",
      platform: "NA1",
      lastTier: "CHALLENGER I",
      lastLp: 1420,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    playerRef.update({
      lastLp: 1465,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const prefsRef = aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("settings");
  await assertSucceeds(
    prefsRef.set({
      userId: ALICE_UID,
      themeMode: "DARK",
      selectedEnvironment: "LIVE",
      patchAlertsEnabled: true,
      pbeAlertsEnabled: true,
      balanceAlertsEnabled: true,
      trendingAlertsEnabled: true,
      lastSeenLivePatch: "18.6",
      lastSeenPbePatch: "18.7-PBE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    prefsRef.update({
      selectedEnvironment: "PBE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    prefsRef.update({
      selectedEnvironment: "LIVE",
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});
