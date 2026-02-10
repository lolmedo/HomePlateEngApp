package edu.stanford.homeplateengapp.nfc

data class NfcTagInfo(
    val id: String,
    val techList: List<String>,
    val nfcAInfo: NfcAInfo? = null,
    val nfcBInfo: NfcBInfo? = null,
    val nfcFInfo: NfcFInfo? = null,
    val nfcVInfo: NfcVInfo? = null,
    val isoDepInfo: IsoDepInfo? = null,
    val ndefInfo: NdefInfo? = null,
    val ndefFormatableInfo: NdefFormatableInfo? = null,
    val mifareClassicInfo: MifareClassicInfo? = null,
    val mifareUltralightInfo: MifareUltralightInfo? = null,
)

data class NfcAInfo(
    val atqa: String,
    val sak: Short,
    val maxTransceiveLength: Int,
    val timeout: Int,
)

data class NfcBInfo(
    val applicationData: String,
    val protocolInfo: String,
    val maxTransceiveLength: Int,
)

data class NfcFInfo(
    val manufacturer: String,
    val systemCode: String,
    val maxTransceiveLength: Int,
    val timeout: Int,
)

data class NfcVInfo(
    val dsfId: Int,
    val responseFlags: Int,
    val maxTransceiveLength: Int,
)

data class IsoDepInfo(
    val hiLayerResponse: String?,
    val historicalBytes: String?,
    val maxTransceiveLength: Int,
    val timeout: Int,
    val isExtendedLengthApduSupported: Boolean,
)

data class NdefInfo(
    val type: String,
    val maxSize: Int,
    val isWritable: Boolean,
    val canMakeReadOnly: Boolean,
    val records: List<NdefRecordInfo>,
)

data class NdefRecordInfo(
    val tnf: Short,
    val type: String,
    val id: String,
    val payload: String,
    val payloadSize: Int,
)

data class NdefFormatableInfo(
    val isDetected: Boolean = true,
)

data class MifareClassicInfo(
    val type: Int,
    val size: Int,
    val sectorCount: Int,
    val blockCount: Int,
    val maxTransceiveLength: Int,
    val timeout: Int,
)

data class MifareUltralightInfo(
    val type: Int,
    val maxTransceiveLength: Int,
    val timeout: Int,
)
