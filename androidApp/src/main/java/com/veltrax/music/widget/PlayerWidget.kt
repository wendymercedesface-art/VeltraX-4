package com.veltrax.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.veltrax.music.MainActivity
import com.veltrax.music.R
import com.veltrax.music.player.PlaybackService

class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (widgetId in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            val opening = PendingIntent.getActivity(
                context,
                9,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open, opening)

            arrayOf(
                R.id.widget_previous to "PREVIOUS",
                R.id.widget_toggle to "TOGGLE",
                R.id.widget_next to "NEXT"
            ).forEach { (view, action) ->
                val intent = Intent(context, PlayerWidget::class.java).setAction("com.veltrax.music.$action")
                val pending = PendingIntent.getBroadcast(
                    context,
                    view,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(view, pending)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val cmd = intent.action ?: return
        if (!cmd.startsWith("com.veltrax.music.")) return

        val pending = goAsync()
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        ).buildAsync()

        future.addListener({
            try {
                val controller = future.get()
                when (cmd.substringAfterLast('.')) {
                    "TOGGLE" -> if (controller.isPlaying) controller.pause() else controller.play()
                    "PREVIOUS" -> controller.seekToPreviousMediaItem()
                    "NEXT" -> controller.seekToNextMediaItem()
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    MediaController.releaseFuture(future)
                    pending.finish()
                }, 400)
            } catch (_: Exception) {
                pending.finish()
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
}
