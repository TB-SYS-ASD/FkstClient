"""
通用分页逻辑模块
统一处理文章列表、评论列表、回复列表等的分页功能
消除重复的分页实现，提供一致的分页体验
"""

import time
from typing import List, Dict, Any, Optional, Callable, Tuple
from dataclasses import dataclass
from abc import ABC, abstractmethod

from rich.console import Console
from rich.table import Table
from rich import box
from rich.panel import Panel

from core.input_handler import InputHandler, KeyAction


@dataclass
class PaginationConfig:
    """分页配置"""

    page_size: int = 10
    initial_page: int = 0
    auto_load_next: bool = True  # 是否自动加载下一页
    cache_pages: bool = True  # 是否缓存页面数据


@dataclass
class PageData:
    """页面数据"""

    items: List[Any]
    page_number: int
    has_more: bool = True
    total_count: Optional[int] = None


class DataProvider(ABC):
    """数据提供者抽象基类"""

    @abstractmethod
    def load_page(self, page: int, **kwargs) -> PageData:
        """
        加载指定页的数据

        Args:
            page: 页码 (从0开始)
            **kwargs: 额外参数

        Returns:
            PageData: 页面数据
        """
        pass

    @abstractmethod
    def format_item(self, item: Any, index: int) -> Dict[str, str]:
        """
        格式化单个数据项用于显示

        Args:
            item: 数据项
            index: 在页面中的索引

        Returns:
            Dict[str, str]: 格式化后的显示数据
        """
        pass

    def get_table_columns(self) -> List[Dict[str, Any]]:
        """
        获取表格列定义

        Returns:
            List[Dict]: 列定义列表，包含 name, style, width 等
        """
        return [{"name": "内容", "style": "white"}]

    def handle_item_selection(self, item: Any) -> Optional[Any]:
        """
        处理项目选择事件

        Args:
            item: 被选择的项目

        Returns:
            Optional[Any]: 处理结果，返回非None值将退出列表
        """
        return None


class SocialPostDataProvider(DataProvider):
    """社交文章数据提供者"""

    def __init__(self, social_service, category_type: str = "10"):
        self.social_service = social_service
        self.category_type = category_type
        self.category_name = self._get_category_name()

    def _get_category_name(self) -> str:
        """获取分类名称"""
        try:
            if self.category_type == "f":
                return "关注用户"

            categories = self.social_service.get_social_categories()
            category_names = {cat["type"]: cat["name"] for cat in categories}
            return category_names.get(self.category_type, "未知分类")
        except Exception:
            return "文章列表"

    def load_page(self, page: int, **kwargs) -> PageData:
        """加载文章页面数据"""
        try:
            if self.category_type == "f":
                # 关注用户文章
                min_time = kwargs.get("min_time", str(int(time.time())))
                response = self.social_service.get_follow_user_notes(
                    min_time=min_time, page=str(page)
                )
            else:
                # 普通文章分类
                base_params = {
                    "page": str(page),
                    "type": self.category_type,
                    "start_time": str(int(time.time())),
                }
                # 如果有分类参数管理器，使用它来构建参数
                if hasattr(self.social_service, "category_param_manager"):
                    params = (
                        self.social_service.category_param_manager.build_request_params(
                            self.category_type, base_params
                        )
                    )
                else:
                    params = base_params

                response = self.social_service.get_posts(**params)

            if response.get("res") == 0:
                notes = response.get("notes", [])
                has_more = not response.get("over", False) and len(notes) > 0

                return PageData(items=notes, page_number=page, has_more=has_more)
            else:
                raise Exception("获取文章列表失败")

        except Exception as e:
            raise Exception(f"加载文章数据失败: {str(e)}")

    def format_item(self, item: Any, index: int) -> Dict[str, str]:
        """格式化文章项"""
        return {
            "序号": str(index + 1),
            "标题": item.get("title", "无标题")[:50],
            "作者": item.get("nick_name", "未知作者"),
            "点赞": str(item.get("like_count", 0)),
        }

    def get_table_columns(self) -> List[Dict[str, Any]]:
        """获取文章表格列定义"""
        return [
            {"name": "序号", "style": "green", "width": 6},
            {"name": "标题", "style": "magenta", "width": 50},
            {"name": "作者", "style": "cyan", "width": 15},
            {"name": "点赞", "style": "yellow", "width": 8},
        ]

    def handle_item_selection(self, item: Any) -> Optional[Any]:
        """处理文章选择"""
        note_id = item.get("id")
        if note_id:
            # 这里应该调用文章详情显示逻辑
            # 由于需要访问TUI实例，这个方法可能需要在具体使用时重写
            return {"action": "show_detail", "note_id": note_id, "note_data": item}
        return None


