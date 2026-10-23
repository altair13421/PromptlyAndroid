package com.promptly.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.promptly.app.ui.screens.create.CreateReminderScreen
import com.promptly.app.ui.screens.create.CreateReminderViewModel
import com.promptly.app.ui.screens.detail.ReminderDetailScreen
import com.promptly.app.ui.screens.detail.ReminderDetailViewModel
import com.promptly.app.ui.screens.home.HomeScreen
import com.promptly.app.ui.screens.home.HomeViewModel
import com.promptly.app.ui.screens.settings.SettingsScreen
import com.promptly.app.ui.screens.settings.SettingsViewModel

object Routes {
    const val HOME = "home"
    const val CREATE = "create"
    const val EDIT = "edit/{id}"
    const val DETAIL = "detail/{id}"
    const val SETTINGS = "settings"

    fun edit(id: Long) = "edit/$id"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun PromptlyNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            val vm: HomeViewModel = hiltViewModel()
            val state = vm.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state.value,
                onTabSelected = vm::onTabSelected,
                onReminderClick = { id -> nav.navigate(Routes.detail(id)) },
                onCreateClick = { nav.navigate(Routes.CREATE) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
                onMarkDone = vm::onMarkDone,
                onDelete = vm::onDelete,
            )
        }

        composable(Routes.CREATE) {
            val vm: CreateReminderViewModel = hiltViewModel()
            val state = vm.uiState.collectAsStateWithLifecycle()
            CreateReminderScreen(
                state = state.value,
                onTitleChange = vm::onTitleChange,
                onNotesChange = vm::onNotesChange,
                onTimeChange = vm::onTimeChange,
                onDateChange = vm::onDateChange,
                onRecurrenceChange = vm::onRecurrenceChange,
                onRingtoneChange = vm::onRingtoneChange,
                onCustomRingtonePicked = vm::onCustomRingtonePicked,
                onPicturePicked = vm::onPicturePicked,
                onCreateCameraCapture = vm::createCameraCaptureFile,
                onPictureCaptured = vm::onPictureCaptured,
                onPictureCaptureCancelled = vm::onPictureCaptureCancelled,
                onPictureRemoved = vm::onPictureRemoved,
                onSave = { vm.onSave { nav.popBackStack() } },
                onCancel = { nav.popBackStack() },
            )
        }

        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            val vm: CreateReminderViewModel = hiltViewModel()
            LaunchedEffect(id) { vm.loadForEdit(id) }
            val state = vm.uiState.collectAsStateWithLifecycle()
            CreateReminderScreen(
                state = state.value,
                isEdit = true,
                onTitleChange = vm::onTitleChange,
                onNotesChange = vm::onNotesChange,
                onTimeChange = vm::onTimeChange,
                onDateChange = vm::onDateChange,
                onRecurrenceChange = vm::onRecurrenceChange,
                onRingtoneChange = vm::onRingtoneChange,
                onCustomRingtonePicked = vm::onCustomRingtonePicked,
                onPicturePicked = vm::onPicturePicked,
                onCreateCameraCapture = vm::createCameraCaptureFile,
                onPictureCaptured = vm::onPictureCaptured,
                onPictureCaptureCancelled = vm::onPictureCaptureCancelled,
                onPictureRemoved = vm::onPictureRemoved,
                onSave = { vm.onSave { nav.popBackStack() } },
                onCancel = { nav.popBackStack() },
            )
        }

        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            val vm: ReminderDetailViewModel = hiltViewModel()
            vm.load(id)
            val state = vm.uiState.collectAsStateWithLifecycle()
            ReminderDetailScreen(
                state = state.value,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
                onSnooze = vm::onSnooze,
                onMarkDone = { vm.onMarkDone(); nav.popBackStack() },
                onDelete = { vm.onDelete(); nav.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = hiltViewModel()
            val state = vm.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state.value,
                onBack = { nav.popBackStack() },
                onDarkModeChange = vm::onDarkModeChange,
                onDynamicColorChange = vm::onDynamicColorChange,
                onRingtonePicked = vm::onRingtonePicked,
                onVibrateChange = vm::onVibrateChange,
                onExportBackup = vm::onExportBackup,
                onRestoreBackup = vm::onRestoreBackup,
            )
        }
    }
}
