package dev.elliectron.eis.gnssspec30x

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.elliectron.eis.gnssspec30x.ui.theme.EISGNSS_spec30XTheme
import kotlinx.coroutines.Job
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class UiState(
    val titleText: String = "SPD/ACC (km/h)",
    val bodyText1: String = "lat/long/hAcc, alt/vAcc",
    val bodyText2: String = "hdg, hdgAcc",
    val bodyText3: String = "gnss data",
    val noteText: String = "",
    val fileNamePrefix: String = "",
    val gpsInterval: String = "",
    val openBtnEnabled: Boolean = true,
    val closeBtnEnabled: Boolean = false,
    val altnLimit: String = "",
    val normLimit: String = ""
)

class MainActivity : ComponentActivity() {
    private var uiState by mutableStateOf(UiState())
    private lateinit var locMgr: LocationManager
    private var locListener: LocationListener? = null
    private var gnssStsCallback: GnssStatus.Callback? = null
    private var logFile: File? = null
    private var pointsLogFile: File? = null
    private var fileWriter: FileWriter? = null
    private var pointsFileWriter: FileWriter? = null
    private var usedSat: Int = 0
    private var gpsL1: Int = 0
    private var gpsL5: Int = 0
    private var gloL1: Int = 0
    private var galE1: Int = 0
    private var galE5a: Int = 0
    private var bdsB1I: Int = 0
    private var bdsB1C: Int = 0
    private var bdsB2a: Int = 0
    private var otherSat: Int = 0
    private var lat: Double = 0.0
    private var long: Double = 0.0
    private var hAcc: Float = 0f
    private var frozenLat: Double = 0.0
    private var frozenLong: Double = 0.0
    private var frozenHAcc: Float = 0f
    private var frozenTemporal: String = ""


