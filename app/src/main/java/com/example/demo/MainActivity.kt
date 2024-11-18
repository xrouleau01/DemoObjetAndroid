package com.example.demo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

val CHANNEL_ID = "Channel1"

class MainActivity : ComponentActivity() {
    val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        creerCanal()
        demanderPermissionNotification()

        val titre = "Ma notification"
        val texte = "Bonjour le monde"
        val notificationID = 1
        afficherNotification(notificationID, titre, texte)

        executerAsynchrone()

        // Créer une requête pour exécuter la tâche une seule fois
        val myWorkRequest = OneTimeWorkRequest.Builder(MyWorker::class.java).build()

        // Planifier la tâche avec WorkManager
        WorkManager.getInstance(this).enqueue(myWorkRequest)

        // Préparer la requête au Worker
        val requete = PeriodicWorkRequest.Builder(
            MyWorker::class.java
            , 15, TimeUnit.MINUTES
        ).build()

        // Lancer la commande au Worker pour qu'il l'exécute
        WorkManager.getInstance(this).enqueue(requete)

        val thread = Thread {
            val jsonData = getData("http://192.168.1.124:11111/")
            handler.post{
                try{
                    Log.d("JSON", jsonData?: "Données nulles")
                    if (jsonData != null) {
                        val obj = JSONObject(jsonData)
                        val temperature = obj.getInt("temperature")
                        val humidity = obj.getInt("humidite")
                        val txtTemperature: TextView = findViewById(R.id.txtTemperature)
                        val txtHumidity: TextView = findViewById(R.id.txtHumidite)
                        txtTemperature.text = temperature.toString()
                        txtHumidity.text = humidity.toString()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("ERREUR", e.toString())
                }
            }
        }
        thread.start()

        val btnChauffage = findViewById<Button>(R.id.btnChauffage)
        btnChauffage.setOnClickListener {
            val thread = Thread {
                sendPost("http://192.168.1.124:11111", "{\"heat\":1}")
            }
            thread.start()
        }
    }

    private fun getData(stUrl: String): String?{
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(stUrl)
            .build()
        return try{
            client.newCall(request).execute().use{ response: Response ->
                if(!response.isSuccessful){
                    Log.e("ERREUR", "Erreur de connection`${response.code}")
                    null
                }else{
                    Log.i("REQUETE", "Done")
                    response.body?.string()
                }
            }
        }
        catch(e: Exception){
            e.printStackTrace()
            Log.e("ERREUR", e.toString())
            null
        }
    }

    private fun sendPost(stUrl: String, jsonMsg: String) {
        try {
            val url = URL(stUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.doInput = true
            DataOutputStream(conn.outputStream).use { os ->
                os.writeBytes(jsonMsg)
                os.flush()
            }
            Log.d("STATUS", conn.responseCode.toString())
            Log.d("MSG", conn.responseMessage)
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ERREUR", e.message?: "Erreur inconnue")
        }
    }

    private fun executerAsynchrone(){
        val thread = Thread {
            Log.i("MON_THREAD", "Affichage d'un log aynchrone")

            val dataRequest = getData("https://google.com/")

            handler.post{
                Log.i("MON_THREAD", "Faire la requete")
                val tvSortie = findViewById<TextView>(R.id.tv_test)
                tvSortie.text = dataRequest
            }
        }
        thread.start()
    }

    private fun demanderPermissionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
                if (!result) {
                    Toast.makeText(this, "La permission n'a pas été accordée", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun creerCanal() {
        val channelName = "Channel1"
        val channelDescription = "Canal de notifications"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
            }
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun afficherNotification(id : Int, titre: String, texte: String) {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(titre)
            .setContentText(texte)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(applicationContext)) {
            if (ActivityCompat.checkSelfPermission(
                applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
                ) {
                notify(id, builder.build())
            }
        }
    }
}

class MyWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams){
    override fun doWork(): Result{
        Log.i("MON_WORKER", "Affichage d'un log en arrière-plan")
        return Result.success()
    }
}