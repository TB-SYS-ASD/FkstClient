"""
TUI (Text-based User Interface) for fkst-client - 重构优化版
基于Rich库的现代化文本用户界面，保持方向键控制和美观界面
消除重复代码，提高可维护性
"""

import os
import time
import datetime
from typing import Optional

from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich import box

from models.user import User
from services.user_service import UserService
from services.social_service import SocialService
from utils.logger import setup_logger
from config.settings import settings
from core.category_param_manager import CategoryParamManager
from core.input_handler import DirectionalInputHandler, InputHandler

logger = setup_logger(__name__)


class TUI:
    """重构优化后的文本用户界面主类"""

    def __init__(self, client):
        self.client = client
        logger.debug(
            "TUI初始化，客户端参数: %s", self.client.param_manager.dynamic_params
        )

        # 初始化组件
        self.console = Console()
        self.input_handler = InputHandler(self.console)
        self.directional_input = DirectionalInputHandler(self.console)

        # 初始化分区参数管理器
        self.category_param_manager = CategoryParamManager()

        self.running = True

    def show_user_info(self) -> Optional[User]:
        """显示用户信息界面"""
        self.console.clear()
        self.console.print(Panel("获取用户信息中...", title="请稍候"))

        try:
            user_service = UserService(self.client)
            user = user_service.get_user_info()

            if user:
                self.console.clear()

                # 创建用户信息表格
                table = Table(title="用户信息", box=box.ROUNDED)
                table.add_column("属性", style="cyan")
                table.add_column("值", style="magenta")

                table.add_row("用户ID", str(user.uid))
                table.add_row("昵称", user.nickname or "未设置")
                table.add_row("头像", user.avatar or "未设置")
                table.add_row("手机号", user.phone_number or "未绑定")
                table.add_row("粉丝数", str(user.fans_count or 0))
                table.add_row("金币数", str(user.coin_count or 0))

                self.console.print(table)

                # 如果用户有消息，显示消息信息
                if user.messages:
                    messages_table = Table(title="未读消息", box=box.ROUNDED)
                    messages_table.add_column("消息类型", style="cyan")
                    messages_table.add_column("未读数量", style="magenta")

                    messages_table.add_row("系统通知", str(user.messages.system))
                    messages_table.add_row("评论消息", str(user.messages.comment))
                    messages_table.add_row("点赞消息", str(user.messages.like))
                    messages_table.add_row("私信消息", str(user.messages.private))

                    self.console.print(messages_table)

                return user
            else:
                self.console.print(Panel("获取用户信息失败", title="错误", style="red"))
                return None

        except Exception as e:
            logger.exception("获取用户信息时发生异常: %s", str(e))
            self.console.print(
                Panel(f"获取用户信息时发生异常: {str(e)}", title="错误", style="red")
            )
            return None

    def show_social_menu(self) -> str:
        """显示社交功能菜单"""
        social_menu_items = [
            "[1] 查看发现文章",
            "[2] 查看关注文章",
            "[3] 查看指定用户文章",
            "[4] 私信功能 (待实现)",
            "[b] 返回主菜单",
        ]

        social_actions = [
            self.show_social_category_menu,
            lambda: self.show_social_posts("f"),
            self.show_user_articles_menu,
            lambda: self.show_feature_coming_soon("私信功能"),
            lambda: "back",
        ]

        return self.directional_input.show_live_menu(
            title="社交功能", menu_items=social_menu_items, item_actions=social_actions
        )

    def show_social_category_menu(self) -> str:
        """显示社交文章分区选择菜单"""
        try:
            social_service = SocialService(self.client)
            categories = social_service.get_social_categories()

            # 设置每个分区的参数到CategoryParamManager
            for category in categories:
                category_type = category["type"]
                category_params = category.get("params", {})
                if category_params:
                    self.category_param_manager.set_category_params(
                        category_type, category_params
                    )

            # 构建菜单项和动作
            category_menu_items = []
            category_actions = []

            for i, category in enumerate(categories):
                category_menu_items.append(f"[{i+1}] {category['name']}")
                category_type = category["type"]
                category_actions.append(
                    lambda ct=category_type: self.show_social_posts(ct)
                )

            category_menu_items.append("[b] 返回上级菜单")
            category_actions.append(lambda: "back")

            return self.directional_input.show_live_menu(
                title="选择文章分区",
                menu_items=category_menu_items,
                item_actions=category_actions,
            )

        except Exception as e:
            logger.exception("获取文章分区失败: %s", str(e))
            self.console.print(
                Panel(f"获取文章分区失败: {str(e)}", title="错误", style="red")
            )
            self.input_handler.wait_for_continue()
            return "back"

    def show_social_posts(self, category_type: str = "10") -> str:
        """显示社交文章列表"""
        try:
            social_service = SocialService(self.client)

            # 获取分类名称
            if category_type == "f":
                category_name = "关注用户"
            else:
                categories = social_service.get_social_categories()
                category_names = {
                    category["type"]: category["name"] for category in categories
                }
                category_name = category_names.get(category_type, "未知")

            # 存储原始文章数据，用于传递给详情页面
            posts_cache = {}

            # 数据加载函数
            def load_posts_data(page: int) -> tuple[list, bool]:
                try:
                    if category_type == "f":
                        # 关注用户文章
                        min_time = str(int(time.time()))
                        response = social_service.get_follow_user_notes(
                            min_time=min_time, page=str(page)
                        )
                    else:
                        # 普通文章分类
                        base_params = {
                            "page": str(page),
                            "type": category_type,
                            "start_time": str(int(time.time())),
                        }
                        params = self.category_param_manager.build_request_params(
                            category_type, base_params
                        )
                        response = social_service.get_posts(**params)

                    if response.get("res") == 0:
                        notes = response.get("notes", [])
                        has_more = not response.get("over", False) and len(notes) > 0

                        # 转换为表格数据格式，同时缓存原始数据
                        table_data = []
                        for i, note in enumerate(notes):
                            # 生成唯一键用于缓存
                            cache_key = f"{page}_{i}"
                            posts_cache[cache_key] = note

                            row = [
                                note.get("title", "无标题")[:40]
                                + ("..." if len(note.get("title", "")) > 40 else ""),
                                note.get("nick_name", "未知作者"),
                                str(note.get("like_count", 0)),
                                cache_key,  # 添加缓存键作为隐藏列
                            ]
                            table_data.append(row)

                        return table_data, has_more
                    else:
                        return [], False
                except Exception as e:
                    raise Exception(f"加载数据失败: {str(e)}")

            # 项目选择处理函数
            def handle_post_selection(index: int, row_data: list) -> Optional[str]:
                try:
                    # 从缓存中获取原始文章数据
                    cache_key = row_data[3]  # 缓存键在第4列
                    if cache_key in posts_cache:
                        note_data = posts_cache[cache_key]
                        note_id = note_data.get("id")
                        if note_id:
                            # 调用文章详情页面
                            self.show_post_detail(note_id, note_data)
                            return None

                    # 如果无法获取文章ID，显示错误
                    self.console.print(
                        Panel("无法获取文章详情", title="错误", style="red")
                    )
                    self.input_handler.wait_for_continue()
                    return None
                except Exception as e:
                    logger.exception("处理文章选择时出错: %s", str(e))
                    self.console.print(
                        Panel(f"处理选择时出错: {str(e)}", title="错误", style="red")
                    )
                    self.input_handler.wait_for_continue()
                    return None

            # 显示分页表格
            columns = ["标题", "作者", "点赞"]
            hint_text = "↑↓:导航 | Enter:查看详情 | Space/n:下一页 | p:上一页 | b:返回"

            result = self.directional_input.show_paginated_table(
                title=f"{category_name}文章",
                columns=columns,
                data_loader=load_posts_data,
                item_handler=handle_post_selection,
                hint_text=hint_text,
            )

            return result or "back"

        except Exception as e:
            logger.exception("显示文章列表失败: %s", str(e))
            self.console.print(
                Panel(f"显示文章列表失败: {str(e)}", title="错误", style="red")
            )
            self.input_handler.wait_for_continue()
            return "back"

    def show_post_detail(self, post_id: str, post_data: Optional[dict] = None) -> None:
        """显示文章详情"""
        self.console.clear()
        self.console.print(Panel("正在获取文章详情...", title="请稍候"))

        temp_file_path = None

        try:
            social_service = SocialService(self.client)

            # 强制使用HTML重构功能，改善显示效果
            use_restructured = True

            # 获取文章详情
            response = social_service.get_note(
                post_id,
                replace_content=True,
                restructure_html=use_restructured,
                post_data=post_data or {},
            )

            if response.get("res") == 0:
                self.console.clear()

                # 处理文章详情数据
                note_data = response.get("data", {})
                logger.debug(f"从 API获取的文章详情数据: {note_data.keys()}")

                # 获取解密和原始内容
                decrypted_content = note_data.get("decrypted_content", "")
                original_content = note_data.get("content", "")

                # 构建显示内容
                display_content = ""

                if decrypted_content:
                    # 截取前500字符用于预览
                    preview_content = decrypted_content[:500]
                    if len(decrypted_content) > 500:
                        preview_content += (
                            "\n\n[dim]（内容过长，仅显示前500字符）[/dim]"
                        )
                    display_content += f"[bold green]解密后的内容预览:[/bold green]\n{preview_content}\n\n"
                elif original_content:
                    display_content += f"[bold yellow]原始加密内容:[/bold yellow]\n{original_content[:200]}...\n\n"
                else:
                    display_content += "[bold red]未找到文章内容[/bold red]\n\n"

                self.console.print(
                    Panel(display_content, title="文章详情", expand=False)
                )

                # 显示评论
                self._show_comments(social_service, post_id)

                # 提供操作选项
                while True:
                    self.console.print("\n[bold cyan]操作选项:[/bold cyan]")
                    self.console.print("[1] 在浏览器中打开重构后的页面")
                    self.console.print("[2] 查看更多评论")
                    self.console.print("[3] 发表评论")  # 新增选项
                    self.console.print("[b] 返回文章列表")

                    choice = self.input_handler.get_simple_choice(
                        "请选择操作", ["1", "2", "3", "b"], "b"
                    )

                    if choice == "1":
                        try:
                            self.console.print(
                                Panel("正在重构HTML并打开浏览器...", title="请稍候")
                            )
                            # 确保传递完整的note_data，其中包含图片URL信息
                            temp_file_path = social_service.open_note_in_browser(
                                note_data, use_restructured=use_restructured
                            )
                            self.console.print(
                                "[green]✅ 已在浏览器中打开重构后的页面[/green]"
                            )
                            time.sleep(2)
                        except Exception as e:
                            self.console.print(
                                f"[red]❌ 在浏览器中打开页面时出错: {str(e)}[/red]"
                            )
                            time.sleep(2)
                    elif choice == "2":
                        # 显示完整的评论列表（可分页）
                        self._show_all_comments(social_service, post_id)
                    elif choice == "3":
                        # 发表评论
                        self._post_new_comment(social_service, post_id, note_data.get('title', '未知标题'))
                    elif choice == "b":
                        break
            else:
                self.console.print(Panel("获取文章详情失败", title="错误", style="red"))
                self.input_handler.wait_for_continue()

        except Exception as e:
            logger.exception("获取文章详情时发生异常: %s", str(e))
            self.console.print(
                Panel(f"获取文章详情时发生异常: {str(e)}", title="错误", style="red")
            )
            self.input_handler.wait_for_continue()
        finally:
            # 清理临时文件
            if temp_file_path and os.path.exists(temp_file_path):
                try:
                    os.unlink(temp_file_path)
                except Exception as e:
                    logger.warning(f"删除临时文件失败: {e}")

    def _show_comments(self, social_service, post_id: str, limit: int = 5) -> None:
        """显示文章评论（限制数量）"""
        try:
            self.console.print(Panel("正在加载评论...", title="请稍候"))

            # 获取评论数据
            response = social_service.get_comments(post_id, page="0")

            if response.get("res") == 0:
                comments = response.get("comments", [])

                if comments:
                    self.console.print(
                        f"\n[bold cyan]评论 (显示前{min(limit, len(comments))}条):[/bold cyan]"
                    )

                    # 创建评论表格
                    comment_table = Table(box=box.ROUNDED, show_header=True)
                    comment_table.add_column("用户", style="cyan", width=15)
                    comment_table.add_column("评论内容", style="white", width=60)
                    comment_table.add_column("点赞", style="yellow", width=6)
                    comment_table.add_column("回复", style="green", width=6)

                    for i, comment in enumerate(comments[:limit]):
                        username = comment.get("nick_name", "未知用户")
                        content = comment.get("content", "")[:60]
                        if len(comment.get("content", "")) > 60:
                            content += "..."
                        likes = str(comment.get("zan_ct", 0))
                        replies = str(comment.get("comment_ct", 0))

                        comment_table.add_row(username, content, likes, replies)

                    self.console.print(comment_table)

                    if len(comments) > limit:
                        self.console.print(
                            f"[dim]还有 {len(comments) - limit} 条评论，选择操作'2'查看更多[/dim]"
                        )
                else:
                    self.console.print("\n[dim]暂无评论[/dim]")
            else:
                self.console.print("\n[red]获取评论失败[/red]")

        except Exception as e:
            logger.exception("获取评论时发生异常: %s", str(e))
            self.console.print(f"\n[red]获取评论时出错: {str(e)}[/red]")

    def _show_all_comments(self, social_service, post_id: str) -> None:
        """显示所有评论（分页）"""
        try:
            from core.pagination_manager import (
                CommentDataProvider,
                UnifiedPaginationManager,
                PaginationConfig,
            )
            from core.ui_components import CommentInputHandler

            # 创建评论数据提供者
            comment_provider = CommentDataProvider(social_service, post_id)

            # 创建分页管理器
            pagination_manager = UnifiedPaginationManager(
                self.console, self.input_handler
            )
            
            # 创建评论输入处理器
            comment_input = CommentInputHandler(self.console)

            # 显示分页评论，支持方向键操作和查看回复
            config = PaginationConfig(
                page_size=6
            )  # 减少每页显示数量，防止内容过长时截断
            
            while True:
                result = pagination_manager.show_table_based_pagination(
                    title=f"文章评论", data_provider=comment_provider, config=config
                )
                
                # 如果返回了刷新指令，则重新显示评论列表
                if result == "refresh":
                    continue
                else:
                    # 其他情况退出循环
                    break

        except Exception as e:
            logger.exception("显示所有评论时发生异常: %s", str(e))
            self.console.print(
                Panel(f"显示评论时出错: {str(e)}", title="错误", style="red")
            )
            self.input_handler.wait_for_continue()

    def show_feature_coming_soon(self, feature_name: str) -> str:
        """显示功能开发中提示"""
        self.console.clear()
        self.console.print(
            Panel(f"{feature_name}功能正在开发中...", title="敬请期待", style="yellow")
        )
        time.sleep(2)
        return "back"

    def show_env_error(self) -> None:
        """显示环境变量错误提示"""
        self.console.clear()
        panel_content = (
            "错误：缺少登录凭证\n\n"
            "请在项目根目录创建 .env 文件，内容如下：\n\n"
            "LOGIN_PHONE=您的手机号\n"
            "LOGIN_PASSWORD=您的密码\n\n"
            "创建完成后重新运行程序。"
        )
        self.console.print(Panel(panel_content, title="环境变量配置", style="red"))
        self.input_handler.wait_for_continue("按回车键退出")

    def run(self) -> None:
        """运行TUI主循环"""
        # 检查环境变量
        phone = settings.LOGIN_PHONE
        password = settings.LOGIN_PASSWORD

        if not phone or not password:
            self.show_env_error()
            return

        self.console.clear()

        # 主菜单项和动作
        main_menu_items = [
            "[1] 查看用户信息",
            "[2] 刷题功能 (待实现)",
            "[3] 试卷管理 (待实现)",
            "[4] 社交功能",
            "[q] 退出程序",
        ]

        main_actions = [
            self._handle_user_info,
            lambda: self.show_feature_coming_soon("刷题"),
            lambda: self.show_feature_coming_soon("试卷管理"),
            self.show_social_menu,
            lambda: "quit",
        ]

        while self.running:
            result = self.directional_input.show_live_menu(
                title="刷题社交客户端",
                menu_items=main_menu_items,
                item_actions=main_actions,
            )

            if result == "quit":
                self.running = False

        # 显示退出信息
        self.console.print(Panel("感谢使用，再见！", style="green"))

    def _handle_user_info(self) -> str:
        """处理用户信息查看"""
        user = self.show_user_info()
        if user:
            self.input_handler.wait_for_continue("按回车键返回主菜单")
        return "back"
    
    def show_user_articles_menu(self) -> str:
        """显示用户文章菜单，允许用户输入用户ID或使用当前用户ID"""
        self.console.clear()
        self.console.print(Panel("查看用户文章", title="社交功能", style="cyan"))
        
        # 提示用户输入用户ID
        home_id = self.input_handler.get_text_input(
            "请输入用户ID（直接回车使用当前登录用户）",
            default=""
        )
        
        # 如果用户直接回车，使用当前登录用户的ID
        if not home_id.strip():
            try:
                # 获取当前用户信息
                from services.user_service import UserService
                user_service = UserService(self.client)
                user = user_service.get_user_info()
                if user and user.uid:
                    home_id = str(user.uid)
                    self.console.print(f"使用当前用户ID: {home_id}")
                else:
                    self.console.print(Panel("获取当前用户信息失败", title="错误", style="red"))
                    self.input_handler.wait_for_continue()
                    return "back"
            except Exception as e:
                logger.exception("获取当前用户信息失败: %s", str(e))
                self.console.print(Panel(f"获取用户信息失败: {str(e)}", title="错误", style="red"))
                self.input_handler.wait_for_continue()
                return "back"
        
        # 展示用户文章列表
        return self.show_user_posts(home_id)
    
    def show_user_posts(self, home_id: str) -> str:
        """显示用户文章列表"""
        try:
            social_service = SocialService(self.client)
            
            # 同时获取用户信息
            user_data = None
            try:
                user_info_response = social_service.get_user_data(home_id)
                if user_info_response.get('res') == 0:
                    user_data = user_info_response
                    logger.debug(f"成功获取用户信息: {user_data.get('info', {}).get('nick_name', 'N/A')}")
            except Exception as e:
                logger.warning(f"获取用户信息失败: {str(e)}, 继续显示文章列表")
            
            # 存储原始文章数据，用于传递给详情页面
            posts_cache = {}
            
            # 数据加载函数
            def load_user_posts_data(page: int) -> tuple[list, bool]:
                try:
                    response = social_service.get_user_notes(
                        home_id=home_id,
                        page=str(page)
                    )
                    
                    if response.get("res") == 0:
                        notes = response.get("notes", [])
                        has_more = not response.get("over", False) and len(notes) > 0
                        
                        # 转换为表格数据格式，同时缓存原始数据
                        table_data = []
                        for i, note in enumerate(notes):
                            # 生成唯一键用于缓存
                            cache_key = f"{page}_{i}"
                            posts_cache[cache_key] = note
                            
                            # 处理时间戳转换
                            created_at = note.get("created_at", "")
                            if created_at and created_at.isdigit():
                                try:
                                    # 转换时间戳为可读日期
                                    timestamp = int(created_at)
                                    date_str = datetime.datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d')
                                except (ValueError, OSError):
                                    date_str = "未知时间"
                            else:
                                date_str = "未知时间"
                            
                            # 格式化文章数据作为表格行
                            row = [
                                note.get("title", "无标题")[:40]
                                + ("..." if len(note.get("title", "")) > 40 else ""),
                                date_str,  # 使用转换后的日期
                                str(note.get("like_count", 0)),
                                cache_key,  # 添加缓存键作为隐藏列
                            ]
                            table_data.append(row)
                        
                        return table_data, has_more
                    else:
                        error_msg = response.get("msg", "未知错误")
                        raise Exception(f"API返回错误: {error_msg}")
                        
                except Exception as e:
                    raise Exception(f"加载用户文章失败: {str(e)}")
            
            # 项目选择处理函数
            def handle_user_post_selection(index: int, row_data: list) -> Optional[str]:
                try:
                    # 从缓存中获取原始文章数据
                    cache_key = row_data[3]  # 缓存键在第4列
                    if cache_key in posts_cache:
                        note_data = posts_cache[cache_key]
                        note_id = note_data.get("id")
                        if note_id:
                            # 调用文章详情页面
                            self.show_post_detail(note_id, note_data)
                            return None
                    
                    # 如果无法获取文章ID，显示错误
                    self.console.print(
                        Panel("无法获取文章详情", title="错误", style="red")
                    )
                    self.input_handler.wait_for_continue()
                    return None
                except Exception as e:
                    logger.exception("处理用户文章选择时出错: %s", str(e))
                    self.console.print(
                        Panel(f"处理选择时出错: {str(e)}", title="错误", style="red")
                    )
                    self.input_handler.wait_for_continue()
                    return None
            
            # 显示分页表格
            columns = ["标题", "发布时间", "点赞"]
            hint_text = "↑↓:导航 | Enter:查看详情 | Space/n:下一页 | p:上一页 | b:返回"
            
            # 构建用户信息标题
            user_info_title = self._format_user_info_title(user_data, home_id)
            
            result = self.directional_input.show_paginated_table(
                title=user_info_title,
                columns=columns,
                data_loader=load_user_posts_data,
                item_handler=handle_user_post_selection,
                hint_text=hint_text,
            )
            
            return result or "back"
            
        except Exception as e:
            logger.exception("显示用户文章列表失败: %s", str(e))
            self.console.print(
                Panel(f"显示用户文章列表失败: {str(e)}", title="错误", style="red")
            )
            self.input_handler.wait_for_continue()
            return "back"
    
    def _format_user_info_title(self, user_data: Optional[dict], home_id: str) -> str:
        """格式化用户信息标题"""
        if not user_data or user_data.get('res') != 0:
            return f"用户文章 (用户ID: {home_id})"
        
        # 提取用户基本信息
        info = user_data.get('info', {})
        nick_name = info.get('nick_name', '未知用户')
        
        # 处理性别显示
        sex = info.get('sex', '0')
        sex_display = ""
        if sex == '1':
            sex_display = " ♂️"  # 男性符号
        elif sex == '2':
            sex_display = " ♀️"  # 女性符号
        
        # 省份信息
        province = info.get('province', '')
        province_display = f" | {province}" if province else ""
        
        # 统计信息
        follow_count = user_data.get('follow_count', '0')
        fans_count = user_data.get('fans_count', '0')
        answer_count = user_data.get('answer_question_count', '0')
        
        # 构建标题
        title = f"{nick_name}{sex_display}{province_display} - 关注{follow_count} 粉丝{fans_count} 答题{answer_count}"
        
        return title
    
    def _post_new_comment(self, social_service, note_id: str, note_title: Optional[str] = None) -> None:
        """发表新评论"""
        try:
            from core.ui_components import CommentInputHandler
            
            # 创建评论输入处理器
            comment_input = CommentInputHandler(self.console)
            
            # 显示评论输入界面
            content = comment_input.show_comment_input(
                note_id=note_id,
                parent_comment_id="0",  # 外层评论
                note_title=note_title
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
                else:
                    # 显示失败结果
                    comment_input.show_comment_result(
                        success=False,
                        message=f"服务器返回错误码: {response.get('res')}",
                        is_reply=False
                    )
                    
        except Exception as e:
            logger.exception(f"发表评论时发生异常: {str(e)}")
            self.console.print(
                Panel(f"发表评论时出错: {str(e)}", title="错误", style="red")
            )
            self.input_handler.wait_for_continue()
