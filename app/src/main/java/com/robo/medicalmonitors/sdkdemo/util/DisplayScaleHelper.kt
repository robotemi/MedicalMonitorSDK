package com.robo.medicalmonitors.sdkdemo.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import kotlin.math.min
import kotlin.math.roundToInt

object DisplayScaleHelper {
    private const val DISPLAY_SCALE = 0.85f
    private const val FIXED_FONT_SCALE = 1.0f

    fun wrap(context: Context): Context {
        val configuration = adjustedConfiguration(context.resources) ?: return context
        return context.createConfigurationContext(configuration)
    }

    fun applyToResources(resources: Resources) {
        val configuration = adjustedConfiguration(resources) ?: return
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    private fun adjustedConfiguration(resources: Resources): Configuration? {
        val configuration = Configuration(resources.configuration)
        val stableDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE
            .takeIf { it > 0 }
            ?: configuration.densityDpi.takeIf { it > 0 }
            ?: resources.displayMetrics.densityDpi
        val targetDensityDpi = (stableDensityDpi * DISPLAY_SCALE)
            .roundToInt()
            .coerceAtLeast(DisplayMetrics.DENSITY_LOW)
        val widthDp = (resources.displayMetrics.widthPixels * DisplayMetrics.DENSITY_DEFAULT.toFloat() / targetDensityDpi)
            .roundToInt()
        val heightDp = (resources.displayMetrics.heightPixels * DisplayMetrics.DENSITY_DEFAULT.toFloat() / targetDensityDpi)
            .roundToInt()

        var changed = false
        if (configuration.densityDpi != targetDensityDpi) {
            configuration.densityDpi = targetDensityDpi
            changed = true
        }
        if (configuration.screenWidthDp != widthDp) {
            configuration.screenWidthDp = widthDp
            changed = true
        }
        if (configuration.screenHeightDp != heightDp) {
            configuration.screenHeightDp = heightDp
            changed = true
        }
        val smallestWidthDp = min(widthDp, heightDp)
        if (configuration.smallestScreenWidthDp != smallestWidthDp) {
            configuration.smallestScreenWidthDp = smallestWidthDp
            changed = true
        }
        if (configuration.fontScale != FIXED_FONT_SCALE) {
            configuration.fontScale = FIXED_FONT_SCALE
            changed = true
        }
        return configuration.takeIf { changed }
    }
}
