"""
通用UI组件模块
集中管理TUI中重复的布局创建、菜单显示、表格创建等功能
消除代码重复，提供统一的UI组件接口
"""

import time
from typing import List, Dict, Any, Optional, Callable
from dataclasses import dataclass
from enum import Enum

from rich.console import Console
from rich.layout import Layout
from rich.panel import Panel
from rich.table import Table
from rich.text import Text
from rich import box
from rich.live import Live
from rich.align import Align
from rich.prompt import Prompt, Confirm


class MenuAction(Enum):
    """菜单动作枚举"""

    SELECT = "select"
    BACK = "back"
    QUIT = "quit"
    UP = "up"
    DOWN = "down"
    NEXT_PAGE = "next_page"
    PREV_PAGE = "prev_page"


@dataclass
class MenuItem:
    """菜单项数据结构"""

    key: str
    label: str
    action: Optional[Callable] = None


@dataclass
class TableColumn:
    """表格列定义"""

    name: str
    style: str = "white"
    no_wrap: bool = False


@dataclass
class PaginationInfo:
    """分页信息"""

    current_page: int = 0
    page_size: int = 10
    has_more: bool = True
    total_items: int = 0


class UIComponents:
    """通用UI组件类"""

    def __init__(self, console: Console):
        self.console = console

    def create_layout(self, header_size: int = 3, footer_size: int = 3) -> Layout:
        """创建标准布局"""
        layout = Layout()
        layout.split_column(
            Layout(name="header", size=header_size),
            Layout(name="main", ratio=1),
            Layout(name="footer", size=footer_size),
        )
        return layout

    def create_header(self, title: str, style: str = "bold blue") -> Panel:
        """创建头部面板"""
        header_text = Text(title, style=style, justify="center")
        return Panel(header_text, box=box.DOUBLE)

    def create_footer(self, text: str, style: str = "italic") -> Panel:
        """创建底部面板"""
        footer_text = Text(text, style=style, justify="center")
        return Panel(footer_text, box=box.SIMPLE)

    def create_menu_table(
        self,
        menu_items: List[MenuItem],
        selected_index: int = 0,
        title: Optional[str] = None,
    ) -> Table:
        """创建通用菜单表格"""
        table = Table(show_header=False, box=box.SIMPLE, expand=True)
        if title:
            table.title = title
        table.add_column("Menu", style="cyan", no_wrap=True)

        for i, item in enumerate(menu_items):
            display_text = f"[{item.key}] {item.label}"
            if i == selected_index:
                table.add_row(f"-> {display_text}", style="white on blue")
            else:
                table.add_row(f"   {display_text}")

        return table

    def create_data_table(
        self,
        columns: List[TableColumn],
        data: List[Dict[str, Any]],
        selected_index: int = -1,
        title: Optional[str] = None,
        show_selection: bool = False,
    ) -> Table:
        """创建通用数据表格"""
        table = Table(box=box.ROUNDED, expand=True)
        if title:
            table.title = title

        # 添加选择列（如果需要）
        if show_selection:
            table.add_column("", style="green", no_wrap=True, width=4)

        # 添加数据列
        for column in columns:
            table.add_column(column.name, style=column.style, no_wrap=column.no_wrap)

        # 添加数据行
        for i, row_data in enumerate(data):
            row_values = []

            # 添加选择标记
            if show_selection:
                selector = ">> " if i == selected_index else "   "
                row_values.append(selector)

            # 添加数据值
            for column in columns:
                value = row_data.get(column.name.lower().replace(" ", "_"), "")
                row_values.append(str(value))

            # 应用选中样式
            style = "white on blue" if show_selection and i == selected_index else None
            table.add_row(*row_values, style=style)

        return table

    def create_loading_panel(self, message: str) -> Panel:
        """创建加载提示面板"""
        return Panel(message, title="请稍候", style="blue")

    def create_error_panel(self, message: str) -> Panel:
        """创建错误提示面板"""
        return Panel(message, title="错误", style="red")

    def create_info_panel(self, message: str, title: str = "提示") -> Panel:
        """创建信息提示面板"""
        return Panel(message, title=title, style="yellow")

    def create_success_panel(self, message: str) -> Panel:
        """创建成功提示面板"""
        return Panel(message, title="成功", style="green")

    def create_layout_with_hint(self, main_content: Any, hint_text: str) -> Layout:
        """创建包含提示信息的布局"""
        layout = Layout()
        layout.split_column(
            Layout(
                Panel(
                    f"[italic]{hint_text}[/italic]", style="blue", border_style="blue"
                ),
                name="hint",
                size=3,
            ),
            Layout(main_content, name="main"),
        )
        return layout

    def show_temporary_message(
        self, message: str, duration: float = 1.0, panel_type: str = "info"
    ) -> None:
        """显示临时消息"""
        if panel_type == "error":
            panel = self.create_error_panel(message)
        elif panel_type == "success":
            panel = self.create_success_panel(message)
        else:
            panel = self.create_info_panel(message)

        self.console.print(panel)
        time.sleep(duration)

    def prompt_continue(self, message: str = "按回车键继续") -> None:
        """提示用户按键继续"""
        Prompt.ask(message, default="")

    def confirm_action(self, message: str, default: bool = False) -> bool:
        """确认操作"""
        return Confirm.ask(message, default=default)

    def get_user_choice(
        self, prompt_text: str, choices: List[str], default: Optional[str] = None
    ) -> str:
        """获取用户选择"""
        result = Prompt.ask(prompt_text, choices=choices, default=default)
        return (
            result if result is not None else (default or choices[0] if choices else "")
        )


