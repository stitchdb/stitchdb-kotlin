package com.stitchdb.storage

import com.stitchdb.StitchClient
import com.stitchdb.StitchSerializer
import com.stitchdb.annotations.StitchExperimental
import com.stitchdb.annotations.StitchInternal
import com.stitchdb.auth.AuthDependentPluginConfig
import com.stitchdb.auth.api.AuthenticatedStitchApi
import com.stitchdb.auth.api.authenticatedStitchApi
import com.stitchdb.bodyOrNull
import com.stitchdb.exceptions.BadRequestRestException
import com.stitchdb.exceptions.HttpRequestException
import com.stitchdb.exceptions.NotFoundRestException
import com.stitchdb.exceptions.RestException
import com.stitchdb.exceptions.UnauthorizedRestException
import com.stitchdb.exceptions.UnknownRestException
import com.stitchdb.logging.StitchLogger
import com.stitchdb.logging.createLogger
import com.stitchdb.logging.w
import com.stitchdb.plugins.CustomSerializationConfig
import com.stitchdb.plugins.CustomSerializationPlugin
import com.stitchdb.plugins.MainConfig
import com.stitchdb.plugins.MainPlugin
import com.stitchdb.plugins.StitchPluginProvider
import com.stitchdb.safeBody
import com.stitchdb.storage.Storage.Companion.DEFAULT_CHUNK_SIZE
import com.stitchdb.storage.analytics.StorageAnalyticsClient
import com.stitchdb.storage.analytics.StorageAnalyticsClientImpl
import com.stitchdb.storage.resumable.ResumableCache
import com.stitchdb.storage.resumable.createDefaultResumableCache
import com.stitchdb.storage.vectors.StorageVectorsClient
import com.stitchdb.storage.vectors.StorageVectorsClientImpl
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Plugin for interacting with the StitchDB Storage API
 *
 * To use it you need to install it to the [StitchClient]:
 * ```kotlin
 * val stitchdb = createStitchClient(stitchdbUrl, stitchdbKey) {
 *    install(Storage)
 * }
 * ```
 *
 * then you have to interact with the Storage API like this:
 * ```kotlin
 * val bucket = stitchdb.storage.from("icons")
 * val bytes = bucket.downloadAuthenticated("icon.png")
 * ```
 */
interface Storage : MainPlugin<Storage.Config>, CustomSerializationPlugin {

    /**
     * Access vector storage operations.
     *
     * **Not available on StitchDB:** these buckets are not offered; calls return an error.
     *
     * @returns A [StorageVectorsClient] instance configured with the current storage settings.
     */
    @StitchExperimental
    val analytics: StorageAnalyticsClient

    /**
     * Access analytics storage operations using Iceberg tables.
     *
     * **Not available on StitchDB:** these buckets are not offered; calls return an error.
     *
     * @returns A [StorageAnalyticsClient] instance configured with the current storage settings.
     */
    @StitchExperimental
    val vectors: StorageVectorsClient

    /**
     * The API used for network requests.
     */
    @StitchInternal
    val api: AuthenticatedStitchApi

    /**
     * Creates a new bucket in the storage
     * @param id the id of the bucket
     * @param builder overrides bucket config options (like whether the bucket should be public,
     * file size limit, etc.)
     * @throws RestException or one of its subclasses if receiving an error response
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    suspend fun createBucket(id: String, builder: BucketBuilder.() -> Unit = {})

    /**
     * Updates a bucket in the storage
     * @param id the id of the bucket
     * @param builder the builder for the bucket
     */
    suspend fun updateBucket(id: String, builder: BucketBuilder.() -> Unit = {})

