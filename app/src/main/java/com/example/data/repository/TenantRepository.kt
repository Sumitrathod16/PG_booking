package com.example.data.repository

import com.example.data.model.ApprovalStatus
import com.example.data.model.Bed
import com.example.data.model.BedStatus
import com.example.data.model.Booking
import com.example.data.model.BookingStatus
import com.example.data.model.BookingType
import com.example.data.model.FavoriteProperty
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationType
import com.example.data.model.PlatformConfig
import com.example.data.model.Property
import com.example.data.model.Review
import com.example.data.model.Room
import com.example.data.model.SettlementStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
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
import java.util.UUID

class TenantRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

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

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .logAndRethrow(OperationType.GET, path, uid)
        )
    }

    suspend fun ensureUserProfile(): Result<UserProfile> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val profile = existing.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                Result.success(profile ?: UserProfile(userId = user.uid, displayName = user.displayName ?: "Guest", email = user.email ?: ""))
            } else {
                val displayName = (user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore("@") ?: "PG Guest").take(80)
                val email = (user.email ?: "${user.uid}@pgbooking.app").take(160)
                val payload = mapOf(
                    "userId" to user.uid,
                    "displayName" to displayName,
                    "email" to email,
                    "activeRole" to UserRole.TENANT.name,
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
                Result.success(UserProfile(id = user.uid, userId = user.uid, displayName = displayName, email = email))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(displayName: String, phone: String?, city: String?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Display name is required" }
            val updates = mutableMapOf<String, Any?>(
                "displayName" to cleanName,
                "phone" to phone?.trim()?.takeIf { it.isNotEmpty() }?.take(20),
                "city" to city?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun switchActiveRole(role: UserRole): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "activeRole" to role.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeApprovedProperties(): Flow<List<Property>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("properties")
                .whereEqualTo("approvalStatus", ApprovalStatus.APPROVED.name)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "properties", uid)
        )
    }

    fun observeProperty(propertyId: String): Flow<Property?> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("properties").document(propertyId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(Property::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .logAndRethrow(OperationType.GET, "properties/$propertyId", uid)
        )
    }

    fun observePublishedRooms(propertyId: String): Flow<List<Room>> = flow {
        val uid = requireUserId()
        val path = "properties/$propertyId/rooms"
        emitAll(
            db.collection("properties").document(propertyId).collection("rooms")
                .whereEqualTo("propertyPublished", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Room::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.roomNumber }
                }
                .logAndRethrow(OperationType.LIST, path, uid)
        )
    }

    fun observePublishedBeds(propertyId: String): Flow<List<Bed>> = flow {
        val uid = requireUserId()
        val path = "properties/$propertyId/beds"
        emitAll(
            db.collection("properties").document(propertyId).collection("beds")
                .whereEqualTo("propertyPublished", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Bed::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { "${it.roomNumber}-${it.bedCode}" }
                }
                .logAndRethrow(OperationType.LIST, path, uid)
        )
    }

    fun observePublishedReviews(propertyId: String): Flow<List<Review>> = flow {
        val uid = requireUserId()
        val path = "properties/$propertyId/reviews"
        emitAll(
            db.collection("properties").document(propertyId).collection("reviews")
                .whereEqualTo("propertyPublished", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Review::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, path, uid)
        )
    }

    fun observeFavorites(userId: String): Flow<List<FavoriteProperty>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$userId/favorites"
        emitAll(
            db.collection("users").document(userId).collection("favorites")
                .snapshots()
                .map { snap ->
                    snap.toObjects(FavoriteProperty::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, path, uid)
        )
    }

    suspend fun toggleFavorite(property: Property, currentlySaved: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/favorites/${property.id}"
        return try {
            val favRef = db.collection("users").document(uid).collection("favorites").document(property.id)
            if (currentlySaved) {
                favRef.delete().await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "propertyId" to property.id,
                    "propertyName" to property.name.take(120),
                    "city" to property.city.take(60),
                    "locality" to property.locality.take(80),
                    "monthlyRentStart" to property.monthlyRentStart,
                    "coverImageKey" to (property.imageKeys.firstOrNull() ?: "img_room_single").take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                favRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeTenantBookings(userId: String): Flow<List<Booking>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("bookings")
                .whereEqualTo("tenantId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Booking::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "bookings", uid)
        )
    }

    fun observeNotifications(userId: String): Flow<List<NotificationItem>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("notifications")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "notifications", uid)
        )
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        val path = "notifications/$notificationId"
        return try {
            requireUserId()
            db.collection("notifications").document(notificationId)
                .update(mapOf("read" to true))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitBooking(
        property: Property,
        bed: Bed,
        tenantName: String,
        moveInDate: String,
        instantBook: Boolean
    ): Result<Booking> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val bookingRef = db.collection("bookings").document()
            val bedRef = db.collection("properties").document(property.id).collection("beds").document(bed.id)
            val configRef = db.collection("platformConfig").document("settings")
            val notifRef = db.collection("notifications").document()

            val createdBooking = db.runTransaction { tx ->
                val bedSnap = tx.get(bedRef)
                val configSnap = tx.get(configRef)

                val currentBed = bedSnap.toObject(Bed::class.java)
                    ?: throw IllegalStateException("Selected bed no longer exists.")
                if (!currentBed.isEffectivelyAvailable()) {
                    throw IllegalStateException("Bed ${currentBed.bedCode} is already reserved or occupied.")
                }

                val commissionPercent = (configSnap.getLong("commissionPercent") ?: 10L).coerceIn(0L, 50L)
                val reservationMins = (configSnap.getLong("reservationMinutes") ?: 15L).coerceIn(1L, 120L)
                val totalAmount = currentBed.monthlyRent + currentBed.securityDeposit
                val commissionAmount = (totalAmount * commissionPercent) / 100L
                val expiresAt = if (instantBook) Timestamp(Date(System.currentTimeMillis() + reservationMins * 60_000L)) else null

                if (instantBook) {
                    tx.update(
                        bedRef,
                        mapOf(
                            "status" to BedStatus.RESERVED.name,
                            "reservedByUserId" to uid,
                            "reservationExpiresAt" to expiresAt,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val status = if (instantBook) BookingStatus.AWAITING_PAYMENT.name else BookingStatus.PENDING_APPROVAL.name
                val bookingType = if (instantBook) BookingType.INSTANT.name else BookingType.APPROVAL.name

                val bookingPayload = mapOf(
                    "propertyId" to property.id,
                    "propertyName" to property.name.take(120),
                    "city" to property.city.take(60),
                    "locality" to property.locality.take(80),
                    "roomId" to currentBed.roomId,
                    "roomNumber" to currentBed.roomNumber,
                    "bedId" to currentBed.id,
                    "bedCode" to currentBed.bedCode,
                    "tenantId" to uid,
                    "tenantName" to tenantName.ifBlank { "Tenant" }.take(80),
                    "ownerId" to property.ownerId,
                    "bookingType" to bookingType,
                    "status" to status,
                    "moveInDate" to moveInDate.take(30),
                    "monthlyRent" to currentBed.monthlyRent,
                    "securityDeposit" to currentBed.securityDeposit,
                    "totalAmount" to totalAmount,
                    "commissionAmount" to commissionAmount,
                    "reservationExpiresAt" to expiresAt,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }

                tx.set(bookingRef, bookingPayload)

                val notifPayload = mapOf(
                    "userId" to property.ownerId,
                    "senderId" to uid,
                    "title" to if (instantBook) "New Instant Reservation" else "New Booking Request",
                    "message" to "$tenantName requested Bed ${currentBed.bedCode} (Room ${currentBed.roomNumber}) at ${property.name}.",
                    "type" to NotificationType.BOOKING.name,
                    "read" to false,
                    "relatedId" to bookingRef.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                tx.set(notifRef, notifPayload)

                Booking(
                    id = bookingRef.id,
                    propertyId = property.id,
                    propertyName = property.name,
                    city = property.city,
                    locality = property.locality,
                    roomId = currentBed.roomId,
                    roomNumber = currentBed.roomNumber,
                    bedId = currentBed.id,
                    bedCode = currentBed.bedCode,
                    tenantId = uid,
                    tenantName = tenantName,
                    ownerId = property.ownerId,
                    bookingType = bookingType,
                    status = status,
                    moveInDate = moveInDate,
                    monthlyRent = currentBed.monthlyRent,
                    securityDeposit = currentBed.securityDeposit,
                    totalAmount = totalAmount,
                    commissionAmount = commissionAmount,
                    reservationExpiresAt = expiresAt
                )
            }.await()

            Result.success(createdBooking)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "bookings")
            Result.failure(e)
        }
    }

    suspend fun completeBookingPayment(bookingId: String, paymentMethod: String): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "bookings/$bookingId"
        return try {
            val bookingRef = db.collection("bookings").document(bookingId)
            val paymentRef = db.collection("payments").document()
            val commissionRef = db.collection("commissionRecords").document()
            val tenantNotifRef = db.collection("notifications").document()
            val ownerNotifRef = db.collection("notifications").document()
            val configRef = db.collection("platformConfig").document("settings")

            val orderId = "order_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val rzpPaymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(14)}"

            db.runTransaction { tx ->
                val bookingSnap = tx.get(bookingRef)
                val booking = bookingSnap.toObject(Booking::class.java)
                    ?: throw IllegalStateException("Booking not found.")
                if (booking.tenantId != uid) {
                    throw IllegalStateException("Unauthorized payment attempt.")
                }
                if (booking.status != BookingStatus.AWAITING_PAYMENT.name &&
                    booking.status != BookingStatus.APPROVED_AWAITING_PAYMENT.name
                ) {
                    throw IllegalStateException("Booking is not awaiting payment.")
                }
                val exp = booking.reservationExpiresAt?.toDate()?.time
                if (exp != null && exp < System.currentTimeMillis()) {
                    throw IllegalStateException("Reservation expired. Please book again.")
                }

                val propRef = db.collection("properties").document(booking.propertyId)
                val bedRef = propRef.collection("beds").document(booking.bedId)
                val bedSnap = tx.get(bedRef)
                val propSnap = tx.get(propRef)
                val configSnap = tx.get(configRef)

                val currentBed = bedSnap.toObject(Bed::class.java)
                    ?: throw IllegalStateException("Bed not found.")
                if (currentBed.status != BedStatus.RESERVED.name || currentBed.reservedByUserId != uid) {
                    throw IllegalStateException("Bed lock no longer held by your account.")
                }

                val propAvail = propSnap.getLong("availableBeds") ?: 0L
                val commissionPercent = (configSnap.getLong("commissionPercent") ?: 10L).coerceIn(0L, 50L)
                val commissionAmount = (booking.totalAmount * commissionPercent) / 100L
                val ownerNet = (booking.totalAmount - commissionAmount).coerceAtLeast(0L)

                tx.update(
                    bedRef,
                    mapOf(
                        "status" to BedStatus.OCCUPIED.name,
                        "reservedByUserId" to null,
                        "reservationExpiresAt" to null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (propAvail > 0L) {
                    tx.update(
                        propRef,
                        mapOf(
                            "availableBeds" to (propAvail - 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                tx.update(
                    bookingRef,
                    mapOf(
                        "status" to BookingStatus.CONFIRMED.name,
                        "paymentId" to paymentRef.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    paymentRef,
                    mapOf(
                        "bookingId" to booking.id,
                        "propertyId" to booking.propertyId,
                        "tenantId" to uid,
                        "ownerId" to booking.ownerId,
                        "amount" to booking.totalAmount,
                        "method" to paymentMethod.take(40),
                        "razorpayOrderId" to orderId,
                        "razorpayPaymentId" to rzpPaymentId,
                        "verificationStatus" to "VERIFIED",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    commissionRef,
                    mapOf(
                        "bookingId" to booking.id,
                        "paymentId" to paymentRef.id,
                        "propertyId" to booking.propertyId,
                        "propertyName" to booking.propertyName.take(120),
                        "ownerId" to booking.ownerId,
                        "tenantId" to uid,
                        "grossAmount" to booking.totalAmount,
                        "commissionPercent" to commissionPercent,
                        "commissionAmount" to commissionAmount,
                        "ownerNetAmount" to ownerNet,
                        "settlementStatus" to SettlementStatus.UNSETTLED.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    tenantNotifRef,
                    mapOf(
                        "userId" to uid,
                        "senderId" to uid,
                        "title" to "Booking Confirmed!",
                        "message" to "Bed ${booking.bedCode} at ${booking.propertyName} is confirmed. Receipt: $rzpPaymentId",
                        "type" to NotificationType.PAYMENT.name,
                        "read" to false,
                        "relatedId" to booking.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    ownerNotifRef,
                    mapOf(
                        "userId" to booking.ownerId,
                        "senderId" to uid,
                        "title" to "Payment Received & Bed Booked",
                        "message" to "${booking.tenantName} confirmed Bed ${booking.bedCode} (${booking.propertyName}).",
                        "type" to NotificationType.PAYMENT.name,
                        "read" to false,
                        "relatedId" to booking.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(rzpPaymentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun cancelBooking(booking: Booking, expired: Boolean = false): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "bookings/${booking.id}"
        return try {
            val bookingRef = db.collection("bookings").document(booking.id)
            val bedRef = db.collection("properties").document(booking.propertyId).collection("beds").document(booking.bedId)

            db.runTransaction { tx ->
                val bookingSnap = tx.get(bookingRef)
                val bedSnap = tx.get(bedRef)
                val current = bookingSnap.toObject(Booking::class.java)
                    ?: throw IllegalStateException("Booking not found")
                if (current.tenantId != uid) throw IllegalStateException("Unauthorized")

                val currentBed = bedSnap.toObject(Bed::class.java)
                if (currentBed != null && currentBed.status == BedStatus.RESERVED.name && currentBed.reservedByUserId == uid) {
                    tx.update(
                        bedRef,
                        mapOf(
                            "status" to BedStatus.AVAILABLE.name,
                            "reservedByUserId" to null,
                            "reservationExpiresAt" to null,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                tx.update(
                    bookingRef,
                    mapOf(
                        "status" to if (expired) BookingStatus.EXPIRED.name else BookingStatus.CANCELLED.name,
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

    suspend fun submitReview(property: Property, userName: String, rating: Int, comment: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "properties/${property.id}/reviews"
        return try {
            val cleanComment = comment.trim().take(500)
            require(cleanComment.length >= 2) { "Please write at least a brief comment." }
            val coercedRating = rating.coerceIn(1, 5).toLong()

            val propRef = db.collection("properties").document(property.id)
            val reviewRef = propRef.collection("reviews").document()

            db.runTransaction { tx ->
                val propSnap = tx.get(propRef)
                val curCount = propSnap.getLong("reviewCount") ?: 0L
                val curRating = propSnap.getDouble("rating") ?: 0.0
                val newCount = curCount + 1L
                val newRating = ((curRating * curCount) + coercedRating) / newCount.toDouble()

                tx.set(
                    reviewRef,
                    mapOf(
                        "propertyId" to property.id,
                        "userId" to uid,
                        "userName" to userName.ifBlank { "Resident" }.take(80),
                        "rating" to coercedRating,
                        "comment" to cleanComment,
                        "propertyPublished" to (property.approvalStatus == ApprovalStatus.APPROVED.name),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    propRef,
                    mapOf(
                        "rating" to Math.round(newRating * 10.0) / 10.0,
                        "reviewCount" to newCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
