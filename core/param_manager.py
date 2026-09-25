import time
from config.settings import settings
from config.api_endpoints import API_ENDPOINTS
from utils.logger import setup_logger

logger = setup_logger(__name__)

class ParamManager:
    def __init__(self):
        # 公共参数存储
        self.dynamic_params = settings.DEFAULT_DYNAMIC_PARAMS.copy()
        logger.debug("初始化动态参数: %s", self.dynamic_params)
    
    def build_params(self, endpoint_name, **kwargs):
        """
        构建完整请求参数
        
        该函数会根据指定的API端点名称，结合公共参数、默认参数和传入的特定参数，
        构建完整的请求参数字典，并在缺少必需参数时抛出异常。
        
        Args:
            endpoint_name (str): API端点名称，用于获取该接口的配置信息
            **kwargs: API特定参数，包含该接口需要的业务参数
            
        Returns:
            dict: 完整的请求参数字典，包含公共参数、默认参数和接口特定参数
            
        Raises:
            ValueError: 当缺少必需参数时抛出异常
        """
        # 1. 获取端点配置
        endpoint = API_ENDPOINTS.get(endpoint_name)
        
        # 2. 获取端点默认参数
        default_params = endpoint.get("default_params", {}) if endpoint else {}
        
        # 3. 校验必需参数（考虑默认参数）
        if endpoint and "required_params" in endpoint:
            missing_params = [
                param for param in endpoint["required_params"]
                if param not in kwargs and param not in default_params
            ]
            if missing_params:
                raise ValueError(f"缺少必需参数: {missing_params}")
        
        # 4. 构建基础参数
        base_params = {}
        
        # 添加设备参数（除非明确跳过）
        if not endpoint or not endpoint.get("skip_device_params", False):
            base_params.update(settings.DEVICE_PARAMS)
        
        # 添加动态参数（除非明确跳过或部分排除）
        if not endpoint or not endpoint.get("skip_dynamic_params", False):
            dynamic_params_to_include = self.dynamic_params.copy()
            # 如果指定了要排除的动态参数，则移除它们
            exclude_dynamic_params = endpoint.get("exclude_dynamic_params", []) if endpoint else []
            for param in exclude_dynamic_params:
                dynamic_params_to_include.pop(param, None)  # 使用pop的默认值避免KeyError
            base_params.update(dynamic_params_to_include)
               
        # 添加时间戳（除非明确指定不添加）
        if endpoint_name != "GET_NOTE":
            base_params["call_id"] = str(int(time.time() * 1000))  # 毛秒时间戳
               
        # 5. 添加API特定参数（参数优先级从低到高：默认参数 < 基础参数 < 特定参数）
        result_params = {**default_params, **base_params, **kwargs}
        logger.debug("构建参数 endpoint=%s, 动态参数=%s, 最终参数=%s", 
                    endpoint_name, self.dynamic_params, result_params)
        return result_params
    
    def update_dynamic_params(self, **kwargs):
        """更新动态参数"""
        self.dynamic_params.update(kwargs)
        logger.debug("更新动态参数: %s", kwargs)
        logger.debug("当前所有动态参数: %s", self.dynamic_params)
    
    def update_from_response(self, response: dict):
        """从响应中更新参数（如果存在）"""
        # 这里可以添加从API响应中提取参数的逻辑
        pass