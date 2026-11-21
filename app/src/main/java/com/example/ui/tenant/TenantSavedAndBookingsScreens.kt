package com.example.ui.tenant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Booking
import com.example.data.model.BookingStatus
import com.example.data.model.FavoriteProperty
import com.example.data.model.PgFormatters
import com.example.data.model.Property
import com.example.ui.shared.BookingReceiptDialog
import com.example.ui.shared.BookingStatusPill
import com.example.ui.shared.PgCard
import com.example.ui.shared.PgEmptyState
import com.example.ui.shared.PgErrorBanner
import com.example.ui.shared.PropertyCoverImage
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.viewmodel.UiState

@Composable
fun TenantSavedScreen(
    favoritesState: UiState<List<FavoriteProperty>>,
    onOpenProperty: (String) -> Unit,
    onRemoveFavorite: (FavoriteProperty) -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            Text(
                text = "Saved PGs",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            when (favoritesState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> PgErrorBanner(favoritesState.message)
                is UiState.Success -> {
                    if (favoritesState.data.isEmpty()) {
                        PgEmptyState(
                            icon = Icons.Filled.FavoriteBorder,
                            title = "No saved properties yet",
                            subtitle = "Tap the heart icon on any PG listing to compare options later.",
                            primaryActionLabel = "Explore PGs",
                            onPrimaryAction = onExploreClick
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(favoritesState.data, key = { it.id }) { fav ->
                                PgCard(onClick = { onOpenProperty(fav.propertyId) }) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PropertyCoverImage(
                                            imageKey = fav.coverImageKey,
                                            contentDescription = fav.propertyName,
                                            modifier = Modifier
                                                .size(76.dp)
                                                .clip(MaterialTheme.shapes.small)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(fav.propertyName, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${fav.locality}, ${fav.city}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "${PgFormatters.formatInr(fav.monthlyRentStart)}/mo",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        IconButton(onClick = { onRemoveFavorite(fav) }) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove saved")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TenantBookingsScreen(
    bookingsState: UiState<List<Booking>>,
    actionBusy: Boolean,
    onPayNow: (Booking) -> Unit,
    onCancelBooking: (Booking, Boolean) -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf("All") }
    var receiptBooking by remember { mutableStateOf<Booking?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            Text(
                text = "My Bookings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )

            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Pending/Pay", "Confirmed").forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (bookingsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> PgErrorBanner(bookingsState.message)
                is UiState.Success -> {
                    val filtered = bookingsState.data.filter { b ->
                        when (selectedTab) {
                            "Pending/Pay" -> b.statusEnum in listOf(
                                BookingStatus.PENDING_APPROVAL,
                                BookingStatus.AWAITING_PAYMENT,
                                BookingStatus.APPROVED_AWAITING_PAYMENT
                            )
                            "Confirmed" -> b.statusEnum == BookingStatus.CONFIRMED
                            else -> true
                        }
                    }
                    if (filtered.isEmpty()) {
                        PgEmptyState(
                            icon = Icons.Filled.ReceiptLong,
                            title = "No bookings yet",
                            subtitle = "Select a PG room and bed to initiate an instant booking or owner request.",
                            primaryActionLabel = "Browse PGs",
                            onPrimaryAction = onExploreClick
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { booking ->
                                val now = System.currentTimeMillis()
                                val expiryMillis = booking.reservationExpiresAt?.toDate()?.time
                                val isReservationExpired = expiryMillis != null && expiryMillis < now &&
                                    (booking.statusEnum == BookingStatus.AWAITING_PAYMENT || booking.statusEnum == BookingStatus.APPROVED_AWAITING_PAYMENT)

                                PgCard(modifier = Modifier.testTag("booking_card_${booking.id}")) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BookingStatusPill(booking.statusEnum)
                                            Text(
                                                text = booking.typeEnum.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Text(booking.propertyName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "Room ${booking.roomNumber} • Bed ${booking.bedCode} • Move-in ${booking.moveInDate}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Total Payable", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                PgFormatters.formatInr(booking.totalAmount),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (booking.statusEnum == BookingStatus.AWAITING_PAYMENT ||
                                            booking.statusEnum == BookingStatus.APPROVED_AWAITING_PAYMENT
                                        ) {
                                            if (isReservationExpired) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.errorContainer,
                                                    shape = MaterialTheme.shapes.small,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "Reservation window expired. Release this reservation to free the bed.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(10.dp)
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { onCancelBooking(booking, true) },
                                                    enabled = !actionBusy,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Release Expired Hold")
                                                }
                                            } else {
                                                val minsLeft = expiryMillis?.let { ((it - now) / 60_000L).coerceAtLeast(1L) }
                                                if (minsLeft != null) {
                                                    Surface(color = AmberContainer, shape = MaterialTheme.shapes.small) {
                                                        Text(
                                                            text = "Bed locked for ~$minsLeft min(s) — pay before expiry",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AmberGold,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    OutlinedButton(
                                                        onClick = { onCancelBooking(booking, false) },
                                                        enabled = !actionBusy,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Cancel")
                                                    }
                                                    Button(
                                                        onClick = { onPayNow(booking) },
                                                        enabled = !actionBusy,
                                                        modifier = Modifier.weight(1f).testTag("pay_now_${booking.id}")
                                                    ) {
                                                        Text("Pay Now")
                                                    }
                                                }
                                            }
                                        } else if (booking.statusEnum == BookingStatus.PENDING_APPROVAL) {
                                            OutlinedButton(
                                                onClick = { onCancelBooking(booking, false) },
                                                enabled = !actionBusy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Withdraw Request")
                                            }
                                        } else if (booking.statusEnum == BookingStatus.CONFIRMED) {
                                            OutlinedButton(
                                                onClick = { receiptBooking = booking },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("View Verified Receipt")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    receiptBooking?.let { booking ->
        BookingReceiptDialog(booking = booking, onDismiss = { receiptBooking = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutSheet(
    booking: Booking,
    actionBusy: Boolean,
    onConfirmPay: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("UPI") }
    val methods = listOf(
        Triple("UPI", "UPI (GPay / PhonePe / Paytm)", Icons.Filled.QrCode2),
        Triple("Card", "Credit / Debit Card", Icons.Filled.CreditCard),
        Triple("NetBanking", "NetBanking", Icons.Filled.AccountBalance)
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Razorpay Checkout", style = MaterialTheme.typography.headlineSmall)
            }

            Surface(
                color = AmberContainer,
                contentColor = AmberGold,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.checkout_test_notice),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(10.dp)
                )
            }

            PgCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(booking.propertyName, style = MaterialTheme.typography.titleSmall)
                    Text("Room ${booking.roomNumber} • Bed ${booking.bedCode} • Move-in ${booking.moveInDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("First Month Rent", style = MaterialTheme.typography.bodySmall)
                        Text(PgFormatters.formatInr(booking.monthlyRent), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Refundable Security Deposit", style = MaterialTheme.typography.bodySmall)
                        Text(PgFormatters.formatInr(booking.securityDeposit), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Payable", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(PgFormatters.formatInr(booking.totalAmount), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Payment Method", style = MaterialTheme.typography.titleSmall)
            methods.forEach { (id, label, icon) ->
                Surface(
                    onClick = { selectedMethod = id },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selectedMethod == id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedMethod == id, onClick = { selectedMethod = id })
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(
                onClick = { onConfirmPay(selectedMethod) },
                enabled = !actionBusy,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_pay_button")
            ) {
                if (actionBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Pay ${PgFormatters.formatInr(booking.totalAmount)} & Confirm Bed")
                }
            }
        }
    }
}
