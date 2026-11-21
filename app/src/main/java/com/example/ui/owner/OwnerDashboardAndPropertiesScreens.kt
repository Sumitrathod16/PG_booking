package com.example.ui.owner

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Bed
import com.example.data.model.BedStatus
import com.example.data.model.Booking
import com.example.data.model.BookingStatus
import com.example.data.model.CommissionRecord
import com.example.data.model.GenderType
import com.example.data.model.OwnerProfile
import com.example.data.model.PgCatalog
import com.example.data.model.PgFormatters
import com.example.data.model.Property
import com.example.data.model.Room
import com.example.data.model.SharingType
import com.example.ui.shared.ApprovalStatusPill
import com.example.ui.shared.OwnerVerificationPill
import com.example.ui.shared.PgCard
import com.example.ui.shared.PgEmptyState
import com.example.ui.shared.PgErrorBanner
import com.example.ui.shared.PropertyCoverImage
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.SageGreen
import com.example.ui.theme.SageGreenContainer
import com.example.ui.viewmodel.UiState

@Composable
fun OwnerDashboardScreen(
    ownerProfileState: UiState<OwnerProfile?>,
    propertiesState: UiState<List<Property>>,
    bookingsState: UiState<List<Booking>>,
    commissionsState: UiState<List<CommissionRecord>>,
    unreadNotifications: Int,
    actionBusy: Boolean,
    onSaveOwnerProfile: (String, String, String, String) -> Unit,
    onProcessBooking: (Booking, Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onGoToProperties: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showKycDialog by remember { mutableStateOf(false) }

    val ownerProfile = (ownerProfileState as? UiState.Success)?.data
    val properties = (propertiesState as? UiState.Success)?.data.orEmpty()
    val bookings = (bookingsState as? UiState.Success)?.data.orEmpty()
    val commissions = (commissionsState as? UiState.Success)?.data.orEmpty()

    val pendingApprovals = bookings.filter { it.statusEnum == BookingStatus.PENDING_APPROVAL }
    val openBeds = properties.sumOf { it.availableBeds }
    val totalBeds = properties.sumOf { it.totalBeds }
    val netEarnings = commissions.sumOf { it.ownerNetAmount }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Owner Dashboard", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = ownerProfile?.businessName ?: "Manage listings, beds & bookings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(badge = { if (unreadNotifications > 0) Badge { Text("$unreadNotifications") } }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                        }
                    }
                }
            }

            // KYC Status Banner
            item {
                PgCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Owner KYC & Payout Setup", style = MaterialTheme.typography.titleMedium)
                            if (ownerProfile != null) {
                                OwnerVerificationPill(ownerProfile.statusEnum)
                            }
                        }
                        if (ownerProfile == null) {
                            Text(
                                "Register your PG operator name, contact phone, and UPI ID to receive payouts and get verified by Admin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showKycDialog = true },
                                modifier = Modifier.testTag("setup_owner_kyc_button")
                            ) {
                                Text("Complete Owner Setup")
                            }
                        } else {
                            Text(
                                "${ownerProfile.businessName} • ${ownerProfile.city} • UPI: ${ownerProfile.upiId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { showKycDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit KYC / UPI Details")
                            }
                        }
                    }
                }
            }

            // KPI Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiBox("Properties", "${properties.size}", Modifier.weight(1f))
                        KpiBox("Open Beds", "$openBeds / $totalBeds", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiBox("Pending Requests", "${pendingApprovals.size}", Modifier.weight(1f))
                        KpiBox("Net Earnings", PgFormatters.formatInr(netEarnings), Modifier.weight(1f))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Incoming Approval Requests", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onGoToProperties) { Text("Manage PGs") }
                }
            }

            if (pendingApprovals.isEmpty()) {
                item {
                    PgCard {
                        Text(
                            text = "No pending booking approval requests right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(pendingApprovals, key = { it.id }) { req ->
                    PgCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${req.tenantName} • ${req.propertyName}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Room ${req.roomNumber} • Bed ${req.bedCode} • Move-in ${req.moveInDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { onProcessBooking(req, false) },
                                    enabled = !actionBusy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reject")
                                }
                                Button(
                                    onClick = { onProcessBooking(req, true) },
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

    if (showKycDialog) {
        OwnerKycDialog(
            existing = ownerProfile,
            onSave = { biz, phone, city, upi ->
                onSaveOwnerProfile(biz, phone, city, upi)
                showKycDialog = false
            },
            onDismiss = { showKycDialog = false }
        )
    }
}

@Composable
private fun KpiBox(label: String, value: String, modifier: Modifier = Modifier) {
    PgCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OwnerKycDialog(
    existing: OwnerProfile?,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var bizName by remember { mutableStateOf(existing?.businessName ?: "") }
    var phone by remember { mutableStateOf(existing?.contactPhone ?: "") }
    var city by remember { mutableStateOf(existing?.city ?: "Bengaluru") }
    var upi by remember { mutableStateOf(existing?.upiId ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Owner KYC & Settlement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = bizName, onValueChange = { bizName = it }, label = { Text("PG / Operator Name") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Primary City") }, singleLine = true)
                OutlinedTextField(value = upi, onValueChange = { upi = it }, label = { Text("Settlement UPI ID (e.g. name@upi)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(bizName, phone, city, upi) },
                enabled = bizName.trim().length >= 2 && phone.trim().length >= 6 && city.trim().length >= 2 && upi.trim().length >= 3
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerPropertiesScreen(
    propertiesState: UiState<List<Property>>,
    ownerName: String,
    actionBusy: Boolean,
    onCreateProperty: (
        ownerName: String,
        name: String,
        city: String,
        locality: String,
        address: String,
        genderType: GenderType,
        rent: Long,
        deposit: Long,
        instant: Boolean,
        description: String,
        amenities: List<String>,
        rules: List<String>,
        images: List<String>
    ) -> Unit,
    onManageInventory: (Property) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_property_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add PG property")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Text(
                    text = "My PG Properties",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )

                when (propertiesState) {
                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is UiState.Error -> PgErrorBanner(propertiesState.message)
                    is UiState.Success -> {
                        if (propertiesState.data.isEmpty()) {
                            PgEmptyState(
                                icon = Icons.Filled.Apartment,
                                title = "No PG listings yet",
                                subtitle = "Tap + to register your first PG property, upload photos, and configure rooms & beds.",
                                primaryActionLabel = "Register a PG",
                                onPrimaryAction = { showCreateSheet = true }
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(propertiesState.data, key = { it.id }) { prop ->
                                    PgCard(onClick = { onManageInventory(prop) }) {
                                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            PropertyCoverImage(
                                                imageKey = prop.imageKeys.firstOrNull() ?: "img_room_single",
                                                contentDescription = prop.name,
                                                modifier = Modifier
                                                    .size(78.dp)
                                                    .clip(MaterialTheme.shapes.small)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                ApprovalStatusPill(prop.approvalEnum)
                                                Text(prop.name, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "${prop.locality}, ${prop.city} • ${prop.availableBeds}/${prop.totalBeds} beds open",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "From ${PgFormatters.formatInr(prop.monthlyRentStart)}/mo",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
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

    if (showCreateSheet) {
        CreatePropertySheet(
            ownerName = ownerName,
            actionBusy = actionBusy,
            onCreate = { n, c, l, a, g, r, d, inst, desc, am, imgs ->
                onCreateProperty(ownerName, n, c, l, a, g, r, d, inst, desc, am, PgCatalog.defaultHouseRules, imgs)
                showCreateSheet = false
            },
            onDismiss = { showCreateSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CreatePropertySheet(
    ownerName: String,
    actionBusy: Boolean,
    onCreate: (String, String, String, String, GenderType, Long, Long, Boolean, String, List<String>, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Bengaluru") }
    var locality by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(GenderType.COLIVING) }
    var rent by remember { mutableStateOf("12500") }
    var deposit by remember { mutableStateOf("15000") }
    var instantBooking by remember { mutableStateOf(true) }
    var description by remember { mutableStateOf("") }
    val selectedAmenities = remember { mutableStateListOf("High-Speed Wi-Fi", "Housekeeping") }
    val selectedImages = remember { mutableStateListOf("img_room_single", "img_common_lounge") }

    // Android Photo Picker (Zero storage permissions required per Play Policy)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        uris.forEach { uri ->
            val str = uri.toString()
            if (selectedImages.size < 8 && !selectedImages.contains(str)) {
                selectedImages.add(0, str)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Register PG Property", style = MaterialTheme.typography.headlineSmall)
            Text("Owner: $ownerName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Property Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = locality, onValueChange = { locality = it }, label = { Text("Locality") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Street Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Gender Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GenderType.entries.forEach { g ->
                    FilterChip(selected = gender == g, onClick = { gender = g }, label = { Text(g.label) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rent,
                    onValueChange = { rent = it.filter(Char::isDigit) },
                    label = { Text("Monthly Rent (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = deposit,
                    onValueChange = { deposit = it.filter(Char::isDigit) },
                    label = { Text("Deposit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Instant Booking", style = MaterialTheme.typography.titleSmall)
                    Text("Allow tenants to lock beds & pay immediately", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = instantBooking, onCheckedChange = { instantBooking = it })
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Property Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Photos (${selectedImages.size}/8)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Photos")
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedImages.toList()) { key ->
                        PropertyCoverImage(
                            imageKey = key,
                            contentDescription = "Property preview",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            Text("Amenities", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PgCatalog.allAmenities.take(8).forEach { amenity ->
                    val chosen = selectedAmenities.contains(amenity)
                    FilterChip(
                        selected = chosen,
                        onClick = {
                            if (chosen) selectedAmenities.remove(amenity)
                            else if (selectedAmenities.size < 15) selectedAmenities.add(amenity)
                        },
                        label = { Text(amenity) }
                    )
                }
            }

            val rentVal = rent.toLongOrNull() ?: 0L
            val depositVal = deposit.toLongOrNull() ?: 0L
            Button(
                onClick = {
                    onCreate(
                        name, city, locality, address, gender,
                        rentVal, depositVal, instantBooking, description,
                        selectedAmenities.toList(), selectedImages.toList()
                    )
                },
                enabled = !actionBusy && name.trim().length >= 2 && locality.trim().length >= 2 &&
                    address.trim().length >= 4 && rentVal >= 1000L && description.trim().length >= 5,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Submit Property for Review")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerInventoryScreen(
    property: Property,
    roomsState: UiState<List<Room>>,
    bedsState: UiState<List<Bed>>,
    actionBusy: Boolean,
    onBack: () -> Unit,
    onAddRoom: (String, String, SharingType, Long, Long) -> Unit,
    onUpdateBedStatus: (Bed, BedStatus) -> Unit
) {
    BackHandler(onBack = onBack)
    var showAddRoomDialog by remember { mutableStateOf(false) }

    val rooms = (roomsState as? UiState.Success)?.data.orEmpty()
    val beds = (bedsState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddRoomDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add room")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(property.name, style = MaterialTheme.typography.titleLarge)
                    Text("Room & Bed Inventory (${property.availableBeds}/${property.totalBeds} free)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ApprovalStatusPill(property.approvalEnum)
            }

            if (rooms.isEmpty()) {
                PgEmptyState(
                    icon = Icons.Filled.MeetingRoom,
                    title = "No rooms configured yet",
                    subtitle = "Add rooms (Single, Twin, or Triple sharing) to auto-generate bookable beds.",
                    primaryActionLabel = "Add First Room",
                    onPrimaryAction = { showAddRoomDialog = true }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rooms, key = { it.id }) { room ->
                        val roomBeds = beds.filter { it.roomId == room.id }
                        PgCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Room ${room.roomNumber} • ${room.sharingEnum.label}", style = MaterialTheme.typography.titleMedium)
                                    Text("${PgFormatters.formatInr(room.monthlyRent)}/mo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    text = "Tap a bed to cycle status (Available ↔ Maintenance ↔ Occupied)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    roomBeds.forEach { bed ->
                                        val (bg, fg) = when (bed.statusEnum) {
                                            BedStatus.AVAILABLE -> SageGreenContainer to SageGreen
                                            BedStatus.RESERVED -> AmberContainer to AmberGold
                                            BedStatus.OCCUPIED, BedStatus.MAINTENANCE -> RoseContainer to RoseAlert
                                        }
                                        val nextStatus = when (bed.statusEnum) {
                                            BedStatus.AVAILABLE -> BedStatus.MAINTENANCE
                                            BedStatus.MAINTENANCE -> BedStatus.OCCUPIED
                                            BedStatus.OCCUPIED, BedStatus.RESERVED -> BedStatus.AVAILABLE
                                        }
                                        Surface(
                                            color = bg,
                                            contentColor = fg,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.clickable(enabled = !actionBusy) {
                                                onUpdateBedStatus(bed, nextStatus)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Bed, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("${bed.bedCode}: ${bed.statusEnum.label}", style = MaterialTheme.typography.labelMedium)
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

    if (showAddRoomDialog) {
        var roomNumber by remember { mutableStateOf("101") }
        var floor by remember { mutableStateOf("1st Floor") }
        var sharing by remember { mutableStateOf(SharingType.TWIN) }
        var rent by remember { mutableStateOf(property.monthlyRentStart.toString()) }
        var deposit by remember { mutableStateOf(property.securityDeposit.toString()) }

        AlertDialog(
            onDismissRequest = { showAddRoomDialog = false },
            title = { Text("Add Room & Beds") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it }, label = { Text("Room Number") }, singleLine = true)
                    OutlinedTextField(value = floor, onValueChange = { floor = it }, label = { Text("Floor") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SharingType.entries.forEach { st ->
                            FilterChip(selected = sharing == st, onClick = { sharing = st }, label = { Text("${st.label} (${st.defaultBeds})") })
                        }
                    }
                    OutlinedTextField(
                        value = rent,
                        onValueChange = { rent = it.filter(Char::isDigit) },
                        label = { Text("Monthly Rent per Bed (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = deposit,
                        onValueChange = { deposit = it.filter(Char::isDigit) },
                        label = { Text("Security Deposit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                val rentVal = rent.toLongOrNull() ?: 0L
                val depVal = deposit.toLongOrNull() ?: 0L
                Button(
                    onClick = {
                        onAddRoom(roomNumber, floor, sharing, rentVal, depVal)
                        showAddRoomDialog = false
                    },
                    enabled = roomNumber.isNotBlank() && rentVal >= 1000L
                ) {
                    Text("Create Room")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRoomDialog = false }) { Text("Cancel") }
            }
        )
    }
}
