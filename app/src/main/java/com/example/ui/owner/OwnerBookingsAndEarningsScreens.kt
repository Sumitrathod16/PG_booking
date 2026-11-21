package com.example.ui.owner

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Booking
import com.example.data.model.BookingStatus
import com.example.data.model.CommissionRecord
import com.example.data.model.OwnerProfile
import com.example.data.model.PayoutRecord
import com.example.data.model.PgFormatters
import com.example.data.model.SettlementStatus
import com.example.ui.shared.BookingStatusPill
import com.example.ui.shared.PayoutStatusPill
import com.example.ui.shared.PgCard
import com.example.ui.shared.PgEmptyState
import com.example.ui.shared.PgErrorBanner
import com.example.ui.shared.StatusPill
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.SageGreen
import com.example.ui.theme.SageGreenContainer
import com.example.ui.viewmodel.UiState

@Composable
fun OwnerBookingsScreen(
    bookingsState: UiState<List<Booking>>,
    actionBusy: Boolean,
    onProcessBooking: (Booking, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf("Pending") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            Text(
                text = "Property Bookings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Pending", "Confirmed", "All").forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when (bookingsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> PgErrorBanner(bookingsState.message)
                is UiState.Success -> {
                    val filtered = bookingsState.data.filter {
                        when (selectedTab) {
                            "Pending" -> it.statusEnum in listOf(
                                BookingStatus.PENDING_APPROVAL,
                                BookingStatus.APPROVED_AWAITING_PAYMENT,
                                BookingStatus.AWAITING_PAYMENT
                            )
                            "Confirmed" -> it.statusEnum == BookingStatus.CONFIRMED
                            else -> true
                        }
                    }
                    if (filtered.isEmpty()) {
                        PgEmptyState(
                            icon = Icons.Filled.ReceiptLong,
                            title = "No bookings in this view",
                            subtitle = "When tenants submit booking requests or instant reservations, manage them here."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { booking ->
                                PgCard {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BookingStatusPill(booking.statusEnum)
                                            Text(booking.typeEnum.label, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text("${booking.tenantName} • ${booking.propertyName}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Room ${booking.roomNumber} • Bed ${booking.bedCode} • Move-in ${booking.moveInDate}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Rent + Deposit", style = MaterialTheme.typography.bodySmall)
                                            Text(PgFormatters.formatInr(booking.totalAmount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                        if (booking.statusEnum == BookingStatus.PENDING_APPROVAL) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                OutlinedButton(
                                                    onClick = { onProcessBooking(booking, false) },
                                                    enabled = !actionBusy,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Reject")
                                                }
                                                Button(
                                                    onClick = { onProcessBooking(booking, true) },
                                                    enabled = !actionBusy,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Approve")
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
    }
}

@Composable
fun OwnerEarningsScreen(
    ownerProfileState: UiState<OwnerProfile?>,
    commissionsState: UiState<List<CommissionRecord>>,
    payoutsState: UiState<List<PayoutRecord>>,
    actionBusy: Boolean,
    onRequestPayout: (OwnerProfile?, List<CommissionRecord>) -> Unit,
    modifier: Modifier = Modifier
) {
    val ownerProfile = (ownerProfileState as? UiState.Success)?.data
    val commissions = (commissionsState as? UiState.Success)?.data.orEmpty()
    val payouts = (payoutsState as? UiState.Success)?.data.orEmpty()

    val gross = commissions.sumOf { it.grossAmount }
    val totalFee = commissions.sumOf { it.commissionAmount }
    val net = commissions.sumOf { it.ownerNetAmount }
    val unsettledNet = commissions.filter { it.statusEnum == SettlementStatus.UNSETTLED }.sumOf { it.ownerNetAmount }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Earnings & Settlements", style = MaterialTheme.typography.headlineMedium)
            }

            item {
                PgCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Financial Summary", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gross Bookings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(PgFormatters.formatInr(gross), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Platform Commission", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("- ${PgFormatters.formatInr(totalFee)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Net Earnings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(PgFormatters.formatInr(net), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available to Settle", style = MaterialTheme.typography.bodySmall)
                            Text(PgFormatters.formatInr(unsettledNet), style = MaterialTheme.typography.titleSmall, color = SageGreen, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { onRequestPayout(ownerProfile, commissions) },
                            enabled = !actionBusy && unsettledNet > 0L && ownerProfile != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (ownerProfile == null) "Complete Owner KYC on Dashboard First"
                                else if (unsettledNet > 0L) "Request Payout (${PgFormatters.formatInr(unsettledNet)})"
                                else "No Unsettled Balance"
                            )
                        }
                    }
                }
            }

            item {
                Text("Commission Ledger (${commissions.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (commissions.isEmpty()) {
                item {
                    PgEmptyState(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = "No commission records yet",
                        subtitle = "Confirmed tenant payments generate itemized commission and net payout records here."
                    )
                }
            } else {
                items(commissions, key = { it.id }) { rec ->
                    PgCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(rec.propertyName, style = MaterialTheme.typography.titleSmall)
                                StatusPill(
                                    text = rec.statusEnum.label,
                                    containerColor = if (rec.statusEnum == SettlementStatus.SETTLED) SageGreenContainer else AmberContainer,
                                    contentColor = if (rec.statusEnum == SettlementStatus.SETTLED) SageGreen else AmberGold
                                )
                            }
                            Text(
                                "Gross ${PgFormatters.formatInr(rec.grossAmount)} • Fee ${rec.commissionPercent}% (${PgFormatters.formatInr(rec.commissionAmount)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Owner Net: ${PgFormatters.formatInr(rec.ownerNetAmount)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Text("Payout Requests (${payouts.size})", style = MaterialTheme.typography.titleMedium)
            }

            items(payouts, key = { it.id }) { payout ->
                PgCard {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(PgFormatters.formatInr(payout.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("To ${payout.upiId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            payout.referenceNote?.let {
                                Text("Ref: $it", style = MaterialTheme.typography.labelSmall, color = SageGreen)
                            }
                        }
                        PayoutStatusPill(payout.statusEnum)
                    }
                }
            }
        }
    }
}
