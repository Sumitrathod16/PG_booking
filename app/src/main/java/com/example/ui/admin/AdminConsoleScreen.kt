package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ApprovalStatus
import com.example.data.model.Booking
import com.example.data.model.OwnerProfile
import com.example.data.model.OwnerVerificationStatus
import com.example.data.model.Payment
import com.example.data.model.PayoutRecord
import com.example.data.model.PayoutStatus
import com.example.data.model.PgFormatters
import com.example.data.model.PlatformConfig
import com.example.data.model.Property
import com.example.ui.shared.ApprovalStatusPill
import com.example.ui.shared.BookingStatusPill
import com.example.ui.shared.OwnerVerificationPill
import com.example.ui.shared.PayoutStatusPill
import com.example.ui.shared.PgCard
import com.example.ui.viewmodel.UiState

@Composable
fun AdminConsoleScreen(
    ownersState: UiState<List<OwnerProfile>>,
    propertiesState: UiState<List<Property>>,
    bookingsState: UiState<List<Booking>>,
    paymentsState: UiState<List<Payment>>,
    payoutsState: UiState<List<PayoutRecord>>,
    configState: UiState<PlatformConfig>,
    actionBusy: Boolean,
    onBack: () -> Unit,
    onVerifyOwner: (String, OwnerVerificationStatus) -> Unit,
    onUpdatePropertyApproval: (Property, ApprovalStatus) -> Unit,
    onSettlePayout: (PayoutRecord, String) -> Unit,
    onUpdateConfig: (Long, Long) -> Unit,
    onSeedDemoCatalog: () -> Unit
) {
    BackHandler(onBack = onBack)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Approvals", "Payouts & Ops")

    val owners = (ownersState as? UiState.Success)?.data.orEmpty()
    val properties = (propertiesState as? UiState.Success)?.data.orEmpty()
    val bookings = (bookingsState as? UiState.Success)?.data.orEmpty()
    val payments = (paymentsState as? UiState.Success)?.data.orEmpty()
    val payouts = (payoutsState as? UiState.Success)?.data.orEmpty()
    val config = (configState as? UiState.Success)?.data ?: PlatformConfig()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Admin Operations Console", style = MaterialTheme.typography.titleLarge)
                }

                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(label) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> AdminOverviewTab(
                        owners = owners,
                        properties = properties,
                        bookings = bookings,
                        payments = payments,
                        config = config,
                        actionBusy = actionBusy,
                        onUpdateConfig = onUpdateConfig,
                        onSeedDemoCatalog = onSeedDemoCatalog
                    )
                    1 -> AdminApprovalsTab(
                        owners = owners,
                        properties = properties,
                        actionBusy = actionBusy,
                        onVerifyOwner = onVerifyOwner,
                        onUpdatePropertyApproval = onUpdatePropertyApproval
                    )
                    2 -> AdminPayoutsAndMonitorTab(
                        payouts = payouts,
                        bookings = bookings,
                        actionBusy = actionBusy,
                        onSettlePayout = onSettlePayout
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminOverviewTab(
    owners: List<OwnerProfile>,
    properties: List<Property>,
    bookings: List<Booking>,
    payments: List<Payment>,
    config: PlatformConfig,
    actionBusy: Boolean,
    onUpdateConfig: (Long, Long) -> Unit,
    onSeedDemoCatalog: () -> Unit
) {
    var commissionPct by remember(config.commissionPercent) { mutableStateOf(config.commissionPercent.toString()) }
    var reserveMins by remember(config.reservationMinutes) { mutableStateOf(config.reservationMinutes.toString()) }

    val gmv = payments.sumOf { it.amount }
    val pendingProps = properties.count { it.approvalEnum == ApprovalStatus.PENDING_REVIEW }
    val pendingOwners = owners.count { it.statusEnum == OwnerVerificationStatus.PENDING }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AdminStatCard("Total GMV", PgFormatters.formatInr(gmv), Modifier.weight(1f))
                AdminStatCard("Total Bookings", "${bookings.size}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AdminStatCard("Pending Owners", "$pendingOwners", Modifier.weight(1f))
                AdminStatCard("Pending Listings", "$pendingProps", Modifier.weight(1f))
            }
        }

        item {
            PgCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Commission & Reservation Config", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = commissionPct,
                            onValueChange = { commissionPct = it.filter(Char::isDigit) },
                            label = { Text("Commission (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reserveMins,
                            onValueChange = { reserveMins = it.filter(Char::isDigit) },
                            label = { Text("Hold Window (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            onUpdateConfig(
                                (commissionPct.toLongOrNull() ?: 10L).coerceIn(0L, 50L),
                                (reserveMins.toLongOrNull() ?: 15L).coerceIn(1L, 120L)
                            )
                        },
                        enabled = !actionBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Platform Rules")
                    }
                }
            }
        }

        item {
            PgCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Marketplace Catalog Bootstrap", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Publish 3 verified starter PG listings across Bengaluru, Pune & Hyderabad with live Single & Twin rooms and beds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onSeedDemoCatalog,
                        enabled = !actionBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Publish 3 Starter PG Listings")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    PgCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminApprovalsTab(
    owners: List<OwnerProfile>,
    properties: List<Property>,
    actionBusy: Boolean,
    onVerifyOwner: (String, OwnerVerificationStatus) -> Unit,
    onUpdatePropertyApproval: (Property, ApprovalStatus) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Owner KYC Verification (${owners.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(owners, key = { it.id }) { owner ->
            PgCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(owner.businessName, style = MaterialTheme.typography.titleSmall)
                        OwnerVerificationPill(owner.statusEnum)
                    }
                    Text("${owner.city} • ${owner.contactPhone} • UPI: ${owner.upiId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onVerifyOwner(owner.ownerId, OwnerVerificationStatus.REJECTED) },
                            enabled = !actionBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Reject") }
                        Button(
                            onClick = { onVerifyOwner(owner.ownerId, OwnerVerificationStatus.VERIFIED) },
                            enabled = !actionBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Verify Owner") }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text("PG Property Listings (${properties.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(properties, key = { it.id }) { prop ->
            PgCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(prop.name, style = MaterialTheme.typography.titleSmall)
                        ApprovalStatusPill(prop.approvalEnum)
                    }
                    Text("${prop.locality}, ${prop.city} • Owner: ${prop.ownerName} • ${prop.totalBeds} beds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onUpdatePropertyApproval(prop, ApprovalStatus.SUSPENDED) },
                            enabled = !actionBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Suspend") }
                        Button(
                            onClick = { onUpdatePropertyApproval(prop, ApprovalStatus.APPROVED) },
                            enabled = !actionBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Approve") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPayoutsAndMonitorTab(
    payouts: List<PayoutRecord>,
    bookings: List<Booking>,
    actionBusy: Boolean,
    onSettlePayout: (PayoutRecord, String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Owner Payout Requests (${payouts.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(payouts, key = { it.id }) { payout ->
            PgCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${payout.ownerName} • ${PgFormatters.formatInr(payout.amount)}", style = MaterialTheme.typography.titleSmall)
                        PayoutStatusPill(payout.statusEnum)
                    }
                    Text("UPI: ${payout.upiId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (payout.statusEnum == PayoutStatus.REQUESTED || payout.statusEnum == PayoutStatus.PROCESSING) {
                        Button(
                            onClick = { onSettlePayout(payout, "UTR-${payout.id.take(6).uppercase()}") },
                            enabled = !actionBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Mark Settled & Notify Owner")
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text("All Marketplace Bookings (${bookings.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(bookings.take(30), key = { it.id }) { b ->
            PgCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${b.propertyName} (${b.bedCode})", style = MaterialTheme.typography.titleSmall)
                        BookingStatusPill(b.statusEnum)
                    }
                    Text("Tenant: ${b.tenantName} • ${PgFormatters.formatInr(b.totalAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
