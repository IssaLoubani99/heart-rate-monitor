package com.issa.loubani.heartratemonitor

import android.media.Image
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.otaliastudios.cameraview.controls.Flash
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private var isFlashOn = false
    private var averageIndex = 0
    private var averageArraySize = 4
    private var averageArray = IntArray(averageArraySize)
    private var processing = AtomicBoolean(false)
    private var startProcessing = false
    private var sumOfRedAvg = 0
    private var sumOfBlueAvg = 0
    private var redAvgList = ArrayList<Int>()
    private var blueAvgList = ArrayList<Int>()
    private var frameCounter = 0
    private var standardDevB = 0
    private var standardDevR = 0
    private var o2 = 0
    private var totalTimeInSecs: Double = 0.0
    private var currentType = TYPE.GREEN
    private var beatsIndex = 0
    private var beatsArraySize = 3
    private var beatsArray = IntArray(beatsArraySize)
    private var beats = 0.0
    private var startTime: Long = 0
    private var endTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // init camera
        camera.setLifecycleOwner(this)
        // add frame processor
        initFrameProcessor()
        // init handlers
        analyzeBtn.setOnClickListener {

            runOnUiThread {
                resetUI()
                toggleFlash()
            }
            startProcessing = true

        }
    }

    private fun initFrameProcessor() {
        camera.addFrameProcessor { frame ->
            // stage 1 : pre-processing
            Log.w("Processing@", "status: preprocessed")
            Log.w("Processing@", "start process: $startProcessing")
            if (startProcessing) {
                if (!processing.compareAndSet(false, true))
                    return@addFrameProcessor
            } else {
                return@addFrameProcessor
            }
            Log.w("Processing@", "status: ready to process")

            // init data holders
            val buffer = frame.getData<Image>().planes[0].buffer
            val data = buffer.toByteArray()
            val size = frame.size
            val width: Int = size.width
            val height: Int = size.height
            frameCounter++ // increase frame counter
            // get rgb average
            val rgbAvg = ImageProcessing.decodeYUV420SPtoRGBAvg(data.clone(), width, height)
            // get red avg
            val redAvg: Int = rgbAvg[ImageProcessing.RED]
            sumOfRedAvg += redAvg
            redAvgList.add(redAvg)
            // get blue avg
            val blueAvg: Int = rgbAvg[ImageProcessing.BLUE]
            sumOfBlueAvg += blueAvg
            blueAvgList.add(blueAvg)
            if (redAvg == 0 || redAvg == 255) {
                processing.set(false)
                return@addFrameProcessor
            }
            var averageArrayAvg = 0
            var averageArrayCnt = 0
            for (i in averageArray.indices) {
                if (averageArray[i] > 0) {
                    averageArrayAvg += averageArray[i]
                    averageArrayCnt++
                }
            }
            val rollingAverage =
                if (averageArrayCnt > 0) averageArrayAvg / averageArrayCnt else 0
            var newType: TYPE = currentType
            if (redAvg < rollingAverage) {
                newType = TYPE.RED
                if (newType !== currentType) {
                    beats++
                    Log.d("Beats@", "BEAT!! beats=$beats");
                }
            } else if (redAvg > rollingAverage) {
                newType = TYPE.GREEN
            }
            if (averageIndex == averageArraySize) averageIndex = 0
            averageArray[averageIndex] = redAvg
            averageIndex++
            // Transitioned from one state to another to the same
            if (newType !== currentType) {
                currentType = newType
                //image.postInvalidate()
            }
            endTime = System.currentTimeMillis()
            totalTimeInSecs = (endTime - startTime) / 1000.0
            Log.w("Time spend@", "time = $totalTimeInSecs")
            // update heart beat text view
            bpmTextView.text = (((beats / totalTimeInSecs) * 60)).toInt().toString()
            runOnUiThread {
                circularProgressBar.progress = totalTimeInSecs.toFloat()
            }
            // when 10 sec are passed
            if (totalTimeInSecs >= 10) {
                // frame.freeze()
                val bps: Double = beats / totalTimeInSecs
                val dpm = (bps * 60.0).toInt()
                if (dpm < 30 || dpm > 180) {
                    startTime = System.currentTimeMillis()
                    beats = 0.0
                    processing.set(false)
                    return@addFrameProcessor
                }
                Log.w("Processing@", "status: stage 2")
                if (beatsIndex == beatsArraySize) beatsIndex = 0
                beatsArray[beatsIndex] = dpm
                beatsIndex++
                var beatsArrayAvg = 0
                var beatsArrayCnt = 0
                for (i in beatsArray.indices) {
                    if (beatsArray[i] > 0) {
                        beatsArrayAvg += beatsArray[i]
                        beatsArrayCnt++
                    }
                }
                val beatsAvg = beatsArrayAvg / beatsArrayCnt
                // on finish
                onAnalysisFinished(beatsAvg)
                // clean for next analyses
                resetAnalysesValues()
            }

            processing.set(false)
        }
    }

    private fun onAnalysisFinished(heartRate: Int) {
        val o2 = calculateOxygenSaturation()
        val pressure = calculateBloodPressure(heartRate)

        runOnUiThread {
            oxygenSaturationTV.text = "Oxygen Saturation : $o2 %"
            bloodPresTV.text =
                "Blood Pressure : ${pressure[BloodPressure.SYSTOLIC_PRESSURE]}/${pressure[BloodPressure.DIASTOLIC_PRESSURE]}"
            toggleFlash()
        }
    }

    private fun resetUI() {
        circularProgressBar.progress = 0.0f
        bpmTextView.text = "--"
        heartView.setDurationBasedOnBPM(70)
    }

    private fun toggleFlash() {
        camera.flash = if (!isFlashOn) {
            Log.w("Flash@", "Flash on")
            Flash.TORCH
        } else {
            Log.w("Flash@", "Flash off")
            Flash.OFF
        }
        isFlashOn = !isFlashOn
    }

    private fun calculateOxygenSaturation(): Int {
        val meanR: Double = (sumOfRedAvg / frameCounter).toDouble()
        val meanB: Double = (sumOfBlueAvg / frameCounter).toDouble()


        for (i in 0 until (frameCounter - 1)) {
            val bufferB: Double = blueAvgList[i].toDouble()
            standardDevB =
                (standardDevB + (bufferB - meanB) * (bufferB - meanB)).roundToInt()
            val bufferR: Double = redAvgList[i].toDouble()
            standardDevR =
                (standardDevR + (bufferR - meanR) * (bufferR - meanR)).roundToInt()
        }

        val varr: Double = sqrt((standardDevR / (frameCounter - 1)).toDouble())
        val varb: Double = sqrt((standardDevB / (frameCounter - 1)).toDouble())

        val R = varr / meanR / (varb / meanB)

        val spo2 = 100 - 5 * R
        o2 = spo2.toInt()
        return o2
    }

    private fun calculateBloodPressure(heartRate: Int): Array<Int> {
        /*
        TODO Get the values of gender and the rest of teh default either by a form to the user or by using build-in sensors
         */
        // DEFAULTS FOR NOW
        val gender = "Male"
        val personWeight = 130 * 2.20462
        val personHeight = 190 * 0.393701
        val position = "Sitting"
        val age = 25
        val r = 18.5
        // END DEFAULT
        val Q: Double =
            if (gender.equals("Male", ignoreCase = true) || gender.equals(
                    "M",
                    ignoreCase = true
                )
            ) 5.0 else 4.5 // Liters per minute of blood through heart
        val ejectionTime =
            if (!position.equals(
                    "Laying Down",
                    ignoreCase = true
                )
            ) 386 - 1.64 * heartRate else 364.5 - 1.23 * heartRate // WAS ()?376-1.64*heartRate:354.5-1.23*heartRate; // ()?sitting:supine
        val bodySurfaceArea =
            0.007184 * personWeight.toDouble().pow(0.425) * personHeight.toDouble()
                .pow(0.725)
        val strokeVolume =
            -6.6 + 0.25 * (ejectionTime - 35) - 0.62 * heartRate + 40.4 * bodySurfaceArea - 0.51 * age // Volume of blood pumped from heart in one beat
        val pulsePressure =
            abs(strokeVolume / (0.013 * personWeight - 0.007 * age - 0.004 * heartRate + 1.307))
        val meanPulsePressure = Q * r
        val systolicPressure = (meanPulsePressure + 4.5 / 3 * pulsePressure).toInt()
        val diastolicPressure = (meanPulsePressure - pulsePressure / 3).toInt()

        Log.w("Blood Pressure", "$systolicPressure / $diastolicPressure")
        return arrayOf(systolicPressure, diastolicPressure)
    }

    class BloodPressure {
        companion object {
            const val SYSTOLIC_PRESSURE: Int = 0
            const val DIASTOLIC_PRESSURE: Int = 1
        }
    }

    private enum class TYPE {
        GREEN, RED
    }

    private fun resetAnalysesValues() {
        //isFlashOn = false
        averageIndex = 0
        averageArraySize = 4
        averageArray = IntArray(averageArraySize)
        // processing = AtomicBoolean(false)
        startProcessing = false
        sumOfRedAvg = 0
        sumOfBlueAvg = 0
        redAvgList.clear()
        blueAvgList.clear()
        frameCounter = 0
        standardDevB = 0
        standardDevR = 0
        o2 = 0
        totalTimeInSecs = 0.0
        currentType = TYPE.GREEN
        beatsIndex = 0
        beatsArraySize = 3
        beatsArray = IntArray(beatsArraySize)
        beats = 0.0
        startTime = 0
        endTime = 0
        circularProgressBar.progress = 0.0f
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

}