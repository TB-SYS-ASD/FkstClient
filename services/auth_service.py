"""
认证服务模块

该模块提供了用户身份验证功能，包括登录和凭证管理功能。
"""
from core.api_client import APIClient
from config.settings import settings
from utils.encryption import encrypt_password
from utils.logger import setup_logger

logger = setup_logger(__name__)

class AuthService:
    def __init__(self, api_client: APIClient):
        self.client = api_client
    
    def login(self, phone_number: str, password: str) -> bool:
        """用户登录
        
        Args:
            phone_number: 手机号码
            password: 原始密码
            
        Returns:
            bool: 登录是否成功
        """
        try:
            # 加密密码
            encrypted_pwd = encrypt_password(password)
            
            # 准备登录参数
            login_params = {
                "phone_number": phone_number,
                "password": encrypted_pwd,
            }
            
            logger.debug("登录请求参数: phone=%s, verify_type=2", phone_number)
            
            # 发送登录请求
            response = self.client.request("LOGIN", **login_params)
            
            # 检查响应
            if response.get("res") == 0:  # 成功响应
                # 更新用户凭证
                self._update_credentials(response)
                logger.info("登录成功: unionid=%s, mid=%s", response['unionid'], response['mid'])
                return True
            
            logger.error("登录失败: %s", response)
            return False
            
        except Exception as e:
            logger.exception("登录异常: %s", str(e))
            return False
    
    def _update_credentials(self, response: dict) -> dict:
        """更新用户凭证"""
        # 提取响应中的用户凭证
        new_credentials = {
            "unionid": response.get("unionid", ""),
            "openid": response.get("openid", ""),
            "mid": response.get("mid", ""),
            "device_token": response.get("device_token", ""),
            "identity": response.get("identity", settings.DEVICE_PARAMS["identity"])
        }
        
        # 更新客户端中的动态参数
        self.client.update_dynamic_params(**new_credentials)
        return new_credentials