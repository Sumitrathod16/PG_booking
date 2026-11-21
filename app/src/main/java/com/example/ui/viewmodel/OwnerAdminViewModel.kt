package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ApprovalStatus
import com.example.data.model.Bed
import com.example.data.model.BedStatus
import com.example.data.model.Booking
import com.example.data.model.CommissionRecord
import com.example.data.model.GenderType
import com.example.data.model.OwnerProfile
import com.example.data.model.OwnerVerificationStatus
import com.example.data.model.Payment
import com.example.data.model.PayoutRecord
import com.example.data.model.PlatformConfig
import com.example.data.model.Property
import com.example.data.model.Room
import com.example.data.model.SharingType
import com.example.data.repository.OwnerAndAdminRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerAdminViewModel(
    private val repo: OwnerAndAdminRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedOwnerPropertyId = MutableStateFlow<String?>(null)
    private val _adminEnabled = MutableStateFlow(false)
    private val _actionBusy = MutableStateFlow(false)
    val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            _adminEnabled.value = repo.isCurrentUserAdmin()
        }
    }

    // --- Owner Flows ---
    val ownerProfileState: StateFlow<UiState<OwnerProfile?>> =
        repo.observeOwnerProfile(currentUserId)
            .asUiState(TAG, "Failed to load owner profile")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ownerPropertiesState: StateFlow<UiState<List<Property>>> =
        repo.observeOwnerProperties(currentUserId)
            .asUiState(TAG, "Failed to load your properties")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ownerRoomsState: StateFlow<UiState<List<Room>>> =
        _selectedOwnerPropertyId.flatMapLatest { propId ->
            if (propId.isNullOrBlank()) flowOf(UiState.Success(emptyList()))
            else repo.observeOwnerRooms(propId, currentUserId).asUiState(TAG, "Failed to load rooms")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ownerBedsState: StateFlow<UiState<List<Bed>>> =
        _selectedOwnerPropertyId.flatMapLatest { propId ->
            if (propId.isNullOrBlank()) flowOf(UiState.Success(emptyList()))
            else repo.observeOwnerBeds(propId, currentUserId).asUiState(TAG, "Failed to load beds")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ownerBookingsState: StateFlow<UiState<List<Booking>>> =
        repo.observeOwnerBookings(currentUserId)
            .asUiState(TAG, "Failed to load owner bookings")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ownerCommissionsState: StateFlow<UiState<List<CommissionRecord>>> =
        repo.observeOwnerCommissions(currentUserId)
            .asUiState(TAG, "Failed to load commission records")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ownerPayoutsState: StateFlow<UiState<List<PayoutRecord>>> =
        repo.observeOwnerPayouts(currentUserId)
            .asUiState(TAG, "Failed to load payout history")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    // --- Admin Flows (Strictly gated behind verified admin status) ---
    val adminOwnersState: StateFlow<UiState<List<OwnerProfile>>> =
        _adminEnabled.flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(UiState.Success(emptyList()))
            else repo.observeAllOwnerProfiles().asUiState(TAG, "Failed to load owner profiles")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminPropertiesState: StateFlow<UiState<List<Property>>> =
        _adminEnabled.flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(UiState.Success(emptyList()))
            else repo.observeAllProperties().asUiState(TAG, "Failed to load properties")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminBookingsState: StateFlow<UiState<List<Booking>>> =
        _adminEnabled.flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(UiState.Success(emptyList()))
            else repo.observeAllBookings().asUiState(TAG, "Failed to load bookings")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminPaymentsState: StateFlow<UiState<List<Payment>>> =
        _adminEnabled.flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(UiState.Success(emptyList()))
            else repo.observeAllPayments().asUiState(TAG, "Failed to load payments")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminPayoutsState: StateFlow<UiState<List<PayoutRecord>>> =
        _adminEnabled.flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(UiState.Success(emptyList()))
            else repo.observeAllPayouts().asUiState(TAG, "Failed to load payouts")
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val platformConfigState: StateFlow<UiState<PlatformConfig>> =
        repo.observePlatformConfig()
            .asUiState(TAG, "Failed to load platform config")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun selectOwnerProperty(propertyId: String?) {
        _selectedOwnerPropertyId.value = propertyId
    }

    fun saveOwnerProfile(businessName: String, contactPhone: String, city: String, upiId: String) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.saveOwnerProfile(businessName, contactPhone, city, upiId)
                .onSuccess { _events.tryEmit("Owner KYC profile submitted!") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to save owner profile") }
            _actionBusy.value = false
        }
    }

    fun createProperty(
        ownerName: String,
        name: String,
        city: String,
        locality: String,
        address: String,
        genderType: GenderType,
        monthlyRentStart: Long,
        securityDeposit: Long,
        instantBookingEnabled: Boolean,
        description: String,
        amenities: List<String>,
        houseRules: List<String>,
        imageKeys: List<String>,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.createProperty(
                ownerName, name, city, locality, address, genderType,
                monthlyRentStart, securityDeposit, instantBookingEnabled,
                description, amenities, houseRules, imageKeys
            )
                .onSuccess { id ->
                    _events.tryEmit("Property created! Add rooms & beds, then await Admin approval.")
                    onCreated(id)
                }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Could not create property") }
            _actionBusy.value = false
        }
    }

    fun addRoomWithBeds(
        property: Property,
        roomNumber: String,
        floor: String,
        sharingType: SharingType,
        monthlyRent: Long,
        securityDeposit: Long
    ) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.addRoomWithBeds(property, roomNumber, floor, sharingType, monthlyRent, securityDeposit)
                .onSuccess { _events.tryEmit("Room $roomNumber (${sharingType.defaultBeds} beds) added!") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to add room") }
            _actionBusy.value = false
        }
    }

    fun updateBedStatus(property: Property, bed: Bed, status: BedStatus) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.updateBedStatus(property, bed, status)
                .onSuccess { _events.tryEmit("Bed ${bed.bedCode} set to ${status.label}") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to update bed") }
            _actionBusy.value = false
        }
    }

    fun processBookingRequest(booking: Booking, approve: Boolean) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.processBookingRequest(booking, approve)
                .onSuccess {
                    _events.tryEmit(if (approve) "Booking approved & bed locked for tenant!" else "Booking rejected")
                }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Action failed") }
            _actionBusy.value = false
        }
    }

    fun requestPayout(ownerProfile: OwnerProfile?, commissions: List<CommissionRecord>) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.requestOwnerPayout(ownerProfile, commissions)
                .onSuccess { _events.tryEmit("Payout request submitted to Admin!") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Payout request failed") }
            _actionBusy.value = false
        }
    }

    fun verifyOwner(ownerId: String, status: OwnerVerificationStatus) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.verifyOwner(ownerId, status)
                .onSuccess { _events.tryEmit("Owner marked ${status.label}") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to update owner") }
            _actionBusy.value = false
        }
    }

    fun updatePropertyApproval(property: Property, status: ApprovalStatus) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.updatePropertyApproval(property, status)
                .onSuccess { _events.tryEmit("${property.name} marked ${status.label}") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to update property status") }
            _actionBusy.value = false
        }
    }

    fun settlePayout(payout: PayoutRecord, referenceNote: String) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.settlePayout(payout, referenceNote)
                .onSuccess { _events.tryEmit("Payout settled!") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Settlement failed") }
            _actionBusy.value = false
        }
    }

    fun updatePlatformConfig(commissionPercent: Long, reservationMinutes: Long) {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.updatePlatformConfig(commissionPercent, reservationMinutes)
                .onSuccess { _events.tryEmit("Platform config updated!") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Config update failed") }
            _actionBusy.value = false
        }
    }

    fun seedStarterMarketplace() {
        viewModelScope.launch {
            _actionBusy.value = true
            repo.seedStarterMarketplace()
                .onSuccess { _events.tryEmit("Published 3 curated PG listings!") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to publish starter PGs") }
            _actionBusy.value = false
        }
    }

    private companion object {
        const val TAG = "OwnerAdminVM"
    }
}
