package com.example.data.repository

import com.example.data.model.ApprovalStatus
import com.example.data.model.Bed
import com.example.data.model.BedStatus
import com.example.data.model.Booking
import com.example.data.model.BookingStatus
import com.example.data.model.CommissionRecord
import com.example.data.model.GenderType
import com.example.data.model.NotificationType
import com.example.data.model.OwnerProfile
import com.example.data.model.OwnerVerificationStatus
import com.example.data.model.Payment
import com.example.data.model.PayoutRecord
import com.example.data.model.PayoutStatus
import com.example.data.model.PgCatalog
import com.example.data.model.PlatformConfig
import com.example.data.model.Property
import com.example.data.model.Room
import com.example.data.model.SettlementStatus
import com.example.data.model.SharingType
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class OwnerAndAdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    companion object {
        const val BOOTSTRAP_ADMIN_EMAIL = "sr8705589@gmail.com"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    suspend fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified) {
            return true
        }
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    private fun <T> Flow<T>.logAndRethrow(op: OperationType, path: String, expectedUid: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
                handleFirestoreError(exceptionToLog, op, path)
            }
            throw error
        }

    // --- Owner Profile & KYC ---
    fun observeOwnerProfile(ownerId: String): Flow<OwnerProfile?> = flow {
        val uid = requireUserId()
        check(uid == ownerId)
        emitAll(
            db.collection("ownerProfiles").document(ownerId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(OwnerProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .logAndRethrow(OperationType.GET, "ownerProfiles/$ownerId", uid)
        )
    }

    suspend fun saveOwnerProfile(businessName: String, contactPhone: String, city: String, upiId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "ownerProfiles/$uid"
        return try {
            val cleanBiz = businessName.trim().take(100)
            val cleanPhone = contactPhone.trim().take(20)
            val cleanCity = city.trim().take(60)
            val cleanUpi = upiId.trim().take(80)
            require(cleanBiz.length >= 2) { "Business name must be at least 2 characters" }
            require(cleanPhone.length >= 6) { "Enter a valid contact phone" }
            require(cleanCity.length >= 2) { "City is required" }
            require(cleanUpi.length >= 3) { "UPI ID is required for settlements" }

            val docRef = db.collection("ownerProfiles").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "businessName" to cleanBiz,
                        "contactPhone" to cleanPhone,
                        "city" to cleanCity,
                        "upiId" to cleanUpi,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "ownerId" to uid,
                        "businessName" to cleanBiz,
                        "contactPhone" to cleanPhone,
                        "city" to cleanCity,
                        "upiId" to cleanUpi,
                        "verificationStatus" to OwnerVerificationStatus.PENDING.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Owner Properties, Rooms & Beds ---
    fun observeOwnerProperties(ownerId: String): Flow<List<Property>> = flow {
        val uid = requireUserId()
        check(uid == ownerId)
        emitAll(
            db.collection("properties")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "properties", uid)
        )
    }

    fun observeOwnerRooms(propertyId: String, ownerId: String): Flow<List<Room>> = flow {
        val uid = requireUserId()
        check(uid == ownerId)
        emitAll(
            db.collection("properties").document(propertyId).collection("rooms")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Room::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.roomNumber }
                }
                .logAndRethrow(OperationType.LIST, "properties/$propertyId/rooms", uid)
        )
    }

    fun observeOwnerBeds(propertyId: String, ownerId: String): Flow<List<Bed>> = flow {
        val uid = requireUserId()
        check(uid == ownerId)
        emitAll(
            db.collection("properties").document(propertyId).collection("beds")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Bed::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { "${it.roomNumber}-${it.bedCode}" }
                }
                .logAndRethrow(OperationType.LIST, "properties/$propertyId/beds", uid)
        )
    }

    suspend fun createProperty(
        ownerName: String,
        name: String,
        city: String,
        locality: String,
        address: String,
        genderType: GenderType,
        monthlyRentStart: Long,
        securityDeposit: Long,
        instantBookingEnabled: Boolean,
        description: String,
        amenities: List<String>,
        houseRules: List<String>,
        imageKeys: List<String>
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(name.trim().length >= 2) { "Property name too short" }
            require(city.trim().length >= 2) { "City is required" }
            require(locality.trim().length >= 2) { "Locality is required" }
            require(address.trim().length >= 4) { "Address is too short" }
            require(monthlyRentStart in 1000L..250000L) { "Rent must be between ₹1,000 and ₹2,50,000" }
            require(securityDeposit in 0L..500000L) { "Deposit out of range" }
            require(description.trim().length >= 5) { "Description must be at least 5 characters" }

            val propRef = db.collection("properties").document()
            val images = imageKeys.ifEmpty { listOf("img_room_single", "img_common_lounge") }.take(8)
            val payload = mapOf(
                "ownerId" to uid,
                "ownerName" to ownerName.ifBlank { "PG Owner" }.take(80),
                "name" to name.trim().take(120),
                "city" to city.trim().take(60),
                "locality" to locality.trim().take(80),
                "address" to address.trim().take(240),
                "genderType" to genderType.name,
                "monthlyRentStart" to monthlyRentStart,
                "securityDeposit" to securityDeposit,
                "instantBookingEnabled" to instantBookingEnabled,
                "description" to description.trim().take(1000),
                "amenities" to amenities.take(15),
                "houseRules" to houseRules.ifEmpty { PgCatalog.defaultHouseRules }.take(15),
                "imageKeys" to images,
                "rating" to 0.0,
                "reviewCount" to 0L,
                "totalBeds" to 0L,
                "availableBeds" to 0L,
                "approvalStatus" to ApprovalStatus.PENDING_REVIEW.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            propRef.set(payload).await()
            Result.success(propRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "properties")
            Result.failure(e)
        }
    }

    suspend fun addRoomWithBeds(
        property: Property,
        roomNumber: String,
        floor: String,
        sharingType: SharingType,
        monthlyRent: Long,
        securityDeposit: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "properties/${property.id}/rooms"
        return try {
            require(property.ownerId == uid) { "Only property owner can add rooms" }
            val cleanRoom = roomNumber.trim().take(20)
            val cleanFloor = floor.trim().ifBlank { "1st Floor" }.take(20)
            require(cleanRoom.isNotEmpty()) { "Room number is required" }
            require(monthlyRent in 1000L..250000L) { "Rent must be ₹1,000–₹2,50,000" }

            val propRef = db.collection("properties").document(property.id)
            val roomRef = propRef.collection("rooms").document()
            val bedCount = sharingType.defaultBeds.toLong()
            val published = property.approvalStatus == ApprovalStatus.APPROVED.name

            val batch = db.batch()
            batch.set(
                roomRef,
                mapOf(
                    "propertyId" to property.id,
                    "ownerId" to uid,
                    "roomNumber" to cleanRoom,
                    "floor" to cleanFloor,
                    "sharingType" to sharingType.name,
                    "monthlyRent" to monthlyRent,
                    "securityDeposit" to securityDeposit,
                    "totalBeds" to bedCount,
                    "propertyPublished" to published,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            for (i in 1..sharingType.defaultBeds) {
                val bedRef = propRef.collection("beds").document()
                batch.set(
                    bedRef,
                    mapOf(
                        "propertyId" to property.id,
                        "roomId" to roomRef.id,
                        "ownerId" to uid,
                        "bedCode" to "B$i",
                        "roomNumber" to cleanRoom,
                        "sharingType" to sharingType.name,
                        "monthlyRent" to monthlyRent,
                        "securityDeposit" to securityDeposit,
                        "status" to BedStatus.AVAILABLE.name,
                        "propertyPublished" to published,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val newMinRent = if (property.totalBeds == 0L) monthlyRent else minOf(property.monthlyRentStart, monthlyRent)
            batch.update(
                propRef,
                mapOf(
                    "totalBeds" to (property.totalBeds + bedCount),
                    "availableBeds" to (property.availableBeds + bedCount),
                    "monthlyRentStart" to newMinRent,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBedStatus(property: Property, bed: Bed, newStatus: BedStatus): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "properties/${property.id}/beds/${bed.id}"
        return try {
            val propRef = db.collection("properties").document(property.id)
            val bedRef = propRef.collection("beds").document(bed.id)
            db.runTransaction { tx ->
                val propSnap = tx.get(propRef)
                val bedSnap = tx.get(bedRef)
                val currentBed = bedSnap.toObject(Bed::class.java) ?: throw IllegalStateException("Bed not found")
                if (currentBed.ownerId != uid) throw IllegalStateException("Unauthorized")

                val curAvail = propSnap.getLong("availableBeds") ?: 0L
                val total = propSnap.getLong("totalBeds") ?: 0L
                val wasAvail = currentBed.status == BedStatus.AVAILABLE.name
                val nowAvail = newStatus == BedStatus.AVAILABLE
                val delta = when {
                    !wasAvail && nowAvail -> 1L
                    wasAvail && !nowAvail -> -1L
                    else -> 0L
                }
                val updatedAvail = (curAvail + delta).coerceIn(0L, total)

                tx.update(
                    bedRef,
                    mapOf(
                        "status" to newStatus.name,
                        "reservedByUserId" to null,
                        "reservationExpiresAt" to null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    propRef,
                    mapOf(
                        "availableBeds" to updatedAvail,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Owner Booking Management ---
    fun observeOwnerBookings(ownerId: String): Flow<List<Booking>> = flow {
        val uid = requireUserId()
        check(uid == ownerId)
        emitAll(
            db.collection("bookings")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Booking::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "bookings", uid)
        )
    }

    suspend fun processBookingRequest(booking: Booking, approve: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "bookings/${booking.id}"
        return try {
            val bookingRef = db.collection("bookings").document(booking.id)
            val bedRef = db.collection("properties").document(booking.propertyId).collection("beds").document(booking.bedId)
            val configRef = db.collection("platformConfig").document("settings")
            val notifRef = db.collection("notifications").document()

            db.runTransaction { tx ->
                val bookingSnap = tx.get(bookingRef)
                val bedSnap = tx.get(bedRef)
                val configSnap = tx.get(configRef)
                val currentBooking = bookingSnap.toObject(Booking::class.java) ?: throw IllegalStateException("Booking not found")
                if (currentBooking.ownerId != uid) throw IllegalStateException("Unauthorized")
                if (currentBooking.status != BookingStatus.PENDING_APPROVAL.name) {
                    throw IllegalStateException("Booking is no longer pending approval")
                }

                if (approve) {
                    val bed = bedSnap.toObject(Bed::class.java) ?: throw IllegalStateException("Bed not found")
                    if (!bed.isEffectivelyAvailable()) {
                        throw IllegalStateException("Bed ${bed.bedCode} is no longer available")
                    }
                    val mins = (configSnap.getLong("reservationMinutes") ?: 15L).coerceIn(1L, 120L)
                    val expiresAt = Timestamp(Date(System.currentTimeMillis() + mins * 60_000L))
                    tx.update(
                        bedRef,
                        mapOf(
                            "status" to BedStatus.RESERVED.name,
                            "reservedByUserId" to currentBooking.tenantId,
                            "reservationExpiresAt" to expiresAt,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    tx.update(
                        bookingRef,
                        mapOf(
                            "status" to BookingStatus.APPROVED_AWAITING_PAYMENT.name,
                            "reservationExpiresAt" to expiresAt,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    tx.set(
                        notifRef,
                        mapOf(
                            "userId" to currentBooking.tenantId,
                            "senderId" to uid,
                            "title" to "Booking Request Approved!",
                            "message" to "Your request for ${currentBooking.propertyName} (Bed ${currentBooking.bedCode}) is approved. Pay within $mins mins to confirm.",
                            "type" to NotificationType.APPROVAL.name,
                            "read" to false,
                            "relatedId" to currentBooking.id,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.update(
                        bookingRef,
                        mapOf(
                            "status" to BookingStatus.REJECTED.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    tx.set(
                        notifRef,
                        mapOf(
                            "userId" to currentBooking.tenantId,
                            "senderId" to uid,
                            "title" to "Booking Request Declined",
                            "message" to "Owner declined your request for ${currentBooking.propertyName} (Bed ${currentBooking.bedCode}).",
                            "type" to NotificationType.APPROVAL.name,
                            "read" to false,
                            "relatedId" to currentBooking.id,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Owner Earnings, Commissions & Payouts ---
    fun observeOwnerCommissions(ownerId: String): Flow<List<CommissionRecord>> = flow {
        val uid = requireUserId()
        check(uid == ownerId)
        emitAll(
            db.collection("commissionRecords")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CommissionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "commissionRecords", uid)
        )
    }

    fun observeOwnerPayouts(ownerId: String): Flow<List<PayoutRecord>> = flow {
        val uid = requireUserId()
        check(uid == ownerId)
        emitAll(
            db.collection("payoutRecords")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PayoutRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "payoutRecords", uid)
        )
    }

    suspend fun requestOwnerPayout(
        ownerProfile: OwnerProfile?,
        unsettledRecords: List<CommissionRecord>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val profile = ownerProfile ?: throw IllegalStateException("Complete your Owner KYC & UPI profile first.")
            val eligible = unsettledRecords.filter { it.statusEnum == SettlementStatus.UNSETTLED }
            val totalNet = eligible.sumOf { it.ownerNetAmount }
            require(totalNet > 0L) { "No unsettled earnings available for payout." }

            val batch = db.batch()
            val payoutRef = db.collection("payoutRecords").document()
            batch.set(
                payoutRef,
                mapOf(
                    "ownerId" to uid,
                    "ownerName" to profile.businessName.take(80),
                    "upiId" to profile.upiId.take(80),
                    "amount" to totalNet,
                    "status" to PayoutStatus.REQUESTED.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            eligible.forEach { rec ->
                batch.update(
                    db.collection("commissionRecords").document(rec.id),
                    mapOf(
                        "settlementStatus" to SettlementStatus.REQUESTED.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "payoutRecords")
            Result.failure(e)
        }
    }

    // --- Admin Operations ---
    fun observeAllOwnerProfiles(): Flow<List<OwnerProfile>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("ownerProfiles")
                .snapshots()
                .map { snap ->
                    snap.toObjects(OwnerProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "ownerProfiles", uid)
        )
    }

    fun observeAllProperties(): Flow<List<Property>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("properties")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "properties", uid)
        )
    }

    fun observeAllBookings(): Flow<List<Booking>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("bookings")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Booking::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "bookings", uid)
        )
    }

    fun observeAllPayments(): Flow<List<Payment>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("payments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Payment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "payments", uid)
        )
    }

    fun observeAllPayouts(): Flow<List<PayoutRecord>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("payoutRecords")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PayoutRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "payoutRecords", uid)
        )
    }

    fun observePlatformConfig(): Flow<PlatformConfig> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("platformConfig").document("settings")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(PlatformConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: PlatformConfig()
                    else PlatformConfig()
                }
                .logAndRethrow(OperationType.GET, "platformConfig/settings", uid)
        )
    }

    suspend fun verifyOwner(ownerId: String, status: OwnerVerificationStatus): Result<Unit> {
        return try {
            requireUserId()
            db.collection("ownerProfiles").document(ownerId).update(
                mapOf(
                    "verificationStatus" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "ownerProfiles/$ownerId")
            Result.failure(e)
        }
    }

    suspend fun updatePropertyApproval(property: Property, status: ApprovalStatus): Result<Unit> {
        return try {
            requireUserId()
            val propRef = db.collection("properties").document(property.id)
            val published = status == ApprovalStatus.APPROVED
            val roomsSnap = propRef.collection("rooms").get().await()
            val bedsSnap = propRef.collection("beds").get().await()

            val batch = db.batch()
            batch.update(
                propRef,
                mapOf(
                    "approvalStatus" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            roomsSnap.documents.forEach { doc ->
                batch.update(doc.reference, mapOf("propertyPublished" to published, "updatedAt" to FieldValue.serverTimestamp()))
            }
            bedsSnap.documents.forEach { doc ->
                batch.update(doc.reference, mapOf("propertyPublished" to published, "updatedAt" to FieldValue.serverTimestamp()))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "properties/${property.id}")
            Result.failure(e)
        }
    }

    suspend fun settlePayout(payout: PayoutRecord, referenceNote: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            batch.update(
                db.collection("payoutRecords").document(payout.id),
                mapOf(
                    "status" to PayoutStatus.SETTLED.name,
                    "referenceNote" to referenceNote.ifBlank { "UTR-VERIFIED" }.take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val commSnap = db.collection("commissionRecords")
                .whereEqualTo("ownerId", payout.ownerId)
                .get()
                .await()
            commSnap.documents.forEach { doc ->
                if (doc.getString("settlementStatus") == SettlementStatus.REQUESTED.name) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "settlementStatus" to SettlementStatus.SETTLED.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.set(
                db.collection("notifications").document(),
                mapOf(
                    "userId" to payout.ownerId,
                    "senderId" to uid,
                    "title" to "Payout Settled",
                    "message" to "Settlement of ₹${payout.amount} to ${payout.upiId} completed ($referenceNote).",
                    "type" to NotificationType.PAYMENT.name,
                    "read" to false,
                    "relatedId" to payout.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "payoutRecords/${payout.id}")
            Result.failure(e)
        }
    }

    suspend fun updatePlatformConfig(commissionPercent: Long, reservationMinutes: Long): Result<Unit> {
        return try {
            requireUserId()
            db.collection("platformConfig").document("settings").set(
                mapOf(
                    "commissionPercent" to commissionPercent.coerceIn(0L, 50L),
                    "reservationMinutes" to reservationMinutes.coerceIn(1L, 120L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "platformConfig/settings")
            Result.failure(e)
        }
    }

    suspend fun seedStarterMarketplace(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            val samples = listOf(
                Triple("Indiranagar Courtyard Living", "Bengaluru", "Indiranagar") to Triple(GenderType.COLIVING, 14500L, true),
                Triple("Koregaon Park Terracotta Stay", "Pune", "Koregaon Park") to Triple(GenderType.GIRLS, 11800L, false),
                Triple("Hitech Studio & Pod PG", "Hyderabad", "Madhapur") to Triple(GenderType.BOYS, 10500L, true)
            )
            samples.forEachIndexed { idx, (loc, specs) ->
                val propRef = db.collection("properties").document()
                val (name, city, locality) = loc
                val (gender, rent, instant) = specs
                val deposit = rent
                batch.set(
                    propRef,
                    mapOf(
                        "ownerId" to uid,
                        "ownerName" to "Courtyard Stays",
                        "name" to name,
                        "city" to city,
                        "locality" to locality,
                        "address" to "Main Avenue, $locality, $city",
                        "genderType" to gender.name,
                        "monthlyRentStart" to rent,
                        "securityDeposit" to deposit,
                        "instantBookingEnabled" to instant,
                        "description" to "Thoughtfully designed PG in $locality with sunlit rooms, high-speed fiber Wi-Fi, wholesome meals, and biometric security.",
                        "amenities" to listOf("High-Speed Wi-Fi", "Home-style Meals", "Air Conditioning", "Housekeeping", "CCTV & Biometric"),
                        "houseRules" to PgCatalog.defaultHouseRules,
                        "imageKeys" to listOf(
                            if (idx == 0) "img_room_single" else if (idx == 1) "img_room_twin" else "img_common_lounge",
                            "img_common_lounge",
                            "img_hero_banner"
                        ),
                        "rating" to 4.8,
                        "reviewCount" to 12L,
                        "totalBeds" to 3L,
                        "availableBeds" to 3L,
                        "approvalStatus" to ApprovalStatus.APPROVED.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val singleRoomRef = propRef.collection("rooms").document()
                batch.set(
                    singleRoomRef,
                    mapOf(
                        "propertyId" to propRef.id,
                        "ownerId" to uid,
                        "roomNumber" to "101",
                        "floor" to "1st Floor",
                        "sharingType" to SharingType.SINGLE.name,
                        "monthlyRent" to (rent + 3000L),
                        "securityDeposit" to deposit,
                        "totalBeds" to 1L,
                        "propertyPublished" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    propRef.collection("beds").document(),
                    mapOf(
                        "propertyId" to propRef.id,
                        "roomId" to singleRoomRef.id,
                        "ownerId" to uid,
                        "bedCode" to "B1",
                        "roomNumber" to "101",
                        "sharingType" to SharingType.SINGLE.name,
                        "monthlyRent" to (rent + 3000L),
                        "securityDeposit" to deposit,
                        "status" to BedStatus.AVAILABLE.name,
                        "propertyPublished" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val twinRoomRef = propRef.collection("rooms").document()
                batch.set(
                    twinRoomRef,
                    mapOf(
                        "propertyId" to propRef.id,
                        "ownerId" to uid,
                        "roomNumber" to "102",
                        "floor" to "1st Floor",
                        "sharingType" to SharingType.TWIN.name,
                        "monthlyRent" to rent,
                        "securityDeposit" to deposit,
                        "totalBeds" to 2L,
                        "propertyPublished" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                for (b in 1..2) {
                    batch.set(
                        propRef.collection("beds").document(),
                        mapOf(
                            "propertyId" to propRef.id,
                            "roomId" to twinRoomRef.id,
                            "ownerId" to uid,
                            "bedCode" to "B$b",
                            "roomNumber" to "102",
                            "sharingType" to SharingType.TWIN.name,
                            "monthlyRent" to rent,
                            "securityDeposit" to deposit,
                            "status" to BedStatus.AVAILABLE.name,
                            "propertyPublished" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "properties")
            Result.failure(e)
        }
    }
}
