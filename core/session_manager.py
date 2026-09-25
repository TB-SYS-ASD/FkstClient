import json
import os
from pathlib import Path
from typing import Dict, Optional
from utils.logger import setup_logger

logger = setup_logger(__name__)

class SessionManager:
    """会话管理器，用于持久化存储和恢复用户登录状态"""
    
    def __init__(self, session_file: str = "user_session.json"):
        self.session_file = Path(session_file)
        self.session_data = {}
    
    def save_session(self, credentials: Dict[str, str]) -> bool:
        """
        保存用户会话信息到文件
        
        Args:
            credentials: 用户凭证信息
            
        Returns:
            bool: 保存是否成功
        """
        try:
            # 确保只保存必要的凭证信息
            session_data = {
                "unionid": credentials.get("unionid", ""),
                "openid": credentials.get("openid", ""),
                "mid": credentials.get("mid", ""),
                "device_token": credentials.get("device_token", ""),
            }
            
            # 保存到文件
            with open(self.session_file, 'w', encoding='utf-8') as f:
                json.dump(session_data, f, ensure_ascii=False, indent=2)
            
            logger.info("用户会话信息已保存到 %s", self.session_file)
            return True
            
        except Exception as e:
            logger.error("保存会话信息失败: %s", str(e))
            return False
    
    def load_session(self) -> Optional[Dict[str, str]]:
        """
        从文件加载用户会话信息
        
        Returns:
            Dict[str, str]: 用户凭证信息，如果加载失败则返回None
        """
        try:
            # 检查会话文件是否存在
            if not self.session_file.exists():
                logger.debug("会话文件不存在: %s", self.session_file)
                return None
            
            # 从文件加载
            with open(self.session_file, 'r', encoding='utf-8') as f:
                session_data = json.load(f)
            
            # 验证必要字段
            required_fields = ["unionid", "openid", "mid"]
            if not all(field in session_data for field in required_fields):
                logger.warning("会话文件缺少必要字段，可能已损坏")
                return None
            
            logger.info("从 %s 成功加载用户会话信息", self.session_file)
            return session_data
            
        except json.JSONDecodeError as e:
            logger.error("会话文件格式错误: %s", str(e))
            return None
        except Exception as e:
            logger.error("加载会话信息失败: %s", str(e))
            return None
    
    def clear_session(self) -> bool:
        """
        清除保存的会话信息
        
        Returns:
            bool: 清除是否成功
        """
        try:
            if self.session_file.exists():
                self.session_file.unlink()
                logger.info("用户会话信息已清除")
            return True
            
        except Exception as e:
            logger.error("清除会话信息失败: %s", str(e))
            return False