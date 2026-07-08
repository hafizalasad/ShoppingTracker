package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Expense
import com.example.ui.viewmodel.ShopExpenseSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Beautiful palette of soft pastel/soothing colors for the charts
val ChartColors = listOf(
    Color(0xFF8E9DF7), // Soft Slate Indigo
    Color(0xFF8FE3B5), // Soft Sage Mint
    Color(0xFFFFB6B9), // Soft Coral Peach
    Color(0xFFFFDF91), // Soft Sand Gold
    Color(0xFFE2B0FF), // Soft Lavender Lilac
    Color(0xFF86E3CE), // Soft Turquoise Aqua
    Color(0xFFD3C5E5), // Soft Plum Slate
    Color(0xFFFFB3B3)  // Soft Rose Pink
)

@Composable
fun ShopPieChart(
    summaries: List<ShopExpenseSummary>,
    currencySymbol: String,
    totalSpent: Double,
    modifier: Modifier = Modifier
) {
    val total = if (totalSpent > 0) totalSpent else 1.0
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(summaries) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Spent Share by Shop",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    val strokeWidth = 24.dp.toPx()
                    val innerRadiusPadding = strokeWidth / 2

                    summaries.forEachIndexed { index, summary ->
                        val sweepAngle = ((summary.totalAmount / total) * 360f).toFloat()
                        val color = ChartColors[index % ChartColors.size]

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle * animProgress.value,
                            useCenter = false,
                            topLeft = Offset(innerRadiusPadding, innerRadiusPadding),
                            size = Size(
                                size.width - strokeWidth,
                                size.height - strokeWidth
                            ),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweepAngle
                    }

                    if (summaries.isEmpty()) {
                        drawArc(
                            color = Color.LightGray.copy(alpha = 0.3f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(innerRadiusPadding, innerRadiusPadding),
                            size = Size(
                                size.width - strokeWidth,
                                size.height - strokeWidth
                            ),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Centered text display
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Total Spent",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = com.example.util.CurrencyUtils.formatBangladeshiStyle(currencySymbol, totalSpent),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Legend displaying shop metrics
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                summaries.forEachIndexed { index, summary ->
                    val color = ChartColors[index % ChartColors.size]
                    val percentage = (summary.totalAmount / total) * 100

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = summary.shopName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row {
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f%%", percentage),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = com.example.util.CurrencyUtils.formatBangladeshiStyle(currencySymbol, summary.totalAmount),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

data class DayExpenseGroup(
    val formattedDate: String,
    val totalAmount: Double,
    val timestamp: Long
)

@Composable
fun DayWiseBarChart(
    expenses: List<Expense>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    // Group and aggregate expenses by day
    val groupedExpenses = remember(expenses) {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("MMM dd", Locale.getDefault())
        val dayGroups = expenses.groupBy {
            calendar.timeInMillis = it.date
            // Zero out time fields to group exactly by day
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }.map { (timestamp, list) ->
            DayExpenseGroup(
                formattedDate = format.format(Date(timestamp)),
                totalAmount = list.sumOf { it.amount },
                timestamp = timestamp
            )
        }.sortedBy { it.timestamp }

        // Keep last 10 days of entries to keep visual scanning compact and clear
        if (dayGroups.size > 10) dayGroups.takeLast(10) else dayGroups
    }

    val maxAmount = remember(groupedExpenses) {
        val max = groupedExpenses.maxOfOrNull { it.totalAmount } ?: 0.0
        if (max > 0.0) max else 1.0
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Daily Spending Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (groupedExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No data available to show daily trends",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    groupedExpenses.forEach { group ->
                        val percentageHeight = (group.totalAmount / maxAmount).toFloat()

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                        ) {
                            // Spent Amount text above the bar
                            Text(
                                text = com.example.util.CurrencyUtils.formatBangladeshiStyle(currencySymbol, group.totalAmount, includeDecimals = false),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            // Colored Bar with dynamic gradient
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .fillMaxHeight(percentageHeight.coerceAtLeast(0.08f))
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            )
                                        )
                                    )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Date Label
                            Text(
                                text = group.formattedDate,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
