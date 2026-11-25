package app.mychitas.peleshaon.ui

import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.domain.ZmanType

/* Названия времён дня (коротко, полностью, что это) — для экрана, виджета и уведомления. Тексты — с сайта. */

internal fun short(id: String): Int = when (id) {
    CANDLES -> R.string.z_s_candles
    HAVDALAH -> R.string.z_s_havdalah
    else -> when (ZmanType.valueOf(id)) {
        ZmanType.ALOT -> R.string.z_s_alot
        ZmanType.SUNRISE -> R.string.z_s_sunrise
        ZmanType.SOF_ZMAN_SHMA -> R.string.z_s_sof_zman_shma
        ZmanType.SOF_ZMAN_TFILA -> R.string.z_s_sof_zman_tfila
        ZmanType.CHATZOT -> R.string.z_s_chatzot
        ZmanType.MINCHA_GEDOLA -> R.string.z_s_mincha_gedola
        ZmanType.MISHEYAKIR -> R.string.z_s_misheyakir
        ZmanType.BIUR_CHAMETZ -> R.string.z_s_biur_chametz
        ZmanType.MINCHA_GEDOLA_AR -> R.string.z_s_mincha_gedola_ar
        ZmanType.MINCHA_KETANA -> R.string.z_s_mincha_ketana
        ZmanType.PLAG_HAMINCHA -> R.string.z_s_plag_hamincha
        ZmanType.SUNSET -> R.string.z_s_sunset
        ZmanType.TZAIT -> R.string.z_s_tzait
        ZmanType.TZAIT_AR -> R.string.z_s_tzait_ar
    }
}

internal fun full(id: String): Int = when (id) {
    CANDLES -> R.string.z_t_candles
    HAVDALAH -> R.string.z_t_havdalah
    else -> when (ZmanType.valueOf(id)) {
        ZmanType.ALOT -> R.string.z_t_alot
        ZmanType.SUNRISE -> R.string.z_t_sunrise
        ZmanType.SOF_ZMAN_SHMA -> R.string.z_t_sof_zman_shma
        ZmanType.SOF_ZMAN_TFILA -> R.string.z_t_sof_zman_tfila
        ZmanType.CHATZOT -> R.string.z_t_chatzot
        ZmanType.MINCHA_GEDOLA -> R.string.z_t_mincha_gedola
        ZmanType.MISHEYAKIR -> R.string.cal_misheyakir
        ZmanType.BIUR_CHAMETZ -> R.string.cal_biur_chametz
        ZmanType.MINCHA_GEDOLA_AR -> R.string.z_t_mincha_gedola_ar
        ZmanType.MINCHA_KETANA -> R.string.cal_mincha_ketana
        ZmanType.PLAG_HAMINCHA -> R.string.z_t_plag_hamincha
        ZmanType.SUNSET -> R.string.z_t_sunset
        ZmanType.TZAIT -> R.string.z_t_tzait
        ZmanType.TZAIT_AR -> R.string.z_t_tzait_ar
    }
}

internal fun about(id: String): Int = when (id) {
    CANDLES -> R.string.z_d_candles
    HAVDALAH -> R.string.z_d_havdalah
    else -> when (ZmanType.valueOf(id)) {
        ZmanType.ALOT -> R.string.z_d_alot
        ZmanType.SUNRISE -> R.string.z_d_sunrise
        ZmanType.SOF_ZMAN_SHMA -> R.string.z_d_sof_zman_shma
        ZmanType.SOF_ZMAN_TFILA -> R.string.z_d_sof_zman_tfila
        ZmanType.CHATZOT -> R.string.z_d_chatzot
        ZmanType.MINCHA_GEDOLA -> R.string.z_d_mincha_gedola
        ZmanType.MISHEYAKIR -> R.string.z_d_misheyakir
        ZmanType.BIUR_CHAMETZ -> R.string.z_d_biur_chametz
        ZmanType.MINCHA_GEDOLA_AR -> R.string.z_d_mincha_gedola_ar
        ZmanType.MINCHA_KETANA -> R.string.z_d_mincha_ketana
        ZmanType.PLAG_HAMINCHA -> R.string.z_d_plag_hamincha
        ZmanType.SUNSET -> R.string.z_d_sunset
        ZmanType.TZAIT -> R.string.z_d_tzait
        ZmanType.TZAIT_AR -> R.string.z_d_tzait_ar
    }
}

internal fun isDeadline(id: String) = id != CANDLES && id != HAVDALAH && ZmanType.valueOf(id).isDeadline

