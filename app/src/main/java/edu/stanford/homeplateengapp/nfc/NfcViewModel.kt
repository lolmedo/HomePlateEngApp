package edu.stanford.homeplateengapp.nfc

import android.nfc.NfcAdapter
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class NfcStatus {
    NOT_AVAILABLE,
    DISABLED,
    ENABLED,
}

data class NfcUiState(
    val nfcStatus: NfcStatus = NfcStatus.NOT_AVAILABLE,
    val lastScannedTag: NfcTagInfo? = null,
    val scanCount: Int = 0,
)

class NfcViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NfcUiState())
    val uiState: StateFlow<NfcUiState> = _uiState.asStateFlow()

    fun updateNfcStatus(adapter: NfcAdapter?) {
        val status = when {
            adapter == null -> NfcStatus.NOT_AVAILABLE
            !adapter.isEnabled -> NfcStatus.DISABLED
            else -> NfcStatus.ENABLED
        }
        _uiState.update { it.copy(nfcStatus = status) }
    }

    fun onTagScanned(tagInfo: NfcTagInfo) {
        _uiState.update {
            it.copy(
                lastScannedTag = tagInfo,
                scanCount = it.scanCount + 1,
            )
        }
    }
}
