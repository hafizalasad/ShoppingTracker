package com.example.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Update
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import com.example.presentation.viewmodel.SavedDateFilter
import java.util.Calendar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.util.CurrencyUtils
import com.example.core.util.DateFormatter
import com.example.presentation.component.DayWiseBarChart
import com.example.presentation.component.ShopPieChart
import com.example.presentation.intent.MainUiIntent
import com.example.presentation.intent.ScanUiIntent
import com.example.presentation.state.ShopExpenseSummary
import com.example.presentation.viewmodel.ExpenseViewModel
import com.example.presentation.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.mainUiState.collectAsState()
    val savedCustomFilters by viewModel.savedDateFilters.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showSaveFilterDialog by remember { mutableStateOf(false) }
    var showMonthlyCycleDialog by remember { mutableStateOf(false) }
    var filterToDelete by remember { mutableStateOf<SavedDateFilter?>(null) }
    var filterNameInput by remember { mutableStateOf("") }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Expenses, 1 = Insights/Charts

    val listState = rememberLazyListState()
    var isListExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Shop Expense",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    Box {
                        Card(
                            onClick = { currencyMenuExpanded = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("currency_selection_trigger")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CurrencyExchange,
                                    contentDescription = "Currency exchange icon",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${state.selectedCurrency} (${state.currencySymbol})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = currencyMenuExpanded,
                            onDismissRequest = { currencyMenuExpanded = false }
                        ) {
                            viewModel.supportedCurrencies.forEach { code ->
                                val sym = when (code) {
                                    "USD" -> "$"
                                    "BDT" -> "৳"
                                    "EUR" -> "€"
                                    "GBP" -> "£"
                                    "JPY" -> "¥"
                                    "INR" -> "₹"
                                    "CAD" -> "CA$"
                                    "AUD" -> "AU$"
                                    "SGD" -> "SG$"
                                    else -> "$"
                                }
                                DropdownMenuItem(
                                    text = { Text("$code ($sym)", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        viewModel.onMainIntent(MainUiIntent.SetCurrency(code))
                                        currencyMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("currency_option_$code")
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        modifier = Modifier.testTag("main_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Scan Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Secondary FAB for scanning receipt
                FloatingActionButton(
                    onClick = {
                        viewModel.openGeneralScan(isManual = false)
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("scan_receipt_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Scan Receipt",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Receipt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Primary FAB for manual input
                FloatingActionButton(
                    onClick = {
                        viewModel.openGeneralScan(isManual = true)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_manual_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Manual",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Manual", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Hero Total Spent Display Card (Hidden when isListExpanded = true)
            AnimatedVisibility(
                visible = !isListExpanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total spent in range",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = CurrencyUtils.formatBangladeshiStyle(state.currencySymbol, state.totalSpent),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Manual Expand Icon to maximize list UI
                                IconButton(
                                    onClick = { isListExpanded = true },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            CircleShape
                                        )
                                        .testTag("expand_list_ui_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Expand Full List UI",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = "Trends",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            CircleShape
                                        )
                                        .padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Date range selection card
                        Card(
                            onClick = { showDatePicker = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("date_filter_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Date Range",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Duration filter",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${DateFormatter.formatDate(state.startDate)} - ${DateFormatter.formatDate(state.endDate)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Saved Date Filter Chips (Presets + User Saved Custom / Monthly Cycle Filters)
                        val nowMs = remember { System.currentTimeMillis() }
                        val cal = remember {
                            Calendar.getInstance().apply {
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        }
                        val startOfMonthMs = cal.timeInMillis

                        val yearCal = remember {
                            Calendar.getInstance().apply {
                                set(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        }
                        val startOfYearMs = yearCal.timeInMillis

                        val last7DaysMs = nowMs - (7L * 24 * 60 * 60 * 1000)
                        val last30DaysMs = nowMs - (30L * 24 * 60 * 60 * 1000)

                        val presetFilters = remember(startOfMonthMs, startOfYearMs, nowMs) {
                            listOf(
                                SavedDateFilter("preset_month", "This Month", startOfMonthMs, nowMs, isCustom = false),
                                SavedDateFilter("preset_7d", "Last 7 Days", last7DaysMs, nowMs, isCustom = false),
                                SavedDateFilter("preset_30d", "Last 30 Days", last30DaysMs, nowMs, isCustom = false),
                                SavedDateFilter("preset_year", "This Year", startOfYearMs, nowMs, isCustom = false)
                            )
                        }

                        val sortedSavedFilters = remember(savedCustomFilters) {
                            savedCustomFilters.sortedByDescending { it.id.toLongOrNull() ?: 0L }
                        }

                        val allFilters = sortedSavedFilters + presetFilters

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            allFilters.forEach { filter ->
                                val activeDates = remember(filter, state.startDate, state.endDate) {
                                    if (filter.startDay != null && filter.endDay != null) {
                                        viewModel.calculateMonthlyCycleDates(filter.startDay, filter.endDay, filter.isStartFromPreviousMonth)
                                    } else {
                                        Pair(filter.startDate, filter.endDate)
                                    }
                                }
                                val isSelected = (state.startDate == activeDates.first && state.endDate == activeDates.second)

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.onMainIntent(MainUiIntent.SetDateRange(activeDates.first, activeDates.second))
                                    },
                                    label = {
                                        Text(
                                            text = filter.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    },
                                    trailingIcon = {
                                        if (filter.isCustom) {
                                            IconButton(
                                                onClick = { filterToDelete = filter },
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .testTag("delete_filter_${filter.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove Saved Filter",
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("date_filter_chip_${filter.id}")
                                )
                            }

                            AssistChip(
                                onClick = { showMonthlyCycleDialog = true },
                                label = { Text("+ Monthly Cycle", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Add Monthly Cycle Filter",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("add_monthly_cycle_chip")
                            )

                            AssistChip(
                                onClick = { showSaveFilterDialog = true },
                                label = { Text("Save Active Filter", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = "Save Preset",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("save_date_filter_chip")
                            )
                        }
                    }
                }
            }

            // Compact Header Bar when list is expanded to full UI
            AnimatedVisibility(
                visible = isListExpanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Total Spent: ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyUtils.formatBangladeshiStyle(state.currencySymbol, state.totalSpent),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Collapse Full UI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { isListExpanded = false },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("collapse_list_ui_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Collapse Full UI",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Custom sliding custom pill Tab Selection Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 0: Expenses
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp)
                        .testTag("tab_expenses"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = "Expenses List",
                            tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Expenses",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Tab 1: Insights
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp)
                        .testTag("tab_insights"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Analytics Graph",
                            tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Crossfade content area
            Crossfade(
                targetState = selectedTab,
                label = "tab_fade",
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp)
                        ) {
                            // Search Shop Field
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.onMainIntent(MainUiIntent.SetSearchQuery(it)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .testTag("search_shop_input"),
                                placeholder = { Text("Search shop by name...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search Shop",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.onMainIntent(MainUiIntent.SetSearchQuery("")) },
                                            modifier = Modifier.testTag("clear_shop_search")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear Search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp, top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.searchQuery.isBlank()) "Spent by Shop" else "Search Results",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (state.searchQuery.isNotBlank()) {
                                    Text(
                                        text = "${state.shopSummaries.size} shop(s) found",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (state.shopSummaries.isEmpty()) {
                                if (state.searchQuery.isNotBlank()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storefront,
                                            contentDescription = "No Shop Found",
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No shops found matching \"${state.searchQuery}\"",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Try searching for another shop name or adjust your date filter.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        OutlinedButton(
                                            onClick = { viewModel.onMainIntent(MainUiIntent.SetSearchQuery("")) },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Clear Search")
                                        }
                                    }
                                } else {
                                    EmptyStateView()
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(state.shopSummaries) { summary ->
                                        ShopSummaryRow(
                                            summary = summary,
                                            formattedAmount = CurrencyUtils.formatBangladeshiStyle(state.currencySymbol, summary.totalAmount),
                                            onClick = {
                                                viewModel.onMainIntent(MainUiIntent.NavigateToShopDetails(summary.shopName))
                                            }
                                        )
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(90.dp))
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (state.expensesInRange.isEmpty()) {
                                Spacer(modifier = Modifier.height(60.dp))
                                EmptyStateView()
                            } else {
                                ShopPieChart(
                                    summaries = state.shopSummaries,
                                    currencySymbol = state.currencySymbol,
                                    totalSpent = state.totalSpent
                                )

                                DayWiseBarChart(
                                    expenses = state.expensesInRange,
                                    currencySymbol = state.currencySymbol
                                )

                                Spacer(modifier = Modifier.height(100.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            initialStartDate = state.startDate,
            initialEndDate = state.endDate,
            onDismiss = { showDatePicker = false },
            onDateRangeSelected = { start, end ->
                viewModel.onMainIntent(MainUiIntent.SetDateRange(start, end))
            }
        )
    }

    if (showSaveFilterDialog) {
        AlertDialog(
            onDismissRequest = { showSaveFilterDialog = false },
            title = { Text("Save Date Filter Preset") },
            text = {
                Column {
                    Text(
                        text = "Save active date filter (${DateFormatter.formatDate(state.startDate)} - ${DateFormatter.formatDate(state.endDate)}) as a quick preset button:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = filterNameInput,
                        onValueChange = { filterNameInput = it },
                        label = { Text("Preset Name") },
                        placeholder = { Text("e.g. Q1 Expenses, Project Alpha") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("filter_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (filterNameInput.isNotBlank()) {
                            viewModel.saveCustomDateFilter(filterNameInput, state.startDate, state.endDate)
                        }
                        showSaveFilterDialog = false
                        filterNameInput = ""
                    },
                    modifier = Modifier.testTag("save_filter_confirm_button")
                ) {
                    Text("Save Preset", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveFilterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMonthlyCycleDialog) {
        MonthlyCycleDialog(
            onDismiss = { showMonthlyCycleDialog = false },
            onSave = { name, startDay, endDay, isStartFromPreviousMonth ->
                viewModel.saveMonthlyCycleFilter(name, startDay, endDay, isStartFromPreviousMonth)
                val (sDate, eDate) = viewModel.calculateMonthlyCycleDates(startDay, endDay, isStartFromPreviousMonth)
                viewModel.onMainIntent(MainUiIntent.SetDateRange(sDate, eDate))
            },
            viewModel = viewModel
        )
    }

    filterToDelete?.let { targetFilter ->
        AlertDialog(
            onDismissRequest = { filterToDelete = null },
            title = {
                Text(
                    text = "Delete Saved Filter?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${targetFilter.name}\"? This custom date filter preset will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSavedDateFilter(targetFilter.id)
                        filterToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_filter_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { filterToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_filter_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MonthlyCycleDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, startDay: Int, endDay: Int, isStartFromPreviousMonth: Boolean) -> Unit,
    viewModel: ExpenseViewModel
) {
    var filterName by remember { mutableStateOf("") }
    var startDayInput by remember { mutableStateOf("15") }
    var endDayInput by remember { mutableStateOf("16") }
    var isStartFromPreviousMonth by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val startDay = startDayInput.toIntOrNull()
    val endDay = endDayInput.toIntOrNull()

    val previewDates = remember(startDay, endDay, isStartFromPreviousMonth) {
        if (startDay != null && startDay in 1..31 && endDay != null && endDay in 1..31) {
            viewModel.calculateMonthlyCycleDates(startDay, endDay, isStartFromPreviousMonth)
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Monthly Cycle Filter", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Set recurring monthly cycle dates for automatic month-to-month tracking.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = filterName,
                    onValueChange = { filterName = it },
                    label = { Text("Filter Name (Optional)") },
                    placeholder = {
                        Text(
                            if (isStartFromPreviousMonth) "e.g. Monthly Cycle ($startDayInput-$endDayInput)"
                            else "e.g. Monthly Cycle ($startDayInput-$endDayInput Next)"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_cycle_name_input")
                )

                // Checkbox: From day is in previous month
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isStartFromPreviousMonth = !isStartFromPreviousMonth }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isStartFromPreviousMonth,
                            onCheckedChange = { isStartFromPreviousMonth = it },
                            modifier = Modifier.testTag("monthly_cycle_prev_month_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "From day is in previous month",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isStartFromPreviousMonth) {
                                    "Period: Prev Month ($startDayInput) → Current Month ($endDayInput)"
                                } else {
                                    "Period: Current Month ($startDayInput) → Future Month ($endDayInput)"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startDayInput,
                        onValueChange = { input ->
                            if (input.isEmpty() || (input.all { it.isDigit() } && (input.toIntOrNull() ?: 0) <= 31)) {
                                startDayInput = input
                            }
                        },
                        label = {
                            Text(if (isStartFromPreviousMonth) "From (Prev M.)" else "From (Current M.)")
                        },
                        placeholder = { Text("15") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("monthly_cycle_start_day")
                    )

                    OutlinedTextField(
                        value = endDayInput,
                        onValueChange = { input ->
                            if (input.isEmpty() || (input.all { it.isDigit() } && (input.toIntOrNull() ?: 0) <= 31)) {
                                endDayInput = input
                            }
                        },
                        label = {
                            Text(if (isStartFromPreviousMonth) "To (Current M.)" else "To (Future M.)")
                        },
                        placeholder = { Text("16") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("monthly_cycle_end_day")
                    )
                }

                if (previewDates != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Active Range Preview:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${DateFormatter.formatDate(previewDates.first)} – ${DateFormatter.formatDate(previewDates.second)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sDay = startDayInput.toIntOrNull()
                    val eDay = endDayInput.toIntOrNull()
                    if (sDay == null || sDay !in 1..31) {
                        errorMessage = "Please enter a valid start day (1 - 31)"
                    } else if (eDay == null || eDay !in 1..31) {
                        errorMessage = "Please enter a valid end day (1 - 31)"
                    } else {
                        onSave(filterName, sDay, eDay, isStartFromPreviousMonth)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_monthly_cycle_button")
            ) {
                Text("Save Cycle", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyStateView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = "No Expenses",
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No expenses recorded",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add receipts or snap photos to track purchases.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun ShopSummaryRow(
    summary: ShopExpenseSummary,
    formattedAmount: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shop_row_${summary.shopName.lowercase().replace(" ", "_")}")
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Shop Icon",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = summary.shopName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${summary.expenseCount} receipt${if (summary.expenseCount > 1) "s" else ""}" +
                                if (summary.latestDate > 0L) " • ${DateFormatter.formatDate(summary.latestDate)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedAmount,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Go to Details",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialStartDate: Long,
    initialEndDate: Long,
    onDismiss: () -> Unit,
    onDateRangeSelected: (Long, Long) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate,
        initialSelectedEndDateMillis = initialEndDate
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null) {
                        onDateRangeSelected(start, end)
                    } else if (start != null) {
                        onDateRangeSelected(start, start)
                    }
                    onDismiss()
                }
            ) {
                Text("Select", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
        ) {
            DateRangePicker(
                state = state,
                title = {
                    Text(
                        text = "Select Date Range",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
