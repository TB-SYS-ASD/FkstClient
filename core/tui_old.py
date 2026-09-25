"""
TUI (Text-based User Interface) for fkst-client
基于Rich库的现代化文本用户界面
"""

import time
from typing import Optional
import sys
import os

from rich.console import Console
from rich.layout import Layout
from rich.panel import Panel
from rich.table import Table
from rich.prompt import Prompt
from rich.text import Text
from rich import box
from rich.live import Live
from rich.align import Align

from models.user import User
from services.user_service import UserService
from services.social_service import SocialService
from utils.logger import setup_logger
from config.settings import settings
from core.category_param_manager import CategoryParamManager

logger = setup_logger(__name__)

class TUI:
    """文本用户界面主类"""
    
    def __init__(self, client):
        self.client = client
        logger.debug("TUI初始化，客户端参数: %s", self.client.param_manager.dynamic_params)
        self.console = Console()
        self.layout = Layout()
        self.running = True
        self.selected_menu_item = 0  # 当前选中的菜单项
        
        # 初始化分区参数管理器
        self.category_param_manager = CategoryParamManager()
        
        # 菜单项列表
        self.menu_items = [
            "[1] 查看用户信息",
            "[2] 刷题功能 (待实现)",
            "[3] 试卷管理 (待实现)",
            "[4] 社交功能",
            "[q] 退出程序"
        ]
        
        # 社交功能二级菜单项
        self.social_menu_items = [
            "[1] 查看发现文章",
            "[2] 查看关注用户文章",
            "[3] 私信功能 (待实现)",
            "[b] 返回主菜单"
        ]
        self.selected_social_menu_item = 0
        
    def create_layout(self) -> Layout:
        """创建主布局"""
        layout = Layout()
        layout.split_column(
            Layout(name="header", size=3),
            Layout(name="main", ratio=1),
            Layout(name="footer", size=3)
        )
        return layout
        
    def create_header(self, title="刷题社交客户端") -> Panel:
        """创建头部面板"""
        header_text = Text(title, style="bold blue", justify="center")
        return Panel(header_text, box=box.DOUBLE)
        
    def create_footer(self, text="q: 退出 | ↑↓: 导航 | Enter: 选择") -> Panel:
        """创建底部面板"""
        footer_text = Text(text, style="italic", justify="center")
        return Panel(footer_text, box=box.SIMPLE)
        
    def create_menu(self, menu_items, selected_item) -> Table:
        """创建通用菜单表格"""
        table = Table(show_header=False, box=box.SIMPLE, expand=True)
        table.add_column("Menu", style="cyan", no_wrap=True)
        
        # 显示菜单项，高亮当前选中项
        for i, item in enumerate(menu_items):
            if i == selected_item:
                table.add_row(f"-> {item}", style="white on blue")
            else:
                table.add_row(f"   {item}")
            
        return table
        
    def show_user_info(self) -> Optional[User]:
        """显示用户信息界面"""
        self.console.clear()
        self.console.print(Panel("获取用户信息中...", title="请稍候"))
        
        try:
            user_service = UserService(self.client)
            user = user_service.get_user_info()
            
            if user:
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
            self.console.print(Panel(f"获取用户信息时发生异常: {str(e)}", title="错误", style="red"))
            return None
            
    def show_social_menu(self):
        """显示社交功能二级菜单"""
        self.selected_social_menu_item = 0  # 重置选中项
        
        while True:
            # 显示社交功能菜单
            layout = self.create_layout()
            layout["header"].update(self.create_header("社交功能"))
            layout["main"].update(Align.center(self.create_menu(self.social_menu_items, self.selected_social_menu_item), vertical="middle"))
            layout["footer"].update(self.create_footer())
            
            with Live(layout, auto_refresh=False, console=self.console) as live:
                while True:
                    # 更新菜单显示
                    layout["main"].update(Align.center(self.create_menu(self.social_menu_items, self.selected_social_menu_item), vertical="middle"))
                    live.refresh()
                    
                    # 等待用户输入
                    user_input = self.get_user_input()
                    
                    if user_input is None:
                        continue
                        
                    if user_input == "up":
                        # 上箭头键，向上移动选择
                        self.selected_social_menu_item = (self.selected_social_menu_item - 1) % len(self.social_menu_items)
                    elif user_input == "down":
                        # 下箭头键，向下移动选择
                        self.selected_social_menu_item = (self.selected_social_menu_item + 1) % len(self.social_menu_items)
                    elif user_input == "enter":
                        # 回车键，执行选中项
                        choice = str(self.selected_social_menu_item + 1) if self.selected_social_menu_item < 2 else "b"
                        break  # 退出Live循环以执行选中的功能
                    elif user_input and user_input.lower() in ["1", "2"]:
                        # 直接输入数字选择
                        choice = user_input
                        self.selected_social_menu_item = int(choice) - 1
                        break  # 退出Live循环以执行选中的功能
                    elif user_input == "b" or user_input == "quit":
                        choice = "b"
                        break  # 退出Live循环以返回主菜单
            
            # 处理选择
            if choice == "1":
                self.show_social_category_menu()  # 显示文章分区选择菜单
            elif choice == "2":
                # 使用特殊类型"f"表示关注用户文章
                self.show_social_posts("f")  # 显示关注用户文章
            elif choice == "3":
                self.show_feature_coming_soon("私信功能")  # 私信功能待实现
            elif choice == "b":
                return  # 返回主菜单
                
    def show_social_category_menu(self):
        """显示社交文章分区选择菜单"""
       
        # 文章分区选项，使用CategoryParamManager管理参数
        social_service = SocialService(self.client)
        categories = social_service.get_social_categories()
        
        # !设置每个分区的参数到CategoryParamManager
        for category in categories:
            category_type = category["type"]
            category_params = category.get("params", {})
            if category_params:  # 只有当有参数时才设置
                self.category_param_manager.set_category_params(category_type, category_params)
        
        selected_category = 0
        
        # 创建分区选项列表
        category_menu_items = [f"[{i+1}] {category['name']}" for i, category in enumerate(categories)]
        category_menu_items.append("[b] 返回上级菜单")
        
        # 创建分区选择菜单表格
        def create_category_table(selected_index):
            table = Table(title="选择文章分区", box=box.ROUNDED)
            table.add_column("选项", style="cyan", no_wrap=True)
            table.add_column("分区", style="magenta")
            
            for i, category in enumerate(categories):
                if i == selected_index:
                    table.add_row(f"-> {i+1}", category["name"], style="white on blue")
                else:
                    table.add_row(f"   {i+1}", category["name"])
            
            if selected_index == len(categories):
                table.add_row("-> b", "返回上级菜单", style="white on blue")
            else:
                table.add_row("   b", "返回上级菜单")
            return table
        
        with Live(create_category_table(selected_category), auto_refresh=False, console=self.console) as live:
            while True:
                # 更新菜单显示
                live.update(create_category_table(selected_category))
                live.refresh()
                
                # 等待用户输入
                user_input = self.get_user_input()
                
                if user_input is None:
                    continue
                    
                if user_input == "up":
                    # 上箭头键，向上移动选择
                    selected_category = (selected_category - 1) % (len(categories) + 1)
                elif user_input == "down":
                    # 下箭头键，向下移动选择
                    selected_category = (selected_category + 1) % (len(categories) + 1)
                elif user_input == "enter":
                    # 回车键，执行选中项
                    if selected_category < len(categories):
                        category_type = categories[selected_category]["type"]
                        # 清理Live界面后显示文章列表
                        live.stop()
                        self.console.clear()
                        self.show_social_posts(category_type)
                        return  # 退出函数
                    elif selected_category == len(categories):
                        # 返回上级菜单
                        live.stop()
                        return
                elif user_input == "b" or user_input == "quit":
                    # 返回上级菜单
                    live.stop()
                    return  # 返回上级菜单
                
    def show_social_posts(self, category_type="10"):
        """显示社交文章列表"""
        
        # 判断是否是关注用户文章（特殊类型"f"）
        is_follow_user_posts = category_type == "f"
        
        page = 0  # 从0开始的页码
        all_notes = []  # 存储所有获取到的文章
        has_more = True  # 是否还有更多文章
        refresh_page = True  # 是否需要重新加载页面
        selected_post_index = -1  # 当前选中的文章索引，-1表示未选中
        page_size = 10  # 初始页面大小，后续会根据实际返回数量调整
        min_time = str(int(time.time()))  # 用于关注用户文章分页的时间戳
        
        if not is_follow_user_posts:
            # 根据type值确定分区名称（从categories数组动态生成）
            social_service = SocialService(self.client)
            categories = social_service.get_social_categories()
            
            # 从categories数组动态生成category_names字典
            category_names = {category["type"]: category["name"] for category in categories}
            category_name = category_names.get(category_type, "未知")
        else:
            category_name = "关注用户"
        
        def create_posts_table(current_page_notes, selected_post_index, category_name, page, has_more):
            """创建文章列表表格"""
            # 创建文章列表表格（删除序号列）
            table = Table(title=f"{category_name}文章 - 第 {page + 1} 页", box=box.ROUNDED)
            table.add_column("标题", style="magenta")
            table.add_column("作者", style="green")
            table.add_column("点赞", style="yellow")
            
            # 显示当前页的文章
            for i, note in enumerate(current_page_notes):
                # 检查是否是当前选中的文章
                if selected_post_index == i:
                    style = "white on blue"  # 高亮选中的文章
                else:
                    style = None
                    
                title = note.get('title', '无标题')
                author = note.get('nick_name', '未知作者')
                likes = str(note.get('like_count', 0))
                table.add_row(title, author, likes, style=style)
            
            # 添加翻页提示
            table.add_row("", "", "", "")
            if has_more:
                table.add_row("", "按Enter键加载更多...", "", "")
            else:
                table.add_row("", "已加载全部文章", "", "")
            table.add_row("", "按'b'返回分区选择", "", "")
            
            return table
        
        # 创建一个包含提示信息和文章列表的布局
        def create_social_layout(posts_table):
            """创建包含提示信息和文章列表的布局"""
            layout = Layout()
            layout.split_column(
                Layout(Panel("[italic]使用方向键导航，按回车键选择文章或加载更多[/italic]", style="blue", border_style="blue"), name="hint", size=3),
                Layout(posts_table, name="posts")
            )
            return layout
        
        with Live(create_social_layout(create_posts_table([], -1, category_name, 0, True)), auto_refresh=False, console=self.console) as live:
            current_page_notes = []  # 当前页的文章列表
            while has_more:                
                # 只有当需要刷新页面时才重新获取数据
                if refresh_page:
                    # 注意：这里不再清屏，而是通过Live组件来更新界面
                    # self.console.clear()  # 只在加载新数据时清屏
                    # self.console.print(Panel(f"正在获取第 {page + 1} 页{category_name}文章...", title="请稍候"))
                    
                    # 在Live组件中显示加载提示
                    loading_panel = Panel(f"正在获取第 {page + 1} 页{category_name}文章...", title="请稍候")
                    live.update(create_social_layout(loading_panel))
                    live.refresh()
                    
                    try:
                        # 使用已有的APIClient实例而不是创建新的
                        social_service = SocialService(self.client)
                        
                        if is_follow_user_posts:
                            # 获取关注用户文章
                            response = social_service.get_follow_user_notes(min_time=min_time, page=str(page))
                        else:
                            # 使用CategoryParamManager构建请求参数
                            base_params = {"page": str(page), "type": category_type}
                            # 确保每次请求都使用当前时间戳
                            base_params["start_time"] = str(int(time.time()))
                            params = self.category_param_manager.build_request_params(category_type, base_params)
                            
                            # 获取当前页文章，page参数从0开始
                            response = social_service.get_posts(**params)
                        
                        if response.get('res') == 0:
                            notes = response.get('notes', [])
                            # 将新获取的文章添加到总列表中
                            all_notes.extend(notes)
                            
                            # 更新当前页的文章列表
                            current_page_notes = notes
                            
                            logger.debug(f"获取到 {len(notes)} 篇文章")
                            logger.debug(f"获取到的文章IDs: {[note.get('id', 'N/A') for note in notes]}")
                            
                            if is_follow_user_posts:
                                # 根据实际返回的文章数量调整page_size（关注用户文章）
                                if notes:
                                    old_page_size = page_size
                                    page_size = len(notes)
                                    logger.debug(f"关注用户文章: 根据当前页文章数量设置page_size从 {old_page_size} 更新为: {page_size}")
                                    
                                    # 更新min_time为当前页最后一篇文章的时间，用于下一页请求
                                    if notes:
                                        last_note = notes[-1]
                                        # 尝试从不同的字段获取时间戳
                                        note_time = last_note.get('created_at') or last_note.get('updated_at') or min_time
                                        if isinstance(note_time, str) and note_time.isdigit():
                                            min_time = note_time
                                        elif isinstance(note_time, (int, float)):
                                            min_time = str(int(note_time))
                            else:
                                # 根据实际返回的文章数量调整page_size（普通文章分类）
                                if notes:
                                    old_page_size = page_size
                                    page_size = len(notes)
                                    logger.debug(f"普通文章: 根据当前页文章数量设置page_size从 {old_page_size} 更新为: {page_size}")
                            
                            # 检查是否还有更多文章
                            has_more = not response.get('over', False) and (len(notes) > 0)  # 如果返回over:true或没有新文章表示到底了
                            logger.debug(f"是否还有更多文章: {has_more}, response.over: {response.get('over', 'N/A')}")
                            
                            if not notes and page == 0:
                                # 使用Live组件显示提示而不是直接打印
                                empty_panel = Panel("暂无文章", title="提示")
                                live.update(create_social_layout(empty_panel))
                                live.refresh()
                                Prompt.ask("按回车键返回")
                                return
                            
                        else:
                            # 使用Live组件显示错误提示
                            error_panel = Panel("获取文章列表失败", title="错误", style="red")
                            live.update(create_social_layout(error_panel))
                            live.refresh()
                            Prompt.ask("按回车键返回")
                            return
                            
                    except Exception as e:
                        logger.exception("获取文章列表时发生异常: %s", str(e))
                        # 使用Live组件显示异常提示
                        error_panel = Panel(f"获取文章列表时发生异常: {str(e)}", title="错误", style="red")
                        live.update(create_social_layout(error_panel))
                        live.refresh()
                        Prompt.ask("按回车键返回")
                        return
                    
                    # 日志记录当前页信息
                    logger.debug(f"重新计算文章范围: page={page}, page_size={page_size}")
                    logger.debug(f"当前页文章数量: {len(current_page_notes)}, 已加载文章总数: {len(all_notes)}")
                
                # 显示当前页的文章列表
                if is_follow_user_posts:
                    logger.debug(f"准备显示关注用户文章列表: 当前页={page}, 已加载文章总数={len(all_notes)}")
                else:
                    logger.debug(f"准备显示普通文章列表: 当前页={page}, 已加载文章总数={len(all_notes)}")
                posts_table = create_posts_table(current_page_notes, selected_post_index, 
                                               category_name, page, has_more)
                live.update(create_social_layout(posts_table))
                live.refresh()
                
                # 等待用户输入
                user_input = self.get_user_input()
                
                if user_input is None:
                    continue
                    
                if user_input == "up" and current_page_notes:
                    # 上箭头键，向上移动选择
                    if selected_post_index == -1:
                        # 如果未选中任何文章，则选中第一篇
                        selected_post_index = 0
                    else:
                        selected_post_index = max(0, selected_post_index - 1)
                    refresh_page = False  # 方向键操作不需要重新加载页面
                    # 不使用continue，而是继续循环更新界面
                elif user_input == "down" and current_page_notes:
                    # 下箭头键，向下移动选择
                    if selected_post_index == -1:
                        # 如果未选中任何文章，则选中第一篇
                        selected_post_index = 0
                    elif selected_post_index >= len(current_page_notes) - 1:
                        # 如果选中的文章是当前页的最后一篇
                        if has_more:
                            # 如果还有更多文章，加载下一页
                            page += 1
                            refresh_page = True
                            selected_post_index = -1  # 取消选中
                            logger.debug(f"加载下一页: page增加到 {page}")
                            continue
                        else:
                            # 如果没有更多文章，保持在最后一篇文章
                            selected_post_index = len(current_page_notes) - 1
                    else:
                        selected_post_index = min(len(current_page_notes) - 1, selected_post_index + 1)
                    refresh_page = False  # 方向键操作不需要重新加载页面
                    # 不使用continue，而是继续循环更新界面
                elif user_input == "enter":
                    # 回车键行为取决于当前状态
                    if selected_post_index != -1 and len(all_notes) > 0:
                        # 确定当前选中的文章索引（在所有文章中的位置）
                        current_selected_index = (page * page_size) + selected_post_index
                        if current_selected_index < len(all_notes):
                            # 如果已选中文章，则显示文章详情
                            selected_note = all_notes[current_selected_index]
                            note_id = selected_note.get('id')
                            logger.debug(f"选择文章详情 - ID: {note_id}, 数据: {selected_note}")
                            if note_id:
                                live.stop()  # 停止Live组件
                                self.show_post_detail(note_id, selected_note)
                                # 重新显示文章列表
                                # self.console.clear()
                                refresh_page = False  # 查看详情后不需要重新加载页面
                                # 重新启动Live组件
                                live.start()
                                # 重新打印提示信息
                                # self.console.print("[italic]使用方向键导航，按回车键选择文章或加载更多[/italic]")
                                continue
                    elif has_more:
                        # 如果未选中文章且还有更多文章，则加载下一页
                        page += 1
                        refresh_page = True
                        selected_post_index = -1  # 取消选中
                        logger.debug(f"Enter键加载下一页: page增加到 {page}")
                        continue
                elif user_input == "b":
                    # 返回分区选择菜单
                    live.stop()
                    return
                else:
                    # 对于其他按键输入，保持当前状态
                    refresh_page = False  # 不需要重新加载页面，保持当前状态
                    continue  # 继续循环，不执行后面的逻辑
                    
            # 重置刷新标志，但不在加载下一页时重置
            if refresh_page != True:
                refresh_page = False

    def show_post_detail(self, post_id, post_data=None):
        """显示文章详情"""
        self.console.clear()
        self.console.print(Panel("正在获取文章详情...", title="请稍候"))
        logger.debug(f"显示文章详情 - ID: {post_id}, 接收到的post_data: {post_data}")
        
        temp_file_path = None  # 用于存储临时文件路径
        
        try:
            # 使用已有的APIClient实例而不是创建新的
            social_service = SocialService(self.client)
            
            # 强制使用HTML重构功能，改善显示效果
            use_restructured = True
            
            # 直接获取笔记数据，强制重构HTML
            # 图片URL处理逻辑已移至服务层，不再需要在TUI层处理
            response = social_service.get_note(
                post_id, 
                replace_content=True,
                restructure_html=use_restructured,
                post_data=post_data
            )
            
            if response.get('res') == 0:
                # 处理文章详情数据
                note_data = response.get('data', {})
                logger.debug(f"从API获取的文章详情数据: {note_data.keys()}")
                
                # 获取解密和原始内容
                decrypted_content = note_data.get('decrypted_content', '')
                original_content = note_data.get('content', '')
                
                # 构建显示内容
                display_content = ""
                
                if decrypted_content:
                    display_content += f"[bold green]解密后的内容:[/bold green]\n{decrypted_content}\n\n"
                elif original_content:
                    display_content += f"[bold yellow]原始加密内容:[/bold yellow]\n{original_content}\n\n"
                else:
                    display_content += "[bold red]未找到文章内容[/bold red]\n\n"
                
                self.console.print(Panel(display_content, title="文章详情", expand=False))
                
                # 获取并显示评论
                self._show_comments(social_service, post_id)
                
                # 提供在浏览器中打开的选项
                open_in_browser = Prompt.ask("是否在浏览器中打开完整页面？(y/N)").strip().lower()
                if open_in_browser == 'y':
                    try:
                        # 确保传递完整的note_data，其中包含图片URL信息
                        temp_file_path = social_service.open_note_in_browser(
                            note_data, 
                            use_restructured=use_restructured
                        )
                        self.console.print("[green]已在浏览器中打开页面[/green]")
                    except Exception as e:
                        self.console.print(f"[red]在浏览器中打开页面时出错: {str(e)}[/red]")
            else:
                self.console.print(Panel("获取文章详情失败", title="错误", style="red"))
                
        except Exception as e:
            logger.exception("获取文章详情时发生异常: %s", str(e))
            self.console.print(Panel(f"获取文章详情时发生异常: {str(e)}", title="错误", style="red"))
        finally:
            # 等待用户按键返回
            Prompt.ask("按回车键返回")
            
            # 清理临时文件
            if temp_file_path and os.path.exists(temp_file_path):
                try:
                    os.unlink(temp_file_path)
                except Exception as e:
                    logger.warning(f"删除临时文件失败: {e}")
        
    def _show_comments(self, social_service, post_id):
        """显示文章评论"""
        try:
            page = 0
            has_more = True
            selected_comment_index = -1  # 当前选中的评论索引
            current_comments = []  # 当前页的评论列表
            scroll_offset = 0  # 当前滚动偏移量
            
            # 创建一个包含提示信息和评论列表的布局
            def create_comments_layout(comments_table):
                """创建包含提示信息和评论列表的布局"""
                layout = Layout()
                layout.split_column(
                    Layout(Panel("[italic]导航: b=上一页 空格=下一页 ↑↓=选择评论 Enter=查看回复 q=返回文章详情[/italic]", 
                               style="blue", border_style="blue"), name="hint", size=3),
                    Layout(comments_table, name="comments")
                )
                return layout
            
            # 初始化Live组件
            comments_table = Table()
            with Live(create_comments_layout(comments_table), auto_refresh=False, console=self.console) as live:
                while True:
                    # 只有在需要刷新页面时才获取评论
                    if page >= 0:
                        # 在Live组件中显示加载提示
                        loading_panel = Panel(f"正在获取第{page + 1}页评论...", title="请稍候")
                        live.update(create_comments_layout(loading_panel))
                        live.refresh()
                        
                        # 获取当前页评论
                        comments_response = social_service.get_comments(post_id, page=str(page))
                        
                        if comments_response.get('res') == 0:
                            current_comments = comments_response.get('comments', [])
                            
                            # 检查是否还有更多评论
                            has_more = not comments_response.get('over', True)
                            
                            if not current_comments and page == 0:
                                # 使用Live组件显示提示而不是直接打印
                                empty_panel = Panel("暂无评论", title="提示")
                                live.update(create_comments_layout(empty_panel))
                                live.refresh()
                                Prompt.ask("按回车键返回")
                                return
                        
                        # 重置page标志，表示不需要重新加载
                        page = -1
                        scroll_offset = 0  # 重置滚动偏移
                    
                    # 创建评论表格
                    comments_table = self._create_comments_table(current_comments, selected_comment_index, 
                                                               page if page >= 0 else 0, scroll_offset)
                    
                    # 更新Live组件显示
                    live.update(create_comments_layout(comments_table))
                    live.refresh()
                    
                    # 等待用户输入
                    while True:
                        user_input = self.get_user_input()
                        if user_input is None:
                            continue
                        elif user_input == "q" or user_input == "quit":
                            live.stop()
                            return
                        elif user_input == "b" and (page > 0 or page == -1):
                            if page == -1:
                                # 如果是已经加载的页面，回到上一页需要重新加载
                                page = 0  # 回到第一页
                            else:
                                page = max(0, page - 1)
                            selected_comment_index = -1
                            scroll_offset = 0
                            break
                        elif user_input == " " and has_more:  # 使用空格键翻页
                            if page == -1:
                                page = 1  # 从已加载的第0页到第1页
                            else:
                                page += 1
                            selected_comment_index = -1
                            scroll_offset = 0
                            break
                        elif user_input == " " and not has_more:
                            # 使用Live组件显示提示
                            info_panel = Panel("已到最后一页", title="提示", style="yellow")
                            temp_layout = create_comments_layout(info_panel)
                            live.update(temp_layout)
                            live.refresh()
                            time.sleep(1)  # 显示1秒提示
                            # 恢复正常显示
                            comments_table = self._create_comments_table(current_comments, selected_comment_index, 
                                                                       page if page >= 0 else 0, scroll_offset)
                            live.update(create_comments_layout(comments_table))
                            live.refresh()
                            continue
                        elif user_input == "up" and current_comments:
                            # 上箭头键，向上移动选择
                            if selected_comment_index == -1:
                                # 如果未选中任何评论，则选中第一条
                                selected_comment_index = 0
                            else:
                                selected_comment_index = max(0, selected_comment_index - 1)
                            
                            # 检查是否需要向上滚动
                            if selected_comment_index < scroll_offset:
                                scroll_offset = selected_comment_index
                            
                            # 更新界面以显示新的选中状态
                            comments_table = self._create_comments_table(current_comments, selected_comment_index, 
                                                                       page if page >= 0 else 0, scroll_offset)
                            live.update(create_comments_layout(comments_table))
                            live.refresh()
                            continue
                        elif user_input == "down" and current_comments:
                            # 下箭头键，向下移动选择
                            if selected_comment_index == -1:
                                # 如果未选中任何评论，则选中第一条
                                selected_comment_index = 0
                            else:
                                selected_comment_index = min(len(current_comments) - 1, selected_comment_index + 1)
                            
                            # 获取终端高度以确定可见行数
                            terminal_height = self.console.size.height
                            # 估算可见评论行数 (标题3行 + 提示3行 + 表格边框和标题行等)
                            visible_comments = max(1, terminal_height - 10)
                            
                            # 检查是否需要向下滚动
                            if selected_comment_index >= scroll_offset + visible_comments:
                                scroll_offset = selected_comment_index - visible_comments + 1
                            
                            # 更新界面以显示新的选中状态
                            comments_table = self._create_comments_table(current_comments, selected_comment_index, 
                                                                       page if page >= 0 else 0, scroll_offset)
                            live.update(create_comments_layout(comments_table))
                            live.refresh()
                            continue
                        elif user_input == "enter" and selected_comment_index != -1:
                            # 查看选中评论的回复
                            selected_comment = current_comments[selected_comment_index]
                            comment_id = selected_comment.get('id')
                            if comment_id:
                                live.stop()  # 停止Live组件
                                self._show_comment_replies(social_service, comment_id)
                                # 重新启动Live组件
                                live.start()
                                # 重新显示评论列表
                                selected_comment_index = -1
                                # 更新界面
                                comments_table = self._create_comments_table(current_comments, selected_comment_index, 
                                                                           page if page >= 0 else 0, scroll_offset)
                                live.update(create_comments_layout(comments_table))
                                live.refresh()
                                continue
                            else:
                                # 使用Live组件显示错误提示
                                error_panel = Panel("无法获取评论ID", title="错误", style="red")
                                temp_layout = create_comments_layout(error_panel)
                                live.update(temp_layout)
                                live.refresh()
                                time.sleep(1)  # 显示1秒提示
                                # 恢复正常显示
                                comments_table = self._create_comments_table(current_comments, selected_comment_index, 
                                                                           page if page >= 0 else 0, scroll_offset)
                                live.update(create_comments_layout(comments_table))
                                live.refresh()
                        else:
                            continue
        except Exception as e:
            logger.exception("获取评论时发生异常: %s", str(e))
            # 使用Live组件显示异常提示
            error_panel = Panel(f"获取评论时发生异常: {str(e)}", title="错误", style="red")
            self.console.print(error_panel)
            Prompt.ask("按回车键继续")
            return
    
    def _create_comments_table(self, current_comments, selected_comment_index, page, scroll_offset=0):
        """创建评论表格"""
        # 创建评论表格
        comments_table = Table(title=f"评论 (第{page + 1}页)" if page >= 0 else f"评论 (第1页)", box=box.ROUNDED)
        comments_table.add_column("序号", style="green", no_wrap=True)
        comments_table.add_column("用户", style="cyan")
        comments_table.add_column("内容", style="white")
        comments_table.add_column("时间", style="yellow")
        comments_table.add_column("赞/回复", style="magenta")
        
        # 如果没有评论，直接返回空表格
        if not current_comments:
            return comments_table
        
        # 计算终端可视区域大小
        terminal_height = self.console.size.height
        # 估算可见评论行数 (标题3行 + 提示3行 + 表格边框和标题行等)
        # 每条评论可能占用多行，因此我们保守估计每条评论占用2行
        visible_comments = max(1, (terminal_height - 12) // 2)
        
        # 确定要显示的评论范围
        start_index = scroll_offset
        end_index = min(scroll_offset + visible_comments, len(current_comments))
        
        # 添加评论数据
        for i in range(start_index, end_index):
            comment = current_comments[i]
            # 主评论信息
            selector = ">> " if i == selected_comment_index else "   "  # 标记选中的评论
            index = f"{selector}{i+1}"
            user_name = comment.get('nick_name', '未知用户')
            content = comment.get('content', '')
            created_at = comment.get('created_at', '')
            
            # 格式化时间
            if created_at.isdigit():
                import datetime
                created_time = datetime.datetime.fromtimestamp(int(created_at))
                formatted_time = created_time.strftime("%Y-%m-%d %H:%M")
            else:
                formatted_time = created_at
            
            likes = comment.get('zan_ct', '0')
            replies = comment.get('comment_ct', '0')
            like_reply_text = f"👍{likes} 💬{replies}"
            
            comments_table.add_row(index, user_name, content, formatted_time, like_reply_text)
            
            # 添加回复（如果有）
            replies_data = comment.get('replies', [])
            for reply in replies_data[:3]:  # 只显示前3条回复
                reply_user = reply.get('nick_name', '未知用户')
                reply_content = reply.get('content', '')
                reply_time = reply.get('created_at', '')
                
                # 格式化回复时间
                if reply_time.isdigit():
                    import datetime
                    reply_time_obj = datetime.datetime.fromtimestamp(int(reply_time))
                    formatted_reply_time = reply_time_obj.strftime("%Y-%m-%d %H:%M")
                else:
                    formatted_reply_time = reply_time
                
                comments_table.add_row(
                    "",
                    f"  ↳ {reply_user}",
                    reply_content,
                    formatted_reply_time,
                    ""
                )
            
            # 如果还有更多回复，显示提示
            if len(replies_data) > 3:
                comments_table.add_row("", "", f"  [italic]还有{len(replies_data) - 3}条回复...[/italic]", "", "")
        
        # 如果有滚动，显示滚动提示
        if len(current_comments) > visible_comments:
            if scroll_offset > 0:
                comments_table.add_row("", "", f"[italic]↑↑↑ 上面还有 {scroll_offset} 条评论 ↑↑↑[/italic]", "", "")
            if end_index < len(current_comments):
                remaining = len(current_comments) - end_index
                comments_table.add_row("", "", f"[italic]↓↓↓ 下面还有 {remaining} 条评论 ↓↓↓[/italic]", "", "")
        
        return comments_table
    
    def _show_comment_replies(self, social_service, comment_id):
        """显示评论的回复"""
        try:
            page = 0
            has_more = True
            
            # 创建一个包含提示信息和回复列表的布局
            def create_replies_layout(replies_table):
                """创建包含提示信息和回复列表的布局"""
                layout = Layout()
                layout.split_column(
                    Layout(Panel("[italic]导航: b=上一页 Enter/空格=下一页 q=返回评论列表[/italic]", 
                               style="blue", border_style="blue"), name="hint", size=3),
                    Layout(replies_table, name="replies")
                )
                return layout
            
            # 初始化Live组件
            replies_table = Table()
            with Live(create_replies_layout(replies_table), auto_refresh=False, console=self.console) as live:
                while True:
                    # 在Live组件中显示加载提示
                    loading_panel = Panel(f"正在获取评论回复 (第{page + 1}页)...", title="请稍候")
                    live.update(create_replies_layout(loading_panel))
                    live.refresh()
                    
                    # 获取当前评论的回复
                    replies_response = social_service.get_comment_replies(comment_id, page=str(page))
                    
                    if replies_response.get('res') == 0:
                        replies = replies_response.get('comments', [])
                        
                        # 检查是否还有更多回复
                        has_more = not replies_response.get('over', True)
                        
                        if not replies and page == 0:
                            # 使用Live组件显示提示而不是直接打印
                            empty_panel = Panel("暂无回复", title="提示")
                            live.update(create_replies_layout(empty_panel))
                            live.refresh()
                            Prompt.ask("按回车键返回")
                            live.stop()
                            return
                        
                        # 创建回复表格
                        replies_table = Table(title=f"回复 (第{page + 1}页)", box=box.ROUNDED)
                        replies_table.add_column("用户", style="cyan")
                        replies_table.add_column("内容", style="white")
                        replies_table.add_column("时间", style="yellow")
                        replies_table.add_column("赞", style="magenta")
                        
                        # 添加回复数据
                        for reply in replies:
                            user_name = reply.get('nick_name', '未知用户')
                            content = reply.get('content', '')
                            created_at = reply.get('created_at', '')
                            
                            # 格式化时间
                            if created_at.isdigit():
                                import datetime
                                created_time = datetime.datetime.fromtimestamp(int(created_at))
                                formatted_time = created_time.strftime("%Y-%m-%d %H:%M")
                            else:
                                formatted_time = created_at
                            
                            likes = reply.get('zan_ct', '0')
                            
                            replies_table.add_row(user_name, content, formatted_time, f"👍{likes}")
                        
                        # 更新Live组件显示
                        live.update(create_replies_layout(replies_table))
                        live.refresh()
                        
                        # 等待用户输入
                        while True:
                            user_input = self.get_user_input()
                            if user_input is None:
                                continue
                            elif user_input == "q" or user_input == "quit":
                                live.stop()
                                return
                            elif user_input == "b" and page > 0:
                                page -= 1
                                break
                            elif (user_input == "enter" or user_input == " ") and has_more:
                                page += 1
                                break
                            elif (user_input == "enter" or user_input == " ") and not has_more:
                                # 使用Live组件显示提示
                                info_panel = Panel("已到最后一页", title="提示", style="yellow")
                                temp_layout = create_replies_layout(info_panel)
                                live.update(temp_layout)
                                live.refresh()
                                time.sleep(1)  # 显示1秒提示
                                # 恢复正常显示
                                live.update(create_replies_layout(replies_table))
                                live.refresh()
                                continue
                            else:
                                continue
                    else:
                        # 使用Live组件显示错误提示
                        error_panel = Panel("获取回复失败", title="错误", style="red")
                        live.update(create_replies_layout(error_panel))
                        live.refresh()
                        Prompt.ask("按回车键继续")
                        live.stop()
                        return
                        
        except Exception as e:
            logger.exception("获取回复时发生异常: %s", str(e))
            self.console.print(f"[red]获取回复时发生异常: {str(e)}[/red]")
            Prompt.ask("按回车键继续")
            return
        
    def show_feature_coming_soon(self, feature_name: str):
        """显示功能开发中提示"""
        self.console.clear()
        self.console.print(Panel(
            f"{feature_name}功能正在开发中...",
            title="敬请期待",
            style="yellow"
        ))
        time.sleep(2)
        
    def show_env_error(self):
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
        Prompt.ask("按回车键退出")
        
    def get_user_input(self):
        """获取用户键盘输入"""
        try:
            # 使用Rich的Console读取单个字符
            if "win" in sys.platform:
                # Windows系统
                import msvcrt
                
                # 等待按键输入
                char = msvcrt.getch()
                
                # 处理特殊键 (箭头键等)
                if char == b'\xe0':  # 特殊键前缀
                    char = msvcrt.getch()
                    if char == b'H':  # 上箭头
                        return "up"
                    elif char == b'P':  # 下箭头
                        return "down"
                    elif char == b'K':  # 左箭头
                        return "left"
                    elif char == b'M':  # 右箭头
                        return "right"
                elif char == b'\r':  # 回车键
                    return "enter"
                elif char == b'\x03':  # Ctrl+C
                    return "quit"
                elif char.lower() == b'q':
                    return "q"
                else:
                    # 其他字符按键
                    decoded_char = char.decode('utf-8', errors='ignore')
                    return decoded_char
            else:
                # Unix/Linux系统
                try:
                    import termios, tty
                except ImportError:
                    # 如果无法导入Unix特定模块，使用默认输入方法
                    choice = Prompt.ask("请选择功能", choices=["1", "2", "3", "4", "q"], default="1")
                    return choice
                
                fd = sys.stdin.fileno()
                old_settings = termios.tcgetattr(fd)
                try:
                    tty.setraw(sys.stdin.fileno())
                    char = sys.stdin.read(1)
                    if char == '\x1b':  # ESC序列
                        char = sys.stdin.read(2)
                        if char == '[A':  # 上箭头
                            return "up"
                        elif char == '[B':  # 下箭头
                            return "down"
                        elif char == '[D':  # 左箭头
                            return "left"
                        elif char == '[C':  # 右箭头
                            return "right"
                    elif char == '\r' or char == '\n':  # 回车键
                        return "enter"
                    elif char == '\x03':  # Ctrl+C
                        return "quit"
                    elif char.lower() == "q":
                        return "q"
                    else:
                        return char
                finally:
                    termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
        except Exception as e:
            logger.exception("读取键盘输入时发生异常: %s", str(e))
            return None
        
    def run(self):
        """运行TUI主循环"""
        # 检查环境变量
        phone = settings.LOGIN_PHONE
        password = settings.LOGIN_PASSWORD
        
        if not phone or not password:
            self.show_env_error()
            return
            
        self.console.clear()
        self.selected_menu_item = 0  # 重置选中项
        
        while self.running:
            # 使用Live组件实现实时更新主菜单
            layout = self.create_layout()
            layout["header"].update(self.create_header())
            layout["main"].update(Align.center(self.create_menu(self.menu_items, self.selected_menu_item), vertical="middle"))
            layout["footer"].update(self.create_footer())
            
            with Live(layout, auto_refresh=False, console=self.console) as live:
                while self.running:
                    # 更新菜单显示
                    layout["main"].update(Align.center(self.create_menu(self.menu_items, self.selected_menu_item), vertical="middle"))
                    live.refresh()
                    
                    # 等待用户输入
                    user_input = self.get_user_input()
                    
                    if user_input is None:
                        continue
                        
                    if user_input == "up":
                        # 上箭头键，向上移动选择
                        self.selected_menu_item = (self.selected_menu_item - 1) % len(self.menu_items)
                    elif user_input == "down":
                        # 下箭头键，向下移动选择
                        self.selected_menu_item = (self.selected_menu_item + 1) % len(self.menu_items)
                    elif user_input == "enter":
                        # 回车键，执行选中项
                        choice = str(self.selected_menu_item + 1) if self.selected_menu_item < 4 else "q"
                        break  # 退出Live循环以执行选中的功能
                    elif user_input and user_input.lower() in ["1", "2", "3", "4"]:
                        # 直接输入数字选择
                        choice = user_input
                        self.selected_menu_item = int(choice) - 1
                        break  # 退出Live循环以执行选中的功能
                    elif user_input == "q" or user_input == "quit":
                        self.running = False
                        break  # 退出Live循环以退出程序
            
            # 如果程序仍在运行且有选择要执行
            if self.running and 'choice' in locals():
                logger.debug("执行菜单选择: %s，当前客户端参数: %s", choice, self.client.param_manager.dynamic_params)
                if choice == "1":
                    user = self.show_user_info()
                    if user:
                        Prompt.ask("按回车键返回主菜单")
                elif choice == "2":
                    self.show_feature_coming_soon("刷题")
                elif choice == "3":
                    self.show_feature_coming_soon("试卷管理")
                elif choice == "4":
                    self.show_social_menu()  # 调用社交功能二级菜单
                elif choice == "q":
                    self.running = False

        # 显示退出信息
        self.console.print(Panel("感谢使用，再见！", style="green"))
