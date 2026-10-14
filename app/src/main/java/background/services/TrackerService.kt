package background.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.abs


private lateinit var sensorManager: SensorManager
private var accelerometer: Sensor? = null
private val NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING = "tracker_service_running"
private val NOTIFICATION_CHANNEL_ID_JUMP_DETECTED = "tracker_jump_detected"
private val NOTIFICATION_ID_SERVICE_RUNNING = 1
private val NOTIFICATION_ID_JUMP_DETECTED = 2
private var isWorkoutMode = false
private var jumpCount = 0
private var lastJumpDetectedAt = 0L
private var jumpArmed = true

class TrackerService : Service(), SensorEventListener {

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }


    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) {
            return
        }

        val y = event.values[1].toDouble()
        val absoluteY = abs(y)
        val jumpThreshold = if (isWorkoutMode) 12.0 else 15.0

        if (absoluteY <= jumpThreshold) {
            jumpArmed = true
            return
        }
        if (!jumpArmed) {
            return
        }

        jumpArmed = false

        if (!isWorkoutMode) {
            Log.d(
                "TrackerService",
                "LOMPATAN Terdeteksi! y: $y"
            )

            sensorManager.unregisterListener(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            showJumpNotification()
            stopSelf()
            return
        }
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastJumpDetectedAt < 800L) {
            return
        }

        lastJumpDetectedAt = currentTime
        jumpCount++

        Log.d(
            "TrackerService",
            "Lompatan ke-$jumpCount terdeteksi! y: $y"
        )
        if (jumpCount > 2) {
            Log.d(
                "TrackerService",
                "Latihan selesai. Total lompatan: $jumpCount"
            )

            sensorManager.unregisterListener(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            showJumpNotification()
            stopSelf()
        }
    }



    private fun createNotificationChannels(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING,
                "Service Running Channel",
                NotificationManager.IMPORTANCE_LOW
            )

            val jumpChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_JUMP_DETECTED,
                "Jump Detected Channel",
                NotificationManager.IMPORTANCE_HIGH
            )

            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(jumpChannel)

        }
    }

    override fun onCreate(){
        super.onCreate()
        sensorManager = getSystemService (Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannels()
        Log.d("TrackerService","Service onCreate")
    }

    private fun startJumpDetection(){
        accelerometer?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            isWorkoutMode =
                intent.action == MainActivity.ACTION_START_WORKOUT

            jumpCount = 0
            lastJumpDetectedAt = 0L
            jumpArmed = true
        }
        Log.d("TrackerService", "Service onStartCommand")

        startForeground(
            NOTIFICATION_ID_SERVICE_RUNNING,
            buildServiceRunningNotification()
        )

        startJumpDetection()
        return START_STICKY
    }

    private fun buildServiceRunningNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,0,notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this,NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING)
            .setContentTitle("Pelacak Aktivitas Latar")
            .setContentText("Aplikasi Tracker Services sedang berjalan...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showJumpNotification(){
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SERVICE_STOPPED_JUMP

            if (isWorkoutMode) {
                putExtra(
                    MainActivity.EXTRA_JUMP_COUNT,
                    jumpCount
                )

                putExtra(
                    MainActivity.EXTRA_WORKOUT_TIME,
                    System.currentTimeMillis()
                )
            }

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingMainActivityIntent = PendingIntent.getActivity(
            this,1,mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val jumpNotification = NotificationCompat.Builder(this,NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
            .setContentTitle(
                if (isWorkoutMode) {
                    "Latihan Selesai"
                } else {
                    "Lompatan Terdeteksi"
                }
            )
            .setContentText(
                if (isWorkoutMode) {
                    "Latihan selesai setelah $jumpCount lompatan. Ketuk untuk membuka aplikasi."
                } else {
                    "Service berhenti. Ketuk untuk membuka aplikasi."
                }
            )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(!isWorkoutMode)
            .setAutoCancel(isWorkoutMode)
            .setContentIntent(pendingMainActivityIntent)
            .setPriority(
                if (isWorkoutMode) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_LOW
                }
            )
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_JUMP_DETECTED, jumpNotification)
        } catch ( e: SecurityException){
            Log.e(
                "TrackerService",
                "Gagal memunculkan notifikasi karena permission POST_NOTIFICATIONS tidak aktif: ${e.message}"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Log.d("TrackerService","Service onDestroy")
    }


}