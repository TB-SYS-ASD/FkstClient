"""
工具模块初始化文件
"""

from .logger import setup_logger
from .encryption import *
from .html_restructure import HTMLRestructurer, restructure_html

__all__ = [
    'setup_logger',
    'HTMLRestructurer',
    'restructure_html'
]