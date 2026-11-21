package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.Booking
import com.example.data.model.Property
import com.example.data.model.UserRole
import com.example.data.repository.OwnerAndAdminRepository
import com.example.data.repository.TenantRepository
import com.example.ui.admin.AdminConsoleScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.owner.OwnerBookingsScreen
import com.example.ui.owner.OwnerDashboardScreen
import com.example.ui.owner.OwnerEarningsScreen
import com.example.ui.owner.OwnerInventoryScreen
import com.example.ui.owner.OwnerPropertiesScreen
import com.example.ui.shared.NotificationsSheet
import com.example.ui.shared.ProfileScreen
import com.example.ui.tenant.CheckoutSheet
import com.example.ui.tenant.TenantBookingsScreen
import com.example.ui.tenant.TenantExploreScreen
import com.example.ui.tenant.TenantPropertyDetailScreen
import com.example.ui.tenant.TenantSavedScreen
import com.example.ui.theme.PgBookingTheme
import com.example.ui.viewmodel.MarketplaceViewModel
import com.example.ui.viewmodel.OwnerAdminViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PgBookingTheme {
                AppContent()
            }
        }
    }
}

private enum class TenantTab(val label: String, val icon: ImageVector) {
    EXPLORE("Explore", Icons.Filled.Search),
    SAVED("Saved", Icons.Filled.Favorite),
    BOOKINGS("Bookings", Icons.Filled.ReceiptLong),
    PROFILE("Profile", Icons.Filled.Person)
}