    companion object {
        private const val LOC_PERM_REQ_CODE = 939
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locMgr = getSystemService(LOCATION_SERVICE) as LocationManager
        reqPerms()
        enableEdgeToEdge()
        setContent {
            EISGNSS_spec30XTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Mainpage(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding),
                        onStartBtnClick = ::startBtnClick,
                        onEndBtnClick = ::endBtnClick,
                        onAppendNote = ::appendNote,
                        onDoorsOpenedClick = ::doorsOpenedClick,
                        onDoorsClosedClick = ::doorsClosedClick,
                        titleText = uiState.titleText,
                        bodyText1 = uiState.bodyText1,
                        bodyText2 = uiState.bodyText2,
                        bodyText3 = uiState.bodyText3,
                        noteText = uiState.noteText,
                        onNoteChange = { text -> uiState = uiState.copy(noteText = text) },
                        fileNamePrefix = uiState.fileNamePrefix,
                        onFileNameChange = { text -> uiState = uiState.copy(fileNamePrefix = text) },
                        gpsInterval = uiState.gpsInterval,
                        onGpsIntervalChange = { text -> uiState = uiState.copy(gpsInterval = text) },
                        openBtnEnabled = uiState.openBtnEnabled,
                        closeBtnEnabled = uiState.closeBtnEnabled,
                        altnLimit = uiState.altnLimit,
                        onAltnLimitChange = { text -> uiState = uiState.copy(altnLimit = text)},
                        normLimit = uiState.normLimit,
                        onNormLimitChange = { text -> uiState = uiState.copy(normLimit = text)},
                        freezePointBtnPress = ::freezePointBtnClick,
                        savePointBtnPress = ::savePointBtnClick
                    )
                }
            }
        }
    }

    private fun reqPerms() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOC_PERM_REQ_CODE
            )
        }
    }

    private fun hasPerms(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun freezePointBtnClick() {
        frozenLat = lat;
        frozenLong = long;
        frozenHAcc = hAcc;
        frozenTemporal = Instant.now().toString()
    }

    private fun savePointBtnClick() {
        pointsFileWriter?.let { writer ->
            writer.appendLine("SPDP|$frozenTemporal|$frozenLat|$frozenLong|$frozenHAcc|${uiState.altnLimit}|${uiState.normLimit}")
            writer.flush()
        }
        Log.d("[I/O ]", "ALTN+NORM Speed points written.")
    }

    private fun logData(location: Location) {
        lat = location.latitude
        long = location.longitude
        val alt = location.altitude
        hAcc = location.accuracy
        val vAcc = location.verticalAccuracyMeters
        val spd = location.speed
        val spdAcc = location.speedAccuracyMetersPerSecond
        var hdg = location.bearing
        val hdgAcc = location.bearingAccuracyDegrees

        // Convert m/s to km/h with high precision
        val spdKmh = spd * 3.6
        val spdAccKmh = spdAcc * 3.6
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        val current = formatter.format(LocalDateTime.now())


        // Update UI state
        uiState = uiState.copy(
            titleText = "%.2f km/h ± %.2f km/h".format(spdKmh, spdAccKmh),
            bodyText1 = "%.6f, %.6f ± %.1fm, %.1f ± %.1fm".format(lat, long, hAcc, alt, vAcc),
            bodyText2 = "%.1f° ± %.1f°".format(hdg, hdgAcc) + " | Time now ${current}L"
        )

        Log.d(
            "[GNSS]",
            "lateral pos $lat, $long +- $hAcc m || alt $alt +- $vAcc m || spd $spdKmh +- $spdAccKmh km/h || hdg $hdg +- $hdgAcc deg"
        )
        fileWriter?.let { writer ->
            val timestamp = Instant.now().toString()
            writer.appendLine("DATA|$timestamp|$lat|$long|$hAcc|$alt|$vAcc|$spdKmh|$spdAccKmh|$hdg|$hdgAcc")
            writer.appendLine("SATL|$timestamp|$usedSat|$gpsL1|$gpsL5|$gloL1|$galE1|$galE5a|$bdsB1I|$bdsB1C|$bdsB2a|$otherSat")
            writer.flush()
        }
        Log.d("[I/O ]", "DATA+SAT Written.")
    }

    private fun appendNote() {
        if (uiState.noteText.isNotBlank()) {
            fileWriter?.let { writer ->
                val timestamp = Instant.now().toString()
                writer.appendLine("NOTE|$timestamp|${uiState.noteText}")
                writer.flush()
                Log.d("[I/O ]", "NOTE written.")
            }
            uiState = uiState.copy(noteText = "")
        }
    }

    private fun doorsOpenedClick() {
        fileWriter?.let { writer ->
            val timestamp = Instant.now().toString()
            writer.appendLine("DOOR|OPEN|$timestamp|$lat|$long|$hAcc")
            writer.flush()
            Log.d("[I/O ]", "DOOR|OPEN written")
        }
        uiState = uiState.copy(closeBtnEnabled = true)
        uiState = uiState.copy(openBtnEnabled = false)
    }

    private fun doorsClosedClick() {
        fileWriter?.let { writer ->
            val timestamp = Instant.now().toString()
            writer.appendLine("DOOR|CLOS|$timestamp|$lat|$long|$hAcc")
            writer.flush()
            Log.d("[I/O ]", "DOOR|CLOS written")
        }
        uiState = uiState.copy(openBtnEnabled = true)
        uiState = uiState.copy(closeBtnEnabled = false)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startBtnClick() {
        Log.d("[ACT ]", "Start button clicked")
        uiState = uiState.copy(
            titleText = "Starting...",
            bodyText1 = "Acquiring GNSS...",
            bodyText2 = "Waiting for fix...",
            bodyText3 = "No data yet"
        )
        createLogfile()
        logThreadStart()
    }

    private fun createLogfile() {
        try {
            val fileDir = File(getExternalFilesDir(null), "EIS/GNSS_spec30X")
            if (!fileDir.exists()) {
                val created = fileDir.mkdirs()
                Log.d("[I/O ]", "Directory creation result: $created")
            }
            val timestamp = Instant.now().toString().replace(":", ".")
            val prefix = if (uiState.fileNamePrefix.isNotEmpty()) "${uiState.fileNamePrefix}_" else ""
            val fileName = "${prefix}gnss_log_$timestamp.txt"
            logFile = File(fileDir, fileName)
            val pointsFileName = "points_${prefix}gnss_log_$timestamp.txt"
            pointsLogFile = File(fileDir, pointsFileName)
            fileWriter = FileWriter(logFile!!, true)
            pointsFileWriter = FileWriter(pointsLogFile!!, true)
            Log.d("[I/O ]", "Created log file: ${logFile!!.absolutePath}")
            Log.d("[I/O ]", "Created points log file: ${pointsLogFile!!.absolutePath}")
        } catch (e: Exception) {
            Log.e("[I/O ]", "Failed to create log file: ${e.message}")
        }
    }

    private fun closeLogfile() {
        fileWriter?.close()
        fileWriter = null
        logFile = null
        Log.d("[I/O ]", "Closed log file")
        pointsFileWriter?.close()
        pointsFileWriter = null
        pointsLogFile = null
        Log.d("[I/O ]", "Closed points log file")
    }

    private fun endBtnClick() {
        Log.d("[ACT ]", "End button clicked")
        uiState = UiState() // Reset to default
        logThreadStop()
        closeLogfile()
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun logThreadStart() {
        if (!hasPerms()) {
            reqPerms()
            Log.e("[ERR ]", "No perms.")
            return
        }

        locListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                logData(location)
            }

            override fun onProviderEnabled(provider: String) {
                Log.d("[GNSS]", "Provider $provider ENabled.")
            }

            override fun onProviderDisabled(provider: String) {
                Log.d("[GNSS]", "Provider $provider DISabled.")
            }
        }

        gnssStsCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                usedSat = 0
                gpsL1 = 0
                gpsL5 = 0
                galE1 = 0
                galE5a = 0
                gloL1 = 0
                bdsB1I = 0
                bdsB1C = 0
                bdsB2a = 0
                otherSat = 0

                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) ++usedSat
                    if (status.hasCarrierFrequencyHz(i)) {
                        val freq = status.getCarrierFrequencyHz(i)
                        when (status.getConstellationType(i)) {
                            GnssStatus.CONSTELLATION_GPS -> {
                                when {
                                    freq > 1.575e9 && freq < 1.576e9 -> ++gpsL1
                                    freq > 1.176e9 && freq < 1.177e9 -> ++gpsL5
                                }
                            }

                            GnssStatus.CONSTELLATION_GLONASS -> {
                                when {
                                    freq > 1.598e9 && freq < 1.607e9 -> ++gloL1
                                }
                            }

                            GnssStatus.CONSTELLATION_GALILEO -> {
                                when {
                                    freq > 1.575e9 && freq < 1.576e9 -> ++galE1
                                    freq > 1.176e9 && freq < 1.177e9 -> ++galE5a
                                }
                            }

                            GnssStatus.CONSTELLATION_BEIDOU -> {
                                when {
                                    freq > 1.561e9 && freq < 1.562e9 -> ++bdsB1I
                                    freq > 1.575e9 && freq < 1.576e9 -> ++bdsB1C
                                    freq > 1.176e9 && freq < 1.177e9 -> ++bdsB2a
                                }
                            }

                            else -> {
                                ++otherSat
                            }
                        }
                    }
                }

                // Update satellite info in UI
                uiState = uiState.copy(
                    bodyText3 = "$usedSat total, GPS L1:$gpsL1+L5:$gpsL5, GLO L1:$gloL1, GAL E1:$galE1+E5a:$galE5a, BDS B1I:$bdsB1I+B1C:$bdsB1C+B2a:$bdsB2a, Other:$otherSat"
                )

                Log.d(
                    "[ AA ]",
                    "TOT: $usedSat, GPS $gpsL1 + $gpsL5, GLONASS $gloL1, Galileo $galE1 $galE5a, Beidou $bdsB1I $bdsB1C $bdsB2a, Other $otherSat"
                )
            }
        }

        try {
            val intervalMs = if (uiState.gpsInterval.isEmpty()) 1000L else uiState.gpsInterval.toLongOrNull() ?: 1000L
            locMgr.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                intervalMs,
                0f,
                locListener!!
            )
            locMgr.registerGnssStatusCallback(gnssStsCallback!!)
            Log.d("[ OK ]", "Logging started")
        } catch (e: SecurityException) {
            Log.e("[ERR ]", "No perms.")
            reqPerms()
        }
    }

    private fun logThreadStop() {
        locListener?.let { listener ->
            locMgr.removeUpdates(listener)
            locListener = null
        }
        gnssStsCallback?.let { callback ->
            locMgr.unregisterGnssStatusCallback(callback)
            gnssStsCallback = null
        }
        Log.d("[TERM]", "Logging ended")
    }
}

