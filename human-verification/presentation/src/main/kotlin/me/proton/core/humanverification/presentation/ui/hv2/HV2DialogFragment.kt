/*
 * Copyright (c) 2022 Proton Technologies AG
 * This file is part of Proton AG and ProtonCore.
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

package me.proton.core.humanverification.presentation.ui.hv2

import android.os.Bundle
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.humanverification.domain.entity.TokenType
import me.proton.core.humanverification.presentation.R
import me.proton.core.humanverification.presentation.databinding.DialogHumanVerificationMainBinding
import me.proton.core.humanverification.presentation.entity.HumanVerificationResult
import me.proton.core.humanverification.presentation.entity.HumanVerificationToken
import me.proton.core.humanverification.presentation.ui.REQUEST_KEY
import me.proton.core.humanverification.presentation.ui.RESULT_HUMAN_VERIFICATION
import me.proton.core.humanverification.presentation.utils.showEnterCode
import me.proton.core.humanverification.presentation.utils.showHelp
import me.proton.core.humanverification.presentation.utils.showHumanVerificationCaptchaContent
import me.proton.core.humanverification.presentation.utils.showHumanVerificationEmailContent
import me.proton.core.humanverification.presentation.utils.showHumanVerificationSMSContent
import me.proton.core.humanverification.presentation.viewmodel.hv2.HV2ViewModel
import me.proton.core.network.domain.client.ClientId
import me.proton.core.network.domain.client.ClientIdType
import me.proton.core.network.domain.client.getId
import me.proton.core.network.domain.session.SessionId
import me.proton.core.presentation.ui.ProtonDialogFragment
import me.proton.core.presentation.utils.viewBinding
import me.proton.core.util.kotlin.exhaustive

/**
 * Shows the dialog for the Human Verification options and option procedures.
 */
@AndroidEntryPoint
class HV2DialogFragment : ProtonDialogFragment(R.layout.dialog_human_verification_main) {
    private val viewModel by viewModels<HV2ViewModel>()
    private val binding by viewBinding(DialogHumanVerificationMainBinding::bind)

    private val clientIdType: ClientIdType by lazy {
        ClientIdType.getByValue(requireArguments().getString(ARG_CLIENT_ID_TYPE, null))
    }

    private val clientId: ClientId by lazy {
        val clientId = requireArguments().getString(ARG_CLIENT_ID)!!
        clientIdType.getId(clientId)
    }

    private val sessionId: SessionId? by lazy {
        when (clientIdType) {
            ClientIdType.SESSION -> SessionId(clientId.id)
            ClientIdType.COOKIE -> null
        }.exhaustive
    }

    private val captchaToken: String? by lazy {
        requireArguments().getString(ARG_CAPTCHA_TOKEN)
    }

    private val captchaUrl: String? by lazy {
        requireArguments().getString(ARG_CAPTCHA_URL)
    }

