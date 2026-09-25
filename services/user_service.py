"""
用户服务实现
"""

from typing import Optional

from core.api_client import APIClient
from models.user import User, UserMessages
from utils.logger import setup_logger

logger = setup_logger(__name__)

class UserService:
    """
    用户相关服务
    """
    
    def __init__(self, api_client: APIClient):
        """
        初始化用户服务
        
        Args:
            api_client (APIClient): API客户端实例
        """
        self.client = api_client
    
    def get_user_info(self) -> Optional[User]:
        """
        获取用户信息和资料
        
        Returns:
            User: 用户对象，如果获取失败则返回None
            
        Raises:
            Exception: 请求失败时抛出异常
        """


        try:
            info_response = self.client.request("GET_USER_INFO")
            profile_response = self.client.request("GET_MY_PROFILE")
          
            if info_response.get("res") == 0 and profile_response.get("res") == 0:
                member = info_response.get("member", {})  # 话说疯刷为什么要套那么多层
                message = profile_response.get("messages", [])  # [{'type': 1, 'count': '0'}, {'type': 2, 'count': '0'}, {'type': 3, 'count': '1'}, {'type': 4, 'count': '0'}]
                logger.debug(message)
                
                # 使用UserMessages类处理消息数据
                messages = UserMessages.from_list(message)
                
                user_data = {  # 因为fkst的json好乱，这里新建了一个字典来整合这些数据
                    "uid": member.get("id"),
                    "nick_name": member.get("nick_name"),
                    "logo": member.get("logo"),
                    "phone_number": member.get("phone_number"),
                    "fans_count": profile_response.get("fans_count"),        
                    "coin_count": profile_response.get("coin_count"),
                    "messages": messages
                }
                logger.info("成功获取用户信息: uid=%s, nickname=%s",
                           user_data.get("uid"),user_data.get("nick_name"))
                
                # 创建用户对象并返回
                user = User.from_dict(user_data)
                
                # 在日志中展示用户信息
                logger.info("用户信息: %s", user)
                
                return user
           
            else:
                error_msg = f"获取用户信息失败: {info_response}"
                logger.error(error_msg)
                return None
                
        except Exception as e:
            logger.exception("获取用户信息异常: %s", str(e))
            return None
    
    def get_user_profile(self, user: User) -> bool:
        """
        获取用户资料
        
        Args:
            user (User): 用户对象
            
        Returns:
            bool: 更新是否成功
            
        Raises:
            Exception: 请求失败时抛出异常
        """
        try:
            # 准备更新参数
            update_params = user.to_dict()
            
            response = self.client.request("UPDATE_USER_PROFILE", **update_params)
            
            if response.get("code") == 200 and response.get("res") == 0:
                logger.info("用户资料更新成功: uid=%s", user.uid)
                # 显示用户的未读消息数量
                if user.messages:
                    logger.info("用户未读消息: %s", user.messages)
                return True
            else:
                logger.error("用户资料更新失败: %s", response)
                return False
                
        except Exception as e:
            logger.exception("更新用户资料异常: %s", str(e))
            raise