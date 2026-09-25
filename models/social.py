"""
社交模型定义
"""

from dataclasses import dataclass
from typing import Optional, Dict, Any, List
import json
from utils.logger import setup_logger

logger = setup_logger(__name__)


@dataclass
class Note:
    """
    文章/笔记数据类，用于表示用户发布的社交内容
    """
    id: str = ""  # 文章id?
    title: str = ""  # 文章标题
    logo: str = ""  # 作者头像
    thumb: str = ""  # 第一张图片
    urls: List[str] = None  # 图片
    created_at: str = ""
    updated_at: str = ""
    like_count: int = 0
    home_id: str = ""  # 作者主页
    nick_name: str = ""  # 作者昵称
    show_card: bool = False
    self_like: bool = False
    
    def __post_init__(self):
        if self.urls is None:
            self.urls = []
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Note':
        """
        从字典数据创建Note实例
        
        Args:
            data (dict): 包含文章信息的字典
            
        Returns:
            Note: Note实例
        """
        logger.debug(f"Note.from_dict接收到的数据: {data}")
        # 处理URLs字符串转换为列表
        urls = []
        if data.get("urls"):
            try:
                urls = json.loads(data["urls"])
                logger.debug(f"解析得到的urls列表: {urls}")
            except (json.JSONDecodeError, TypeError):
                urls = [data["urls"]] if isinstance(data["urls"], str) else []
                logger.debug(f"直接获取的urls: {urls}")
        else:
            logger.debug("数据中未包含urls字段")
        
        result = cls(
            id=str(data.get("id", "")),
            title=data.get("title", ""),
            logo=data.get("logo", ""),
            thumb=data.get("thumb", ""),
            urls=urls,
            created_at=str(data.get("created_at", "")),
            updated_at=str(data.get("updated_at", "")),
            like_count=int(data.get("like_count", 0)),
            home_id=str(data.get("home_id", "")),
            nick_name=data.get("nick_name", ""),
            show_card=bool(data.get("show_card", False)),
            self_like=bool(data.get("self_like", False))
        )
        logger.debug(f"创建的Note实例urls字段: {result.urls}")
        return result
    
    def to_dict(self) -> Dict[str, Any]:
        """
        将Note实例转换为字典
        
        Returns:
            dict: 文章信息字典
        """
        return {
            "id": self.id,
            "title": self.title,
            "logo": self.logo,
            "thumb": self.thumb,
            "urls": json.dumps(self.urls) if self.urls else "[]",
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "like_count": self.like_count,
            "home_id": self.home_id,
            "nick_name": self.nick_name,
            "show_card": self.show_card,
            "self_like": self.self_like
        }


@dataclass
class Comment:
    """
    评论数据类
    """
    id: str = ""
    content: str = ""
    author: str = ""
    avatar: str = ""
    created_time: str = ""
    parent_id: str = "0"  # 新增：父级评论ID
    note_id: str = ""     # 新增：关联文章ID
    reply_count: int = 0  # 新增：回复数量
    coin_count: str = ""  # 新增：用户积分数量
    size_score: int = 0   # 新增：内容评分
    
    @property
    def is_reply(self) -> bool:
        """判断是否为回复评论"""
        return self.parent_id != "0"
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Comment':
        """
        从字典数据创建Comment实例
        
        Args:
            data (dict): 包含评论信息的字典
            
        Returns:
            Comment: Comment实例
        """
        return cls(
            id=str(data.get("id", "")),
            content=data.get("content", ""),
            author=data.get("nick_name", ""),
            avatar=data.get("logo", ""),
            created_time=str(data.get("created_at", "")),
            parent_id=str(data.get("fid", "0")),
            note_id=str(data.get("nid", "")),
            reply_count=int(data.get("comment_ct", 0)),
            coin_count=data.get("coin_count", ""),
            size_score=int(data.get("size_score", 0))
        )
    
    @classmethod
    def from_api_response(cls, data: dict, note_id: str = "", parent_id: str = "0") -> 'Comment':
        """
        从API响应创建Comment实例
        
        Args:
            data (dict): API响应数据
            note_id (str): 文章ID
            parent_id (str): 父级评论ID
            
        Returns:
            Comment: Comment实例
        """
        return cls(
            id=str(data.get("id", "")),
            content=data.get("content", ""),
            note_id=note_id,
            parent_id=parent_id,
            coin_count=data.get("coin_count", ""),
            size_score=int(data.get("size_score", 0))
        )
    
    def to_dict(self) -> Dict[str, Any]:
        """
        将Comment实例转换为字典
        
        Returns:
            dict: 评论信息字典
        """
        return {
            "id": self.id,
            "content": self.content,
            "nick_name": self.author,
            "logo": self.avatar,
            "created_at": self.created_time,
            "fid": self.parent_id,
            "nid": self.note_id,
            "comment_ct": self.reply_count,
            "coin_count": self.coin_count,
            "size_score": self.size_score
        }


@dataclass
class Like:
    """
    点赞数据类
    """
    id: str = ""
    user_id: str = ""
    username: str = ""
    avatar: str = ""
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Like':
        """
        从字典数据创建Like实例
        
        Args:
            data (dict): 包含点赞信息的字典
            
        Returns:
            Like: Like实例
        """
        # TODO: 实现从字典创建实例的逻辑
        pass
    
    def to_dict(self) -> Dict[str, Any]:
        """
        将Like实例转换为字典
        
        Returns:
            dict: 点赞信息字典
        """
        # TODO: 实现转换为字典的逻辑
        pass