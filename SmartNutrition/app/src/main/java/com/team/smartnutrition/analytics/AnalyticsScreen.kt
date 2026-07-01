package com.team.smartnutrition.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.R
import com.team.smartnutrition.analytics.util.PdfExporter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.team.smartnutrition.analytics.model.DailyCalorieEntry
import com.team.smartnutrition.analytics.model.TimeRange
import com.team.smartnutrition.analytics.model.WeightChartEntry
import com.team.smartnutrition.analytics.viewmodel.AnalyticsViewModel
import com.team.smartnutrition.common.components.ErrorCard
import com.team.smartnutrition.common.components.LoadingScreen
import com.team.smartnutrition.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: AnalyticsViewModel = viewModel()
    val weightEntries by viewModel.weightEntries.collectAsState()
    val calorieEntries by viewModel.calorieEntries.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val timeRange by viewModel.timeRange.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 " + stringResource(R.string.analytics_title)) },
                actions = {
                    IconButton(onClick = {
                        PdfExporter.exportAndShareReport(context, userProfile, weightEntries, calorieEntries)
                    }) {
                        Icon(Icons.Filled.PictureAsPdf, stringResource(R.string.export_pdf))
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingScreen()
            error != null -> ErrorCard(message = error!!, onRetry = { viewModel.refreshData() })
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // TimeRange Toggle
                    item {
                        TimeRangeSelector(
                            selected = timeRange,
                            onSelect = { viewModel.setTimeRange(it) }
                        )
                    }
                    
                    // Weight Chart Section
                    item {
                        WeightChartSection(weightEntries = weightEntries)
                    }
                    
                    // Calorie Chart Section
                    item {
                        CalorieChartSection(
                            entries = calorieEntries,
                            calorieTarget = userProfile?.calorieTarget ?: 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeRangeSelector(selected: TimeRange, onSelect: (TimeRange) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeRange.values().forEach { range ->
                val isSelected = selected == range
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(range) },
                    label = {
                        Text(
                            text = if (range == TimeRange.WEEK) "📅 " + stringResource(R.string.range_week) else "📆 " + stringResource(R.string.range_month),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun WeightChartSection(weightEntries: List<WeightChartEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚖️ " + stringResource(R.string.weight_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (weightEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_weight_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val modelProducer = remember { CartesianChartModelProducer() }
                LaunchedEffect(weightEntries) {
                    modelProducer.runTransaction {
                        lineSeries {
                            series(weightEntries.map { it.weightKg.toDouble() })
                        }
                    }
                }
                
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(
                                        fill(MaterialTheme.colorScheme.primary)
                                    )
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = CartesianValueFormatter { _, value, _ ->
                                val date = weightEntries.getOrNull(value.toInt())?.date ?: ""
                                if (date.isEmpty()) " " else date.takeLast(5)
                            }
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                // Hiển thị cân nặng mới nhất
                weightEntries.lastOrNull()?.let { latest ->
                    Text(
                        text = stringResource(R.string.current_weight_bmi, latest.weightKg, latest.bmi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CalorieChartSection(entries: List<DailyCalorieEntry>, calorieTarget: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🔥 " + stringResource(R.string.daily_calories),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (calorieTarget > 0) {
                Text(
                    text = stringResource(R.string.calorie_target_label, calorieTarget),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_meal_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val modelProducer = remember { CartesianChartModelProducer() }
                LaunchedEffect(entries) {
                    modelProducer.runTransaction {
                        columnSeries {
                            series(entries.map { it.totalCalories.toDouble() })
                        }
                    }
                }
                
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(
                            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                rememberLineComponent(
                                    fill = fill(MaterialTheme.colorScheme.secondary),
                                    thickness = 12.dp
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = CartesianValueFormatter { _, value, _ ->
                                val label = entries.getOrNull(value.toInt())?.dayLabel ?: ""
                                if (label.isEmpty()) " " else label
                            }
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}
