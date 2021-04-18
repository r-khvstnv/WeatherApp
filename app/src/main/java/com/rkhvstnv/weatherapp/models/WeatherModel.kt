package com.rkhvstnv.weatherapp.models

import java.io.Serializable

class WeatherModel(
        val id: Int,
        val main: String,
        val description: String,
        val icon: String) : Serializable {
}