class NavigationHandler:
    """导航处理器 - 处理键盘输入和菜单导航"""

    def __init__(self):
        self.selected_index = 0
        self.max_index = 0

    def handle_navigation(self, key: str, max_items: int) -> Optional[MenuAction]:
        """处理导航键"""
        self.max_index = max_items - 1

        if key == "up":
            self.selected_index = (self.selected_index - 1) % max_items
            return MenuAction.UP
        elif key == "down":
            self.selected_index = (self.selected_index + 1) % max_items
            return MenuAction.DOWN
        elif key == "enter":
            return MenuAction.SELECT
        elif key == "b":
            return MenuAction.BACK
        elif key == "q":
            return MenuAction.QUIT
        elif key == " ":
            return MenuAction.NEXT_PAGE
        else:
            # 检查是否是数字选择
            if key.isdigit():
                choice_index = int(key) - 1
                if 0 <= choice_index < max_items:
                    self.selected_index = choice_index
                    return MenuAction.SELECT

        return None

    def reset_selection(self):
        """重置选择"""
        self.selected_index = 0


class PaginationHandler:
    """分页处理器 - 统一处理各种列表的分页逻辑"""

    def __init__(self, page_size: int = 10):
        self.pagination_info = PaginationInfo(page_size=page_size)
        self.data_cache = []  # 缓存所有数据

    def load_page_data(self, data_loader: Callable, page: int) -> List[Any]:
        """加载指定页的数据"""
        try:
            response = data_loader(page)
            return response if response else []
        except Exception as e:
            raise Exception(f"加载数据失败: {str(e)}")

    def add_data(self, new_data: List[Any], is_last_page: bool = False):
        """添加新数据到缓存"""
        self.data_cache.extend(new_data)
        self.pagination_info.total_items = len(self.data_cache)
        self.pagination_info.has_more = not is_last_page and len(new_data) > 0

    def get_current_page_data(self) -> List[Any]:
        """获取当前页数据"""
        start_idx = self.pagination_info.current_page * self.pagination_info.page_size
        end_idx = start_idx + self.pagination_info.page_size
        return self.data_cache[start_idx:end_idx]

    def next_page(self):
        """下一页"""
        if self.pagination_info.has_more:
            self.pagination_info.current_page += 1

    def prev_page(self):
        """上一页"""
        if self.pagination_info.current_page > 0:
            self.pagination_info.current_page -= 1

    def reset(self):
        """重置分页信息"""
        self.pagination_info.current_page = 0
        self.pagination_info.has_more = True
        self.data_cache.clear()
        self.pagination_info.total_items = 0


