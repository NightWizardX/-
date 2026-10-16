package com.nwx.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nwx.expensetracker.data.Period
import com.nwx.expensetracker.data.PeriodType
import com.nwx.expensetracker.data.Repository
import com.nwx.expensetracker.ui.AppTheme
import com.nwx.expensetracker.ui.CategoriesScreen
import com.nwx.expensetracker.ui.DashboardScreen
import com.nwx.expensetracker.ui.EditExpenseScreen
import com.nwx.expensetracker.ui.SettingsScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme { App() }
        }
    }
}

private sealed interface Screen {
    data object Dashboard : Screen
    data class Edit(val expenseId: Long?, val date: LocalDate) : Screen
    data object Settings : Screen
    data object Categories : Screen
}

@Composable
private fun App() {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Dashboard)) }
    var period by remember { mutableStateOf(Period.of(repo.lastPeriodType, LocalDate.now())) }
    val dashboardListState = rememberLazyListState()

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // Slide screens in from the right when going forward, back to the right when going back.
    AnimatedContent(
        targetState = stack,
        contentKey = { it.last() },
        transitionSpec = {
            val forward = targetState.size >= initialState.size
            if (forward) {
                ((slideInHorizontally(tween(320)) { it } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(320))))
                    .apply { targetContentZIndex = 1f }
            } else {
                ((slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(320))))
                    .apply { targetContentZIndex = -1f }
            }
        },
        label = "nav",
    ) { s ->
        when (val screen = s.last()) {
            Screen.Dashboard -> DashboardScreen(
                period = period,
                listState = dashboardListState,
                onPeriodChange = {
                    period = it
                    repo.lastPeriodType = it.type
                },
                onAdd = {
                    // When looking at a past day, new entries default to that day.
                    val date = if (period.type == PeriodType.DAY) period.start else LocalDate.now()
                    push(Screen.Edit(null, date))
                },
                onEdit = { push(Screen.Edit(it, LocalDate.now())) },
                onSettings = { push(Screen.Settings) },
            )
            is Screen.Edit -> key(screen) {
                EditExpenseScreen(
                    expenseId = screen.expenseId,
                    defaultDate = screen.date,
                    onDone = ::pop,
                )
            }
            Screen.Settings -> SettingsScreen(onBack = ::pop, onCategories = { push(Screen.Categories) })
            Screen.Categories -> CategoriesScreen(onBack = ::pop)
        }
    }
}