    private val recoveryEmailAddress: String? by lazy {
        requireArguments().getString(ARG_RECOVERY_EMAIL_ADDRESS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.setFragmentResultListener(KEY_PHASE_TWO, this) { _, bundle ->
            val destination = bundle.getString(ARG_DESTINATION)
            val tokenType = TokenType.fromString(bundle.getString(ARG_TOKEN_TYPE)!!)
            childFragmentManager.showEnterCode(
                sessionId = sessionId,
                tokenType = tokenType,
                destination = destination
            )
        }
        childFragmentManager.setFragmentResultListener(KEY_VERIFICATION_DONE, this) { _, bundle ->
            val tokenCode = requireNotNull(bundle.getString(ARG_TOKEN_CODE)) { "Missing token code" }
            val tokenType = requireNotNull(bundle.getString(ARG_TOKEN_TYPE)) { "Missing token type" }
            setResultAndDismiss(
                token = HumanVerificationToken(
                    code = tokenCode,
                    type = tokenType
                )
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.enabledMethods
            .onEach { setEnabledVerificationMethods(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.activeMethod
            .onEach { setActiveVerificationMethod(TokenType.fromString(it)) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        binding.toolbar.apply {
            val useBackArrow = arguments?.getBoolean(ARG_IS_PART_OF_FLOW) ?: false
            val navigationIconId = if (useBackArrow) R.drawable.ic_proton_arrow_back else R.drawable.ic_proton_close
            navigationIcon = AppCompatResources.getDrawable(requireContext(), navigationIconId)
            setNavigationOnClickListener {
                setResultAndDismiss(token = null)
            }
            setOnMenuItemClickListener {
                if (it.itemId == R.id.menu_help) {
                    childFragmentManager.showHelp()
                    true
                } else false
            }
        }

        binding.verificationOptions.apply {
            restoreTabSelection(this)
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabReselected(tab: TabLayout.Tab?) {}

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.let {
                        val type = tab.tag as TokenType
                        viewModel.defineActiveVerificationMethod(type)
                    }
                }
            })
        }
    }

    override fun onBackPressed() {
        with(childFragmentManager) {
            if (backStackEntryCount >= 1) {
                popBackStack()
            } else {
                setResultAndDismiss(token = null)
            }
        }
    }

    private fun restoreTabSelection(tabLayout: TabLayout) = with(tabLayout) {
        // Restore tab selection on config change
        for (i in 0 until tabCount) {
            val tab = getTabAt(i) ?: continue
            if ((tab.tag as? TokenType)?.value == viewModel.activeMethod.value) {
                selectTab(tab)
                return
            }
        }
    }

    private fun setEnabledVerificationMethods(enabledMethods: List<String>) {
        if (enabledMethods.isEmpty()) {
            // If there are not available methods, dismiss the fragment with an error result
            setResultAndDismiss(null)
        } else {
            binding.verificationOptions.apply {
                for (method in enabledMethods) {
                    val tab = newTab().apply {
                        text = method
                        tag = TokenType.fromString(method)
                    }
                    addTab(tab)
                }
            }
        }
    }

    private fun setActiveVerificationMethod(verificationMethod: TokenType) {
        when (verificationMethod) {
            TokenType.CAPTCHA -> {
                childFragmentManager.showHumanVerificationCaptchaContent(
                    captchaUrl = captchaUrl,
                    token = captchaToken,
                    containerId = binding.fragmentOptionsContainer.id
                )
            }
            TokenType.EMAIL -> {
                childFragmentManager.showHumanVerificationEmailContent(
                    sessionId = sessionId,
                    containerId = binding.fragmentOptionsContainer.id,
                    recoveryEmailAddress = recoveryEmailAddress
                )
            }
            TokenType.SMS -> {
                childFragmentManager.showHumanVerificationSMSContent(
                    sessionId = sessionId,
                    containerId = binding.fragmentOptionsContainer.id
                )
            }
            else -> {
                childFragmentManager.showHelp()
            }
        }
    }

    private fun setResultAndDismiss(token: HumanVerificationToken?) {
        viewModel.onHumanVerificationResult(clientId, token).invokeOnCompletion {
            val result = HumanVerificationResult(
                clientId = clientId.id,
                clientIdType = sessionId?.let { ClientIdType.SESSION.value } ?: ClientIdType.COOKIE.value,
                token = token
            )
            val resultBundle = Bundle().apply { putParcelable(RESULT_HUMAN_VERIFICATION, result) }
            setFragmentResult(REQUEST_KEY, resultBundle)
            dismissAllowingStateLoss()
        }
    }

    companion object {
        private const val ARG_CLIENT_ID = "arg.clientId"
        private const val ARG_CLIENT_ID_TYPE = "arg.clientIdType"
        private const val ARG_CAPTCHA_TOKEN = "arg.captcha-token"
        private const val ARG_RECOVERY_EMAIL_ADDRESS = "arg.recoveryEmailAddress"
        private const val ARG_CAPTCHA_URL = "arg.captchaUrl"
        const val ARG_VERIFICATION_OPTIONS = "arg.verification-options"
        const val ARG_DESTINATION = "arg.destination"
        const val ARG_TOKEN_CODE = "arg.token-code"
        const val ARG_TOKEN_TYPE = "arg.token-type"
        const val ARG_IS_PART_OF_FLOW = "arg.part-of-flow"
        const val KEY_PHASE_TWO = "key.phase_two"
        const val KEY_VERIFICATION_DONE = "key.verification_done"

        /**
         * The only verification method (type) that is receiving aa token from the 9001 human
         * verification response is [TokenType.CAPTCHA] and should be passed to the constructor.
         *
         * @param availableVerificationMethods all available verification methods, returned from the API
         * @param captchaToken if the API returns it, otherwise null
         */
        operator fun invoke(
            clientId: String,
            captchaUrl: String? = null,
            clientIdType: String,
            availableVerificationMethods: List<String>,
            captchaToken: String?,
            recoveryEmailAddress: String?,
            isPartOfFlow: Boolean,
        ): HV2DialogFragment {
            return HV2DialogFragment().apply {
                arguments = bundleOf(
                    ARG_CLIENT_ID to clientId,
                    ARG_CAPTCHA_URL to captchaUrl,
                    ARG_CLIENT_ID_TYPE to clientIdType,
                    ARG_VERIFICATION_OPTIONS to availableVerificationMethods,
                    ARG_CAPTCHA_TOKEN to captchaToken,
                    ARG_RECOVERY_EMAIL_ADDRESS to recoveryEmailAddress,
                    ARG_IS_PART_OF_FLOW to isPartOfFlow,
                )
            }
        }
    }
}
