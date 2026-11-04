package com.starsmuggler.ui.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.starsmuggler.R
import com.starsmuggler.content.AssetContentSource
import com.starsmuggler.content.ContentLoader
import com.starsmuggler.content.LoadedContent
import com.starsmuggler.content.TextLanguage
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.Astucia
import com.starsmuggler.engine.AstuciaSetup
import com.starsmuggler.engine.Clock
import com.starsmuggler.engine.ActionResult
import com.starsmuggler.engine.Engine
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.LogEntry
import com.starsmuggler.engine.Rejection
import com.starsmuggler.engine.Skill
import com.starsmuggler.engine.dice.DiceHand
import com.starsmuggler.engine.dice.DiceRoll
import com.starsmuggler.engine.dice.RollRequest
import com.starsmuggler.engine.dice.RollSource
import com.starsmuggler.engine.dice.Rng
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.formatSees
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.PaperBackground
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * A bench for the engine core (SMUG-18): start a game from a seed, roll on the game's tables with
 * the app's dice or the player's own, read the log and check that a saved game loads the same.
 * Debug builds only.
 */
@Composable
fun EngineScreen() {
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
            Text(stringResource(R.string.engine_title), style = MaterialTheme.typography.headlineLarge, color = RocketRed)
            Text(stringResource(R.string.engine_subtitle), style = MaterialTheme.typography.labelMedium, color = InkFaded)
            val loaded = result
            when {
                loaded == null -> Text(stringResource(R.string.content_loading), style = MaterialTheme.typography.bodyLarge, color = Ink)
                loaded.isFailure -> Text(loaded.exceptionOrNull()?.message.orEmpty(), style = TeletypeStyles.small, color = RocketRed)
                else -> EngineBench(loaded.getOrThrow())
            }
        }
    }
}

/** A physical roll waiting for the player's faces. */
private class PendingRoll(val request: RollRequest, val answer: CompletableDeferred<List<Int>>)

