package com.reborntales.smoothor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs

class CalibrateActivity : AppCompatActivity() {

    var defaultValue = 0f
    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    private val gyroDataList = ArrayList<GyroData>()
    private val maxGyroDataList = ArrayList<Float>()
    private var nowTime = System.currentTimeMillis()
    private var timePassed = 0
    private var done = false
    private var x = 0.002f
    private var y = 0.002f
    private var z = 0.002f
    private var shouldAllowBack = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibrate)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        val countdown = findViewById<TextView>(R.id.countdown_text)
        val df = DecimalFormat("#.#")
        object : CountDownTimer(30000, 100) {
            override fun onTick(millisUntilFinished: Long) {
                countdown.text = "" + df.format(millisUntilFinished / 1000.0)
            }

            override fun onFinish() {
                countdown.text = "Done!"
            }
        }.start()
    }

    override fun onBackPressed() {
        if (shouldAllowBack) {
            super.onBackPressed()
        }
    }

    public override fun onResume() {
        super.onResume()
        sensorManager!!.registerListener(gyroListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    public override fun onStop() {
        super.onStop()
        stopListener()
    }

    private var gyroListener: SensorEventListener = object : SensorEventListener {

        override fun onAccuracyChanged(sensor: Sensor, acc: Int) {}

        override fun onSensorChanged(event: SensorEvent) { //Calculate time passed
            timePassed += (System.currentTimeMillis() - nowTime).toInt()
            nowTime = System.currentTimeMillis()
            if (timePassed > 30000) done = true
            //Calculate rotation values
            x = abs(abs(event.values[0]) - x)
            y = abs(abs(event.values[1]) - y)
            z = abs(abs(event.values[2]) - z)
            if (timePassed >= 100 && !done) {
                gyroDataList.add(GyroData(timePassed.toFloat(), x, y, z))
            }
            //When time is passed
            if (done) {
                addingMax()
                defaultValue = calculateAvg(maxGyroDataList)
                println(defaultValue)
                saveValue()
                stopListener()
                startActivity(Intent(this@CalibrateActivity, MainActivity::class.java))
            }
        }
    }

    fun stopListener() {
        sensorManager!!.unregisterListener(gyroListener, sensor)
        gyroDataList.clear()
        shouldAllowBack = true
    }

    fun addingMax() {
        for (i in gyroDataList.indices) {
            if (gyroDataList[i].getxRot() >= gyroDataList[i].getyRot() && gyroDataList[i].getxRot() >= gyroDataList[i].getzRot()) {
                maxGyroDataList.add(gyroDataList[i].getxRot())
            } else if (gyroDataList[i].getyRot() >= gyroDataList[i].getxRot() && gyroDataList[i].getyRot() > gyroDataList[i].getzRot()) {
                maxGyroDataList.add(gyroDataList[i].getyRot())
            } else if (gyroDataList[i].getzRot() >= gyroDataList[i].getyRot() && gyroDataList[i].getzRot() >= gyroDataList[i].getxRot()) {
                maxGyroDataList.add(gyroDataList[i].getzRot())
            }
        }
    }

    fun calculateAvg(array: ArrayList<Float>): Float {
        var sum = 0f
        for (i in array.indices) {
            sum = +array[i]
        }
        return sum / array.size
    }

    fun saveValue() {
        val settings = applicationContext.getSharedPreferences("preferences", 0)
        val editor = settings.edit()
        editor.putFloat("defaultValue", defaultValue)
        editor.apply()
    }
}