package com.example.lovale2.services

import android.content.Intent

object ScreenCaptureHolder {
    var resultCode: Int = 0
    var resultData: Intent? = null
    var isCapturing: Boolean = false
}