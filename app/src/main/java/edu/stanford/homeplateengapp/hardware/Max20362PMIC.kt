package edu.stanford.homeplateengapp.hardware
import edu.stanford.homeplateengapp.nfc.NfcVHandler
//package edu.stanford.homplateengapp.NfcVHandler

class Max20362(private val transport: NfcVHandler) {

    val readAddress: UByte = 0xD1u
    val writeAddress: UByte = 0xD0u

    object Registers {
        val CHIP_ID: UByte = 0x00u
        val BBstCfg0: UByte = 0x01u
        val BBstVSet: UByte = 0x02u
        val LDOStatus:UByte = 0x13u
        val LDOCfg: UByte = 0x40u
        val LDOVSet: UByte = 0x41u
        val CONFIG: UByte = 0x10u
        val STATUS: UByte = 0x11u
        val CONTROL: UByte = 0x12u
    }

    object Bits {
        val LDOCfg_Ena: UByte = 0x01u
        val ENABLE: UByte = 0x01u
        val ERROR: UByte = 0x80u

    }

    fun writeRegister(registerAddress: UByte, value: UByte) {
//        val current = readRegister(registerAddress)
        val message = ubyteArrayOf(
            writeAddress,
            registerAddress,
            value,
        )
        transport.i2cTransceive(message,0, true)
    }

    fun readRegister(registerAddress: UByte): UByte {
        val message = ubyteArrayOf(
            writeAddress,
            registerAddress,
            readAddress,
        )
        val response = transport.i2cTransceive(message,1,true)
        return response[0]
    }

    fun readChipId() {
        readRegister(Registers.CHIP_ID)
    }

    fun readBBstCfg0() {

    }
    fun readBBstVSet() {
        readRegister(Registers.BBstVSet)
    }

    fun readLDOStatus(){
        readRegister(Registers.LDOStatus)
    }

    fun readLDOVSet() {
        readRegister(Registers.LDOVSet)
    }

    fun writeLDOVSet18() {
        writeRegister(Registers.LDOVSet, 0x08u.toUByte())
    }

    fun writeLDOCfg(bitmask: UByte) {
        val current = readRegister(Registers.LDOCfg)
        writeRegister(Registers.LDOCfg, current or bitmask)
    }

    fun enableLDO() {
        writeLDOCfg(Bits.LDOCfg_Ena)
    }

//    suspend fun enable() {
//        setBit(Registers.CONTROL, Bits.ENABLE)
//    }
//
//    suspend fun disable() {
//        clearBit(Registers.CONTOL, Bits.ENABLE)
//    }

//    suspend fun isError(): Boolean {
//        val status = readRegister(Registers.STATUS)
//        return (status and Bits.ERROR) != 0u.toUByte()
//    }
}
