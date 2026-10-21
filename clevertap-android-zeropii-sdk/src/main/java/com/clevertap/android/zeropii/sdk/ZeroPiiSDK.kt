package com.clevertap.android.zeropii.sdk

import com.clevertap.android.zeropii.sdk.auth.AccessTokenProvider
import com.clevertap.android.zeropii.sdk.encryption.EncryptionManager
import com.clevertap.android.zeropii.sdk.model.BatchTokenizeResult
import com.clevertap.android.zeropii.sdk.model.TokenizeResult
import com.clevertap.android.zeropii.sdk.network.NetworkProvider
import com.clevertap.android.zeropii.sdk.repository.AccessTokenProviderAuthRepository
import com.clevertap.android.zeropii.sdk.repository.AuthRepository
import com.clevertap.android.zeropii.sdk.repository.TokenRepositoryImpl
import com.clevertap.android.zeropii.sdk.repository.TokenRepository
import com.clevertap.android.zeropii.sdk.retry.DefaultRetryPolicy
import com.clevertap.android.zeropii.sdk.retry.RetryPolicy
import com.clevertap.android.zeropii.sdk.util.CoreSdkVersionRegistrar
import com.clevertap.android.zeropii.sdk.util.TypeConverterRegistry
import com.clevertap.android.zeropii.sdk.util.ZeroPiiLogger
import com.clevertap.android.zeropii.sdk.util.toPublicResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main entry point for the ZeroPii SDK, which provides tokenization services
 * for sensitive data like PII (Personally Identifiable Information).
 */