class ListViewManager:
    """列表视图管理器 - 统一管理各种列表的显示和交互"""

    def __init__(self, ui_components: UIComponents):
        self.ui = ui_components
        self.nav_handler = NavigationHandler()
        self.pagination = PaginationHandler()

    def show_list(
        self,
        title: str,
        columns: List[TableColumn],
        data_loader: Callable,
        item_handler: Optional[Callable] = None,
        hint_text: str = "↑↓:导航 Enter:选择 b:返回 q:退出",
    ) -> Any:
        """
        通用列表显示方法

        Args:
            title: 列表标题
            columns: 表格列定义
            data_loader: 数据加载函数，接收页码参数
            item_handler: 选中项处理函数
            hint_text: 提示文本
        """
        self.nav_handler.reset_selection()
        self.pagination.reset()

        # 初始加载第一页数据
        try:
            first_page_data = self.pagination.load_page_data(data_loader, 0)
            if not first_page_data:
                self.ui.show_temporary_message("暂无数据")
                return None

            self.pagination.add_data(first_page_data)
        except Exception as e:
            self.ui.show_temporary_message(
                f"加载数据失败: {str(e)}", panel_type="error"
            )
            return None

        with Live(
            self._create_list_layout(title, columns, hint_text),
            auto_refresh=False,
            console=self.ui.console,
        ) as live:

            while True:
                # 更新显示
                layout = self._create_list_layout(title, columns, hint_text)
                live.update(layout)
                live.refresh()

                # 等待用户输入 (这里暂时使用简化的输入方式)
                user_input = Prompt.ask("", default="").strip().lower()

                action = self.nav_handler.handle_navigation(
                    user_input, len(self.pagination.get_current_page_data())
                )

                if action == MenuAction.SELECT and item_handler:
                    current_data = self.pagination.get_current_page_data()
                    if 0 <= self.nav_handler.selected_index < len(current_data):
                        selected_item = current_data[self.nav_handler.selected_index]
                        result = item_handler(selected_item)
                        if result:  # 如果处理函数返回结果，退出列表
                            return result

                elif action == MenuAction.NEXT_PAGE:
                    if self.pagination.pagination_info.has_more:
                        try:
                            next_page_data = self.pagination.load_page_data(
                                data_loader,
                                self.pagination.pagination_info.current_page + 1,
                            )
                            self.pagination.add_data(next_page_data)
                            self.pagination.next_page()
                            self.nav_handler.reset_selection()
                        except Exception as e:
                            self.ui.show_temporary_message(
                                f"加载下一页失败: {str(e)}", panel_type="error"
                            )
                    else:
                        self.ui.show_temporary_message("已到最后一页")

                elif action == MenuAction.PREV_PAGE:
                    if self.pagination.pagination_info.current_page > 0:
                        self.pagination.prev_page()
                        self.nav_handler.reset_selection()

                elif action == MenuAction.BACK or action == MenuAction.QUIT:
                    return None

    def _create_list_layout(
        self, title: str, columns: List[TableColumn], hint_text: str
    ) -> Layout:
        """创建列表布局"""
        current_data = self.pagination.get_current_page_data()

        # 转换数据格式为表格所需的字典格式
        table_data = []
        for item in current_data:
            if isinstance(item, dict):
                table_data.append(item)
            else:
                # 如果是对象，转换为字典
                table_data.append(item.__dict__ if hasattr(item, "__dict__") else {})

        table = self.ui.create_data_table(
            columns=columns,
            data=table_data,
            selected_index=self.nav_handler.selected_index,
            title=f"{title} (第{self.pagination.pagination_info.current_page + 1}页)",
            show_selection=True,
        )

        return self.ui.create_layout_with_hint(table, hint_text)


