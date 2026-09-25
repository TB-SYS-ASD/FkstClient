import requests
import re
from config.settings import settings
from config.api_endpoints import API_ENDPOINTS
from core.param_manager import ParamManager
from core.signer import generate_signature, get_note_signature, get_comment_signature
from utils.logger import setup_logger

logger = setup_logger(__name__)

class APIClient:
    def __init__(self):
        self.param_manager = ParamManager()
        self.session = requests.Session()
        self.logged_in = False
        logger.debug("APIClient初始化完成，初始动态参数: %s", self.param_manager.dynamic_params)
    
    def request(self, endpoint_name, **kwargs):
        """统一请求入口"""
        if endpoint_name not in API_ENDPOINTS:
            raise ValueError(f"未知的API端点: {endpoint_name}")
        
        endpoint = API_ENDPOINTS[endpoint_name]
        
        # 1. 准备参数
        params = self.param_manager.build_params(endpoint_name, **kwargs)
        
        # 2. 添加签名 (根据端点使用不同的签名算法)
        if endpoint_name == "GET_NOTE":
            params["api_sig"] = get_note_signature(params)
        elif endpoint_name == "SET_NOTE_COMMENT":
            params["api_sig"] = get_comment_signature(params)
        else:
            params["api_sig"] = generate_signature(params)
        
        # 3. 发送请求
        # 检查是否使用自定义BASE_URL
        base_url = endpoint.get("base_url", settings.BASE_URL)
        url = f"{base_url}{endpoint['path']}"
        method = endpoint["method"].lower()
        
        logger.debug("请求 %s: %s %s", endpoint_name, method.upper(), url)
        logger.debug("请求参数: %s", params)
        logger.debug("当前会话的Cookies: %s", dict(self.session.cookies))
        
        if method == "get":
            response = self.session.get(url, params=params)
        else:
            # POST请求不携带cookie，创建新的session发送请求后关闭
            temp_session = requests.Session()
            # 复制原始session的headers（如果有的话）
            temp_session.headers.update(self.session.headers)
            response = temp_session.post(url, data=params)
            temp_session.close()
        
        # 4. 处理响应
        if response.status_code != 200:
            error_msg = f"API错误: {response.status_code} - {response.text}"
            logger.error(error_msg)
            raise Exception(error_msg)
        
        # 记录收到的Cookies
        if response.cookies:
            logger.debug("收到响应Cookies: %s", dict(response.cookies))
        
        # 检查是否为特定的HTML响应端点
        if endpoint_name == "GET_NOTE":
            # 对于文章详情，返回原始HTML内容
            html_content = response.text
            logger.debug("HTML响应数据预览: %s", html_content[:500])
            
            # 提取加密内容
            encrypted_content = self._extract_encrypted_content(html_content)
            
            # 构造响应格式，保持与JSON响应的一致性
            result = {
                "res": 0,  # 默认成功
                "data": {
                    "content": encrypted_content,  # 提取的密文
                    "html": html_content  # 源代码
                }
            }
            
            logger.debug("提取的加密内容: %s", encrypted_content)
            return result
        else:
            # 标准JSON响应处理
            json_response = response.json()
            logger.debug("响应数据: %s", json_response)
            
            # 5. 更新动态参数（如果响应中有新token等）
            self.param_manager.update_from_response(json_response)
            
            return json_response
    
    def _extract_encrypted_content(self, html_content: str) -> str:
        """
        从HTML内容中提取加密的内容
        
        Args:
            html_content (str): 完整的HTML内容
            
        Returns:
            str: 提取的加密内容，如果没有找到则返回空字符串
        """
        # 查找包含加密内容的div元素
        pattern = r'<div[^>]*class="note-content[^>]*>([^<]+)</div>'
        match = re.search(pattern, html_content)
        
        if match:
            encrypted_content = match.group(1)
            return encrypted_content
        else:
            # 如果正则表达式没有匹配到，记录日志并返回空字符串
            logger.warning("未在HTML中找到加密内容")
            return ""
    
    def update_dynamic_params(self, **kwargs):
        """更新动态参数"""
        logger.debug("APIClient更新动态参数: %s", kwargs)
        self.param_manager.update_dynamic_params(**kwargs)