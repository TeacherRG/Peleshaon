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

    fun request(context: Context, date: Boolean = false): Boolean {
        if (!supported(context)) return false
        val cls = if (date) DateWidget::class.java else FaceWidget::class.java
        return runCatching {
            AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, cls), null, null)
        }.getOrDefault(false)
    }

    /** Сколько виджетов уже стоит на рабочем столе. */
    fun count(context: Context): Int {
        val m = AppWidgetManager.getInstance(context)
        return m.getAppWidgetIds(ComponentName(context, FaceWidget::class.java)).size +
            m.getAppWidgetIds(ComponentName(context, DateWidget::class.java)).size
    }
}

/**
 * Значок приложения на главный экран. Телефон ставит его сам не всегда: часто только для приложений из Google Play,
 * а при установке APK — нет. Ярлык с тем же значком и названием открывает приложение, как обычный значок.
 */
object PinIcon {
    fun supported(context: Context): Boolean =
        context.getSystemService(android.content.pm.ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    fun request(context: Context): Boolean {
        val sm = context.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false
        val open = android.content.Intent(context, app.mychitas.peleshaon.MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val info = android.content.pm.ShortcutInfo.Builder(context, "pele-home")
            .setShortLabel(context.getString(app.mychitas.peleshaon.R.string.app_name))
            .setIcon(android.graphics.drawable.Icon.createWithResource(context, app.mychitas.peleshaon.R.mipmap.ic_launcher))
            .setIntent(open)
            .build()
        return runCatching { sm.requestPinShortcut(info, null) }.getOrDefault(false)
    }
}
