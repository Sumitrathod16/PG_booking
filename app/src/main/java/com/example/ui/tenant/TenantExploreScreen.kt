package com.example.ui.tenant

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GenderType
import com.example.data.model.PgCatalog
import com.example.data.model.PgFormatters
import com.example.data.model.Property
import com.example.ui.shared.PgCard
import com.example.ui.shared.PgEmptyState
import com.example.ui.shared.PgErrorBanner
import com.example.ui.shared.PropertyCoverImage
import com.example.ui.shared.StatusPill
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.SageGreen
import com.example.ui.theme.SageGreenContainer
import com.example.ui.viewmodel.ExploreFilter
import com.example.ui.viewmodel.PropertySortOption
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TenantExploreScreen(
    propertiesState: UiState<List<Property>>,
    favoriteIds: Set<String>,
    filter: ExploreFilter,
    unreadNotificationCount: Int,
    isAdmin: Boolean,
    onUpdateFilter: ((ExploreFilter) -> ExploreFilter) -> Unit,
    onOpenProperty: (String) -> Unit,
    onToggleFavorite: (Property, Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onSwitchToOwner: () -> Unit,
    onSeedDemoCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            // Header + Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Find your next PG",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Verified stays across Indian cities",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.testTag("notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotificationCount > 0) {
                                Badge { Text(unreadNotificationCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notifications"
                        )
                    }
                }
            }

            // Search + Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { q -> onUpdateFilter { it.copy(query = q) } },
                    placeholder = { Text("Search city, locality or PG...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) {
                            IconButton(onClick = { onUpdateFilter { it.copy(query = "") } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("explore_search_input")
                )

                Surface(
                    onClick = { showFilterSheet = true },
                    shape = MaterialTheme.shapes.medium,
                    color = if (filter.genderFilter != null || filter.instantOnly || filter.sortOption != PropertySortOption.FEATURED)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("explore_filter_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Filters and sort",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // City chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PgCatalog.cities) { city ->
                    FilterChip(
                        selected = filter.selectedCity == city,
                        onClick = { onUpdateFilter { it.copy(selectedCity = city) } },
                        label = { Text(city) },
                        modifier = Modifier.testTag("city_chip_$city")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (propertiesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    PgErrorBanner(message = propertiesState.message)
                }
                is UiState.Success -> {
                    val properties = propertiesState.data
                    if (properties.isEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                PgEmptyState(
                                    title = if (filter.query.isNotBlank() || filter.selectedCity != "All Cities")
                                        "No matching PGs found"
                                    else "No approved PG listings yet",
                                    subtitle = if (isAdmin)
                                        "Publish 3 curated Bengaluru, Pune & Hyderabad PG stays or create one in Owner Mode."
                                    else
                                        "Switch to Owner Mode to list your PG property, or adjust your search filters.",
                                    primaryActionLabel = if (isAdmin) "Publish 3 Starter PGs" else "List a PG (Owner Mode)",
                                    onPrimaryAction = if (isAdmin) onSeedDemoCatalog else onSwitchToOwner,
                                    secondaryActionLabel = if (filter.query.isNotBlank() || filter.selectedCity != "All Cities" || filter.genderFilter != null)
                                        "Reset Filters"
                                    else null,
                                    onSecondaryAction = { onUpdateFilter { ExploreFilter() } }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(properties, key = { it.id }) { property ->
                                val isSaved = favoriteIds.contains(property.id)
                                PropertyDiscoveryCard(
                                    property = property,
                                    isSaved = isSaved,
                                    onClick = { onOpenProperty(property.id) },
                                    onToggleFavorite = { onToggleFavorite(property, isSaved) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filters & Sorting", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { onUpdateFilter { ExploreFilter() } }) {
                        Text("Reset")
                    }
                }

                Text("Gender Category", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter.genderFilter == null,
                        onClick = { onUpdateFilter { it.copy(genderFilter = null) } },
                        label = { Text("Any") }
                    )
                    GenderType.entries.forEach { g ->
                        FilterChip(
                            selected = filter.genderFilter == g,
                            onClick = { onUpdateFilter { it.copy(genderFilter = g) } },
                            label = { Text(g.label) }
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max Monthly Rent", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = PgFormatters.formatInr(filter.maxBudget),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = filter.maxBudget.toFloat(),
                        onValueChange = { v -> onUpdateFilter { it.copy(maxBudget = v.toLong()) } },
                        valueRange = 5000f..40000f,
                        steps = 6
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Instant Booking Only", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Lock bed & pay immediately without waiting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = filter.instantOnly,
                        onCheckedChange = { checked -> onUpdateFilter { it.copy(instantOnly = checked) } }
                    )
                }

                Text("Sort By", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PropertySortOption.entries.forEach { opt ->
                        FilterChip(
                            selected = filter.sortOption == opt,
                            onClick = { onUpdateFilter { it.copy(sortOption = opt) } },
                            label = { Text(opt.label) }
                        )
                    }
                }

                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }
}

@Composable
fun PropertyDiscoveryCard(
    property: Property,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    PgCard(
        onClick = onClick,
        modifier = Modifier.testTag("property_card_${property.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
        ) {
            PropertyCoverImage(
                imageKey = property.imageKeys.firstOrNull() ?: "img_room_single",
                contentDescription = property.name,
                modifier = Modifier.fillMaxSize()
            )

            // Top badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        text = property.genderEnum.label,
                        containerColor = Color(0xFF1E2229),
                        contentColor = Color.White
                    )
                    if (property.instantBookingEnabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Instant", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Surface(
                    onClick = onToggleFavorite,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("favorite_button_${property.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isSaved) "Remove from saved" else "Save property",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = property.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = AmberContainer,
                    contentColor = AmberGold,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (property.reviewCount > 0) "${property.rating}" else "New",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${property.locality}, ${property.city}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = PgFormatters.formatInr(property.monthlyRentStart),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                    )
                }

                StatusPill(
                    text = if (property.availableBeds > 0) "${property.availableBeds} beds open" else "Waitlist",
                    containerColor = if (property.availableBeds > 0) SageGreenContainer else AmberContainer,
                    contentColor = if (property.availableBeds > 0) SageGreen else AmberGold
                )
            }
        }
    }
}
