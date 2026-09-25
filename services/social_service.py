"""
社交功能实现（动态、消息等）
"""

import time
import base64
import hashlib
import urllib.parse
import webbrowser
import tempfile
import os
import json
from typing import List
from random import randint
from config.settings import settings
from core.api_client import APIClient
from utils.logger import setup_logger
from utils.html_restructure import restructure_html
from models.social import Note

logger = setup_logger(__name__)


class SocialService:
    """社交服务类，处理社交相关API调用"""
    
    def __init__(self, api_client: APIClient = None):
        """
        初始化社交服务
        
        Args:
            api_client (APIClient, optional): API客户端实例。如果未提供，将创建新的实例。
        """
        self.client = api_client if api_client else APIClient()
    
    def _generate_key(self, secret_key: str) -> str:
        """
        生成解密密钥
        
        Args:
            secret_key (str): 用于生成密钥的秘密字符串
            
        Returns:
            str: 生成的解密密钥
        """
        fixed_str = "17bf6ed3b808eb7dcfa5wa0f1f0cf1de"
        md5_input = secret_key + fixed_str
        md5_hash = hashlib.md5(md5_input.encode('utf-8')).hexdigest()
        key = md5_hash[8:16].lower()
        return key
    
    def _decrypt_content(self, encrypted_str: str, secret_key: str) -> str:
        """
        解密文章内容
        
        Args:
            encrypted_str (str): 被加密的内容
            secret_key (str): 解密密钥
            
        Returns:
            str: 解密后的内容
        """
        key = self._generate_key(str(secret_key))
        
        # 1. base64 decode 输入字符串
        step1 = base64.b64decode(encrypted_str)

        # 2. XOR 解密
        key_bytes = key.encode('utf-8')
        key_len = len(key_bytes)
        decrypted_bytes = bytearray()
        for i in range(len(step1)):
            decrypted_bytes.append(step1[i] ^ key_bytes[i % key_len])

        # 3. base64 decode 解密后的字节串
        step3 = base64.b64decode(decrypted_bytes)

        # 4. URL 解码成明文字符串
        plaintext = urllib.parse.unquote(step3.decode('utf-8'))
        return plaintext
    
    def get_note(self, note_id: str, replace_content: bool = False, restructure_html: bool = False, external_image_urls: List[str] = None, post_data: dict = None) -> dict:
        """
        获取笔记详情
        
        Args:
            note_id (str): 笔记ID
            replace_content (bool): 是否将解密后的内容替换回原始HTML中
            restructure_html (bool): 是否重构HTML内容以改善显示效果
            external_image_urls (List[str], optional): 外部提供的图片URL列表
            post_data (dict, optional): 文章列表中的文章数据，包含图片URL等信息
            
        Returns:
            dict: 笔记详情数据
        """
        # 使用settings.GET_NOTE_PARAMS作为基础参数
        params = settings.GET_NOTE_PARAMS.copy()
        
        # 生成secret_key用于API请求和后续解密
        secret_key = randint(100000, 2000000)
        
        # 更新动态生成的参数
        params.update({
            "secret_key": secret_key,
            "timestamp": str(int(time.time() * 1000)),
            "id": note_id  # ID作为查询参数而不是路径参数
        })
        
        # 发起API请求
        try:
            # id参数会作为查询参数附加在URL后面
            response = self.client.request("GET_NOTE", **params)
            
            # 如果响应中有加密内容，则进行解密
            if response.get('res') == 0 and 'data' in response:  # 在api_client里把响应还原成json了
                note_data = response['data']
                logger.debug(f"获取笔记详情，note_data字段: {note_data.keys()}")
                
                # 从post_data中提取图片URL信息
                image_urls = []
                if post_data and 'urls' in post_data:
                    urls_data = post_data['urls']
                    # 如果urls_data是字符串，则尝试解析为JSON
                    if isinstance(urls_data, str):
                        try:
                            image_urls = json.loads(urls_data)
                        except json.JSONDecodeError:
                            # 如果解析失败，则将原始字符串作为单个URL处理
                            image_urls = [urls_data]
                    # 如果urls_data已经是列表，则直接使用
                    elif isinstance(urls_data, list):
                        image_urls = urls_data
                    
                    # 将图片URL信息添加到note_data中
                    if image_urls:
                        note_data['_image_urls'] = image_urls
                        logger.debug(f"从post_data中提取的图片URL: {image_urls}")
                
                # 统一处理图片URL
                if 'urls' in note_data and note_data['urls'] and '_image_urls' not in note_data:
                    urls_data = note_data['urls']
                    if isinstance(urls_data, str):
                        try:
                            image_urls = json.loads(urls_data)
                        except json.JSONDecodeError:
                            image_urls = [urls_data]
                    elif isinstance(urls_data, list):
                        image_urls = urls_data
                    else:
                        image_urls = []
                    
                    if image_urls:
                        note_data['_image_urls'] = image_urls
                        logger.debug(f"从note_data中解析得到的图片URL: {image_urls}")
                
                if 'content' in note_data and note_data['content']:
                    try:
                        # 尝试解密内容
                        decrypted_content = self._decrypt_content(note_data['content'], secret_key)
                        note_data['decrypted_content'] = decrypted_content
                        
                        # 如果需要将解密后的内容替换回原始HTML
                        if replace_content and 'html' in note_data:
                            # 替换HTML中的加密内容为解密后的内容
                            original_content = note_data['content']
                            # 直接使用解密后的内容替换，因为它是原始的HTML格式
                            html_with_decrypted = note_data['html'].replace(
                                original_content, decrypted_content)
                            
                            # 只移除hidden属性，保留div元素以便后续添加CSS样式
                            html_with_decrypted = html_with_decrypted.replace(
                                '<div hidden class="note-content', 
                                '<div class="note-content'
                            )
                            
                            note_data['html_with_decrypted_content'] = html_with_decrypted
                        
                        # 如果需要重构HTML以改善显示效果
                        if restructure_html and 'html_with_decrypted_content' in note_data:
                            # 重构HTML内容（使用已解密替换后的内容）
                            restructured_html = self._restructure_note_html(
                                note_data['html_with_decrypted_content'], note_data, external_image_urls)
                            note_data['restructured_html'] = restructured_html
                        elif restructure_html and 'html' in note_data:
                            # 如果没有解密后的内容，使用原始HTML
                            restructured_html = self._restructure_note_html(
                                note_data['html'], note_data, external_image_urls)
                            note_data['restructured_html'] = restructured_html
                    except Exception as e:
                        logger.warning(f"解密笔记内容失败: {e}")
            
            logger.info(f"获取笔记成功: {note_id}")
            logger.debug(f"最终返回的note_data包含字段: {note_data.keys()}")
            if '_image_urls' in note_data:
                logger.debug(f"图片URL数量: {len(note_data['_image_urls'])}")
            return response
        except Exception as e:
            logger.error(f"获取笔记失败: {note_id}, 错误: {e}")
            raise
    
    def _restructure_note_html(self, html_content: str, note_data: dict = None, external_image_urls: List[str] = None) -> str:
        """
        重构笔记HTML内容
        
        Args:
            html_content: 原始HTML内容
            note_data: 笔记数据，包含图片URL等信息
            external_image_urls: 外部提供的图片URL列表
            
        Returns:
            重构后的HTML内容
        """
        # 从note_data创建Note模型实例以处理图片URL
        image_urls = []
        
        # 优先使用外部提供的图片URL
        if external_image_urls:
            image_urls = external_image_urls
            logger.debug(f"使用外部提供的图片链接: {image_urls}")
        elif note_data:
            # 优先使用从post_data中提取的图片URL
            if '_image_urls' in note_data and note_data['_image_urls']:
                image_urls = note_data['_image_urls']
                logger.debug(f"使用从post_data中提取的图片链接: {image_urls}")
            # 使用API返回的urls字段（如果存在）
            elif 'urls' in note_data and note_data['urls']:
                urls_data = note_data.get('urls', [])
                logger.debug(f"直接从note_data获取的urls数据: {urls_data}")
                if isinstance(urls_data, list):
                    image_urls = urls_data
                elif isinstance(urls_data, str):
                    try:
                        image_urls = json.loads(urls_data)
                    except (json.JSONDecodeError, TypeError):
                        image_urls = [urls_data] if urls_data else []
                logger.debug(f"从note_data中解析得到的图片链接: {image_urls}")
        else:
            logger.debug("_restructure_note_html未接收到note_data")
        
        result = restructure_html(html_content, content_type="note", image_urls=image_urls)
        logger.debug(f"重构后的HTML内容长度: {len(result)}")
        return result
    
    def open_note_in_browser(self, note_data: dict, use_restructured: bool = False) -> str:
        """
        在浏览器中打开笔记
        
        Args:
            note_data (dict): 笔记数据，应该包含html_with_decrypted_content或html字段
            use_restructured (bool): 是否使用重构后的HTML内容
            
        Returns:
            str: 临时文件路径
        """
        # 确定使用哪个HTML内容
        if use_restructured and 'restructured_html' in note_data:
            html_content = note_data['restructured_html']
        else:
            html_content = note_data.get('html_with_decrypted_content') or note_data.get('html')
        
        if not html_content:
            raise ValueError("没有找到HTML内容")
        
        # 创建临时文件
        with tempfile.NamedTemporaryFile(mode='w', suffix='.html', delete=False, encoding='utf-8') as f:
            f.write(html_content)
            temp_file_path = f.name
        
        # 在浏览器中打开临时文件
        webbrowser.open(f'file://{temp_file_path}')
        
        return temp_file_path
    
    def get_follow_user_notes(self, page: str = "0", min_time: str = None, **kwargs):
        """
        获取关注用户的文章列表
        
        Args:
            min_time (str, optional): 时间戳，用于分页加载更多数据
            page (str): 页码，默认为"0"
            **kwargs: 其他API特定参数
            
        Returns:
            dict: API响应数据，包含文章列表、分页信息等
        """
        # 构建请求参数
        params = {
            "page": page,
            **kwargs
        }
        
        # 只有当提供了min_time参数时才添加到请求参数中
        if min_time is not None:
            params["min_time"] = min_time
        
        try:
            # 发起API请求
            response = self.client.request("GET_FOLLOW_USER_NOTES", **params)
            if response.get('res') == 0:
                notes = response.get('notes', [])
                logger.info(f"成功获取到{len(notes)}篇关注用户的文章")
                # 添加调试日志，记录文章数据结构
                for i, note in enumerate(notes):
                    logger.debug(f"关注文章[{i}] ID: {note.get('id', 'N/A')}, "
                               f"标题: {note.get('title', 'N/A')}, "
                               f"图片URLs: {note.get('urls', 'N/A')}, "
                               f"缩略图: {note.get('thumb', 'N/A')}")
                return response
            else:
                logger.error("获取关注用户文章失败")
                return response
                
        except Exception as e:
            logger.error(f"获取关注用户文章失败: {str(e)}")
            raise

    def get_user_notes(self, home_id: str, type: str = "", page: str = "0", **kwargs) -> dict:
        """
        获取指定用户的文章列表
        
        Args:
            home_id (str): 用户ID，指定要查看文章的用户
            type (str): 文章类型筛选，默认为空字符串(所有类型)
            page (str): 页码，默认为"0"
            **kwargs: 其他API特定参数
            
        Returns:
            dict: API响应数据，包含用户文章列表、分页信息等
            
        Raises:
            Exception: 当API请求失败时抛出异常
        """
        # 构建请求参数
        params = {
            "home_id": home_id,
            "type": type,
            "page": page,
            **kwargs
        }
        
        try:
            # 发起API请求
            response = self.client.request("GET_USER_NOTES", **params)
            if response.get('res') == 0:
                notes = response.get('notes', [])
                logger.info(f"成功获取到{len(notes)}篇用户文章（用户ID: {home_id}）")
                # 添加调试日志，记录文章数据结构
                for i, note in enumerate(notes):
                    logger.debug(f"用户文章[{i}] ID: {note.get('id', 'N/A')}, "
                               f"标题: {note.get('title', 'N/A')}, "
                               f"图片URLs: {note.get('urls', 'N/A')}, "
                               f"缩略图: {note.get('thumb', 'N/A')}")
                return response
            else:
                logger.error(f"获取用户文章失败（用户ID: {home_id}）")
                return response
                
        except Exception as e:
            logger.error(f"获取用户文章失败（用户ID: {home_id}）: {str(e)}")
            raise

    def get_user_data(self, home_id: str, **kwargs) -> dict:
        """
        获取指定用户的详细信息
        
        Args:
            home_id (str): 用户ID，指定要查看信息的用户
            **kwargs: 其他API特定参数
            
        Returns:
            dict: API响应数据，包含用户详细信息
            
        Raises:
            Exception: 当API请求失败时抛出异常
        """
        # 构建请求参数
        params = {
            "home_id": home_id,
            **kwargs
        }
        
        try:
            # 发起API请求
            response = self.client.request("GET_USER_DATA", **params)
            if response.get('res') == 0:
                logger.info(f"成功获取到用户详细信息（用户ID: {home_id}）")
                logger.debug(f"用户信息包含字段: {list(response.keys())}")
                return response
            else:
                logger.error(f"获取用户详细信息失败（用户ID: {home_id}）")
                return response
                
        except Exception as e:
            logger.error(f"获取用户详细信息失败（用户ID: {home_id}）: {str(e)}")
            raise

    def get_social_categories(self):
        """获取社交文章分区选项"""
        return [
            {"name": "日常", "type": "10", "params": {"flag": "0"}},
            {"name": "好物", "type": "7", "params": {"flag": "0"}},
            {"name": "试卷", "type": "12", "params": {"subject_tag": "0", "grade_tag": "0"}},
            {"name": "难题趣题", "type": "6", "params": {"flag": "0", "grade_tag": "0"}},
            {"name": "学习经验", "type": "2", "params": {"flag": "0", "xd_tag": "0"}},
            {"name": "绘画", "type": "9", "params": {"flag": "0"}},
            {"name": "学习Plog", "type": "13", "params": {"flag": "0"}},
            {"name": "手工种植", "type": "8", "params": {"flag": "0"}},
            {"name": "飞花令", "type": "11", "params": {"flag": "0"}},
            {"name": "作文随笔", "type": "16", "params": {"flag": "0"}},
            {"name": "书法", "type": "14", "params": {"flag": "0"}}
        ]
        
    def get_posts(self, **kwargs):
        """
        获取文章列表
        
        Args:
            **kwargs: API特定参数
            
        Returns:
            dict: API响应数据
        """
        try:
            # 实现获取文章的逻辑
            response = self.client.request("GET_DISCOVER_NOTE", **kwargs)
            if response.get('res') == 0:
                notes = response.get('notes')
                logger.info(f"成功获取到{len(notes)}篇文章")  #! WHAT THE HELL
                # 添加调试日志，记录文章数据结构
                for i, note in enumerate(notes):
                    logger.debug(f"文章[{i}] ID: {note.get('id', 'N/A')}, "
                               f"标题: {note.get('title', 'N/A')}, "
                               f"图片URLs: {note.get('urls', 'N/A')}, "
                               f"缩略图: {note.get('thumb', 'N/A')}")
                return response
            else:
                logger.error("获取文章失败")
                return response
                
        except Exception as e:
            logger.error(f"获取文章失败: {str(e)}")
            raise
    
    def get_comments(self, note_id: str, page: str = "0", order_type: str = "1") -> dict:
        """
        获取文章评论
        
        Args:
            note_id (str): 文章ID
            page (str): 页码，默认为"0"
            order_type (str): 排序类型，默认为"1"(最新评论)
            
        Returns:
            dict: 评论数据
        """
        # 构建请求参数
        params = {
            "nid": note_id,
            "order_type": order_type,
            "start_time": str(int(time.time())),
            "ct": "10",  # 每页评论数量
            "page": page
        }
        
        try:
            # 发起API请求
            response = self.client.request("GET_COMMENT_BY_NID", **params)
            if response.get('res') == 0:
                comments = response.get('comments', [])
                logger.info(f"成功获取到{len(comments)}条评论")
                return response
            else:
                logger.error("获取评论失败")
                return response
                
        except Exception as e:
            logger.error(f"获取评论失败: {str(e)}")
            raise

    def get_comment_replies(self, comment_id: str, page: str = "0", order_type: str = "1") -> dict:
        """
        获取评论的回复（内层评论）
        
        Args:
            comment_id (str): 外层评论ID（fid）
            page (str): 页码，默认为"0"
            order_type (str): 排序类型，默认为"1"(最新评论)
            
        Returns:
            dict: 回复评论数据
        """
        # 构建请求参数
        params = {
            "fid": comment_id,
            "order_type": order_type,
            "start_time": str(int(time.time())),
            "ct": "20",  # 每页评论数量
            "page": page
        }
        
        try:
            # 发起API请求
            response = self.client.request("GET_COMMENT_BY_FID", **params)
            if response.get('res') == 0:
                replies = response.get('comments', [])
                logger.info(f"成功获取到{len(replies)}条回复评论")
                return response
            else:
                logger.error("获取回复评论失败")
                return response
                
        except Exception as e:
            logger.error(f"获取回复评论失败: {str(e)}")
            raise
    
    def post_comment(self, content: str, note_id: str, parent_comment_id: str = "0") -> dict:
        """
        发布评论或回复
        
        Args:
            content (str): 评论内容
            note_id (str): 文章ID
            parent_comment_id (str): 父级评论ID，默认为"0"（外层评论）
            
        Returns:
            dict: API响应数据，包含以下字段：
                - res (int): 响应状态码，0表示成功
                - coin_count (str): 用户当前积分数量
                - size_score (int): 内容评分
                - content (str): 处理后的评论内容
                - id (str): 新创建评论的唯一标识符
            
        Raises:
            Exception: 当API请求失败时抛出异常
        """
        # 验证输入参数
        if not content or not content.strip():
            raise ValueError("评论内容不能为空")
        
        if not note_id:
            raise ValueError("文章ID不能为空")
        
        # 构建请求参数
        params = {
            "content": content.strip(),
            "nid": note_id,
            "fid": parent_comment_id
        }
        
        try:
            # 发起API请求
            response = self.client.request("SET_NOTE_COMMENT", **params)
            
            if response.get('res') == 0:
                comment_id = response.get('id', '')
                is_reply = parent_comment_id != "0"
                action_type = "回复" if is_reply else "评论"
                logger.info(f"成功发布{action_type}（评论ID: {comment_id}）")
                logger.debug(f"响应数据: {response}")
                return response
            else:
                error_msg = f"发布评论失败，服务器返回错误码: {response.get('res')}"
                logger.error(error_msg)
                raise Exception(error_msg)
                
        except Exception as e:
            is_reply = parent_comment_id != "0"
            action_type = "回复" if is_reply else "评论"
            logger.error(f"发布{action_type}失败: {str(e)}")
            raise
