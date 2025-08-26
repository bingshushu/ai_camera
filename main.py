from libs.PipeLine import PipeLine, ScopedTiming
from libs.YOLO import YOLOv8
import os,sys,gc
import ulab.numpy as np
import image
from machine import TOUCH
from led_control import LEDController, TouchSlider
import time

def draw_cross(img, x, y, size=15, color=(255,0,0), thickness=2):
    # 画水平线
    img.draw_line(x-size, y, x+size, y, color, thickness)
    # 画垂直线
    img.draw_line(x, y-size, x, y+size, color, thickness)

def draw_circle_detection(img, x1, y1, x2, y2, label, score, color=(0, 255, 0), thickness=2, display_size=[800, 480], rgb888p_size=[1280, 720]):
    """使用圆形替代矩形框绘制检测结果"""
    # 计算中心点并映射到显示分辨率
    center_x = int((x1 + x2) / 2 * display_size[0] / rgb888p_size[0])
    center_y = int((y1 + y2) / 2 * display_size[1] / rgb888p_size[1])
    
    # 计算半径 (取宽高的最大值的一半)，并根据显示比例调整
    width = x2 - x1
    height = y2 - y1
    radius = int(max(width, height) / 2 * min(display_size[0] / rgb888p_size[0], display_size[1] / rgb888p_size[1]))
    
    # 绘制圆形
    img.draw_circle(center_x, center_y, radius, color=color, thickness=thickness)
    
    # 在圆形上方绘制标签和分数
    text = f"{label}: {score:.2f}"
    # 调整文本位置到适当位置
    text_x = center_x - radius
    text_y = center_y - radius - 30
    if text_y < 10:
        text_y = 10
    
    img.draw_string_advanced(text_x, text_y, 24, text, color=color, scale=1)

# 重写YOLOv8类的draw_result方法，使用圆形替代矩形
def custom_draw_result(yolo_instance, res, img, display_size=[800, 480], rgb888p_size=[1280, 720]):
    """自定义绘制YOLO检测结果的函数，使用圆形替代矩形"""
    if res is None or len(res) == 0:
        return
    
    # 遍历每个检测结果
    for det in res:
        # det格式：[x1, y1, x2, y2, score, class_id]
        x1, y1, x2, y2 = map(int, det[:4])
        score = det[4]
        class_id = int(det[5])
        
        # 获取标签
        label = yolo_instance.labels[class_id] if class_id < len(yolo_instance.labels) else str(class_id)
        
        # 根据类别选择不同颜色
        color = yolo_instance.colors[class_id % len(yolo_instance.colors)]
        
        # 使用圆形绘制检测结果
        draw_circle_detection(img, x1, y1, x2, y2, label, score, color=color, thickness=2, 
                           display_size=display_size, rgb888p_size=rgb888p_size)

