package com.ai.bb.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.NodeInfo
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * YOLOv8 ONNX模型检测器，专门用于检测圆形目标
 * 支持两个类别：ROI和RedCenter
 */
class OnnxCircleDetector(context: Context, private val modelUpdateManager: ModelUpdateManager? = null) {
    
    /**
     * 检测结果数据类
     * @param cx 圆心x坐标（原图坐标系）
     * @param cy 圆心y坐标（原图坐标系）
     * @param r 圆的半径（原图坐标系）
     * @param confidence 置信度
     * @param className 类别名称
     */
    data class Circle(
        val cx: Float, 
        val cy: Float, 
        val r: Float, 
        val confidence: Float, 
        val className: String
    )

    /**
     * YOLOv8检测框数据类
     */
    private data class Detection(
        val x1: Float,
        val y1: Float, 
        val x2: Float,
        val y2: Float,
        val confidence: Float,
        val classId: Int
    )

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    
    // 模型配置
    private var modelInputSize = 320 // 默认输入尺寸，将从模型中动态获取
    private val confThreshold = 0.1f // 置信度阈值，与Python代码保持一致
    private val nmsThreshold = 0.48f // NMS阈值，与Python代码保持一致
    private val classNames = arrayOf("ROI", "RedCenter") // 类别名称

    init {
        val modelBytes = if (modelUpdateManager?.hasDownloadedModel() == true) {
            // 使用下载的最新模型
            val modelPath = modelUpdateManager.getModelFilePath()
            Log.i("OnnxCircleDetector", "使用下载的模型: $modelPath")
            File(modelPath).readBytes()
        } else {
            // 使用assets中的默认模型
            Log.i("OnnxCircleDetector", "使用assets中的默认模型")
            context.assets.open("model.onnx").use { it.readBytes() }
        }
        
        session = env.createSession(modelBytes)
        
        // 从模型中获取输入尺寸
        getModelInputSize()
        
        // 打印模型信息
        logModelInfo()
    }
    
