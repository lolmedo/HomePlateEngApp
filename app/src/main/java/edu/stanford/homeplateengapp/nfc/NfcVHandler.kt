package edu.stanford.homeplateengapp.nfc
import android.util.Log
import android.nfc.Tag
import android.nfc.tech.NfcV
import edu.stanford.homeplateengapp.UBytetoHexString
import java.io.IOException

class NfcVHandler(private val tag: Tag) {
    private var nfcV: NfcV =
        NfcV.get(tag) ?: throw IllegalArgumentException("Tag does not support NfcV")

    init {
        connect()
    }

    private fun connect() {
        try {
            nfcV?.connect()
            println("NFCV Connected")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun transceive(data: UByteArray): UByteArray {
        Log.d("NfcVTranscieve", "TX: ${data.toByteArray().toHexString()}" )
        val responseByte: ByteArray = nfcV.transceive(data.toByteArray())
        val responseUByte: UByteArray =  responseByte.toUByteArray()
        Log.d("NfcVTransceive", "RX: ${responseUByte.UBytetoHexString()}")
        return responseUByte
    }

    fun voltageHighEnable(ehoe_bit: Boolean = true) { // This function will enable the energy harvesting pin to 3.3V
        val parByte: UByte = if (ehoe_bit) 0xA9u else 0xA8u
        val nfcData = ubyteArrayOf(
            0x02u.toUByte(),        // Request Flags = High Data Rate ON
            0xA9u.toUByte(),        // Command: Peripheral Transaction (PTRA)
            0x2Bu.toUByte(),        // Manufacturer Code
            0x42u.toUByte(),        // Control Write
//            0x40u.toUByte(),         // Configuration Write
            parByte,                // Parameter byte.
            // Table 2.
        )
        transceive(nfcData)
    }

    fun i2cTransceive(i2cData: UByteArray, numberReadBytes: Int, stopBit: Boolean = true): UByteArray {
        return try {
            // Create NFC data packets.
            val numberWriteBytes: Int = i2cData.size
            val parByte: UByte = if (stopBit) 0x10u else 0x00u
            val nfcData = ubyteArrayOf(
                0x02u.toUByte(),        // Request Flags = High Data Rate ON
                0xA2u.toUByte(),        // Command: Peripheral Transaction (PTRA)
                0x2Bu.toUByte(),        // Manufacturer Code
                parByte,                // Parameter Byte
                numberWriteBytes.toUByte(),
            ) + i2cData + ubyteArrayOf(numberReadBytes.toUByte())
//            Log.d("NfcV_i2cTransceive", "TX: ${nfcData.toHexString()}")

            // Transceive NFC data.
            val response = transceive(nfcData)
            if (response == null || response[0]==0x01.toUByte()) {
                // Log invalid or erroneous response
                Log.e("NfcV_i2cTransceive", "RX: Operation failed.")
                return UByteArray(1)
            }
//            Log.d("NfcV_i2cTransceive", "RX: ${response.toHexString()}")
            return response.drop(1).toUByteArray()   // Drop byte indicating successful response
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("NfcV_i2cTransceive", "Operation error.")
            return UByteArray(1)
        }
    }

    fun close() {
        try {
            nfcV?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}