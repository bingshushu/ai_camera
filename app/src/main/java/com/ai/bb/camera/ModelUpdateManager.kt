package com.ai.bb.camera

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class ModelVersionInfo(
    val version: Int,
    val url: String
)

class ModelUpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelUpdateManager"
        private const val PREFS_NAME = "model_update_prefs"
        private const val KEY_MODEL_VERSION = "model_version"
        private const val DEFAULT_MODEL_VERSION = 0
        private const val MODEL_FILE_NAME = "latest_model.onnx"
        private const val VERSION_CHECK_URL = "https://lightstart-1376045481.cos.ap-guangzhou.myqcloud.com/ver.json\n" // 请替换为实际的JSON文件URL
    }

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    fun getCurrentModelVersion(): Int {
        return sharedPrefs.getInt(KEY_MODEL_VERSION, DEFAULT_MODEL_VERSION)
    }

    private fun saveModelVersion(version: Int) {
        sharedPrefs.edit().putInt(KEY_MODEL_VERSION, version).apply()
    }

    suspend fun checkForUpdate(): ModelVersionInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "检查模型版本更新...")
            val request = Request.Builder()
                .url(VERSION_CHECK_URL)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "检查版本更新失败: ${response.code}")
                return@withContext null
            }

            val jsonString = response.body?.string() ?: return@withContext null
            val versionInfo = gson.fromJson(jsonString, ModelVersionInfo::class.java)
            
            Log.d(TAG, "远程版本: ${versionInfo.version}, 本地版本: ${getCurrentModelVersion()}")
            
            if (versionInfo.version > getCurrentModelVersion()) {
                return@withContext versionInfo
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "检查版本更新异常", e)
            return@withContext null
        }
    }

    suspend fun downloadModel(versionInfo: ModelVersionInfo, progressCallback: (progress: Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始下载模型: ${versionInfo.url}")
            
            val request = Request.Builder()
                .url(versionInfo.url)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "下载模型失败: ${response.code}")
                return@withContext false
            }

            val inputStream = response.body?.byteStream() ?: return@withContext false
            val contentLength = response.body?.contentLength() ?: -1
            
            val modelFile = getDownloadedModelFile()
            val tempFile = File(modelFile.parent, "${modelFile.name}.tmp")
            
            FileOutputStream(tempFile).use { output ->
                var totalBytesRead = 0L
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        progressCallback(progress)
                    }
                }
            }
            
            if (tempFile.exists() && tempFile.length() > 0) {
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                
                if (tempFile.renameTo(modelFile)) {
                    saveModelVersion(versionInfo.version)
                    Log.d(TAG, "模型下载并保存成功")
                    return@withContext true
                } else {
                    Log.e(TAG, "重命名临时文件失败")
                    tempFile.delete()
                    return@withContext false
                }
            } else {
                Log.e(TAG, "下载的文件为空")
                tempFile.delete()
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "下载模型异常", e)
            return@withContext false
        }
    }

    fun getModelFilePath(): String {
        val downloadedModel = getDownloadedModelFile()
        return if (downloadedModel.exists()) {
            downloadedModel.absolutePath
        } else {
            "model.onnx" // 使用assets中的默认模型
        }
    }

    private fun getDownloadedModelFile(): File {
        return File(context.filesDir, MODEL_FILE_NAME)
    }

    fun hasDownloadedModel(): Boolean {
        return getDownloadedModelFile().exists()
    }
}