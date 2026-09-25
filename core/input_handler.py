"""
改进的键盘输入处理模块
支持方向键控制，保留原有的美观界面体验
提供更稳定、跨平台的输入处理方案
"""

import sys
import time
from typing import List, Optional, Dict, Any, Callable
from enum import Enum

from rich.console import Console
from rich.prompt import Prompt, Confirm, IntPrompt
from rich.text import Text
from rich.panel import Panel
from rich.live import Live
from rich.layout import Layout
from rich.align import Align
from rich.table import Table
from rich import box


class KeyAction(Enum):
    """按键动作枚举"""

    UP = "up"
    DOWN = "down"
    LEFT = "left"
    RIGHT = "right"
    ENTER = "enter"
    SELECT = "select"  # 选择动作（同 ENTER）
    BACK = "back"
    QUIT = "quit"
    SPACE = "space"
    NEXT_PAGE = "next_page"  # 下一页
    PREV_PAGE = "prev_page"  # 上一页
    NUMBER = "number"
    CHAR = "char"
    UNKNOWN = "unknown"


class DirectionalInputHandler:
    """方向键输入处理器 - 支持真正的方向键控制"""

    def __init__(self, console: Console):
        self.console = console

    def get_key_input(self) -> tuple[KeyAction, str]:
        """
        获取单个按键输入，支持方向键

        Returns:
            tuple: (动作类型, 按键值)
        """
        try:
            if "win" in sys.platform:
                # Windows系统
                import msvcrt

                # 等待按键输入
                char = msvcrt.getch()

                # 处理特殊键 (箭头键等)
                if char == b"\xe0":  # 特殊键前缀
                    char = msvcrt.getch()
                    if char == b"H":  # 上箭头
                        return KeyAction.UP, "up"
                    elif char == b"P":  # 下箭头
                        return KeyAction.DOWN, "down"
                    elif char == b"K":  # 左箭头
                        return KeyAction.LEFT, "left"
                    elif char == b"M":  # 右箭头
                        return KeyAction.RIGHT, "right"
                elif char == b"\r":  # 回车键
                    return KeyAction.ENTER, "enter"
                elif char == b"\x03":  # Ctrl+C
                    return KeyAction.QUIT, "quit"
                elif char == b" ":  # 空格键
                    return KeyAction.SPACE, " "
                elif char.lower() == b"q":
                    return KeyAction.QUIT, "q"
                elif char.lower() == b"b":
                    return KeyAction.BACK, "b"
                elif char.isdigit():
                    decoded_char = char.decode("utf-8", errors="ignore")
                    return KeyAction.NUMBER, decoded_char
                else:
                    # 其他字符按键
                    decoded_char = char.decode("utf-8", errors="ignore")
                    return KeyAction.CHAR, decoded_char
            else:
                # Unix/Linux系统
                try:
                    import termios, tty
                except ImportError:
                    # 如果无法导入Unix特定模块，返回未知
                    return KeyAction.UNKNOWN, ""

                fd = sys.stdin.fileno()
                old_settings = termios.tcgetattr(fd)
                try:
                    tty.setraw(sys.stdin.fileno())
                    char = sys.stdin.read(1)
                    if char == "\x1b":  # ESC序列
                        char = sys.stdin.read(2)
                        if char == "[A":  # 上箭头
                            return KeyAction.UP, "up"
                        elif char == "[B":  # 下箭头
                            return KeyAction.DOWN, "down"
                        elif char == "[D":  # 左箭头
                            return KeyAction.LEFT, "left"
                        elif char == "[C":  # 右箭头
                            return KeyAction.RIGHT, "right"
                    elif char == "\r" or char == "\n":  # 回车键
                        return KeyAction.ENTER, "enter"
                    elif char == "\x03":  # Ctrl+C
                        return KeyAction.QUIT, "quit"
                    elif char == " ":  # 空格键
                        return KeyAction.SPACE, " "
                    elif char.lower() == "q":
                        return KeyAction.QUIT, "q"
                    elif char.lower() == "b":
                        return KeyAction.BACK, "b"
                    elif char.isdigit():
                        return KeyAction.NUMBER, char
                    else:
                        return KeyAction.CHAR, char
                finally:
                    termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
        except Exception:
            pass

        # 如果没有成功读取，返回默认值
        return KeyAction.UNKNOWN, ""

    def create_live_menu(
        self,
        title: str,
        menu_items: List[str],
        selected_index: int,
        footer_text: Optional[str] = None,
    ) -> Layout:
        """创建Live菜单布局"""
        layout = Layout()
        layout.split_column(
            Layout(name="header", size=3),
            Layout(name="main", ratio=1),
            Layout(name="footer", size=3),
        )

        # 头部
        header_text = Text(title, style="bold blue", justify="center")
        layout["header"].update(Panel(header_text, box=box.DOUBLE))

        # 主菜单
        table = Table(show_header=False, box=box.SIMPLE, expand=True)
        table.add_column("Menu", style="cyan", no_wrap=True)

        for i, item in enumerate(menu_items):
            if i == selected_index:
                table.add_row(f"-> {item}", style="white on blue")
            else:
                table.add_row(f"   {item}")

        layout["main"].update(Align.center(table, vertical="middle"))

        # 底部
        if footer_text is None:
            footer_text = "q: 退出 | ↑↓: 导航 | Enter: 选择"
        footer = Text(footer_text, style="italic", justify="center")
        layout["footer"].update(Panel(footer, box=box.SIMPLE))

        return layout

    def show_live_menu(
        self,
        title: str,
        menu_items: List[str],
        item_actions: Optional[List[Callable]] = None,
        footer_text: Optional[str] = None,
    ) -> Any:
        """
        显示Live菜单，支持方向键导航

        Args:
            title: 菜单标题
            menu_items: 菜单项列表
            item_actions: 对应的动作函数列表
            footer_text: 底部提示文本

        Returns:
            Any: 执行结果
        """
        selected_index = 0

        with Live(
            self.create_live_menu(title, menu_items, selected_index, footer_text),
            auto_refresh=False,
            console=self.console,
        ) as live:

            while True:
                # 更新菜单显示
                layout = self.create_live_menu(
                    title, menu_items, selected_index, footer_text
                )
                live.update(layout)
                live.refresh()

                # 获取用户输入
                action, value = self.get_key_input()

                if action == KeyAction.UP:
                    selected_index = (selected_index - 1) % len(menu_items)
                elif action == KeyAction.DOWN:
                    selected_index = (selected_index + 1) % len(menu_items)
                elif action == KeyAction.ENTER:
                    # 执行选中的动作
                    if item_actions and selected_index < len(item_actions):
                        live.stop()
                        try:
                            result = item_actions[selected_index]()
                            return result
                        except Exception as e:
                            self.console.print(f"[red]执行操作时出错: {str(e)}[/red]")
                            time.sleep(2)
                            live.start()
                            continue
                    else:
                        return selected_index
                elif action == KeyAction.NUMBER:
                    # 直接数字选择
                    try:
                        choice_idx = int(value) - 1
                        if 0 <= choice_idx < len(menu_items):
                            selected_index = choice_idx
                            if item_actions and choice_idx < len(item_actions):
                                live.stop()
                                try:
                                    result = item_actions[choice_idx]()
                                    return result
                                except Exception as e:
                                    self.console.print(
                                        f"[red]执行操作时出错: {str(e)}[/red]"
                                    )
                                    time.sleep(2)
                                    live.start()
                                    continue
                            else:
                                return choice_idx
                    except ValueError:
                        continue
                elif action == KeyAction.BACK:
                    return "back"
                elif action == KeyAction.QUIT or (
                    action == KeyAction.CHAR and value.lower() == "q"
                ):
                    return "quit"

    def create_data_table_layout(
        self,
        title: str,
        columns: List[str],
        data: List[List[str]],
        selected_index: int,
        page_info: Optional[str] = None,
        hint_text: Optional[str] = None,
    ) -> Layout:
        """创建数据表格布局"""
        layout = Layout()

        if hint_text:
            layout.split_column(
                Layout(
                    Panel(
                        f"[italic]{hint_text}[/italic]",
                        style="blue",
                        border_style="blue",
                    ),
                    name="hint",
                    size=3,
                ),
                Layout(name="main"),
            )
            main_layout = layout["main"]
        else:
            main_layout = layout

        # 创建表格
        table_title = f"{title} {page_info}" if page_info else title
        table = Table(title=table_title, box=box.ROUNDED, expand=True)

        # 添加选择列
        table.add_column("", style="green", width=4)

        # 添加数据列
        for col in columns:
            table.add_column(col, style="white")

        # 添加数据行
        for i, row in enumerate(data):
            selector = ">> " if i == selected_index else "   "
            row_data = [selector] + row
            style = "white on blue" if i == selected_index else None
            table.add_row(*row_data, style=style)

        main_layout.update(table)
        return layout

    def show_paginated_table(
        self,
        title: str,
        columns: List[str],
        data_loader: Callable[[int], tuple[List[List[str]], bool]],
        item_handler: Optional[Callable[[int, List[str]], Any]] = None,
        hint_text: Optional[str] = None,
        reply_handler: Optional[Callable[[int, List[str]], Any]] = None,  # 新增：回复处理函数
    ) -> Any:
        """
        显示分页表格，支持方向键导航

        Args:
            title: 表格标题
            columns: 列名列表
            data_loader: 数据加载函数 (page) -> (data, has_more)
            item_handler: 项目选择处理函数 (index, row_data) -> result
            hint_text: 提示文本

        Returns:
            Any: 处理结果
        """
        page = 0
        selected_index = 0
        current_data = []
        has_more = True

        # 加载初始数据
        try:
            current_data, has_more = data_loader(page)
            if not current_data:
                self.console.print(Panel("暂无数据", title="提示", style="yellow"))
                Prompt.ask("按回车键返回")
                return None
        except Exception as e:
            self.console.print(
                Panel(f"加载数据失败: {str(e)}", title="错误", style="red")
            )
            Prompt.ask("按回车键返回")
            return None

        with Live(
            self.create_data_table_layout(
                title,
                columns,
                current_data,
                selected_index,
                f"(第{page + 1}页)",
                hint_text,
            ),
            auto_refresh=False,
            console=self.console,
        ) as live:

            need_refresh = False  # 初始不需要刷新，Live已经显示了初始状态

            while True:
                # 只在需要时才更新显示
                if need_refresh:
                    page_info = f"(第{page + 1}页)"
                    layout = self.create_data_table_layout(
                        title,
                        columns,
                        current_data,
                        selected_index,
                        page_info,
                        hint_text,
                    )
                    live.update(layout)
                    live.refresh()
                    need_refresh = False  # 重置刷新标记

                # 获取用户输入
                action, value = self.get_key_input()

                if action == KeyAction.UP and current_data:
                    selected_index = max(0, selected_index - 1)
                    need_refresh = True
                elif action == KeyAction.DOWN and current_data:
                    selected_index = min(len(current_data) - 1, selected_index + 1)
                    need_refresh = True
                elif action == KeyAction.ENTER:
                    if (
                        current_data
                        and item_handler
                        and 0 <= selected_index < len(current_data)
                    ):
                        live.stop()
                        try:
                            result = item_handler(
                                selected_index, current_data[selected_index]
                            )
                            if result == "refresh":
                                # 刷新当前页数据
                                current_data, has_more = data_loader(page)
                                live.start()
                                need_refresh = True
                            elif result:
                                return result
                            else:
                                live.start()
                                need_refresh = True
                        except Exception as e:
                            self.console.print(f"[red]处理选择时出错: {str(e)}[/red]")
                            time.sleep(2)
                            live.start()
                            need_refresh = True
                elif action == KeyAction.SPACE or (
                    action == KeyAction.CHAR and value.lower() == "n"
                ):
                    # 下一页
                    if has_more:
                        try:
                            # 暂停Live更新
                            live.stop()

                            # 清除屏幕并显示加载提示
                            self.console.clear()
                            self.console.print(
                                Panel(f"正在加载第{page + 2}页...", title="请稍候")
                            )

                            # 加载下一页数据
                            next_data, has_more = data_loader(page + 1)
                            if next_data:
                                page += 1
                                current_data = next_data
                                selected_index = 0

                            # 清除屏幕准备重新显示
                            self.console.clear()

                            # 重新启动Live并刷新
                            live.start(refresh=True)
                            need_refresh = True
                        except Exception as e:
                            self.console.clear()
                            self.console.print(
                                Panel(
                                    f"加载下一页失败: {str(e)}",
                                    title="错误",
                                    style="red",
                                )
                            )
                            time.sleep(2)
                            self.console.clear()
                            live.start(refresh=True)
                            need_refresh = True
                elif action == KeyAction.CHAR and value.lower() == "p":
                    # 上一页
                    if page > 0:
                        try:
                            # 暂停Live更新
                            live.stop()

                            # 清除屏幕并显示加载提示
                            self.console.clear()
                            self.console.print(
                                Panel(f"正在加载第{page}页...", title="请稍候")
                            )

                            # 加载上一页数据
                            prev_data, _ = data_loader(page - 1)
                            if prev_data:
                                page -= 1
                                current_data = prev_data
                                selected_index = 0
                                # 重新检查是否有更多页
                                try:
                                    _, has_more = data_loader(page + 1)
                                except:
                                    has_more = False

                            # 清除屏幕准备重新显示
                            self.console.clear()

                            # 重新启动Live并刷新
                            live.start(refresh=True)
                            need_refresh = True
                        except Exception as e:
                            self.console.clear()
                            self.console.print(
                                Panel(
                                    f"加载上一页失败: {str(e)}",
                                    title="错误",
                                    style="red",
                                )
                            )
                            time.sleep(2)
                            self.console.clear()
                            live.start(refresh=True)
                            need_refresh = True
                elif action == KeyAction.CHAR and value.lower() == "r" and current_data and "评论" in title:
                    # r键 - 回复评论（只在评论列表中有效）
                    if 0 <= selected_index < len(current_data):
                        live.stop()
                        try:
                            # 优先使用reply_handler，如果没有则使用item_handler
                            handler = reply_handler if reply_handler else item_handler
                            if handler:
                                result = handler(selected_index, current_data[selected_index])
                                if result == "refresh":
                                    current_data, has_more = data_loader(page)
                                elif result:
                                    return result
                            live.start()
                            need_refresh = True
                        except Exception as e:
                            self.console.print(f"[red]回复评论时出错: {str(e)}[/red]")
                            time.sleep(2)
                            live.start()
                            need_refresh = True
                elif action == KeyAction.CHAR and value.lower() == "c" and "评论" in title:
                    # c键 - 发表评论（只在评论列表中有效）
                    live.stop()
                    try:
                        # 返回特殊信号表示需要发表新评论
                        return "new_comment"
                    except Exception as e:
                        self.console.print(f"[red]发表评论时出错: {str(e)}[/red]")
                        time.sleep(2)
                        live.start()
                        need_refresh = True
                elif action == KeyAction.BACK:
                    return None
                elif action == KeyAction.QUIT:
                    return "quit"


