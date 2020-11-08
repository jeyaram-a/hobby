package curl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class Status(val url: String, val sucess: Boolean, val errorMsg: String?)
data class Progress(val fileName: String, val completedInPercentage: Float)

val statusChannel = Channel<Status>(5)
val progressChannel = Channel<Progress>(5)
val doneEventChannel = Channel<Boolean> { }

const val displayInterval = 200

suspend fun download(url: String) {
    val url = URL(url)
    val urlConnection = url.openConnection()
    val fileName = url.toString().transform { it.substring(it.lastIndexOf('/') + 1) }
    try {
        if (urlConnection is HttpURLConnection) {
            val fileSize = urlConnection.contentLengthLong
            var downloaded = 0
            val bufferSize = 8 * 1024
            val ds = BufferedInputStream(urlConnection.inputStream, bufferSize)
            var nextSendTime = System.currentTimeMillis() + displayInterval
            while (true) {
                val buf = ds.readNBytes(bufferSize)
                if (buf.size < 1)
                    break
                //  fo.write(buf)
                downloaded += buf.size
                if (System.currentTimeMillis() > nextSendTime) {
                    progressChannel.send(Progress(fileName, (downloaded.toFloat() / fileSize) * 100))
                    nextSendTime = System.currentTimeMillis() + displayInterval
                }
            }
            progressChannel.send(Progress(fileName, (downloaded.toFloat() / fileSize) * 100))
            doneEventChannel.send(true)
            statusChannel.send(Status(url.toString(), true, null))
        }
    } catch (e: Exception) {
        statusChannel.send(Status(url.toString(), false, "${e.cause} "))
        doneEventChannel.send(true)
    }


}

suspend fun showProgress() {
    for (progress in progressChannel) {
        System.out.printf("\r%s = %f", progress.fileName, progress.completedInPercentage)
    }
}

fun main(args: List<String>): Unit = runBlocking {

    supervisorScope {
        launch {
            showProgress()
        }

        launch {
            cancelChannels(args.size)
        }

        launch {
            displayResults()
        }

        repeat(args.size) { i ->
            launch(Dispatchers.Default) {
                download(args[i])
            }
        }
    }
    println("\n")

}

suspend fun cancelChannels(size: Int) {
    var size = size
    for (done in doneEventChannel) {
        if (--size == 0) {
            if (statusChannel.isEmpty)
                statusChannel.close()
            if (progressChannel.isEmpty)
                progressChannel.close()
            if (doneEventChannel.isEmpty)
                doneEventChannel.close()
        }
    }
}

suspend fun displayResults() {
    for (status in statusChannel) {
        if (status.sucess) {
            println("\n${status.url} - ✅")
        } else {
            println("\n${status.url} - ❎ ${status.errorMsg}")
        }
    }
}
