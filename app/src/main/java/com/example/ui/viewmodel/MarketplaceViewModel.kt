package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Bed
import com.example.data.model.Booking
import com.example.data.model.FavoriteProperty
import com.example.data.model.NotificationItem
import com.example.data.model.Property
import com.example.data.model.Review
import com.example.data.model.Room
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.OwnerAndAdminRepository
import com.example.data.repository.TenantRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceViewModel(
    private val tenantRepo: TenantRepository,
    private val ownerAdminRepo: OwnerAndAdminRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _filter = MutableStateFlow(ExploreFilter())
    val filter: StateFlow<ExploreFilter> = _filter.asStateFlow()

    private val _selectedPropertyId = MutableStateFlow<String?>(null)

    private val _actionBusy = MutableStateFlow(false)
    val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            tenantRepo.ensureUserProfile()
            _isAdmin.value = ownerAdminRepo.isCurrentUserAdmin()
        }
    }

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        tenantRepo.observeUserProfile(currentUserId)
            .asUiState(TAG, "Failed to load profile")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val rawApprovedProperties = tenantRepo.observeApprovedProperties()
        .asUiState(TAG, "Failed to load PG listings")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val filteredPropertiesState: StateFlow<UiState<List<Property>>> =
        combine(rawApprovedProperties, _filter) { state, f ->
            when (state) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> state
                is UiState.Success -> {
                    val q = f.query.trim().lowercase()
                    val filtered = state.data.filter { prop ->
                        val matchesQuery = q.isEmpty() ||
                            prop.name.lowercase().contains(q) ||
                            prop.city.lowercase().contains(q) ||
                            prop.locality.lowercase().contains(q)
                        val matchesCity = f.selectedCity == "All Cities" ||
                            prop.city.equals(f.selectedCity, ignoreCase = true)
                        val matchesGender = f.genderFilter == null || prop.genderEnum == f.genderFilter
                        val matchesBudget = prop.monthlyRentStart <= f.maxBudget
                        val matchesInstant = !f.instantOnly || prop.instantBookingEnabled
                        matchesQuery && matchesCity && matchesGender && matchesBudget && matchesInstant
                    }
                    val sorted = when (f.sortOption) {
                        PropertySortOption.FEATURED -> filtered
                        PropertySortOption.PRICE_LOW -> filtered.sortedBy { it.monthlyRentStart }
                        PropertySortOption.PRICE_HIGH -> filtered.sortedByDescending { it.monthlyRentStart }
                        PropertySortOption.RATING -> filtered.sortedByDescending { it.rating }
                        PropertySortOption.AVAILABILITY -> filtered.sortedByDescending { it.availableBeds }
                    }
                    UiState.Success(sorted)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val favoritesState: StateFlow<UiState<List<FavoriteProperty>>> =
        tenantRepo.observeFavorites(currentUserId)
            .asUiState(TAG, "Failed to load saved properties")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val tenantBookingsState: StateFlow<UiState<List<Booking>>> =
        tenantRepo.observeTenantBookings(currentUserId)
            .asUiState(TAG, "Failed to load bookings")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> =
        tenantRepo.observeNotifications(currentUserId)
            .asUiState(TAG, "Failed to load notifications")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val selectedPropertyState: StateFlow<UiState<Property?>> =
        _selectedPropertyId.flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(UiState.Success(null))
            else tenantRepo.observeProperty(id).asUiState(TAG, "Failed to load property details")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val selectedPropertyRoomsState: StateFlow<UiState<List<Room>>> =
        _selectedPropertyId.flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(UiState.Success(emptyList()))
            else tenantRepo.observePublishedRooms(id).asUiState(TAG, "Failed to load rooms")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val selectedPropertyBedsState: StateFlow<UiState<List<Bed>>> =
        _selectedPropertyId.flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(UiState.Success(emptyList()))
            else tenantRepo.observePublishedBeds(id).asUiState(TAG, "Failed to load beds")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val selectedPropertyReviewsState: StateFlow<UiState<List<Review>>> =
        _selectedPropertyId.flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(UiState.Success(emptyList()))
            else tenantRepo.observePublishedReviews(id).asUiState(TAG, "Failed to load reviews")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun selectProperty(propertyId: String?) {
        _selectedPropertyId.value = propertyId
    }

    fun updateFilter(transform: (ExploreFilter) -> ExploreFilter) {
        _filter.update(transform)
    }

    fun switchRole(role: UserRole) {
        viewModelScope.launch {
            tenantRepo.switchActiveRole(role)
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to switch mode") }
        }
    }

    fun updateProfile(displayName: String, phone: String?, city: String?) {
        viewModelScope.launch {
            _actionBusy.value = true
            tenantRepo.updateUserProfile(displayName, phone, city)
                .onSuccess { _events.tryEmit("Profile updated") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to update profile") }
            _actionBusy.value = false
        }
    }

    fun toggleFavorite(property: Property, currentlySaved: Boolean) {
        viewModelScope.launch {
            tenantRepo.toggleFavorite(property, currentlySaved)
                .onSuccess { _events.tryEmit(if (currentlySaved) "Removed from Saved" else "Saved ${property.name}") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Could not update Saved list") }
        }
    }

    fun submitBooking(
        property: Property,
        bed: Bed,
        tenantName: String,
        moveInDate: String,
        instantBook: Boolean,
        onBookingCreated: (Booking) -> Unit
    ) {
        viewModelScope.launch {
            _actionBusy.value = true
            tenantRepo.submitBooking(property, bed, tenantName, moveInDate, instantBook)
                .onSuccess { booking ->
                    _events.tryEmit(
                        if (instantBook) "Bed ${bed.bedCode} reserved! Complete payment to confirm."
                        else "Booking request sent to owner!"
                    )
                    onBookingCreated(booking)
                }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Booking failed") }
            _actionBusy.value = false
        }
    }

    fun completePayment(bookingId: String, method: String, onPaid: (String) -> Unit) {
        viewModelScope.launch {
            _actionBusy.value = true
            tenantRepo.completeBookingPayment(bookingId, method)
                .onSuccess { paymentRef ->
                    _events.tryEmit("Payment verified! Booking confirmed.")
                    onPaid(paymentRef)
                }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Payment failed") }
            _actionBusy.value = false
        }
    }

    fun cancelBooking(booking: Booking, expired: Boolean = false) {
        viewModelScope.launch {
            _actionBusy.value = true
            tenantRepo.cancelBooking(booking, expired)
                .onSuccess { _events.tryEmit(if (expired) "Reservation expired & bed released" else "Booking cancelled") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Could not cancel booking") }
            _actionBusy.value = false
        }
    }

    fun submitReview(property: Property, userName: String, rating: Int, comment: String) {
        viewModelScope.launch {
            _actionBusy.value = true
            tenantRepo.submitReview(property, userName, rating, comment)
                .onSuccess { _events.tryEmit("Review published!") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Could not post review") }
            _actionBusy.value = false
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            tenantRepo.markNotificationRead(id)
        }
    }

    private companion object {
        const val TAG = "MarketplaceVM"
    }
}
