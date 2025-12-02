package com.example.aerogcsclone.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Language Manager for app-wide localization
 * Provides strings in English and Telugu based on selected language
 */
object AppStrings {
    private var currentLanguage: String = "en" // Default to English

    fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
    }

    fun getCurrentLanguage(): String = currentLanguage

    // Helper function to get string based on current language
    private fun getString(english: String, telugu: String): String {
        return if (currentLanguage == "en") english else telugu
    }

    // Connection Page Strings
    val connectionTitle get() = getString("Connect to Drone", "డ్రోన్‌కు కనెక్ట్ చేయండి")
    val connectionType get() = getString("Connection Type", "కనెక్షన్ రకం")
    val tcp get() = getString("TCP", "టీసీపీ")
    val bluetooth get() = getString("Bluetooth", "బ్లూటూత్")
    val ipAddress get() = getString("IP Address", "ఐపీ చిరునామా")
    val port get() = getString("Port", "పోర్ట్")
    val selectDevice get() = getString("Select Device", "పరికరాన్ని ఎంచుకోండి")
    val noDevicesPaired get() = getString("No devices paired", "పరికరాలు జత చేయబడలేదు")
    val connect get() = getString("Connect", "కనెక్ట్ చేయండి")
    val cancel get() = getString("Cancel", "రద్దు చేయండి")
    val connecting get() = getString("Connecting...", "కనెక్ట్ అవుతోంది...")
    val connectionTimedOut get() = getString("Connection timed out. Please check your settings and try again.", "కనెక్షన్ గడువు ముగిసింది. దయచేసి మీ సెట్టింగ్‌లను తనిఖీ చేసి మళ్లీ ప్రయత్నించండి.")
    val connectionFailed get() = getString("Connection failed", "కనెక్షన్ విఫలమైంది")

    // Select Method Page Strings
    val selectFlightMode get() = getString("Select Flight Mode", "ఫ్లైట్ మోడ్‌ను ఎంచుకోండి")
    val automatic get() = getString("Automatic", "ఆటోమేటిక్")
    val manual get() = getString("Manual", "మాన్యువల్")
    val automaticDesc get() = getString("Plan and execute missions automatically", "స్వయంచాలకంగా మిషన్‌లను ప్లాన్ చేసి అమలు చేయండి")
    val manualDesc get() = getString("Control the drone manually", "డ్రోన్‌ను మాన్యువల్‌గా నియంత్రించండి")

    // Main Page / Telemetry Strings
    val altitude get() = getString("Altitude", "ఎత్తు")
    val speed get() = getString("Speed", "వేగం")
    val battery get() = getString("Battery", "బ్యాటరీ")
    val satellites get() = getString("Satellites", "ఉపగ్రహాలు")
    val mode get() = getString("Mode", "మోడ్")
    val armed get() = getString("Armed", "ఆర్మ్ అయింది")
    val disarmed get() = getString("Disarmed", "డిసార్మ్ అయింది")
    val connected get() = getString("Connected", "కనెక్ట్ అయింది")
    val disconnected get() = getString("Disconnected", "డిస్కనెక్ట్ అయింది")

    // Calibration Strings
    val calibration get() = getString("Calibration", "కేలిబ్రేషన్")
    val compass get() = getString("Compass", "కంపాస్")
    val accelerometer get() = getString("Accelerometer", "యాక్సిలరోమీటర్")
    val barometer get() = getString("Barometer", "బేరోమీటర్")
    val remoteController get() = getString("Remote Controller", "రిమోట్ కంట్రోలర్")
    val startCalibration get() = getString("Start Calibration", "కేలిబ్రేషన్ ప్రారంభించండి")
    val cancelCalibration get() = getString("Cancel Calibration", "కేలిబ్రేషన్ రద్దు చేయండి")
    val calibrationInProgress get() = getString("Calibration in Progress", "కేలిబ్రేషన్ పురోగతిలో ఉంది")
    val calibrationComplete get() = getString("Calibration Complete", "కేలిబ్రేషన్ పూర్తయింది")
    val calibrationFailed get() = getString("Calibration Failed", "కేలిబ్రేషన్ విఫలమైంది")
    val calibrationStarted get() = getString("Calibration Started", "కేలిబ్రేషన్ ప్రారంభమైంది")

    // IMU Calibration Position Strings
    val placeVehicleLevel get() = getString("Place vehicle level", "వాహనాన్ని సమానంగా ఉంచండి")
    val placeVehicleOnLeft get() = getString("Place vehicle on left side", "వాహనాన్ని ఎడమ వైపు ఉంచండి")
    val placeVehicleOnRight get() = getString("Place vehicle on right side", "వాహనాన్ని కుడి వైపు ఉంచండి")
    val placeVehicleNoseDown get() = getString("Place vehicle nose down", "వాహనాన్ని నోస్ కిందకి ఉంచండి")
    val placeVehicleNoseUp get() = getString("Place vehicle nose up", "వాహనాన్ని నోస్ పైకి ఉంచండి")
    val placeVehicleOnBack get() = getString("Place vehicle on back", "వాహనాన్ని వెనక్కి తిప్పండి")

    // IMU Position voice announcements (more natural speech)
    val imuLevel get() = getString("Place the vehicle level", "అన్ని వైపులా సమానంగా పెట్టండి")
    val imuLeft get() = getString("Place on left side", "ఎడమ వైపు పెట్టండి")
    val imuRight get() = getString("Place on right side", "కుడి వైపు పెట్టండి")
    val imuNoseDown get() = getString("Place nose down", "నోస్ కిందకి పెట్టండి")
    val imuNoseUp get() = getString("Place nose up", "నోస్ పైకి పెట్టండి")
    val imuBack get() = getString("Place on back, upside down", "వెనక్కి తిప్పండి")

    // Compass Calibration Strings
    val rotateVehicle get() = getString("Rotate vehicle", "వాహనాన్ని తిప్పండి")
    val holdSteady get() = getString("Hold steady", "స్థిరంగా పట్టుకోండి")
    val compassCalibrationStarted get() = getString("Compass calibration started", "కంపాస్ కేలిబ్రేషన్ ప్రారంభమైంది")
    val compassCalibrationComplete get() = getString("Compass calibration complete", "కంపాస్ కేలిబ్రేషన్ పూర్తయింది")

    // Mission/Plan Strings
    val plan get() = getString("Plan", "ప్లాన్")
    val fly get() = getString("Fly", "ఎగరండి")
    val uploadMission get() = getString("Upload Mission", "మిషన్ అప్‌లోడ్ చేయండి")
    val startMission get() = getString("Start Mission", "మిషన్ ప్రారంభించండి")
    val clearMission get() = getString("Clear Mission", "మిషన్ క్లియర్ చేయండి")
    val missionUploaded get() = getString("Mission Uploaded", "మిషన్ అప్‌లోడ్ అయింది")
    val missionStarted get() = getString("Mission Started", "మిషన్ ప్రారంభమైంది")
    val waypoints get() = getString("Waypoints", "వేపాయింట్లు")
    val missionPaused get() = getString("Mission Paused", "మిషన్ పాజ్ అయింది")
    val missionResumed get() = getString("Mission Resumed", "మిషన్ రిజ్యూమ్ అయింది")
    val pausedAtWaypoint get() = getString("Paused at waypoint", "వేపాయింట్ వద్ద పాజ్ చేయబడింది")

    // Settings Strings
    val settings get() = getString("Settings", "సెట్టింగ్‌లు")
    val language get() = getString("Language", "భాష")
    val about get() = getString("About", "గురించి")

    // Common Strings
    val ok get() = getString("OK", "సరే")
    val yes get() = getString("Yes", "అవును")
    val no get() = getString("No", "కాదు")
    val save get() = getString("Save", "సేవ్ చేయండి")
    val delete get() = getString("Delete", "తొలగించండి")
    val edit get() = getString("Edit", "సవరించండి")
    val back get() = getString("Back", "వెనక్కి")
    val next get() = getString("Next", "తదుపరి")
    val previous get() = getString("Previous", "మునుపటి")
    val done get() = getString("Done", "పూర్తయింది")
    val close get() = getString("Close", "మూసివేయండి")
    val error get() = getString("Error", "లోపం")
    val warning get() = getString("Warning", "హెచ్చరిక")
    val info get() = getString("Info", "సమాచారం")
    val success get() = getString("Success", "విజయం")

    // Login/Auth Strings
    val login get() = getString("Login", "లాగిన్")
    val signup get() = getString("Signup", "సైన్అప్")
    val email get() = getString("Email", "ఇమెయిల్")
    val password get() = getString("Password", "పాస్‌వర్డ్")
    val loginWithPavaman get() = getString("Login with pavaman", "పవమాన్‌తో లాగిన్ అవ్వండి")
    val loginCredentials get() = getString("Login with pavaman credentials", "పవమాన్ ఆధారాలతో లాగిన్ అవ్వండి")
    val signInWithGoogle get() = getString("Sign in with Google", "గూగుల్‌తో సైన్ ఇన్ చేయండి")

    // Reboot Strings
    val rebootDrone get() = getString("Please reboot your drone", "దయచేసి మీ డ్రోన్ రీబూట్ చేయండి")
}

// Composable for reactive language updates
val LocalLanguage = staticCompositionLocalOf { "en" }
