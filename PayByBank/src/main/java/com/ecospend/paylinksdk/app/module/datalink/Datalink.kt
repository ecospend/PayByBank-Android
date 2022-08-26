package com.ecospend.paybybank.app.module.datalink

import android.app.Activity
import com.ecospend.paybybank.app.PayByBankState
import com.ecospend.paybybank.data.remote.model.datalink.request.DatalinkCreateRequest
import com.ecospend.paybybank.data.remote.model.datalink.request.DatalinkDeleteRequest
import com.ecospend.paybybank.data.remote.model.datalink.request.DatalinkGetConsentDatalinkRequest
import com.ecospend.paybybank.data.remote.model.datalink.request.DatalinkGetRequest
import com.ecospend.paybybank.data.remote.model.datalink.response.DatalinkCreateResponse
import com.ecospend.paybybank.data.remote.model.datalink.response.DatalinkGetResponse
import com.ecospend.paybybank.data.remote.model.datalink.response.DatalinkModel
import com.ecospend.paybybank.data.repository.DatalinkRepository
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
 * Datalink API
 * Note: Datalink is a whitelabel consent journey solution provided by Ecospend that downsizes the required implementation for the consent journey to a single endpoint integration. By making a single call to /datalink endpoint you will be able to initiate the consent journey.
 */
class Datalink(
    private val iamRepository: IamRepository,
    private val datalinkRepository: DatalinkRepository
) {

    /**
     *  Opens webview using with `uniqueID` of datalink
     *
     *@property activity: Activty that provides to present bank selection
     *@property uniqueID: Unique id value of datalink.
     *@property completion: It provides to handle result or error
     */
    fun open(
        activity: Activity,
        uniqueID: String,
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ) {
        execute(
            activity = activity,
            type = DatalinkExecuteType.Open(uniqueID),
            completion = completion
        )
    }

    /**
     *  Opens webview using with `url` of Datalink
     *
     *@property activity: Activty that provides to present bank selection
     *@property datalinkUrl:  Url of paylink.
     *@property redirectUrl:  Redirect url value of paylink.
     *@property completion: It provides to handle result or error
     */
    fun openUrl(
        activity: Activity,
        datalinkUrl: String,
        redirectUrl: String,
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ) {
        execute(
            activity = activity,
            type = DatalinkExecuteType.OpenUrl(datalinkUrl, redirectUrl),
            completion = completion
        )
    }

    /**
     *  Opens webview using with request model of datalink
     *
     *@property activity: Activty that provides to present bank selection
     *@property request: Request to create datalink.
     *@property completion: It provides to handle result or error
     */
    fun initiate(
        activity: Activity,
        request: DatalinkCreateRequest,
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ) {
        execute(
            activity = activity,
            type = DatalinkExecuteType.Initiate(request),
            completion = completion
        )
    }

    /**
     *   Creates Datalink
     *
     *@property request: Request to create Datalink
     *@property completion: It provides to handle result or error
     */
    fun createDatalink(
        request: DatalinkCreateRequest,
        completion: (DatalinkCreateResponse?, PayByBankError?) -> Unit
    ) {
        Coroutine.coroutineScope.launch(Dispatchers.IO) {
            val response = datalinkRepository.createDatalink(request)
            completion.invoke(response, null)
        }
        Coroutine.cancel()
    }

    /**
     *  Gets Datalink detail
     *
     *@property request: Request to get Datalink detail
     *@property completion: It provides to handle result or error
     */
    fun getDatalink(
        request: DatalinkGetRequest,
        completion: (DatalinkGetResponse?, PayByBankError?) -> Unit
    ) {
        Coroutine.coroutineScope.launch(Dispatchers.IO) {
            val response = datalinkRepository.getDatalink(request)
            completion.invoke(response, null)
        }
        Coroutine.cancel()
    }

    /**
     * Deletes the Datalink with given id.
     *
     *@property request: Request to soft delete Datalink
     *@property completion: It provides to handle result or error
     */
    fun deleteDatalink(
        request: DatalinkDeleteRequest,
        completion: (Boolean?, PayByBankError?) -> Unit

    ) {
        Coroutine.coroutineScope.launch(Dispatchers.IO) {
            val response = datalinkRepository.deleteDatalink(request)
            completion.invoke(response, null)
        }
        Coroutine.cancel()
    }

    /**
     *  Returns datalink with given `consentID`
     *
     *@property request:  Request to get Datalink of a consent
     *@property completion: It provides to handle result or error
     */
    fun getDatalinkOfConsent(
        request: DatalinkGetConsentDatalinkRequest,
        completion: (DatalinkGetResponse?, PayByBankError?) -> Unit

    ) {
        Coroutine.coroutineScope.launch(Dispatchers.IO) {
            val response = datalinkRepository.getDatalinkOfConsent(request)
            completion.invoke(response, null)
        }
        Coroutine.cancel()
    }

    //region Private Functions

    private fun execute(
        activity: Activity,
        type: DatalinkExecuteType,
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
        type: DatalinkExecuteType,
        completion: (PayByBankResult?, PayByBankError?) -> Unit
    ): Triple<String, String, String>? {
        val response = when (type) {
            is DatalinkExecuteType.Open -> {
                withContext(Dispatchers.IO) {
                    datalinkRepository.getDatalink(
                        DatalinkGetRequest(type.uniqueID)
                    )
                }
            }
            is DatalinkExecuteType.OpenUrl -> {
                withContext(Dispatchers.IO) {
                    DatalinkGetResponse(datalink = DatalinkModel(uniqueID = "openUrl", url = type.url), redirectURL = type.redirectUrl)
                }
            }
            is DatalinkExecuteType.Initiate -> {
                val datalinkCreateResponse = datalinkRepository.createDatalink(
                    type.datalinkCreateRequest
                )
                datalinkCreateResponse?.uniqueID ?: run {
                    completion(null, PayByBankError.WrongPaylink("uniqueID Error."))
                    return null
                }
                withContext(Dispatchers.IO) {
                    datalinkRepository.getDatalink(
                        DatalinkGetRequest(datalinkCreateResponse.uniqueID)
                    )
                }
            }
        }

        val uniqueID = response?.datalink?.uniqueID
            ?: run {
                completion(null, PayByBankError.WrongPaylink("uniqueID Error."))
                return null
            }
        val url = response.datalink.url
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
