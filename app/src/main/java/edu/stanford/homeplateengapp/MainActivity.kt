package edu.stanford.homeplateengapp

import android.nfc.NfcAdapter
import android.nfc.Tag
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
    }
}
