package edu.stanford.homeplateengapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import edu.stanford.homeplateengapp.nfc.NfcStatus
import edu.stanford.homeplateengapp.nfc.NfcTagInfo
import edu.stanford.homeplateengapp.nfc.NfcViewModel

@Composable
fun NfcScanScreen(viewModel: NfcViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "NFC Discovery",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        val statusText = when (uiState.nfcStatus) {
            NfcStatus.NOT_AVAILABLE -> "NFC is not available on this device"
            NfcStatus.DISABLED -> "NFC is disabled. Please enable it in Settings."
            NfcStatus.ENABLED -> "NFC enabled. Ready to scan."
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = when (uiState.nfcStatus) {
                NfcStatus.ENABLED -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
        )

        if (uiState.scanCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scans: ${uiState.scanCount}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val tag = uiState.lastScannedTag
        if (tag != null) {
            TagInfoCard(tag)
        } else if (uiState.nfcStatus == NfcStatus.ENABLED) {
            Text(
                text = "Hold an NFC tag near the device to scan it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TagInfoCard(tag: NfcTagInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("Tag Info")
            InfoLine("ID", tag.id)
            InfoLine("Technologies", tag.techList.joinToString(", "))

            tag.nfcAInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("NfcA")
                InfoLine("ATQA", info.atqa)
                InfoLine("SAK", "0x${info.sak.toString(16).padStart(2, '0')}")
                InfoLine("Max transceive", "${info.maxTransceiveLength} bytes")
                InfoLine("Timeout", "${info.timeout} ms")
            }

            tag.nfcBInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("NfcB")
                InfoLine("App data", info.applicationData)
                InfoLine("Protocol info", info.protocolInfo)
                InfoLine("Max transceive", "${info.maxTransceiveLength} bytes")
            }

            tag.nfcFInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("NfcF")
                InfoLine("Manufacturer", info.manufacturer)
                InfoLine("System code", info.systemCode)
                InfoLine("Max transceive", "${info.maxTransceiveLength} bytes")
                InfoLine("Timeout", "${info.timeout} ms")
            }

            tag.nfcVInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("NfcV (ISO 15693)")
                InfoLine("DSF ID", "0x${info.dsfId.toString(16).padStart(2, '0')}")
                InfoLine("Response flags", "0x${info.responseFlags.toString(16).padStart(2, '0')}")
                InfoLine("Max transceive", "${info.maxTransceiveLength} bytes")
            }

            tag.isoDepInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("IsoDep")
                InfoLine("Hi-layer response", info.hiLayerResponse ?: "N/A")
                InfoLine("Historical bytes", info.historicalBytes ?: "N/A")
                InfoLine("Max transceive", "${info.maxTransceiveLength} bytes")
                InfoLine("Timeout", "${info.timeout} ms")
                InfoLine("Extended APDU", "${info.isExtendedLengthApduSupported}")
            }

            tag.ndefInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("NDEF")
                InfoLine("Type", info.type)
                InfoLine("Max size", "${info.maxSize} bytes")
                InfoLine("Writable", "${info.isWritable}")
                InfoLine("Can make read-only", "${info.canMakeReadOnly}")
                InfoLine("Records", "${info.records.size}")
                info.records.forEachIndexed { i, rec ->
                    InfoLine("  Record[$i] TNF", "${rec.tnf}")
                    InfoLine("  Record[$i] type", rec.type)
                    InfoLine("  Record[$i] payload", "${rec.payloadSize} bytes")
                }
            }

            tag.ndefFormatableInfo?.let {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("NdefFormatable")
                InfoLine("Status", "Tag is NDEF formatable")
            }

            tag.mifareClassicInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("MIFARE Classic")
                InfoLine("Type", "${info.type}")
                InfoLine("Size", "${info.size} bytes")
                InfoLine("Sectors", "${info.sectorCount}")
                InfoLine("Blocks", "${info.blockCount}")
            }

            tag.mifareUltralightInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("MIFARE Ultralight")
                InfoLine("Type", "${info.type}")
                InfoLine("Max transceive", "${info.maxTransceiveLength} bytes")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
