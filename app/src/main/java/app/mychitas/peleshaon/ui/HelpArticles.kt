package app.mychitas.peleshaon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.R

/*
 * «Помощь» → «Еврейское время» (владелец, 25.11.2026): статьи о еврейском счёте времени.
 * Текст статьи — в res/raw[-ru|-uk|-de|-iw]/, язык выбирается сам, как у строк.
 * Простая разметка: «## » — раздел (раскрывается), «### » — подзаголовок, «> » — סימן (памятка),
 * «- » — пункт списка, «| a | b |» — строка таблицы (первая — заголовок), пустая строка — новый абзац.
 */

private data class HelpArticle(val title: Int, val sub: Int, val raw: Int)

private val ARTICLES = listOf(
    HelpArticle(R.string.jt_kh_t, R.string.jt_kh_sub, R.raw.jt_kiddush_hachodesh),
)

@Composable
fun JewishTimeSheet(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(-1) }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val article = ARTICLES.getOrNull(open)
        if (article == null) {
            BackToHelp(onBack)
            Text(stringResource(R.string.jt_t), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ClockColors.Ink)
            Text(stringResource(R.string.jt_intro), fontSize = 14.sp, lineHeight = 20.sp, color = ClockColors.Ink2)
            ARTICLES.forEachIndexed { i, a ->
                HelpItem("📜", stringResource(a.title), stringResource(a.sub)) { open = i }
            }
        } else {
            Text(
                "‹ " + stringResource(R.string.jt_t),
                color = ClockColors.TcheletDeep, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.clickable { open = -1 }.padding(vertical = 4.dp)
            )
            ArticleView(article)
        }
    }
}

// ---------------------------------------------------------------- разметка

private sealed interface Block {
    data class Para(val text: String) : Block
    data class Sub(val text: String) : Block
    data class Siman(val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
}

private class Section(val title: String?, val blocks: List<Block>)

private fun parse(text: String): List<Section> {
    val sections = mutableListOf<Section>()
    var title: String? = null
    var blocks = mutableListOf<Block>()
    val para = StringBuilder()
    val table = mutableListOf<List<String>>()
    fun flushPara() { if (para.isNotEmpty()) { blocks += Block.Para(para.toString()); para.clear() } }
    fun flushTable() { if (table.isNotEmpty()) { blocks += Block.Table(table.toList()); table.clear() } }
    fun flush() { flushPara(); flushTable() }
    for (raw in text.lines()) {
        val line = raw.trimEnd()
        when {
            line.startsWith("# ") -> flush() // заголовок файла: название берём из строк
            line.startsWith("## ") -> {
                flush()
                if (title != null || blocks.isNotEmpty()) sections += Section(title, blocks)
                title = line.removePrefix("## ").trim()
                blocks = mutableListOf()
            }
            line.startsWith("### ") -> { flush(); blocks += Block.Sub(line.removePrefix("### ").trim()) }
            line.startsWith("> ") -> { flush(); blocks += Block.Siman(line.removePrefix("> ").trim()) }
            line.startsWith("- ") -> { flush(); blocks += Block.Bullet(line.removePrefix("- ").trim()) }
            line.startsWith("|") -> {
                flushPara()
                table += line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            }
            line.isBlank() -> flush()
            else -> { flushTable(); if (para.isNotEmpty()) para.append('\n'); para.append(line.trim()) }
        }
    }
    flush()
    if (title != null || blocks.isNotEmpty()) sections += Section(title, blocks)
    return sections
}

@Composable
private fun ArticleView(article: HelpArticle) {
    val context = LocalContext.current
    val sections = remember(article.raw, context) {
        parse(context.resources.openRawResource(article.raw).bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
    // открытые разделы; все сразу — «Развернуть всё»
    var opened by rememberSaveable(article.raw) { mutableStateOf(setOf<Int>()) }
    val titled = sections.indices.filter { sections[it].title != null }
    val all = opened.containsAll(titled)

    Text(stringResource(article.title), fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, color = ClockColors.Ink)
    Text(stringResource(article.sub), fontSize = 14.sp, lineHeight = 20.sp, color = ClockColors.Muted)
    Text(
        stringResource(if (all) R.string.jt_collapse else R.string.jt_expand),
        color = ClockColors.TcheletDeep, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.clickable { opened = if (all) emptySet() else titled.toSet() }.padding(vertical = 4.dp)
    )
    sections.forEachIndexed { i, sec ->
        if (sec.title == null) {
            sec.blocks.forEach { BlockView(it) }
        } else {
            val isOpen = i in opened
            HorizontalDivider(color = ClockColors.Line)
            Row(
                Modifier.fillMaxWidth().clickable { opened = if (isOpen) opened - i else opened + i }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isOpen) "▾" else "▸", color = ClockColors.Gold, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                Text(sec.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp,
                    color = if (isOpen) ClockColors.GoldDeep else ClockColors.Ink, modifier = Modifier.weight(1f))
            }
            if (isOpen) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { sec.blocks.forEach { BlockView(it) } }
        }
    }
}

@Composable
private fun BlockView(b: Block) {
    when (b) {
        is Block.Para -> Text(b.text, fontSize = 14.5.sp, lineHeight = 21.sp, color = ClockColors.Ink2)
        is Block.Sub -> Text(b.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ClockColors.Ink,
            modifier = Modifier.padding(top = 4.dp))
        is Block.Bullet -> Row {
            Text("•", color = ClockColors.Gold, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
            Text(b.text, fontSize = 14.5.sp, lineHeight = 21.sp, color = ClockColors.Ink2)
        }
        is Block.Siman -> Surface(
            shape = RoundedCornerShape(10.dp),
            color = ClockColors.Surface,
            border = BorderStroke(1.dp, ClockColors.Gold.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("✡ " + b.text, fontSize = 14.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
                color = ClockColors.GoldDeep, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        is Block.Table -> TableView(b.rows)
    }
}

@Composable
private fun TableView(rows: List<List<String>>) {
    val cols = rows.maxOf { it.size }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, ClockColors.Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            rows.forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(color = ClockColors.Line)
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (r == 0) Modifier.background(ClockColors.Gold.copy(alpha = 0.12f)) else Modifier)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    for (c in 0 until cols) {
                        Text(
                            row.getOrElse(c) { "" },
                            fontSize = 12.5.sp, lineHeight = 17.sp,
                            fontWeight = if (r == 0 || c == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (r == 0) ClockColors.GoldDeep else ClockColors.Ink,
                            modifier = Modifier.weight(if (c == 0) 1.6f else 1f).padding(horizontal = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
