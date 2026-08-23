package com.noyorin.balanceisland.ui

import android.app.Application
import com.noyorin.balanceisland.R
import com.noyorin.balanceisland.localization.AppLanguagePreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noyorin.balanceisland.data.BalanceRepository
import com.noyorin.balanceisland.data.BalanceSnapshot
import com.noyorin.balanceisland.data.AccountBalanceSettings
import com.noyorin.balanceisland.data.AccountSettingsStore
import com.noyorin.balanceisland.data.CredentialSummary
import com.noyorin.balanceisland.data.Provider
import com.noyorin.balanceisland.data.SecureKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val credentials: List<CredentialSummary> = emptyList(),
    val snapshots: List<BalanceSnapshot> = emptyList(),
    val accountSettings: Map<String, AccountBalanceSettings> = emptyMap(),
    val refreshing: Boolean = false,
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val keys = SecureKeyStore(application)
    private val repository = BalanceRepository(application)
    private val accountSettingsStore = AccountSettingsStore(application)
    private val _uiState = MutableStateFlow(
        MainUiState(
            credentials = keys.summaries(),
            snapshots = repository.cached(),
            accountSettings = settingsFor(keys.summaries())
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun addCredential(provider: Provider, label: String, apiKey: String) {
        if (apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(message = text(R.string.message_key_empty))
            return
        }
        val saveResult = runCatching { keys.addCredential(provider, label, apiKey) }
        if (saveResult.isFailure) {
            _uiState.value = _uiState.value.copy(
                message = saveResult.exceptionOrNull()?.message ?: text(R.string.message_save_failed)
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            credentials = keys.summaries(),
            snapshots = repository.cached(),
            accountSettings = settingsFor(keys.summaries()),
            refreshing = true,
            message = null
        )
        refresh(messageOnSuccess = text(R.string.message_account_saved))
    }

    fun removeCredential(id: String) {
        keys.removeCredential(id)
        repository.removeCached(id)
        accountSettingsStore.remove(id)
        _uiState.value = _uiState.value.copy(
            credentials = keys.summaries(),
            snapshots = repository.cached(),
            accountSettings = settingsFor(keys.summaries()),
            message = text(R.string.message_account_deleted)
        )
    }

    fun saveAccountSettings(settings: AccountBalanceSettings) {
        val result = runCatching { accountSettingsStore.save(settings) }
        _uiState.value = if (result.isSuccess) {
            _uiState.value.copy(
                snapshots = repository.cached(),
                accountSettings = settingsFor(keys.summaries()),
                message = text(R.string.message_settings_saved)
            )
        } else {
            _uiState.value.copy(message = result.exceptionOrNull()?.message ?: text(R.string.message_settings_failed))
        }
    }

    fun refresh(messageOnSuccess: String? = null) {
        if (_uiState.value.refreshing && messageOnSuccess == null) return
        _uiState.value = _uiState.value.copy(refreshing = true, message = null)
        viewModelScope.launch {
            val result = repository.refreshAll()
            _uiState.value = _uiState.value.copy(
                credentials = keys.summaries(),
                snapshots = result,
                accountSettings = settingsFor(keys.summaries()),
                refreshing = false,
                message = messageOnSuccess
            )
        }
    }

    fun loadCached() {
        _uiState.value = _uiState.value.copy(
            credentials = keys.summaries(),
            snapshots = repository.cached(),
            accountSettings = settingsFor(keys.summaries())
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun settingsFor(credentials: List<CredentialSummary>): Map<String, AccountBalanceSettings> =
        credentials.associate { it.id to accountSettingsStore.get(it.id) }

    private fun text(id: Int): String =
        AppLanguagePreferences.wrap(getApplication()).getString(id)
}