@Composable
private fun EngineBench(content: LoadedContent) {
    val engine = remember(content) { Engine(content.game) }
    val scope = rememberCoroutineScope()
    var seedText by remember { mutableStateOf(DEFAULT_SEED.toString()) }
    var gameState by remember { mutableStateOf<GameState?>(null) }
    var laterGame by remember { mutableStateOf(false) }
    var previousAstucia by remember { mutableStateOf(3) }
    var adjustment by remember { mutableStateOf(0) }
    var ownDice by remember { mutableStateOf(false) }
    var openTable by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingRoll?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var roundTrip by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val locale = currentLocale()

    fun hand(): DiceHand = if (ownDice) {
        DiceHand.physical { request -> CompletableDeferred<List<Int>>().also { pending = PendingRoll(request, it) }.await() }
    } else {
        DiceHand.APP
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            try {
                block()
            } catch (_: CancellationException) {
                notice = R.string.engine_roll_cancelled
            } finally {
                pending = null
                busy = false
            }
        }
    }

    fun perform(action: Action) = run {
        when (val done = engine.perform(checkNotNull(gameState), action, hand())) {
            is ActionResult.Done -> gameState = done.state
            is ActionResult.Rejected -> notice = rejectionText(done.reason)
        }
    }

    fun newGame() = run {
        val astucia = if (laterGame) AstuciaSetup.Adjust(previousAstucia, adjustment) else AstuciaSetup.Roll
        gameState = engine.newGame(seedText.toLongOrNull() ?: DEFAULT_SEED, astucia, hand())
        roundTrip = null
    }

    LaunchedEffect(engine) {
        if (gameState == null) gameState = engine.newGame(DEFAULT_SEED)
    }
    pending?.let { roll ->
        Dialog(onDismissRequest = { roll.answer.cancel() }) {
            RecordCard(stringResource(R.string.engine_own_dice)) {
                OwnDiceEntry(roll, onCancel = { roll.answer.cancel() })
            }
        }
    }
    val game = gameState ?: return

    RecordCard(stringResource(R.string.engine_game)) {
        OutlinedTextField(
            value = seedText,
            onValueChange = { text -> seedText = text.filter { it.isDigit() || it == '-' }.take(19) },
            label = { Text(stringResource(R.string.engine_seed)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        AstuciaSetupPicker(
            laterGame = laterGame,
            previous = previousAstucia,
            adjustment = adjustment,
            onLaterGame = { laterGame = it },
            onPrevious = { previousAstucia = it },
            onAdjustment = { adjustment = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StampButton(stringResource(R.string.engine_new_game), enabled = !busy, onClick = ::newGame)
            StampButton(stringResource(R.string.engine_save_load), enabled = !busy, onClick = {
                val json = Json.encodeToString(game)
                val loaded = Json.decodeFromString<GameState>(json)
                roundTrip = json.toByteArray().size to (loaded == game)
                gameState = loaded
            })
        }
        RecordLine(stringResource(R.string.engine_step), Formats.integer(game.step, locale))
        RecordLine(stringResource(R.string.engine_seed), game.seed.toString())
        RecordLine(stringResource(R.string.engine_rng), java.lang.Long.toHexString(game.rng.state))
        RecordLine(stringResource(R.string.engine_log_size), Formats.integer(game.log.size, locale))
        roundTrip?.let { (bytes, same) ->
            Text(
                stringResource(if (same) R.string.engine_round_trip_same else R.string.engine_round_trip_differs, Formats.integer(bytes, locale)),
                style = TeletypeStyles.small,
                color = if (same) Ink else RocketRed,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }

    DukeCard(game, content, enabled = !busy, perform = ::perform)

    ClockCard(game, enabled = !busy, perform = ::perform)

    RecordCard(stringResource(R.string.engine_dice)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            StampButton(
                stringResource(R.string.engine_app_dice),
                style = if (ownDice) StampStyle.Outline else StampStyle.Solid,
                enabled = !busy,
                onClick = { ownDice = false },
            )
            StampButton(
                stringResource(R.string.engine_own_dice),
                style = if (ownDice) StampStyle.Solid else StampStyle.Outline,
                enabled = !busy,
                onClick = { ownDice = true },
            )
        }
        notice?.let { Text(stringResource(it), style = TeletypeStyles.small, color = RocketRed, modifier = Modifier.padding(vertical = 4.dp)) }
    }

    RecordCard(stringResource(R.string.engine_tables)) {
        Text(stringResource(R.string.engine_tables_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 4.dp))
        for (table in content.game.tables.values) {
            val dice = table.dice?.toString() ?: stringResource(R.string.content_no_dice)
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.content_table_line, table.id, dice),
                    style = TeletypeStyles.medium,
                    color = Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) {
                            if (table.rows.size == 1) perform(Action.RollOnTable(table.id)) else openTable = table.id.takeIf { it != openTable }
                        }
                        .padding(vertical = 6.dp),
                )
                if (openTable == table.id) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (row in table.rows) {
                            Text(
                                rowLabel(table.id, row.key, content),
                                style = TeletypeStyles.small,
                                color = RocketRed,
                                modifier = Modifier.clickable(enabled = !busy) { perform(Action.RollOnTable(table.id, row.key)) }.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    RecordCard(stringResource(R.string.engine_log)) {
        for (entry in game.log.asReversed()) {
            Text(logText(entry, content), style = TeletypeStyles.small, color = Ink, modifier = Modifier.padding(vertical = 3.dp))
        }
    }
}

/** How Astucia is set for the next game (r202): roll 1d6, or adjust the previous one. */
@Composable
private fun AstuciaSetupPicker(
    laterGame: Boolean,
    previous: Int,
    adjustment: Int,
    onLaterGame: (Boolean) -> Unit,
    onPrevious: (Int) -> Unit,
    onAdjustment: (Int) -> Unit,
) {
    val locale = currentLocale()
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StampButton(stringResource(R.string.engine_first_game), style = if (laterGame) StampStyle.Outline else StampStyle.Solid, onClick = { onLaterGame(false) })
            StampButton(stringResource(R.string.engine_later_game), style = if (laterGame) StampStyle.Solid else StampStyle.Outline, onClick = { onLaterGame(true) })
        }
        if (!laterGame) {
            Text(stringResource(R.string.engine_astucia_roll_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded)
        } else {
            Text(stringResource(R.string.engine_previous_astucia), style = MaterialTheme.typography.labelMedium, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (value in Astucia.MIN..Astucia.MAX) {
                    Text(
                        Formats.integer(value, locale),
                        style = TeletypeStyles.medium,
                        color = if (value == previous) RocketRed else InkFaded,
                        modifier = Modifier.clickable { onPrevious(value) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(stringResource(R.string.engine_adjustment), style = MaterialTheme.typography.labelMedium, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((value, label) in listOf(Astucia.suggestion(won = true) to R.string.engine_adjust_down, 0 to R.string.engine_adjust_none, Astucia.suggestion(won = false) to R.string.engine_adjust_up)) {
                    Text(
                        stringResource(label),
                        style = TeletypeStyles.medium,
                        color = if (value == adjustment) RocketRed else InkFaded,
                        modifier = Modifier.clickable { onAdjustment(value) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.engine_astucia_result, Formats.integer(Astucia.adjusted(previous, adjustment), locale)),
                style = TeletypeStyles.small,
                color = RocketRed,
            )
            Text(stringResource(R.string.engine_adjust_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded)
        }
    }
}

/** Duke Springer and his ship as the game has them (SMUG-21). */
@Composable
private fun DukeCard(game: GameState, content: LoadedContent, enabled: Boolean, perform: (Action) -> Unit) {
    val duke = game.duke
    val ship = game.ship
    val locale = currentLocale()
    val names = content.names
    RecordCard(stringResource(R.string.engine_duke)) {
        RecordLine(stringResource(R.string.engine_marksmanship), Formats.integer(duke.marksmanship, locale))
        RecordLine(stringResource(R.string.engine_melee), Formats.integer(duke.melee, locale))
        RecordLine(stringResource(R.string.engine_endurance), Formats.integer(duke.endurance, locale))
        RecordLine(stringResource(R.string.engine_astucia), Formats.integer(duke.astucia, locale))
        for (skill in duke.skills) RecordLine(stringResource(R.string.engine_skill), stringResource(skillName(skill)))
        RecordLine(stringResource(R.string.engine_cash), formatSees(game.cash))
        for (item in duke.items) {
            RecordLine(stringResource(R.string.engine_item), stringResource(R.string.engine_with_tech, names.equipment(item.id), Formats.integer(item.tech, locale)))
        }
        RecordLine(stringResource(R.string.engine_ship), stringResource(R.string.engine_with_tech, stringResource(R.string.engine_antelope), Formats.integer(ship.tech, locale)))
        RecordLine(stringResource(R.string.engine_hypercharges), Formats.integer(ship.hypercharges, locale))
        ship.guns?.let { RecordLine(names.equipment(STARSHIP_GUNS), stringResource(R.string.engine_tech, Formats.integer(it, locale))) }
        RecordLine(names.equipment(STASIS_UNIT), stringResource(R.string.engine_capacity_units, Formats.integer(ship.stasis, locale)))
        ship.boat?.let { boat ->
            RecordLine(stringResource(R.string.engine_boat), stringResource(R.string.engine_with_tech, stringResource(R.string.engine_hopper), Formats.integer(boat.tech, locale)))
            RecordLine(stringResource(R.string.engine_boat_fuel), Formats.integer(boat.fuel, locale))
        }
        RecordLine(
            stringResource(R.string.engine_papers),
            stringResource(if (game.standing.papersInOrder) R.string.engine_papers_in_order else R.string.engine_papers_missing),
        )
        RecordLine(
            stringResource(R.string.engine_wanted),
            game.standing.wantedIn.takeIf { it.isNotEmpty() }?.joinToString { names.system(it) } ?: stringResource(R.string.engine_wanted_nowhere),
        )
        val place = game.location
        val area = content.game.tiles[place.tile]?.areas?.firstOrNull { it.id == place.area }
        Text(
            stringResource(
                R.string.engine_location,
                names.system(place.system),
                Formats.integer(place.planet, locale),
                area?.let { names.areaType(it.type.key) } ?: place.area,
            ),
            style = TeletypeStyles.small,
            color = Ink,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        StampButton(stringResource(R.string.engine_astucia_check), enabled = enabled, onClick = { perform(Action.AstuciaRoll()) }, modifier = Modifier.padding(vertical = 6.dp))
    }
}

private fun skillName(skill: Skill): Int = when (skill) {
    Skill.PILOT_NAVIGATOR -> R.string.engine_skill_pilot
    Skill.STARSHIP_GUNNER -> R.string.engine_skill_gunner
}

private const val STARSHIP_GUNS = "starship_guns" // not-ui
private const val STASIS_UNIT = "stasis_unit" // not-ui

/** The day clock (SMUG-19) and actions that make it run. */
@Composable
private fun ClockCard(game: GameState, enabled: Boolean, perform: (Action) -> Unit) {
    val clock = game.clock
    val locale = currentLocale()
    RecordCard(stringResource(R.string.engine_clock)) {
        RecordLine(stringResource(R.string.engine_clock_now), stringResource(R.string.clock_week_day, clock.week, clock.dayOfWeek))
        RecordLine(stringResource(R.string.engine_clock_day), Formats.integer(clock.day, locale))
        RecordLine(stringResource(R.string.engine_clock_used), hoursText(clock.used))
        RecordLine(stringResource(R.string.engine_clock_left), hoursText(clock.left))
        RecordLine(stringResource(R.string.engine_clock_overtime), hoursText(clock.overtime))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            StampButton(stringResource(R.string.engine_pass_tenth), enabled = enabled, onClick = { perform(Action.PassTime(1)) })
            StampButton(stringResource(R.string.engine_pass_hour), enabled = enabled, onClick = { perform(Action.PassTime(Clock.HOUR)) })
            StampButton(stringResource(R.string.engine_pass_event), enabled = enabled, onClick = { perform(Action.PassTime(3 * Clock.HOUR)) })
            StampButton(stringResource(R.string.engine_pass_activity), enabled = enabled, onClick = { perform(Action.PassTime(2 * Clock.HOUR, event = false)) })
            StampButton(stringResource(R.string.engine_rest_of_day), enabled = enabled, onClick = { perform(Action.PassRestOfDay) })
            StampButton(stringResource(R.string.engine_end_day), style = StampStyle.Solid, enabled = enabled, onClick = { perform(Action.EndDay) })
        }
    }
}

/** Tenths of an hour as hours with one decimal, such as "6,5 h". */
@Composable
private fun hoursText(tenths: Int): String = stringResource(R.string.engine_hours, Formats.decimal(tenths / Clock.HOUR.toDouble(), currentLocale()))

/** A quick picker for the faces of the player's own dice: one row of 1 to 6 per die. */
@Composable
private fun OwnDiceEntry(roll: PendingRoll, onCancel: () -> Unit) {
    val faces = remember(roll) { mutableStateListOf<Int?>().apply { repeat(roll.request.dice.count) { add(null) } } }
    val locale = currentLocale()
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.engine_roll_your_dice, roll.request.dice.toString(), roll.request.table ?: roll.request.rule.id),
            style = MaterialTheme.typography.titleMedium,
            color = RocketRed,
        )
        for (die in faces.indices) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (face in 1..Rng.FACES) {
                    Text(
                        Formats.integer(face, locale),
                        style = TeletypeStyles.medium,
                        color = if (faces[die] == face) RocketRed else InkFaded,
                        modifier = Modifier.clickable { faces[die] = face }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StampButton(
                stringResource(R.string.engine_confirm),
                style = StampStyle.Solid,
                enabled = faces.all { it != null },
                onClick = { roll.answer.complete(faces.map { it!! }) },
            )
            StampButton(stringResource(R.string.engine_cancel), onClick = onCancel)
        }
    }
}

@Composable
private fun logText(entry: LogEntry, content: LoadedContent): String {
    val locale = currentLocale()
    val step = Formats.integer(entry.step, locale)
    return when (entry) {
        is LogEntry.GameStarted -> stringResource(R.string.engine_log_started, step, entry.seed.toString())
        is LogEntry.Rolled -> rolledText(step, entry.roll)
        is LogEntry.AstuciaSet -> {
            val previous = entry.previous
            if (previous == null) {
                stringResource(R.string.engine_log_astucia_rolled, step, Formats.integer(entry.astucia, locale))
            } else {
                stringResource(
                    R.string.engine_log_astucia_adjusted,
                    step,
                    Formats.integer(entry.astucia, locale),
                    Formats.integer(previous, locale),
                    Formats.signed(entry.adjustment, locale),
                )
            }
        }
        is LogEntry.StartingCash -> stringResource(R.string.engine_log_starting_cash, step, formatSees(entry.sees))
        is LogEntry.AstuciaRolled -> stringResource(
            if (entry.success) R.string.engine_log_astucia_success else R.string.engine_log_astucia_failure,
            step,
            Formats.integer(entry.total, locale),
            Formats.integer(entry.astucia, locale),
        )
        is LogEntry.TimePassed -> if (entry.overtime == 0) {
            stringResource(R.string.engine_log_time, step, hoursText(entry.tenths))
        } else {
            stringResource(R.string.engine_log_time_overtime, step, hoursText(entry.tenths), hoursText(entry.overtime))
        }
        is LogEntry.DayEnded -> if (entry.overtime == 0) {
            stringResource(R.string.engine_log_day_ended, step, Formats.integer(entry.day, locale))
        } else {
            stringResource(R.string.engine_log_day_ended_overtime, step, Formats.integer(entry.day, locale), hoursText(entry.overtime))
        }
        is LogEntry.WeekEnded -> stringResource(R.string.engine_log_week_ended, step, Formats.integer(entry.week, locale))
        is LogEntry.TableResult -> stringResource(
            R.string.engine_log_table,
            step,
            entry.table,
            rowLabel(entry.table, entry.key, content),
            Formats.integer(entry.total, locale),
            entry.outcome?.let { outcomeText(it, content) } ?: stringResource(R.string.engine_no_outcome),
        )
    }
}

@Composable
private fun rolledText(step: String, roll: DiceRoll): String {
    val locale = currentLocale()
    val faces = roll.values.joinToString(DICE_SEPARATOR) { Formats.integer(it, locale) }
    val source = stringResource(if (roll.source == RollSource.PHYSICAL) R.string.engine_source_own else R.string.engine_source_app)
    val modifier = roll.request.modifier
    return if (modifier == 0) {
        stringResource(R.string.engine_log_rolled, step, roll.request.dice.toString(), faces, Formats.integer(roll.total, locale), source)
    } else {
        stringResource(
            R.string.engine_log_rolled_modified,
            step,
            roll.request.dice.toString(),
            faces,
            Formats.integer(modifier, locale),
            Formats.integer(roll.total, locale),
            source,
        )
    }
}

private fun rejectionText(reason: Rejection): Int = when (reason) {
    Rejection.UNKNOWN_TABLE -> R.string.engine_rejected_table
    Rejection.TABLE_WITHOUT_DICE -> R.string.engine_rejected_no_dice
    Rejection.UNKNOWN_ROW -> R.string.engine_rejected_row
    Rejection.NOT_ENOUGH_HOURS -> R.string.engine_rejected_hours
    Rejection.NO_TIME -> R.string.engine_rejected_no_time
}

private const val DEFAULT_SEED = 1982L
private const val DICE_SEPARATOR = " + "
