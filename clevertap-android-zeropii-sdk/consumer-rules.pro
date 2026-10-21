# ============================================================================
# CleverTap ZeroPii SDK — Consumer ProGuard/R8 Rules
# ============================================================================
# These rules are bundled into the AAR and automatically applied when a
# consumer app enables R8/ProGuard minification. They protect:
#   1. Public API surface (classes consumers reference directly)
#   2. Gson-serialized models (field names must match JSON keys)
#   3. Retrofit interface (annotation-based code generation)
# ============================================================================

# ————————————————————————————————————————————————————————————————————————————
# 1. SDK ENTRY POINT
# ————————————————————————————————————————————————————————————————————————————
# ZeroPiiSDK is the only class consumers instantiate / call directly.
# Preserve class name (for stack traces) and all public + static members
# (initialize, getInstance, tokenize, batchTokenize* methods).
-keep class com.clevertap.android.zeropii.sdk.ZeroPiiSDK { public *; }

# ————————————————————————————————————————————————————————————————————————————
# 2. PUBLIC RESULT MODELS (sealed class hierarchies)
# ————————————————————————————————————————————————————————————————————————————
# Consumers pattern-match on these sealed subclasses with `is` / `when`.
# R8 must not rename, inline, or remove any subclass — even if the consumer
# only handles one branch — because the SDK creates all variants internally.
# Data class properties (val) must also survive for consumer field access.

# TokenizeResult and its subclasses
-keep class com.clevertap.android.zeropii.sdk.model.TokenizeResult { *; }
-keep class com.clevertap.android.zeropii.sdk.model.TokenizeResult$Success { *; }
-keep class com.clevertap.android.zeropii.sdk.model.TokenizeResult$Error { *; }

# BatchTokenizeResult and its subclasses
-keep class com.clevertap.android.zeropii.sdk.model.BatchTokenizeResult { *; }
-keep class com.clevertap.android.zeropii.sdk.model.BatchTokenizeResult$Success { *; }
-keep class com.clevertap.android.zeropii.sdk.model.BatchTokenizeResult$Error { *; }

# BatchTokenItem — used in BatchTokenizeResult.Success.results list.
# Consumers iterate and read fields: originalValue, token, exists, etc.
-keep class com.clevertap.android.zeropii.sdk.model.BatchTokenItem { *; }

# BatchTokenizeSummary — dual purpose: Gson-deserialized from API response
# AND directly exposed in BatchTokenizeResult.Success.summary.
# Both class name and field names must survive.
-keep class com.clevertap.android.zeropii.sdk.model.BatchTokenizeSummary { *; }

# ————————————————————————————————————————————————————————————————————————————
# 3. AUTH PROVIDER INTERFACES (implemented by consumers)
# ————————————————————————————————————————————————————————————————————————————
# Consumers implement AccessTokenProvider and receive AccessTokenCallback.
# Interface method signatures and AccessTokenInfo field names must survive.
-keep interface com.clevertap.android.zeropii.sdk.auth.AccessTokenProvider { *; }
-keep interface com.clevertap.android.zeropii.sdk.auth.AccessTokenCallback { *; }
-keep class com.clevertap.android.zeropii.sdk.auth.AccessTokenInfo { *; }

# ————————————————————————————————————————————————————————————————————————————
# 4. RETRY POLICY INTERFACE (implemented by consumers)
# ————————————————————————————————————————————————————————————————————————————
# Consumers may provide a custom RetryPolicy to ZeroPiiSDK.initialize().
# Method signatures must survive so the SDK can call shouldRetry/retryDelayMs
# on consumer implementations.
-keep interface com.clevertap.android.zeropii.sdk.retry.RetryPolicy { *; }

# ————————————————————————————————————————————————————————————————————————————
# 5. LOG LEVEL ENUM (passed by consumers to initialize)
# ————————————————————————————————————————————————————————————————————————————
# Consumers pass ZeroPiiLogger.LogLevel.OFF / .ERROR / .DEBUG / etc.
# Keep the enum class with all constants and the enclosing class name.
-keep class com.clevertap.android.zeropii.sdk.util.ZeroPiiLogger$LogLevel { *; }

# ————————————————————————————————————————————————————————————————————————————
# 6. GSON SERIALIZED MODELS
# ————————————————————————————————————————————————————————————————————————————

#  GSON MODELS - All network request/response classes
-keep class com.clevertap.android.zeropii.sdk.model.*Request { *; }
-keep class com.clevertap.android.zeropii.sdk.model.*Response { *; }
-keep public class com.clevertap.android.zeropii.sdk.model.Batch*Summary { *; }


# ————————————————————————————————————————————————————————————————————————————
# 7. RETROFIT API INTERFACE
# ————————————————————————————————————————————————————————————————————————————
# Retrofit generates implementation via reflection on @POST, @Header, @Body
# annotations. Although Retrofit ships its own ProGuard rules, explicitly
# keeping the SDK's interface provides defense-in-depth against edge cases
# (e.g., consumer using a different Retrofit version with different rules).
-keep interface com.clevertap.android.zeropii.sdk.api.TokenizationApi { *; }

# ————————————————————————————————————————————————————————————————————————————
# 8. CLEVERTAP CORE SDK VERSION REGISTRATION
# ————————————————————————————————————————————————————————————————————————————
# CoreSdkVersionRegistrar finds CleverTap Core through reflection to report the
# ZeroPii version, so the class name and these two members must survive R8.
# Matches nothing when the app does not include Core.
-keep class com.clevertap.android.sdk.CleverTapAPI {
    public static java.util.HashMap getInstances();
    public void setCustomSdkVersion(java.lang.String, int);
}
