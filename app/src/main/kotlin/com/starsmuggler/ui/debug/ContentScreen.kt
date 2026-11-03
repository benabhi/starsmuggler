package com.starsmuggler.ui.debug

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
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
import com.starsmuggler.engine.content.GameContent
import com.starsmuggler.engine.content.Restriction
import com.starsmuggler.engine.content.StarSystem
import com.starsmuggler.engine.content.Tile
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.PaperBackground
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    game.systems.values.forEach { SystemCard(it, content) }
    game.tiles.values.forEach { TileCard(it, content) }
}

@Composable
private fun Counts(game: GameContent) {
    val locale = currentLocale()
    RecordCard(stringResource(R.string.content_counts)) {
        RecordLine(stringResource(R.string.content_format), Formats.integer(GameContent.FORMAT_VERSION, locale))
        RecordLine(stringResource(R.string.content_systems), Formats.integer(game.systems.size, locale))
        RecordLine(stringResource(R.string.content_tiles), Formats.integer(game.tiles.size, locale))
        RecordLine(stringResource(R.string.content_tables), Formats.integer(game.tables.size, locale))
        RecordLine(stringResource(R.string.content_equipment), Formats.integer(game.equipment.size, locale))
    }
}

@Composable
private fun SystemCard(system: StarSystem, content: LoadedContent) {
    val locale = currentLocale()
    RecordCard(stringResource(R.string.content_system, content.names.system(system.id), system.rule.id)) {
        RecordLine(stringResource(R.string.content_wealth), Formats.integer(system.wealth, locale))
        RecordLine(stringResource(R.string.content_tech), Formats.integer(system.tech, locale))
        for (planet in system.planets) {
            RecordLine(
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
        RecordLine(stringResource(R.string.content_jumps), neighbors)
    }
}

@Composable
private fun TileCard(tile: Tile, content: LoadedContent) {
    RecordCard(stringResource(R.string.content_tile, tile.id)) {
        for (area in tile.areas) {
            // Name above its details: the details are too long to share a ruled line with it.
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(content.names.areaType(area.type.key), style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text(areaDetails(area), style = TeletypeStyles.small, color = InkFaded)
            }
        }
        HorizontalDivider(Modifier.fillMaxWidth().padding(vertical = 6.dp), thickness = 1.dp, color = Ink)
        for (connector in tile.connectors) {
            val from = content.names.areaType(tile.area(connector.from)!!.type.key)
            val to = connector.to?.let { content.names.areaType(tile.area(it)!!.type.key) }
                ?: stringResource(R.string.content_tile_edge, connector.edgeSlot ?: 0)
            val text = when (connector.kind) {
                ConnectorKind.TRIPLE -> stringResource(R.string.content_connector_triple, from, to)
                ConnectorKind.SINGLE -> pluralStringResource(R.plurals.content_connector_steps, connector.steps ?: 0, from, to, connector.steps ?: 0)
            }
            Text(text, style = TeletypeStyles.small, color = Ink, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

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
