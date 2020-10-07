package com.reborntales.smoothor

class GyroData(private val time: Float, private val xRot: Float, private val yRot: Float, private val zRot: Float) {
    fun getxRot(): Float {
        return xRot
    }

    fun getyRot(): Float {
        return yRot
    }

    fun getzRot(): Float {
        return zRot
    }
}