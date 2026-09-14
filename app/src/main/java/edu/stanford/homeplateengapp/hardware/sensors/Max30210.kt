package edu.stanford.homeplateengapp.hardware.sensors

import edu.stanford.homeplateengapp.nfc.NfcVHandler

class Max30210(private val transport: NfcVHandler) {
    companion object {
        private const val I2C_ADDRESS: UByte = 0x80u
        private const val WRITE_BIT: UByte = 0x00u
        private const val READ_BIT: UByte = 0x01u
        private const val REG_PART_IDENTIFIER: UByte = 0xFFu
    }

    fun initialize() {
        val message = ubyteArrayOf(
            I2C_ADDRESS or WRITE_BIT,
            REG_PART_IDENTIFIER,
            I2C_ADDRESS or READ_BIT,
        )
        transport.i2cTransceive(message, 1)
    }
}

//infix fun UByte.appendBit(bit: UByte): UByte =
//    (((this.toInt() shl 1) or (bit.toInt() and 0x01)) and 0xFF).toUByte()