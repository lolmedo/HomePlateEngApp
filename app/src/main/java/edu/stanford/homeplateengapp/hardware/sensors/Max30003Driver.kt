@file:OptIn(ExperimentalUnsignedTypes::class)

package edu.stanford.homeplateengapp.hardware.sensors

/**
 * MAX30003 ECG AFE driver.
 *
 * Layering:
 *   Android app / service
 *      -> NFC command transport
 *      -> NFC-to-I2C bridge
 *      -> SC18IS606 I2C-to-SPI bridge
 *      -> SpiTransferTransport (this file's only transport dependency)
 *      -> MAX30003
 *
 * The MAX30003 driver intentionally knows nothing about NFC or I2C. The lower transport
 * layer is responsible for delivering one SPI transaction with CS asserted for the whole
 * txData array and returning one received byte for each transmitted byte.
 */
class Max30003Driver(
    private val spi: SpiTransferTransport,
    private val chipSelect: Int = 0,
) {

    /**
     * Minimal SPI contract expected from the transport layer.
     *
     * This matches the transaction shape exposed by the SC18IS606 layer:
     *   transfer(chipSelect, txData) -> rxData
     */
    fun interface SpiTransferTransport {
        fun transfer(chipSelect: Int, txData: UByteArray): UByteArray
    }

    /**
     * Physical byte addresses for one logical 24-bit MAX30003 register.
     *
     * Your custom device returns one byte per normal SPI transaction, so a logical
     * 24-bit MAX30003 register is reconstructed from three byte reads.
     */
    data class RegisterByteAddresses(
        val msb: UShort,
        val mid: UShort,
        val lsb: UShort,
    )

    enum class Register(val address: UShort) {
        NO_OP(0x00u),
        STATUS(0x01u),
        EN_INT(0x02u),
        EN_INT2(0x03u),
        MNGR_INT(0x04u),
        MNGR_DYN(0x05u),
        SW_RST(0x08u),
        SYNCH(0x09u),
        FIFO_RST(0x0Au),
        INFO(0x01FFu),
        CNFG_GEN(0x10u),
        CNFG_CAL(0x12u),
        CNFG_EMUX(0x14u),
        CNFG_ECG(0x15u),
        CNFG_RTOR1(0x1Du),
        CNFG_RTOR2(0x1Eu),
        ECG_FIFO_BURST(0x20u),
        ECG_FIFO(0x21u),
        RTOR(0x25u),
        NO_OP_ALT(0x7Fu),
    }

    enum class MasterClock(
        val bits: Int,
        val rtorResolutionMillis: Double,
    ) {
        FCLK_32768_HZ(bits = 0b00, rtorResolutionMillis = 7.8125),
        FCLK_32000_HZ_500_FAMILY(bits = 0b01, rtorResolutionMillis = 8.0),
        FCLK_32000_HZ_200_FAMILY(bits = 0b10, rtorResolutionMillis = 8.0),
        FCLK_31968_78_HZ(bits = 0b11, rtorResolutionMillis = 8.0078),
    }

    enum class EcgRate(val bits: Int) {
        RATE_0(bits = 0b00),
        RATE_1(bits = 0b01),
        RATE_2(bits = 0b10),
    }

    enum class EcgGain(val bits: Int, val voltsPerVolt: Int) {
        X20(bits = 0b00, voltsPerVolt = 20),
        X40(bits = 0b01, voltsPerVolt = 40),
        X80(bits = 0b10, voltsPerVolt = 80),
        X160(bits = 0b11, voltsPerVolt = 160),
    }

    enum class DigitalHighPass(val bit: Int) {
        BYPASS(0),
        HZ_0_5(1),
    }

    enum class DigitalLowPass(val bits: Int) {
        BYPASS(0b00),
        APPROX_40_HZ(0b01),
        APPROX_100_HZ(0b10),
        APPROX_150_HZ(0b11),
    }

    enum class LeadBiasResistance(val bits: Int) {
        M50(0b00),
        M100(0b01),
        M200(0b10),
    }

    enum class LeadOffCurrent(val bits: Int) {
        OFF(0b000),
        NA_5(0b001),
        NA_10(0b010),
        NA_20(0b011),
        NA_50(0b100),
        NA_100(0b101),
    }

    enum class LeadOffThreshold(val bits: Int) {
        MV_300(0b00),
        MV_400(0b01),
        MV_450(0b10),
        MV_500(0b11),
    }

    enum class InterruptOutputType(val bits: Int) {
        DISABLED(0b00),
        CMOS(0b01),
        OPEN_DRAIN(0b10),
        OPEN_DRAIN_WITH_125K_PULLUP(0b11),
    }

    enum class RtorInterruptClear(val bits: Int) {
        ON_STATUS_READ(0b00),
        ON_RTOR_READ(0b01),
        SELF_CLEAR(0b10),
    }

    enum class EcgTag(val bits: Int) {
        VALID(0b000),
        FAST_MODE(0b001),
        VALID_EOF(0b010),
        FAST_MODE_EOF(0b011),
        UNUSED_100(0b100),
        UNUSED_101(0b101),
        EMPTY(0b110),
        OVERFLOW(0b111),
        ;

        val hasValidVoltage: Boolean
            get() = this == VALID || this == VALID_EOF

        val advancesTime: Boolean
            get() = this == VALID || this == FAST_MODE || this == VALID_EOF || this == FAST_MODE_EOF

        val isEndOfFile: Boolean
            get() = this == VALID_EOF || this == FAST_MODE_EOF
    }

    data class Status(
        val raw: Int,
        val ecgFifoInterrupt: Boolean,
        val ecgFifoOverflow: Boolean,
        val fastRecoveryInterrupt: Boolean,
        val dcLeadOffInterrupt: Boolean,
        val leadsOnInterrupt: Boolean,
        val rtorInterrupt: Boolean,
        val sampleInterrupt: Boolean,
        val pllUnlocked: Boolean,
        val leadOffPositiveHigh: Boolean,
        val leadOffPositiveLow: Boolean,
        val leadOffNegativeHigh: Boolean,
        val leadOffNegativeLow: Boolean,
    )

    data class DeviceInfo(
        val raw: UByte,
        val interfacePatternValid: Boolean,
    )

    data class EcgSample(
        /** Full 24-bit FIFO word. */
        val rawWord: Int,
        /** Signed 18-bit ECG ADC code after sign extension. */
        val code: Int,
        val tag: EcgTag,
        /** PTAG is exposed raw because this datasheet section does not define its semantics. */
        val pTag: Int,
    ) {
        val hasValidVoltage: Boolean get() = tag.hasValidVoltage
        val advancesTime: Boolean get() = tag.advancesTime
        val isEndOfFile: Boolean get() = tag.isEndOfFile
    }

    data class RtorMeasurement(
        val rawCount: Int,
        val intervalMillis: Double,
        val beatsPerMinute: Double?,
    )

    data class EcgConfig(
        val masterClock: MasterClock = MasterClock.FCLK_32768_HZ,
        val rate: EcgRate = EcgRate.RATE_2,
        val gain: EcgGain = EcgGain.X20,
        val highPass: DigitalHighPass = DigitalHighPass.HZ_0_5,
        val lowPass: DigitalLowPass = DigitalLowPass.APPROX_40_HZ,
        val invertInputs: Boolean = false,
        val enableRtor: Boolean = false,
        val enableLeadBias: Boolean = false,
        val leadBiasResistance: LeadBiasResistance = LeadBiasResistance.M100,
        val biasPositiveInput: Boolean = true,
        val biasNegativeInput: Boolean = true,
        val enableDcLeadOff: Boolean = false,
        val leadOffCurrent: LeadOffCurrent = LeadOffCurrent.OFF,
        val leadOffThreshold: LeadOffThreshold = LeadOffThreshold.MV_300,
        val leadOffReversePolarity: Boolean = false,
        /** Number of unread FIFO records that causes EINT, 1..32. */
        val fifoInterruptThreshold: Int = 8,
        val enableFifoInterrupt: Boolean = true,
        val enableFifoOverflowInterrupt: Boolean = true,
        val enableRtorInterrupt: Boolean = false,
        val interruptOutputType: InterruptOutputType = InterruptOutputType.OPEN_DRAIN_WITH_125K_PULLUP,
    ) {
        init {
            require(fifoInterruptThreshold in 1..32) {
                "fifoInterruptThreshold must be in 1..32"
            }
            require(!enableRtorInterrupt || enableRtor) {
                "enableRtorInterrupt requires enableRtor=true"
            }
        }

        /**
         * Human-readable ECG sample rate implied by masterClock + rate.
         * Throws for combinations the datasheet marks reserved.
         */
        fun sampleRateSps(): Double = when (masterClock) {
            MasterClock.FCLK_32768_HZ -> when (rate) {
                EcgRate.RATE_0 -> 512.0
                EcgRate.RATE_1 -> 256.0
                EcgRate.RATE_2 -> 128.0
            }
            MasterClock.FCLK_32000_HZ_500_FAMILY -> when (rate) {
                EcgRate.RATE_0 -> 500.0
                EcgRate.RATE_1 -> 250.0
                EcgRate.RATE_2 -> 125.0
            }
            MasterClock.FCLK_32000_HZ_200_FAMILY -> {
                require(rate == EcgRate.RATE_2) { "FMSTR=10 only supports RATE=10 (200 sps)" }
                200.0
            }
            MasterClock.FCLK_31968_78_HZ -> {
                require(rate == EcgRate.RATE_2) { "FMSTR=11 only supports RATE=10 (~199.8 sps)" }
                199.8049
            }
        }

        fun validate() {
            val sps = sampleRateSps()
            when (lowPass) {
                DigitalLowPass.BYPASS,
                DigitalLowPass.APPROX_40_HZ -> Unit

                DigitalLowPass.APPROX_100_HZ -> require(
                    sps == 512.0 || sps == 500.0 || sps == 256.0 || sps == 250.0
                ) { "~100 Hz DLPF is unsupported at $sps sps" }

                DigitalLowPass.APPROX_150_HZ -> require(
                    sps == 512.0 || sps == 500.0
                ) { "~150 Hz DLPF is unsupported at $sps sps" }
            }
        }
    }

    /**
     * Configure normal ECG acquisition without crossing transport layers.
     *
     * Sequence:
     *  1. Software reset.
     *  2. One NO-OP because INFO is not valid as the first command after reset.
     *  3. Configure FIFO/interrupt behavior, ECG front-end, RTOR and input mux.
     *  4. Enable ECG in CNFG_GEN.
     *  5. Caller may wait for PLL lock, then issue SYNCH to establish time zero.
     *
     * By default this method waits for PLL lock and synchronizes. Set waitForPll=false
     * if your upper layer owns timing/polling.
     */
    fun initialize(
        config: EcgConfig = EcgConfig(),
        waitForPll: Boolean = true,
        pllTimeoutMs: Long = 250L,
        pollDelayMs: Long = 5L,
        delay: (Long) -> Unit = { Thread.sleep(it) },
    ): DeviceInfo {
        config.validate()

        softwareReset()
        readRegister(Register.NO_OP)
        val info = readInfo()

//        writeRegister(Register.MNGR_INT, buildManagerInterrupt(config))
//        writeRegister(Register.EN_INT, buildInterruptEnable(config))
//        writeRegister(Register.EN_INT2, 0x000000) // Dedicated INT2B path disabled unless the upper layer configures it.
//        writeRegister(Register.CNFG_ECG, buildEcgConfig(config))
//        writeRegister(Register.CNFG_RTOR1, buildRtor1Config(config))
//        writeRegister(Register.CNFG_RTOR2, DEFAULT_RTOR2)
//        writeRegister(Register.CNFG_EMUX, buildEmuxConfig(config))
//        writeRegister(Register.CNFG_GEN, buildGeneralConfig(config))

        if (waitForPll) {
            waitForPllLock(
                timeoutMs = pllTimeoutMs,
                pollDelayMs = pollDelayMs,
                delay = delay,
            )
        }

        synchronize()
        return info
    }

    /**
     * Read one physical 8-bit register from the custom device.
     *
     * Wire format:
     *   [ADDR_MSB][ADDR_LSB][READ=0x80][DUMMY]
     *
     * The received register byte is expected in rx[3].
     */
    fun readByte(address: UShort): UByte {
        val a = address.toUInt()
        val tx = ubyteArrayOf(
            ((a shr 8) and 0xFFu).toUByte(),
            (a and 0xFFu).toUByte(),
            READ_COMMAND,
            0x00u,
        )
        return transferChecked(tx)[3]
    }

    /** Write one physical 8-bit register on the custom device. */
    fun writeByte(address: UShort, value: UByte) {
        val a = address.toUInt()
        val tx = ubyteArrayOf(
            ((a shr 8) and 0xFFu).toUByte(),
            (a and 0xFFu).toUByte(),
            WRITE_COMMAND,
            value,
        )
        transferChecked(tx)
    }


    fun readRegister(register: Register): UByte {
        return readByte(register.address)
    }


    fun writeRegister(register: Register, value: UByte) {
        writeByte(register.address, value)
    }

    fun softwareReset() = writeRegister(Register.SW_RST, 0x00u)

    fun synchronize() = writeRegister(Register.SYNCH, 0x00u)

    fun resetFifo() = writeRegister(Register.FIFO_RST, 0x00u)

    fun readInfo(): DeviceInfo {
        val raw = readRegister(Register.INFO)
        return DeviceInfo(
            raw = raw,
            interfacePatternValid = raw == 0x4f.toUByte(),
        )
    }

    /**
     * Reading STATUS can clear latched status/interrupt terms according to the MAX30003
     * interrupt-clear configuration. Treat this as a servicing operation, not a passive peek.
     */
//    fun readStatus(): Status {
//        val raw = readRegister(Register.STATUS)
//        return Status(
//            raw = raw,
//            ecgFifoInterrupt = raw.hasBit(23),
//            ecgFifoOverflow = raw.hasBit(22),
//            fastRecoveryInterrupt = raw.hasBit(21),
//            dcLeadOffInterrupt = raw.hasBit(20),
//            leadsOnInterrupt = raw.hasBit(11),
//            rtorInterrupt = raw.hasBit(10),
//            sampleInterrupt = raw.hasBit(9),
//            pllUnlocked = raw.hasBit(8),
//            leadOffPositiveHigh = raw.hasBit(3),
//            leadOffPositiveLow = raw.hasBit(2),
//            leadOffNegativeHigh = raw.hasBit(1),
//            leadOffNegativeLow = raw.hasBit(0),
//        )
//    }

    fun waitForPllLock(
        timeoutMs: Long = 250L,
        pollDelayMs: Long = 5L,
        delay: (Long) -> Unit = { Thread.sleep(it) },
    ) {
        require(timeoutMs >= 0L)
        require(pollDelayMs > 0L)

        var elapsed = 0L
        while (true) {
//            if (!readStatus().pllUnlocked) return
            if (elapsed >= timeoutMs) {
                error("MAX30003 PLL did not lock within ${timeoutMs}ms")
            }
            delay(pollDelayMs)
            elapsed += pollDelayMs
        }
    }

    /**
     * Read one ECG FIFO sample using the custom burst protocol.
     *
     * A custom FIFO sample is 4 bytes. For compatibility with the original
     * MAX30003 parser, the lower 24 bits are interpreted as the stock FIFO word.
     * Change [extractMax30003Word] if your custom 32-bit frame places those bits
     * somewhere else.
     */
    fun readEcgSample(): EcgSample = readEcgFifoBurst(1).first()

    /**
     * Read up to [maxSamples] using one-sample custom burst transactions.
     * Stops on EOF or EMPTY and resets the FIFO on overflow.
     */
    fun drainEcgFifo(maxSamples: Int = 32): List<EcgSample> {
        require(maxSamples in 1..32) { "maxSamples must be in 1..32" }

        val result = ArrayList<EcgSample>(maxSamples)
        repeat(maxSamples) {
            val sample = readEcgSample()
            when (sample.tag) {
                EcgTag.OVERFLOW -> {
                    resetFifo()
                    error("MAX30003 ECG FIFO overflowed; FIFO was reset")
                }
                EcgTag.EMPTY -> return result
                else -> {
                    result += sample
                    if (sample.isEndOfFile) return result
                }
            }
        }
        return result
    }

    /**
     * Read [wordCount] FIFO samples in one custom burst frame.
     *
     * TX/RX frame shape:
     *   [ADDR_MSB][ADDR_LSB][READ] + 4 clocks per requested sample
     *
     * Chip select must remain asserted for the entire frame.
     */
    fun readEcgFifoBurst(wordCount: Int): List<EcgSample> {
        require(wordCount in 1..32) { "wordCount must be in 1..32" }

        val burstAddress = Register.ECG_FIFO_BURST.address.toUInt()
        val headerSize = 3
        val sampleSize = 4
        val tx = UByteArray(headerSize + wordCount * sampleSize)
        tx[0] = ((burstAddress shr 8) and 0xFFu).toUByte()
        tx[1] = (burstAddress and 0xFFu).toUByte()
        tx[2] = READ_COMMAND

        val rx = transferChecked(tx)
        val result = ArrayList<EcgSample>(wordCount)

        for (index in 0 until wordCount) {
            val base = headerSize + index * sampleSize
            val rawFrame = bytesTo32(
                rx[base],
                rx[base + 1],
                rx[base + 2],
                rx[base + 3],
            )
            val sample = parseEcgWord(extractMax30003Word(rawFrame))
            when (sample.tag) {
                EcgTag.OVERFLOW -> {
                    resetFifo()
                    error("MAX30003 ECG FIFO overflowed during burst read; FIFO was reset")
                }
                EcgTag.EMPTY -> return result
                else -> {
                    result += sample
                    if (sample.isEndOfFile) return result
                }
            }
        }
        return result
    }

    /**
     * Compatibility assumption for the custom 4-byte sample frame:
     * stock MAX30003 FIFO bits occupy the lower 24 bits.
     */
    private fun extractMax30003Word(rawFrame: UInt): Int =
        (rawFrame and 0x00FF_FFFFu).toInt()

    fun parseEcgWord(rawWord: Int): EcgSample {
        require(rawWord in 0..MASK_24) { "FIFO word must fit in 24 bits" }

        val unsigned18 = (rawWord ushr 6) and 0x3FFFF
        val signed18 = if ((unsigned18 and 0x20000) != 0) {
            unsigned18 - 0x40000
        } else {
            unsigned18
        }
        val tagBits = (rawWord ushr 3) and 0x07
        val pTag = rawWord and 0x07

        return EcgSample(
            rawWord = rawWord,
            code = signed18,
            tag = EcgTag.entries.first { it.bits == tagBits },
            pTag = pTag,
        )
    }

    private fun buildGeneralConfig(config: EcgConfig): Int {
        var value = 0
        value = value or (config.masterClock.bits shl 20)
        value = value or (1 shl 19) // EN_ECG

        if (config.enableDcLeadOff) {
            value = value or (0b01 shl 12)
            if (config.leadOffReversePolarity) value = value or (1 shl 11)
            value = value or (config.leadOffCurrent.bits shl 8)
            value = value or (config.leadOffThreshold.bits shl 6)
        }

        if (config.enableLeadBias) {
            value = value or (0b01 shl 4)
            value = value or (config.leadBiasResistance.bits shl 2)
            if (config.biasPositiveInput) value = value or (1 shl 1)
            if (config.biasNegativeInput) value = value or 1
        }

        return value
    }

    private fun buildEcgConfig(config: EcgConfig): Int {
        return (config.rate.bits shl 22) or
                (config.gain.bits shl 16) or
                (config.highPass.bit shl 14) or
                (config.lowPass.bits shl 12)
    }

    private fun buildEmuxConfig(config: EcgConfig): Int {
        var value = 0
        if (config.invertInputs) value = value or (1 shl 23)
        // OPENP=0 and OPENN=0 connect both electrodes to the ECG AFE.
        // No calibration source is selected.
        return value
    }

    private fun buildRtor1Config(config: EcgConfig): Int {
        // Datasheet defaults: WNDW=3, RGAIN=15(auto), PAVG=2, PTSF=3.
        var value = (0x3 shl 20) or (0xF shl 16) or (0x2 shl 12) or (0x3 shl 8)
        if (config.enableRtor) value = value or (1 shl 15)
        return value
    }

    private fun buildManagerInterrupt(config: EcgConfig): Int {
        val efit = config.fifoInterruptThreshold - 1
        return (efit shl 19) or
                (RtorInterruptClear.ON_RTOR_READ.bits shl 4) or
                (1 shl 2) // CLR_SAMP = self-clear
    }

    private fun buildInterruptEnable(config: EcgConfig): Int {
        var value = config.interruptOutputType.bits
        if (config.enableFifoInterrupt) value = value or (1 shl 23)
        if (config.enableFifoOverflowInterrupt) value = value or (1 shl 22)
        if (config.enableRtorInterrupt) value = value or (1 shl 10)
        return value
    }

    private fun transferChecked(tx: UByteArray): UByteArray {
        val rx = spi.transfer(chipSelect, tx)
        require(rx.size == tx.size) {
            "SPI transport returned ${rx.size} bytes for ${tx.size} transmitted bytes"
        }
        return rx
    }

    private fun bytesTo32(b0: UByte, b1: UByte, b2: UByte, b3: UByte): UInt =
        (b0.toUInt() shl 24) or
                (b1.toUInt() shl 16) or
                (b2.toUInt() shl 8) or
                b3.toUInt()

    private fun Int.hasBit(bit: Int): Boolean = (this and (1 shl bit)) != 0

    companion object {
        private const val MASK_24 = 0x00FF_FFFF
        private const val DEFAULT_RTOR2 = (0x20 shl 16) or (0x2 shl 12) or (0x4 shl 8)
        private val READ_COMMAND: UByte = 0x80u
        private val WRITE_COMMAND: UByte = 0x00u
    }
}
