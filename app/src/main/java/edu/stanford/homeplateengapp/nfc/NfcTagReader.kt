package edu.stanford.homeplateengapp.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.util.Log

object NfcTagReader {

    private const val TAG = "NFC_DISCOVERY"

    fun readTag(tag: Tag): NfcTagInfo {
        val id = tag.id.toHexString()
        val techList = tag.techList.map { it.substringAfterLast('.') }

        Log.d(TAG, "========== TAG DISCOVERED ==========")
        Log.d(TAG, "Tag ID (hex): $id")
        Log.d(TAG, "Tech list: $techList")

        val nfcAInfo = if ("android.nfc.tech.NfcA" in tag.techList) readNfcA(tag) else null
        val nfcBInfo = if ("android.nfc.tech.NfcB" in tag.techList) readNfcB(tag) else null
        val nfcFInfo = if ("android.nfc.tech.NfcF" in tag.techList) readNfcF(tag) else null
        val nfcVInfo = if ("android.nfc.tech.NfcV" in tag.techList) readNfcV(tag) else null
        val isoDepInfo = if ("android.nfc.tech.IsoDep" in tag.techList) readIsoDep(tag) else null
        val ndefInfo = if ("android.nfc.tech.Ndef" in tag.techList) readNdef(tag) else null
        val ndefFormatableInfo = if ("android.nfc.tech.NdefFormatable" in tag.techList) readNdefFormatable() else null
        val mifareClassicInfo = if ("android.nfc.tech.MifareClassic" in tag.techList) readMifareClassic(tag) else null
        val mifareUltralightInfo = if ("android.nfc.tech.MifareUltralight" in tag.techList) readMifareUltralight(tag) else null

        Log.d(TAG, "====================================")

        return NfcTagInfo(
            id = id,
            techList = techList,
            nfcAInfo = nfcAInfo,
            nfcBInfo = nfcBInfo,
            nfcFInfo = nfcFInfo,
            nfcVInfo = nfcVInfo,
            isoDepInfo = isoDepInfo,
            ndefInfo = ndefInfo,
            ndefFormatableInfo = ndefFormatableInfo,
            mifareClassicInfo = mifareClassicInfo,
            mifareUltralightInfo = mifareUltralightInfo,
        )
    }

    private fun readNfcA(tag: Tag): NfcAInfo? = try {
        val nfcA = NfcA.get(tag)
        nfcA.connect()
        val info = NfcAInfo(
            atqa = nfcA.atqa.toHexString(),
            sak = nfcA.sak,
            maxTransceiveLength = nfcA.maxTransceiveLength,
            timeout = nfcA.timeout,
        )
        Log.d(TAG, "--- NfcA ---")
        Log.d(TAG, "  ATQA: ${info.atqa}")
        Log.d(TAG, "  SAK: 0x${info.sak.toString(16).padStart(2, '0')}")
        Log.d(TAG, "  Max transceive length: ${info.maxTransceiveLength}")
        Log.d(TAG, "  Timeout: ${info.timeout} ms")
        nfcA.close()
        info
    } catch (e: Exception) {
        Log.e(TAG, "Error reading NfcA: ${e.message}")
        null
    }

    private fun readNfcB(tag: Tag): NfcBInfo? = try {
        val nfcB = NfcB.get(tag)
        nfcB.connect()
        val info = NfcBInfo(
            applicationData = nfcB.applicationData.toHexString(),
            protocolInfo = nfcB.protocolInfo.toHexString(),
            maxTransceiveLength = nfcB.maxTransceiveLength,
        )
        Log.d(TAG, "--- NfcB ---")
        Log.d(TAG, "  Application data: ${info.applicationData}")
        Log.d(TAG, "  Protocol info: ${info.protocolInfo}")
        Log.d(TAG, "  Max transceive length: ${info.maxTransceiveLength}")
        nfcB.close()
        info
    } catch (e: Exception) {
        Log.e(TAG, "Error reading NfcB: ${e.message}")
        null
    }

    private fun readNfcF(tag: Tag): NfcFInfo? = try {
        val nfcF = NfcF.get(tag)
        nfcF.connect()
        val info = NfcFInfo(
            manufacturer = nfcF.manufacturer.toHexString(),
            systemCode = nfcF.systemCode.toHexString(),
            maxTransceiveLength = nfcF.maxTransceiveLength,
            timeout = nfcF.timeout,
        )
        Log.d(TAG, "--- NfcF ---")
        Log.d(TAG, "  Manufacturer: ${info.manufacturer}")
        Log.d(TAG, "  System code: ${info.systemCode}")
        Log.d(TAG, "  Max transceive length: ${info.maxTransceiveLength}")
        Log.d(TAG, "  Timeout: ${info.timeout} ms")
        nfcF.close()
        info
    } catch (e: Exception) {
        Log.e(TAG, "Error reading NfcF: ${e.message}")
        null
    }

    private fun readNfcV(tag: Tag): NfcVInfo? = try {
        val nfcV = NfcV.get(tag)
        nfcV.connect()
        val info = NfcVInfo(
            dsfId = nfcV.dsfId.toInt() and 0xFF,
            responseFlags = nfcV.responseFlags.toInt() and 0xFF,
            maxTransceiveLength = nfcV.maxTransceiveLength,
        )
        Log.d(TAG, "--- NfcV (ISO 15693) ---")
        Log.d(TAG, "  DSF ID: 0x${info.dsfId.toString(16).padStart(2, '0')}")
        Log.d(TAG, "  Response flags: 0x${info.responseFlags.toString(16).padStart(2, '0')}")
        Log.d(TAG, "  Max transceive length: ${info.maxTransceiveLength}")
        nfcV.close()
        info
    } catch (e: Exception) {
        Log.e(TAG, "Error reading NfcV: ${e.message}")
        null
    }

