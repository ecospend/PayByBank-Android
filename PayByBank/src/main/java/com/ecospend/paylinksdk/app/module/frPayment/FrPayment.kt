package com.ecospend.paybybank.app.module.frPayment

import android.app.Activity
import com.ecospend.paybybank.app.PayByBankState
import com.ecospend.paybybank.data.remote.model.frPayment.FrPaymentCreateRequest
import com.ecospend.paybybank.data.remote.model.frPayment.FrPaymentCreateResponse
import com.ecospend.paybybank.data.remote.model.frPayment.FrPaymentDeleteRequest
import com.ecospend.paybybank.data.remote.model.frPayment.FrPaymentGetRequest
import com.ecospend.paybybank.data.remote.model.frPayment.FrPaymentGetResponse
import com.ecospend.paybybank.data.repository.FrPaymentRepository
import com.ecospend.paybybank.data.repository.IamRepository
import com.ecospend.paybybank.shared.coroutine.Coroutine
import com.ecospend.paybybank.shared.coroutine.cancel
import com.ecospend.paybybank.shared.logger.AppLog
import com.ecospend.paybybank.shared.model.completion.PayByBankError
import com.ecospend.paybybank.shared.model.completion.PayByBankResult
import com.ecospend.paybybank.shared.network.interceptor.ErrorInterceptor
import com.ecospend.paybybank.ui.pay.AppWebView
import com.ecospend.paybybank.ui.pay.model.AppWebViewUIModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FrPayment (Standing Order) API
 * A Standing Order is an instruction that an account holder gives to their bank to make payments of a fixed amount at regular intervals.
 * Payments are made automatically by the bank on a defined schedule (e.g. weekly or monthly) on an ongoing basis, unless a specified condition has been met, such as an end-date being reached or a set number of payments having been made.
 * Standing Orders can only be created, amended or cancelled by the account holder, typically by using their online or telephone banking service. They are most commonly used for recurring payments where the amount stays the same, such as rent payments, subscription services or regular account top-ups.
 */
class FrPayment(
    private val iamRepository: IamRepository,
    private val frPaymentRepository: FrPaymentRepository
) {

    /**
     *  Opens webview using with request model of FrPayment
     *
     *@property activity: Activty that provides to present bank selection
     *@property request: Request to create FrPayment
     *@property completion: It provides to handle result or error
     */
    fun initiate(
        activity: Activity,
        request: FrPaymentCreateRequest,
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ) {
        execute(
            activity = activity,
            type = FrPaymentExecuteType.Initiate(request),
            completion = completion
        )
    }

    /**
     *  Opens webview using with `uniqueID` of FrPayment
     *
     *@property activity: Activty that provides to present bank selection
     *@property uniqueID: Unique id value of FrPayment.
     *@property completion: It provides to handle result or error
     */
    fun open(
        activity: Activity,
        uniqueID: String,
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ) {
        execute(
            activity = activity,
            type = FrPaymentExecuteType.Open(uniqueID),
            completion = completion
        )
    }

    /**
     * Creates FrPayment
     *
     *@property request: Request to create FrPayment
     *@property completion: It provides to handle result or error
     */
    fun createFrPayment(
        request: FrPaymentCreateRequest,
        completion: (FrPaymentCreateResponse?, PayByBankError?) -> Unit
    ) {
        Coroutine.coroutineScope.launch(Dispatchers.IO) {
            val response = frPaymentRepository.createFrPayment(request)
            completion.invoke(response, null)
        }
        Coroutine.cancel()
    }

    /**
     *  Gets FrPayment detail
     *
     *@property request: Request to get FrPayment detail
     *@property completion: It provides to handle result or error
     */
    fun getFrPayment(
        request: FrPaymentGetRequest,
        completion: (FrPaymentGetResponse?, PayByBankError?) -> Unit
    ) {
        Coroutine.coroutineScope.launch(Dispatchers.IO) {
            val response = frPaymentRepository.getFrPayment(request)
            completion.invoke(response, null)
        }
        Coroutine.cancel()
    }

    /**
     *  Soft deletes FrPayment with given id
     *
     *@property request: Request to get FrPayment detail
     *@property completion: It provides to handle result or error
     */
    fun deactivateFrPayment(
        request: FrPaymentDeleteRequest,
        completion: (Boolean, PayByBankError?) -> Unit

    ) {
        Coroutine.coroutineScope.launch(Dispatchers.IO) {
            val response = frPaymentRepository.deleteFrPayment(request)
            completion.invoke(response ?: false, null)
        }
        Coroutine.cancel()
    }

    //region Private Functions

    private fun execute(
        activity: Activity,
        type: FrPaymentExecuteType,
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ) {
        if (!AppLog.isInit) AppLog.init(activity)

        Coroutine.coroutineScope.launch(Dispatchers.Main) {
            auth(completion)
            val executeTypeResult = handleExecuteType(type, completion) ?: return@launch

            val appWebView = AppWebView(
                AppWebViewUIModel(
                    uniqueID = executeTypeResult.first,
                    webViewURL = executeTypeResult.second,
                    redirectURL = executeTypeResult.third,
                    completion = { result, error ->
                        completion(result, error)
                    }
                )
            )

            ErrorInterceptor.errorHandler = { result, error ->
                completion(result, error)
                completion(
                    result,
                    error ?: PayByBankError.Unknown(
                        "${error?.localizedMessage} \n ${error?.message} \n ${error?.stackTrace} \n ${error?.cause}"
                    )
                )
                error?.let {
                    launch(Dispatchers.Main) {
                        appWebView.removeViews()
                    }
                }
            }

            appWebView.open(activity)
        }
    }

    /**
     * uniqueID, url, redirectUrl
     */
    private suspend fun handleExecuteType(
        type: FrPaymentExecuteType,
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ): Triple<String, String, String>? {
        val response = when (type) {
            is FrPaymentExecuteType.Open -> {
                withContext(Dispatchers.IO) {
                    frPaymentRepository.getFrPayment(
                        FrPaymentGetRequest(type.uniqueID)
                    )
                }
            }
            is FrPaymentExecuteType.Initiate -> {
                val frPaymentCreateResponse = frPaymentRepository.createFrPayment(
                    type.frPaymentCreateRequest
                )
                frPaymentCreateResponse?.uniqueID ?: run {
                    completion(null, PayByBankError.WrongPaylink("uniqueID Error."))
                    return null
                }
                withContext(Dispatchers.IO) {
                    frPaymentRepository.getFrPayment(
                        FrPaymentGetRequest(frPaymentCreateResponse.uniqueID)
                    )
                }
            }
        }

        val uniqueID = response?.uniqueID
            ?: run {
                completion(null, PayByBankError.WrongPaylink("uniqueID Error."))
                return null
            }
        val url = response.url
            ?: run {
                completion(null, PayByBankError.WrongPaylink("url Error."))
                return null
            }
        val redirectURL = response.redirectURL
            ?: run {
                completion(null, PayByBankError.WrongPaylink("redirectUrl Error."))
                return null
            }

        return Triple(uniqueID, url, redirectURL)
    }

    private suspend fun auth(
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ): Boolean {
        PayByBankState.Config.authentication
            ?: run {
                completion(null, PayByBankError.NotConfigured)
                return false
            }

        return withContext(Dispatchers.IO) {
            val response = iamRepository.connect()
            return@withContext if (response?.accessToken.isNullOrBlank()) {
                completion(null, PayByBankError.WrongPaylink("token error."))
                false
            } else true
        }
    }

    //endregion
}