    /**
     * Returns all buckets in the storage
     * @throws RestException or one of its subclasses if receiving an error response
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    suspend fun listBuckets(filter: StorageListFilter.Buckets.() -> Unit = {}): List<Bucket>

    /**
     * Retrieves a bucket by its [bucketId]
     * @throws RestException or one of its subclasses if receiving an error response
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    suspend fun getBucket(bucketId: String): Bucket?

    /**
     * Empties a bucket by its [bucketId]
     * @throws RestException or one of its subclasses if receiving an error response
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    suspend fun emptyBucket(bucketId: String)

    /**
     * Deletes a bucket by its [bucketId]
     * @throws RestException or one of its subclasses if receiving an error response
     * @throws HttpRequestTimeoutException if the request timed out
     * @throws HttpRequestException on network related issues
     */
    suspend fun deleteBucket(bucketId: String)

    /**
     * Builder function for interacting with a bucket with the given [bucketId]
     */
    operator fun get(bucketId: String): BucketApi

    /**
     * Builder function for interacting with a bucket with the given [bucketId]
     */
    fun from(bucketId: String): BucketApi = get(bucketId)

    /**
     * Purges the CDN cache for an entire bucket.
     *
     * Maps to `DELETE /cdn/{bucket}` on the Storage API. The server
     * issues a CDN invalidation for the bucket.
     *
     * **Requires the `service_role` key.** The underlying endpoint enforces
     * `service_role` JWT — calls made with the anon key or a user JWT will be
     * rejected by the server.
     *
     * **Not available on StitchDB:** StitchDB storage has no CDN cache to purge, so
     * the server returns an error for this call.
     *
     * @param id The unique identifier of the bucket you would like to purge from cache.
     * @param options Optional purge cache options.
     */
    suspend fun purgeBucketCache(
        id: String,
        options: PurgeCacheOptions.() -> Unit = {}
    )

    /**
     * Config for the storage plugin
     * @param transferTimeout the timeout for uploading and downloading files (default: 120 seconds)
     * @param resumable the resume settings to use for the storage api
     * @param serializer the serializer to use for the storage api, use null to use the default serializer
     */
    data class Config(
        var transferTimeout: Duration = 120.seconds,
        @PublishedApi internal var resumable: Resumable = Resumable(),
        override var serializer: StitchSerializer? = null,
        override var requireValidSession: Boolean = false,
    ) : MainConfig(), CustomSerializationConfig, AuthDependentPluginConfig {

        /**
         * @param cache the cache for caching resumable upload urls
         * @param retryTimeout the timeout for retrying resumable uploads when uploading a chunk fails
         * @param onlyUpdateStateAfterChunk whether the state should only be updated after a chunk was uploaded successfully or also when the chunk is currently being uploaded
         */
        data class Resumable(
            var cache: ResumableCache? = null,
            var retryTimeout: Duration = 5.seconds,
            var onlyUpdateStateAfterChunk: Boolean = false
        ) {

            /**
             * The default chunk size for resumable uploads. **StitchDB currently only supports a chunk size of 6MB, so be careful when changing this value**
             */
            var defaultChunkSize: Long = DEFAULT_CHUNK_SIZE

        }

        /**
         * Config for resumable uploads
         */
        inline fun resumable(builder: Resumable.() -> Unit) {
            resumable = Resumable().apply(builder)
        }

    }

    companion object : StitchPluginProvider<Config, Storage> {

        override val key: String = "storage"

        /**
         * The tag for the Storage logger.
         */
        const val LOGGING_TAG = "StitchDB-Storage"

        /**
         * The api version of the storage plugin
         */
        const val API_VERSION = 1

        /**
         * The default chunk size for resumable uploads.
         */
        const val DEFAULT_CHUNK_SIZE = 6L * 1024L * 1024L

        override fun createConfig(init: Config.() -> Unit) = Config().apply(init)
        override fun create(stitchdbClient: StitchClient, config: Config): Storage {
            return StorageImpl(stitchdbClient, config)
        }

    }

}

internal class StorageImpl(override val stitchdbClient: StitchClient, override val config: Storage.Config) : Storage {

    override val pluginKey: String
        get() = Storage.key

    override val apiVersion: Int
        get() = Storage.API_VERSION

    override val serializer: StitchSerializer = config.serializer ?: stitchdbClient.defaultSerializer
    override val logger: StitchLogger = stitchdbClient.createLogger(Storage.LOGGING_TAG, config)

