package com.albasel.tawsilaapp.utils

import android.content.Context
import android.graphics.*
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import kotlin.math.sqrt

object MapMarkerUtils {

    fun createLocationPinIcon(context: Context, color: Int): Icon {
        val width = 56 // Reduced from 80 (~70%)
        val height = 70 // Reduced from 100 (~70%)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Shadow - bottom circle
        paint.color = Color.parseColor("#40000000")
        canvas.drawCircle(width / 2f, height - 7f, 10f, paint)

        // Main pin body (Teardrop shape)
        val path = Path()
        val radius = width / 2.2f
        val centerX = width / 2f
        val centerY = radius + 3f
        
        path.arcTo(RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius), 150f, 240f)
        path.lineTo(centerX, height - 7f)
        path.close()

        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(path, paint)

        // White inner circle
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, radius / 2f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        paint.alpha = 150
        canvas.drawPath(path, paint)

        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createBlueUserLocationDotIcon(context: Context): Icon {
        val size = 110
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val centerX = size / 2f
        val centerY = size / 2f

        // 1. Light translucent blue accuracy aura circle
        paint.color = Color.parseColor("#331A73E8")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, 48f, paint)

        // 2. White outer disc with subtle drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        shadowPaint.color = Color.parseColor("#25000000")
        shadowPaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY + 2f, 20f, shadowPaint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, 19f, paint)

        // 3. Vibrant Google Blue central dot
        paint.color = Color.parseColor("#1A73E8")
        canvas.drawCircle(centerX, centerY, 13f, paint)

        // 4. White inner ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 13f, paint)

        // 5. Blue direction pointer triangle at top
        val arrowPath = Path()
        arrowPath.moveTo(centerX, centerY - 24f)
        arrowPath.lineTo(centerX - 7f, centerY - 13f)
        arrowPath.lineTo(centerX + 7f, centerY - 13f)
        arrowPath.close()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1967D2")
        canvas.drawPath(arrowPath, paint)

        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createColoredUserLocationDotIcon(context: Context, mainColor: Int): Icon {
        val size = 110
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val centerX = size / 2f
        val centerY = size / 2f

        // 1. Light translucent aura circle matching mainColor
        val alphaColor = Color.argb(45, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor))
        paint.color = alphaColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, 48f, paint)

        // 2. White outer disc with subtle drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        shadowPaint.color = Color.parseColor("#25000000")
        shadowPaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY + 2f, 20f, shadowPaint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, 19f, paint)

        // 3. Central colored dot
        paint.color = mainColor
        canvas.drawCircle(centerX, centerY, 13f, paint)

        // 4. White inner ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 13f, paint)

        // 5. Direction pointer triangle at top
        val arrowPath = Path()
        arrowPath.moveTo(centerX, centerY - 24f)
        arrowPath.lineTo(centerX - 7f, centerY - 13f)
        arrowPath.lineTo(centerX + 7f, centerY - 13f)
        arrowPath.close()

        paint.style = Paint.Style.FILL
        paint.color = mainColor
        canvas.drawPath(arrowPath, paint)

        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createFlagIcon(context: Context, color: Int): Icon {
        val width = 100
        val height = 120
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Shadow - bottom oval
        paint.color = Color.parseColor("#40000000")
        canvas.drawOval(20f, height - 25f, width - 20f, height - 5f, paint)

        // Pole
        paint.color = Color.parseColor("#424242") // Dark gray
        paint.strokeWidth = 6f
        canvas.drawLine(width / 2f, height - 15f, width / 2f, 30f, paint)

        // Flag body
        val path = Path()
        path.moveTo(width / 2f, 20f)
        path.lineTo(width - 10f, 40f)
        path.lineTo(width - 10f, 80f)
        path.lineTo(width / 2f, 100f)
        path.close()

        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(path, paint)

        // 3D effect - lighter top/left part
        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        lightPaint.style = Paint.Style.STROKE
        lightPaint.strokeWidth = 2f
        lightPaint.color = Color.WHITE
        lightPaint.alpha = 100
        canvas.drawPath(path, lightPaint)

        // Small ball on top of the pole
        paint.color = Color.parseColor("#BDBDBD")
        canvas.drawCircle(width / 2f, 20f, 6f, paint)

        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    private val iconCache = mutableMapOf<String, Icon>()
    private val iconWithLabelCache = mutableMapOf<String, Icon>()

    fun resolveCategoryKey(category: String?, label: String?): String {
        val combined = "${category ?: ""} ${label ?: ""}".lowercase()
        return when {
            combined.contains("university") || combined.contains("education") || combined.contains("school") || combined.contains("جامعة") || combined.contains("مدرسة") || combined.contains("كلية") || combined.contains("أكاديمية") || combined.contains("معهد") -> "education"
            combined.contains("transport") || combined.contains("station") || combined.contains("موقف") || combined.contains("محطة") || combined.contains("مطار") || combined.contains("سرفيس") -> "transport"
            combined.contains("residential") || combined.contains("compound") || combined.contains("دار") || combined.contains("سكن") || combined.contains("جنة") || combined.contains("ديار") || combined.contains("كمباوند") -> "residential"
            combined.contains("health") || combined.contains("hospital") || combined.contains("medical") || combined.contains("مستشفى") || combined.contains("مركز طبي") || combined.contains("علاج") -> "health"
            combined.contains("mosque") || combined.contains("church") || combined.contains("مسجد") || combined.contains("جامع") || combined.contains("كنيسة") || combined.contains("ديني") -> "mosque"
            combined.contains("mall") || combined.contains("market") || combined.contains("pharmacy") || combined.contains("سوق") || combined.contains("مول") || combined.contains("صيدلية") || combined.contains("سوبر ماركت") || combined.contains("هايبر") -> "shopping"
            combined.contains("food") || combined.contains("restaurant") || combined.contains("مطعم") || combined.contains("حلوى") || combined.contains("حلواني") || combined.contains("أغذية") || combined.contains("مأكولات") -> "food"
            combined.contains("cafe") || combined.contains("coffee") || combined.contains("كافيه") || combined.contains("قهوة") || combined.contains("مقهى") || combined.contains("ستارباكس") || combined.contains("كوستا") -> "cafe"
            combined.contains("beach") || combined.contains("park") || combined.contains("leisure") || combined.contains("شاطئ") || combined.contains("حديقة") || combined.contains("اللسان") || combined.contains("فنار") || combined.contains("شاليه") -> "beach"
            combined.contains("sports") || combined.contains("club") || combined.contains("نادي") || combined.contains("ملعب") || combined.contains("رياضي") -> "sports"
            else -> "government"
        }
    }

    fun getCategoryColor(category: String?, label: String? = null): Int {
        return when (resolveCategoryKey(category, label)) {
            "education" -> Color.parseColor("#1565C0") // Royal Blue
            "transport" -> Color.parseColor("#283593") // Transit Navy
            "residential" -> Color.parseColor("#C5A059") // Muted Gold
            "health" -> Color.parseColor("#D32F2F") // Emergency Red
            "mosque" -> Color.parseColor("#2E7D32") // Islamic Green
            "shopping" -> Color.parseColor("#00695C") // Teal
            "food" -> Color.parseColor("#E65100") // Warm Orange
            "cafe" -> Color.parseColor("#4E342E") // Coffee Brown
            "beach" -> Color.parseColor("#00838F") // Marine Blue
            "sports" -> Color.parseColor("#388E3C") // Sports Green
            else -> Color.parseColor("#37474F") // Government Gray
        }
    }

    fun getCategoryIconWithLabel(context: Context, category: String?, label: String): Icon {
        val cacheKey = "${(category ?: "generic").lowercase()}_$label"
        return iconWithLabelCache.getOrPut(cacheKey) {
            val baseIcon = getCategoryIcon(context, category, label)
            val iconBitmap = baseIcon.bitmap ?: return@getOrPut baseIcon
            val categoryColor = getCategoryColor(category, label)
            
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.textSize = 13f
            paint.typeface = Typeface.DEFAULT_BOLD
            
            val displayLabel = if (label.length > 15) label.take(14) + ".." else label
            val textWidth = paint.measureText(displayLabel)
            
            val paddingX = 6f
            val pillWidth = textWidth + (paddingX * 2)
            val pillHeight = 22f
            
            val canvasWidth = maxOf(iconBitmap.width.toFloat(), pillWidth).toInt() + 4
            val canvasHeight = iconBitmap.height + pillHeight.toInt() + 4
            
            val combined = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(combined)
            
            // 1. Draw Icon centered at top
            val iconLeft = (canvasWidth - iconBitmap.width) / 2f
            canvas.drawBitmap(iconBitmap, iconLeft, 0f, null)
            
            // 2. Draw white pill background for label BELOW icon
            val pillLeft = (canvasWidth - pillWidth) / 2f
            val pillTop = iconBitmap.height.toFloat() + 1f
            val pillRight = pillLeft + pillWidth
            val pillBottom = pillTop + pillHeight
            val labelRect = RectF(pillLeft, pillTop, pillRight, pillBottom)
            
            // Pill background - Solid White with subtle shadow
            paint.color = Color.parseColor("#20000000")
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(RectF(pillLeft, pillTop + 1.5f, pillRight, pillBottom + 1.5f), 6f, 6f, paint)

            paint.color = Color.WHITE
            canvas.drawRoundRect(labelRect, 6f, 6f, paint)
            
            // Pill border - Category Base Color
            paint.color = categoryColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRoundRect(labelRect, 6f, 6f, paint)
            
            // 3. Draw Text inside pill - Dark Text
            paint.color = Color.parseColor("#111111")
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(displayLabel, canvasWidth / 2f, pillTop + 15f, paint)
            
            IconFactory.getInstance(context).fromBitmap(combined)
        }
    }

    fun getCategoryIcon(context: Context, category: String?, label: String? = null): Icon {
        val key = resolveCategoryKey(category, label)
        return iconCache.getOrPut(key) {
            when (key) {
                "education" -> createEducationIcon(context)
                "transport" -> createBusIcon(context)
                "residential" -> createResidentialPinIcon(context)
                "health" -> createHospitalIcon(context)
                "mosque" -> createMosqueIcon(context)
                "shopping" -> createShoppingIcon(context)
                "food" -> createRestaurantIcon(context)
                "cafe" -> createCoffeeIcon(context)
                "beach" -> createBeachIcon(context)
                "sports" -> createSportsIcon(context)
                else -> createGovernmentIcon(context)
            }
        }
    }

    private fun create2XIconBitmap(size: Int = 64, emojiSymbol: String): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cx = size / 2f
        val cy = size / 2f

        // Draw 2X Glyph Symbol directly (No circle / no bubble background)
        paint.style = Paint.Style.FILL
        paint.textSize = (size * 0.85f) // 2X larger icon
        paint.textAlign = Paint.Align.CENTER
        
        val fontMetrics = paint.fontMetrics
        val baseline = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(emojiSymbol, cx, baseline, paint)

        return bitmap
    }

    fun createBusIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🚌")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createResidentialPinIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "📍")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createCoffeeIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "☕")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createBeachIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🏖")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createMosqueIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🕌")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createSportsIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "⚽")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createHospitalIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🏥")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createRestaurantIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🍴")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createEducationIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🎓")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createShoppingIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🛒")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createGasIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "⛽")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createGovernmentIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🏛")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createDistrictIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "🏙")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createFamousPlaceIcon(context: Context): Icon {
        val bitmap = create2XIconBitmap(64, "⭐")
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createTollGateIcon(context: Context): Icon {
        val size = 60
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Blue circle
        paint.color = Color.parseColor("#1976D2")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // White border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)

        // "$" or icon for toll
        paint.style = Paint.Style.FILL
        paint.textSize = 30f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("P", size / 2f, size / 2f + 10f, paint)

        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    fun createNavigationIcon(context: Context, scale: Float = 1.0f): Icon {
        val baseSize = 80
        val size = (baseSize * scale.coerceAtLeast(0.5f)).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val centerX = size / 2f
        val centerY = size / 2f

        // Shadow/White Base
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, size / 2.2f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * scale
        paint.color = Color.LTGRAY
        canvas.drawCircle(centerX, centerY, size / 2.2f, paint)

        // Blue Arrow
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2196F3")
        val path = Path()
        path.moveTo(centerX, size * 0.25f) // Top
        path.lineTo(size * 0.7f, size * 0.75f) // Bottom Right
        path.lineTo(centerX, size * 0.65f) // Inner Notch
        path.lineTo(size * 0.3f, size * 0.75f) // Bottom Left
        path.close()
        canvas.drawPath(path, paint)

        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    private var baseLocationBitmap: Bitmap? = null
    private val bearingIconCache = mutableMapOf<Int, Icon>()

    fun createNavigationArrowIcon(context: Context, bearing: Float, isDarkMode: Boolean): Icon {
        val roundedBearing = ((bearing % 360 + 360) % 360).toInt()
        
        return bearingIconCache.getOrPut(roundedBearing) {
            val baseBmp = baseLocationBitmap ?: run {
                val resId = context.resources.getIdentifier("location", "drawable", context.packageName)
                val loaded = if (resId != 0) {
                    try { BitmapFactory.decodeResource(context.resources, resId) } catch (e: Exception) { null }
                } else null

                loaded ?: run {
                    val size = 110
                    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C5A059") }
                    val path = Path()
                    path.moveTo(size / 2f, 0f)
                    path.lineTo(0f, size.toFloat())
                    path.lineTo(size.toFloat(), size.toFloat())
                    path.close()
                    canvas.drawPath(path, paint)
                    b
                }
            }.also { baseLocationBitmap = it }

            // 1.5x Enlarged Car Dimensions (81 x 144)
            val carW = 81
            val carH = 144
            
            // Large transparent bounds (175 x 175) so 360-degree rotation NEVER clips the car (No circular halo)
            val canvasSize = 175
            val rotatedBitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rotatedBitmap)

            val centerX = canvasSize / 2f
            val centerY = canvasSize / 2f

            // Draw rotated 1.5x car bitmap centered inside the transparent canvas (Bumper/Hood faces front along road bearing)
            val matrix = Matrix()
            matrix.postScale(carW.toFloat() / baseBmp.width, carH.toFloat() / baseBmp.height)
            matrix.postTranslate((canvasSize - carW) / 2f, (canvasSize - carH) / 2f)
            matrix.postRotate((roundedBearing + 90f) % 360f, centerX, centerY)

            canvas.drawBitmap(baseBmp, matrix, Paint(Paint.ANTI_ALIAS_FLAG))
            IconFactory.getInstance(context).fromBitmap(rotatedBitmap)
        }
    }

    fun createCustomLocationIcon(context: Context): Icon {
        val resId = context.resources.getIdentifier("location", "drawable", context.packageName)
        val bitmap = if (resId != 0) BitmapFactory.decodeResource(context.resources, resId)
                     else BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_menu_mylocation)
        val scaled = Bitmap.createScaledBitmap(bitmap, 70, 70, true)
        return IconFactory.getInstance(context).fromBitmap(scaled)
    }

    fun createVehicleMarker(context: Context, vehicleRes: Int, bearing: Float = 0f, scale: Float = 1.0f, showRipples: Boolean = true): Icon {
        val baseSize = 180 // Increased to accommodate ripples
        val size = (baseSize * scale).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val centerX = size / 2f
        val centerY = size / 2f

        if (showRipples) {
            // Draw motion ripples (concentric circles)
            val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
                color = Color.parseColor("#C5A059") // Theme Gold
            }

            // Ripple 1
            ripplePaint.alpha = 100
            canvas.drawCircle(centerX, centerY, 35f * scale, ripplePaint)
            // Ripple 2
            ripplePaint.alpha = 60
            canvas.drawCircle(centerX, centerY, 55f * scale, ripplePaint)
            // Ripple 3
            ripplePaint.alpha = 30
            canvas.drawCircle(centerX, centerY, 75f * scale, ripplePaint)
        }

        // Load vehicle bitmap
        val vBmp = BitmapFactory.decodeResource(context.resources, vehicleRes) ?: 
                 BitmapFactory.decodeResource(context.resources, context.resources.getIdentifier("ic_privatecar1", "drawable", context.packageName))
        
        val scaledVSize = (90 * scale).toInt()
        val scaledV = Bitmap.createScaledBitmap(vBmp, scaledVSize, scaledVSize, true)
        
        // Rotate and Draw vehicle on top of ripples (Straight / Upright vehicle icon)
        val matrix = Matrix()
        matrix.postRotate(bearing, scaledV.width / 2f, scaledV.height / 2f)
        matrix.postTranslate((size - scaledV.width) / 2f, (size - scaledV.height) / 2f)
        canvas.drawBitmap(scaledV, matrix, paint)

        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }
}
