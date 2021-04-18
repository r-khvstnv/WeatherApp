package com.rkhvstnv.weatherapp.models

import java.io.Serializable

data class MainModel(
        val temp: Double,
        val feelsLike: Double,
        val tempMin: Double,
        val tempMax: Double,
        val pressure: Int,
        val humidity: Int) : Serializable {
}