    @OptIn(StitchInternal::class)
    override val api = stitchdbClient.authenticatedStitchApi(this, defaultRequest = {
        timeout {
            requestTimeoutMillis = config.transferTimeout.inWholeMilliseconds
        }
    })

    override val analytics: StorageAnalyticsClient = StorageAnalyticsClientImpl(api.resolve("iceberg"))
    override val vectors: StorageVectorsClient = StorageVectorsClientImpl(api.resolve("vector"))

    init {
        if(config.resumable.defaultChunkSize != DEFAULT_CHUNK_SIZE) {
            logger.w { "StitchDB currently only supports a chunk size of 6MB" }
        }
    }

    override suspend fun listBuckets(filter: StorageListFilter.Buckets.() -> Unit): List<Bucket> {
        val response = api.get("bucket") {
            url.parameters.appendAll(StorageListFilter.Buckets().apply(filter).buildParameters())
        }
        return response.safeBody()
    }

    override suspend fun getBucket(bucketId: String): Bucket? = api.get("bucket/${storagePath(bucketId)}").safeBody()

    override suspend fun deleteBucket(bucketId: String) {
        api.delete("bucket/${storagePath(bucketId)}")
    }

    override suspend fun createBucket(id: String, builder: BucketBuilder.() -> Unit) {
        val bucketBuilder = BucketBuilder().apply(builder)
        val body = buildJsonObject {
            put("name", id)
            put("id", id)
            put("public", bucketBuilder.public ?: false)
            bucketBuilder.allowedMimeTypes?.let {
                put("allowed_mime_types", JsonArray(it.map { type -> JsonPrimitive(type) }))
            }
            bucketBuilder.fileSizeLimit?.let {
                put("file_size_limit", it.value)
            }
        }
        api.postJson("bucket", body)
    }

    override suspend fun updateBucket(id: String, builder: BucketBuilder.() -> Unit) {
        val bucketBuilder = BucketBuilder().apply(builder)
        val body = buildJsonObject {
            put("name", id)
            put("id", id)
            bucketBuilder.public?.let {
                put("public", bucketBuilder.public)
            }
            bucketBuilder.allowedMimeTypes?.let {
                put("allowed_mime_types", JsonArray(it.map { type -> JsonPrimitive(type) }))
            }
            bucketBuilder.fileSizeLimit?.let {
                put("file_size_limit", it.value)
            }
        }
        api.putJson("bucket/${storagePath(id)}", body)
    }

    override suspend fun emptyBucket(bucketId: String) {
        api.post("bucket/${storagePath(bucketId)}/empty")
    }

    override fun get(bucketId: String): BucketApi = BucketApiImpl(bucketId, this, api.resolve("object"), config.resumable.cache ?: createDefaultResumableCache())

    override suspend fun parseErrorResponse(response: HttpResponse): RestException {
        val statusCode = response.status
        val error = stitchdbClient.bodyOrNull<StorageErrorResponse>(response) ?: StorageErrorResponse(
            response.status.value,
            "Unknown error",
            ""
        )
        if (statusCode != HttpStatusCode.BadRequest) return UnknownRestException("Unknown error response $error", response)
        when (error.statusCode) {
            HttpStatusCode.Unauthorized.value -> throw UnauthorizedRestException(error.error, response, error.message)
            HttpStatusCode.BadRequest.value -> throw BadRequestRestException(error.error, response, error.message)
            HttpStatusCode.NotFound.value -> throw NotFoundRestException(error.error, response, error.message)
            else -> throw UnknownRestException(error.message, response)
        }
    }

    override suspend fun purgeBucketCache(id: String, options: PurgeCacheOptions.() -> Unit) {
        val options = PurgeCacheOptions().apply(options)
        api.delete("cdn/${storagePath(id)}") {
            if(options.transformations) {
                parameter("transformations", true)
            }
        }
    }

}

/**
 * StitchDB Storage is a simple way to store large files for various purposes
 */
val StitchClient.storage: Storage
    get() = pluginManager.getPlugin(Storage)
