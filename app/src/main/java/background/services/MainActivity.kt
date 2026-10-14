package background.services

import android.Manifest
import android.app.ComponentCaller
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.ActivityNotFoundException
class MainActivity : AppCompatActivity() {
    private val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
    private lateinit var txtLatihan: TextView
    private lateinit var btnLatihan: Button

    private var workoutPermissionRequest = false

    private lateinit var btnKalender: Button
    private var completedJumpCount = 0
    private var completedWorkoutTime = 0L

    companion object {
        const val ACTION_SERVICE_STOPPED_JUMP = "background.services.ACTION_SERVICE_STOPPED_JUMP"
        const val ACTION_START_WORKOUT = "background.services.ACTION_START_WORKOUT"
        const val EXTRA_JUMP_COUNT = "background.services.EXTRA_JUMP_COUNT"
        const val EXTRA_WORKOUT_TIME = "background.services.EXTRA_WORKOUT_TIME"
        const val REQUEST_CODE_PERMISSIONS = 101
    }

    private fun hasAllPermissions(): Boolean {
        return permissionToRequest.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startTrackerService() {
        val serviceIntent = Intent(this, TrackerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        txtLacak.text = "Background service sedang berjalan..."
    }

    private fun startWorkoutService() {
        val serviceIntent = Intent(this, TrackerService::class.java).apply {
            action = ACTION_START_WORKOUT
        }
        ContextCompat.startForegroundService(
            this,
            serviceIntent
        )
        txtLatihan.text = "Latihan sedang berjalan..."
        btnLatihan.isEnabled = false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode == REQUEST_CODE_PERMISSIONS) {
                var allPermissionsGranted = true
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false
                        break
                    }
                }
                if (allPermissionsGranted) {
                    if (workoutPermissionRequest) {
                        workoutPermissionRequest = false
                        startWorkoutService()
                    } else {
                        startTrackerService()
                        finish()
                    }
                } else {
                    if (workoutPermissionRequest) {
                        txtLatihan.text = "Izin ditolak. Latihan tidak dapat dimulai."
                    } else {
                        txtLacak.text = "Izin ditolak, service tidak dapat dijalankan."
                    }

                    workoutPermissionRequest = false
                }
            }
        }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_SERVICE_STOPPED_JUMP) {
            return
        }

        val totalJumps = intent.getIntExtra(
            EXTRA_JUMP_COUNT,
            -1
        )

        if (totalJumps >= 0) {
            completedJumpCount = totalJumps
            completedWorkoutTime = intent.getLongExtra(
                EXTRA_WORKOUT_TIME,
                System.currentTimeMillis()
            )

            txtLatihan.text = "Latihan selesai!\nJumlah lompatan: $completedJumpCount"
            btnLatihan.isEnabled = true
            btnKalender.visibility = View.VISIBLE

        } else {
            txtLacak.text = "Background services sudah berhenti karena Anda melompat."
        }
    }

    private fun addWorkoutToCalendar() {
        val startTime = if (completedWorkoutTime > 0L) {
            completedWorkoutTime
        } else {
            System.currentTimeMillis()
        }

        val endTime = startTime + 30 * 60 * 1000L
        val calendarIntent = Intent(
            Intent.ACTION_INSERT
        ).apply {
            data = CalendarContract.Events.CONTENT_URI

            putExtra(
                CalendarContract.Events.TITLE,
                "Latihan Lompat"
            )

            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Latihan selesai dengan $completedJumpCount lompatan."
            )

            putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                startTime
            )

            putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                endTime
            )
        }

        try {
            startActivity(calendarIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Tidak ada aplikasi kalender.",
                Toast.LENGTH_LONG
            ).show()
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
            txtLacak = findViewById<TextView>(R.id.txtLacak)

            btnLatihan = findViewById(R.id.btnLatihan)
            txtLatihan = findViewById(R.id.txtLatihan)


            btnLacak.setOnClickListener {
                if (hasAllPermissions()) {
                    startTrackerService()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        permissionToRequest,
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            }

            btnLatihan.setOnClickListener {
                if (hasAllPermissions()) {
                    startWorkoutService()
                } else {
                    workoutPermissionRequest = true
                    ActivityCompat.requestPermissions(
                        this,
                        permissionToRequest,
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            }

            btnKalender = findViewById(R.id.btnTambahKalender)
            btnKalender.setOnClickListener {
                addWorkoutToCalendar()
            }

            handleIntent(intent)
        }
    }