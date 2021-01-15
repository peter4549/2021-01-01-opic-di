package com.duke.elliot.opicdi.Legacy

/*
class AudioEncoder(private val randomAccessFile: RandomAccessFile) {
    private val mediaCodec: MediaCodec
    private val mediaType = "audio/mp4a-latm"

    fun close() {
        try {
            mediaCodec.stop()
            mediaCodec.release()
            randomAccessFile.close()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    @Synchronized
    fun offerEncoder(input: ByteArray, uiCallback: (ByteArray) -> Unit) {
        var outData: ByteArray? = null
        Timber.d("%s is coming", input.size.toString())
        try {
            val inputBuffers: Array<ByteBuffer> = mediaCodec.inputBuffers
            val outputBuffers: Array<ByteBuffer> = mediaCodec.outputBuffers
            val inputBufferIndex = mediaCodec.dequeueInputBuffer(-1)
            if (inputBufferIndex >= 0) {
                val inputBuffer: ByteBuffer = inputBuffers[inputBufferIndex]
                inputBuffer.clear()
                inputBuffer.put(input)
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.size, 0, 0)
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)

            while (outputBufferIndex >= 0) {
                val outBitsSize = bufferInfo.size
                val outPacketSize = outBitsSize + 7 // 7 is ADTS size
                val outputBuffer = outputBuffers[outputBufferIndex]

                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + outBitsSize)

                val outData = ByteArray(outPacketSize)
                uiCallback.invoke(outData)
                addADTStoPacket(outData, outPacketSize)
                outputBuffer.get(outData, 7, outBitsSize)
                outputBuffer.position(bufferInfo.offset)

                randomAccessFile.write(outData, 0, outData.size)
                Log.e("AudioEncoder", outData.size.toString() + " bytes written")
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
            }

        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @Suppress("SpellCheckingInspection")
    /**
     * Add ADTS header at the beginning of each and every AAC packet.
     * This is needed as MediaCodec encoder generates a packet of raw
     * AAC data.
     *
     * Note the packetLen must count in the ADTS header itself.
     */
    private fun addADTStoPacket(packet: ByteArray, packetLen: Int) {
        val profile = 2 // AAC LC
        val freqencyIndex = 4 // 44.1KHz
        val channelConfig = 1 // Mono

        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = ((profile - 1 shl 6) + (freqencyIndex shl 2) + (channelConfig shr 2)).toByte()
        packet[3] = (((channelConfig and 3) shl 6) + (packetLen shr 11)).toByte()
        packet[4] = (packetLen and 0x7FF shr 3).toByte()
        packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

    fun touch(file: File) {
        try {
            if (!file.exists())
                file.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    init {
        mediaCodec = MediaCodec.createEncoderByType(mediaType)
        val sampleRates = intArrayOf(8000, 11025, 22050, 44100, 48000)
        val bitRates = intArrayOf(64000, 128000)
        val mediaFormat = MediaFormat.createAudioFormat(mediaType, sampleRates[3], 1)
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRates[1])
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()
    }
}
 */