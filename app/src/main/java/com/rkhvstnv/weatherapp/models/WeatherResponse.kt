package com.rkhvstnv.weatherapp.models

import java.io.Serializable

data class WeatherResponse(
        val coord: CoordModel,
        val weather: List<WeatherModel>,
        val base: String,
        val main: MainModel,
        val visibility: Int,
        val wind: WindModel,
        val clouds: CloudsModel,
        val dt: Int,
        val sys: SysModel,
        val timezone: Int,
        val id: Int,
        val name: String,
        val cod: Int) : Serializable{
}