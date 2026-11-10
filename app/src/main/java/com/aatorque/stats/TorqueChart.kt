package com.aatorque.stats

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        /** The chart advances in 3 s steps; the gauges and readouts keep their own refresh. */
        const val REFRESH_MS = 3_000L

        /** 15% white: enough to read the scale against any wallpaper, faint enough to ignore. */
        const val GRID_COLOR = 0x26FFFFFF
    }

    // Keyed by the gauge instance on purpose: each gauge owns one series, and setupItems()
    // rebuilds both together. A recreated TorqueData must get a new series, never reuse one, so
    // identity is the correct semantics and IdentityHashMap says so out loud. See issue #24.
    private val series = IdentityHashMap<TorqueData, LineGraphSeries<DataPointInterface>>()

    /** The gauges this chart plots, and how far each series has read its history. */
    private var items: Array<TorqueData> = emptyArray()
    private val lastPoint = IdentityHashMap<TorqueData, Long>()

    /** True while the window ends at the newest reading and rolls forward with it. */
    private var following = true

    private val handler = Handler(Looper.getMainLooper())
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
        graph.onViewportSettled = { onViewportSettled() }
        graph.onVerticalFling = { direction -> onVerticalFling?.invoke(direction) }
        binding.liveBtn.setOnClickListener { resumeLive() }
        graph.legendRenderer.isVisible = false
        graph.legendRenderer.width = graph.width
        // No pinch: the scale is fixed so a slope means the same thing at any time.
        graph.viewport.isScalable = false
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
        items = torqueData
        lastPoint.clear()

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

        var oldest = Long.MAX_VALUE
        binding.labelWrap.removeAllViews()
        legendBinding = torqueData.mapIndexed { idx, data ->
            val line = LineGraphSeries<DataPointInterface>()
            line.title = data.display.label
            line.isDrawDataPoints = false
            line.color = colors[idx]
            graph.addSeries(line)
            series[data] = line

            // Seed from the readings the gauge kept while the chart was closed, so a swipe
            // down shows the recent past instead of an empty grid. One snapshot feeds both the
            // points and the window start, so they cannot disagree. See issue #193.
            val snapshot = data.history.snapshot()
            if (snapshot.isNotEmpty()) {
                val points = snapshot.map { DataPoint(it.time.toDouble(), scale(data, it.value)) }
                line.resetData(points.toTypedArray())
                // The tick that follows setupItems() reads this watermark. Without it every
                // seeded reading is appended again, out of order, and GraphView throws.
                lastPoint[data] = snapshot.last().time
                oldest = minOf(oldest, snapshot.first().time)
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
        // The series are percentages of each gauge range, so the vertical scale must not follow
        // the visible data: on a drag GraphView would otherwise rescale Y to the stretch being
        // read, and the traces would stretch and lose the spacing between them.
        graph.viewport.setYAxisBoundsManual(true)
        // Manual bounds, or a drag is clamped to the data GraphView can see and the older
        // readings cannot be reached. The window ends at the newest point and follows it
        // until the user drags back.
        graph.viewport.setXAxisBoundsManual(true)
        graph.minAllowed = if (oldest == Long.MAX_VALUE) now.toDouble() else oldest.toDouble()
        graph.maxAllowed = now.toDouble()
        setFollowing(true)
        showWindow(now)
        tick()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    /**
     * Pulls the new readings out of each gauge's history instead of waiting for
     * `TorqueData.notifyUpdate`. That slot has one owner and the gauges reclaim it, so a chart
     * driven by it froze the moment another view registered. The ring is the record; the chart
     * reads it.
     */
    private fun tick() {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        items.forEachIndexed { index, data ->
            val binding = legendBinding.getOrNull(index) ?: return@forEachIndexed
            appendNewReadings(data, binding)
        }
        graph.maxAllowed = now.toDouble()
        // The ring drops the oldest readings as it fills, so the oldest allowed edge must move
        // with it. A parked window that outlives the ring then shows empty grid and never
        // counts as fallen off the end.
        val oldest = items.mapNotNull { it.history.oldestTime }.minOrNull()
        if (oldest != null) graph.minAllowed = oldest.toDouble()
        // A window the user dragged back stays where it was put, and the live button brings it
        // forward again. This used to be decided from the whole data range
        // (`getMaxX(true)` is the complete range, not the viewport), so the newest edge was
        // always "at now" and every tick pulled a scrolled window back.
        if (!following && ChartMath.fellOffTheEnd(currentWindow(), graph.minAllowed)) {
            // The readings under the window are gone, so there is nothing left to read.
            resumeLive()
        }
        if (following) {
            follow(now)
        }
    }

    private fun appendNewReadings(data: TorqueData, binding: LegendBinding) {
        val line = series[data] ?: return
        val snapshot = data.history.snapshot()
        if (snapshot.isEmpty()) return
        val last = lastPoint[data] ?: Long.MIN_VALUE
        snapshot.filter { it.time > last }.forEach { reading ->
            line.appendData(
                DataPoint(reading.time.toDouble(), scale(data, reading.value)),
                false,
                PidHistory.DEFAULT_CAPACITY
            )
        }
        lastPoint[data] = snapshot.last().time
        binding.value.value = "${data.lastDataStr}${data.display.unit}"
    }

    /**
     * Put the readable part of the window on screen. A session younger than the window shows
     * what it has and grows, instead of showing empty grid on the left.
     */
    private fun showWindow(now: Long) {
        applyWindow(ChartMath.follow(now.toDouble(), WINDOW_MS))
    }

    /** True when the last gesture started on the graph, which drags its own viewport. */
    val consumedLastGesture: Boolean
        get() = ::graph.isInitialized && graph.handledGesture

    /** Up and down over the graph page dashboards; the owner wires this to its screen change. */
    var onVerticalFling: ((Int) -> Unit)? = null

    /**
     * Walk the visible window through the readings the gauge kept. Left and right on the car
     * screen land here; the window keeps its width, and stops at the oldest and newest reading.
     */
    fun scrollHistory(direction: Int) {
        if (!::graph.isInitialized) return
        val scrolled = ChartMath.scroll(
            currentWindow(),
            direction,
            graph.minAllowed,
            graph.maxAllowed
        )
        applyWindow(scrolled)
        setFollowing(ChartMath.atLive(scrolled, graph.maxAllowed))
    }

    /** The user finished a drag or a pinch on the graph; remember where it landed. */
    private fun onViewportSettled() {
        val settled = ChartMath.clamp(currentWindow(), graph.minAllowed, graph.maxAllowed)
        applyWindow(settled)
        setFollowing(ChartMath.atLive(settled, graph.maxAllowed))
    }

    /** Bring the window back to the newest reading after the user scrolled into the history. */
    fun resumeLive() {
        if (!::graph.isInitialized) return
        setFollowing(true)
        follow(System.currentTimeMillis())
    }

    /** The live button is shown while the window is parked in the history. */
    private fun setFollowing(value: Boolean) {
        following = value
        if (::graph.isInitialized) {
            binding.liveBtn.visibility = if (value) View.GONE else View.VISIBLE
        }
    }

    /** The viewport is the window: read it with `false`, never with the complete-range form. */
    private fun currentWindow(): ChartWindow =
        ChartWindow(graph.viewport.getMinX(false), graph.viewport.getMaxX(false))

    private fun applyWindow(window: ChartWindow) {
        graph.viewport.setMinX(window.min)
        graph.viewport.setMaxX(window.max)
    }

    /**
     * Roll the window forward without changing how much of it is visible, so a zoomed
     * viewport keeps its width while new readings arrive. The window never starts before the
     * oldest reading, and never ends after now.
     */
    private fun follow(now: Long) {
        applyWindow(ChartMath.follow(now.toDouble(), WINDOW_MS))
    }

    /**
     * The series is plotted as a percentage of the gauge range, which the user can change in
     * settings, so the conversion happens when a point is plotted rather than when it is read.
     * A zero range (min == max, or a PID the car never answers) would scale to NaN or
     * Infinity; TorqueGauge.setMinMax() already bumps an equal max, so 0 is the only case.
     */
    private fun scale(data: TorqueData, value: Double): Double {
        val range = (data.display.maxValue - data.display.minValue).toDouble()
        // The plot is a percentage of the gauge range, so the bottom of the range is 0, not 0
        // on the gauge. A reading below a positive minimum, or above a negative one, otherwise
        // lands outside the fixed 0..100 bounds and is clipped. See issue #159.
        return if (range == 0.0) {
            0.0
        } else {
            ((value - data.display.minValue) / range) * 100.0
        }
    }
}