class CommentInputHandler:
    """评论输入处理器 - 处理评论内容输入和验证"""
    
    def __init__(self, console: Console):
        self.console = console
        self.ui = UIComponents(console)
    
    def show_comment_input(self, note_id: str, parent_comment_id: str = "0", 
                          reply_to_user: Optional[str] = None, note_title: Optional[str] = None) -> Optional[str]:
        """
        显示评论输入界面
        
        Args:
            note_id: 文章ID
            parent_comment_id: 父级评论ID
            reply_to_user: 被回复用户昵称
            note_title: 文章标题
            
        Returns:
            用户输入的评论内容，取消时返回None
        """
        self.console.clear()
        
        # 判断是评论还是回复
        is_reply = parent_comment_id != "0"
        action_type = "回复" if is_reply else "评论"
        
        # 构建标题
        title_text = f"发表{action_type}"
        if note_title:
            title_text += f" - {note_title[:30]}"
            if len(note_title) > 30:
                title_text += "..."
        
        # 构建提示信息
        hint_parts = []
        if is_reply and reply_to_user:
            hint_parts.append(f"回复 @{reply_to_user}")
        
        hint_parts.extend([
            "请输入评论内容（不超过500字）",
            "输入完成后按回车键继续，输入 'cancel' 取消"
        ])
        
        # 显示界面
        header_panel = self.ui.create_header(title_text)
        self.console.print(header_panel)
        
        for hint in hint_parts:
            self.console.print(f"[dim]{hint}[/dim]")
        
        self.console.print("\n" + "="*60 + "\n")
        
        # 获取用户输入
        try:
            content_lines = []
            self.console.print("[评论内容]")
            
            while True:
                line = Prompt.ask("> ", default="")
                
                # 检查取消命令
                if line.strip().lower() == "cancel":
                    self.console.print("[yellow]取消发表{action_type}[/yellow]")
                    time.sleep(1)
                    return None
                
                # 空行表示输入结束
                if not line.strip():
                    if content_lines:  # 如果已经有内容，结束输入
                        break
                    else:  # 如果还没有内容，继续等待输入
                        continue
                
                content_lines.append(line)
                
                # 检查内容长度
                current_content = "\n".join(content_lines)
                if len(current_content) > 500:
                    self.console.print("[red]评论内容过长，请控制在500字以内[/red]")
                    content_lines.pop()  # 移除最后一行
                    continue
            
            final_content = "\n".join(content_lines).strip()
            
            # 验证内容
            if not final_content:
                self.console.print(f"[red]评论内容不能为空[/red]")
                time.sleep(1)
                return None
            
            # 显示预览并确认
            self.console.print("\n" + "="*60)
            self.console.print(f"[bold cyan]预览{action_type}内容：[/bold cyan]")
            self.console.print(Panel(final_content, border_style="blue"))
            
            if self.ui.confirm_action(f"确认发表此{action_type}？", default=True):
                return final_content
            else:
                self.console.print(f"[yellow]已取消发表{action_type}[/yellow]")
                time.sleep(1)
                return None
                
        except KeyboardInterrupt:
            self.console.print(f"\n[yellow]用户取消发表{action_type}[/yellow]")
            return None
        except Exception as e:
            self.console.print(f"\n[red]输入过程中发生错误： {str(e)}[/red]")
            return None
    
    def show_comment_result(self, success: bool, message: str, is_reply: bool = False) -> None:
        """
        显示评论发布结果
        
        Args:
            success: 是否成功
            message: 结果消息
            is_reply: 是否为回复
        """
        action_type = "回复" if is_reply else "评论"
        
        if success:
            panel = self.ui.create_success_panel(f"{action_type}发布成功！\n{message}")
        else:
            panel = self.ui.create_error_panel(f"{action_type}发布失败：\n{message}")
        
        self.console.print(panel)
        time.sleep(2)  # 显示2秒后自动消失
