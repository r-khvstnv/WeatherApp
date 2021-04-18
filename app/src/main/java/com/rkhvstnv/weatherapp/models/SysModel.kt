package com.rkhvstnv.weatherapp.models

import java.io.Serializable

data class SysModel(
        val type: Int,
        val id: Int,
        val message: Double,
        val country: String,
        val sunrise: Int,
        val sunset: Int) : Serializable {
}