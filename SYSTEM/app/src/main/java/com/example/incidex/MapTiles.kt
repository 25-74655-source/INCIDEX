package com.example.incidex

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object MapTiles {

    private const val KEY = "Y2qwehmY9hOM8qQ4IGPq"

    val STREETS: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "MapTilerStreets", 0, 20, 256, ".png",
        arrayOf("https://api.maptiler.com/maps/streets-v2/256/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl +
                    MapTileIndex.getZoom(pMapTileIndex) + "/" +
                    MapTileIndex.getX(pMapTileIndex) + "/" +
                    MapTileIndex.getY(pMapTileIndex) + ".png?key=" + KEY
        }
    }
}

fun mapPin(
    context: Context,
    color: Int = Color.parseColor("#1E3A8A"),
    sizeDp: Int = 40
): Drawable {
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    val vector = ContextCompat.getDrawable(context, R.drawable.ic_location)!!.mutate()
    vector.setTint(color)
    return BitmapDrawable(context.resources, vector.toBitmap(px, px))
}