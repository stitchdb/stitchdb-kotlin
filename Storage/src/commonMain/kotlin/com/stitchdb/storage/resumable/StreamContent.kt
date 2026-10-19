package com.stitchdb.storage.resumable

import com.stitchdb.annotations.StitchInternal
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel

@StitchInternal
internal class StreamContent(
    size: Long,
    private val copyTo: suspend ByteWriteChannel.() -> Unit
) : OutgoingContent.WriteChannelContent() {

    override val contentLength: Long = size
    override val contentType: ContentType = ContentType.parse("application/offset+octet-stream")

    override suspend fun writeTo(channel: ByteWriteChannel) {
        copyTo(channel)
    }

}
