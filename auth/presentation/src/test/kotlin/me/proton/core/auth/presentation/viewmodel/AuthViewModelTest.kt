/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.auth.presentation.viewmodel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.coroutineScope
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import junit.framework.Assert.assertNotSame
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.test.TestCoroutineScope
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.presentation.HumanVerificationManagerObserver
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.humanverification.presentation.observe
import me.proton.core.humanverification.presentation.onHumanVerificationNeeded
import me.proton.core.test.android.ArchTest
import me.proton.core.test.kotlin.CoroutinesTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthViewModelTest : ArchTest, CoroutinesTest {

    private val lifecycle: Lifecycle = mockk()
    private val humanVerificationManager: HumanVerificationManager = mockk(relaxed = true)
    private val humanVerificationOrchestrator: HumanVerificationOrchestrator = mockk(relaxed = true)
    internal lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        clearAllMocks()

        val coroutineScope = mockk<LifecycleCoroutineScope>() {
            every { coroutineContext } returns TestCoroutineScope().coroutineContext
        }
        // `HumanVerificationManager.observe` is an extension fun, we have to manually mock it
        mockkStatic(HumanVerificationManager::observe)
        // Mock extension functions of Lifecycle (for lifecycle.coroutineScope)
        mockkStatic("androidx.lifecycle.LifecycleKt")
        every { lifecycle.coroutineScope } returns coroutineScope

        viewModel = LoginViewModel(
            savedStateHandle = mockk(),
            accountWorkflow = mockk(),
            createLoginSession = mockk(),
            keyStoreCrypto = mockk(),
            postLoginAccountSetup = mockk(),
            humanVerificationManager,
            humanVerificationOrchestrator
        )
    }

    @Test
    fun `handleHumanVerificationState always creates a new observer`() = coroutinesTest {
        // GIVEN
        assertNull(viewModel.humanVerificationObserver)
        // WHEN
        viewModel.handleHumanVerificationState(lifecycle)
        val firstObserver = viewModel.humanVerificationObserver
        viewModel.handleHumanVerificationState(lifecycle)
        val secondObserver = viewModel.humanVerificationObserver
        // THEN
        assertNotNull(viewModel.humanVerificationObserver)
        assertNotSame(firstObserver, secondObserver)
    }

    @Test
    fun `handleHumanVerificationState cancels previous observer if it exists`() = coroutinesTest {
        // GIVEN
        val observer = mockk<HumanVerificationManagerObserver> {
            coEvery { onHumanVerificationNeeded(any(), any()) } returns this
            every { cancelAllObservers() } returns Unit
        }
        viewModel.humanVerificationObserver = observer
        // WHEN
        viewModel.handleHumanVerificationState(lifecycle)
        // THEN
        verify { observer.cancelAllObservers() }
    }

    @Test
    fun `handleHumanVerificationState calls onHumanVerificationNeeded on the observer`() = coroutinesTest {
        // GIVEN
        val observer = mockk<HumanVerificationManagerObserver> {
            coEvery { onHumanVerificationNeeded(any(), any()) } returns this
        }
        every { humanVerificationManager.observe(any(), any()) } returns observer
        // WHEN
        viewModel.handleHumanVerificationState(lifecycle)
        // THEN
        coVerify { observer.onHumanVerificationNeeded(any(), any()) }
    }

    @Test
    fun `register calls HumanVerificationOrchestrator's register`() = coroutinesTest {
        // WHEN
        viewModel.register(mockk())
        // THEN
        coVerify { humanVerificationOrchestrator.register(any(), any()) }
    }

}
