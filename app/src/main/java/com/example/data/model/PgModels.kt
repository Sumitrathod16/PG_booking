package com.example.data.model

import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class UserRole(val label: String) {
    TENANT("Tenant"),
    OWNER("Owner")
}

enum class GenderType(val label: String) {
    BOYS("Boys"),
    GIRLS("Girls"),
    COLIVING("Co-living")
}

enum class SharingType(val label: String, val defaultBeds: Int) {
    SINGLE("Single", 1),
    TWIN("Twin Sharing", 2),
    TRIPLE("Triple Sharing", 3)
}

enum class BedStatus(val label: String) {
    AVAILABLE("Available"),
    RESERVED("Reserved"),
    OCCUPIED("Occupied"),
    MAINTENANCE("Maintenance")
}

enum class ApprovalStatus(val label: String) {
    PENDING_REVIEW("Pending Review"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    SUSPENDED("Suspended")
}

enum class OwnerVerificationStatus(val label: String) {
    PENDING("Pending Verification"),
    VERIFIED("Verified Owner"),
    REJECTED("Rejected")
}

enum class BookingType(val label: String) {
    INSTANT("Instant Booking"),
    APPROVAL("Owner Approval")
}

enum class BookingStatus(val label: String) {
    PENDING_APPROVAL("Pending Owner Approval"),
    APPROVED_AWAITING_PAYMENT("Approved — Pay to Confirm"),
    AWAITING_PAYMENT("Reserved — Complete Payment"),
    CONFIRMED("Confirmed"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled"),
    EXPIRED("Reservation Expired")
}

enum class SettlementStatus(val label: String) {
    UNSETTLED("Unsettled"),
    REQUESTED("Payout Requested"),
    SETTLED("Settled")
}

enum class PayoutStatus(val label: String) {
    REQUESTED("Requested"),
    PROCESSING("Processing"),
    SETTLED("Settled"),
    REJECTED("Rejected")
}

enum class NotificationType(val label: String) {
    BOOKING("Booking"),
    PAYMENT("Payment"),
    APPROVAL("Approval"),
    SYSTEM("System")
}

inline fun <reified T : Enum<T>> safeEnum(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String? = null,
    val city: String? = null,
    val activeRole: String = UserRole.TENANT.name,
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val roleEnum: UserRole get() = safeEnum(activeRole, UserRole.TENANT)
}

@Keep
@IgnoreExtraProperties
data class FavoriteProperty(
    @DocumentId val id: String = "",
    val userId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val city: String = "",
    val locality: String = "",
    val monthlyRentStart: Long = 0L,
    val coverImageKey: String = "img_room_single",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OwnerProfile(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val businessName: String = "",
    val contactPhone: String = "",
    val city: String = "",
    val upiId: String = "",
    val verificationStatus: String = OwnerVerificationStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: OwnerVerificationStatus
        get() = safeEnum(verificationStatus, OwnerVerificationStatus.PENDING)
}

@Keep
@IgnoreExtraProperties
data class Property(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val name: String = "",
    val city: String = "",
    val locality: String = "",
    val address: String = "",
    val genderType: String = GenderType.COLIVING.name,
    val monthlyRentStart: Long = 0L,
    val securityDeposit: Long = 0L,
    val instantBookingEnabled: Boolean = true,
    val description: String = "",
    val amenities: List<String> = emptyList(),
    val houseRules: List<String> = emptyList(),
    val imageKeys: List<String> = listOf("img_room_single"),
    val rating: Double = 0.0,
    val reviewCount: Long = 0L,
    val totalBeds: Long = 0L,
    val availableBeds: Long = 0L,
    val approvalStatus: String = ApprovalStatus.PENDING_REVIEW.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val genderEnum: GenderType get() = safeEnum(genderType, GenderType.COLIVING)
    val approvalEnum: ApprovalStatus get() = safeEnum(approvalStatus, ApprovalStatus.PENDING_REVIEW)
}

@Keep
@IgnoreExtraProperties
data class Room(
    @DocumentId val id: String = "",
    val propertyId: String = "",
    val ownerId: String = "",
    val roomNumber: String = "",
    val floor: String = "",
    val sharingType: String = SharingType.TWIN.name,
    val monthlyRent: Long = 0L,
    val securityDeposit: Long = 0L,
    val totalBeds: Long = 1L,
    val propertyPublished: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val sharingEnum: SharingType get() = safeEnum(sharingType, SharingType.TWIN)
}

@Keep
@IgnoreExtraProperties
data class Bed(
    @DocumentId val id: String = "",
    val propertyId: String = "",
    val roomId: String = "",
    val ownerId: String = "",
    val bedCode: String = "",
    val roomNumber: String = "",
    val sharingType: String = SharingType.TWIN.name,
    val monthlyRent: Long = 0L,
    val securityDeposit: Long = 0L,
    val status: String = BedStatus.AVAILABLE.name,
    val reservedByUserId: String? = null,
    val reservationExpiresAt: Timestamp? = null,
    val propertyPublished: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val sharingEnum: SharingType get() = safeEnum(sharingType, SharingType.TWIN)
    val statusEnum: BedStatus get() = safeEnum(status, BedStatus.AVAILABLE)

    fun isEffectivelyAvailable(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return when (statusEnum) {
            BedStatus.AVAILABLE -> true
            BedStatus.RESERVED -> {
                val exp = reservationExpiresAt?.toDate()?.time ?: 0L
                exp in 1..nowMillis
            }
            BedStatus.OCCUPIED, BedStatus.MAINTENANCE -> false
        }
    }
}

@Keep
@IgnoreExtraProperties
data class Booking(
    @DocumentId val id: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val city: String = "",
    val locality: String = "",
    val roomId: String = "",
    val roomNumber: String = "",
    val bedId: String = "",
    val bedCode: String = "",
    val tenantId: String = "",
    val tenantName: String = "",
    val ownerId: String = "",
    val bookingType: String = BookingType.INSTANT.name,
    val status: String = BookingStatus.AWAITING_PAYMENT.name,
    val moveInDate: String = "",
    val monthlyRent: Long = 0L,
    val securityDeposit: Long = 0L,
    val totalAmount: Long = 0L,
    val commissionAmount: Long = 0L,
    val reservationExpiresAt: Timestamp? = null,
    val paymentId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val typeEnum: BookingType get() = safeEnum(bookingType, BookingType.INSTANT)
    val statusEnum: BookingStatus get() = safeEnum(status, BookingStatus.PENDING_APPROVAL)
}

@Keep
@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val bookingId: String = "",
    val propertyId: String = "",
    val tenantId: String = "",
    val ownerId: String = "",
    val amount: Long = 0L,
    val method: String = "UPI",
    val razorpayOrderId: String = "",
    val razorpayPaymentId: String = "",
    val verificationStatus: String = "VERIFIED",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommissionRecord(
    @DocumentId val id: String = "",
    val bookingId: String = "",
    val paymentId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val ownerId: String = "",
    val tenantId: String = "",
    val grossAmount: Long = 0L,
    val commissionPercent: Long = 10L,
    val commissionAmount: Long = 0L,
    val ownerNetAmount: Long = 0L,
    val settlementStatus: String = SettlementStatus.UNSETTLED.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: SettlementStatus get() = safeEnum(settlementStatus, SettlementStatus.UNSETTLED)
}

@Keep
@IgnoreExtraProperties
data class PayoutRecord(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val upiId: String = "",
    val amount: Long = 0L,
    val status: String = PayoutStatus.REQUESTED.name,
    val referenceNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: PayoutStatus get() = safeEnum(status, PayoutStatus.REQUESTED)
}

@Keep
@IgnoreExtraProperties
data class Review(
    @DocumentId val id: String = "",
    val propertyId: String = "",
    val userId: String = "",
    val userName: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    val propertyPublished: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = NotificationType.BOOKING.name,
    val read: Boolean = false,
    val relatedId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val typeEnum: NotificationType get() = safeEnum(type, NotificationType.BOOKING)
}

@Keep
@IgnoreExtraProperties
data class PlatformConfig(
    @DocumentId val id: String = "settings",
    val commissionPercent: Long = 10L,
    val reservationMinutes: Long = 15L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object PgCatalog {
    val cities = listOf("All Cities", "Bengaluru", "Pune", "Hyderabad", "Mumbai", "Delhi NCR", "Chennai")

    val allAmenities = listOf(
        "High-Speed Wi-Fi",
        "Home-style Meals",
        "Air Conditioning",
        "Laundry & Ironing",
        "Power Backup",
        "CCTV & Biometric",
        "Housekeeping",
        "Study Desk",
        "Parking",
        "Gym Access",
        "Attached Washroom",
        "RO Drinking Water"
    )

    val defaultHouseRules = listOf(
        "Government ID required on check-in",
        "Quiet hours from 11:00 PM to 6:00 AM",
        "No smoking inside rooms",
        "1 month notice period prior to move-out"
    )

    val presetImageKeys = listOf(
        "img_room_single",
        "img_room_twin",
        "img_common_lounge",
        "img_hero_banner"
    )

    fun drawableForKey(key: String): Int? = when (key) {
        "img_hero_banner" -> R.drawable.img_hero_banner
        "img_room_single" -> R.drawable.img_room_single
        "img_room_twin" -> R.drawable.img_room_twin
        "img_common_lounge" -> R.drawable.img_common_lounge
        else -> null
    }
}

object PgFormatters {
    private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun formatInr(amount: Long): String = inrFormat.format(amount)

    fun formatTimestamp(ts: Timestamp?): String {
        val date = ts?.toDate() ?: Date()
        return dateTimeFormat.format(date)
    }

    fun formatShortDate(ts: Timestamp?): String {
        val date = ts?.toDate() ?: Date()
        return shortDateFormat.format(date)
    }
}