class InputHandler:
    """兼容性输入处理器 - 保持向后兼容"""

    def __init__(self, console: Console):
        self.console = console
        self.directional = DirectionalInputHandler(console)

    def get_simple_choice(
        self, prompt_text: str, choices: List[str], default: Optional[str] = None
    ) -> str:
        """获取简单选择"""
        try:
            result = Prompt.ask(prompt_text, choices=choices, default=default)
            return (
                result
                if result is not None
                else (default or choices[0] if choices else "")
            )
        except KeyboardInterrupt:
            return "q"
        except Exception:
            return default or choices[0] if choices else ""

    def get_text_input(
        self, prompt_text: str, default: Optional[str] = None, password: bool = False
    ) -> str:
        """获取文本输入"""
        try:
            result = Prompt.ask(prompt_text, default=default, password=password)
            return result if result is not None else (default or "")
        except KeyboardInterrupt:
            return ""
        except Exception:
            return default or ""

    def confirm_action(self, prompt_text: str, default: bool = False) -> bool:
        """确认操作"""
        try:
            return Confirm.ask(prompt_text, default=default)
        except KeyboardInterrupt:
            return False
        except Exception:
            return default

    def wait_for_continue(self, message: str = "按回车键继续...") -> None:
        """等待用户按键继续"""
        try:
            Prompt.ask(message, default="")
        except KeyboardInterrupt:
            pass
        except Exception:
            pass


# 删除旧的MenuManager类，不再需要
class MenuManager:
    """简化的菜单管理器 - 使用DirectionalInputHandler"""

    def __init__(self, console: Console, input_handler: InputHandler):
        self.console = console
        self.input_handler = input_handler
        self.directional = DirectionalInputHandler(console)

    def show_menu(
        self,
        title: str,
        menu_options: List[Dict[str, Any]],
        allow_back: bool = True,
        allow_quit: bool = True,
    ) -> Any:
        """显示菜单并获取用户选择"""

        # 构建菜单项和动作
        menu_items = []
        actions = []

        for option in menu_options:
            menu_items.append(f"[{option['key']}] {option['label']}")
            actions.append(option.get("action", lambda: None))

        if allow_back:
            menu_items.append("[b] 返回上级菜单")
            actions.append(lambda: "back")

        if allow_quit:
            menu_items.append("[q] 退出程序")
            actions.append(lambda: "quit")

        return self.directional.show_live_menu(
            title=title, menu_items=menu_items, item_actions=actions
        )