private enum class OwnerTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard),
    PROPERTIES("Properties", Icons.Filled.Apartment),
    BOOKINGS("Bookings", Icons.Filled.ReceiptLong),
    EARNINGS("Earnings", Icons.Filled.AccountBalanceWallet),
    PROFILE("Profile", Icons.Filled.Person)
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            PgMarketplaceRoot(
                currentUserId = uid,
                onSignOut = {
                    signOut(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}

@Composable
fun PgMarketplaceRoot(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val marketplaceVm: MarketplaceViewModel = viewModel(
        key = "marketplace_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                MarketplaceViewModel(
                    tenantRepo = TenantRepository(db),
                    ownerAdminRepo = OwnerAndAdminRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val ownerAdminVm: OwnerAdminViewModel = viewModel(
        key = "owner_admin_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                OwnerAdminViewModel(
                    repo = OwnerAndAdminRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val profileState by marketplaceVm.userProfileState.collectAsStateWithLifecycle()
    val isAdmin by marketplaceVm.isAdmin.collectAsStateWithLifecycle()
    val filter by marketplaceVm.filter.collectAsStateWithLifecycle()
    val propertiesState by marketplaceVm.filteredPropertiesState.collectAsStateWithLifecycle()
    val favoritesState by marketplaceVm.favoritesState.collectAsStateWithLifecycle()
    val tenantBookingsState by marketplaceVm.tenantBookingsState.collectAsStateWithLifecycle()
    val notificationsState by marketplaceVm.notificationsState.collectAsStateWithLifecycle()
    val tenantBusy by marketplaceVm.actionBusy.collectAsStateWithLifecycle()

    val selectedPropertyState by marketplaceVm.selectedPropertyState.collectAsStateWithLifecycle()
    val selectedRoomsState by marketplaceVm.selectedPropertyRoomsState.collectAsStateWithLifecycle()
    val selectedBedsState by marketplaceVm.selectedPropertyBedsState.collectAsStateWithLifecycle()
    val selectedReviewsState by marketplaceVm.selectedPropertyReviewsState.collectAsStateWithLifecycle()

    val ownerProfileState by ownerAdminVm.ownerProfileState.collectAsStateWithLifecycle()
    val ownerPropertiesState by ownerAdminVm.ownerPropertiesState.collectAsStateWithLifecycle()
    val ownerRoomsState by ownerAdminVm.ownerRoomsState.collectAsStateWithLifecycle()
    val ownerBedsState by ownerAdminVm.ownerBedsState.collectAsStateWithLifecycle()
    val ownerBookingsState by ownerAdminVm.ownerBookingsState.collectAsStateWithLifecycle()
    val ownerCommissionsState by ownerAdminVm.ownerCommissionsState.collectAsStateWithLifecycle()
    val ownerPayoutsState by ownerAdminVm.ownerPayoutsState.collectAsStateWithLifecycle()
    val ownerAdminBusy by ownerAdminVm.actionBusy.collectAsStateWithLifecycle()

    val adminOwnersState by ownerAdminVm.adminOwnersState.collectAsStateWithLifecycle()
    val adminPropertiesState by ownerAdminVm.adminPropertiesState.collectAsStateWithLifecycle()
    val adminBookingsState by ownerAdminVm.adminBookingsState.collectAsStateWithLifecycle()
    val adminPaymentsState by ownerAdminVm.adminPaymentsState.collectAsStateWithLifecycle()
    val adminPayoutsState by ownerAdminVm.adminPayoutsState.collectAsStateWithLifecycle()
    val platformConfigState by ownerAdminVm.platformConfigState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        marketplaceVm.events.collect { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }
    LaunchedEffect(Unit) {
        ownerAdminVm.events.collect { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    val userProfile = (profileState as? UiState.Success)?.data
    val activeRole = userProfile?.roleEnum ?: UserRole.TENANT
    val favoriteIds = ((favoritesState as? UiState.Success)?.data.orEmpty()).map { it.propertyId }.toSet()
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val unreadCount = notifications.count { !it.read }

    var tenantTab by remember { mutableStateOf(TenantTab.EXPLORE) }
    var ownerTab by remember { mutableStateOf(OwnerTab.DASHBOARD) }

    var detailPropertyId by remember { mutableStateOf<String?>(null) }
    var inventoryProperty by remember { mutableStateOf<Property?>(null) }
    var showAdminConsole by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var checkoutBooking by remember { mutableStateOf<Booking?>(null) }

    // Keep live inventory property updated from ownerPropertiesState
    val liveInventoryProp = inventoryProperty?.let { target ->
        ((ownerPropertiesState as? UiState.Success)?.data.orEmpty()).firstOrNull { it.id == target.id } ?: target
    }

    // Full-screen secondary overlays
    if (showAdminConsole && isAdmin) {
        AdminConsoleScreen(
            ownersState = adminOwnersState,
            propertiesState = adminPropertiesState,
            bookingsState = adminBookingsState,
            paymentsState = adminPaymentsState,
            payoutsState = adminPayoutsState,
            configState = platformConfigState,
            actionBusy = ownerAdminBusy,
            onBack = { showAdminConsole = false },
            onVerifyOwner = { oid, st -> ownerAdminVm.verifyOwner(oid, st) },
            onUpdatePropertyApproval = { prop, st -> ownerAdminVm.updatePropertyApproval(prop, st) },
            onSettlePayout = { p, ref -> ownerAdminVm.settlePayout(p, ref) },
            onUpdateConfig = { pct, mins -> ownerAdminVm.updatePlatformConfig(pct, mins) },
            onSeedDemoCatalog = { ownerAdminVm.seedStarterMarketplace() }
        )
        return
    }

    if (detailPropertyId != null) {
        TenantPropertyDetailScreen(
            propertyState = selectedPropertyState,
            roomsState = selectedRoomsState,
            bedsState = selectedBedsState,
            reviewsState = selectedReviewsState,
            isSaved = favoriteIds.contains(detailPropertyId),
            actionBusy = tenantBusy,
            tenantName = userProfile?.displayName ?: "Tenant",
            onBack = {
                detailPropertyId = null
                marketplaceVm.selectProperty(null)
            },
            onToggleFavorite = { prop, saved -> marketplaceVm.toggleFavorite(prop, saved) },
            onSubmitBooking = { prop, bed, moveIn, instant ->
                marketplaceVm.submitBooking(
                    property = prop,
                    bed = bed,
                    tenantName = userProfile?.displayName ?: "Tenant",
                    moveInDate = moveIn,
                    instantBook = instant
                ) { created ->
                    detailPropertyId = null
                    marketplaceVm.selectProperty(null)
                    tenantTab = TenantTab.BOOKINGS
                    if (instant) {
                        checkoutBooking = created
                    }
                }
            },
            onSubmitReview = { prop, rating, comment ->
                marketplaceVm.submitReview(prop, userProfile?.displayName ?: "Resident", rating, comment)
            }
        )
        return
    }

    if (liveInventoryProp != null) {
        OwnerInventoryScreen(
            property = liveInventoryProp,
            roomsState = ownerRoomsState,
            bedsState = ownerBedsState,
            actionBusy = ownerAdminBusy,
            onBack = {
                inventoryProperty = null
                ownerAdminVm.selectOwnerProperty(null)
            },
            onAddRoom = { num, floor, sharing, rent, dep ->
                ownerAdminVm.addRoomWithBeds(liveInventoryProp, num, floor, sharing, rent, dep)
            },
            onUpdateBedStatus = { bed, status ->
                ownerAdminVm.updateBedStatus(liveInventoryProp, bed, status)
            }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        if (activeRole == UserRole.TENANT) {
                            TenantTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tenantTab == tab,
                                    onClick = { tenantTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_tenant_${tab.name}")
                                )
                            }
                        } else {
                            OwnerTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = ownerTab == tab,
                                    onClick = { ownerTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_owner_${tab.name}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        if (activeRole == UserRole.TENANT) {
                            TenantTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = tenantTab == tab,
                                    onClick = { tenantTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        } else {
                            OwnerTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = ownerTab == tab,
                                    onClick = { ownerTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (activeRole == UserRole.TENANT) {
                        when (tenantTab) {
                            TenantTab.EXPLORE -> TenantExploreScreen(
                                propertiesState = propertiesState,
                                favoriteIds = favoriteIds,
                                filter = filter,
                                unreadNotificationCount = unreadCount,
                                isAdmin = isAdmin,
                                onUpdateFilter = marketplaceVm::updateFilter,
                                onOpenProperty = { propId ->
                                    detailPropertyId = propId
                                    marketplaceVm.selectProperty(propId)
                                },
                                onToggleFavorite = marketplaceVm::toggleFavorite,
                                onOpenNotifications = { showNotifications = true },
                                onSwitchToOwner = { marketplaceVm.switchRole(UserRole.OWNER) },
                                onSeedDemoCatalog = { ownerAdminVm.seedStarterMarketplace() }
                            )
                            TenantTab.SAVED -> TenantSavedScreen(
                                favoritesState = favoritesState,
                                onOpenProperty = { propId ->
                                    detailPropertyId = propId
                                    marketplaceVm.selectProperty(propId)
                                },
                                onRemoveFavorite = { fav ->
                                    marketplaceVm.toggleFavorite(Property(id = fav.propertyId, name = fav.propertyName), true)
                                },
                                onExploreClick = { tenantTab = TenantTab.EXPLORE }
                            )
                            TenantTab.BOOKINGS -> TenantBookingsScreen(
                                bookingsState = tenantBookingsState,
                                actionBusy = tenantBusy,
                                onPayNow = { booking -> checkoutBooking = booking },
                                onCancelBooking = { booking, expired -> marketplaceVm.cancelBooking(booking, expired) },
                                onExploreClick = { tenantTab = TenantTab.EXPLORE }
                            )
                            TenantTab.PROFILE -> ProfileScreen(
                                profileState = profileState,
                                isAdmin = isAdmin,
                                unreadNotifications = unreadCount,
                                onSwitchRole = marketplaceVm::switchRole,
                                onUpdateProfile = marketplaceVm::updateProfile,
                                onOpenNotifications = { showNotifications = true },
                                onOpenAdminConsole = { showAdminConsole = true },
                                onSignOut = onSignOut
                            )
                        }
                    } else {
                        when (ownerTab) {
                            OwnerTab.DASHBOARD -> OwnerDashboardScreen(
                                ownerProfileState = ownerProfileState,
                                propertiesState = ownerPropertiesState,
                                bookingsState = ownerBookingsState,
                                commissionsState = ownerCommissionsState,
                                unreadNotifications = unreadCount,
                                actionBusy = ownerAdminBusy,
                                onSaveOwnerProfile = ownerAdminVm::saveOwnerProfile,
                                onProcessBooking = ownerAdminVm::processBookingRequest,
                                onOpenNotifications = { showNotifications = true },
                                onGoToProperties = { ownerTab = OwnerTab.PROPERTIES }
                            )
                            OwnerTab.PROPERTIES -> OwnerPropertiesScreen(
                                propertiesState = ownerPropertiesState,
                                ownerName = userProfile?.displayName ?: "PG Owner",
                                actionBusy = ownerAdminBusy,
                                onCreateProperty = { oName, name, city, loc, addr, gender, rent, dep, inst, desc, am, rules, imgs ->
                                    ownerAdminVm.createProperty(
                                        oName, name, city, loc, addr, gender, rent, dep, inst, desc, am, rules, imgs
                                    ) {}
                                },
                                onManageInventory = { prop ->
                                    inventoryProperty = prop
                                    ownerAdminVm.selectOwnerProperty(prop.id)
                                }
                            )
                            OwnerTab.BOOKINGS -> OwnerBookingsScreen(
                                bookingsState = ownerBookingsState,
                                actionBusy = ownerAdminBusy,
                                onProcessBooking = ownerAdminVm::processBookingRequest
                            )
                            OwnerTab.EARNINGS -> OwnerEarningsScreen(
                                ownerProfileState = ownerProfileState,
                                commissionsState = ownerCommissionsState,
                                payoutsState = ownerPayoutsState,
                                actionBusy = ownerAdminBusy,
                                onRequestPayout = ownerAdminVm::requestPayout
                            )
                            OwnerTab.PROFILE -> ProfileScreen(
                                profileState = profileState,
                                isAdmin = isAdmin,
                                unreadNotifications = unreadCount,
                                onSwitchRole = marketplaceVm::switchRole,
                                onUpdateProfile = marketplaceVm::updateProfile,
                                onOpenNotifications = { showNotifications = true },
                                onOpenAdminConsole = { showAdminConsole = true },
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNotifications) {
        NotificationsSheet(
            notifications = notifications,
            onMarkRead = marketplaceVm::markNotificationRead,
            onDismiss = { showNotifications = false }
        )
    }

    checkoutBooking?.let { booking ->
        CheckoutSheet(
            booking = booking,
            actionBusy = tenantBusy,
            onConfirmPay = { method ->
                marketplaceVm.completePayment(booking.id, method) {
                    checkoutBooking = null
                }
            },
            onDismiss = { checkoutBooking = null }
        )
    }
}
