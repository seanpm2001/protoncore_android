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

package me.proton.core.auth.presentation.viewmodel.signup

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.auth.domain.usecase.PerformLogin
import me.proton.core.auth.domain.usecase.signup.PerformCreateExternalEmailUser
import me.proton.core.auth.domain.usecase.signup.PerformCreateUser
import me.proton.core.auth.domain.usecase.signup.SignupChallengeConfig
import me.proton.core.auth.presentation.entity.signup.RecoveryMethod
import me.proton.core.auth.presentation.entity.signup.RecoveryMethodType
import me.proton.core.challenge.domain.ChallengeConfig
import me.proton.core.challenge.domain.ChallengeManager
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.domain.entity.UserId
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.presentation.HumanVerificationManagerObserver
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.ResponseCodes
import me.proton.core.network.domain.client.ClientId
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.network.domain.client.CookieSessionId
import me.proton.core.payment.presentation.PaymentsOrchestrator
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.test.android.ArchTest
import me.proton.core.test.kotlin.CoroutinesTest
import me.proton.core.user.domain.entity.CreateUserType
import me.proton.core.user.domain.entity.User
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SignupViewModelTest : ArchTest, CoroutinesTest {

    // region mocks
    private val performCreateUser = mockk<PerformCreateUser>(relaxed = true)
    private val performCreateExternalUser = mockk<PerformCreateExternalEmailUser>(relaxed = true)
    private val keyStoreCrypto = mockk<KeyStoreCrypto>(relaxed = true)
    private val humanVerificationManager = mockk<HumanVerificationManager>()
    private val humanVerificationOrchestrator = mockk<HumanVerificationOrchestrator>()
    private val plansOrchestrator = mockk<PlansOrchestrator>(relaxed = true)
    private val paymentsOrchestrator = mockk<PaymentsOrchestrator>(relaxed = true)
    private val clientIdProvider = mockk<ClientIdProvider>(relaxed = true)
    private val performLogin = mockk<PerformLogin>()
    private val challengeManager = mockk<ChallengeManager>(relaxed = true)
    // endregion


    // region test data
    private val testUsername = "test-username"
    private val testClientIdString = "test-clientId"
    private val testClientId = ClientId.CookieSession(CookieSessionId(testClientIdString))
    private val testPassword = "test-password"
    private val testEmail = "test-email"
    private val testPhone = "test-phone"

    private val testUser = User(
        userId = UserId("test-user-id"),
        email = null,
        name = testUsername,
        displayName = null,
        currency = "test-curr",
        credit = 0,
        usedSpace = 0,
        maxSpace = 100,
        maxUpload = 100,
        role = null,
        private = true,
        services = 1,
        subscribed = 0,
        delinquent = null,
        keys = emptyList()
    )

    private val usernameTakenError: ApiException
        get() = ApiException(
            ApiResult.Error.Http(
                409,
                "Conflict",
                ApiResult.Error.ProtonData(ResponseCodes.USER_CREATE_NAME_INVALID, "Username taken")
            )
        )

    private val signupChallengeConfig = SignupChallengeConfig()
    // endregion

    private lateinit var viewModel: SignupViewModel

    @Before
    fun beforeEveryTest() {
        viewModel = SignupViewModel(
            performCreateUser,
            performCreateExternalUser,
            keyStoreCrypto,
            plansOrchestrator,
            paymentsOrchestrator,
            clientIdProvider,
            humanVerificationManager,
            performLogin,
            challengeManager,
            signupChallengeConfig,
            humanVerificationOrchestrator,
            mockk(relaxed = true)
        )
        coEvery { clientIdProvider.getClientId(any()) } returns testClientId
        every { keyStoreCrypto.decrypt(any<String>()) } returns testPassword
        every { keyStoreCrypto.encrypt(any<String>()) } returns "encrypted-$testPassword"

        coEvery {
            performCreateUser.invoke(
                username = testUsername,
                domain = any(),
                password = any(),
                recoveryEmail = any(),
                recoveryPhone = any(),
                referrer = null,
                type = any(),
            )
        } returns testUser.userId

        coEvery {
            performCreateExternalUser.invoke(
                email = testEmail,
                password = any(),
                referrer = null
            )
        } returns testUser.userId
    }

    @Test
    fun `create Internal user no username no password set`() = coroutinesTest {
        viewModel.userCreationState.test {
            val throwable = assertFails { viewModel.startCreateUserWorkflow() }
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertEquals("Username is not set.", throwable.message)

            coVerify(exactly = 0) {
                performCreateUser(
                    username = any(),
                    password = any(),
                    recoveryEmail = any(),
                    recoveryPhone = any(),
                    referrer = any(),
                    type = any(),
                    domain = any(),
                )
            }
        }
    }

    @Test
    fun `create Internal user no username set but password set`() = coroutinesTest {
        // GIVEN
        viewModel.setPassword(testPassword)

        viewModel.userCreationState.test {
            val throwable = assertFails { viewModel.startCreateUserWorkflow() }
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertEquals("Username is not set.", throwable.message)

            coVerify(exactly = 0) {
                performCreateUser(
                    username = any(),
                    password = any(),
                    recoveryEmail = any(),
                    recoveryPhone = any(),
                    referrer = any(),
                    type = any(),
                    domain = any(),
                )
            }
        }
    }

    @Test
    fun `create Internal user no password set`() = coroutinesTest {
        // GIVEN
        viewModel.username = testUsername
        viewModel.userCreationState.test {
            val throwable = assertFails { viewModel.startCreateUserWorkflow() }
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertEquals("Password is not set (initialized).", throwable.message)

            coVerify(exactly = 0) {
                performCreateUser(
                    username = any(),
                    password = any(),
                    recoveryEmail = any(),
                    recoveryPhone = any(),
                    referrer = any(),
                    type = any(),
                    domain = any(),
                )
            }
        }
    }

    @Test
    fun `create Internal user no recovery method set`() = coroutinesTest {
        // GIVEN
        viewModel.username = testUsername
        viewModel.setPassword(testPassword)
        viewModel.userCreationState.test {
            // WHEN
            viewModel.startCreateUserWorkflow()
            // THEN
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertTrue(awaitItem() is SignupViewModel.State.Processing)
            val successItem = awaitItem()
            assertTrue(successItem is SignupViewModel.State.Success)
            assertEquals(testUser.userId.id, successItem.userId)

            coVerify(exactly = 1) {
                performCreateUser(
                    username = testUsername,
                    password = "encrypted-$testPassword",
                    recoveryEmail = null,
                    recoveryPhone = null,
                    referrer = null,
                    type = CreateUserType.Normal,
                    domain = any(),
                )
            }
        }
    }

    @Test
    fun `create Internal user email recovery method set`() = coroutinesTest {
        // GIVEN
        val emailRecovery = RecoveryMethod(RecoveryMethodType.EMAIL, testEmail)
        viewModel.username = testUsername
        viewModel.setPassword(testPassword)
        viewModel.setRecoveryMethod(emailRecovery)

        viewModel.userCreationState.test {
            // WHEN
            viewModel.startCreateUserWorkflow()
            // THEN
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertTrue(awaitItem() is SignupViewModel.State.Processing)
            val successItem = awaitItem()
            assertTrue(successItem is SignupViewModel.State.Success)
            assertEquals(testUser.userId.id, successItem.userId)

            coVerify(exactly = 1) {
                performCreateUser(
                    username = testUsername,
                    password = "encrypted-$testPassword",
                    recoveryEmail = testEmail,
                    recoveryPhone = null,
                    referrer = null,
                    type = CreateUserType.Normal,
                    domain = any(),
                )
            }
        }
    }

    @Test
    fun `create Internal user phone recovery method set`() = coroutinesTest {
        // GIVEN
        val emailRecovery = RecoveryMethod(RecoveryMethodType.SMS, testPhone)
        viewModel.username = testUsername
        viewModel.setPassword(testPassword)
        viewModel.setRecoveryMethod(emailRecovery)
        viewModel.userCreationState.test {
            // WHEN
            viewModel.startCreateUserWorkflow()
            // THEN
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertTrue(awaitItem() is SignupViewModel.State.Processing)
            val successItem = awaitItem()
            assertTrue(successItem is SignupViewModel.State.Success)
            assertEquals(testUser.userId.id, successItem.userId)

            coVerify(exactly = 1) {
                performCreateUser(
                    username = testUsername,
                    password = "encrypted-$testPassword",
                    recoveryEmail = null,
                    recoveryPhone = testPhone,
                    referrer = null,
                    type = CreateUserType.Normal,
                    domain = any(),
                )
            }
        }
    }

    @Test
    fun `create Internal user API error`() = coroutinesTest {
        // GIVEN
        coEvery {
            performCreateUser.invoke(
                username = testUsername,
                domain = any(),
                password = any(),
                recoveryEmail = any(),
                recoveryPhone = any(),
                referrer = null,
                type = any(),
            )
        } throws ApiException(
            ApiResult.Error.Http(
                httpCode = 123,
                "http error",
                ApiResult.Error.ProtonData(
                    code = 12106,
                    error = "create user error"
                )
            )
        )
        viewModel.username = testUsername
        viewModel.setPassword(testPassword)
        viewModel.userCreationState.test {
            // WHEN
            viewModel.startCreateUserWorkflow()
            // THEN
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertTrue(awaitItem() is SignupViewModel.State.Processing)
            val errorItem = awaitItem()
            assertTrue(errorItem is SignupViewModel.State.Error.Message)
            assertEquals("create user error", errorItem.message)

            coVerify(exactly = 1) {
                performCreateUser(
                    username = testUsername,
                    password = "encrypted-$testPassword",
                    recoveryEmail = null,
                    recoveryPhone = null,
                    referrer = null,
                    type = CreateUserType.Normal,
                    domain = any(),
                )
            }
        }
    }

    @Test
    fun `create External user no external email set`() = coroutinesTest {
        // GIVEN
        viewModel.currentAccountType = AccountType.External
        viewModel.userCreationState.test {
            val throwable = assertFails { viewModel.startCreateUserWorkflow() }
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertEquals("External email is not set.", throwable.message)

            coVerify(exactly = 0) {
                performCreateExternalUser(
                    email = any(),
                    password = any(),
                    referrer = any()
                )
            }
        }
    }

    @Test
    fun `create External user no external email set but password set`() = coroutinesTest {
        // GIVEN
        viewModel.currentAccountType = AccountType.External
        viewModel.setPassword(testPassword)
        viewModel.userCreationState.test {
            val throwable = assertFails { viewModel.startCreateUserWorkflow() }
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertEquals("External email is not set.", throwable.message)

            coVerify(exactly = 0) {
                performCreateExternalUser(
                    email = any(),
                    password = any(),
                    referrer = any()
                )
            }
        }
    }

    @Test
    fun `create External user no password set`() = coroutinesTest {
        // GIVEN
        viewModel.currentAccountType = AccountType.External
        viewModel.externalEmail = testEmail
        viewModel.userCreationState.test {
            val throwable = assertFails { viewModel.startCreateUserWorkflow() }
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertEquals("Password is not set (initialized).", throwable.message)

            coVerify(exactly = 0) {
                performCreateExternalUser(
                    email = any(),
                    password = any(),
                    referrer = any()
                )
            }
        }
    }

    @Test
    fun `create External user success`() = coroutinesTest {
        // GIVEN
        viewModel.currentAccountType = AccountType.External
        viewModel.externalEmail = testEmail
        viewModel.setPassword(testPassword)
        viewModel.userCreationState.test {
            // WHEN
            viewModel.startCreateUserWorkflow()
            // THEN
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertTrue(awaitItem() is SignupViewModel.State.Processing)
            val successItem = awaitItem()
            assertTrue(successItem is SignupViewModel.State.Success)
            assertEquals(testUser.userId.id, successItem.userId)

            coVerify(exactly = 1) {
                performCreateExternalUser(
                    email = testEmail,
                    password = "encrypted-$testPassword",
                    referrer = null
                )
            }
        }
    }

    @Test
    fun `create External user error`() = coroutinesTest {
        // GIVEN
        coEvery {
            performCreateExternalUser.invoke(
                email = testEmail,
                password = any(),
                referrer = null
            )
        } throws ApiException(
            ApiResult.Error.Http(
                httpCode = 123,
                "http error",
                ApiResult.Error.ProtonData(
                    code = 12106,
                    error = "create user error"
                )
            )
        )

        viewModel.currentAccountType = AccountType.External
        viewModel.externalEmail = testEmail
        viewModel.setPassword(testPassword)
        viewModel.userCreationState.test {
            // WHEN
            viewModel.startCreateUserWorkflow()
            // THEN
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertTrue(awaitItem() is SignupViewModel.State.Processing)
            val errorItem = awaitItem()
            assertTrue(errorItem is SignupViewModel.State.Error.Message)
            assertEquals("create user error", errorItem.message)

            coVerify(exactly = 1) {
                performCreateExternalUser(
                    email = testEmail,
                    password = "encrypted-$testPassword",
                    referrer = null
                )
            }
        }
    }

    @Test
    fun `tries login if internal username taken`() = coroutinesTest {
        coEvery {
            performCreateUser.invoke(
                username = testUsername,
                domain = any(),
                password = any(),
                recoveryEmail = any(),
                recoveryPhone = any(),
                referrer = null,
                type = any(),
            )
        } throws usernameTakenError

        coEvery { performLogin.invoke(testUsername, any()) } returns mockk {
            every { userId } returns testUser.userId
        }

        // GIVEN
        viewModel.username = testUsername
        viewModel.setPassword(testPassword)

        viewModel.userCreationState.test {
            // WHEN
            viewModel.startCreateUserWorkflow()
            // THEN
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertTrue(awaitItem() is SignupViewModel.State.Processing)
            val successItem = awaitItem()
            assertTrue(successItem is SignupViewModel.State.Success)
            assertEquals(testUser.userId.id, successItem.userId)

            coVerify(exactly = 1) {
                performCreateUser(
                    username = testUsername,
                    password = "encrypted-$testPassword",
                    recoveryEmail = null,
                    recoveryPhone = null,
                    referrer = null,
                    type = CreateUserType.Normal,
                    domain = any(),
                )
            }

            coVerify(exactly = 1) {
                performLogin(
                    username = testUsername,
                    password = "encrypted-$testPassword"
                )
            }
        }
    }

    @Test
    fun `tries login if External username taken`() = coroutinesTest {
        coEvery {
            performCreateExternalUser.invoke(
                email = testEmail,
                password = any(),
                referrer = null
            )
        } throws usernameTakenError

        coEvery { performLogin.invoke(testEmail, any()) } returns mockk {
            every { userId } returns testUser.userId
        }

        // GIVEN
        viewModel.currentAccountType = AccountType.External
        viewModel.externalEmail = testEmail
        viewModel.setPassword(testPassword)
        viewModel.userCreationState.test {
            // WHEN
            viewModel.startCreateUserWorkflow()
            // THEN
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
            assertTrue(awaitItem() is SignupViewModel.State.Processing)
            val successItem = awaitItem()
            assertTrue(successItem is SignupViewModel.State.Success)
            assertEquals(testUser.userId.id, successItem.userId)

            coVerify(exactly = 1) {
                performCreateExternalUser(
                    email = testEmail,
                    password = "encrypted-$testPassword",
                    referrer = null
                )
            }

            coVerify(exactly = 1) {
                performLogin(
                    username = testEmail,
                    password = "encrypted-$testPassword"
                )
            }
        }
    }

    @Test
    fun `stopObservingHumanVerification emits Error if throwError is true`() = coroutinesTest {
        // WHEN
        viewModel.stopObservingHumanVerification(true)
        // THEN
        viewModel.userCreationState.test {
            assertTrue(awaitItem() is SignupViewModel.State.Error.HumanVerification)
        }
    }

    @Test
    fun `stopObservingHumanVerification emits Idle if throwError is false`() = coroutinesTest {
        // WHEN
        viewModel.stopObservingHumanVerification(false)
        // THEN
        viewModel.userCreationState.test {
            assertTrue(awaitItem() is SignupViewModel.State.Idle)
        }
    }

    @Test
    fun `stopObservingHumanVerification cancels all observers of humanVerificationObserver`() = coroutinesTest {
        // GIVEN
        val observer = mockk<HumanVerificationManagerObserver>(relaxed = true)
        viewModel.humanVerificationObserver = observer
        // WHEN
        viewModel.stopObservingHumanVerification(false)
        // THEN
        coVerify { observer.cancelAllObservers() }
    }
}
