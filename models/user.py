"""
用户模型定义
"""

from dataclasses import dataclass
from typing import Optional, Dict, Any, List


@dataclass
class UserMessages:
    """
    用户消息数据类，用于存储各类未读消息数量
    """
    system: str = "0"      # 系统通知
    comment: str = "0"     # 评论消息
    like: str = "0"        # 点赞消息
    private: str = "0"     # 私信消息
    
    @classmethod
    def from_dict(cls, data: Dict[str, str]) -> 'UserMessages':
        """
        从字典数据创建UserMessages实例
        
        Args:
            data (dict): 包含消息信息的字典
            
        Returns:
            UserMessages: UserMessages实例
        """
        return cls(
            system=data.get('system', '0'),
            comment=data.get('comment', '0'),
            like=data.get('like', '0'),
            private=data.get('private', '0')
        )
    
    @classmethod
    def from_list(cls, data: List[Dict[str, Any]]) -> 'UserMessages':
        """
        从消息列表创建UserMessages实例
        原始数据格式: [{'type': 1, 'count': '0'}, {'type': 2, 'count': '0'}, ...]
        
        Args:
            data (list): 包含消息信息的列表
            
        Returns:
            UserMessages: UserMessages实例
        """
        messages = cls()
        if not data:
            return messages
            
        for msg in data:
            msg_type = msg.get('type')
            msg_count = msg.get('count', '0')
            if msg_type == 1:
                messages.system = msg_count
            elif msg_type == 2:
                messages.comment = msg_count
            elif msg_type == 3:
                messages.like = msg_count
            elif msg_type == 4:
                messages.private = msg_count
                
        return messages
    
    def to_dict(self) -> Dict[str, str]:
        """
        将UserMessages实例转换为字典
        
        Returns:
            dict: 消息信息字典
        """
        return {
            "system": self.system,
            "comment": self.comment,
            "like": self.like,
            "private": self.private
        }
    
    def __str__(self) -> str:
        """
        返回消息信息的字符串表示
        
        Returns:
            str: 消息信息字符串
        """
        return (f"消息通知: 系统({self.system}) 评论({self.comment}) "
                f"点赞({self.like}) 私信({self.private})")


@dataclass
class User:
    """
    用户实体类
    """
    uid: str
    nickname: str
    avatar: str
    phone: Optional[str] = None
    phone_number: Optional[str] = None
    fans_count: Optional[str] = None
    coin_count: Optional[str] = None
    messages: Optional[UserMessages] = None
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'User':
        """
        从字典数据创建User实例
        
        Args:
            data (dict): 包含用户信息的字典
            
        Returns:
            User: User实例
        """
        # 处理消息数据
        messages_data = data.get("messages")
        messages = None
        if messages_data:
            if isinstance(messages_data, dict):
                messages = UserMessages.from_dict(messages_data)
            elif isinstance(messages_data, UserMessages):
                messages = messages_data
            elif isinstance(messages_data, list):
                messages = UserMessages.from_list(messages_data)
        
        return cls(
            uid=str(data.get("uid", "")),
            nickname=data.get("nick_name", ""),
            avatar=data.get("logo", ""),
            phone_number=data.get("phone_number"),
            fans_count=data.get("fans_count"),
            coin_count=data.get("coin_count"),
            messages=messages
        )
    
    def to_dict(self) -> Dict[str, Any]:
        """
        将User实例转换为字典
        
        Returns:
            dict: 用户信息字典
        """
        result = {
            "uid": self.uid,
            "nick_name": self.nickname,
            "logo": self.avatar,
            "phone_number": self.phone_number,
            "fans_count": self.fans_count,
            "coin_count": self.coin_count
        }
        
        # 处理消息数据
        if self.messages:
            result["messages"] = self.messages.to_dict()
        else:
            result["messages"] = None
            
        return result
    
    def is_authenticated(self) -> bool:
        """
        检查用户是否已认证（通过uid是否存在来判断）
        
        Returns:
            bool: 如果用户已认证返回True，否则返回False
        """
        return bool(self.uid)
    
    def __str__(self) -> str:
        """
        返回用户信息的字符串表示
        
        Returns:
            str: 用户信息字符串
        """
        base_info = f"用户 {self.nickname}(ID: {self.uid}) - 粉丝: {self.fans_count}, 金币: {self.coin_count}"
        if self.messages:
            return f"{base_info}\n{self.messages}"
        return base_info
    
    def __repr__(self) -> str:
        """
        返回用户对象的详细字符串表示
        
        Returns:
            str: 用户对象的详细字符串表示
        """
        return (f"User(uid='{self.uid}', nickname='{self.nickname}', "
                f"avatar='{self.avatar}', "
                f"phone_number='{self.phone_number}', fans_count='{self.fans_count}', "
                f"coin_count='{self.coin_count}', messages={self.messages!r})")