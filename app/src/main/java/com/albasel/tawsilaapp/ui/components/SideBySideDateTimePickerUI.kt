package com.albasel.tawsilaapp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import kotlin.math.*

@Composable
fun SideBySideDateTimePickerUI(
    isAr: Boolean,
    isDarkMode: Boolean,
    selectedDate: String,
    selectedTime: String,
    showMinutes: Boolean = false,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val calendar = remember { Calendar.getInstance() }
    val goldColor = Color(0xFFC5A059)
    
    // Parse Date
    val dateParts = selectedDate.split("-")
    val initialYear = dateParts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
    val initialMonth = (dateParts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
    val initialDay = dateParts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)

    var currentYear by remember { mutableIntStateOf(initialYear) }
    var currentMonth by remember { mutableIntStateOf(initialMonth) }
    var currentDay by remember { mutableIntStateOf(initialDay) }

    // Time States
    var pickingMinutes by remember { mutableStateOf(false) }
    
    val timeParts = selectedTime.split(" ")
    val hM = timeParts.getOrNull(0)?.split(":")
    val initialHour = hM?.getOrNull(0)?.toIntOrNull() ?: 12
    val initialMinute = hM?.getOrNull(1)?.toIntOrNull() ?: 0
    val initialAmPm = timeParts.getOrNull(1) ?: (if(isAr) "صباحاً" else "AM")

    var selHour by remember { mutableIntStateOf(initialHour) }
    var selMinute by remember { mutableIntStateOf(if(showMinutes) initialMinute else 0) }
    var selAmPm by remember { mutableStateOf(initialAmPm) }

    fun notifyTimeChange() {
        val minToUse = if(showMinutes) selMinute else 0
        val timeStr = String.format(Locale.ENGLISH, "%02d:%02d %s", selHour, minToUse, selAmPm)
        onTimeChange(timeStr)
    }

    LaunchedEffect(showMinutes) {
        if (!showMinutes) {
            selMinute = 0
            pickingMinutes = false
            notifyTimeChange()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDarkMode) Color(0xFF121212) else Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if(isAr) "اختر التاريخ والوقت" else "Select Date & Time", fontWeight = FontWeight.Black, color = goldColor, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))

        // --- Date Picker Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White),
            border = BorderStroke(1.dp, Color.LightGray.copy(0.2f))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (currentMonth == 0) { currentMonth = 11; currentYear-- } else currentMonth-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                    val monthNames = if (isAr) listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر") else listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
                    Text("${monthNames[currentMonth]} $currentYear", fontWeight = FontWeight.Black, color = goldColor, fontSize = 15.sp)
                    IconButton(onClick = { if (currentMonth == 11) { currentMonth = 0; currentYear++ } else currentMonth++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                }
                Row(Modifier.fillMaxWidth()) {
                    val days = if(isAr) listOf("س", "ح", "ن", "ث", "ر", "خ", "ج") else listOf("S", "S", "M", "T", "W", "T", "F")
                    days.forEach { day -> Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Black, color = goldColor) }
                }
                val tempCal = Calendar.getInstance().apply { set(currentYear, currentMonth, 1) }
                val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
                val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val offset = (firstDayOfWeek) % 7 
                var dayCounter = 1
                for (i in 0..5) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        for (j in 0..6) {
                            val currentIdx = i * 7 + j
                            if (currentIdx >= offset && dayCounter <= daysInMonth) {
                                val d = dayCounter
                                val isSelected = currentDay == d
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (isSelected) goldColor else Color.Transparent).clickable { currentDay = d; onDateChange(String.format(Locale.ENGLISH, "%d-%02d-%02d", currentYear, currentMonth + 1, d)) }, contentAlignment = Alignment.Center) {
                                    Text(d.toString(), color = if (isSelected) Color.White else if(isDarkMode) Color.White else Color.Black, fontSize = 14.sp, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                                dayCounter++
                            } else { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (dayCounter > daysInMonth) break
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Time Picker (Watch Style under Date) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White),
            border = BorderStroke(1.dp, Color.LightGray.copy(0.2f))
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp), tint = goldColor)
                    Spacer(Modifier.width(8.dp))
                    Text(if(isAr) "الوقت" else "Time", fontWeight = FontWeight.Bold, color = goldColor, fontSize = 13.sp)
                }
                
                Spacer(Modifier.height(8.dp))
                
                // LTR Row to ensure Watch is Left and Display is Right as requested
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        // Circular Watch UI (Small Wrist Watch Style)
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .background(if (isDarkMode) Color.Black else Color(0xFFF5F5F5), CircleShape)
                                .border(2.dp, goldColor.copy(0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val items = if (pickingMinutes) (0..59 step 5).toList() else (1..12).toList()
                            val radius = 50.dp
                            
                            items.forEachIndexed { index, value ->
                                val angle = (index * (360f / items.size)) - 90f
                                val rad = Math.toRadians(angle.toDouble())
                                val x = (radius.value * cos(rad)).dp
                                val y = (radius.value * sin(rad)).dp
                                
                                val isSelected = if (pickingMinutes) selMinute == value else selHour == value
                                
                                Box(
                                    modifier = Modifier
                                        .offset(x = x, y = y)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) goldColor else Color.Transparent)
                                        .clickable {
                                            if (pickingMinutes) {
                                                selMinute = value
                                                notifyTimeChange()
                                            } else {
                                                selHour = value
                                                if (showMinutes) pickingMinutes = true else notifyTimeChange()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        value.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else if (isDarkMode) Color.White else Color.Black
                                    )
                                }
                            }
                            
                            // Center Label
                            Text(if(pickingMinutes) "M" else "H", fontSize = 8.sp, color = goldColor, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.width(20.dp))

                        // Display Side (Selected time to the Right)
                        Column(horizontalAlignment = Alignment.Start) {
                            // "Change Hour" placed ABOVE the digital time display with a fixed height placeholder so time text never shifts
                            if (showMinutes && pickingMinutes) {
                                TextButton(onClick = { pickingMinutes = false }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(20.dp)) {
                                    Text(if(isAr) "تغيير الساعة" else "Change Hour", fontSize = 9.sp, color = goldColor, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Spacer(Modifier.height(20.dp))
                            }

                            Text(if(isAr) "الوقت:" else "Time:", fontSize = 9.sp, color = Color.Gray)
                            Text(
                                String.format(Locale.ENGLISH, "%02d:%02d", selHour, selMinute),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = goldColor
                            )
                            
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val amLabel = if(isAr) "صباحاً" else "AM"
                                val pmLabel = if(isAr) "مساءً" else "PM"
                                
                                TimePeriodBtn(amLabel, selAmPm == amLabel, isDarkMode) { selAmPm = amLabel; notifyTimeChange() }
                                TimePeriodBtn(pmLabel, selAmPm == pmLabel, isDarkMode) { selAmPm = pmLabel; notifyTimeChange() }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(if(isAr) "إلغاء" else "Cancel", color = goldColor, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1.5f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = goldColor),
                shape = RoundedCornerShape(23.dp)
            ) {
                Text(if(isAr) "تأكيد" else "Confirm", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun TimePeriodBtn(label: String, isSelected: Boolean, isDarkMode: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if(isSelected) Color(0xFFC5A059) else if(isDarkMode) Color.Black else Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if(isSelected) Color.Transparent else Color.Gray.copy(0.3f)),
        modifier = Modifier.height(32.dp).width(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if(isSelected) Color.White else Color.Gray)
        }
    }
}
