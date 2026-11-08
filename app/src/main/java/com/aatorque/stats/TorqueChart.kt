package com.aatorque.stats

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.aatorque.prefs.SettingsViewModel
import com.aatorque.stats.databinding.FragmentChartBinding
import com.google.common.collect.ImmutableList
import com.jjoe64.graphview.GridLabelRenderer
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.DataPointInterface
import com.jjoe64.graphview.series.LineGraphSeries
import java.util.Date
import java.util.IdentityHashMap
import kotlin.properties.Delegates
import timber.log.Timber

class TorqueChart : Fragment() {

    class LegendBinding : ViewModel() {
        var color = MutableLiveData<Int>()
        var label = MutableLiveData<String>()
        var value = MutableLiveData<String>()
        var icon = MutableLiveData<Int>()
    }

    private companion object {
        /** Visible width of the chart. Older readings are reached by dragging left. */
        const val WINDOW_MS = 60_000L

        /** 15% white: enough to read the scale against any wallpaper, faint enough to ignore. */
        const val GRID_COLOR = 0x26FFFFFF
    }

    // Keyed by the gauge instance on purpose: each gauge owns one series, and setupItems()
    // rebuilds both together. A recreated TorqueData must get a new series, never reuse one, so
    // identity is the correct semantics and IdentityHashMap says so out loud. See issue #24.
    private val series = IdentityHashMap<TorqueData, LineGraphSeries<DataPointInterface>>()
    private lateinit var graph: ClampedGraphView
    private lateinit var binding: FragmentChartBinding
    private var startDate by Delegates.notNull<Long>()
    lateinit var legendBinding: List<LegendBinding>
    lateinit var settingsViewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        settingsViewModel =
            ViewModelProvider(requireParentFragment())[SettingsViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startDate = Date().time
        graph = view.findViewById(R.id.graph)
        graph.legendRenderer.isVisible = false
        graph.legendRenderer.width = graph.width
        graph.viewport.isScalable = true
        graph.viewport.isScrollable = true
        graph.gridLabelRenderer.numHorizontalLabels = 5
        graph.gridLabelRenderer.numVerticalLabels = 7
        // A grid instead of time labels: the labels had to be squeezed into the pane width and
        // ran into each other, and a driver reads the shape of the trace, not the second.
        graph.gridLabelRenderer.gridColor = GRID_COLOR
        graph.gridLabelRenderer.gridStyle = GridLabelRenderer.GridStyle.BOTH
        graph.gridLabelRenderer.isHorizontalLabelsVisible = false
        graph.gridLabelRenderer.isVerticalLabelsVisible = false

        view.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            var previousCanvasHeight = graph.height
            var previousCanvasWidth = graph.width
            override fun onGlobalLayout() {
                val canvasWidth = graph.width
                val canvasHeight = graph.height

                // Check if the size of the canvas has shrunk.
                if (canvasWidth != previousCanvasWidth || canvasHeight != previousCanvasHeight) {
                    previousCanvasHeight = canvasHeight
                    previousCanvasWidth = canvasWidth
                    // Eight fixed labels do not fit every width: on a narrow pane the last two
                    // were drawn on top of each other and the axis read "00:0100:03". The count
                    // follows the measured width instead.
                    val perLabel = 60f * resources.displayMetrics.density
                    graph.gridLabelRenderer.numHorizontalLabels =
                        (canvasWidth / perLabel).toInt().coerceIn(2, 8)
                }
            }
        })
    }

    fun setupItems(torqueData: Array<TorqueData>) {
        graph.series.clear()
        series.clear()

        val defaultColors = resources.obtainTypedArray(R.array.chartColors)
        val colors = torqueData.mapIndexed { index, td ->
            td.display.chartColor.let {
                if (it == 0) {
                    defaultColors.getColor(index, Color.WHITE)
                } else {
                    it
                }
            }
        }
        defaultColors.recycle()

        binding.labelWrap.removeAllViews()
        legendBinding = torqueData.mapIndexed { idx, data ->
            val line = LineGraphSeries<DataPointInterface>()
            line.title = data.display.label
            line.isDrawDataPoints = false
            line.color = colors[idx]
            graph.addSeries(line)
            series[data] = line

            // Seed from the readings the gauge kept while the chart was closed, so a swipe
            // down shows the recent past instead of an empty grid. See issue #193.
            val history = data.history
            if (history.size > 0) {
                val points = ArrayList<DataPointInterface>(history.size)
                for (i in 0 until history.size) {
                    val time = history.timeAt(i).toDouble()
                    points.add(DataPoint(time, scale(data, history.valueAt(i))))
                }
                line.resetData(points.toTypedArray())
            }

            val iconRes = try {
                resources.getIdentifier(
                    data.getDrawableName(),
                    "drawable",
                    requireContext().packageName
                )
            } catch (e: Resources.NotFoundException) {
                R.drawable.ic_none
            }
            val binding = LegendBinding().apply {
                color.value = line.color
                label.value = data.display.label
                icon.value = if (iconRes == R.drawable.ic_none || iconRes == 0) {
                    if (data.pid == null) R.drawable.ic_none else R.drawable.ic_box
                } else {
                    iconRes
                }
            }
            data.notifyUpdate = {
                notifyUpdate(it, binding)
            }
            binding
        }
        binding.layoutManager =
            StaggeredGridLayoutManager(legendBinding.size, RecyclerView.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        binding.legendData = LegendAdapter(settingsViewModel, ImmutableList.copyOf(legendBinding))
        val now = Date().time
        // Labels count from the oldest reading, so the axis shows elapsed time rather than
        // running backwards past the moment the chart opened.
        startDate = torqueData.mapNotNull { it.history.oldestTime }.minOrNull() ?: now
        graph.viewport.setMaxY(100.0)
        graph.viewport.setMinY(0.0)
        // Manual bounds, or a drag is clamped to the data GraphView can see and the older
        // readings cannot be reached. The window ends at the newest point and follows it
        // until the user drags back.
        graph.viewport.setXAxisBoundsManual(true)
        graph.minAllowed = startDate.toDouble()
        graph.maxAllowed = now.toDouble()
        showWindow(now)
    }

    /**
     * Put the last [WINDOW_MS] of data on screen, ending at [now]. The window never starts
     * before the oldest reading: a session younger than the window would otherwise show empty
     * grid on the left and negative time labels.
     */
    private fun showWindow(now: Long) {
        val from = maxOf(now - WINDOW_MS, graph.minAllowed.toLong())
        graph.viewport.setMinX(from.toDouble())
        graph.viewport.setMaxX(now.toDouble())
    }

    fun notifyUpdate(data: TorqueData, binding: LegendBinding) {
        val line = series[data]
        if (line == null) {
            // A gauge whose series was never registered would otherwise be dropped silently.
            Timber.w("No chart series for ${data.display.label}; skipping point")
            return
        }
        val now = Date()
        val point = DataPoint(now, scale(data, data.lastData))
        line.appendData(point, true, PidHistory.DEFAULT_CAPACITY)
        // Follow the newest reading only while the view is parked at the right edge. A user
        // who dragged back through the history stays where they are.
        graph.maxAllowed = now.time.toDouble()
        if (graph.viewport.getMaxX(false) >= now.time - 2_000) {
            showWindow(now.time)
        }
        // Zero is a reading, not a missing value: show it. The series and the label follow
        // the same rule, or the chart plots a point its legend hides. See issue #158.
        binding.value.value = "${data.lastDataStr}${data.display.unit}"
    }

    /**
     * The series is plotted as a percentage of the gauge range, which the user can change in
     * settings, so the conversion happens when a point is plotted rather than when it is read.
     * A zero range (min == max, or a PID the car never answers) would scale to NaN or
     * Infinity; TorqueGauge.setMinMax() already bumps an equal max, so 0 is the only case.
     */
    private fun scale(data: TorqueData, value: Double): Double {
        val range = (data.display.maxValue - data.display.minValue).toDouble()
        return if (range == 0.0) 0.0 else (value / range) * 100.0
    }
}
