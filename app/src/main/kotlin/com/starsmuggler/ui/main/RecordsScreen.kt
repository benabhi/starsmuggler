package com.starsmuggler.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.data.GameRecord
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.formatSees
import com.starsmuggler.ui.game.endingText
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.PaperBackground
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles

/** The records (SMUG-24, P10): the best fortune, the longest game and every finished game, newest first. */
@Composable
fun RecordsScreen(games: List<GameRecord>) {
    val locale = currentLocale()
    PaperBackground(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(stringResource(R.string.records_title), style = MaterialTheme.typography.headlineLarge, color = RocketRed)
            }
            if (games.isEmpty()) {
                item { Text(stringResource(R.string.records_empty), style = MaterialTheme.typography.bodyLarge, color = InkFaded) }
                return@LazyColumn
            }
            item {
                val richest = games.maxBy { it.netWorth }
                val longest = games.maxBy { it.days }
                RecordCard(stringResource(R.string.records_best)) {
                    RecordLine(stringResource(R.string.records_best_fortune), formatSees(richest.netWorth))
                    RecordLine(stringResource(R.string.records_longest), pluralDays(longest.days))
                    RecordLine(stringResource(R.string.records_played), Formats.integer(games.size, locale))
                    RecordLine(stringResource(R.string.records_won), Formats.integer(games.count { it.won }, locale))
                }
            }
            items(games.asReversed()) { game ->
                RecordCard(Formats.dateTime(game.endedAt, locale)) {
                    Text(
                        stringResource(endingText(game.ending, game.won)),
                        style = TeletypeStyles.medium,
                        color = if (game.won) Ink else RocketRed,
                    )
                    RecordLine(stringResource(R.string.record_days), Formats.integer(game.days, locale))
                    RecordLine(stringResource(R.string.engine_cash), formatSees(game.cash))
                    RecordLine(stringResource(R.string.record_debt), formatSees(game.debt))
                    RecordLine(stringResource(R.string.records_net), formatSees(game.netWorth))
                    RecordLine(stringResource(R.string.engine_astucia), Formats.integer(game.astucia, locale))
                }
            }
        }
    }
}

@Composable
private fun pluralDays(days: Int): String =
    pluralStringResource(R.plurals.records_days, days, Formats.integer(days, currentLocale()))