class ToggleButton:
    def __init__(self, img, x, y, width, height, text, active=False, on_color=(0, 200, 0), off_color=(200, 0, 0), text_color=(255, 255, 255)):
        """
        创建一个开关按钮
        
        Args:
            img: 显示图像
            x, y: 按钮左上角位置
            width, height: 按钮尺寸
            text: 按钮文本
            active: 初始状态是否激活
            on_color: 激活状态颜色
            off_color: 未激活状态颜色
            text_color: 文本颜色
        """
        self.img = img
        self.x = int(x)
        self.y = int(y)
        self.width = int(width)
        self.height = int(height)
        self.text = text
        self.active = active
        self.on_color = on_color
        self.off_color = off_color
        self.text_color = text_color
        self.last_press_time = 0
        
    def draw(self):
        """绘制按钮"""
        # 选择当前状态的颜色
        color = self.on_color if self.active else self.off_color
        
        # 绘制按钮背景
        self.img.draw_rectangle(self.x, self.y, self.width, self.height, color=color, fill=True)
        self.img.draw_rectangle(self.x, self.y, self.width, self.height, color=(255, 255, 255), thickness=2)
        
        # 绘制按钮文本
        text_x = int(self.x + self.width // 2 - len(self.text) * 8)
        text_y = int(self.y + self.height // 2 - 15)
        self.img.draw_string_advanced(text_x, text_y, 30, self.text, color=self.text_color, scale=2)
        
        # 绘制当前状态文本
        status_text = "开启" if self.active else "关闭"
        status_x = int(self.x + self.width // 2 - len(status_text) * 8)
        status_y = int(self.y + self.height + 10)
        self.img.draw_string_advanced(status_x, status_y, 24, status_text, color=self.text_color, scale=1)
    
    def handle_touch(self, touch_point):
        """
        处理触摸事件
        
        Args:
            touch_point: 触摸点对象，包含x, y坐标
            
        Returns:
            如果按钮状态改变则返回True，否则返回False
        """
        current_time = time.ticks_ms()
        
        # 检查是否在按钮区域内触摸
        if (self.x <= touch_point.x <= self.x + self.width and
            self.y <= touch_point.y <= self.y + self.height):
            
            # 防抖动：确保两次触发之间至少间隔500毫秒
            if current_time - self.last_press_time > 500:
                self.active = not self.active
                self.last_press_time = current_time
                return True
                
        return False

if __name__=="__main__":
    # 显示模式，默认"hdmi",可以选择"hdmi"和"lcd"
    display_mode="lcd"
    rgb888p_size=[1280,720]
    if display_mode=="hdmi":
        display_size=[1920,1080]
    else:
        display_size=[800,480]
    # 路径可以自行修改适配您自己的模型
    kmodel_path="/sdcard/model/best_625.kmodel"
    labels = ["ROI","RedCenter"]
    confidence_threshold = 0.1
    nms_threshold=0.48
    model_input_size=[320,320]
    
    # 初始化LED控制器
    led_controller = LEDController(pin=61, channel=1, freq=2000, initial_duty=50)
    
    # 初始化触摸屏
    tp = TOUCH(0)
    
    # 初始化PipeLine
    pl = None
    yolo = None
    
    try:
        # 初始化PipeLine
        pl=PipeLine(rgb888p_size=rgb888p_size,display_size=display_size,display_mode=display_mode)
        pl.create()
        
        # 初始化YOLOv8实例
        yolo=YOLOv8(task_type="detect",mode="video",kmodel_path=kmodel_path,labels=labels,rgb888p_size=rgb888p_size,model_input_size=model_input_size,display_size=display_size,conf_thresh=confidence_threshold,nms_thresh=nms_threshold,max_boxes_num=80,debug_mode=0)
        yolo.config_preprocess()
        
        # 创建亮度调节滑块 - 放在屏幕底部
        slider_width = int(display_size[0] * 0.7)  # 滑块宽度为屏幕宽度的70%
        slider_height = 40  # 滑块高度
        slider_x = int((display_size[0] - slider_width) // 2)  # 水平居中
        slider_y = int(display_size[1] - slider_height - 30)  # 底部位置，留出一些边距
        
        brightness_slider = TouchSlider(
            pl.osd_img,
            slider_x,
            slider_y,
            slider_width,
            slider_height,
            led_controller,
            color=(0, 150, 255),
            bg_color=(50, 50, 50)
        )
        
        # 创建YOLO检测开关按钮 - 放在屏幕右上角
        button_width = 160
        button_height = 60
        button_x = int(display_size[0] - button_width - 20)  # 右侧位置
        button_y = 20  # 顶部位置
        
        detection_button = ToggleButton(
            pl.osd_img,
            button_x,
            button_y,
            button_width,
            button_height,
            "目标检测",
            active=False,  # 初始状态为关闭
            on_color=(0, 200, 0),  # 开启时为绿色
            off_color=(200, 0, 0)   # 关闭时为红色
        )
        
        while True:
            try:
                os.exitpoint()
                with ScopedTiming("total",1):
                    # 逐帧推理
                    img=pl.get_frame()
                    
                    # 只有当检测按钮激活时才执行YOLO检测
                    res = None
                    if detection_button.active:
                        res = yolo.run(img)
                    
                    # 清除之前的绘制内容
                    pl.osd_img.clear()
                    
                    # 使用自定义方法绘制圆形检测结果，而不是原本的矩形框
                    if detection_button.active and res is not None:
                        custom_draw_result(yolo, res, pl.osd_img, display_size=display_size, rgb888p_size=rgb888p_size)
                    
                        # 在每个检测目标的中心绘制十字标记
                        if len(res) > 0:
                            for det in res:
                                # det: [x1, y1, x2, y2, score, cls]
                                x1, y1, x2, y2 = map(int, det[:4])
                                # 坐标映射到显示分辨率
                                x_center = int((x1 + x2) / 2 * display_size[0] / rgb888p_size[0])
                                y_center = int((y1 + y2) / 2 * display_size[1] / rgb888p_size[1])
                                draw_cross(pl.osd_img, x_center, y_center, size=20, color=(255,0,0), thickness=3)
                    
                    # 处理触摸输入
                    touch_points = tp.read(1)
                    if touch_points != ():
                        for point in touch_points:
                            # 处理滑块的触摸事件
                            brightness_slider.handle_touch(point)
                            # 处理检测按钮的触摸事件
                            detection_button.handle_touch(point)
                    
                    # 绘制亮度调节滑块
                    brightness_slider.draw()
                    
                    # 绘制检测开关按钮
                    detection_button.draw()
                    
                    pl.show_image()
                    gc.collect()
            except Exception as e:
                # 捕获帧处理中的异常，但允许程序继续运行
                error_type = type(e).__name__
                if error_type == 'KeyboardInterrupt' or 'IDE interrupt' in str(e):
                    print("用户中断，正在退出...")
                    break
                else:
                    sys.print_exception(e)
                    print("发生错误，但程序将继续运行...")
                    # 短暂暂停以避免错误消息快速循环
                    time.sleep_ms(500)
    except KeyboardInterrupt:
        print("用户中断，正在退出...")
    except Exception as e:
        sys.print_exception(e)
    finally:
        # 清理资源
        if led_controller:
            try:
                led_controller.deinit()
            except:
                pass
        
        if yolo:
            try:
                yolo.deinit()
            except:
                pass
            
        if pl:
            try:
                pl.destroy()
            except:
                pass