    private fun getModelInputSize() {
        try {
            val inputName = session.inputNames.first()
            val inputNode = session.inputInfo.getValue(inputName)
            val inputTensorInfo = inputNode.info as? TensorInfo
            
            if (inputTensorInfo != null) {
                val shape = inputTensorInfo.shape
                if (shape.size >= 4) {
                    // 期望格式: [batch, channels, height, width]
                    val height = shape[2].toInt()
                    val width = shape[3].toInt()
                    if (height > 0 && width > 0 && height == width) {
                        modelInputSize = height
                        Log.i("OnnxCircleDetector", "从模型获取输入尺寸: ${modelInputSize}x${modelInputSize}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("OnnxCircleDetector", "无法获取模型输入尺寸，使用默认值: ${modelInputSize}x${modelInputSize}")
        }
    }

    fun close() {
        try { session.close() } catch (_: Throwable) {}
        try { env.close() } catch (_: Throwable) {}
    }
    
    private fun logModelInfo() {
        Log.i("OnnxCircleDetector", "=== 模型信息 ===")
        Log.i("OnnxCircleDetector", "输入: ${session.inputNames}")
        session.inputInfo.forEach { (name, node) ->
            val infoObj: Any? = node.info
            if (infoObj is TensorInfo) {
                Log.i(
                    "OnnxCircleDetector",
                    "输入 $name -> 类型=TENSOR 元素类型=${infoObj.type} 形状=${infoObj.shape.contentToString()}"
                )
            }
        }
        
        Log.i("OnnxCircleDetector", "输出: ${session.outputNames}")
        session.outputInfo.forEach { (name, node) ->
            val infoObj: Any? = node.info
            if (infoObj is TensorInfo) {
                Log.i(
                    "OnnxCircleDetector",
                    "输出 $name -> 类型=TENSOR 元素类型=${infoObj.type} 形状=${infoObj.shape.contentToString()}"
                )
            }
        }
    }

    /**
     * 主要检测方法
     * @param bitmap 输入图像
     * @return 检测到的圆形列表
     */
    fun detect(bitmap: Bitmap): List<Circle> {
        return try {
            Log.i("OnnxCircleDetector", "开始检测，输入图像尺寸: ${bitmap.width}x${bitmap.height}")
            
            // 1. 预处理图像：letterbox缩放到模型输入尺寸
            val letterbox = createLetterboxBitmap(bitmap, modelInputSize, modelInputSize)
            val inputArray = bitmapToFloatArray(letterbox.bitmap, normalize = true)
            
            // 2. 创建输入张量
            val inputName = session.inputNames.first()
            val inputTensor = OnnxTensor.createTensor(
                env, 
                FloatBuffer.wrap(inputArray), 
                longArrayOf(1, 3, modelInputSize.toLong(), modelInputSize.toLong())
            )
            
            // 3. 运行推理
            val outputs = session.run(mapOf(inputName to inputTensor))
            
            // 4. 解析YOLOv8输出
            val circles = parseYoloOutput(outputs, letterbox, bitmap.width, bitmap.height)
            
            // 5. 清理资源
            outputs.close()
            inputTensor.close()
            
            Log.i("OnnxCircleDetector", "检测完成，找到 ${circles.size} 个目标")
            circles
            
        } catch (e: Exception) {
            Log.e("OnnxCircleDetector", "检测失败", e)
            emptyList()
        }
    }

    /**
     * Letterbox预处理结果
     */
    private data class LetterboxResult(
        val bitmap: Bitmap,
        val padX: Float,
        val padY: Float,
        val scale: Float
    )

    /**
     * 创建letterbox图像，保持宽高比并填充到目标尺寸
     */
    private fun createLetterboxBitmap(src: Bitmap, targetW: Int, targetH: Int): LetterboxResult {
        val srcW = src.width
        val srcH = src.height
        
        if (srcW <= 0 || srcH <= 0) {
            val blank = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            return LetterboxResult(blank, 0f, 0f, 1f)
        }
        
        // 计算缩放比例，保持宽高比
        val scale = minOf(targetW.toFloat() / srcW, targetH.toFloat() / srcH)
        val newW = (srcW * scale).toInt()
        val newH = (srcH * scale).toInt()
        
        // 计算padding
        val padX = (targetW - newW) / 2f
        val padY = (targetH - newH) / 2f
        
        // 创建目标bitmap并绘制
        val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // 填充黑色背景
        canvas.drawColor(0xFF000000.toInt())
        
        val paint = Paint().apply { 
            isFilterBitmap = true 
            isAntiAlias = true
        }
        
        val srcRect = Rect(0, 0, srcW, srcH)
        val dstRect = Rect(
            padX.toInt(), 
            padY.toInt(), 
            padX.toInt() + newW, 
            padY.toInt() + newH
        )
        
        canvas.drawBitmap(src, srcRect, dstRect, paint)
        
        return LetterboxResult(result, padX, padY, scale)
    }

    /**
     * 将bitmap转换为CHW格式的float数组
     */
    private fun bitmapToFloatArray(bitmap: Bitmap, normalize: Boolean = true): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val output = FloatArray(3 * height * width)
        
        // CHW格式：Channel-Height-Width
        val rOffset = 0
        val gOffset = height * width
        val bOffset = 2 * height * width
        
        var pixelIndex = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[pixelIndex++]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val chwIndex = y * width + x
                if (normalize) {
                    output[rOffset + chwIndex] = r / 255f
                    output[gOffset + chwIndex] = g / 255f  
                    output[bOffset + chwIndex] = b / 255f
                } else {
                    output[rOffset + chwIndex] = r.toFloat()
                    output[gOffset + chwIndex] = g.toFloat()
                    output[bOffset + chwIndex] = b.toFloat()
                }
            }
        }
        
        return output
    }

    /**
     * 解析YOLOv8模型输出
     */
    private fun parseYoloOutput(
        outputs: OrtSession.Result, 
        letterbox: LetterboxResult,
        originalWidth: Int,
        originalHeight: Int
    ): List<Circle> {
        try {
            val outputTensor = outputs.get(0) as? OnnxTensor ?: return emptyList()
            val outputArray = outputTensor.floatBuffer ?: return emptyList()
            
            // YOLOv8输出形状通常是 [1, 84, 8400] 或 [1, 6, anchor_points]
            // 其中84 = 4(bbox) + 80(COCO类) 或 6 = 4(bbox) + 2(我们的类)
            val shape = (outputTensor.info as TensorInfo).shape
            Log.i("OnnxCircleDetector", "输出形状: ${shape.contentToString()}")
            
            if (shape.size != 3) {
                Log.e("OnnxCircleDetector", "不支持的输出形状: ${shape.contentToString()}")
                return emptyList()
            }
            
            val batchSize = shape[0].toInt()
            val numFeatures = shape[1].toInt() 
            val numAnchors = shape[2].toInt()
            
            // 预期特征数 = 4(bbox) + 2(类别数)
            val expectedFeatures = 4 + classNames.size
            if (numFeatures != expectedFeatures) {
                Log.w("OnnxCircleDetector", "特征数不匹配，预期: $expectedFeatures，实际: $numFeatures")
                // 如果特征数不匹配且少于预期，调整类别数
                if (numFeatures < expectedFeatures) {
                    Log.w("OnnxCircleDetector", "调整类别数以匹配模型输出")
                }
            }
            
            // 解析检测结果
            val detections = mutableListOf<Detection>()
            outputArray.rewind()
            val data = FloatArray(numFeatures * numAnchors)
            outputArray.get(data)
            
            // YOLOv8输出可能有两种格式：
            // 1. [1, features, anchors] - 我们当前的假设
            // 2. [1, anchors, features] - 另一种可能的格式
            // 我们需要检测实际的格式
            val isTransposed = numFeatures > numAnchors
            val actualAnchors = if (isTransposed) numFeatures else numAnchors
            val actualFeatures = if (isTransposed) numAnchors else numFeatures
            
            Log.i("OnnxCircleDetector", "数据格式: ${if (isTransposed) "转置" else "标准"} - 锚点数: $actualAnchors, 特征数: $actualFeatures")
            
            for (i in 0 until actualAnchors) {
                // 根据格式调整数据访问方式
                val xCenter: Float
                val yCenter: Float
                val width: Float
                val height: Float
                
                if (isTransposed) {
                    // 格式: [1, anchors, features]
                    xCenter = data[i * actualFeatures + 0]
                    yCenter = data[i * actualFeatures + 1]
                    width = data[i * actualFeatures + 2]
                    height = data[i * actualFeatures + 3]
                } else {
                    // 格式: [1, features, anchors]
                    xCenter = data[i]
                    yCenter = data[actualAnchors + i]
                    width = data[2 * actualAnchors + i]
                    height = data[3 * actualAnchors + i]
                }
                
                // 计算类别分数 - 动态确定实际类别数
                var maxScore = -1f
                var maxClassId = -1
                val actualNumClasses = actualFeatures - 4 // 减去4个bbox特征
                val numClassesToCheck = minOf(classNames.size, actualNumClasses)
                
                for (c in 0 until numClassesToCheck) {
                    val score: Float = if (isTransposed) {
                        data[i * actualFeatures + (4 + c)]
                    } else {
                        val index = (4 + c) * actualAnchors + i
                        if (index < data.size) {
                            data[index]
                        } else {
                            Log.w("OnnxCircleDetector", "跳过越界访问: index=$index, dataSize=${data.size}")
                            continue
                        }
                    }
                    if (score > maxScore) {
                        maxScore = score
                        maxClassId = c
                    }
                }
                
                // 应用置信度阈值
                if (maxScore >= confThreshold) {
                    // 转换为x1,y1,x2,y2格式
                    val x1 = xCenter - width / 2
                    val y1 = yCenter - height / 2
                    val x2 = xCenter + width / 2
                    val y2 = yCenter + height / 2
                    
                    detections.add(Detection(x1, y1, x2, y2, maxScore, maxClassId))
                }
            }
            
            Log.i("OnnxCircleDetector", "置信度阈值过滤后: ${detections.size} 个候选")
            
            // 应用NMS
            val nmsDetections = applyNMS(detections, nmsThreshold)
            Log.i("OnnxCircleDetector", "NMS后: ${nmsDetections.size} 个检测结果")
            
            // 转换为圆形并映射回原图坐标
            return nmsDetections.map { detection ->
                convertDetectionToCircle(detection, letterbox, originalWidth, originalHeight)
            }
            
        } catch (e: Exception) {
            Log.e("OnnxCircleDetector", "解析输出失败", e)
            return emptyList()
        }
    }

    /**
     * 非最大抑制(NMS)
     */
    private fun applyNMS(detections: List<Detection>, nmsThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        
        // 按置信度降序排序
        val sortedDetections = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()
        
        while (sortedDetections.isNotEmpty()) {
            val current = sortedDetections.removeAt(0)
            result.add(current)
            
            // 移除与当前检测重叠度过高的其他检测
            sortedDetections.removeAll { other ->
                calculateIoU(current, other) > nmsThreshold
            }
        }
        
        return result
    }

    /**
     * 计算两个检测框的IoU (Intersection over Union)
     */
    private fun calculateIoU(det1: Detection, det2: Detection): Float {
        val x1 = maxOf(det1.x1, det2.x1)
        val y1 = maxOf(det1.y1, det2.y1) 
        val x2 = minOf(det1.x2, det2.x2)
        val y2 = minOf(det1.y2, det2.y2)
        
        if (x2 <= x1 || y2 <= y1) return 0f
        
        val intersection = (x2 - x1) * (y2 - y1)
        val area1 = (det1.x2 - det1.x1) * (det1.y2 - det1.y1)
        val area2 = (det2.x2 - det2.x1) * (det2.y2 - det2.y1)
        val union = area1 + area2 - intersection
        
        return if (union > 0) intersection / union else 0f
    }

    /**
     * 将检测框转换为圆形并映射回原图坐标系
     */
    private fun convertDetectionToCircle(
        detection: Detection,
        letterbox: LetterboxResult,
        originalWidth: Int,
        originalHeight: Int
    ): Circle {
        // 计算边界框中心点（模型输出坐标系）
        val centerX = (detection.x1 + detection.x2) / 2
        val centerY = (detection.y1 + detection.y2) / 2
        
        // 计算半径（取宽高的平均值或最大值）
        val width = detection.x2 - detection.x1
        val height = detection.y2 - detection.y1
        val radius = maxOf(width, height) / 2
        
        // 去除letterbox padding
        val centerXNoPad = centerX - letterbox.padX
        val centerYNoPad = centerY - letterbox.padY
        
        // 缩放回原图尺寸
        val originalCenterX = centerXNoPad / letterbox.scale
        val originalCenterY = centerYNoPad / letterbox.scale
        val originalRadius = radius / letterbox.scale
        
        val className = if (detection.classId < classNames.size) {
            classNames[detection.classId]
        } else {
            "Unknown"
        }
        
        Log.i(
            "OnnxCircleDetector", 
            "检测到 $className: 中心(${originalCenterX.toInt()}, ${originalCenterY.toInt()}) " +
            "半径=${originalRadius.toInt()} 置信度=${String.format("%.3f", detection.confidence)}"
        )
        
        return Circle(
            originalCenterX,
            originalCenterY, 
            originalRadius,
            detection.confidence,
            className
        )
    }
}
