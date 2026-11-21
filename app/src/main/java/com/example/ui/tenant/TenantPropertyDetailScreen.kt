package com.example.ui.tenant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Bed
import com.example.data.model.BedStatus
import com.example.data.model.PgFormatters
import com.example.data.model.Property
import com.example.data.model.Review
import com.example.data.model.Room
import com.example.ui.shared.PgCard
import com.example.ui.shared.PgErrorBanner
import com.example.ui.shared.PropertyCoverImage
import com.example.ui.shared.StatusPill
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.SageGreen
import com.example.ui.theme.SageGreenContainer
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TenantPropertyDetailScreen(
    propertyState: UiState<Property?>,
    roomsState: UiState<List<Room>>,
    bedsState: UiState<List<Bed>>,
    reviewsState: UiState<List<Review>>,
    isSaved: Boolean,
    actionBusy: Boolean,
    tenantName: String,
    onBack: () -> Unit,
    onToggleFavorite: (Property, Boolean) -> Unit,
    onSubmitBooking: (Property, Bed, String, Boolean) -> Unit,
    onSubmitReview: (Property, Int, String) -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedBed by remember { mutableStateOf<Bed?>(null) }
    val moveInOptions = remember { listOf("01 Dec 2026", "15 Dec 2026", "01 Jan 2027") }
    var moveInDate by remember { mutableStateOf(moveInOptions.first()) }
    var showReviewDialog by remember { mutableStateOf(false) }

    val property = (propertyState as? UiState.Success)?.data
    val rooms = (roomsState as? UiState.Success)?.data.orEmpty()
    val beds = (bedsState as? UiState.Success)?.data.orEmpty()
    val reviews = (reviewsState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (property != null) {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        val bed = selectedBed
                        if (bed == null) {
                            Text(
                                text = "Select an available bed above to continue",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Room ${bed.roomNumber} • Bed ${bed.bedCode}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total due: ${PgFormatters.formatInr(bed.monthlyRent + bed.securityDeposit)} (incl. deposit)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (property.instantBookingEnabled) {
                                    OutlinedButton(
                                        onClick = { onSubmitBooking(property, bed, moveInDate, false) },
                                        enabled = !actionBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("request_approval_button"),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text("Request Approval")
                                    }
                                    Button(
                                        onClick = { onSubmitBooking(property, bed, moveInDate, true) },
                                        enabled = !actionBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("instant_book_button"),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Instant Book")
                                    }
                                } else {
                                    Button(
                                        onClick = { onSubmitBooking(property, bed, moveInDate, false) },
                                        enabled = !actionBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("request_approval_button"),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text("Request Owner Approval")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when (propertyState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding).statusBarsPadding()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    PgErrorBanner(message = propertyState.message)
                }
            }
            is UiState.Success -> {
                if (property == null) {
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding).statusBarsPadding()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        PgErrorBanner(message = "Property not found.")
                    }
                } else {
                    val gallery = property.imageKeys.ifEmpty { listOf("img_room_single") }
                    val pagerState = rememberPagerState(pageCount = { gallery.size })

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Gallery Header
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    PropertyCoverImage(
                                        imageKey = gallery[page],
                                        contentDescription = "${property.name} photo ${page + 1}",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        onClick = onBack,
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.size(44.dp).testTag("detail_back_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }

                                    Surface(
                                        onClick = { onToggleFavorite(property, isSaved) },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                contentDescription = "Save property",
                                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                // Pager indicators
                                if (gallery.size > 1) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        repeat(gallery.size) { i ->
                                            Box(
                                                modifier = Modifier
                                                    .size(if (pagerState.currentPage == i) 9.dp else 7.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (pagerState.currentPage == i) Color.White
                                                        else Color.White.copy(alpha = 0.5f)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Overview
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusPill(
                                        text = property.genderEnum.label,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (property.instantBookingEnabled) {
                                        StatusPill(
                                            text = "Instant Bookable",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    StatusPill(
                                        text = "${property.availableBeds} / ${property.totalBeds} beds free",
                                        containerColor = SageGreenContainer,
                                        contentColor = SageGreen
                                    )
                                }

                                Text(property.name, style = MaterialTheme.typography.headlineMedium)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${property.address}, ${property.locality}, ${property.city}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                PgCard {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Starting Rent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(PgFormatters.formatInr(property.monthlyRentStart) + "/mo", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Security Deposit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(PgFormatters.formatInr(property.securityDeposit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                Text(property.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // Move-in date & Room/Bed Selection Matrix
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Select Move-in Date", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    moveInOptions.forEach { d ->
                                        FilterChip(
                                            selected = moveInDate == d,
                                            onClick = { moveInDate = d },
                                            label = { Text(d) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Rooms & Live Bed Matrix", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Tap any available green bed to lock it for booking.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (rooms.isEmpty()) {
                            item {
                                Text(
                                    text = "No published rooms available yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        } else {
                            items(rooms, key = { it.id }) { room ->
                                val roomBeds = beds.filter { it.roomId == room.id }
                                PgCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Room ${room.roomNumber} • ${room.sharingEnum.label}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${room.floor} • Deposit ${PgFormatters.formatInr(room.securityDeposit)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "${PgFormatters.formatInr(room.monthlyRent)}/mo",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            roomBeds.forEach { bed ->
                                                val avail = bed.isEffectivelyAvailable()
                                                val isSelected = selectedBed?.id == bed.id
                                                val (bg, fg) = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                                                    avail -> SageGreenContainer to SageGreen
                                                    bed.statusEnum == BedStatus.RESERVED -> AmberContainer to AmberGold
                                                    else -> RoseContainer to RoseAlert
                                                }
                                                Surface(
                                                    color = bg,
                                                    contentColor = fg,
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                                                    modifier = Modifier
                                                        .clickable(enabled = avail) { selectedBed = bed }
                                                        .testTag("bed_chip_${bed.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Filled.Bed, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "${bed.bedCode} • ${if (avail) "Free" else bed.statusEnum.label}",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Amenities & House Rules
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Amenities", style = MaterialTheme.typography.titleMedium)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    property.amenities.forEach { a ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = a,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Text("House Rules", style = MaterialTheme.typography.titleMedium)
                                property.houseRules.forEach { rule ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(rule, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Reviews (${reviews.size})", style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = { showReviewDialog = true }) {
                                        Text("Write Review")
                                    }
                                }
                            }
                        }

                        items(reviews, key = { it.id }) { rev ->
                            PgCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(rev.userName, style = MaterialTheme.typography.titleSmall)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Star, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("${rev.rating}/5", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    Text(rev.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReviewDialog && property != null) {
        var rating by remember { mutableIntStateOf(5) }
        var comment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            title = { Text("Rate ${property.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { rating = star }) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "$star stars",
                                    tint = if (star <= rating) AmberGold else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Share your stay experience (as $tenantName)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSubmitReview(property, rating, comment)
                        showReviewDialog = false
                    },
                    enabled = comment.trim().length >= 2
                ) {
                    Text("Post Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDialog = false }) { Text("Cancel") }
            }
        )
    }
}