    private fun readIsoDep(tag: Tag): IsoDepInfo? = try {
        val isoDep = IsoDep.get(tag)
        isoDep.connect()
        val info = IsoDepInfo(
            hiLayerResponse = isoDep.hiLayerResponse?.toHexString(),
            historicalBytes = isoDep.historicalBytes?.toHexString(),
            maxTransceiveLength = isoDep.maxTransceiveLength,
            timeout = isoDep.timeout,
            isExtendedLengthApduSupported = isoDep.isExtendedLengthApduSupported,
        )
        Log.d(TAG, "--- IsoDep ---")
        Log.d(TAG, "  Hi-layer response: ${info.hiLayerResponse ?: "N/A"}")
        Log.d(TAG, "  Historical bytes: ${info.historicalBytes ?: "N/A"}")
        Log.d(TAG, "  Max transceive length: ${info.maxTransceiveLength}")
        Log.d(TAG, "  Timeout: ${info.timeout} ms")
        Log.d(TAG, "  Extended APDU supported: ${info.isExtendedLengthApduSupported}")
        isoDep.close()
        info
    } catch (e: Exception) {
        Log.e(TAG, "Error reading IsoDep: ${e.message}")
        null
    }

    private fun readNdef(tag: Tag): NdefInfo? = try {
        val ndef = Ndef.get(tag)
        ndef.connect()
        val ndefMessage = ndef.ndefMessage
        val records = ndefMessage?.records?.map { record ->
            NdefRecordInfo(
                tnf = record.tnf,
                type = record.type.toHexString(),
                id = record.id.toHexString(),
                payload = record.payload.toHexString(),
                payloadSize = record.payload.size,
            )
        } ?: emptyList()
        val info = NdefInfo(
            type = ndef.type ?: "unknown",
            maxSize = ndef.maxSize,
            isWritable = ndef.isWritable,
            canMakeReadOnly = ndef.canMakeReadOnly(),
            records = records,
        )
        Log.d(TAG, "--- NDEF ---")
        Log.d(TAG, "  Type: ${info.type}")
        Log.d(TAG, "  Max size: ${info.maxSize} bytes")
        Log.d(TAG, "  Writable: ${info.isWritable}")
        Log.d(TAG, "  Can make read-only: ${info.canMakeReadOnly}")
        Log.d(TAG, "  Record count: ${records.size}")
        records.forEachIndexed { i, rec ->
            Log.d(TAG, "  Record[$i]: TNF=${rec.tnf}, type=${rec.type}, id=${rec.id}, payload size=${rec.payloadSize}")
            Log.d(TAG, "  Record[$i] payload (hex): ${rec.payload}")
        }
        ndef.close()
        info
    } catch (e: Exception) {
        Log.e(TAG, "Error reading NDEF: ${e.message}")
        null
    }

    private fun readNdefFormatable(): NdefFormatableInfo {
        Log.d(TAG, "--- NdefFormatable ---")
        Log.d(TAG, "  Tag is NDEF formatable")
        return NdefFormatableInfo()
    }

    private fun readMifareClassic(tag: Tag): MifareClassicInfo? = try {
        val mc = MifareClassic.get(tag)
        mc.connect()
        val info = MifareClassicInfo(
            type = mc.type,
            size = mc.size,
            sectorCount = mc.sectorCount,
            blockCount = mc.blockCount,
            maxTransceiveLength = mc.maxTransceiveLength,
            timeout = mc.timeout,
        )
        val typeName = when (mc.type) {
            MifareClassic.TYPE_CLASSIC -> "Classic"
            MifareClassic.TYPE_PLUS -> "Plus"
            MifareClassic.TYPE_PRO -> "Pro"
            else -> "Unknown"
        }
        Log.d(TAG, "--- MifareClassic ---")
        Log.d(TAG, "  Type: $typeName (${info.type})")
        Log.d(TAG, "  Size: ${info.size} bytes")
        Log.d(TAG, "  Sectors: ${info.sectorCount}")
        Log.d(TAG, "  Blocks: ${info.blockCount}")
        Log.d(TAG, "  Max transceive length: ${info.maxTransceiveLength}")
        Log.d(TAG, "  Timeout: ${info.timeout} ms")
        mc.close()
        info
    } catch (e: Exception) {
        Log.e(TAG, "Error reading MifareClassic: ${e.message}")
        null
    }

    private fun readMifareUltralight(tag: Tag): MifareUltralightInfo? = try {
        val mu = MifareUltralight.get(tag)
        mu.connect()
        val info = MifareUltralightInfo(
            type = mu.type,
            maxTransceiveLength = mu.maxTransceiveLength,
            timeout = mu.timeout,
        )
        val typeName = when (mu.type) {
            MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
            MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
            else -> "Unknown"
        }
        Log.d(TAG, "--- MifareUltralight ---")
        Log.d(TAG, "  Type: $typeName (${info.type})")
        Log.d(TAG, "  Max transceive length: ${info.maxTransceiveLength}")
        Log.d(TAG, "  Timeout: ${info.timeout} ms")
        mu.close()
        info
    } catch (e: Exception) {
        Log.e(TAG, "Error reading MifareUltralight: ${e.message}")
        null
    }
}

fun ByteArray.toHexString(): String =
    joinToString(":") { "%02X".format(it) }
