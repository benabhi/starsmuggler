package com.starsmuggler.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.starsmuggler.BuildConfig
import com.starsmuggler.content.AssetContentSource
import com.starsmuggler.content.ContentLoader
import com.starsmuggler.content.LoadedContent
import com.starsmuggler.content.TextLanguage
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.Astucia
import com.starsmuggler.engine.AstuciaSetup
import com.starsmuggler.engine.Ending
import com.starsmuggler.engine.won
import com.starsmuggler.engine.Clock
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.Loan
import com.starsmuggler.engine.content.Air
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.dice.DiceOptions
import com.starsmuggler.ui.dice.LocalDice
import com.starsmuggler.ui.game.GameSession
import com.starsmuggler.ui.sound.LocalSoundBoard
import com.starsmuggler.ui.sound.SoundEffect
import com.starsmuggler.ui.sound.SoundOptions
import com.starsmuggler.ui.game.endingText
import com.starsmuggler.ui.game.hoursText
import com.starsmuggler.ui.game.logText
import com.starsmuggler.ui.game.rejectionText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.starsmuggler.R
import com.starsmuggler.ui.components.ActionCard
import com.starsmuggler.ui.components.HourDial
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.debug.ContentScreen
import com.starsmuggler.ui.debug.EngineScreen
import com.starsmuggler.ui.gallery.ComponentGallery
import com.starsmuggler.ui.theme.ChromeOchre
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.PaperBackground
import com.starsmuggler.ui.theme.PaperShade
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles
import com.starsmuggler.ui.formatHours
import com.starsmuggler.ui.formatSees

/** The five sections of the bottom bar (PRD section 7). */
enum class MainTab(val label: Int) {
    Bridge(R.string.tab_bridge),
    Map(R.string.tab_map),
    Sheet(R.string.tab_sheet),
    Log(R.string.tab_log),
    Rules(R.string.tab_rules),
}

/** The main screen: the game played with the engine (SMUG-25), with its five sections. */
@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(MainTab.Bridge) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    var showContent by rememberSaveable { mutableStateOf(false) }
    var showEngine by rememberSaveable { mutableStateOf(false) }
    var showRecords by rememberSaveable { mutableStateOf(false) }

    if (showGallery) {
        BackHandler { showGallery = false }
        ComponentGallery()
        return
    }
    if (showContent) {
        BackHandler { showContent = false }
        ContentScreen()
        return
    }
    if (showEngine) {
        BackHandler { showEngine = false }
        EngineScreen()
        return
    }
    // Back from any other tab returns to the bridge before leaving the app.
    BackHandler(enabled = tab != MainTab.Bridge) { tab = MainTab.Bridge }

    val context = LocalContext.current
    val language = TextLanguage.resolve(currentLocale().toLanguageTag())
    val loaded by produceState<Result<LoadedContent>?>(null, language) {
        value = withContext(Dispatchers.IO) {
            runCatching { ContentLoader(AssetContentSource(context.assets)).load(language) }
        }
    }
    val scope = rememberCoroutineScope()
    val content = loaded?.getOrNull()
    val dice = LocalDice.current
    val sounds = LocalSoundBoard.current
    val session = remember(content) { content?.let { GameSession(it, context.filesDir, scope, dice, sounds) } }
    LaunchedEffect(session) { session?.start() }
    val game = session?.game

    if (showRecords && session != null) {
        BackHandler { showRecords = false }
        RecordsScreen(session.games)
        return
    }

    PaperBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val top = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            if (session == null || game == null) {
                Box(top.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        loaded?.exceptionOrNull()?.message ?: stringResource(R.string.content_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (loaded?.isFailure == true) RocketRed else Ink,
                    )
                }
                TabBar(tab, onSelect = { tab = it })
                return@Column
            }
            ClockStrip(game, top)
            LocationLine(game, session.content)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    MainTab.Bridge -> BridgePane(session, game, onRecords = { showRecords = true })
                    MainTab.Map -> MockPane(R.string.mock_map_title, R.string.mock_map_body)
                    MainTab.Sheet -> RecordSheet(session, game)
                    MainTab.Log -> LogPane(game, session.content)
                    MainTab.Rules -> MockPane(R.string.mock_rules_title, R.string.mock_rules_body) {
                        DiceOptions(dice, Modifier.padding(vertical = 8.dp))
                        sounds?.let { SoundOptions(it, Modifier.padding(bottom = 8.dp)) }
                        StampButton(stringResource(R.string.open_records), onClick = { showRecords = true })
                        if (BuildConfig.DEBUG) {
                            StampButton(stringResource(R.string.open_gallery), onClick = { showGallery = true })
                            StampButton(stringResource(R.string.open_content), onClick = { showContent = true })
                            StampButton(stringResource(R.string.open_engine), onClick = { showEngine = true })
                        }
                    }
                }
            }
            TabBar(tab, onSelect = { tab = it })
        }
    }
}

