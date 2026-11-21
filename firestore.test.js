const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const TENANT_A = "tenant_a_101";
const TENANT_B = "tenant_b_202";
const OWNER_UID = "owner_303";
const ADMIN_UID = "admin_404";
const ADMIN_EMAIL = "sr8705589@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

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

test("Unauthenticated users cannot read or write marketplace collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("properties").where("approvalStatus", "==", "APPROVED").get());
  await assertFails(unauth.collection("bookings").where("tenantId", "==", TENANT_A).get());
  await assertFails(unauth.collection("users").doc(TENANT_A).get());
  await assertFails(
    unauth.collection("users").doc(TENANT_A).set({
      userId: TENANT_A,
      displayName: "Unauth",
      email: "a@b.com",
      activeRole: "TENANT",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("User profile and favorites enforce strict UID isolation and schema rules", async () => {
  const tenantADb = testEnv.authenticatedContext(TENANT_A).firestore();
  const tenantBDb = testEnv.authenticatedContext(TENANT_B).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    tenantADb.collection("users").doc(TENANT_A).set({
      userId: TENANT_A,
      displayName: "Aarav Sharma",
      email: "aarav@example.com",
      activeRole: "TENANT",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update preserving createdAt succeeds
  await assertSucceeds(
    tenantADb.collection("users").doc(TENANT_A).update({
      city: "Bengaluru",
      activeRole: "OWNER",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user profile read/write fails
  await assertFails(tenantBDb.collection("users").doc(TENANT_A).get());
  await assertFails(tenantBDb.collection("users").doc(TENANT_A).update({ displayName: "Hijacked", updatedAt: pastDate() }));

  // Favorites subcollection isolation
  await assertSucceeds(
    tenantADb.collection("users").doc(TENANT_A).collection("favorites").doc("prop_1").set({
      userId: TENANT_A,
      propertyId: "prop_1",
      propertyName: "Terracotta House",
      city: "Bengaluru",
      locality: "Indiranagar",
      monthlyRentStart: 12000,
      coverImageKey: "img_room_single",
      createdAt: now,
    })
  );
  await assertSucceeds(tenantADb.collection("users").doc(TENANT_A).collection("favorites").get());
  await assertFails(tenantBDb.collection("users").doc(TENANT_A).collection("favorites").get());
});

test("Owner profile prevents self-verification and blocks email spoofing", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const spoofAdminDb = testEnv.authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const now = pastDate(5000);

  // Cannot create directly as VERIFIED
  await assertFails(
    ownerDb.collection("ownerProfiles").doc(OWNER_UID).set({
      ownerId: OWNER_UID,
      businessName: "Urban Nest PGs",
      contactPhone: "+919876543210",
      city: "Bengaluru",
      upiId: "urbannest@upi",
      verificationStatus: "VERIFIED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid PENDING create succeeds
  await assertSucceeds(
    ownerDb.collection("ownerProfiles").doc(OWNER_UID).set({
      ownerId: OWNER_UID,
      businessName: "Urban Nest PGs",
      contactPhone: "+919876543210",
      city: "Bengaluru",
      upiId: "urbannest@upi",
      verificationStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unverified email spoof cannot verify owner
  await assertFails(
    spoofAdminDb.collection("ownerProfiles").doc(OWNER_UID).update({
      verificationStatus: "VERIFIED",
      updatedAt: pastDate(1000),
    })
  );

  // Verified admin can verify owner
  await assertSucceeds(
    realAdminDb.collection("ownerProfiles").doc(OWNER_UID).update({
      verificationStatus: "VERIFIED",
      updatedAt: pastDate(1000),
    })
  );
});

test("Property creation, admin approval, filtered discovery, and bed reservation locking", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const tenantADb = testEnv.authenticatedContext(TENANT_A).firestore();
  const tenantBDb = testEnv.authenticatedContext(TENANT_B).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const now = pastDate(5000);

  const propPayload = {
    ownerId: OWNER_UID,
    ownerName: "Urban Nest PGs",
    name: "Indiranagar Courtyard PG",
    city: "Bengaluru",
    locality: "Indiranagar",
    address: "100 Feet Road, HAL 2nd Stage",
    genderType: "COLIVING",
    monthlyRentStart: 13500,
    securityDeposit: 15000,
    instantBookingEnabled: true,
    description: "Sunlit coliving PG with study desks, fiber Wi-Fi, and homemade meals.",
    amenities: ["Wi-Fi", "Meals", "AC", "Laundry"],
    houseRules: ["No smoking indoors", "Quiet hours after 11 PM"],
    imageKeys: ["img_room_single", "img_common_lounge"],
    rating: 4.8,
    reviewCount: 0,
    totalBeds: 1,
    availableBeds: 1,
    approvalStatus: "PENDING_REVIEW",
    createdAt: now,
    updatedAt: now,
  };

  // Owner cannot create property already APPROVED
  await assertFails(
    ownerDb.collection("properties").doc("prop_1").set({ ...propPayload, approvalStatus: "APPROVED" })
  );

  // Owner creates PENDING_REVIEW property
  await assertSucceeds(ownerDb.collection("properties").doc("prop_1").set(propPayload));

  // Owner creates room and bed
  await assertSucceeds(
    ownerDb.collection("properties").doc("prop_1").collection("rooms").doc("room_101").set({
      propertyId: "prop_1",
      ownerId: OWNER_UID,
      roomNumber: "101",
      floor: "1st Floor",
      sharingType: "SINGLE",
      monthlyRent: 13500,
      securityDeposit: 15000,
      totalBeds: 1,
      propertyPublished: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    ownerDb.collection("properties").doc("prop_1").collection("beds").doc("bed_101_a").set({
      propertyId: "prop_1",
      roomId: "room_101",
      ownerId: OWNER_UID,
      bedCode: "B1",
      roomNumber: "101",
      sharingType: "SINGLE",
      monthlyRent: 13500,
      securityDeposit: 15000,
      status: "AVAILABLE",
      propertyPublished: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unfiltered properties list fails for tenant
  await assertFails(tenantADb.collection("properties").get());

  // Admin approves property and publishes room & bed
  await assertSucceeds(
    adminDb.collection("properties").doc("prop_1").update({ approvalStatus: "APPROVED", updatedAt: pastDate(1000) })
  );
  await assertSucceeds(
    adminDb.collection("properties").doc("prop_1").collection("rooms").doc("room_101").update({ propertyPublished: true, updatedAt: pastDate(1000) })
  );
  await assertSucceeds(
    adminDb.collection("properties").doc("prop_1").collection("beds").doc("bed_101_a").update({ propertyPublished: true, updatedAt: pastDate(1000) })
  );

  // Filtered tenant queries succeed
  await assertSucceeds(tenantADb.collection("properties").where("approvalStatus", "==", "APPROVED").get());
  await assertSucceeds(tenantADb.collection("properties").doc("prop_1").collection("rooms").where("propertyPublished", "==", true).get());
  await assertSucceeds(tenantADb.collection("properties").doc("prop_1").collection("beds").where("propertyPublished", "==", true).get());

  // Tenant A reserves bed for instant booking
  const futureExpiry = new Date(Date.now() + 15 * 60 * 1000);
  await assertSucceeds(
    tenantADb.collection("properties").doc("prop_1").collection("beds").doc("bed_101_a").update({
      status: "RESERVED",
      reservedByUserId: TENANT_A,
      reservationExpiresAt: futureExpiry,
      updatedAt: pastDate(500),
    })
  );

  // Tenant B CANNOT steal or reserve the already-reserved bed
  await assertFails(
    tenantBDb.collection("properties").doc("prop_1").collection("beds").doc("bed_101_a").update({
      status: "RESERVED",
      reservedByUserId: TENANT_B,
      reservationExpiresAt: futureExpiry,
      updatedAt: pastDate(200),
    })
  );
});

test("Booking workflows, payment verification, commission records, and terminal locking", async () => {
  const tenantADb = testEnv.authenticatedContext(TENANT_A).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const now = pastDate(5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("properties").doc("prop_1").set({
      ownerId: OWNER_UID,
      ownerName: "Urban Nest PGs",
      name: "Koramangala Studio PG",
      city: "Bengaluru",
      locality: "Koramangala",
      address: "4th Block",
      genderType: "GIRLS",
      monthlyRentStart: 12000,
      securityDeposit: 12000,
      instantBookingEnabled: false,
      description: "Quiet girls PG near tech parks.",
      amenities: ["Wi-Fi"],
      houseRules: ["No visitors after 9 PM"],
      imageKeys: ["img_room_single"],
      rating: 4.5,
      reviewCount: 1,
      totalBeds: 1,
      availableBeds: 1,
      approvalStatus: "APPROVED",
      createdAt: now,
      updatedAt: now,
    });
  });

  // Tenant A submits owner-approval booking request
  await assertSucceeds(
    tenantADb.collection("bookings").doc("book_1").set({
      propertyId: "prop_1",
      propertyName: "Koramangala Studio PG",
      city: "Bengaluru",
      locality: "Koramangala",
      roomId: "room_1",
      roomNumber: "101",
      bedId: "bed_1",
      bedCode: "B1",
      tenantId: TENANT_A,
      tenantName: "Aarav",
      ownerId: OWNER_UID,
      bookingType: "APPROVAL",
      status: "PENDING_APPROVAL",
      moveInDate: "2026-12-01",
      monthlyRent: 12000,
      securityDeposit: 12000,
      totalAmount: 24000,
      commissionAmount: 2400,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Tenant cannot self-approve or directly set CONFIRMED on PENDING_APPROVAL
  await assertFails(
    tenantADb.collection("bookings").doc("book_1").update({
      status: "CONFIRMED",
      paymentId: "pay_1",
      updatedAt: pastDate(1000),
    })
  );

  // Owner approves booking request
  await assertSucceeds(
    ownerDb.collection("bookings").doc("book_1").update({
      status: "APPROVED_AWAITING_PAYMENT",
      reservationExpiresAt: new Date(Date.now() + 15 * 60 * 1000),
      updatedAt: pastDate(1000),
    })
  );

  // Tenant confirms booking with payment + commission batch
  const batch = tenantADb.batch();
  const bookingRef = tenantADb.collection("bookings").doc("book_1");
  const paymentRef = tenantADb.collection("payments").doc("pay_1");
  const commissionRef = tenantADb.collection("commissionRecords").doc("comm_1");

  batch.update(bookingRef, {
    status: "CONFIRMED",
    paymentId: "pay_1",
    updatedAt: pastDate(500),
  });
  batch.set(paymentRef, {
    bookingId: "book_1",
    propertyId: "prop_1",
    tenantId: TENANT_A,
    ownerId: OWNER_UID,
    amount: 24000,
    method: "UPI",
    razorpayOrderId: "order_test_123",
    razorpayPaymentId: "pay_test_456",
    verificationStatus: "VERIFIED",
    createdAt: pastDate(500),
  });
  batch.set(commissionRef, {
    bookingId: "book_1",
    paymentId: "pay_1",
    propertyId: "prop_1",
    propertyName: "Koramangala Studio PG",
    ownerId: OWNER_UID,
    tenantId: TENANT_A,
    grossAmount: 24000,
    commissionPercent: 10,
    commissionAmount: 2400,
    ownerNetAmount: 21600,
    settlementStatus: "UNSETTLED",
    createdAt: pastDate(500),
    updatedAt: pastDate(500),
  });
  await assertSucceeds(batch.commit());

  // Terminal locking check on REJECTED booking
  await assertSucceeds(
    tenantADb.collection("bookings").doc("book_2").set({
      propertyId: "prop_1",
      propertyName: "Koramangala Studio PG",
      city: "Bengaluru",
      locality: "Koramangala",
      roomId: "room_1",
      roomNumber: "101",
      bedId: "bed_2",
      bedCode: "B2",
      tenantId: TENANT_A,
      tenantName: "Aarav",
      ownerId: OWNER_UID,
      bookingType: "APPROVAL",
      status: "PENDING_APPROVAL",
      moveInDate: "2026-12-05",
      monthlyRent: 12000,
      securityDeposit: 12000,
      totalAmount: 24000,
      commissionAmount: 2400,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    ownerDb.collection("bookings").doc("book_2").update({
      status: "REJECTED",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    ownerDb.collection("bookings").doc("book_2").update({
      status: "APPROVED_AWAITING_PAYMENT",
      updatedAt: pastDate(200),
    })
  );
});

test("Admin batched starter seeding and tenant booking cancellation work end-to-end", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const tenantDb = testEnv.authenticatedContext(TENANT_A).firestore();
  const now = pastDate(5000);

  const seedBatch = adminDb.batch();
  const propRef = adminDb.collection("properties").doc("seed_prop_1");
  const roomRef = propRef.collection("rooms").doc("seed_room_1");
  const bedRef = propRef.collection("beds").doc("seed_bed_1");

  seedBatch.set(propRef, {
    ownerId: ADMIN_UID,
    ownerName: "Courtyard Stays",
    name: "Indiranagar Courtyard Living",
    city: "Bengaluru",
    locality: "Indiranagar",
    address: "Main Avenue, Indiranagar, Bengaluru",
    genderType: "COLIVING",
    monthlyRentStart: 14500,
    securityDeposit: 14500,
    instantBookingEnabled: true,
    description: "Thoughtfully designed PG with sunlit rooms and high-speed Wi-Fi.",
    amenities: ["High-Speed Wi-Fi", "Home-style Meals"],
    houseRules: ["Quiet hours after 10:30 PM"],
    imageKeys: ["img_room_single", "img_common_lounge"],
    rating: 4.8,
    reviewCount: 12,
    totalBeds: 1,
    availableBeds: 1,
    approvalStatus: "APPROVED",
    createdAt: now,
    updatedAt: now,
  });
  seedBatch.set(roomRef, {
    propertyId: "seed_prop_1",
    ownerId: ADMIN_UID,
    roomNumber: "101",
    floor: "1st Floor",
    sharingType: "SINGLE",
    monthlyRent: 14500,
    securityDeposit: 14500,
    totalBeds: 1,
    propertyPublished: true,
    createdAt: now,
    updatedAt: now,
  });
  seedBatch.set(bedRef, {
    propertyId: "seed_prop_1",
    roomId: "seed_room_1",
    ownerId: ADMIN_UID,
    bedCode: "B1",
    roomNumber: "101",
    sharingType: "SINGLE",
    monthlyRent: 14500,
    securityDeposit: 14500,
    status: "AVAILABLE",
    propertyPublished: true,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(seedBatch.commit());

  // Tenant creates an instant booking and cancels it, releasing the bed cleanly
  const reserveExpiry = new Date(Date.now() + 15 * 60 * 1000);
  const tenantBedRef = tenantDb.collection("properties").doc("seed_prop_1").collection("beds").doc("seed_bed_1");
  const reserveBatch = tenantDb.batch();
  reserveBatch.update(tenantBedRef, {
    status: "RESERVED",
    reservedByUserId: TENANT_A,
    reservationExpiresAt: reserveExpiry,
    updatedAt: pastDate(1000),
  });
  reserveBatch.set(tenantDb.collection("bookings").doc("book_cancel_1"), {
    propertyId: "seed_prop_1",
    propertyName: "Indiranagar Courtyard Living",
    city: "Bengaluru",
    locality: "Indiranagar",
    roomId: "seed_room_1",
    roomNumber: "101",
    bedId: "seed_bed_1",
    bedCode: "B1",
    tenantId: TENANT_A,
    tenantName: "Aarav",
    ownerId: ADMIN_UID,
    bookingType: "INSTANT",
    status: "AWAITING_PAYMENT",
    moveInDate: "2026-12-10",
    monthlyRent: 14500,
    securityDeposit: 14500,
    totalAmount: 29000,
    commissionAmount: 2900,
    reservationExpiresAt: reserveExpiry,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(reserveBatch.commit());

  const cancelBatch = tenantDb.batch();
  cancelBatch.update(tenantBedRef, {
    status: "AVAILABLE",
    reservedByUserId: null,
    reservationExpiresAt: null,
    updatedAt: pastDate(500),
  });
  cancelBatch.update(tenantDb.collection("bookings").doc("book_cancel_1"), {
    status: "CANCELLED",
    updatedAt: pastDate(500),
  });
  await assertSucceeds(cancelBatch.commit());
});

