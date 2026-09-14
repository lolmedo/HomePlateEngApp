package edu.stanford.homeplateengapp.hardware.spi

import edu.stanford.homeplateengapp.nfc.NfcVHandler

class SC18IS606Driver(
    private val transport: NfcVHandler
) : SpiBridge {

    companion object {
//        const val CS0 = 0
//        const val CS1 = 1
//        const val CS2 = 2
//        const val CS3 = 3
//
//        const val CONFIG_SPI = 0xF0.toByte()
//        const val CLEAR_INTERRUPT = 0xF1.toByte()
        private const val I2C_ADDRESS: UByte = 0x50u
        private const val WRITE_BIT: UByte = 0x00u
        private const val READ_BIT: UByte = 0x01u
        private const val CMD_READ_VERSION: UByte = 0xFEu
        private const val VERSION_LENGTH = 16
        private const val EXPECTED_PART = "SC18IS606"
    }

    override fun initialize(): Boolean {
        return try {
            readVersion().startsWith(EXPECTED_PART)
        } catch (e: Exception) {
            false
        }
    }

    fun readVersion(): String {
        // Command: Read Version (FEh)
        val message_command = ubyteArrayOf(
            I2C_ADDRESS or WRITE_BIT,
            CMD_READ_VERSION,
        )
        val message_read = ubyteArrayOf(
            I2C_ADDRESS or READ_BIT
        )
        transport.i2cTransceive(message_command, 0)
        transport.i2cTransceive(message_read, VERSION_LENGTH)
        return "Nothing"
    }

//    override fun transfer(
//        chipSelect: Int,
//        txData: ByteArray
//    ): ByteArray {
//        ...
//    }
//
//    fun configureSpi(
//        clockHz: Int,
//        mode: Int
//    ) {
//        ...
//    }
}