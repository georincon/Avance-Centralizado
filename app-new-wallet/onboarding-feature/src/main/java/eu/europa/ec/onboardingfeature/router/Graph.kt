package eu.europa.ec.onboardingfeature.router

import androidx.compose.runtime.remember
import org.koin.compose.koinInject
import eu.europa.ec.onboardingfeature.data.OtherDataStore
import eu.europa.ec.onboardingfeature.data.PersonalInfoStore
import eu.europa.ec.onboardingfeature.ui.otherdata.OtherDataScreen
import eu.europa.ec.onboardingfeature.ui.personalinfo.PersonalInfoScreen
import eu.europa.ec.onboardingfeature.ui.personalinfo.AcademicIssuanceScreen
import eu.europa.ec.onboardingfeature.ui.personalinfo.PidIssuanceRequestScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import eu.europa.ec.onboardingfeature.data.OnboardingStatus
import eu.europa.ec.onboardingfeature.data.OnboardingStore
import eu.europa.ec.onboardingfeature.navigation.OnboardingNavHost
import eu.europa.ec.onboardingfeature.ui.OnboardingState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.OnboardingScreens

fun NavGraphBuilder.featureOnboardingGraph(navController: NavController) {
    navigation(
        startDestination = OnboardingScreens.Onboarding.screenRoute,
        route = ModuleRoute.OnboardingModule.route
    ) {
        composable(route = OnboardingScreens.Onboarding.screenRoute) {
            val context = LocalContext.current.applicationContext
            val personalInfoStore = koinInject<PersonalInfoStore>()
            val otherDataStore = koinInject<OtherDataStore>()
            val state = remember {
                OnboardingState(OnboardingStore(context), OnboardingStatus(context), personalInfoStore, otherDataStore)
            }
            // rememberSaveable: se ejecuta solo al entrar al flujo, no en recreaciones de la pantalla.
            rememberSaveable { state.discardStaleCaptures(); true }
            OnboardingNavHost(
                state = state,
                onFinished = {
                    navController.navigate(DashboardScreens.Dashboard.screenRoute) {
                        popUpTo(OnboardingScreens.Onboarding.screenRoute) { inclusive = true }
                    }
                }
            )
        }

        composable(route = OnboardingScreens.PersonalInfo.screenRoute) {
            PersonalInfoScreen(
                onBack = { navController.popBackStack() },
                onRequestPid = { navController.navigate(OnboardingScreens.PidIssuanceRequest.screenRoute) },
                onRequestAcademic = { navController.navigate(OnboardingScreens.AcademicIssuance.screenRoute) }
            )
        }
        composable(route = OnboardingScreens.OtherData.screenRoute) {
            OtherDataScreen(onBack = { navController.popBackStack() })
        }
        composable(route = OnboardingScreens.AcademicIssuance.screenRoute) {
            AcademicIssuanceScreen(
                onBack = { navController.popBackStack() },
                onIssued = {
                    navController.navigate(DashboardScreens.Dashboard.screenRoute) {
                        popUpTo(DashboardScreens.Dashboard.screenRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(route = OnboardingScreens.PidIssuanceRequest.screenRoute) {
            PidIssuanceRequestScreen(
                onBack = { navController.popBackStack() },
                onIssued = {
                    navController.navigate(DashboardScreens.Dashboard.screenRoute) {
                        popUpTo(DashboardScreens.Dashboard.screenRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}


