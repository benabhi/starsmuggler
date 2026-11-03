package com.starsmuggler.ui.debug

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import com.starsmuggler.engine.content.MapPoint
import com.starsmuggler.engine.content.PlanetSide
import com.starsmuggler.ui.theme.ChromeOchre
import com.starsmuggler.ui.theme.ChromeTurquoise
import com.starsmuggler.ui.theme.PaperShade
import com.starsmuggler.ui.theme.SpaceBlack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.content.AssetContentSource
import com.starsmuggler.content.ContentLoader
import com.starsmuggler.content.LoadedContent
import com.starsmuggler.content.TextLanguage
import com.starsmuggler.engine.content.Air
import com.starsmuggler.engine.content.Area
import com.starsmuggler.engine.content.ConnectorKind
import com.starsmuggler.engine.content.DiceTable
import com.starsmuggler.engine.content.Outcome
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Restriction
import com.starsmuggler.engine.content.StarSystem
import com.starsmuggler.engine.content.Tile
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.GameText
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.Paper
import com.starsmuggler.ui.theme.PaperBackground
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** What the content loader read from the JSON assets, to check the data on the phone (SMUG-9). Debug builds only. */
@Composable
fun ContentScreen() {
    val context = LocalContext.current
    val language = TextLanguage.resolve(currentLocale().toLanguageTag())
    val result by produceState<Result<LoadedContent>?>(null, language) {
        value = withContext(Dispatchers.IO) {
            runCatching { ContentLoader(AssetContentSource(context.assets)).load(language) }
        }
    }
    PaperBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.content_title), style = MaterialTheme.typography.headlineLarge, color = RocketRed)
            Text(stringResource(R.string.content_subtitle), style = MaterialTheme.typography.labelMedium, color = InkFaded)
            val loaded = result
            when {
                loaded == null -> Text(stringResource(R.string.content_loading), style = MaterialTheme.typography.bodyLarge, color = Ink)
                loaded.isFailure -> LoadError(loaded.exceptionOrNull())
                else -> LoadedContentView(loaded.getOrThrow())
            }
        }
    }
}

