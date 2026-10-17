package com.lingion.sleepy.data.jw

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.abs

/** Local point-in-polygon lookup. Never guesses a city from its nearest centre. */
class OfflineSchoolCityIndex private constructor(private val areas: List<Area>) {
    private data class Ring(val coordinates: IntArray) {
        val minX: Int
        val maxX: Int
        val minY: Int
        val maxY: Int
        init {
            var left = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var bottom = Int.MAX_VALUE
            var top = Int.MIN_VALUE
            for (point in coordinates.indices step 2) {
                left = minOf(left, coordinates[point])
                right = maxOf(right, coordinates[point])
                bottom = minOf(bottom, coordinates[point + 1])
                top = maxOf(top, coordinates[point + 1])
            }
            minX = left; maxX = right; minY = bottom; maxY = top
        }

        fun contains(x: Double, y: Double): Boolean {
            if (x < minX || x > maxX || y < minY || y > maxY) return false
            var inside = false
            var previous = coordinates.size - 2
            for (current in coordinates.indices step 2) {
                val ax = coordinates[previous]
                val ay = coordinates[previous + 1]
                val bx = coordinates[current]
                val by = coordinates[current + 1]
                val cross = (x - ax) * (by - ay) - (y - ay) * (bx - ax)
                if (abs(cross) < 1e-6 && x >= minOf(ax, bx) && x <= maxOf(ax, bx) &&
                    y >= minOf(ay, by) && y <= maxOf(ay, by)) return true
                if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside
                previous = current
            }
            return inside
        }
    }

    private data class Polygon(val rings: List<Ring>) {
        fun contains(x: Double, y: Double): Boolean {
            if (!rings.first().contains(x, y)) return false
            for (hole in 1 until rings.size) if (rings[hole].contains(x, y)) return false
            return true
        }
    }

    private data class Area(val name: String, val polygons: List<Polygon>)

    internal val cityNames: Set<String> get() = areas.map { it.name }.filterNot { it in nonCityAreas }.toSet()

    fun cityAt(latitude: Double, longitude: Double): String? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0) return null
        return areas.firstOrNull { area ->
            area.name !in nonCityAreas &&
                area.polygons.any { it.contains(longitude * 10000, latitude * 10000) }
        }?.name
    }

    companion object {
        // Never expose a province outline if an older asset is accidentally supplied.
        private val nonCityAreas = setOf("台湾省")

        /** Exact metadata locations without a bundled city polygon; never guess their city.
         * This explicit snapshot limitation is not generated from unrecognized names.
         * Factual cities remain available for school search and city matching.
         */
        internal val unsupportedCityReasons: Map<String, String> =
            mapOf("新星市" to "The bundled boundary snapshot has no Xinxing city polygon.")

        /** SCB1 is streamed straight into scaled integer rings, with no JSON tree or point objects.
         * Caller owns the stream. Cancellation is checked at every ring and every 256 points.
         */
        fun parse(stream: InputStream, checkCancelled: () -> Unit = {}): OfflineSchoolCityIndex {
            val input = DataInputStream(stream.buffered())
            checkCancelled()
            require(input.readInt() == 0x53434231) { "Unsupported city boundary format" }
            fun count(minimum: Int, maximum: Int): Int = input.readInt().also {
                require(it in minimum..maximum) { "Invalid city boundary count" }
            }
            var totalPoints = 0
            val areas = ArrayList<Area>()
            repeat(count(1, 10000)) {
                checkCancelled()
                val nameBytes = ByteArray(count(1, 256))
                input.readFully(nameBytes)
                val name = nameBytes.toString(Charsets.UTF_8)
                require(name.isNotBlank()) { "Missing city name" }
                val polygons = ArrayList<Polygon>()
                repeat(count(1, 10000)) {
                    val rings = ArrayList<Ring>()
                    repeat(count(1, 10000)) {
                        checkCancelled()
                        val points = count(3, 1000000)
                        totalPoints += points
                        require(totalPoints <= 2000000) { "City boundary data too large" }
                        val coordinates = IntArray(points * 2)
                        repeat(points) { point ->
                            if (point % 256 == 0) checkCancelled()
                            val longitude = input.readInt()
                            val latitude = input.readInt()
                            require(longitude in -1800000..1800000 && latitude in -900000..900000) {
                                "Invalid city boundary coordinate"
                            }
                            coordinates[point * 2] = longitude
                            coordinates[point * 2 + 1] = latitude
                        }
                        rings.add(Ring(coordinates))
                    }
                    polygons.add(Polygon(rings))
                }
                areas.add(Area(name, polygons))
            }
            require(input.read() == -1) { "Trailing city boundary data" }
            checkCancelled()
            return OfflineSchoolCityIndex(areas)
        }
    }
}
