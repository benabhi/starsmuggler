package com.starsmuggler.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.starsmuggler.R
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.GearRef
import com.starsmuggler.engine.Loan
import com.starsmuggler.engine.SheetEdit
import com.starsmuggler.engine.Skill
import com.starsmuggler.engine.Stat
import com.starsmuggler.engine.gear
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.RecordLine
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.formatSees
import com.starsmuggler.ui.game.GameSession
import com.starsmuggler.ui.game.STARSHIP_GUNS
import com.starsmuggler.ui.game.gearName
import com.starsmuggler.ui.game.rejectionText
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.PaperShade
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles

/** The four sections of the record sheet (P6). */
private enum class SheetTab(@StringRes val label: Int, @StringRes val title: Int) {
    Duke(R.string.sheet_tab_duke, R.string.sheet_duke),
    Ship(R.string.sheet_tab_ship, R.string.sheet_ship),
    Money(R.string.sheet_tab_money, R.string.sheet_money),
    Papers(R.string.sheet_tab_papers, R.string.sheet_papers),
}

/**
 * The record sheet (SMUG-26) in tabs, read from the game state. With «Edit» on, every value can
 * be changed by hand for an event the engine does not resolve yet (PRD P2); each change is an
 * engine action, logged and saved like any other.
 */
@Composable
fun RecordSheet(session: GameSession, game: GameState) {
    var tab by rememberSaveable { mutableStateOf(SheetTab.Duke) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var payDialog by remember { mutableStateOf(false) }
    val edit = { change: SheetEdit -> session.perform(Action.EditSheet(change)) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(PaperShade)) {
            for (entry in SheetTab.entries) {
                val active = entry == tab
                Box(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { tab = entry }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(entry.label).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) RocketRed else InkFaded,
                        maxLines = 1,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = InkFaded)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StampButton(
                stringResource(if (editing) R.string.sheet_editing else R.string.sheet_edit),
                style = if (editing) StampStyle.Solid else StampStyle.Outline,
                onClick = { editing = !editing },
            )
            if (editing) Text(stringResource(R.string.sheet_edit_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded)
            session.rejection?.let { Text(stringResource(rejectionText(it)), style = TeletypeStyles.small, color = RocketRed) }
            when (tab) {
                SheetTab.Duke -> DukeTab(session, game, editing, edit)
                SheetTab.Ship -> ShipTab(session, game, editing, edit)
                SheetTab.Money -> MoneyTab(session, game, editing, onPay = { payDialog = true })
                SheetTab.Papers -> PapersTab(session, game, editing, edit)
            }
        }
    }
    if (payDialog) PayDialog(session, game, onClose = { payDialog = false })
}

@Composable
private fun DukeTab(session: GameSession, game: GameState, editing: Boolean, edit: (SheetEdit) -> Unit) {
    val duke = game.duke
    val locale = currentLocale()
    var adding by remember { mutableStateOf(false) }
    RecordCard(stringResource(R.string.sheet_duke)) {
        for ((stat, label, value) in listOf(
            Triple(Stat.MARKSMANSHIP, R.string.engine_marksmanship, duke.marksmanship),
            Triple(Stat.MELEE, R.string.engine_melee, duke.melee),
            Triple(Stat.ENDURANCE, R.string.engine_endurance, duke.endurance),
            Triple(Stat.ASTUCIA, R.string.engine_astucia, duke.astucia),
        )) {
            EditLine(
                stringResource(label),
                Formats.integer(value, locale),
                editing,
                onMinus = { edit(SheetEdit.SetStat(stat, value - 1)) }.takeIf { value - 1 in stat.range },
                onPlus = { edit(SheetEdit.SetStat(stat, value + 1)) }.takeIf { value + 1 in stat.range },
            )
        }
        for (skill in duke.skills) RecordLine(stringResource(R.string.engine_skill), stringResource(skillName(skill)))
    }
    RecordCard(stringResource(R.string.sheet_personal_gear)) {
        if (duke.items.isEmpty()) Text(stringResource(R.string.sheet_nothing), style = TeletypeStyles.small, color = InkFaded)
        duke.items.indices.forEach { index -> GearLine(session, game, GearRef.DukeItem(index), editing, edit, removable = true) }
        if (editing) {
            StampButton(stringResource(R.string.sheet_add_item), onClick = { adding = !adding }, modifier = Modifier.padding(vertical = 8.dp))
            if (adding) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (id in session.content.game.equipment.keys) {
                        Text(
                            session.content.names.equipment(id),
                            style = TeletypeStyles.small,
                            color = RocketRed,
                            modifier = Modifier
                                .clickable {
                                    edit(SheetEdit.AddItem(id, 1))
                                    adding = false
                                }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShipTab(session: GameSession, game: GameState, editing: Boolean, edit: (SheetEdit) -> Unit) {
    val ship = game.ship
    val locale = currentLocale()
    RecordCard(stringResource(R.string.sheet_ship)) {
        GearLine(session, game, GearRef.Starship, editing, edit)
        EditLine(
            stringResource(R.string.engine_hypercharges),
            Formats.integer(ship.hypercharges, locale),
            editing,
            onMinus = { edit(SheetEdit.SetHypercharges(ship.hypercharges - 1)) }.takeIf { ship.hypercharges > 0 },
            onPlus = { edit(SheetEdit.SetHypercharges(ship.hypercharges + 1)) }.takeIf { ship.hypercharges < SheetEdit.COUNT.last },
        )
        if (ship.guns != null) GearLine(session, game, GearRef.StarshipGuns, editing, edit)
        RecordLine(session.content.names.equipment(STASIS_UNIT), stringResource(R.string.engine_capacity_units, Formats.integer(ship.stasis, locale)))
    }
    ship.boat?.let { boat ->
        RecordCard(stringResource(R.string.sheet_boat)) {
            GearLine(session, game, GearRef.Boat, editing, edit)
            EditLine(
                stringResource(R.string.engine_boat_fuel),
                Formats.integer(boat.fuel, locale),
                editing,
                onMinus = { edit(SheetEdit.SetBoatFuel(boat.fuel - 1)) }.takeIf { boat.fuel > 0 },
                onPlus = { edit(SheetEdit.SetBoatFuel(boat.fuel + 1)) }.takeIf { boat.fuel < SheetEdit.COUNT.last },
            )
        }
    }
    RecordCard(stringResource(R.string.sheet_cargo)) {
        Text(stringResource(R.string.sheet_cargo_coming), style = TeletypeStyles.small, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun MoneyTab(session: GameSession, game: GameState, editing: Boolean, onPay: () -> Unit) {
    val loan = game.loan
    val locale = currentLocale()
    RecordCard(stringResource(R.string.sheet_cash)) {
        RecordLine(stringResource(R.string.engine_cash), formatSees(game.cash))
        if (editing) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                for (step in CASH_STEPS) {
                    StampButton(Formats.signed(step, locale), enabled = game.cash + step >= 0, onClick = { session.perform(Action.AdjustCash(step)) })
                }
            }
        }
    }
    RecordCard(stringResource(R.string.engine_loan)) {
        RecordLine(stringResource(R.string.engine_principal), formatSees(loan.principal))
        RecordLine(stringResource(R.string.sheet_weekly_interest), formatSees(if (loan.isPaidOff) 0 else Loan.WEEKLY_INTEREST))
        Box(Modifier.padding(vertical = 6.dp)) { LoanLine(game) }
        StampButton(
            stringResource(R.string.bridge_pay),
            enabled = session.check(Action.PayPrincipal(1)) == null,
            onClick = onPay,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun PapersTab(session: GameSession, game: GameState, editing: Boolean, edit: (SheetEdit) -> Unit) {
    val standing = game.standing
    RecordCard(stringResource(R.string.engine_papers)) {
        ToggleLine(
            stringResource(R.string.engine_papers),
            stringResource(if (standing.papersInOrder) R.string.engine_papers_in_order else R.string.engine_papers_missing),
            alarm = !standing.papersInOrder,
            editing = editing,
            onToggle = { edit(SheetEdit.SetPapers(!standing.papersInOrder)) },
        )
    }
    RecordCard(stringResource(R.string.sheet_wanted)) {
        Text(stringResource(R.string.sheet_wanted_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 4.dp))
        for (system in session.content.game.systems.keys) {
            val wanted = system in standing.wantedIn
            ToggleLine(
                session.content.names.system(system),
                stringResource(if (wanted) R.string.sheet_wanted_yes else R.string.sheet_wanted_no),
                alarm = wanted,
                editing = editing,
                onToggle = { edit(SheetEdit.SetWanted(system, !wanted)) },
            )
        }
    }
}

/** A piece of gear: tech and state, and when editing its tech, a breakdown switch and, for items, removal. */
@Composable
private fun GearLine(session: GameSession, game: GameState, ref: GearRef, editing: Boolean, edit: (SheetEdit) -> Unit, removable: Boolean = false) {
    val status = game.gear(ref) ?: return
    val locale = currentLocale()
    val name = gearName(ref, game, session.content)
    val state = stringResource(if (status.broken) R.string.engine_gear_broken else R.string.engine_gear_ok)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        RecordLine(name, stringResource(R.string.sheet_gear_value, Formats.integer(status.tech, locale), state))
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                // Gear above the range (such as raised on the engine bench) comes straight down into it.
                val lower = (status.tech - 1).coerceAtMost(SheetEdit.TECH.last)
                SmallControl(stringResource(R.string.sheet_tech_down), enabled = lower in SheetEdit.TECH) { edit(SheetEdit.SetTech(ref, lower)) }
                SmallControl(stringResource(R.string.sheet_tech_up), enabled = status.tech + 1 in SheetEdit.TECH) { edit(SheetEdit.SetTech(ref, status.tech + 1)) }
                SmallControl(stringResource(if (status.broken) R.string.sheet_repair else R.string.sheet_break)) { edit(SheetEdit.SetBroken(ref, !status.broken)) }
                if (removable && ref is GearRef.DukeItem) SmallControl(stringResource(R.string.sheet_remove)) { edit(SheetEdit.RemoveItem(ref.index)) }
            }
        }
    }
}

/** A value with − and + next to it while editing; a null action greys its button out. */
@Composable
private fun EditLine(label: String, value: String, editing: Boolean, onMinus: (() -> Unit)?, onPlus: (() -> Unit)?) {
    if (!editing) {
        RecordLine(label, value)
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        SmallControl(stringResource(R.string.sheet_minus), enabled = onMinus != null) { onMinus?.invoke() }
        Text(value, style = TeletypeStyles.medium, color = Ink, modifier = Modifier.padding(horizontal = 12.dp))
        SmallControl(stringResource(R.string.sheet_plus), enabled = onPlus != null) { onPlus?.invoke() }
    }
}

/** A yes/no value that flips with a tap while editing. */
@Composable
private fun ToggleLine(label: String, value: String, alarm: Boolean, editing: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = editing, onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        Text(value, style = TeletypeStyles.medium, color = if (alarm) RocketRed else InkFaded)
    }
}

@Composable
private fun SmallControl(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = TeletypeStyles.medium,
        color = if (enabled) RocketRed else InkFaded.copy(alpha = 0.4f),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

private fun skillName(skill: Skill): Int = when (skill) {
    Skill.PILOT_NAVIGATOR -> R.string.engine_skill_pilot
    Skill.STARSHIP_GUNNER -> R.string.engine_skill_gunner
}

private val CASH_STEPS = listOf(-1000, -100, -10, 10, 100, 1000)
private const val STASIS_UNIT = "stasis_unit" // not-ui
