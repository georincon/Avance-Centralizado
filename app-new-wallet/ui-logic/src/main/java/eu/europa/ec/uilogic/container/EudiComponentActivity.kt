/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.container

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.corelogic.di.getOrNullKoinScope
import eu.europa.ec.resourceslogic.theme.ThemeManager
import eu.europa.ec.uilogic.extension.exposeTestTagsAsResourceId
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.RouterHost
import eu.europa.ec.uilogic.navigation.helper.DeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import eu.europa.ec.uilogic.navigation.helper.IntentType
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.hasDeepLink
import eu.europa.ec.uilogic.navigation.helper.hasIntentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

open class EudiComponentActivity : FragmentActivity() {

    private val routerHost: RouterHost by inject()
    private val viewModel: EudiComponentActivityViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onCreate()
    }

    internal fun cacheIntent(intent: Intent?) {
        viewModel.cacheIntent(intent)
    }

    internal fun getCachedIntent(): Intent? = viewModel.getCachedIntent()

    internal fun pendingIntentSignal(): StateFlow<Int> = viewModel.pendingIntentSignal

    @Composable
    protected fun Content(
        intent: Intent?,
        builder: NavGraphBuilder.(NavController) -> Unit,
    ) {
        ThemeManager.instance.Theme {
            Surface(
                modifier = Modifier
                    .exposeTestTagsAsResourceId()
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                routerHost.StartFlow {
                    builder(it)
                }
                LaunchedEffect(Unit) {
                    viewModel.onFlowStart()
                    handleDeepLink(intent, coldBoot = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (viewModel.hasFlowStarted()) {
            handleDeepLink(intent)
        } else {
            runPendingDeepLink(intent)
        }
        keepRedrawingAfterLink()
    }

    // Al llegar un enlace desde el navegador (selector de apps), la ventana queda sin cuadros nuevos hasta el
    // siguiente toque y la pantalla de destino no llega a crearse. Se piden cuadros durante unos segundos.
    private fun keepRedrawingAfterLink() {
        lifecycleScope.launch {
            repeat(40) {
                window?.decorView?.postInvalidateOnAnimation()
                delay(100.milliseconds)
            }
        }
    }

    private fun runPendingDeepLink(intent: Intent?) {
        lifecycleScope.launch {
            var count = 0
            while (!viewModel.hasFlowStarted() && count <= 10) {
                count++
                delay(500.milliseconds)
            }
            if (count <= 10) {
                handleDeepLink(intent)
            }
        }
    }

    private fun handleDeepLink(intent: Intent?, coldBoot: Boolean = false) {
        hasDeepLink(intent?.data)?.let {
            if (it.type == DeepLinkType.ISSUANCE && !coldBoot) {
                handleDeepLinkAction(
                    routerHost.getNavController(),
                    it.link
                )
            } else if (
                it.type == DeepLinkType.CREDENTIAL_OFFER
                && !routerHost.userIsLoggedInWithDocuments()
                && routerHost.userIsLoggedInWithNoDocuments()
            ) {
                cacheIntent(intent)
                routerHost.popToIssuanceOnboardingScreen()
            } else if (it.type == DeepLinkType.OPENID4VP
                && routerHost.userIsLoggedInWithDocuments()
                && (routerHost.isScreenOnBackStackOrForeground(IssuanceScreens.AddDocument)
                        || routerHost.isScreenOnBackStackOrForeground(IssuanceScreens.DocumentOffer)
                        || routerHost.isScreenOnBackStackOrForeground(DashboardScreens.DocumentDetails))
            ) {
                handleDeepLinkAction(
                    routerHost.getNavController(),
                    DeepLinkAction(it.link, DeepLinkType.DYNAMIC_PRESENTATION)
                )
            } else if (it.type == DeepLinkType.RQES && !coldBoot) {
                // Regreso del navegador tras autorizar una firma: la firma sigue en la actividad del
                // SDK, que se retoma con el codigo. Sin esto el enlace se descartaba al ir a Inicio y
                // la wallet nunca pedia el token al servicio de firma.
                handleDeepLinkAction(
                    routerHost.getNavController(),
                    it.link
                )
            } else if (it.type != DeepLinkType.ISSUANCE) {
                cacheIntent(intent)
                if (routerHost.userIsLoggedInWithDocuments()) {
                    routerHost.popToDashboardScreen()
                    // El panel de inicio atiende el enlace guardado en cuanto recibe la senal de cacheIntent (no hace falta
                    // esperar a que la actividad vuelva a primer plano). OJO: no se vuelve a lanzar la actividad "al frente":
                    // Android ya la trae al recibir el enlace, y un segundo lanzamiento provoca una pausa y reanudacion
                    // inmediatas tras las que la pantalla no se redibuja hasta el siguiente toque.
                }
            }
            setIntent(Intent())
        } ?: hasIntentAction(intent)?.let {
            when (it.type) {
                IntentType.DC_API -> {
                    cacheIntent(it.intent)
                    if (routerHost.userIsLoggedInWithDocuments()) {
                        routerHost.popToDashboardScreen()
                    }
                }
            }
            setIntent(Intent())
        }
    }
}

@KoinViewModel
internal class EudiComponentActivityViewModel(
    private val prefKeys: PrefKeys,
    uuidProvider: UuidProvider
) : ViewModel() {

    private val sessionId: String = uuidProvider.provideUuid()

    private var flowStarted: Boolean = false
    private var pendingIntent: Intent? = null

    override fun onCleared() {
        getOrNullKoinScope(sessionId)?.close()
    }

    fun onCreate() {
        setSessionId()
    }

    fun onResume() {
        setSessionId()
    }

    fun onFlowStart() {
        flowStarted = true
    }

    // Sube cada vez que se guarda un enlace: el panel de inicio lo observa para atenderlo en el momento, sin esperar a que
    // la actividad vuelva a primer plano (si ya esta en primer plano, ese regreso no ocurre y el enlace se perdia).
    private val _pendingIntentSignal = MutableStateFlow(0)
    val pendingIntentSignal: StateFlow<Int> = _pendingIntentSignal.asStateFlow()

    fun cacheIntent(intent: Intent?) {
        pendingIntent = intent
        if (intent != null) _pendingIntentSignal.value += 1
    }

    fun getCachedIntent(): Intent? = pendingIntent

    fun hasFlowStarted(): Boolean = flowStarted

    private fun setSessionId() {
        runBlocking(Dispatchers.IO) {
            prefKeys.setSessionId(sessionId)
        }
    }
}