package edu.stanford.homeplateengapp

import kotlin.UByte
import kotlinx.coroutines.delay
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import edu.stanford.homeplateengapp.nfc.NfcTagReader
import edu.stanford.homeplateengapp.nfc.NfcVHandler
import edu.stanford.homeplateengapp.hardware.Max20362
import edu.stanford.homeplateengapp.hardware.sensors.Max30210
import edu.stanford.homeplateengapp.hardware.spi.SC18IS606Driver
import edu.stanford.homeplateengapp.ui.theme.HomePlateEngAppTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        Log.d("NFC_DISCOVERY", "NFC adapter available: ${nfcAdapter != null}")
        if (nfcAdapter != null) {
            Log.d("NFC_DISCOVERY", "NFC enabled: ${nfcAdapter!!.isEnabled}")
        }

        enableEdgeToEdge()
        setContent {
            HomePlateEngAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("NFC Discovery — see Logcat")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE
                adapter.enableReaderMode(this, this, flags, null)
                Log.d("NFC_DISCOVERY", "Reader mode enabled")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        Log.d("NFC_DISCOVERY", "Reader mode disabled")
    }

    override fun onTagDiscovered(tag: Tag) {
        Log.d("NFC_DISCOVERY", "onTagDiscovered called on thread: ${Thread.currentThread().name}")
        NfcTagReader.readTag(tag)

        // Initialize hardware
        Log.i("MainActivity", "Initializing Homeplate...")
        val nfcHandler = NfcVHandler(tag)
        val pmic = Max20362(nfcHandler)
        val bridge = SC18IS606Driver(nfcHandler)
        val tempSensor = Max30210(nfcHandler)

        // Enable Vout to 3.3V
        nfcHandler.voltageHighEnable(true)
        Log.i("MainActivity", "Enabled Vout to 3.3V")

        // Delay and initialize MAX20362
//        Thread.sleep(1000)

        // Read MAX20362 chip_id
//        pmic.readChipId()

        // Scratchwork
//        Log.d("MainActivity", "Unlocking?")
//        pmic.readRegister(0x50u.toUByte())
//        pmic.writeRegister(0x50u.toUByte(), 0x00u)
//        pmic.readRegister(0x50u.toUByte())
//        pmic.readRegister(0x51u.toUByte())
//        pmic.writeRegister(0x51u, 0x55u)

        // Configure BBst
//        Log.d("MainActivity", "Probing BBst")
//        pmic.readRegister(registerAddress = 0x0Cu)
//        pmic.readRegister(Max20362.Registers.BBstCfg0)
//        pmic.writeRegister(Max20362.Registers.BBstCfg0, 0x63u)
//        pmic.readRegister(Max20362.Registers.BBstCfg0)
//        pmic.readRegister(Max20362.Registers.BBstVSet)
//        pmic.readRegister(0x0Au)

        Log.d("MainActivity", "Probing LDO")
        Log.d("MainActivity", "Reading LDO Status ...")
        pmic.readLDOStatus()
        pmic.readLDOVSet()
        Log.d("MainActivity", "Setting LDO to 1.8V ...")
        pmic.writeRegister(Max20362.Registers.LDOVSet, 0x09u)
//        pmic.writeRegister(Max20362.Registers.LDOVSet, 0x04u)
        Log.d("MainActivity", "Reading LDO Status ...")
        pmic.readLDOStatus()
        pmic.readLDOVSet()
        Log.d("MainActivity", "Enabling LDO ...")
        pmic.enableLDO()
        Log.d("MainActivity", "Reading LDO Status ...")
        pmic.readLDOStatus()

        // Initialize SPI Bridge
        bridge.initialize()

        // Communicate with AFE
        bridge.configureSpi(
            mode = SC18IS606Driver.SpiMode.MODE0,
            clock = SC18IS606Driver.SpiClock.KHZ_58,
            bitOrder = SC18IS606Driver.BitOrder.MSB_FIRST
        )
        val register = 0x01FFu
        val txData = ubyteArrayOf(
            ((register.toUInt() shr 8) and 0xFFu).toUByte(),
            (register.toUInt() and 0xFFu).toUByte(),
            0x80u, // Read command
            0x00u  // Dummy byte; clocks out register value
        )

        val rxData = bridge.transfer(
            chipSelect = 0,
            txData = txData
        )

        // Initialize temperature sensor
        tempSensor.initialize()
    }
}

fun UByteArray.UBytetoHexString(): String =
    this.joinToString(" ") { byte ->
        byte.toString(16).uppercase().padStart(2, '0')
    }
