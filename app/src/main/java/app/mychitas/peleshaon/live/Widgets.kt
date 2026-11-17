package app.mychitas.peleshaon.live

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Виджет «Циферблат дня» — тот же циферблат, что в приложении. */
class FaceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Live.update(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = Live.update(context)
    override fun onDisabled(context: Context) = Live.update(context)
}

/** Виджет-строка «Ближайшее время»: время города, ближайшее время дня и отсчёт до него. */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Live.update(context)
    override fun onDisabled(context: Context) = Live.update(context)
}
