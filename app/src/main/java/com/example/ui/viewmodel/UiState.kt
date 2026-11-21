package com.example.ui.viewmodel

import android.util.Log
import com.example.data.model.GenderType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class PropertySortOption(val label: String) {
    FEATURED("Recommended"),
    PRICE_LOW("Rent: Low to High"),
    PRICE_HIGH("Rent: High to Low"),
    RATING("Top Rated"),
    AVAILABILITY("Most Beds Available")
}

data class ExploreFilter(
    val query: String = "",
    val selectedCity: String = "All Cities",
    val genderFilter: GenderType? = null,
    val maxBudget: Long = 35000L,
    val instantOnly: Boolean = false,
    val sortOption: PropertySortOption = PropertySortOption.FEATURED
)

fun <T> Flow<T>.asUiState(tag: String, fallbackMessage: String): Flow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(tag, fallbackMessage, rootError)
            emit(UiState.Error(rootError.localizedMessage ?: fallbackMessage))
        }
