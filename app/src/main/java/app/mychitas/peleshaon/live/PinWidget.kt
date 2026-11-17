package app.mychitas.peleshaon.live

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * Просит рабочий стол поставить виджет. Android при установке приложения виджеты не предлагает, поэтому —
 * кнопкой в знакомстве и в «Помощи». false — рабочий стол так не умеет (тогда — инструкция вручную).
 */
object PinWidget {
    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun request(context: Context, line: Boolean = false): Boolean {
        if (!supported(context)) return false
        val cls = if (line) LineWidget::class.java else FaceWidget::class.java
        return runCatching {
            AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, cls), null, null)
        }.getOrDefault(false)
    }

    /** Сколько виджетов уже стоит на рабочем столе. */
    fun count(context: Context): Int {
        val m = AppWidgetManager.getInstance(context)
        return m.getAppWidgetIds(ComponentName(context, FaceWidget::class.java)).size +
            m.getAppWidgetIds(ComponentName(context, LineWidget::class.java)).size
    }
}
