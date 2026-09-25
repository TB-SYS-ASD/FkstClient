import json
from typing import Dict, Any, Optional
from config.settings import settings
from utils.logger import setup_logger

logger = setup_logger(__name__)

class CategoryParamManager:
    """
    分区参数管理器，用于管理不同分区的自定义请求参数
    """
    
    def __init__(self):
        # 存储每个分区的自定义参数
        self.category_params: Dict[str, Dict[str, Any]] = {}
        # 默认参数配置（注意：这些是默认值，不会覆盖实际的用户凭证）
        self.default_params: Dict[str, Any] = {}
        logger.debug("初始化分区参数管理器")
    
    def set_category_params(self, category_type: str, params: Dict[str, Any]):
        """
        设置特定分区的参数
        
        Args:
            category_type: 分区类型 (如 "10", "7", "12" 等)
            params: 要设置的参数字典
        """
        if category_type not in self.category_params:
            self.category_params[category_type] = {}
        
        self.category_params[category_type].update(params)
        logger.debug(f"设置分区 {category_type} 的参数: {params}")
    
    def get_category_params(self, category_type: str) -> Dict[str, Any]:
        """
        获取特定分区的参数
        
        Args:
            category_type: 分区类型
            
        Returns:
            该分区的参数字典
        """
        return self.category_params.get(category_type, {}).copy()
    
    def remove_category_params(self, category_type: str, param_keys: Optional[list] = None):
        """
        移除特定分区的参数
        
        Args:
            category_type: 分区类型
            param_keys: 要移除的参数键列表，如果为None则移除整个分区的参数
        """
        if param_keys is None:
            # 移除整个分区的参数
            self.category_params.pop(category_type, None)
            logger.debug(f"移除分区 {category_type} 的所有参数")
        else:
            # 只移除指定的参数
            if category_type in self.category_params:
                for key in param_keys:
                    self.category_params[category_type].pop(key, None)
                logger.debug(f"从分区 {category_type} 移除参数: {param_keys}")
    
    def get_all_category_params(self) -> Dict[str, Dict[str, Any]]:
        """
        获取所有分区的参数
        
        Returns:
            所有分区参数的字典
        """
        return self.category_params.copy()
    
    def clear_all_category_params(self):
        """
        清除所有分区的参数
        """
        self.category_params.clear()
        logger.debug("清除所有分区参数")
    
    def build_request_params(self, category_type: str, base_params: Dict[str, Any] = None) -> Dict[str, Any]:
        """
        构建请求参数，结合基础参数、默认参数和分区特定参数
        
        参数优先级（从低到高）：
        1. 默认参数（default_params） - 最低优先级
        2. 分区特定参数（category_params）
        3. 基础参数（base_params） - 最高优先级
        
        Args:
            category_type: 分区类型
            base_params: 基础参数
            
        Returns:
            完整的请求参数字典
        """
        if base_params is None:
            base_params = {}
            
        # 按照优先级顺序合并参数: 默认参数 -> 分区参数 -> 基础参数
        # 注意：这里的默认参数是空的，不会覆盖实际的用户凭证
        result_params = self.default_params.copy()
        result_params.update(self.category_params.get(category_type, {}))
        result_params.update(base_params)
        
        logger.debug(f"构建分区 {category_type} 的请求参数: {result_params}")
        return result_params
    
    def save_to_file(self, filepath: str):
        """
        将分区参数保存到文件
        
        Args:
            filepath: 保存文件路径
        """
        try:
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(self.category_params, f, ensure_ascii=False, indent=2)
            logger.debug(f"分区参数已保存到 {filepath}")
        except Exception as e:
            logger.error(f"保存分区参数到文件失败: {e}")
    
    def load_from_file(self, filepath: str):
        """
        从文件加载分区参数
        
        Args:
            filepath: 加载文件路径
        """
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                self.category_params = json.load(f)
            logger.debug(f"从 {filepath} 加载分区参数成功")
        except FileNotFoundError:
            logger.warning(f"分区参数文件 {filepath} 不存在，使用默认配置")
        except Exception as e:
            logger.error(f"从文件加载分区参数失败: {e}")