@Composable
private fun ClockStrip(game: GameState, modifier: Modifier = Modifier) {
    val clock = game.clock
    val hoursLeftText = if (clock.overtime > 0) {
        stringResource(R.string.clock_overtime, hoursText(clock.overtime))
    } else {
        stringResource(R.string.clock_hours_left, hoursText(clock.left))
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HourDial(hoursUsed = clock.used.toFloat() / Clock.HOUR, description = hoursLeftText, size = 84.dp, showNumbers = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.clock_week_day, clock.week, clock.dayOfWeek),
                style = MaterialTheme.typography.headlineMedium,
                color = RocketRed,
            )
            Text(hoursLeftText, style = MaterialTheme.typography.titleSmall, color = if (clock.overtime > 0) RocketRed else Ink)
            LoanLine(game)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.money_label).uppercase(), style = MaterialTheme.typography.labelSmall, color = InkFaded)
            Text(formatSees(game.cash), style = TeletypeStyles.large, color = Ink)
        }
    }
    HorizontalDivider(thickness = 2.dp, color = Ink)
}

/** What is coming with the loan (r203e): the next interest, the deadline of a default, or the end of it. */
@Composable
internal fun LoanLine(game: GameState) {
    val loan = game.loan
    val locale = currentLocale()
    val due = loan.interestDueIn(game.clock)
    val deadline = loan.deadline
    val text = when {
        loan.isPaidOff -> stringResource(R.string.loan_paid_off)
        loan.repossession -> stringResource(R.string.loan_repossession)
        loan.isDefaulted && deadline != null -> stringResource(R.string.loan_defaulted, formatSees(loan.principal), Formats.integer(deadline, locale))
        due == null -> ""
        // The interest falls due when today ends.
        due == 1 -> stringResource(R.string.loan_due_today, formatSees(Loan.WEEKLY_INTEREST))
        else -> pluralStringResource(R.plurals.loan_due_in_days, due - 1, due - 1, formatSees(Loan.WEEKLY_INTEREST))
    }
    val urgent = loan.isDefaulted || (due != null && due <= 3 && game.cash < Loan.WEEKLY_INTEREST)
    Text(text, style = TeletypeStyles.small, color = if (urgent || due == 1) RocketRed else InkFaded)
}

/** Where Duke is: system, area and its air (r204f). */
@Composable
private fun LocationLine(game: GameState, content: LoadedContent) {
    val place = game.location
    val area = content.game.tiles[place.tile]?.areas?.firstOrNull { it.id == place.area }
    val planet = content.game.systems[place.system]?.planets?.firstOrNull { it.number == place.planet }
    val air = when {
        area?.ownAir == true -> R.string.air_own
        planet?.air == Air.BAD -> R.string.air_bad
        planet?.air == Air.NONE -> R.string.air_none
        else -> R.string.air_good
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(PaperShade)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.location_line, content.names.system(place.system), area?.let { content.names.areaType(it.type.key) } ?: place.area).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(air).uppercase(), style = TeletypeStyles.small, color = ChromeOchre)
    }
    HorizontalDivider(thickness = 1.dp, color = InkFaded)
}

