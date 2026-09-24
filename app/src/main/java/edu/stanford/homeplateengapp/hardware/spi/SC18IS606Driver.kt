@file:OptIn(ExperimentalUnsignedTypes::class)

package edu.stanford.homeplateengapp.hardware.spi

import edu.stanford.homeplateengapp.nfc.NfcVHandler

/**
 * Driver for the NXP SC18IS606 I2C-to-SPI bridge.
 *
 * Transport assumption:
 * NfcVHandler.i2cTransceive(message, readLength) accepts an I2C frame whose first
 * byte is the 8-bit address byte (7-bit target address shifted left, plus R/W),
 * and returns exactly readLength bytes for read transactions.
 *
 * If NfcVHandler instead accepts a 7-bit I2C address separately, move the address
 * byte construction into NfcVHandler and keep this driver's command sequencing.
 */
class SC18IS606Driver(
    private val transport: NfcVHandler,
    private val addressPins: UByte = 0u, // A2:A1:A0, valid range 0..7
) : SpiBridge {

    companion object {
        private const val BUFFER_SIZE = 1024
        private const val VERSION_LENGTH = 16
        private const val EXPECTED_PART = "SC18IS606"

        // SC18IS606 Function IDs
        private val CMD_CONFIG_SPI: UByte = 0xF0u
        private val CMD_CLEAR_INTERRUPT: UByte = 0xF1u
        private val CMD_IDLE: UByte = 0xF2u
        private val CMD_GPIO_WRITE: UByte = 0xF4u
        private val CMD_GPIO_READ: UByte = 0xF5u
        private val CMD_GPIO_ENABLE: UByte = 0xF6u
        private val CMD_GPIO_CONFIG: UByte = 0xF7u
        private val CMD_READ_VERSION: UByte = 0xFEu

        private val NULL_BYTE: UByte = 0x00u

        /*
         * The datasheet says the SC18IS606 does not acknowledge its address while
         * busy. These retry values are software policy, not datasheet timing limits.
         * Tune them after measuring the NFC -> I2C path on the target hardware.
         */
        private const val BUSY_RETRY_COUNT = 1
        private const val BUSY_RETRY_DELAY_MS = 1L
    }

    enum class SpiMode(val bits: Int) {
        MODE0(0b00), // CPOL=0, CPHA=0
        MODE1(0b01), // CPOL=0, CPHA=1
        MODE2(0b10), // CPOL=1, CPHA=0
        MODE3(0b11), // CPOL=1, CPHA=1
    }

    enum class SpiClock(val bits: Int) {
        KHZ_1875(0b00),
        KHZ_455(0b01),
        KHZ_115(0b10),
        KHZ_58(0b11),
    }

    enum class BitOrder(val bit: Int) {
        MSB_FIRST(0),
        LSB_FIRST(1),
    }

    init {
        require(addressPins.toInt() in 0..7) {
            "SC18IS606 addressPins must encode A2:A1:A0 in the range 0..7"
        }
    }

    /**
     * Production-friendly presence check.
     *
     * Reads the part/version string and accepts any firmware version whose
     * part-number field is SC18IS606.
     */
    override fun initialize(): Boolean {
        return runCatching {
            readVersion().substringBefore(' ') == EXPECTED_PART
        }.getOrDefault(false)
    }

    /**
     * Function ID FEh.
     *
     * Sequence:
     *   START -> address(W) -> FEh -> STOP
     *   START -> address(R) -> 16 bytes -> STOP
     *
     * The read-buffer transaction contains no Function ID.
     */
    fun readVersion(): String {
        writeCommand(CMD_READ_VERSION)

        val raw = readBuffer(VERSION_LENGTH)
        val nullIndex = raw.indexOf(NULL_BYTE)

        require(nullIndex >= 0) {
            "SC18IS606 version response is not null-terminated: ${raw.toHexString()}"
        }

        return buildString(nullIndex) {
            for (index in 0 until nullIndex) {
                append(raw[index].toInt().toChar())
            }
        }
    }

    /**
     * Configures SPI with Function ID F0h.
     *
     * Reset/default configuration is MSB-first, Mode 0, 1.875 MHz.
     */
    fun configureSpi(
        mode: SpiMode = SpiMode.MODE0,
        clock: SpiClock = SpiClock.KHZ_1875,
        bitOrder: BitOrder = BitOrder.MSB_FIRST,
    ) {
        val config =
            ((bitOrder.bit and 0x01) shl 5) or
                    ((mode.bits and 0x03) shl 2) or
                    (clock.bits and 0x03)

        writeCommand(
            functionId = CMD_CONFIG_SPI,
            payload = ubyteArrayOf(config.toUByte()),
        )
    }

    /**
     * Starts an SPI transaction and leaves the captured MISO data in the
     * SC18IS606 internal buffer.
     *
     * chipSelect is an index:
     *   0 -> SS0 (Function ID 01h)
     *   1 -> SS1 (Function ID 02h)
     *   2 -> SS2 (Function ID 04h)
     */
    fun writeSpiTransaction(
        chipSelect: Int,
        txData: UByteArray,
    ) {
        require(chipSelect in 0..2) {
            "SC18IS606 has three chip selects: SS0, SS1, SS2"
        }
        require(txData.isNotEmpty()) {
            "SPI transfer must contain at least one data byte"
        }
        require(txData.size <= BUFFER_SIZE) {
            "SPI transfer exceeds the SC18IS606 $BUFFER_SIZE-byte buffer"
        }

        val functionId = (1 shl chipSelect).toUByte()

        writeCommand(
            functionId = functionId,
            payload = txData,
        )
    }

    /**
     * Reads previously captured MISO bytes from the SC18IS606 internal buffer.
     */
    fun readSpiBuffer(length: Int): UByteArray {
        return readBuffer(length)
    }

    /**
     * Convenience full-duplex SPI transaction.
     *
     * Equivalent to:
     *   writeSpiTransaction(chipSelect, txData)
     *   readSpiBuffer(txData.size)
     */
    fun transfer(
        chipSelect: Int,
        txData: UByteArray,
    ): UByteArray {
        writeSpiTransaction(
            chipSelect = chipSelect,
            txData = txData,
        )

        return readSpiBuffer(txData.size)
    }

    /**
     * Function ID F1h. Not required when polling if the INT pin is unused.
     */
    fun clearInterrupt() {
        writeCommand(CMD_CLEAR_INTERRUPT)
    }

    /**
     * Function ID F2h.
     * Idle mode is exited when the SC18IS606 detects its I2C target address.
     */
    fun enterIdleMode() {
        writeCommand(CMD_IDLE)
    }

    /**
     * Function ID F6h.
     *
     * Bit 0 -> SS0/GPIO0
     * Bit 1 -> SS1/GPIO1
     * Bit 2 -> SS2/GPIO2
     *
     * A set bit enables GPIO function for that pin.
     */
    fun enableGpio(mask: UByte) {
        require((mask.toInt() and 0xF8) == 0) {
            "GPIO enable mask may only use bits 0..2"
        }

        writeCommand(
            functionId = CMD_GPIO_ENABLE,
            payload = ubyteArrayOf(mask),
        )
    }

    /**
     * Function ID F4h.
     * Only pins configured as GPIO are affected.
     */
    fun writeGpio(value: UByte) {
        require((value.toInt() and 0xF8) == 0) {
            "GPIO value may only use bits 0..2"
        }

        writeCommand(
            functionId = CMD_GPIO_WRITE,
            payload = ubyteArrayOf(value),
        )
    }

    /**
     * Function ID F5h followed by a one-byte read-buffer operation.
     */
    fun readGpio(): UByte {
        writeCommand(CMD_GPIO_READ)
        return readBuffer(1).first()
    }

    /**
     * Function ID F7h.
     *
     * Raw layout:
     *   bits 1:0 -> GPIO0 mode
     *   bits 3:2 -> GPIO1 mode
     *   bits 5:4 -> GPIO2 mode
     *
     * Mode encoding:
     *   00 = input-only
     *   01 = push-pull
     *   10 = input-only
     *   11 = open-drain
     */
    fun configureGpio(rawConfig: UByte) {
        require((rawConfig.toInt() and 0xC0) == 0) {
            "GPIO configuration uses only bits 0..5"
        }

        writeCommand(
            functionId = CMD_GPIO_CONFIG,
            payload = ubyteArrayOf(rawConfig),
        )
    }

    /**
     * Sends a Function ID and optional payload to the SC18IS606 data buffer.
     */
    private fun writeCommand(
        functionId: UByte,
        payload: UByteArray = ubyteArrayOf(),
    ) {
        require(payload.size <= BUFFER_SIZE) {
            "Payload exceeds the SC18IS606 $BUFFER_SIZE-byte buffer"
        }

        val frame = UByteArray(2 + payload.size)
        frame[0] = addressByte(read = false)
        frame[1] = functionId

        if (payload.isNotEmpty()) {
            payload.copyInto(
                destination = frame,
                destinationOffset = 2,
            )
        }

        retryWhileBusy {
            transport.i2cTransceive(frame, 0)
            Unit
        }
    }

    /**
     * Reads bytes already present in the SC18IS606 data buffer.
     *
     * Per the datasheet, a read-buffer operation has no Function ID.
     */
    private fun readBuffer(length: Int): UByteArray {
        require(length in 1..BUFFER_SIZE) {
            "Read length must be in 1..$BUFFER_SIZE"
        }

        val frame = ubyteArrayOf(addressByte(read = true))

        return retryWhileBusy {
            val response = transport.i2cTransceive(frame, length)

            require(response.size >= length) {
                "Expected $length bytes from SC18IS606, received ${response.size}"
            }

            response
        }
    }

    /**
     * 7-bit address format from the datasheet:
     *
     *   0 1 0 1 A2 A1 A0
     *
     * For A2:A1:A0 = 000:
     *   7-bit target address = 0x28
     *   write address byte   = 0x50
     *   read address byte    = 0x51
     */
    private fun addressByte(read: Boolean): UByte {
        val address7Bit = 0x28 or (addressPins.toInt() and 0x07)
        val rw = if (read) 1 else 0

        return ((address7Bit shl 1) or rw).toUByte()
    }

    /**
     * SC18IS606 NACKs its I2C address while an internal function is still busy.
     *
     * This assumes NfcVHandler reports that NACK as an exception. If it returns
     * an explicit status instead, adapt this helper rather than changing the
     * higher-level driver logic.
     */
    private inline fun <T> retryWhileBusy(block: () -> T): T {
        var lastError: Exception? = null

        repeat(BUSY_RETRY_COUNT) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e

                if (attempt < BUSY_RETRY_COUNT - 1) {
                    Thread.sleep(BUSY_RETRY_DELAY_MS)
                }
            }
        }

        throw IllegalStateException(
            "SC18IS606 did not become ready after $BUSY_RETRY_COUNT attempts",
            lastError,
        )
    }

    private fun UByteArray.toHexString(): String =
        joinToString(separator = " ") { byte ->
            "0x%02X".format(byte.toInt())
        }
}