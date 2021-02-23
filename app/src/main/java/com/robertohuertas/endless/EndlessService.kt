package com.robertohuertas.endless

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.*
import android.provider.Settings
import android.widget.Toast
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.google.android.gms.location.*
import com.orm.SugarContext
import com.orm.query.Condition
import com.orm.query.Select
import com.robertohuertas.endless.dao.IDao
import com.robertohuertas.endless.dao.LocationDao
import com.robertohuertas.endless.dao.LogDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*


class EndlessService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mLocation: Location? = null

    private var locationCallback: LocationCallback? = null

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private val UPDATE_INTERVAL_IN_MILLISECONDS = (60 * 1000).toLong()

    /**
     * The fastest rate for active location updates. Updates will never be more frequent
     * than this value.
     */
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

    var locationRequest: LocationRequest? = null

    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created".toUpperCase())
        val notification = createNotification()
        startForeground(1, notification)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        SugarContext.init(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {

                    if(location == null)
                        continue

                    if(mLocation == null || mLocation!!.time < location.time)
                        mLocation = location

                    salvarPosicao(location)
                }
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun salvarPosicao(location: Location) {
        val locationDao = LocationDao()
        locationDao.longitude = location.longitude
        locationDao.latitude = location.latitude
        locationDao.time = location.time
        locationDao.timeDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.mmmZ").format(
            Date(
                location.time
            )
        )
        locationDao.enviado = 0

        locationDao.save()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        locationRequest = LocationRequest()
        locationRequest?.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        locationRequest?.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        locationRequest?.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        log("The service has been destroyed".toUpperCase())
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, EndlessService::class.java).also {
            it.setPackage(packageName)
        }
        val restartServicePendingIntent: PendingIntent = PendingIntent.getService(
            this,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT
        )
        applicationContext.getSystemService(Context.ALARM_SERVICE)
        val alarmService: AlarmManager =
            applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {
                launch(Dispatchers.IO) {
                    saveLog()
                    mLocation?.let { salvarPosicao(it) }

                    enviarPosicoesPendentes()
                }
                delay(1 * 60 * 1000)
            }
            log("End of the loop for the service")
        }

        startLocationUpdates()
    }

    @SuppressLint("HardwareIds", "SimpleDateFormat")
    private fun saveLog() {

        val pm = getSystemService(POWER_SERVICE) as PowerManager


        val gmtTime =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.mmmZ").format(Date(System.currentTimeMillis()))

        val deviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val log = LogDao()
        log.deviceId = deviceId
        log.data_computador_bordo = gmtTime
        log.version = 4
        log.enviado = 0
        log.log = getLocation()
        log.isPowerSaveMode = pm.isPowerSaveMode
        log.isDeviceIdleMode = pm.isDeviceIdleMode
        log.isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(packageName)
        log.save()

        pingFakeServer()
    }

    private fun getLocation(): String {
        if (mLocation == null)
            return ""

        val json = JSONObject()
        json.put("getTime", mLocation?.time)
        json.put("getLongitude", mLocation?.longitude)
        json.put("getLatitude", mLocation?.latitude)
        return json.toString()
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun enviarPosicoesPendentes() {
//        fusedLocationClient.lastLocation
//            .addOnSuccessListener { location: Location? ->
//                val json = JSONObject()
//                json.put("latitude", location?.latitude)
//                json.put("longitude", location?.longitude)
//                json.put("data_computador_bordo", location?.time)
//                json.put(
//                    "terminal", Settings.Secure.getString(
//                        applicationContext.contentResolver,
//                        Settings.Secure.ANDROID_ID
//                    )
//                )
//
//                sendRequest(
//                    "http://ec2-54-233-86-218.sa-east-1.compute.amazonaws.com:3000/api/posicoes",
//                    json.toString()
//                )
//            }

        val locations =
            Select.from(LocationDao::class.java).where(Condition.prop("enviado").eq(0)).limit("25")
                .list()

        val jsonArray = JSONArray()
        locations?.forEach { location ->

            val json = JSONObject()
            json.put("latitude", location?.latitude)
            json.put("longitude", location?.longitude)
            json.put("data_computador_bordo", location?.time)
            json.put(
                "terminal", Settings.Secure.getString(
                    applicationContext.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            )
            json.put("creationDate", location?.creationDate)
            json.put("modelo", """${Build.MANUFACTURER} ${Build.MODEL}""")

            jsonArray.put(json)
        }

        sendRequest(
            "http://ec2-54-233-86-218.sa-east-1.compute.amazonaws.com:3000/api/posicoes",
            jsonArray.toString(), locations
        )
    }

    private fun stopService() {
        log("Stopping the foreground service")
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
    }

    private fun pingFakeServer() {
        val logs =
            Select.from(LogDao::class.java).where(Condition.prop("enviado").eq(0)).limit("25")
                .list()

        val jsonArray = JSONArray()
        logs.forEach { log ->

            val json = JSONObject()
            json.put("deviceId", log.deviceId)
            json.put("data_computador_bordo", log.data_computador_bordo)
            json.put("version", log.version)
            json.put("log", log.log)
            json.put("isPowerSaveMode", log.isPowerSaveMode)
            json.put("isDeviceIdleMode", log.isDeviceIdleMode)
            json.put("isIgnoringBatteryOptimizations", log.isIgnoringBatteryOptimizations)
            json.put("creationDate", log.creationDate)
            json.put("modelo", """${Build.MANUFACTURER} ${Build.MODEL}""")

            jsonArray.put(json)
        }

        sendRequest(
            "http://ec2-54-233-86-218.sa-east-1.compute.amazonaws.com:3000/api/log",
            jsonArray.toString(), logs
        )
    }

    private fun sendRequest(url: String, json: String, logs: List<IDao>? = null) {
        try {
            Fuel.post(url)
                .timeout(50 * 1000)
                .jsonBody(json)
                .response { _, b, result ->
                    val (bytes, error) = result
                    if (bytes != null) {
                        log("[response bytes] ${String(bytes)}")
                    } else {
                        log("[response error] ${error?.message}")
                    }

                    if (b.statusCode in 200..299) {
                        logs?.forEach { log ->
                            log.enviado = 1
                            log.saveDao()
                        }
                    }
                }
        } catch (e: Exception) {
            log("Error making the request: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                notificationChannelId
            ) else Notification.Builder(this)

        return builder
            .setContentTitle("Endless Service")
            .setContentText("This is your favorite endless service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }
}
