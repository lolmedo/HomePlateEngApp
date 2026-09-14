package edu.stanford.homeplateengapp.hardware.spi

interface SpiBridge {

//    fun transfer(
//        chipSelect: Byte,
//        txData: ByteArray
//    ): ByteArray
    fun initialize(): Boolean

}