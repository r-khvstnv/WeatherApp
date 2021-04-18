package com.rkhvstnv.weatherapp.models

import java.io.Serializable

data class MainModel(
        val temp: Double,
        val feelsLike: Double,
        val temp_min: Double,
        val temp_max: Double,
        val pressure: Double,
        val humidity: Int) : Serializable {
}