class ZeroPiiSDK private constructor(
    private val tokenProvider: AccessTokenProvider,
    private val apiUrl: String,
    private val enableEncryption: Boolean,
    private val logLevel: Int,
    private val retryPolicy: RetryPolicy
) {
    internal var sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal lateinit var tokenRepository: TokenRepository
    internal lateinit var authRepository: AuthRepository
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var logger: ZeroPiiLogger
    internal lateinit var coreSdkVersionRegistrar: CoreSdkVersionRegistrar

    init {
        initialize()
    }

    private fun initialize() {
        logger = ZeroPiiLogger(logLevel)

        logger.d("Initializing ZeroPiiSDK")

        require(apiUrl.isNotBlank()) { "apiUrl must not be blank" }

        // Create network provider
        val networkProvider = NetworkProvider(apiUrl)

        // Setup encryption manager if enabled
        encryptionManager = EncryptionManager(enableEncryption, logger)

        // Create repositories
        authRepository = AccessTokenProviderAuthRepository(tokenProvider, logger)

        tokenRepository = TokenRepositoryImpl(networkProvider, authRepository, encryptionManager, logger, retryPolicy)
        coreSdkVersionRegistrar = CoreSdkVersionRegistrar(logger)

        logger.d("ZeroPiiSDK initialization complete")
    }

    // ========================================
    // TOKENIZATION METHODS (Overloaded)
    // ========================================

    /**
     * Tokenizes a String value
     * @param value The String value to tokenize
     * @param callback The callback to receive the result
     */
    fun tokenize(value: String, callback: (TokenizeResult) -> Unit) {
        performTokenization(value, String::class.java, callback)
    }

    /**
     * Tokenizes an Int value
     * @param value The Int value to tokenize
     * @param callback The callback to receive the result
     */
    fun tokenize(value: Int, callback: (TokenizeResult) -> Unit) {
        performTokenization(value, Int::class.java, callback)
    }

    /**
     * Tokenizes a Long value
     * @param value The Long value to tokenize
     * @param callback The callback to receive the result
     */
    fun tokenize(value: Long, callback: (TokenizeResult) -> Unit) {
        performTokenization(value, Long::class.java, callback)
    }

    /**
     * Tokenizes a Float value
     * @param value The Float value to tokenize
     * @param callback The callback to receive the result
     */
    fun tokenize(value: Float, callback: (TokenizeResult) -> Unit) {
        performTokenization(value, Float::class.java, callback)
    }

    /**
     * Tokenizes a Double value
     * @param value The Double value to tokenize
     * @param callback The callback to receive the result
     */
    fun tokenize(value: Double, callback: (TokenizeResult) -> Unit) {
        performTokenization(value, Double::class.java, callback)
    }

    /**
     * Tokenizes a Boolean value
     * @param value The Boolean value to tokenize
     * @param callback The callback to receive the result
     */
    fun tokenize(value: Boolean, callback: (TokenizeResult) -> Unit) {
        performTokenization(value, Boolean::class.java, callback)
    }

    // ========================================
    // BATCH TOKENIZATION METHODS (Overloaded)
    // ========================================

    /**
     * Tokenizes multiple String values in a single batch operation.
     *
     * @param values The list of String values to tokenize
     * @param callback The callback to receive the result
     */
    fun batchTokenizeStringValues(values: List<String>, callback: (BatchTokenizeResult) -> Unit) {
        performBatchTokenization(values, String::class.java, callback)
    }

    /**
     * Tokenizes multiple Int values in a single batch operation.
     *
     * @param values The list of Int values to tokenize
     * @param callback The callback to receive the result
     */
    fun batchTokenizeIntValues(values: List<Int>, callback: (BatchTokenizeResult) -> Unit) {
        performBatchTokenization(values, Int::class.java, callback)
    }

    /**
     * Tokenizes multiple Long values in a single batch operation.
     *
     * @param values The list of Long values to tokenize
     * @param callback The callback to receive the result
     */
    fun batchTokenizeLongValues(values: List<Long>, callback: (BatchTokenizeResult) -> Unit) {
        performBatchTokenization(values, Long::class.java, callback)
    }

    /**
     * Tokenizes multiple Float values in a single batch operation.
     *
     * @param values The list of Float values to tokenize
     * @param callback The callback to receive the result
     */
    fun batchTokenizeFloatValues(values: List<Float>, callback: (BatchTokenizeResult) -> Unit) {
        performBatchTokenization(values, Float::class.java, callback)
    }

    /**
     * Tokenizes multiple Double values in a single batch operation.
     *
     * @param values The list of Double values to tokenize
     * @param callback The callback to receive the result
     */
    fun batchTokenizeDoubleValues(values: List<Double>, callback: (BatchTokenizeResult) -> Unit) {
        performBatchTokenization(values, Double::class.java, callback)
    }

    /**
     * Tokenizes multiple Boolean values in a single batch operation.
     *
     * @param values The list of Boolean values to tokenize
     * @param callback The callback to receive the result
     */
    fun batchTokenizeBooleanValues(values: List<Boolean>, callback: (BatchTokenizeResult) -> Unit) {
        performBatchTokenization(values, Boolean::class.java, callback)
    }

    // ========================================
    // PRIVATE IMPLEMENTATION METHODS
    // ========================================

    /**
     * Internal method to perform tokenization with type conversion
     */
    private fun <T> performTokenization(
        value: T,
        type: Class<T>,
        callback: (TokenizeResult) -> Unit
    ) {
        sdkScope.launch {
            try {
                logger.d("Tokenizing ${type.simpleName} value")

                val converter = TypeConverterRegistry.getConverter(type)
                if (converter == null) {
                    withContext(Dispatchers.Main) {
                        callback(TokenizeResult.Error("Unsupported type: ${type.simpleName}"))
                    }
                    return@launch
                }

                // Convert typed value to string for repository layer
                val stringValue = converter.toString(value)

                // Call repository with string value
                val repoResult = if (enableEncryption) {
                    tokenRepository.tokenizeWithEncryptionOverTransit(stringValue)
                } else {
                    tokenRepository.tokenize(stringValue)
                }

                // Convert repository result to public result
                val publicResult = repoResult.toPublicResult()
                logger.d("tokenization result: ${type.simpleName} value: $publicResult")
                withContext(Dispatchers.Main) {
                    callback(publicResult)
                }
            } catch (e: Exception) {
                logger.e("Error tokenizing ${type.simpleName} value", e)
                withContext(Dispatchers.Main) {
                    callback(TokenizeResult.Error(e.message ?: "Unknown error occurred"))
                }
            }
        }
    }

    /**
     * Internal method to perform batch tokenization
     */
    private fun <T> performBatchTokenization(
        values: List<T>,
        type: Class<T>,
        callback: (BatchTokenizeResult) -> Unit
    ) {
        sdkScope.launch {
            try {
                if (values.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        callback(BatchTokenizeResult.Error("Batch tokenize request contains no values"))
                    }
                    return@launch
                }

                logger.d("Batch tokenizing ${values.size} ${type.simpleName} values")

                val converter = TypeConverterRegistry.getConverter(type)
                if (converter == null) {
                    withContext(Dispatchers.Main) {
                        callback(BatchTokenizeResult.Error("Unsupported type: ${type.simpleName}"))
                    }
                    return@launch
                }

                // Convert typed values to strings for repository layer
                val stringValues = values.map { converter.toString(it) }

                // Call repository with string values
                val repoResult = if (enableEncryption) {
                    tokenRepository.batchTokenizeWithEncryptionOverTransit(stringValues)
                } else {
                    tokenRepository.batchTokenize(stringValues)
                }

                // Convert repository result (strings) to public result (non-generic) using converter
                val publicResult = repoResult.toPublicResult()
                logger.d("batch tokenization result: ${type.simpleName} values: $publicResult")

                withContext(Dispatchers.Main) {
                    callback(publicResult)
                }
            } catch (e: Exception) {
                logger.e("Error batch tokenizing ${type.simpleName} values", e)
                withContext(Dispatchers.Main) {
                    callback(BatchTokenizeResult.Error(e.message ?: "Unknown error occurred"))
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ZeroPiiSDK? = null

        @JvmStatic
        @JvmOverloads
        fun initialize(
            tokenProvider: AccessTokenProvider,
            apiUrl: String,
            logLevel: ZeroPiiLogger.LogLevel = ZeroPiiLogger.LogLevel.OFF,
            retryPolicy: RetryPolicy = DefaultRetryPolicy()
        ): ZeroPiiSDK {
            val sdk = INSTANCE ?: synchronized(this) {
                INSTANCE ?: ZeroPiiSDK(
                    tokenProvider,
                    apiUrl,
                    enableEncryption = true,
                    logLevel = logLevel.intValue,
                    retryPolicy = retryPolicy
                ).also { INSTANCE = it }
            }
            // Runs on every initialize call, so a later call registers CleverTap instances created since
            sdk.coreSdkVersionRegistrar.registerWithAllInstances()
            return sdk
        }

        /**
         * Gets the ZeroPiiSDK instance.
         *
         * @return The ZeroPiiSDK instance
         * @throws IllegalStateException if not initialized
         */
        @JvmStatic
        fun getInstance(): ZeroPiiSDK {
            return INSTANCE ?: throw IllegalStateException(
                "ZeroPiiSDK is not initialized. Call initialize() first."
            )
        }
    }
}