/** The day's dashboard (PRD section 7.2): what can be done now, its cost in hours, and why not when it cannot. */
@Composable
private fun BridgePane(session: GameSession, game: GameState, onRecords: () -> Unit) {
    var eventDialog by remember { mutableStateOf(false) }
    var endDialog by remember { mutableStateOf(false) }
    var newGameDialog by remember { mutableStateOf(false) }
    var payDialog by remember { mutableStateOf(false) }
    val enabled = !session.busy
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        session.rejection?.let { Text(stringResource(rejectionText(it)), style = TeletypeStyles.small, color = RocketRed) }
        if (session.rollCancelled) Text(stringResource(R.string.roll_cancelled), style = TeletypeStyles.small, color = RocketRed)
        if (session.lastEntries.isNotEmpty()) {
            RecordCard(stringResource(R.string.bridge_last_result)) {
                for (entry in session.lastEntries) {
                    Text(logText(entry, game, session.content), style = TeletypeStyles.small, color = Ink, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
        if (game.ending != null) {
            EndedCard(game, onNewGame = { newGameDialog = true }, onRecords = onRecords)
            return@Column
        }
        Text(stringResource(R.string.bridge_actions).uppercase(), style = MaterialTheme.typography.labelMedium, color = RocketRed)
        ActionCard(
            title = stringResource(R.string.bridge_event),
            description = stringResource(R.string.bridge_event_desc),
            cost = stringResource(R.string.cost_by_event),
            disabledReason = null,
            onClick = { if (enabled) eventDialog = true },
        )
        val astucia = Action.AstuciaRoll()
        ActionCard(
            title = stringResource(R.string.bridge_astucia),
            description = stringResource(R.string.bridge_astucia_desc, Formats.integer(game.duke.astucia, currentLocale())),
            cost = formatHours(0),
            ruleRef = "r202",
            disabledReason = session.check(astucia)?.let { stringResource(rejectionText(it)) },
            onClick = { session.perform(astucia) },
        )
        ActionCard(
            title = stringResource(R.string.bridge_pay),
            description = stringResource(R.string.bridge_pay_desc, formatSees(game.loan.principal)),
            cost = formatHours(0),
            ruleRef = "r203e",
            disabledReason = session.check(Action.PayPrincipal(1))?.let { stringResource(rejectionText(it)) },
            onClick = { if (enabled) payDialog = true },
        )
        for (coming in comingActions) {
            ActionCard(
                title = stringResource(coming.title),
                description = stringResource(coming.description),
                cost = stringResource(coming.cost),
                ruleRef = coming.ruleRef,
                disabledReason = stringResource(coming.comingWith),
                onClick = {},
            )
        }
        ActionCard(
            title = stringResource(R.string.bridge_end_game),
            description = stringResource(R.string.bridge_end_game_desc),
            cost = stringResource(R.string.cost_none),
            ruleRef = "r203f",
            disabledReason = null,
            onClick = { if (enabled) endDialog = true },
        )
        Spacer(Modifier.height(4.dp))
        StampButton(
            stringResource(R.string.end_day),
            onClick = { session.perform(Action.EndDay) },
            enabled = enabled,
            style = StampStyle.Solid,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
    }
    if (eventDialog) EventDialog(session, game, onClose = { eventDialog = false })
    if (payDialog) PayDialog(session, game, onClose = { payDialog = false })
    if (endDialog) EndGameDialog(session, onClose = { endDialog = false })
    if (newGameDialog) NewGameDialog(session, onClose = { newGameDialog = false })
}

/** What the game came to, and the way to the next one (r203f, P10). */
@Composable
private fun EndedCard(game: GameState, onNewGame: () -> Unit, onRecords: () -> Unit) {
    val ending = checkNotNull(game.ending)
    val locale = currentLocale()
    RecordCard(stringResource(R.string.ended_title)) {
        Text(
            stringResource(endingText(ending, game.won)),
            style = MaterialTheme.typography.titleMedium,
            color = if (game.won) Ink else RocketRed,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        RecordLine(stringResource(R.string.record_days), Formats.integer(game.clock.day, locale))
        RecordLine(stringResource(R.string.engine_cash), formatSees(game.cash))
        RecordLine(stringResource(R.string.record_debt), formatSees(game.loan.principal))
        RecordLine(stringResource(R.string.engine_astucia), Formats.integer(game.duke.astucia, locale))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 10.dp)) {
            StampButton(stringResource(R.string.ended_new_game), style = StampStyle.Solid, onClick = onNewGame)
            StampButton(stringResource(R.string.open_records), onClick = onRecords)
        }
    }
}

/** Retire, or record that Duke died (r203f). Either way the game ends for good. */
@Composable
private fun EndGameDialog(session: GameSession, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        RecordCard(stringResource(R.string.bridge_end_game)) {
            Text(stringResource(R.string.end_game_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                StampButton(stringResource(R.string.end_game_retire), onClick = {
                    session.endGame(Ending.RETIRED)
                    onClose()
                })
                StampButton(stringResource(R.string.end_game_died), onClick = {
                    session.endGame(Ending.DIED)
                    onClose()
                })
                StampButton(stringResource(R.string.engine_cancel), onClick = onClose)
            }
        }
    }
}

/** The next game's Astucia (r202): the change the rules suggest, accepted, inverted or left out. */
@Composable
private fun NewGameDialog(session: GameSession, onClose: () -> Unit) {
    val suggestion = session.suggestion
    val locale = currentLocale()
    Dialog(onDismissRequest = onClose) {
        RecordCard(stringResource(R.string.ended_new_game)) {
            if (suggestion == null) {
                Text(stringResource(R.string.new_game_roll), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
                StampButton(stringResource(R.string.new_game_start), style = StampStyle.Solid, onClick = {
                    session.newGame(AstuciaSetup.Roll)
                    onClose()
                })
            } else {
                Text(
                    stringResource(
                        if (suggestion.lastWon) R.string.new_game_suggest_won else R.string.new_game_suggest_lost,
                        Formats.integer(suggestion.previous, locale),
                        Formats.signed(suggestion.adjustment, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaded,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                for ((label, setup) in listOf(
                    R.string.new_game_accept to suggestion.accept,
                    R.string.new_game_invert to suggestion.invert,
                    R.string.new_game_keep to suggestion.keep,
                )) {
                    val adjust = setup as AstuciaSetup.Adjust
                    StampButton(
                        stringResource(label, Formats.integer(Astucia.adjusted(adjust.previous, adjust.adjustment), locale)),
                        onClick = {
                            session.newGame(setup)
                            onClose()
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            StampButton(stringResource(R.string.engine_cancel), onClick = onClose, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

/** Time taken by an event resolved by hand (PRD P2): an event runs into overtime rather than the next day (r203). */
@Composable
private fun EventDialog(session: GameSession, game: GameState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        RecordCard(stringResource(R.string.bridge_event)) {
            Text(stringResource(R.string.bridge_event_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (hours in 1..3) {
                    StampButton(formatHours(hours), onClick = {
                        session.perform(Action.PassTime(hours * Clock.HOUR))
                        onClose()
                    })
                }
                StampButton(
                    stringResource(R.string.bridge_rest_of_day),
                    enabled = game.clock.left > 0,
                    onClick = {
                        session.perform(Action.PassRestOfDay)
                        onClose()
                    },
                )
            }
            StampButton(stringResource(R.string.engine_cancel), onClick = onClose, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

/** Paying part or all of the loan's principal from cash (r203e). */
@Composable
internal fun PayDialog(session: GameSession, game: GameState, onClose: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    val all = minOf(game.cash, game.loan.principal)
    Dialog(onDismissRequest = onClose) {
        RecordCard(stringResource(R.string.bridge_pay)) {
            Text(
                stringResource(R.string.bridge_pay_hint, formatSees(game.cash), formatSees(game.loan.principal)),
                style = MaterialTheme.typography.bodySmall,
                color = InkFaded,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { text -> amount = text.filter { it.isDigit() }.take(9) },
                label = { Text(stringResource(R.string.bridge_pay_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            val sees = amount.toIntOrNull() ?: 0
            val why = session.check(Action.PayPrincipal(sees))
            if (amount.isNotEmpty() && why != null) {
                Text(stringResource(rejectionText(why)), style = TeletypeStyles.small, color = RocketRed, modifier = Modifier.padding(vertical = 4.dp))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 10.dp),
            ) {
                StampButton(stringResource(R.string.bridge_pay_confirm), style = StampStyle.Solid, enabled = why == null, onClick = {
                    session.perform(Action.PayPrincipal(sees))
                    onClose()
                })
                StampButton(stringResource(R.string.bridge_pay_all, formatSees(all)), enabled = all > 0, onClick = {
                    session.perform(Action.PayPrincipal(all))
                    onClose()
                })
                StampButton(stringResource(R.string.engine_cancel), onClick = onClose)
            }
        }
    }
}

/** An action of a later plan, shown greyed out so the bridge already has its shape. */
private class ComingAction(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val cost: Int,
    val ruleRef: String,
    @StringRes val comingWith: Int,
)

private val comingActions = listOf(
    ComingAction(R.string.bridge_travel, R.string.bridge_travel_desc, R.string.cost_by_route, "r203a", R.string.coming_plan_5),
    ComingAction(R.string.bridge_contact, R.string.bridge_contact_desc, R.string.cost_by_event, "r227", R.string.coming_plan_6),
    ComingAction(R.string.bridge_trade, R.string.bridge_trade_desc, R.string.cost_by_event, "r229", R.string.coming_plan_7),
    ComingAction(R.string.bridge_rrr, R.string.bridge_rrr_desc, R.string.cost_whole_day, "r203c", R.string.coming_plan_6),
)

/** The game's history, newest first (PRD section 7, log). */
@Composable
private fun LogPane(game: GameState, content: LoadedContent) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.log_title).uppercase(), style = MaterialTheme.typography.labelMedium, color = RocketRed)
        }
        items(game.log.asReversed()) { entry ->
            Text(logText(entry, game, content), style = TeletypeStyles.medium, color = Ink)
        }
    }
}

@Composable
private fun MockPane(title: Int, body: Int, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.displaySmall, color = RocketRed, textAlign = TextAlign.Center)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = Ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.mock_pending), style = MaterialTheme.typography.bodySmall, color = InkFaded, textAlign = TextAlign.Center)
        extra()
    }
}

@Composable
private fun TabBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PaperShade)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
    ) {
        HorizontalDivider(thickness = 2.dp, color = Ink)
        Row(Modifier.fillMaxWidth()) {
            val sounds = LocalSoundBoard.current
            MainTab.entries.forEach { tab ->
                val active = tab == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) {
                            sounds?.play(SoundEffect.CLICK)
                            onSelect(tab)
                        }
                        .background(if (active) RocketRed else PaperShade)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(tab.label).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else Ink,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
