package com.critt.trandroidlator.data

import com.critt.trandroidlator.BuildConfig
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TranslationSource {
    private var socketSubject: Socket? = null
    private var socketObject: Socket? = null

    fun connectObject(languageSubject: String, languageObject: String): Flow<SpeechData> {
        socketObject = IO.socket(BuildConfig.API_BASE_URL + "object")
        return initSocket(socketObject, getTranscriptionConfig(languageObject, languageSubject))
    }

    fun connectSubject(languageSubject: String, languageObject: String): Flow<SpeechData> {
        socketSubject = IO.socket(BuildConfig.API_BASE_URL + "subject")
        return initSocket(socketSubject, getTranscriptionConfig(languageSubject, languageObject))
    }

    fun onData(subjectData: ByteArray?, objectData: ByteArray?) {
        socketSubject?.emit("binaryAudioData", subjectData)
        socketObject?.emit("binaryAudioData", objectData)
    }

    private fun initSocket(socket: Socket?, config: Map<String, Any>): Flow<SpeechData> = callbackFlow {
        socket?.connect()
        socket?.emit("startGoogleCloudStream", Gson().toJson(config))

        socket?.on("speechData") { args ->
            Gson().fromJson(args[0].toString(), SpeechData::class.java).let {
                println("Object speechData: $it")
                trySend(it)
            }
        }

        awaitClose {
            socket?.emit("endGoogleCloudStream")
            socket?.off("speechData")
            socket?.disconnect()
        }
    }

    fun disconnect() {
        socketObject?.emit("endGoogleCloudStream")
        socketObject?.off("speechData")
        socketObject?.disconnect()

        socketSubject?.emit("endGoogleCloudStream")
        socketSubject?.off("speechData")
        socketSubject?.disconnect()
    }

    private fun getTranscriptionConfig(languageSubject: String, languageObject: String) =
        mapOf(
            "audio" to mapOf(
                "encoding" to "LINEAR16",
                "sampleRateHertz" to 16000,
                "languageCode" to languageSubject
            ),
            "interimResults" to true,
            "targetLanguage" to languageObject
        )
}