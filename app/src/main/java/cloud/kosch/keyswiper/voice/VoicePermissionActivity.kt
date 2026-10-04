package cloud.kosch.keyswiper.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

class VoicePermissionActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) finish()
        else if (state == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 41) {
            Toast.makeText(this, if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                "Mikrofon freigegeben. Zum Diktieren erneut auf das Mikrofon tippen."
                else "Mikrofonzugriff wurde nicht erlaubt.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
