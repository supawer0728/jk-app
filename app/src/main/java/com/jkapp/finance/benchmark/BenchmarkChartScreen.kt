package com.jkapp.finance.benchmark

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.finance.benchmark.BenchmarkChartUtils.ChartPeriod
import com.jkapp.nav.BenchmarkChartType
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.math.BigDecimal
import java.util.Locale

private val ExtraStoreKey = ExtraStore.Key<List<String>>()

// 수익률 %축이 데이터에 눌리지 않도록 자동 감지된 min/max 위·아래로 10%씩 여백을 두고,
// 0선이 항상 보이도록 범위에 0을 포함한다. 데이터 기반이므로 실제 값을 잘라내지 않는다.
private object PercentRangeProvider : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        val lo = minOf(minY, 0.0)
        val hi = maxOf(maxY, 0.0)
        val padding = (hi - lo).takeIf { it > 0.0 }?.times(0.1) ?: 1.0
        return lo - padding
    }

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        val lo = minOf(minY, 0.0)
        val hi = maxOf(maxY, 0.0)
        val padding = (hi - lo).takeIf { it > 0.0 }?.times(0.1) ?: 1.0
        return hi + padding
    }
}

// 수익률/MDD 차트 전체화면. 진입 시 가로모드 고정, 뒤로가기 시 원래 방향 복원.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkChartScreen(
    viewModel: BenchmarkViewModel,
    chartType: BenchmarkChartType,
    onBack: () -> Unit,
) {
    val activity = LocalContext.current as? Activity

    // 차트 화면 진입 시 landscape 고정, 이탈 시 원래 방향 복원
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val allRows by viewModel.rowMetrics.collectAsStateWithLifecycle()
    var selectedPeriod by remember { mutableStateOf(ChartPeriod.ALL) }
    val filteredRows = remember(allRows, selectedPeriod) {
        BenchmarkChartUtils.filterByPeriod(allRows, selectedPeriod)
    }

    val title = when (chartType) {
        BenchmarkChartType.MDD -> stringResource(R.string.benchmark_chart_mdd)
        BenchmarkChartType.RETURN -> stringResource(R.string.benchmark_chart_return)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp),
        ) {
            PeriodFilterRow(selectedPeriod = selectedPeriod, onSelect = { selectedPeriod = it })
            Spacer(modifier = Modifier.height(8.dp))
            if (filteredRows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.benchmark_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                when (chartType) {
                    BenchmarkChartType.MDD -> BenchmarkMddChart(rows = filteredRows, modifier = Modifier.weight(1f))
                    BenchmarkChartType.RETURN -> BenchmarkReturnChart(rows = filteredRows, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(selectedPeriod: ChartPeriod, onSelect: (ChartPeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartPeriod.entries.forEach { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onSelect(period) },
                label = {
                    Text(
                        text = when (period) {
                            ChartPeriod.MONTHS_3 -> stringResource(R.string.benchmark_chart_period_3m)
                            ChartPeriod.MONTHS_6 -> stringResource(R.string.benchmark_chart_period_6m)
                            ChartPeriod.YEAR_1 -> stringResource(R.string.benchmark_chart_period_1y)
                            ChartPeriod.ALL -> stringResource(R.string.benchmark_chart_period_all)
                        }
                    )
                },
            )
        }
    }
}

// 수익률 이중 축 차트: 막대(현재금액, 오른쪽 Y축) + 선 4종(수익률, 왼쪽 Y축)
@Composable
private fun BenchmarkReturnChart(rows: List<BenchmarkRowMetrics>, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    val colorAsset = colorScheme.primary
    val colorKospi = Color(0xFFE65100)
    val colorSnp500 = Color(0xFF1565C0)
    val colorNasdaq = Color(0xFF2E7D32)
    val colorAmount = colorScheme.secondaryContainer

    val dates = rows.map { it.benchmark.date }
    val amountValues = rows.map { it.benchmark.currentAmount.toDouble() }
    val assetReturnValues = rows.map { it.returnRatePercent?.toDouble() ?: 0.0 }
    val kospiReturnValues = rows.map { it.kospi.returnRatePercent.toDouble() }
    val snpReturnValues = rows.map { it.snp500.returnRatePercent.toDouble() }
    val nasdaqReturnValues = rows.map { it.nasdaq.returnRatePercent.toDouble() }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(rows) {
        modelProducer.runTransaction {
            columnModel { series(amountValues) }
            lineModel {
                series(assetReturnValues)
                series(kospiReturnValues)
                series(snpReturnValues)
                series(nasdaqReturnValues)
            }
            extras { it.set(ExtraStoreKey, dates) }
        }
    }

    val amountFormatter = CartesianValueFormatter { _, value, _ ->
        BenchmarkChartUtils.formatAmount(BigDecimal.valueOf(value))
    }
    val percentFormatter = CartesianValueFormatter { _, value, _ ->
        String.format(Locale.US, "%.1f%%", value)
    }
    val dateFormatter = CartesianValueFormatter { context, x, _ ->
        context.model.extraStore.getOrNull(ExtraStoreKey)?.getOrNull(x.toInt()) ?: ""
    }

    val amountColumn = remember(colorAmount) { LineComponent(Fill(SolidColor(colorAmount)), 8.dp) }

    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(amountColumn),
            // 단일 시리즈이므로 병합 의미가 없다. 명시적으로 Grouped를 지정해 의도를 드러낸다.
            mergeMode = { ColumnCartesianLayer.MergeMode.Grouped() },
            verticalAxisPosition = Axis.Position.Vertical.End,
        ),
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(colorAsset)))
                ),
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(colorKospi)))
                ),
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(colorSnp500)))
                ),
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(colorNasdaq)))
                ),
            ),
            // %선이 왼쪽 축에서 눌리지 않도록 데이터 기반으로 여백을 준다(MEDIUM-4).
            rangeProvider = PercentRangeProvider,
            verticalAxisPosition = Axis.Position.Vertical.Start,
        ),
        startAxis = VerticalAxis.rememberStart(valueFormatter = percentFormatter),
        endAxis = VerticalAxis.rememberEnd(valueFormatter = amountFormatter),
        bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFormatter),
    )

    Column(modifier = modifier) {
        ChartLegend(
            items = listOf(
                colorAsset to stringResource(R.string.benchmark_chart_legend_asset),
                colorKospi to stringResource(R.string.benchmark_chart_legend_kospi),
                colorSnp500 to stringResource(R.string.benchmark_chart_legend_snp500),
                colorNasdaq to stringResource(R.string.benchmark_chart_legend_nasdaq),
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

// MDD 영역 차트: 자산/KOSPI/S&P500/나스닥 MDD 4종
@Composable
private fun BenchmarkMddChart(rows: List<BenchmarkRowMetrics>, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    val colorAsset = colorScheme.primary
    val colorKospi = Color(0xFFE65100)
    val colorSnp500 = Color(0xFF1565C0)
    val colorNasdaq = Color(0xFF2E7D32)

    val dates = rows.map { it.benchmark.date }
    val assetMddValues = rows.map { it.assetMdd?.toDouble() ?: 0.0 }
    val kospiMddValues = rows.map { it.kospi.mdd?.toDouble() ?: 0.0 }
    val snpMddValues = rows.map { it.snp500.mdd?.toDouble() ?: 0.0 }
    val nasdaqMddValues = rows.map { it.nasdaq.mdd?.toDouble() ?: 0.0 }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(rows) {
        modelProducer.runTransaction {
            lineModel {
                series(assetMddValues)
                series(kospiMddValues)
                series(snpMddValues)
                series(nasdaqMddValues)
            }
            extras { it.set(ExtraStoreKey, dates) }
        }
    }

    val percentFormatter = CartesianValueFormatter { _, value, _ ->
        String.format(Locale.US, "%.1f%%", value)
    }
    val dateFormatter = CartesianValueFormatter { context, x, _ ->
        context.model.extraStore.getOrNull(ExtraStoreKey)?.getOrNull(x.toInt()) ?: ""
    }

    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(colorAsset))),
                    areaFill = LineCartesianLayer.AreaFill.single(Fill(SolidColor(colorAsset.copy(alpha = 0.2f)))),
                ),
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(colorKospi))),
                    areaFill = LineCartesianLayer.AreaFill.single(Fill(SolidColor(colorKospi.copy(alpha = 0.2f)))),
                ),
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(colorSnp500))),
                    areaFill = LineCartesianLayer.AreaFill.single(Fill(SolidColor(colorSnp500.copy(alpha = 0.2f)))),
                ),
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(colorNasdaq))),
                    areaFill = LineCartesianLayer.AreaFill.single(Fill(SolidColor(colorNasdaq.copy(alpha = 0.2f)))),
                ),
            ),
        ),
        startAxis = VerticalAxis.rememberStart(valueFormatter = percentFormatter),
        bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFormatter),
    )

    Column(modifier = modifier) {
        ChartLegend(
            items = listOf(
                colorAsset to stringResource(R.string.benchmark_chart_legend_asset),
                colorKospi to stringResource(R.string.benchmark_chart_legend_kospi),
                colorSnp500 to stringResource(R.string.benchmark_chart_legend_snp500),
                colorNasdaq to stringResource(R.string.benchmark_chart_legend_nasdaq),
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ChartLegend(items: List<Pair<Color, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, shape = MaterialTheme.shapes.extraSmall)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
