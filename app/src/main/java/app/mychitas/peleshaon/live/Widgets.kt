package app.mychitas.peleshaon.live

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Виджет «Циферблат дня» — тот же циферблат, что в приложении. */
class FaceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Live.updateAsync(context, goAsync())
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = Live.updateAsync(context, goAsync())
    override fun onDisabled(context: Context) = Live.updateAsync(context, goAsync())
}

/** Виджет-строка «Ближайшее время»: время города, ближайшее время дня и отсчёт до него. */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Live.updateAsync(context, goAsync())
    override fun onDisabled(context: Context) = Live.updateAsync(context, goAsync())
}

/** Прямоугольный виджет «Часы»: дата, время, восход, галахический час, закат — в выбранном оформлении. */
class CardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Live.updateAsync(context, goAsync())
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = Live.updateAsync(context, goAsync())
    override fun onDisabled(context: Context) = Live.updateAsync(context, goAsync())
}