@Composable
fun Mainpage(
    name: String,
    modifier: Modifier = Modifier,
    onStartBtnClick: () -> Unit = {},
    onEndBtnClick: () -> Unit = {},
    onAppendNote: () -> Unit = {},
    onDoorsOpenedClick: () -> Unit = {},
    onDoorsClosedClick: () -> Unit = {},
    titleText: String = "Status",
    bodyText1: String = "Ready",
    bodyText2: String = "0 satellites",
    bodyText3: String = "No location",
    noteText: String = "",
    onNoteChange: (String) -> Unit = {},
    fileNamePrefix: String = "",
    onFileNameChange: (String) -> Unit = {},
    gpsInterval: String = "",
    onGpsIntervalChange: (String) -> Unit = {},
    openBtnEnabled: Boolean = true,
    closeBtnEnabled: Boolean = false,
    altnLimit: String = "",
    onAltnLimitChange: (String) -> Unit = {},
    normLimit: String = "",
    onNormLimitChange: (String) -> Unit = {},
    freezePointBtnPress: () -> Unit = {},
    savePointBtnPress: () -> Unit = {}
) {
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(systemBarsPadding)
    ) {
        // Top left label
        Text(
            text = "EIS.GNSS_Spec30X",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 20.dp),
            color = Color.White
        )

        // Centered column with buttons and labels
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = fileNamePrefix,
                    onValueChange = onFileNameChange,
                    placeholder = { Text("file name", color = Color.Gray) },
                    modifier = Modifier.width(140.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = gpsInterval,
                    onValueChange = onGpsIntervalChange,
                    placeholder = { Text("every 1000ms", color = Color.Gray) },
                    modifier = Modifier.width(140.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = onStartBtnClick) {
                    Text("Start")
                }
                FilledTonalButton(onClick = onEndBtnClick) {
                    Text("End")
                }
            }

            // Labels section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = bodyText1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = bodyText2,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = bodyText3,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = onNoteChange,
                label = { Text("Add note", color = Color.Gray) },
                modifier = Modifier
                    .width(300.dp)
                    .height(120.dp),
                maxLines = 4
            )

            Button(
                onClick = onAppendNote,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Save note")
            }

            // Quick notes section
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = onDoorsOpenedClick, enabled = openBtnEnabled) {
                    Text("Doors opened")
                }
                OutlinedButton(onClick = onDoorsClosedClick, enabled = closeBtnEnabled) {
                    Text("Doors closed")
                }
            }

            // Points section
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedButton(onClick = freezePointBtnPress) {
                    Text("Freeze")
                }
                OutlinedTextField(
                    value = altnLimit,
                    onValueChange = onAltnLimitChange,
                    placeholder = { Text("altn") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = normLimit,
                    onValueChange = onNormLimitChange,
                    placeholder = { Text("norm") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                FilledTonalButton(onClick = savePointBtnPress) {
                    Text("Save")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EISGNSS_spec30XTheme {
        Mainpage("Android")
    }
}