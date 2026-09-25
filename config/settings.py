from random import randint
from time import time
from dotenv import load_dotenv, dotenv_values


# 加载环境变量
load_dotenv()

# 获取环境变量值
env_values = dotenv_values()

class Settings:
    # API基础配置
    BASE_URL = "https://api.yaerxing.com"
    
    # 设备固定参数
    DEVICE_PARAMS = {
        "api_key": "17bf6ed3b808eb7dcfa5wa0f1f0cf1de",
        "appid": "wx2bd42ba7f4c547f5",
        "app_c": "171",
        "app_v": "2.0.2",
        "channel": "none",
        "platform_id": "2",
        "device_imei": "f448c5eaf564af4dc63d5c0587e68290",
        "rom": "OPPO",
        "model": "PJJ110",
        "brand": "OPPO",
        "os_v": "29",
        "oam": "0",
        "url_name": "",
        "device_token": "",
        "identity": "171171dguf117cf2.a011f178egua59bd3dest.2194st1h",  # 固定identity
        "um_token": "AjyrWarcqPA-F-J60x70BmVVl8f0BWZzsx2WtcdvgSJm",
        
    }
    
    # 默认动态参数（未登录状态），登录之后会更新。
    DEFAULT_DYNAMIC_PARAMS = {
        "unionid": "guest",
        "openid": "guest",
        "mid": "1"
    }
    
    GET_NOTE_PARAMS = {
        "adolescent_model": "0",
        "api_key": "17bf6ed3b808eb7dcfa5wa0f1f0cf1de",
        "app_v": "171",
        "appid": "wx2bd42ba7f4c547f5",
        "channel": "none",
        "font_size": "2",
        # "id": "",
        # "mid": "",
        "os_v": "29",
        "platform_id": "2",
        "rom": "OPPO",
        # "unionid": "",
        "version": "2"
}

    # 环境变量
    LOGIN_PHONE = env_values.get("LOGIN_PHONE")
    LOGIN_PASSWORD = env_values.get("LOGIN_PASSWORD")
    DEBUG_MODE = env_values.get("DEBUG_MODE", "0")

settings = Settings()
