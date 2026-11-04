package com.starsmuggler.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.starsmuggler.R
import com.starsmuggler.engine.Action
import com.starsmuggler.engine.Clock
import com.starsmuggler.engine.GameState
import com.starsmuggler.engine.LandRoute
import com.starsmuggler.engine.TravelMode
import com.starsmuggler.engine.content.ConnectorKind
import com.starsmuggler.ui.Formats
import com.starsmuggler.ui.components.RecordCard
import com.starsmuggler.ui.components.StampButton
import com.starsmuggler.ui.components.StampStyle
import com.starsmuggler.ui.currentLocale
import com.starsmuggler.ui.formatSees
import com.starsmuggler.ui.game.GameSession
import com.starsmuggler.ui.game.hoursText
import com.starsmuggler.ui.game.locationText
import com.starsmuggler.ui.game.rejectionText
import com.starsmuggler.ui.game.shipPositionText
import com.starsmuggler.ui.game.travelModeText
import com.starsmuggler.ui.theme.Ink
import com.starsmuggler.ui.theme.InkFaded
import com.starsmuggler.ui.theme.RocketRed
import com.starsmuggler.ui.theme.TeletypeStyles

/** Travel over land to a neighboring area, on foot, by commercial vehicle or by skimmer (r203a, r215). */
@Composable
internal fun TravelDialog(session: GameSession, game: GameState, onRent: () -> Unit, onClose: () -> Unit) {
    val content = session.content
    val locale = currentLocale()
    Dialog(onDismissRequest = onClose) {
        RecordCard(stringResource(R.string.bridge_travel)) {
            Text(
                stringResource(R.string.travel_hint, locationText(game.location, content)),
                style = MaterialTheme.typography.bodySmall,
                color = InkFaded,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            val rental = game.rental?.takeIf { it.day == game.clock.day && it.until > game.clock.used }
            Text(
                if (rental == null) {
                    stringResource(R.string.travel_no_rental)
                } else {
                    stringResource(R.string.travel_rental, Formats.integer(rental.capacity, locale), hoursText(rental.until - game.clock.used))
                },
                style = TeletypeStyles.small,
                color = Ink,
            )
            StampButton(stringResource(R.string.travel_rent), onClick = onRent, modifier = Modifier.padding(vertical = 8.dp))
            val routes = session.landRoutes()
            if (routes.isEmpty()) Text(stringResource(R.string.travel_no_routes), style = TeletypeStyles.small, color = InkFaded)
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                for (route in routes) RouteRow(session, route, onClose)
            }
            StampButton(stringResource(R.string.engine_cancel), onClick = onClose, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

@Composable
private fun RouteRow(session: GameSession, route: LandRoute, onClose: () -> Unit) {
    val locale = currentLocale()
    HorizontalDivider(color = InkFaded)
    Text(locationText(route.to, session.content).uppercase(), style = MaterialTheme.typography.labelMedium, color = Ink, modifier = Modifier.padding(top = 8.dp))
    Text(
        if (route.kind == ConnectorKind.TRIPLE) {
            stringResource(R.string.travel_triple)
        } else {
            pluralStringResource(R.plurals.travel_steps, route.steps ?: 1, Formats.integer(route.steps ?: 1, locale))
        },
        style = TeletypeStyles.small,
        color = InkFaded,
    )
    val reasons = mutableListOf<Int>()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        for (mode in TravelMode.entries) {
            val action = Action.Travel(mode, route.to)
            val why = session.check(action)
            if (why != null) reasons += rejectionText(why)
            val time = if (mode == TravelMode.ON_FOOT && route.walkingDays > 0) {
                pluralStringResource(R.plurals.travel_days, route.walkingDays, Formats.integer(route.walkingDays, locale))
            } else {
                hoursText(if (mode == TravelMode.ON_FOOT) Clock.HOUR else route.vehicleTenths)
            }
            StampButton(
                stringResource(R.string.travel_mode_time, stringResource(travelModeText(mode)), time),
                enabled = why == null && !session.busy,
                onClick = {
                    session.perform(action)
                    onClose()
                },
            )
        }
    }
    for (reason in reasons.distinct()) Text(stringResource(reason), style = TeletypeStyles.small, color = RocketRed)
}

/** Renting a commercial vehicle, as a commercial transport event offers (e074, r215b): 1 S per 3 UC per hour, in advance. */
@Composable
internal fun RentDialog(session: GameSession, game: GameState, onClose: () -> Unit) {
    val locale = currentLocale()
    var capacity by remember { mutableIntStateOf(3) }
    var hours by remember { mutableIntStateOf(1) }
    val maxHours = (game.clock.left / Clock.HOUR).coerceAtLeast(1)
    Dialog(onDismissRequest = onClose) {
        RecordCard(stringResource(R.string.travel_rent)) {
            Text(stringResource(R.string.rent_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                for (size in listOf(3, 6, 9)) {
                    StampButton(
                        stringResource(R.string.rent_capacity, Formats.integer(size, locale)),
                        style = if (size == capacity) StampStyle.Solid else StampStyle.Outline,
                        onClick = { capacity = size },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                StampButton(stringResource(R.string.rent_less), enabled = hours > 1, onClick = { hours-- })
                Text(hoursText(hours * Clock.HOUR), style = TeletypeStyles.large, color = Ink, modifier = Modifier.padding(horizontal = 8.dp))
                StampButton(stringResource(R.string.rent_more), enabled = hours < maxHours, onClick = { hours++ })
            }
            val action = Action.RentVehicle(capacity, hours)
            val why = session.check(action)
            Text(stringResource(R.string.rent_cost, formatSees(capacity / 3 * hours)), style = TeletypeStyles.small, color = Ink)
            why?.let { Text(stringResource(rejectionText(it)), style = TeletypeStyles.small, color = RocketRed) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                StampButton(stringResource(R.string.rent_confirm), style = StampStyle.Solid, enabled = why == null, onClick = {
                    session.perform(action)
                    onClose()
                })
                StampButton(stringResource(R.string.engine_cancel), onClick = onClose)
            }
        }
    }
}

/** A walk that lasts several days goes on, one land step per day (r203a). */
@Composable
internal fun TrekLine(game: GameState, session: GameSession) {
    val trek = game.trek ?: return
    Text(
        pluralStringResource(R.plurals.trek_status, trek.stepsLeft, locationText(trek.to, session.content), Formats.integer(trek.stepsLeft, currentLocale())),
        style = TeletypeStyles.small,
        color = RocketRed,
    )
}

/** The ship's boat: out of the starship, flown with its time and fuel, and back in (r214). */
@Composable
internal fun BoatDialog(session: GameSession, game: GameState, onClose: () -> Unit) {
    val locale = currentLocale()
    val boat = game.ship.boat
    Dialog(onDismissRequest = onClose) {
        RecordCard(stringResource(R.string.bridge_boat)) {
            val position = boat?.position
            Text(
                if (position == null) {
                    stringResource(R.string.boat_hint_stowed)
                } else {
                    stringResource(R.string.boat_hint_out, shipPositionText(position, session.content), Formats.integer(boat.fuel, locale))
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkFaded,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            val toggle = if (position == null) Action.LaunchBoat else Action.StowBoat
            val toggleWhy = session.check(toggle)
            StampButton(
                stringResource(if (position == null) R.string.boat_launch else R.string.boat_stow),
                style = StampStyle.Solid,
                enabled = toggleWhy == null && !session.busy,
                onClick = { session.perform(toggle) },
            )
            if (position == null) toggleWhy?.let { Text(stringResource(rejectionText(it)), style = TeletypeStyles.small, color = RocketRed) }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val reasons = mutableListOf<Int>()
                for (move in session.boatMoves()) {
                    val action = Action.MoveBoat(move.to)
                    val why = session.check(action)
                    if (why != null) reasons += rejectionText(why)
                    StampButton(
                        stringResource(R.string.boat_move, shipPositionText(move.to, session.content), hoursText(move.tenths), Formats.integer(move.fuel, locale)),
                        enabled = why == null && !session.busy,
                        onClick = {
                            session.perform(action)
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                for (reason in reasons.distinct()) Text(stringResource(reason), style = TeletypeStyles.small, color = RocketRed)
            }
            StampButton(stringResource(R.string.engine_cancel), onClick = onClose, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

/** A seat on the orbital shuttle to another spaceport or station of the planet (r215a). */
@Composable
internal fun ShuttleDialog(session: GameSession, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        RecordCard(stringResource(R.string.bridge_shuttle)) {
            Text(stringResource(R.string.shuttle_hint), style = MaterialTheme.typography.bodySmall, color = InkFaded, modifier = Modifier.padding(vertical = 6.dp))
            for (stop in session.shuttleStops()) {
                val action = Action.Shuttle(stop)
                val why = session.check(action)
                StampButton(
                    locationText(stop, session.content),
                    enabled = why == null && !session.busy,
                    onClick = {
                        session.perform(action)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                why?.let { Text(stringResource(rejectionText(it)), style = TeletypeStyles.small, color = RocketRed) }
            }
            StampButton(stringResource(R.string.engine_cancel), onClick = onClose, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}
