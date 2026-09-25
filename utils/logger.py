# utils/logger.py
import logging
import os
import sys
from logging.handlers import RotatingFileHandler
from datetime import datetime
from pathlib import Path
from config.settings import settings

# 根据DEBUG_MODE环境变量控制是否启用控制台输出
# DEBUG_MODE=1时启用控制台输出，DEBUG_MODE=0或未设置时禁用控制台输出
DEBUG_MODE = settings.DEBUG_MODE
ENABLE_CONSOLE_OUTPUT = DEBUG_MODE == "1"

# 定义日志颜色
class LogColors:
    DEBUG = '\033[94m'    # 蓝色
    INFO = '\033[92m'     # 绿色
    WARNING = '\033[93m'  # 黄色
    ERROR = '\033[91m'    # 红色
    CRITICAL = '\033[95m' # 紫色
    RESET = '\033[0m'     # 重置颜色

# 彩色格式化器
class ColoredFormatter(logging.Formatter):
    def format(self, record):
        color_map = {
            logging.DEBUG: LogColors.DEBUG,
            logging.INFO: LogColors.INFO,
            logging.WARNING: LogColors.WARNING,
            logging.ERROR: LogColors.ERROR,
            logging.CRITICAL: LogColors.CRITICAL,
        }
        
        color = color_map.get(record.levelno, LogColors.RESET)
        message = super().format(record)
        return f"{color}{message}{LogColors.RESET}"

# 全局文件处理器
_file_handler = None

def _get_file_handler():
    """获取全局文件处理器实例"""
    global _file_handler
    if _file_handler is None:
        # 创建日志目录
        logs_dir = Path("logs")
        logs_dir.mkdir(exist_ok=True)
        
        # 创建全局文件处理器（带轮转）
        log_file = logs_dir / f"{datetime.now().strftime('%Y%m%d')}.log"
        _file_handler = RotatingFileHandler(
            log_file, maxBytes=5*1024*1024, backupCount=5  # 5MB/文件，保留5个
        )
        file_formatter = logging.Formatter(
            "%(asctime)s | %(levelname)8s | %(name)s:%(lineno)d - %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S"
        )
        _file_handler.setFormatter(file_formatter)
        _file_handler.setLevel(logging.INFO)  #? 文件只记录INFO及以上级别
    return _file_handler

def setup_logger(name: str, log_level: int = logging.DEBUG) -> logging.Logger:
    """配置并返回一个日志记录器
    
    Args:
        name: 日志记录器名称（通常是模块名）
        log_level: 日志级别（默认DEBUG）
        
    Returns:
        配置好的日志记录器
    """
    # 创建日志记录器
    logger_instance = logging.getLogger(name)
    logger_instance.setLevel(log_level)
    
    # 如果已经配置过处理器，直接返回
    if logger_instance.handlers:
        return logger_instance
    
    # 1. 控制台处理器（带颜色）
    if ENABLE_CONSOLE_OUTPUT:
        console_handler = logging.StreamHandler(sys.stdout)
        console_formatter = ColoredFormatter(
            "%(asctime)s | %(levelname)8s | %(name)s:%(lineno)d - %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S"
        )
        console_handler.setFormatter(console_formatter)
        console_handler.setLevel(log_level)
        logger_instance.addHandler(console_handler)
    
    # 2. 添加共享的文件处理器
    logger_instance.addHandler(_get_file_handler())
    
    # 添加启动日志
    logger_instance.info("=== 日志记录器初始化完成 ===")
    logger_instance.info("日志级别: %s", logging.getLevelName(log_level))
    logger_instance.info("日志文件: %s", _get_file_handler().baseFilename)
    
    return logger_instance