@Composable
private fun LoadError(error: Throwable?) {
    RecordCard(stringResource(R.string.content_error_title)) {
        Text(error?.message ?: error.toString(), style = TeletypeStyles.small, color = RocketRed, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun LoadedContentView(content: LoadedContent) {
    val game = content.game
    Counts(game)
    TextsCard(content)
    TablesCard(content)
    SectorDiagram(content)
    game.systems.values.forEach { SystemCard(it, content) }
    TileCard(content)
}

@Composable
private fun Counts(game: GameContent) {
    val locale = currentLocale()
    RecordCard(stringResource(R.string.content_counts)) {
        RecordLine(stringResource(R.string.content_format), Formats.integer(GameContent.FORMAT_VERSION, locale))
        RecordLine(stringResource(R.string.content_systems), Formats.integer(game.systems.size, locale))
        RecordLine(stringResource(R.string.content_tiles), Formats.integer(game.tiles.size, locale))
        RecordLine(stringResource(R.string.content_tables), Formats.integer(game.tables.size, locale))
        RecordLine(stringResource(R.string.content_factor_tables), Formats.integer(game.factors.size, locale))
        RecordLine(stringResource(R.string.content_paragraphs), Formats.integer(game.paragraphs.size, locale))
        RecordLine(stringResource(R.string.content_equipment), Formats.integer(game.equipment.size, locale))
        RecordLine(stringResource(R.string.content_goods), Formats.integer(game.goods.size, locale))
    }
}

/** Any rule or event text by its id, with tappable references (SMUG-13). */
@Composable
private fun TextsCard(content: LoadedContent) {
    var query by rememberSaveable { mutableStateOf(FIRST_TEXT) }
    val id = query.trim().lowercase()
    val entry = content.texts.find(id)
    RecordCard(stringResource(R.string.content_texts)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.content_texts_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        if (entry == null) {
            Text(stringResource(R.string.content_texts_not_found, id), style = MaterialTheme.typography.bodyMedium, color = InkFaded)
        } else {
            Text(stringResource(R.string.content_text_title, id, entry.title), style = MaterialTheme.typography.titleMedium, color = Ink)
            entry.group?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = InkFaded) }
            entry.duration?.let { Text(it, style = TeletypeStyles.small, color = RocketRed) }
            entry.parent?.let { parent ->
                Text(
                    stringResource(R.string.content_text_parent, parent),
                    style = MaterialTheme.typography.labelMedium,
                    color = RocketRed,
                    modifier = Modifier.clickable { query = parent },
                )
            }
            GameText(entry.blocks, onReference = { query = it }, modifier = Modifier.padding(vertical = 8.dp), exists = { content.texts.find(it) != null })
            for (child in entry.children) {
                Text(
                    stringResource(R.string.content_text_title, child, content.texts.rule(child)?.title.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RocketRed,
                    modifier = Modifier.clickable { query = child }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** The text the card opens with: the first event of the game. */
private const val FIRST_TEXT = "e001"

/** Every table with a tap to roll on it, to check the transcription against the rule book (SMUG-12). */
@Composable
private fun TablesCard(content: LoadedContent) {
    var rolled by remember { mutableStateOf<Pair<String, String>?>(null) }
    val locale = currentLocale()
    RecordCard(stringResource(R.string.content_tables_roll)) {
        Text(stringResource(R.string.content_tables_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 4.dp))
        for (table in content.game.tables.values) {
            val dice = table.dice?.toString() ?: stringResource(R.string.content_no_dice)
            val roll = rollOn(table)
            val result = roll?.let { (row, value, outcome) ->
                stringResource(R.string.content_roll_result, rowLabel(table.id, row, content), Formats.integer(value, locale), outcomeText(outcome, content))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { rolled = table.id to (result ?: "") }
                    .padding(vertical = 6.dp),
            ) {
                Text(stringResource(R.string.content_table_line, table.id, dice), style = TeletypeStyles.medium, color = Ink)
                if (rolled?.first == table.id) {
                    Text(rolled!!.second, style = TeletypeStyles.small, color = RocketRed)
                }
            }
        }
    }
}

/** A random roll on [table]: its row key, the rolled number and the result. */
private fun rollOn(table: DiceTable): Triple<String?, Int, Outcome>? {
    val row = table.rows.random()
    val dice = table.dice
    val value = if (dice != null) {
        (1..dice.count).sumOf { Random.nextInt(1, 7) }
    } else {
        // Tables without dice are looked up by a number such as a tech code: pick one inside a random entry.
        val range = row.entries.random().roll
        val low = if (range.isOpenBelow) 0 else range.min
        val high = if (range.isOpenEnded) range.min + 100 else range.max
        Random.nextInt(low, high + 1)
    }
    val outcome = table.lookup(value, row.key) ?: return null
    return Triple(row.key, value, outcome)
}

@Composable
private fun rowLabel(tableId: String, key: String?, content: LoadedContent): String = when {
    key == null -> stringResource(R.string.content_single_row)
    tableId == "r241" -> stringResource(R.string.content_row_wealth, key)
    tableId == "r235" -> stringResource(R.string.content_row_tech, key)
    tableId == "r238" -> content.names.travelMode(key)
    key in content.names.areaTypes -> content.names.areaType(key)
    key in content.names.systems -> content.names.system(key)
    else -> key
}

@Composable
private fun outcomeText(outcome: Outcome, content: LoadedContent): String {
    val locale = currentLocale()
    return when (outcome) {
        is Outcome.Event -> outcome.ref.id
        is Outcome.Rule -> outcome.ref.id
        Outcome.SystemTable -> stringResource(R.string.content_result_system)
        Outcome.NoMoreRolls -> stringResource(R.string.content_result_stop)
        is Outcome.Key -> content.names.results[outcome.key] ?: content.names.goods(outcome.key)
        is Outcome.Multiplier -> stringResource(R.string.content_result_multiplier, Formats.decimal(outcome.value, locale))
        is Outcome.Value -> if (outcome.marked) {
            stringResource(R.string.content_result_marked, Formats.integer(outcome.value, locale))
        } else {
            Formats.integer(outcome.value, locale)
        }
    }
}

/**
 * The standard jump pattern drawn from the data (r234). The diagram is turned upright to fit
 * the portrait screen: its left-to-right axis runs top to bottom.
 */
@Composable
private fun SectorDiagram(content: LoadedContent) {
    val pattern = content.game.pattern()
    val measurer = rememberTextMeasurer()
    val labelStyle = TeletypeStyles.small.copy(color = Ink)
    val labels = pattern.nodes.associate { it.system to content.names.system(it.system) }
    RecordCard(stringResource(R.string.content_pattern, pattern.rule.id)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(DIAGRAM_ASPECT)
                .padding(vertical = 8.dp),
        ) {
            val xs = pattern.nodes.map { it.position.y }
            val ys = pattern.nodes.map { it.position.x }
            fun at(system: String): Offset {
                val p = pattern.position(system)!!
                val x = (p.y - xs.min()) / (xs.max() - xs.min())
                val y = (p.x - ys.min()) / (ys.max() - ys.min())
                return Offset(size.width * (0.3f + 0.4f * x), size.height * (0.04f + 0.92f * y))
            }
            val segments = pattern.routes.map { at(it.a) to at(it.b) }
            for ((a, b) in segments) drawLine(Ink, a, b, strokeWidth = 2.dp.toPx())
            val radius = 5.dp.toPx()
            val gap = 4.dp.toPx()
            val taken = pattern.nodes.map { node -> Rect(at(node.system), radius + gap) }.toMutableList()
            for (node in pattern.nodes) {
                val center = at(node.system)
                drawCircle(Paper, radius, center)
                drawCircle(RocketRed, radius, center, style = Stroke(2.dp.toPx()))
                val label = measurer.measure(labels.getValue(node.system), labelStyle)
                val w = label.size.width.toFloat()
                val h = label.size.height.toFloat()
                val reach = radius + gap
                val candidates = listOf(
                    Offset(center.x + reach, center.y - h / 2),
                    Offset(center.x - reach - w, center.y - h / 2),
                    Offset(center.x + reach, center.y - reach - h),
                    Offset(center.x + reach, center.y + reach),
                    Offset(center.x - reach - w, center.y - reach - h),
                    Offset(center.x - reach - w, center.y + reach),
                ).map { Rect(it, Size(w, h)) }
                val canvas = Rect(Offset.Zero, size)
                val box = candidates.firstOrNull { r ->
                    canvas.contains(r.topLeft) && canvas.contains(r.bottomRight - Offset(1f, 1f)) &&
                        taken.none { it.overlaps(r) } &&
                        segments.none { (a, b) -> crosses(r, a, b) }
                } ?: candidates.first()
                taken += box
                drawText(label, topLeft = box.topLeft)
            }
        }
    }
}

/** Whether the segment [a]-[b] passes through [rect], sampled finely enough for a label. */
private fun crosses(rect: Rect, a: Offset, b: Offset): Boolean =
    (0..SEGMENT_SAMPLES).any { rect.contains(a + (b - a) * (it / SEGMENT_SAMPLES.toFloat())) }

private const val SEGMENT_SAMPLES = 64

/** Width over height of the upright diagram. */
private const val DIAGRAM_ASPECT = 0.8f

@Composable
private fun SystemCard(system: StarSystem, content: LoadedContent) {
    val locale = currentLocale()
    RecordCard(stringResource(R.string.content_system, content.names.system(system.id), system.rule.id)) {
        RecordLine(stringResource(R.string.content_wealth), Formats.integer(system.wealth, locale))
        RecordLine(stringResource(R.string.content_tech), Formats.integer(system.tech, locale))
        for (planet in system.planets) {
            StackedLine(
                stringResource(R.string.content_planet, planet.number),
                stringResource(
                    R.string.content_planet_value,
                    planet.tiles[0],
                    planet.tiles[1],
                    Formats.integer(planet.gravity, locale),
                    stringResource(airName(planet.air)),
                ),
            )
        }
        if (system.asteroids) RecordLine(stringResource(R.string.content_asteroids), "")
        Text(stringResource(R.string.content_entry_events), style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp))
        val entryRoll = stringResource(R.string.content_entry_event)
        Text(
            system.entryEvents.mapIndexed { i, event -> entryRoll.format(Formats.integer(i + 1, locale), event.id) }.joinToString("  "),
            style = TeletypeStyles.small,
            color = Ink,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        val neighbors = content.game.pattern().neighbors(system.id).joinToString { content.names.system(it) }
        StackedLine(stringResource(R.string.content_jumps), neighbors)
    }
}

/** A label with its value below it, for values too long to share a ruled line with the label. */
@Composable
private fun StackedLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Text(value, style = TeletypeStyles.medium, color = Ink)
    }
}

/**
 * The tile scans with their transcribed data drawn on top, to check SMUG-11 against the
 * original: a circle per area in its restriction color, its connectors and the seam slots.
 */
@Composable
private fun TileCard(content: LoadedContent) {
    val tiles = content.game.tiles.values.toList()
    var selected by rememberSaveable { mutableStateOf(tiles.first().id) }
    val tile = content.game.tile(selected)
    RecordCard(stringResource(R.string.content_tile, tile.id)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in tiles) {
                Text(
                    if (t.planetSide == null) stringResource(R.string.content_tile_asteroids) else t.id,
                    style = TeletypeStyles.medium,
                    color = if (t.id == selected) Paper else Ink,
                    modifier = Modifier
                        .background(if (t.id == selected) RocketRed else PaperShade)
                        .clickable { selected = t.id }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        TileOverlay(tile)
        Text(stringResource(R.string.content_tile_legend), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 4.dp))
        tile.areas.forEachIndexed { index, area ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    stringResource(R.string.content_tile_area, index + 1, content.names.areaType(area.type.key)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
                Text(areaDetails(area), style = TeletypeStyles.small, color = InkFaded)
            }
        }
        HorizontalDivider(Modifier.fillMaxWidth().padding(vertical = 6.dp), thickness = 1.dp, color = Ink)
        for (connector in tile.connectors) {
            val from = content.names.areaType(tile.area(connector.from)!!.type.key)
            val to = connector.to?.let { content.names.areaType(tile.area(it)!!.type.key) }
                ?: stringResource(R.string.content_tile_edge, connector.edgeSlot ?: 0)
            val steps = connector.steps
            val text = when {
                connector.kind == ConnectorKind.TRIPLE -> stringResource(R.string.content_connector_triple, from, to)
                steps == null -> stringResource(R.string.content_connector_other_tile, from, to)
                else -> pluralStringResource(R.plurals.content_connector_steps, steps, from, to, steps)
            }
            Text(text, style = TeletypeStyles.small, color = Ink, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

/** The scan of [tile] with its areas (numbered circles), connectors and seam slots drawn on top. */
@Composable
private fun TileOverlay(tile: Tile) {
    val context = LocalContext.current
    val bitmap = remember(tile.image) {
        context.assets.open(tile.image).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TeletypeStyles.small.copy(color = Paper, fontWeight = FontWeight.Bold)
    Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
        Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            fun at(p: MapPoint) = Offset(p.x * size.width, p.y * size.height)
            val edgeX = if (tile.planetSide == PlanetSide.LEFT) 0f else size.width
            for (c in tile.connectors) {
                val from = at(tile.area(c.from)!!.position)
                val to = c.to?.let { at(tile.area(it)!!.position) } ?: Offset(edgeX, EDGE_SLOT_Y[c.edgeSlot!!] * size.height)
                val stroke = if (c.kind == ConnectorKind.TRIPLE) 6.dp.toPx() else 3.dp.toPx()
                val dash = if (c.kind == ConnectorKind.SINGLE) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())) else null
                drawLine(ChromeTurquoise, from, to, strokeWidth = stroke, pathEffect = dash)
                c.steps?.let { steps ->
                    val label = measurer.measure(steps.toString(), labelStyle)
                    val mid = (from + to) / 2f
                    drawCircle(SpaceBlack, 10.dp.toPx(), mid)
                    drawText(label, topLeft = mid - Offset(label.size.width / 2f, label.size.height / 2f))
                }
            }
            tile.areas.forEachIndexed { index, area ->
                val center = at(area.position)
                drawCircle(restrictionColor(area.restriction), 13.dp.toPx(), center)
                drawCircle(SpaceBlack, 13.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                val ink = if (area.restriction == Restriction.WHITE) SpaceBlack else Paper
                val label = measurer.measure((index + 1).toString(), labelStyle.copy(color = ink))
                drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
            }
        }
    }
}

/** Heights of the three seam slots on the scans, top to bottom. */
private val EDGE_SLOT_Y = listOf(0.23f, 0.52f, 0.81f)

private fun restrictionColor(restriction: Restriction): Color = when (restriction) {
    Restriction.WHITE -> Paper
    Restriction.YELLOW -> ChromeOchre
    Restriction.RED -> RocketRed
    Restriction.PURPLE -> PURPLE
}

/** Purple areas (r204g); the theme palette has no purple of its own. */
private val PURPLE = Color(0xFF7B4FA0)

@Composable
private fun areaDetails(area: Area): String {
    val services = buildList {
        add(stringResource(restrictionName(area.restriction)))
        if (area.ownAir) add(stringResource(R.string.content_own_air))
        if (area.bank) add(stringResource(R.string.content_bank_mark))
        if (area.medical) add(stringResource(R.string.content_medical_mark))
    }
    return services.joinToString(stringResource(R.string.list_separator))
}

private fun airName(air: Air): Int = when (air) {
    Air.GOOD -> R.string.air_good
    Air.BAD -> R.string.air_bad
    Air.NONE -> R.string.air_none
}

private fun restrictionName(restriction: Restriction): Int = when (restriction) {
    Restriction.WHITE -> R.string.restriction_white
    Restriction.YELLOW -> R.string.restriction_yellow
    Restriction.RED -> R.string.restriction_red
    Restriction.PURPLE -> R.string.restriction_purple
}
