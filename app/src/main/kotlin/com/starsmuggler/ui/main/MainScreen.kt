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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starsmuggler.BuildConfig
import com.starsmuggler.R
import com.starsmuggler.ui.components.ActionCard
import com.starsmuggler.ui.components.HourDial
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.debug.ContentScreen
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

/** Navigable mock-up of the main screen with fake data (SMUG-5). */
@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(MainTab.Bridge) }
    var game by rememberSaveable(stateSaver = MockGameSaver) { mutableStateOf(MockGame()) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    var showContent by rememberSaveable { mutableStateOf(false) }

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
    // Back from any other tab returns to the bridge before leaving the app.
    BackHandler(enabled = tab != MainTab.Bridge) { tab = MainTab.Bridge }

    PaperBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ClockStrip(game, Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
            LocationLine()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    MainTab.Bridge -> BridgePane(game, onGame = { game = it })
                    MainTab.Map -> MockPane(R.string.mock_map_title, R.string.mock_map_body)
                    MainTab.Sheet -> SheetPane()
                    MainTab.Log -> LogPane()
                    MainTab.Rules -> MockPane(R.string.mock_rules_title, R.string.mock_rules_body) {
                        if (BuildConfig.DEBUG) {
                            StampButton(stringResource(R.string.open_gallery), onClick = { showGallery = true })
                            StampButton(stringResource(R.string.open_content), onClick = { showContent = true })
                        }
                    }
                }
            }
            TabBar(tab, onSelect = { tab = it })
        }
    }
}

@Composable
private fun ClockStrip(game: MockGame, modifier: Modifier = Modifier) {
    val hoursLeftText = pluralStringResource(R.plurals.dial_hours_left, game.hoursLeft, game.hoursLeft)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HourDial(hoursUsed = game.hoursUsed, description = hoursLeftText, size = 84.dp, showNumbers = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.clock_week_day, game.week, game.day),
                style = MaterialTheme.typography.headlineMedium,
                color = RocketRed,
            )
            Text(hoursLeftText, style = MaterialTheme.typography.titleSmall, color = Ink)
            val payment = if (game.daysToPayment == 0) {
                stringResource(R.string.loan_due_today, formatSees(game.weeklyInterest))
            } else {
                pluralStringResource(R.plurals.loan_due_in_days, game.daysToPayment, game.daysToPayment, formatSees(game.weeklyInterest))
            }
            Text(payment, style = TeletypeStyles.small, color = if (game.daysToPayment <= 2) RocketRed else InkFaded)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.money_label).uppercase(), style = MaterialTheme.typography.labelSmall, color = InkFaded)
            Text(formatSees(game.money), style = TeletypeStyles.large, color = Ink)
        }
    }
    HorizontalDivider(thickness = 2.dp, color = Ink)
}

@Composable
private fun LocationLine() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PaperShade)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.location_line, stringResource(R.string.mock_location_system), stringResource(R.string.mock_location_area)).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.mock_location_air), style = TeletypeStyles.small, color = ChromeOchre)
    }
    HorizontalDivider(thickness = 1.dp, color = InkFaded)
}

@Composable
private fun BridgePane(game: MockGame, onGame: (MockGame) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.bridge_actions).uppercase(), style = MaterialTheme.typography.labelMedium, color = RocketRed)
        mockActions.forEach { action ->
            val hours = if (action.hours == WHOLE_DAY) game.hoursLeft else action.hours
            val noTime = game.hoursLeft < hours || game.hoursLeft == 0
            val reason = when {
                action.disabledReason != null -> stringResource(action.disabledReason)
                noTime -> stringResource(R.string.reason_no_time)
                else -> null
            }
            ActionCard(
                title = stringResource(action.title),
                description = action.description?.let { stringResource(it) },
                cost = if (action.hours == WHOLE_DAY) stringResource(R.string.cost_whole_day) else formatHours(hours),
                ruleRef = action.ruleRef,
                disabledReason = reason,
                onClick = { onGame(game.spend(hours)) },
            )
        }
        Spacer(Modifier.height(4.dp))
        StampButton(
            stringResource(R.string.end_day),
            onClick = { onGame(game.endDay()) },
            style = StampStyle.Solid,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SheetPane() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.mock_sheet_tabs).uppercase(), style = MaterialTheme.typography.labelMedium, color = InkFaded)
        RecordCard(stringResource(R.string.gallery_card_title)) {
            RecordLine(stringResource(R.string.stat_marksmanship), "4")
            RecordLine(stringResource(R.string.stat_hand_to_hand), "3")
            RecordLine(stringResource(R.string.stat_endurance), "5")
            RecordLine(stringResource(R.string.stat_cunning), "2")
        }
        Text(stringResource(R.string.mock_pending), style = MaterialTheme.typography.bodySmall, color = InkFaded)
    }
}

@Composable
private fun LogPane() {
    val lines = stringArrayResource(R.array.mock_log_lines)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.mock_log_title).uppercase(), style = MaterialTheme.typography.labelMedium, color = RocketRed)
        lines.forEach { Text(it, style = TeletypeStyles.medium, color = Ink) }
    }
}

@Composable
private fun MockPane(title: Int, body: Int, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
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
            MainTab.entries.forEach { tab ->
                val active = tab == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onSelect(tab) }
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