class CommentDataProvider(DataProvider):
    """评论数据提供者"""

    def __init__(self, social_service, post_id: str):
        self.social_service = social_service
        self.post_id = post_id

    def load_page(self, page: int, **kwargs) -> PageData:
        """加载评论页面数据"""
        try:
            response = self.social_service.get_comments(self.post_id, page=str(page))

            if response.get("res") == 0:
                comments = response.get("comments", [])
                has_more = not response.get("over", True)

                return PageData(items=comments, page_number=page, has_more=has_more)
            else:
                raise Exception("获取评论失败")

        except Exception as e:
            raise Exception(f"加载评论数据失败: {str(e)}")

    def format_item(self, item: Any, index: int) -> Dict[str, str]:
        """格式化评论项"""
        created_at = item.get("created_at", "")

        # 格式化时间
        if created_at.isdigit():
            import datetime

            created_time = datetime.datetime.fromtimestamp(int(created_at))
            formatted_time = created_time.strftime("%m-%d %H:%M")
        else:
            formatted_time = created_at

        # 智能截取评论内容，防止过长内容导致显示问题
        content = item.get("content", "")
        # 根据终端宽度动态调整截取长度
        max_content_length = 45  # 适合大多数终端的长度
        if len(content) > max_content_length:
            content = content[:max_content_length] + "..."

        return {
            "用户": item.get("nick_name", "未知用户"),
            "内容": content,
            "时间": formatted_time,
            "点赞": str(item.get("zan_ct", 0)),
            "回复": str(item.get("comment_ct", 0)),
        }

    def get_table_columns(self) -> List[Dict[str, Any]]:
        """获取评论表格列定义"""
        return [
            {"name": "用户", "style": "cyan", "width": 12, "no_wrap": True},
            {"name": "内容", "style": "white", "width": 45, "no_wrap": False},
            {"name": "时间", "style": "yellow", "width": 10, "no_wrap": True},
            {"name": "点赞", "style": "magenta", "width": 5, "no_wrap": True},
            {"name": "回复", "style": "green", "width": 5, "no_wrap": True},
        ]

    def handle_item_selection(self, item: Any) -> Optional[Any]:
        """处理评论选择"""
        comment_id = item.get("id")
        if comment_id:
            return {"action": "show_replies", "comment_id": comment_id}
        return None


class ReplyDataProvider(DataProvider):
    """回复数据提供者"""

    def __init__(self, social_service, comment_id: str):
        self.social_service = social_service
        self.comment_id = comment_id

    def load_page(self, page: int, **kwargs) -> PageData:
        """加载回复页面数据"""
        try:
            response = self.social_service.get_comment_replies(
                self.comment_id, page=str(page)
            )

            if response.get("res") == 0:
                replies = response.get("comments", [])
                has_more = not response.get("over", True)

                return PageData(items=replies, page_number=page, has_more=has_more)
            else:
                raise Exception("获取回复失败")

        except Exception as e:
            raise Exception(f"加载回复数据失败: {str(e)}")

    def format_item(self, item: Any, index: int) -> Dict[str, str]:
        """格式化回复项"""
        created_at = item.get("created_at", "")

        # 格式化时间
        if created_at.isdigit():
            import datetime

            created_time = datetime.datetime.fromtimestamp(int(created_at))
            formatted_time = created_time.strftime("%m-%d %H:%M")
        else:
            formatted_time = created_at

        # 智能截取回复内容，防止过长内容导致显示问题
        content = item.get("content", "")
        # 回复内容可以显示更长一些，但仍要控制在合理范围内
        max_content_length = 65  # 适合大多数终端的长度
        if len(content) > max_content_length:
            content = content[:max_content_length] + "..."

        return {
            "用户": item.get("nick_name", "未知用户"),
            "内容": content,
            "时间": formatted_time,
            "点赞": str(item.get("zan_ct", 0)),
        }

    def get_table_columns(self) -> List[Dict[str, Any]]:
        """获取回复表格列定义"""
        return [
            {"name": "用户", "style": "cyan", "width": 12, "no_wrap": True},
            {"name": "内容", "style": "white", "width": 65, "no_wrap": False},
            {"name": "时间", "style": "yellow", "width": 10, "no_wrap": True},
            {"name": "点赞", "style": "magenta", "width": 5, "no_wrap": True},
        ]


