# YOLOv8 圆形检测器使用说明

## 概述

这个项目包含一个重新构建的 `OnnxCircleDetector` 类，专门用于在 Android 应用中检测圆形目标。该检测器基于训练好的 YOLOv8 模型，能够准确识别两种类别的目标：`ROI`（感兴趣区域）和 `RedCenter`（红色中心点）。

## 模型信息

- **模型类型**: YOLOv8 检测模型
- **输入尺寸**: 320x320 像素（从模型自动检测）
- **类别数量**: 2个类别
  - `ROI`: 感兴趣区域（绿色显示）
  - `RedCenter`: 红色中心点（红色显示）
- **置信度阈值**: 0.1（与Python训练配置一致）
- **NMS阈值**: 0.48（与Python训练配置一致）

## 主要功能特性

### 1. 精确的圆心定位
- 从YOLOv8的边界框输出计算精确的圆心位置
- 支持letterbox预处理，保持图像宽高比
- 自动坐标映射回原始图像尺寸
- **动态输入尺寸检测**: 自动从ONNX模型中获取正确的输入尺寸

### 2. 智能输出格式检测
- 自动检测ONNX模型的输出格式（标准或转置）
- 支持两种常见的YOLOv8 ONNX输出格式：
  - `[1, features, anchors]`
  - `[1, anchors, features]`

### 3. 完整的后处理流程
- 置信度阈值过滤
- 非最大抑制（NMS）去除重复检测
- IoU计算和重叠框合并

### 4. 可视化增强
- 不同类别使用不同颜色显示
- 圆心十字标记提高可见性
- 支持缩放时的自适应线条宽度

## 使用方法

### 1. 初始化检测器

```kotlin
class MyActivity : ComponentActivity() {
    private var detector: OnnxCircleDetector? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 确保model.onnx文件放在assets目录下
        detector = OnnxCircleDetector(this)
    }
}
```

### 2. 运行检测

```kotlin
fun detectCircles(bitmap: Bitmap) {
    val circles = detector?.detect(bitmap) ?: emptyList()
    
    circles.forEach { circle ->
        Log.i("Detection", 
            "发现${circle.className}: " +
            "中心(${circle.cx.toInt()}, ${circle.cy.toInt()}) " +
            "半径=${circle.r.toInt()} " +
            "置信度=${String.format("%.3f", circle.confidence)}"
        )
    }
}
```

### 3. 绘制检测结果

在Compose Canvas中绘制：

```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    circles.forEach { circle ->
        val color = when (circle.className) {
            "RedCenter" -> Color.Red
            "ROI" -> Color.Green
            else -> Color.White
        }
        
        // 绘制圆形边界
        drawCircle(
            color = color,
            radius = circle.r,
            center = Offset(circle.cx, circle.cy),
            style = Stroke(width = 3f)
        )
        
        // 绘制中心点十字标记
        val crossSize = 15f
        drawLine(
            color = color,
            start = Offset(circle.cx - crossSize, circle.cy),
            end = Offset(circle.cx + crossSize, circle.cy),
            strokeWidth = 3f
        )
        drawLine(
            color = color,
            start = Offset(circle.cx, circle.cy - crossSize),
            end = Offset(circle.cx, circle.cy + crossSize),
            strokeWidth = 3f
        )
    }
}
```

### 4. 资源清理

```kotlin
override fun onDestroy() {
    super.onDestroy()
    detector?.close()
    detector = null
}
```

## 数据结构

### Circle 检测结果

```kotlin
data class Circle(
    val cx: Float,          // 圆心X坐标（原图坐标系）
    val cy: Float,          // 圆心Y坐标（原图坐标系）
    val r: Float,           // 圆的半径（原图坐标系）
    val confidence: Float,  // 置信度 (0.0-1.0)
    val className: String   // 类别名称 ("ROI" 或 "RedCenter")
)
```

## 技术实现细节

### 预处理流程
1. **Letterbox缩放**: 保持图像宽高比，填充黑色边框
2. **颜色空间转换**: RGB → CHW格式的float数组
3. **归一化**: 像素值从0-255缩放到0.0-1.0

### 后处理流程
1. **输出解析**: 自动检测并解析YOLOv8输出格式
2. **置信度过滤**: 移除低置信度检测
3. **NMS处理**: 去除重叠检测框
4. **坐标转换**: 映射回原图坐标系
5. **圆形计算**: 从边界框计算圆心和半径

### 性能优化
- 智能格式检测减少数据处理开销
- 高效的NMS算法
- 内存管理和资源自动清理

## 调试和日志

检测器会输出详细的日志信息：

```
I/OnnxCircleDetector: === 模型信息 ===
I/OnnxCircleDetector: 输入: [images]
I/OnnxCircleDetector: 输出: [output0]
I/OnnxCircleDetector: 开始检测，输入图像尺寸: 1920x1080
I/OnnxCircleDetector: 输出形状: [1, 6, 8400]
I/OnnxCircleDetector: 数据格式: 标准 - 锚点数: 8400, 特征数: 6
I/OnnxCircleDetector: 置信度阈值过滤后: 15 个候选
I/OnnxCircleDetector: NMS后: 3 个检测结果
I/OnnxCircleDetector: 检测到 RedCenter: 中心(640, 360) 半径=25 置信度=0.892
```

## 故障排除

### 常见问题

1. **输入尺寸不匹配错误**
   ```
   Error: Got invalid dimensions for input: images
   index: 2 Got: 512 Expected: 320
   ```
   - **解决方案**: 代码现在会自动从模型中检测正确的输入尺寸
   - 检查模型导出时使用的输入尺寸设置
   - 确保ONNX模型与训练配置一致

2. **没有检测结果**
   - 检查模型文件是否正确放置在`assets/model.onnx`
   - 验证输入图像是否包含目标物体
   - 调整置信度阈值

3. **坐标不准确**
   - 确认输入图像尺寸与显示尺寸的映射关系
   - 检查letterbox预处理的参数

4. **性能问题**
   - 考虑降低输入图像分辨率
   - 调整NMS阈值减少计算量

## 更新历史

- **v2.0**: 完全重写基于YOLOv8标准输出格式
  - 支持标准YOLOv8后处理流程
  - 智能输出格式检测
  - 改进的坐标映射
  - 增强的可视化效果

- **v1.0**: 初始版本（已废弃）
  - 基于猜测的输出格式解析
  - 简单的圆心检测逻辑
