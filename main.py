from core.api_client import APIClient
from core.session_manager import SessionManager
from core.tui import TUI
from services.auth_service import AuthService
from services.user_service import UserService
from utils.logger import setup_logger
from config.settings import settings

# 使用模块名创建logger实例
logger = setup_logger(__name__)

def handle_user_info(client):
    """处理用户信息查看功能"""
    logger.info("=== 获取用户信息 ===")
    user_service = UserService(client)
    user = user_service.get_user_info()
    
    # 展示用户信息
    if user:
        print("\n获取到的用户信息:")
        print(f"{user}")
    else:
        print("\n获取用户信息失败")

def main():
    logger.info("=== 应用启动 ===")
    
    # 初始化API客户端和会话管理器
    client = APIClient()
    session_manager = SessionManager()
    
    # 尝试加载保存的会话
    saved_session = session_manager.load_session()
    logger.debug("从会话文件加载的数据: %s", saved_session)
    if saved_session:
        logger.info("发现保存的会话信息，正在恢复...")
        client.update_dynamic_params(**saved_session)
        logger.info("会话恢复成功，当前客户端参数: %s", client.param_manager.dynamic_params)
        
        # 验证会话是否仍然有效
        logger.info("=== 验证会话有效性 ===")
        user_service = UserService(client)
        try:
            user = user_service.get_user_info()
            if user:
                logger.info("会话验证成功，无需重新登录，用户mid: %s", user.uid)
            else:
                logger.warning("会话验证失败，用户信息获取为空")
                # 清除无效的会话信息
                session_manager.clear_session()
                # 继续执行登录流程
        except Exception as e:
            logger.warning("会话已失效，需要重新登录: %s", str(e))
            # 清除无效的会话信息
            session_manager.clear_session()
            # 继续执行登录流程
    else:
        logger.info("未找到保存的会话信息")
    
    # 如果没有有效会话，则执行登录流程
    if not session_manager.load_session():
        logger.info("=== 用户登录 ===")
        auth = AuthService(client)
        
        # 从环境变量获取账号密码（安全方式）
        phone = settings.LOGIN_PHONE
        password = settings.LOGIN_PASSWORD
        
        if not phone or not password:
            logger.error("请设置 LOGIN_PHONE 和 LOGIN_PASSWORD 环境变量")
            return
        
        # 执行登录
        if auth.login(phone, password):
            logger.info("登录成功！")
            
            # 保存会话信息
            current_params = client.param_manager.dynamic_params
            logger.debug("准备保存的会话参数: %s", current_params)
            session_manager.save_session(current_params)
            
        else:
            print("登录失败，请检查账号密码")
            return
    
    # 启动TUI界面
    logger.info("=== 启动TUI界面 ===")
    logger.debug("TUI启动前的客户端参数: %s", client.param_manager.dynamic_params)
    try:
        tui = TUI(client)
        tui.run()
    except KeyboardInterrupt:
        print("\n\n程序被用户中断，正在退出...")
    except Exception as e:
        logger.exception("程序运行异常: %s", str(e))
        print(f"发生未预期的错误: {e}")

if __name__ == "__main__":
    main()
