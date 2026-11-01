package com.aatorque.stats

import android.content.ComponentName
import android.content.ServiceConnection
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.service.media.MediaBrowserService
import com.aatorque.datastore.Display
import com.aatorque.datastore.UserPreference
import com.aatorque.prefs.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.prowl.torque.remote.ITorqueService
import timber.log.Timber

/**
 * Publishes the gauges as an Android Auto media card, so they can sit beside Maps.
 *
 * This is not a player. The session exists only to put a card on the Coolwalk rail; nothing is
 * ever played, no audio focus is taken and no foreground service is used. Transport commands are
 * deliberately ignored: a tap on Play/Pause must never freeze the values.
 *
 * Values come from the same Torque binding the dashboard uses, and are gated on
 * [ITorqueService.isConnectedToECU]. A bench with no adapter therefore shows dashes and
 * "No ECU", never a live-looking zero.
 */
class CardService : MediaBrowserService() {

    companion object {
        private const val TAG = "CardService"
        private const val ROOT_ID = "card"
        private const val MAX_METRICS = 5

        /** Shown instead of a value when Torque has no live ECU, so a bench never looks live. */
        const val NO_VALUE = "-"
    }

    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var poller: Job? = null
    private var torque: TorqueServiceWrapper? = null
    private var frameId = 0
    private var lastSubtitle = ""
    private var lastDescription = ""

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            torque = (binder as TorqueServiceWrapper.LocalBinder).getService()
            Timber.i("$TAG bound to the Torque wrapper")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            torque = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "aatorque-card")
        session.setCallback(object : MediaSession.Callback() {
            // Not a player: every transport command keeps the card live.
            override fun onPlay() = publishState()
            override fun onPause() = publishState()
            override fun onStop() = publishState()
        })
        session.isActive = true
        setSessionToken(session.sessionToken)
        TorqueServiceWrapper.runStartIntent(this, connection)
        startPoller()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        // The card is the whole surface; there is no browsable tree.
        result.sendResult(mutableListOf())
    }

    private fun startPoller() {
        if (poller != null) {
            return
        }
        poller = scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (e: Exception) {
                    Timber.e(e, "$TAG tick failed")
                }
                delay(TorqueRefresher.REFRESH_INTERVAL)
            }
        }
    }

    private suspend fun tick() {
        val prefs = applicationContext.dataStore.data.first()
        val displays = activeDisplays(prefs)
        val service = torque?.torqueService
        val ecuLive =
            service != null && runCatching { service.isConnectedToECU() }.getOrDefault(false)

        val metrics = displays.map { metricFor(it, service, ecuLive) }
        val subtitle = CardText.subtitle(metrics)
        val description = CardText.description(ecuLive, metrics.any { it.value != NO_VALUE })
        if (subtitle == lastSubtitle && description == lastDescription) {
            return
        }
        lastSubtitle = subtitle
        lastDescription = description

        frameId++
        CardFrames.publish(
            frameId,
            CardSnapshot(
                style = styleOf(prefs),
                theme = themeOf(prefs),
                metrics = metrics,
                subtitle = subtitle,
                description = description
            )
        )
        publishMetadata(frameId, subtitle, description)
    }

    private fun activeDisplays(prefs: UserPreference): List<Display> {
        val screens = prefs.screensList
        if (screens.isEmpty()) {
            return emptyList()
        }
        val index = prefs.currentScreen.coerceIn(0, screens.size - 1)
        return screens[index].gaugesList
            .filter { !it.disabled && it.pid.isNotEmpty() }
            .take(MAX_METRICS)
    }

    private fun styleOf(prefs: UserPreference): CardStyle =
        CardStyle.values().getOrElse(prefs.cardStyle) { CardStyle.NUMBERS }

    private fun themeOf(prefs: UserPreference): String =
        prefs.selectedTheme.ifEmpty { resources.getStringArray(R.array.Themes).first() }

    private fun metricFor(
        display: Display,
        service: ITorqueService?,
        ecuLive: Boolean
    ): CardMetric {
        val min = display.minValue.toFloat()
        val max = display.maxValue.toFloat().takeIf { it > min } ?: 100f
        val title = display.label.ifEmpty { display.pid }
        if (!ecuLive || service == null) {
            return CardMetric(title, NO_VALUE, min, max, 0f)
        }
        val raw = runCatching {
            service.getPIDValuesAsDouble(arrayOf(display.pid)).getOrNull(0)
        }.getOrNull() ?: return CardMetric(title, NO_VALUE, min, max, 0f)
        val text = format(display, raw) + if (display.unit.isNotEmpty()) " ${display.unit}" else ""
        return CardMetric(
            title = title,
            value = text,
            min = min,
            max = max,
            at = ((raw.toFloat() - min) / (max - min)).coerceIn(0f, 1f)
        )
    }

    private fun format(display: Display, value: Double): String = try {
        if (display.wholeNumbers) {
            TorqueData.intPlaces.format(
                value
            )
        } else {
            TorqueData.twoPlaces.format(value)
        }
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "$TAG could not format ${display.label}")
        value.toString()
    }

    private fun publishMetadata(id: Int, subtitle: String, description: String) {
        val art = Uri.parse("content://${CardArtworkProvider.AUTHORITY}/f/$id").toString()
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, getString(R.string.app_name))
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, getString(R.string.app_name))
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, description)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadata.METADATA_KEY_ART_URI, art)
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, art)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, art)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 300_000L)
            .build()
        session.setMetadata(metadata)
        publishState()
    }

    private fun publishState() {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .build()
        )
    }

    override fun onDestroy() {
        poller?.cancel()
        scope.cancel()
        runCatching { unbindService(connection) }
        session.isActive = false
        session.release()
        CardFrames.clear()
        super.onDestroy()
    }
}
