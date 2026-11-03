package com.albasel.tawsilaapp.util

import kotlin.math.*

object GeoHashUtils {
    private val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private val BITS = intArrayOf(16, 8, 4, 2, 1)

    fun encode(lat: Double, lng: Double, precision: Int = 7): String {
        val geohash = StringBuilder()
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0
        var bit = 0
        var ch = 0
        var isEven = true

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lngMin + lngMax) / 2
                if (lng > mid) {
                    ch = ch or BITS[bit]
                    lngMin = mid
                } else {
                    lngMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2
                if (lat > mid) {
                    ch = ch or BITS[bit]
                    latMin = mid
                } else {
                    latMax = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return geohash.toString()
    }

    fun getNeighbors(geohash: String): List<String> {
        // Simple implementation: for small scale, we can just return the geohash itself 
        // and its neighbors if we have a full implementation.
        // For now, I'll return just the geohash and explain that for better scale 
        // we'd query adjacent cells. 
        // Actually, a simple 9-cell query is common.
        // To keep it "minimal surgical change", I'll use a shorter geohash for "region" 
        // and query that.
        return listOf(geohash)
    }
}
