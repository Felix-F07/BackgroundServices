package background.services

import android.Manifest
import android.app.ComponentCaller
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,

        )
    } else {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

    }

    private lateinit var txtLacak: TextView
    private lateinit var btnLacak: Button

    companion object{
        const val ACTION_SERVICE_STOPPED_JUMP = "background.services.ACTION_SERVICE_STOPPED_JUMP"
        const val REQUEST_CODE_PERMISSIONS = 101
    }

    private fun hasAllPermissions(): Boolean{
        return permissionToRequest.all {
            ContextCompat.checkSelfPermission(this,it)== PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startTrackerService(){
        val serviceIntent = Intent(this, TrackerService::class.java)
        ContextCompat.startForegroundService(this,serviceIntent)
        txtLacak.text = "Background service sedang berjalan..."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var allPermissionsGranted = true
            for (result in grantResults){
                if (result != PackageManager.PERMISSION_GRANTED){
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted){
                val serviceIntent = Intent(this, TrackerService::class.java)
                startForegroundService(serviceIntent)
                finish()
            } else{
                txtLacak.text = "Izin ditolak,service tidak dapat dijalankan."
            }
        }
    }

    private fun handleIntent (intent: Intent?){
        if (intent?.action  == ACTION_SERVICE_STOPPED_JUMP){
            txtLacak.text = "Background services sudah berhenti akrena Anda melompat"
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnLacak = findViewById<Button>(R.id.btnLacak)
        txtLacak = findViewById<TextView>(R.id.txtLaca)


        btnLacak.setOnClickListener {
            if (hasAllPermissions()) {
                startTrackerService()
            } else{
                ActivityCompat.requestPermissions(
                    this,
                    permissionToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }

        handleIntent(intent)
    }
}