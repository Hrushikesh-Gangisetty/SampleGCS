package com.example.aerogcsclone.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

/**
 * Utility class for generating polygon geofences around mission plans
 */
object GeofenceUtils {

    /**
     * Generates a polygon buffer around a list of waypoints
     * @param waypoints List of waypoints to create buffer around
     * @param bufferDistanceMeters Buffer distance in meters (default 5m as requested)
     * @return List of LatLng points forming the buffer polygon (always extends outward)
     */
    fun generatePolygonBuffer(waypoints: List<LatLng>, bufferDistanceMeters: Double = 5.0): List<LatLng> {
        if (waypoints.isEmpty()) return emptyList()

        // For a single point, create a circular buffer
        if (waypoints.size == 1) {
            return createCircularBuffer(waypoints.first(), bufferDistanceMeters)
        }

        // For multiple points, create convex hull first, then buffer outward
        val hull = convexHull(waypoints)
        if (hull.isEmpty()) return emptyList()

        return createOutwardPolygonBuffer(hull, bufferDistanceMeters)
    }

    /**
     * Creates a circular buffer around a single point
     */
    private fun createCircularBuffer(center: LatLng, radiusMeters: Double, numPoints: Int = 16): List<LatLng> {
        val points = mutableListOf<LatLng>()
        val earthRadius = 6371000.0 // Earth's radius in meters

        for (i in 0 until numPoints) {
            val angle = 2 * PI * i / numPoints
            val lat = center.latitude + (radiusMeters / earthRadius) * cos(angle) * 180 / PI
            val lon = center.longitude + (radiusMeters / (earthRadius * cos(center.latitude * PI / 180))) * sin(angle) * 180 / PI
            points.add(LatLng(lat, lon))
        }

        return points
    }

    /**
     * Creates an outward polygon buffer around a convex hull
     * This ensures the buffer always extends outside the original plan boundary
     */
    private fun createOutwardPolygonBuffer(hull: List<LatLng>, bufferDistanceMeters: Double): List<LatLng> {
        val bufferedPoints = mutableListOf<LatLng>()
        val earthRadius = 6371000.0 // Earth's radius in meters

        for (i in hull.indices) {
            val current = hull[i]
            val prev = hull[if (i == 0) hull.size - 1 else i - 1]
            val next = hull[if (i == hull.size - 1) 0 else i + 1]

            // Calculate outward normal vector at this vertex
            val outwardNormal = calculateOutwardNormal(prev, current, next)

            // Apply buffer distance in the outward direction
            val offsetLat = outwardNormal.first * (bufferDistanceMeters / earthRadius) * 180 / PI
            val offsetLon = outwardNormal.second * (bufferDistanceMeters / (earthRadius * cos(current.latitude * PI / 180))) * 180 / PI

            bufferedPoints.add(LatLng(
                current.latitude + offsetLat,
                current.longitude + offsetLon
            ))
        }

        return bufferedPoints
    }

    /**
     * Calculates the outward normal vector at a vertex
     * Returns a pair of (latDirection, lonDirection) normalized to unit length
     */
    private fun calculateOutwardNormal(prev: LatLng, current: LatLng, next: LatLng): Pair<Double, Double> {
        // Calculate edge vectors
        val edge1Lat = current.latitude - prev.latitude
        val edge1Lon = current.longitude - prev.longitude
        val edge2Lat = next.latitude - current.latitude
        val edge2Lon = next.longitude - current.longitude

        // Calculate average direction of the two edges
        val avgEdgeLat = (edge1Lat + edge2Lat) / 2.0
        val avgEdgeLon = (edge1Lon + edge2Lon) / 2.0

        // Calculate perpendicular vector (rotate 90 degrees counterclockwise)
        val perpLat = -avgEdgeLon
        val perpLon = avgEdgeLat

        // Normalize the perpendicular vector
        val length = sqrt(perpLat * perpLat + perpLon * perpLon)
        if (length == 0.0) return Pair(0.0, 0.0)

        val normalizedLat = perpLat / length
        val normalizedLon = perpLon / length

        // Determine if this is pointing inward or outward relative to the polygon centroid
        val centroid = calculateCentroid(listOf(prev, current, next))
        val toCentroidLat = centroid.latitude - current.latitude
        val toCentroidLon = centroid.longitude - current.longitude

        // If the normal points toward the centroid, flip it to point outward
        val dotProduct = normalizedLat * toCentroidLat + normalizedLon * toCentroidLon
        return if (dotProduct > 0) {
            Pair(-normalizedLat, -normalizedLon) // Flip to point outward
        } else {
            Pair(normalizedLat, normalizedLon) // Already pointing outward
        }
    }

