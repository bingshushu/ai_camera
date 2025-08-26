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
import java.nio.FloatBuffer

/**
 * Minimal ONNX Runtime wrapper to detect circles from a bitmap.
 * Note: The exact input size and output parsing depend on your model.
 * We introspect the first input tensor shape to resize the bitmap accordingly (NCHW float32).
 */
class OnnxCircleDetector(context: Context) {
    data class Circle(val cx: Float, val cy: Float, val r: Float)

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets.open("model.onnx").use { it.readBytes() }
        session = env.createSession(modelBytes)
    }

    fun close() {
        try { session.close() } catch (_: Throwable) {}
        try { env.close() } catch (_: Throwable) {}
    }

    fun detect(bitmap: Bitmap): List<Circle> {
        return try {
            // Log input meta
            Log.i("OnnxCircleDetector", "Inputs: ${session.inputNames}")
            session.inputInfo.forEach { (name, node) ->
                val infoObj: Any? = node.info
                if (infoObj is TensorInfo) {
                    Log.i(
                        "OnnxCircleDetector",
                        "Input $name -> kind=TENSOR elemType=${infoObj.type} shape=${infoObj.shape.contentToString()}"
                    )
                } else {
                    Log.i(
                        "OnnxCircleDetector",
                        "Input $name -> kind=${infoObj?.javaClass?.simpleName ?: "Unknown"}"
                    )
                }
            }
            // Log output meta
            Log.i("OnnxCircleDetector", "Outputs: ${session.outputNames}")
            session.outputInfo.forEach { (name, node) ->
                val infoObj: Any? = node.info
                if (infoObj is TensorInfo) {
                    Log.i(
                        "OnnxCircleDetector",
                        "Output $name -> kind=TENSOR elemType=${infoObj.type} shape=${infoObj.shape.contentToString()}"
                    )
                } else {
                    Log.i(
                        "OnnxCircleDetector",
                        "Output $name -> kind=${infoObj?.javaClass?.simpleName ?: "Unknown"}"
                    )
                }
            }

            val inputName = session.inputNames.first()
            val inputNode: NodeInfo = session.inputInfo.getValue(inputName)
            val inputInfoObj: Any? = inputNode.info
            val inputTensorInfo = inputInfoObj as? TensorInfo
            if (inputTensorInfo == null) {
                Log.e(
                    "OnnxCircleDetector",
                    "Input $inputName is not a tensor. kind=${inputInfoObj?.javaClass?.simpleName ?: "Unknown"}"
                )
                return emptyList()
            }
            val inputShape = inputTensorInfo.shape
            // Expecting [N, C, H, W]
            val n = inputShape.getOrNull(0)?.toInt() ?: 1
            val c = inputShape.getOrNull(1)?.toInt() ?: 3
            val h = inputShape.getOrNull(2)?.toInt() ?: bitmap.height
            val w = inputShape.getOrNull(3)?.toInt() ?: bitmap.width

            // Build letterboxed input to preserve aspect ratio
            val lb = buildLetterboxedBitmap(bitmap, w, h)
            val chw = bitmapToCHWFloat(lb.bmp, normalize = true)
            val fb = FloatBuffer.wrap(chw)
            val inputTensor = OnnxTensor.createTensor(env, fb, longArrayOf(n.toLong(), c.toLong(), h.toLong(), w.toLong()))
            val results = session.run(mapOf(inputName to inputTensor))
            val circles = mutableListOf<Circle>()

            // Dump sample output values to Logcat to infer format
            for (i in 0 until results.size()) {
                val v = results.get(i)
                Log.i("OnnxCircleDetector", "Result[$i] type=${v.type} class=${v.javaClass.simpleName}")
                if (v is OnnxTensor) {
                    val value = v.value
                    val preview = previewFloats(value, limit = 16)
                    Log.i("OnnxCircleDetector", "Result[$i] preview=$preview")
                }
            }

            // Extra analysis for primary output 'output0' if available
            try {
                val outNames = session.outputNames.toList()
                val idx0 = outNames.indexOf("output0")
                if (idx0 >= 0) {
                    val nodeInfo = session.outputInfo["output0"]
                    val tInfo = nodeInfo?.info as? TensorInfo
                    val out = results.get(idx0)
                    if (tInfo != null && out is OnnxTensor) {
                        val shape = tInfo.shape
                        if (shape.size == 3 && shape[0] == 1L && shape[1] == 6L) {
                            val channels = shape[1].toInt()
                            val count = shape[2].toInt()
                            val fb = out.floatBuffer?.duplicate()
                            if (fb != null) {
                                fb.rewind()
                                val arr = FloatArray(channels * count)
                                fb.get(arr)
                                // per-channel stats
                                for (cIdx in 0 until channels) {
                                    var mn = Float.POSITIVE_INFINITY
                                    var mx = Float.NEGATIVE_INFINITY
                                    for (j in 0 until count) {
                                        val vj = arr[cIdx * count + j]
                                        if (vj < mn) mn = vj
                                        if (vj > mx) mx = vj
                                    }
                                    val samples = (0 until kotlin.math.min(8, count)).joinToString(
                                        prefix = "[", postfix = "]", separator = ", "
                                    ) { k -> arr[cIdx * count + k].toString() }
                                    Log.i(
                                        "OnnxCircleDetector",
                                        "output0 ch=$cIdx min=$mn max=$mx samples=$samples"
                                    )
                                }
                                // Heuristic: assume ch3..5 contain scores; pick best among them
                                var bestScore = -Float.MAX_VALUE
                                var bestIdx = -1
                                var bestScoreCh = -1
                                for (j in 0 until count) {
                                    for (scCh in 3 until channels) {
                                        val s = arr[scCh * count + j]
                                        if (s > bestScore) { bestScore = s; bestIdx = j; bestScoreCh = scCh }
                                    }
                                }
                                if (bestIdx >= 0) {
                                    val cx = arr[0 * count + bestIdx]
                                    val cy = arr[1 * count + bestIdx]
                                    val r  = arr[2 * count + bestIdx]
                                    Log.i(
                                        "OnnxCircleDetector",
                                        "Best candidate idx=$bestIdx cx=$cx cy=$cy r=$r scoreCh=$bestScoreCh score=$bestScore"
                                    )
                                    // Map from model input (w,h) back to original bitmap size using letterbox params
                                    val cxNoPad = (cx - lb.padX)
                                    val cyNoPad = (cy - lb.padY)
                                    val cxImg = cxNoPad / lb.scale
                                    val cyImg = cyNoPad / lb.scale
                                    val rImg = r / lb.scale
                                    circles.add(Circle(cxImg, cyImg, rImg))
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w("OnnxCircleDetector", "output0 analysis failed: ${e.message}")
            }

            // Close and return detected circles
            results.close()
            inputTensor.close()
            circles
        } catch (t: Throwable) {
            Log.e("OnnxCircleDetector", "detect failed", t)
            emptyList()
        }
    }

    private data class Letterbox(val bmp: Bitmap, val padX: Float, val padY: Float, val scale: Float)

    private fun buildLetterboxedBitmap(src: Bitmap, targetW: Int, targetH: Int): Letterbox {
        val sw = src.width
        val sh = src.height
        if (sw <= 0 || sh <= 0) {
            val blank = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            return Letterbox(blank, 0f, 0f, 1f)
        }
        val scale = minOf(targetW.toFloat() / sw.toFloat(), targetH.toFloat() / sh.toFloat())
        val nw = (sw * scale).toInt()
        val nh = (sh * scale).toInt()
        val padX = ((targetW - nw) / 2f)
        val padY = ((targetH - nh) / 2f)
        val dst = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)
        val paint = Paint().apply { isFilterBitmap = true }
        // Optional: fill black background (already zeroed)
        val srcRect = Rect(0, 0, sw, sh)
        val dstRect = Rect(padX.toInt(), padY.toInt(), padX.toInt() + nw, padY.toInt() + nh)
        canvas.drawBitmap(src, srcRect, dstRect, paint)
        return Letterbox(dst, padX, padY, scale)
    }

    private fun previewFloats(value: Any?, limit: Int): String {
        if (value == null) return "null"
        val out = ArrayList<Float>()
        flattenToFloats(value, out, limit)
        return out.joinToString(prefix = "[", postfix = "]", separator = ", ")
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenToFloats(value: Any?, sink: MutableList<Float>, limit: Int) {
        if (value == null || sink.size >= limit) return
        when (value) {
            is FloatArray -> {
                for (f in value) { if (sink.size >= limit) break; sink.add(f) }
            }
            is DoubleArray -> {
                for (d in value) { if (sink.size >= limit) break; sink.add(d.toFloat()) }
            }
            is IntArray -> {
                for (x in value) { if (sink.size >= limit) break; sink.add(x.toFloat()) }
            }
            is Array<*> -> {
                for (e in value) { if (sink.size >= limit) break; flattenToFloats(e, sink, limit) }
            }
            else -> {
                // attempt to toString for other types
                // no-op for preview
            }
        }
    }

    private fun bitmapToCHWFloat(bm: Bitmap, normalize: Boolean): FloatArray {
        val w = bm.width
        val h = bm.height
        val pixels = IntArray(w * h)
        bm.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = FloatArray(3 * h * w)
        var idx = 0
        // CHW: R plane, G plane, B plane
        val rOffset = 0
        val gOffset = h * w
        val bOffset = 2 * h * w
        for (y in 0 until h) {
            for (x in 0 until w) {
                val color = pixels[idx++]
                val r = (color shr 16 and 0xFF)
                val g = (color shr 8 and 0xFF)
                val b = (color and 0xFF)
                if (normalize) {
                    out[rOffset + y * w + x] = r / 255f
                    out[gOffset + y * w + x] = g / 255f
                    out[bOffset + y * w + x] = b / 255f
                } else {
                    out[rOffset + y * w + x] = r.toFloat()
                    out[gOffset + y * w + x] = g.toFloat()
                    out[bOffset + y * w + x] = b.toFloat()
                }
            }
        }
        return out
    }
}
