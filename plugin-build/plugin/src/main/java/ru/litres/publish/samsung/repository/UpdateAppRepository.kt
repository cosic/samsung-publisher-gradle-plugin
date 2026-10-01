package ru.litres.publish.samsung.repository

import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.serialization.kotlinxDeserializerOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import ru.litres.publish.samsung.DebugSetting
import ru.litres.publish.samsung.NetworkSetting
import ru.litres.publish.samsung.PublishSetting
import ru.litres.publish.samsung.exception.UploadApkException
import ru.litres.publish.samsung.models.update.AddBinaryRequest
import ru.litres.publish.samsung.models.update.AddBinaryResponse
import ru.litres.publish.samsung.models.update.SubmitReviewRequest
import ru.litres.publish.samsung.models.update.UpdateDataRequest
import ru.litres.publish.samsung.models.update.UpdateDataResponse
import ru.litres.publish.samsung.models.upload.UploadResponse
import ru.litres.publish.samsung.models.upload.session.UploadSessionResponse
import ru.litres.publish.samsung.network.NetworkClient
import ru.litres.publish.samsung.network.bodyOrThrow
import java.io.File
import kotlin.math.roundToInt

class UpdateAppRepository(
    private val debugSetting: DebugSetting,
    private val networkSetting: NetworkSetting,
    private val networkClient: NetworkClient,
    private val uploadNetworkClient: NetworkClient,
) {
    /**
     * publish flow:
     *   1. create upload session
     *   2. upload the apk to the url from the session, retrying transport failures -> fileKey
     *   3. contentUpdate with metadata only -> moves the app into REGISTERING state
     *   4. add the uploaded binary via POST /seller/v2/content/binary
     */
    fun update(
        apk: File,
        publishSetting: PublishSetting,
    ): Boolean {
        val session = getUploadSession()
        val fileKey = uploadApkWithRetry(session, apk)

        val metadataUpdated = updateApplication(publishSetting)
        if (!metadataUpdated) return false

        return addBinary(fileKey, publishSetting)
    }

    fun submitReview(publishSetting: PublishSetting): Boolean {
        return submitReviewApplication(publishSetting)
    }

    private fun getUploadSession(): UploadSessionResponse =
        networkClient.post(CREATE_UPLOAD_SESSION)
            .responseObject<UploadSessionResponse>(kotlinxDeserializerOf())
            .bodyOrThrow(CREATE_UPLOAD_SESSION)

    /**
     * Galaxy Store returns the upload endpoint together with the session id, and it points to a
     * different host than the rest of the api. Honour that url instead of a hardcoded one, so a
     * change on the store side does not require a new plugin release.
     */
    private fun resolveUploadUrl(session: UploadSessionResponse): String =
        networkSetting.uploadUrl?.takeUnless(String::isBlank)
            ?: session.url?.takeUnless(String::isBlank)
            ?: UPLOAD_APK

    private fun uploadApkWithRetry(
        session: UploadSessionResponse,
        file: File,
    ): String {
        val sessionId =
            session.sessionId
                ?: throw UploadApkException("Field \"sessionId\" not found in \"$CREATE_UPLOAD_SESSION\"")
        val uploadUrl = resolveUploadUrl(session)
        val attempts = networkSetting.uploadAttempts.coerceAtLeast(1)
        println("Uploading apk to $uploadUrl")

        var delayMs = networkSetting.uploadRetryDelayMs
        var attempt = 1
        while (true) {
            try {
                return uploadApk(sessionId, uploadUrl, file)
            } catch (error: UploadApkException) {
                if (!error.retryable || attempt >= attempts) throw error
                println("Upload attempt $attempt of $attempts failed: ${error.message}")
                println("Retrying in $delayMs ms")
                Thread.sleep(delayMs)
                delayMs *= RETRY_BACKOFF_FACTOR
                attempt++
            }
        }
    }

    private fun uploadApk(
        sessionId: String,
        uploadUrl: String,
        file: File,
    ): String {
        if (debugSetting.dryMode) return String()
        var prevProgress = 0
        val uploadResponse =
            uploadNetworkClient.upload(uploadUrl, listOf(SESSION_ID_FIELD to sessionId))
                .add { FileDataPart(file, name = "file") }
                .progress { readBytes, totalBytes ->
                    val progress = (readBytes.toFloat() / totalBytes.toFloat() * PERCENT_MULTIPLIER).roundToInt()
                    if (progress != prevProgress) {
                        val readyMb = readBytes / KB_DIVIDER
                        val totalMb = totalBytes / KB_DIVIDER
                        println("Uploaded ${readyMb}kb / ${totalMb}kb ($progress %)")
                    }
                    prevProgress = progress
                }
                .responseObject<UploadResponse>(kotlinxDeserializerOf())
                .bodyOrThrow(uploadUrl)

        if (uploadResponse.errorMsg != null) throw UploadApkException(uploadResponse.errorMsg)
        val key = uploadResponse.fileKey
        if (key.isNullOrBlank()) throw UploadApkException("Field \"fileKey\" not found in \"$uploadUrl\"")
        return key
    }

    @Suppress("ReturnCount")
    private fun updateApplication(publishSetting: PublishSetting): Boolean {
        val contentId = publishSetting.contentId ?: return false
        val paid = if (publishSetting.paid) YES_FIELD else NO_FIELD
        val data =
            UpdateDataRequest(
                contentId = contentId,
                defaultLanguageCode = publishSetting.defaultLanguageCode,
                paid = paid,
                publicationType = publishSetting.publicationType.code,
            )
        val json = Json.encodeToJsonElement(data)

        if (debugSetting.dryMode) {
            println("Data for update: ")
            println(data)
            return true
        }

        val updateResponse =
            networkClient.post(UPDATE_APPLICATION)
                .jsonBody(json.jsonObject.toString())
                .responseObject<UpdateDataResponse>(kotlinxDeserializerOf())
                .bodyOrThrow(UPDATE_APPLICATION)

        if (updateResponse.errorMsg != null) throw UploadApkException(updateResponse.errorMsg)

        return updateResponse.contentStatus == SUCCESS_UPDATE_APK_RESULT
    }

    @Suppress("ReturnCount")
    private fun addBinary(
        fileKey: String,
        publishSetting: PublishSetting,
    ): Boolean {
        val contentId = publishSetting.contentId ?: return false
        val gms = if (publishSetting.hasGoogleService) YES_FIELD else NO_FIELD

        val data =
            AddBinaryRequest(
                contentId = contentId,
                filekey = fileKey,
                gms = gms,
            )
        val json = Json.encodeToJsonElement(data)

        if (debugSetting.dryMode) {
            println("Data for add binary: ")
            println(data)
            return true
        }

        val addBinaryResponse =
            networkClient.post(ADD_BINARY)
                .jsonBody(json.jsonObject.toString())
                .responseObject<AddBinaryResponse>(kotlinxDeserializerOf())
                .bodyOrThrow(ADD_BINARY)

        if (addBinaryResponse.resultCode != SUCCESS_ADD_BINARY_RESULT) {
            throw UploadApkException(
                "Add binary failed: ${addBinaryResponse.resultCode} ${addBinaryResponse.resultMessage}",
            )
        }
        return true
    }

    @Suppress("ReturnCount")
    private fun submitReviewApplication(publishSetting: PublishSetting): Boolean {
        val contentId = publishSetting.contentId ?: return false
        val data =
            SubmitReviewRequest(
                contentId,
            )
        val json = Json.encodeToJsonElement(data)

        if (debugSetting.dryMode) {
            println("Data for submit review: ")
            println(json)
            return true
        }

        val submitReviewResult =
            networkClient.post(SUBMIT_APPLICATION)
                .jsonBody(json.jsonObject.toString())
                .response()

        return submitReviewResult.second.statusCode in SUCCESS_STATUS_CODES
    }

    companion object {
        private const val CREATE_UPLOAD_SESSION = "/seller/createUploadSessionId"
        private const val UPLOAD_APK = "/galaxyapi/fileUpload"
        private const val UPDATE_APPLICATION = "/seller/contentUpdate"
        private const val ADD_BINARY = "/seller/v2/content/binary"
        private const val SUBMIT_APPLICATION = "/seller/contentSubmit"

        private const val SESSION_ID_FIELD = "sessionId"
        private const val SUCCESS_UPDATE_APK_RESULT = "REGISTERING"
        private const val SUCCESS_ADD_BINARY_RESULT = "0000"
        private val SUCCESS_STATUS_CODES = setOf(200, 204)

        private const val KB_DIVIDER = 1024
        private const val PERCENT_MULTIPLIER = 100

        private const val YES_FIELD = "Y"
        private const val NO_FIELD = "N"

        private const val RETRY_BACKOFF_FACTOR = 2
    }
}
