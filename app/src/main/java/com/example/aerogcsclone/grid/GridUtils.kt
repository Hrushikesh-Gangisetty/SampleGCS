package com.example.aerogcsclone.grid

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.util.Locale
import kotlin.math.*

/**
 * Utility functions for grid calculations
 */
object GridUtils {

    /**
     * Calculate distance between two points using Haversine formula
     * @param a First point
     * @param b Second point
     * @return Distance in meters
     */
    fun haversineDistance(a: LatLng, b: LatLng): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val aHarv = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(aHarv), sqrt(1 - aHarv))
        return R * c
    }

    /**
     * Move a point by dx, dy meters (approximate for small distances)
     * @param point Original point
     * @param dx Distance to move east (meters)
     * @param dy Distance to move north (meters)
     * @return New point
     */
    fun moveLatLng(point: LatLng, dx: Double, dy: Double): LatLng {
        val dLat = dy / 111111.0 // Approximate meters per degree latitude
        val dLng = dx / (111111.0 * cos(Math.toRadians(point.latitude)))
        return LatLng(point.latitude + dLat, point.longitude + dLng)
    }

    /**
     * Calculate the bearing (angle) from point A to point B
     * @param from Starting point
     * @param to Ending point
     * @return Bearing in degrees (0-360, where 0 = North)
     */
    fun calculateBearing(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }

    /**
     * Find the centroid (center) of a polygon
     * @param polygon List of points defining the polygon
     * @return Center point
     */
    fun calculatePolygonCenter(polygon: List<LatLng>): LatLng {
        var lat = 0.0
        var lng = 0.0

        polygon.forEach { point ->
            lat += point.latitude
            lng += point.longitude
        }

        return LatLng(lat / polygon.size, lng / polygon.size)
    }

    /**
     * Calculate the bounding box of a polygon
     * @param polygon List of points
     * @return Pair of (southwest corner, northeast corner)
     */
    fun calculateBoundingBox(polygon: List<LatLng>): Pair<LatLng, LatLng> {
        val minLat = polygon.minOf { it.latitude }
        val maxLat = polygon.maxOf { it.latitude }
        val minLng = polygon.minOf { it.longitude }
        val maxLng = polygon.maxOf { it.longitude }

        return Pair(
            LatLng(minLat, minLng), // Southwest
            LatLng(maxLat, maxLng)  // Northeast
        )
    }

    /**
     * Find the angle of the longest side of a polygon (for auto grid angle)
     * @param polygon List of points
     * @return Angle in degrees
     */
    fun getAngleOfLongestSide(polygon: List<LatLng>): Double {
        if (polygon.size < 2) return 0.0

        var maxDistance = 0.0
        var longestSideAngle = 0.0

        for (i in polygon.indices) {
            val current = polygon[i]
            val next = polygon[(i + 1) % polygon.size]
            val distance = haversineDistance(current, next)

            if (distance > maxDistance) {
                maxDistance = distance
                longestSideAngle = calculateBearing(current, next)
            }
        }

        return longestSideAngle
    }

    /**
     * Check if a point is inside a polygon using ray casting algorithm
     * @param point Point to check
     * @param polygon Polygon vertices
     * @return True if point is inside polygon
     */
    fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude

            if (((yi > point.latitude) != (yj > point.latitude)) &&
                (point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi)) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    /**
     * Calculate the area of a polygon in square meters using SphericalUtil.
     * @param polygon List of points
     * @return Area in square meters
     */
    fun calculatePolygonArea(polygon: List<LatLng>): Double {
        if (polygon.size < 3) return 0.0
        return SphericalUtil.computeArea(polygon)
    }

    /**
     * Calculates and formats the area of a polygon into ft², acres, or mi².
     * @param polygon The list of LatLng points defining the polygon.
     * @return A formatted string representing the area.
     */
    fun calculateAndFormatPolygonArea(polygon: List<LatLng>): String {
        if (polygon.size < 3) return "0 ft²"

        val areaInSqMeters = SphericalUtil.computeArea(polygon)
        val areaInSqFeet = areaInSqMeters * 10.7639

        // Conversion constants
        val ft2PerAcre = 43560.0
        val acresPerSqMi = 640.0

        // Thresholds
        val ft2Threshold = 21780.0 // ft²
        val acreThreshold = 640.0   // acres

        return when {
            areaInSqFeet < ft2Threshold -> {
                String.format(Locale.US, "%,.0f ft²", areaInSqFeet)
            }
            else -> {
                val areaInAcres = areaInSqFeet / ft2PerAcre
                if (areaInAcres < acreThreshold) {
                    String.format(Locale.US, "%.1f acres", areaInAcres)
                } else {
                    val areaInSqMiles = areaInAcres / acresPerSqMi
                    String.format(Locale.US, "%.1f mi²", areaInSqMiles)
                }
            }
        }
    }
}