class UnifiedPaginationManager:
    """统一分页管理器"""

    def __init__(self, console: Console, input_handler: InputHandler):
        self.console = console
        self.input_handler = input_handler

    def show_paginated_data(
        self,
        title: str,
        data_provider: DataProvider,
        config: Optional[PaginationConfig] = None,
    ) -> Any:
        """
        显示分页数据

        Args:
            title: 页面标题
            data_provider: 数据提供者
            config: 分页配置

        Returns:
            Any: 用户操作结果
        """
        if config is None:
            config = PaginationConfig()

        # 直接使用 show_table_based_pagination 方法
        return self.show_table_based_pagination(title, data_provider, config)

    def show_table_based_pagination(
        self,
        title: str,
        data_provider: DataProvider,
        config: Optional[PaginationConfig] = None,
    ) -> Any:
        """
        显示基于表格的分页数据 (更适合结构化数据)

        Args:
            title: 页面标题
            data_provider: 数据提供者
            config: 分页配置

        Returns:
            Any: 用户操作结果
        """
        if config is None:
            config = PaginationConfig()

        # 根据终端大小动态调整每页显示数量
        terminal_height = self.console.size.height
        # 保留空间给标题、提示和操作按键说明，大约需要10-12行
        available_height = max(3, terminal_height - 12)
        # 每个评论可能占用2行（考虑换行和间距）
        max_items_per_page = max(3, available_height // 2)

        # 更新配置
        if config.page_size > max_items_per_page:
            config.page_size = max_items_per_page

        # 使用DirectionalInputHandler来显示分页评论，支持真正的方向键操作
        from core.input_handler import DirectionalInputHandler

        directional_handler = DirectionalInputHandler(self.console)

        # 存储当前页的原始数据，用于评论选择
        current_page_items = []

        # 构建数据加载函数
        def load_comments_data(page: int) -> tuple[list, bool]:
            nonlocal current_page_items
            try:
                page_data = data_provider.load_page(page)
                current_page_items = page_data.items  # 保存原始数据

                # 转换为表格数据格式
                table_data = []
                for i, item in enumerate(page_data.items):
                    formatted_item = data_provider.format_item(item, i)
                    # 按列顺序构建行数据
                    columns = data_provider.get_table_columns()
                    row = []
                    for col in columns:
                        col_name = col["name"]
                        value = formatted_item.get(col_name, "")
                        row.append(str(value))
                    table_data.append(row)

                return table_data, page_data.has_more
            except Exception as e:
                raise Exception(f"加载评论数据失败: {str(e)}")

        # 评论选择处理函数
        def handle_comment_selection(index: int, row_data: list) -> Optional[str]:
            # 处理评论选择，支持查看回复功能
            try:
                if 0 <= index < len(current_page_items):
                    selected_item = current_page_items[index]

                    # 判断是否是评论数据提供者
                    if isinstance(data_provider, CommentDataProvider):
                        comment_id = selected_item.get("id")
                        if comment_id:
                            # 获取social_service并显示回复
                            social_service = data_provider.social_service
                            self._show_comment_replies(social_service, comment_id)

                    # 对于其他类型的数据提供者，调用其handle_item_selection方法
                    else:
                        result = data_provider.handle_item_selection(selected_item)
                        if result:
                            return result

                return None
            except Exception as e:
                from rich.panel import Panel

                directional_handler.console.print(
                    Panel(f"处理评论选择时出错: {str(e)}", title="错误", style="red")
                )
                return None
        
        # 评论回复处理函数 - 为r键的特殊处理
        def handle_comment_reply_action(index: int, row_data: list) -> Optional[str]:
            """处理评论回复功能"""
            try:
                if 0 <= index < len(current_page_items):
                    selected_item = current_page_items[index]
                    
                    # 判断是否是评论数据提供者
                    if isinstance(data_provider, CommentDataProvider):
                        note_id = data_provider.post_id
                        comment_id = selected_item.get("id")
                        comment_author = selected_item.get("nick_name", "未知用户")
                        
                        if comment_id and note_id:
                            # 调用回复评论功能
                            social_service = data_provider.social_service
                            success = self._handle_comment_reply(
                                social_service, note_id, comment_id, comment_author
                            )
                            # 如果回复成功，返回刷新指令
                            if success:
                                return "refresh"
                return None
            except Exception as e:
                from rich.panel import Panel
                directional_handler.console.print(
                    Panel(f"处理回复时出错: {str(e)}", title="错误", style="red")
                )
                return None
        
        # 评论回复处理函数 - 新增
        def handle_comment_reply(index: int, row_data: list) -> Optional[str]:
            """处理评论回复功能"""
            try:
                if 0 <= index < len(current_page_items):
                    selected_item = current_page_items[index]
                    
                    # 判断是否是评论数据提供者
                    if isinstance(data_provider, CommentDataProvider):
                        note_id = data_provider.post_id
                        comment_id = selected_item.get("id")
                        comment_author = selected_item.get("nick_name", "未知用户")
                        
                        if comment_id and note_id:
                            # 调用回复评论功能
                            social_service = data_provider.social_service
                            success = self._handle_comment_reply(
                                social_service, note_id, comment_id, comment_author
                            )
                            # 如果回复成功，返回刷新指令
                            if success:
                                return "refresh"
                return None
            except Exception as e:
                from rich.panel import Panel
                directional_handler.console.print(
                    Panel(f"处理回复时出错: {str(e)}", title="错误", style="red")
                )
                return None
        
        # 发表新评论处理函数 - 新增  
        def handle_new_comment(index: int, row_data: list) -> Optional[str]:
            """处理发表新评论功能"""
            try:
                # 判断是否是评论数据提供者
                if isinstance(data_provider, CommentDataProvider):
                    note_id = data_provider.post_id
                    social_service = data_provider.social_service
                    
                    # 调用发表新评论功能
                    success = self._handle_new_comment(social_service, note_id)
                    # 如果发表成功，返回刷新指令
                    if success:
                        return "refresh"
                return None
            except Exception as e:
                from rich.panel import Panel
                directional_handler.console.print(
                    Panel(f"处理新评论时出错: {str(e)}", title="错误", style="red")
                )
                return None

        # 获取列名
        columns = [col["name"] for col in data_provider.get_table_columns()]
        
        # 根据数据类型设置不同的提示文本
        if isinstance(data_provider, CommentDataProvider):
            hint_text = "↑↓:导航 | Enter:查看回复 | r:回复评论 | c:发表评论 | Space/n:下一页 | p:上一页 | b:返回"
        else:
            hint_text = "↑↓:导航 | Enter:选择 | Space/n:下一页 | p:上一页 | b:返回"

        # 使用DirectionalInputHandler的show_paginated_table方法
        while True:
            result = directional_handler.show_paginated_table(
                title=title,
                columns=columns,
                data_loader=load_comments_data,
                item_handler=handle_comment_selection,
                hint_text=hint_text,
                reply_handler=handle_comment_reply_action  # 传入回复处理函数
            )
            
            # 处理特殊返回值
            if result == "new_comment" and isinstance(data_provider, CommentDataProvider):
                # 处理发表新评论
                note_id = data_provider.post_id
                social_service = data_provider.social_service
                success = self._handle_new_comment(social_service, note_id)
                if success:
                    # 如果发表成功，继续循环以刷新评论列表
                    continue
                else:
                    # 如果发表失败，也继续循环返回评论列表
                    continue
            else:
                # 其他情况直接返回结果
                return result

    def _show_comment_replies(self, social_service, comment_id: str) -> None:
        """显示评论回复"""
        try:
            # 创建回复数据提供者
            reply_provider = ReplyDataProvider(social_service, comment_id)

            # 递归调用自身的show_table_based_pagination
            config = PaginationConfig(
                page_size=6
            )  # 使用更小的页面大小，防止内容过长时截断
            result = self.show_table_based_pagination(
                title=f"评论回复", data_provider=reply_provider, config=config
            )

        except Exception as e:
            from utils.logger import setup_logger

            logger = setup_logger(__name__)
            logger.exception("显示评论回复时发生异常: %s", str(e))
            from rich.panel import Panel

            self.console.print(
                Panel(f"显示回复时出错: {str(e)}", title="错误", style="red")
            )
            self.input_handler.wait_for_continue()
    
    def _handle_comment_reply(self, social_service, note_id: str, comment_id: str, comment_author: str) -> bool:
        """处理评论回复"""
        try:
            from core.ui_components import CommentInputHandler
            
            # 创建评论输入处理器
            comment_input = CommentInputHandler(self.console)
            
            # 显示评论输入界面
            content = comment_input.show_comment_input(
                note_id=note_id,
                parent_comment_id=comment_id,
                reply_to_user=comment_author
            )
            
            if content:
                # 发布回复
                response = social_service.post_comment(content, note_id, comment_id)
                
                if response.get('res') == 0:
                    # 显示成功结果
                    comment_input.show_comment_result(
                        success=True,
                        message=f"回复发布成功！评论ID: {response.get('id', '')}",
                        is_reply=True
                    )
                    return True
                else:
                    # 显示失败结果
                    comment_input.show_comment_result(
                        success=False,
                        message=f"服务器返回错误码: {response.get('res')}",
                        is_reply=True
                    )
                    return False
            
            return False
            
        except Exception as e:
            from utils.logger import setup_logger
            logger = setup_logger(__name__)
            logger.error(f"处理回复评论旱败: {str(e)}")
            
            from core.ui_components import CommentInputHandler
            comment_input = CommentInputHandler(self.console)
            comment_input.show_comment_result(
                success=False,
                message=str(e),
                is_reply=True
            )
            return False
    
    def _handle_new_comment(self, social_service, note_id: str) -> bool:
        """处理发表新评论"""
        try:
            from core.ui_components import CommentInputHandler
            
            # 创建评论输入处理器
            comment_input = CommentInputHandler(self.console)
            
            # 显示评论输入界面
            content = comment_input.show_comment_input(
                note_id=note_id,
                parent_comment_id="0"  # 外层评论
            )
            
            if content:
                # 发布评论
                response = social_service.post_comment(content, note_id, "0")
                
                if response.get('res') == 0:
                    # 显示成功结果
                    comment_input.show_comment_result(
                        success=True,
                        message=f"评论发布成功！评论ID: {response.get('id', '')}",
                        is_reply=False
                    )
                    return True
                else:
                    # 显示失败结果
                    comment_input.show_comment_result(
                        success=False,
                        message=f"服务器返回错误码: {response.get('res')}",
                        is_reply=False
                    )
                    return False
            
            return False
            
        except Exception as e:
            from utils.logger import setup_logger
            logger = setup_logger(__name__)
            logger.error(f"处理新评论失败: {str(e)}")
            
            from core.ui_components import CommentInputHandler
            comment_input = CommentInputHandler(self.console)
            comment_input.show_comment_result(
                success=False,
                message=str(e),
                is_reply=False
            )
            return False