    /**
     * Calculate the centroid of a list of points
     */
    private fun calculateCentroid(points: List<LatLng>): LatLng {
        val avgLat = points.map { it.latitude }.average()
        val avgLon = points.map { it.longitude }.average()
        return LatLng(avgLat, avgLon)
    }

    /**
     * Computes convex hull using Graham scan algorithm
     */
    private fun convexHull(points: List<LatLng>): List<LatLng> {
        if (points.size < 3) return points

        val sorted = points.sortedWith { a, b ->
            when {
                a.latitude < b.latitude -> -1
                a.latitude > b.latitude -> 1
                else -> a.longitude.compareTo(b.longitude)
            }
        }

        // Build lower hull
        val lower = mutableListOf<LatLng>()
        for (point in sorted) {
            while (lower.size >= 2 && crossProduct(lower[lower.size - 2], lower[lower.size - 1], point) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(point)
        }

        // Build upper hull
        val upper = mutableListOf<LatLng>()
        for (point in sorted.reversed()) {
            while (upper.size >= 2 && crossProduct(upper[upper.size - 2], upper[upper.size - 1], point) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(point)
        }

        // Remove last point of each half because it's repeated
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)

        return lower + upper
    }

    /**
     * Calculates cross product for convex hull algorithm
     */
    private fun crossProduct(o: LatLng, a: LatLng, b: LatLng): Double {
        return (a.latitude - o.latitude) * (b.longitude - o.longitude) -
               (a.longitude - o.longitude) * (b.latitude - o.latitude)
    }

    /**
     * Checks if a point is inside a polygon (returns true if inside or on boundary)
     * Uses ray casting algorithm
     */
    fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false
        var intersectCount = 0
        val n = polygon.size
        for (i in 0 until n) {
            val a = polygon[i]
            val b = polygon[(i + 1) % n]
            if (rayIntersectsSegment(point, a, b)) {
                intersectCount++
            }
        }
        return (intersectCount % 2 == 1) || isPointOnPolygonEdge(point, polygon)
    }

    // Helper: Ray-casting intersection
    private fun rayIntersectsSegment(p: LatLng, a: LatLng, b: LatLng): Boolean {
        val px = p.longitude
        val py = p.latitude
        val ax = a.longitude
        val ay = a.latitude
        val bx = b.longitude
        val by = b.latitude
        if (ay > by) return rayIntersectsSegment(p, b, a)
        if (py == ay || py == by) {
            // Move point slightly if exactly at vertex
            val tiny = 1e-10
            return rayIntersectsSegment(LatLng(py + tiny, px), a, b)
        }
        if (py > by || py < ay || ax > px && bx > px) return false
        if (ax < px && bx < px) return false
        val m = if (bx != ax) (by - ay) / (bx - ax) else Double.POSITIVE_INFINITY
        val x = if (m != 0.0) (py - ay) / m + ax else ax
        return x > px
    }

    // Helper: Check if point is on polygon edge
    private fun isPointOnPolygonEdge(point: LatLng, polygon: List<LatLng>, tolerance: Double = 1e-7): Boolean {
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            if (isPointOnSegment(point, a, b, tolerance)) return true
        }
        return false
    }

    // Helper: Check if point is on segment
    private fun isPointOnSegment(p: LatLng, a: LatLng, b: LatLng, tolerance: Double): Boolean {
        val cross = (p.latitude - a.latitude) * (b.longitude - a.longitude) - (p.longitude - a.longitude) * (b.latitude - a.latitude)
        if (abs(cross) > tolerance) return false
        val dot = (p.latitude - a.latitude) * (b.latitude - a.latitude) + (p.longitude - a.longitude) * (b.longitude - a.longitude)
        if (dot < 0) return false
        val squaredLen = (b.latitude - a.latitude) * (b.latitude - a.latitude) + (b.longitude - a.longitude) * (b.longitude - a.longitude)
        if (dot > squaredLen) return false
        return true
    }
}
