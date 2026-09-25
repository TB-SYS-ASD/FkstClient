import hashlib
from config import settings
from utils.logger import setup_logger

logger = setup_logger(__name__)

def generate_signature(params: dict) -> str:
    """
    生成API签名。

    该函数通过给定的参数字典生成API签名。它首先对参数键进行排序（跳过'api_sig'键），
    然后拼接键和值以生成一个字符串。之后，它添加一个密钥，并基于这个字符串生成一个MD5哈希值作为签名。

    参数:
    params (dict): 包含API请求参数的字典。

    返回:
    str: 生成的API签名的MD5哈希值。
    """
    # 定义常量
    SKIP_KEYS = ["api_sig"]
    SECRET_KEY = "9bldwb2d5d02e81h"
    TEXT_PATTERN = "f0{call_id}com.yaerxing.fkst{app_c}F.K*$t"

    # 检查必要参数是否存在
    if "call_id" not in params:
        raise ValueError("Missing required parameter 'call_id'")

    # 按键排序并拼接键和值（使用列表推导式优化）
    result = "".join(
        f"{key}{value}" for key, value in sorted(params.items())
        if key not in SKIP_KEYS
    )

    # 添加密钥到结果字符串
    result += SECRET_KEY

    # 获取call_id的后四位
    call_id_suffix = params["call_id"][-4:]

    # 构造需要进行MD5哈希的文本字符串
    text = TEXT_PATTERN.format(call_id=call_id_suffix, app_c=settings.settings.DEVICE_PARAMS["app_c"])
    
    # 计算MD5哈希值
    md5 = hashlib.md5()
    md5.update(text.encode("utf-8"))
    md5_hash = md5.hexdigest()

    # 获取文本字符串的MD5哈希值，并截取特定部分
    text_hash = md5_hash[5:21]

    # 将文本哈希值添加到结果字符串
    result += text_hash

    # 生成最终的API签名的MD5哈希值
    api_sig = hashlib.md5()
    api_sig.update(result.encode("utf-8"))
    api_sig = api_sig.hexdigest().upper()

    # 返回API签名
    return api_sig

def get_note_signature(params: dict) -> str:
    """
    为GET_NOTE API生成特殊签名，不包含call_id参数
    
    参数:
    params (dict): 包含API请求参数的字典。

    返回:
    str: 生成的API签名的MD5哈希值。
    """
    # 定义要跳过的键
    SKIP_KEYS = ["api_sig"]
    SECRET_KEY = "9bldwb2d5d02e81h"
    TEXT_PATTERN = "f0{timestamp}{app_v}F.K*$t"

    # 按键排序并拼接键和值（使用列表推导式优化）
    result = "".join(
        f"{key}{value}" for key, value in sorted(params.items())
        if key not in SKIP_KEYS
    )

    # 添加密钥到结果字符串
    result += SECRET_KEY

    logger.debug(result)

     # 获取timestamp的后四位
    timestamp_suffix = params["timestamp"][-4:]

    # 构造需要进行MD5哈希的文本字符串
    text = TEXT_PATTERN.format(timestamp=timestamp_suffix, app_v=settings.settings.GET_NOTE_PARAMS["app_v"])
    logger.debug(text)
    # 计算MD5哈希值
    md5 = hashlib.md5()
    md5.update(text.encode("utf-8"))
    md5_hash = md5.hexdigest()

    # 获取文本字符串的MD5哈希值，并截取特定部分
    text_hash = md5_hash[5:21]

    # 将文本哈希值添加到结果字符串
    result += text_hash

    # 生成最终的API签名的MD5哈希值
    api_sig = hashlib.md5()
    api_sig.update(result.encode("utf-8"))
    api_sig = api_sig.hexdigest().upper()

    # 返回API签名
    return api_sig

def old_sig(params: dict) -> str:
    # 定义要跳过的键
    skip_keys = ["api_sig"]

    # 按键排序并拼接键和值，跳过指定的键
    result = ""
    for key, value in sorted(params.items()):
        if key in skip_keys:
            continue
        result += f"{key}{value}"

    # 添加密钥到结果字符串
    result += "9bldwb2d5d02e81h"

    print(result)

    # 获取call_id的后四位
    call_id = params["call_id"][-4:]

    # 构造需要进行MD5哈希的文本字符串
    text = f"f0{call_id}com.yaerxing.fkst1.16.21F.K*$t"
    print(f"加密前：{text}")
    # 计算给定字符串的MD5哈希值。
    md5 = hashlib.md5()
    md5.update(text.encode("utf-8"))
    md5 = md5.hexdigest()

    print(f"加密后：{md5}")
    # 获取文本字符串的MD5哈希值，并截取特定部分
    text_hash = md5[5:21]
    print(text_hash)
    # 将文本哈希值添加到结果字符串
    result += text_hash

    # 生成最终的API签名的MD5哈希值
    api_sig = hashlib.md5()
    api_sig.update(result.encode("utf-8"))
    api_sig = api_sig.hexdigest()
    # 将哈希值转换为大写
    api_sig = api_sig.upper()
    print(f"加密后2：{api_sig}")
    # 返回API签名
    return api_sig

def get_comment_signature(params: dict) -> str:
    """
    为SET_NOTE_COMMENT API生成特殊签名，只拼接api_key、call_id、openid三个参数
    
    参数:
    params (dict): 包含API请求参数的字典。

    返回:
    str: 生成的API签名的MD5哈希值。
    """
    # 定义常量
    SECRET_KEY = "9bldwb2d5d02e81h"
    TEXT_PATTERN = "f0{call_id}com.yaerxing.fkst{app_c}F.K*$t"
    
    # 检查必要参数是否存在
    if "call_id" not in params:
        raise ValueError("Missing required parameter 'call_id'")
    if "api_key" not in params:
        raise ValueError("Missing required parameter 'api_key'")
    if "openid" not in params:
        raise ValueError("Missing required parameter 'openid'")
    
    # 只拼接api_key、call_id、openid三个参数
    # 按照固定顺序拼接：api_key + call_id + openid
    result = f"api_key{params['api_key']}call_id{params['call_id']}openid{params['openid']}"
    
    # 添加密钥到结果字符串
    result += SECRET_KEY
    
    # 获取call_id的后四位
    call_id_suffix = params["call_id"][-4:]
    
    # 构造需要进行MD5哈希的文本字符串
    text = TEXT_PATTERN.format(call_id=call_id_suffix, app_c=settings.settings.DEVICE_PARAMS["app_c"])
    
    # 计算MD5哈希值
    md5 = hashlib.md5()
    md5.update(text.encode("utf-8"))
    md5_hash = md5.hexdigest()
    
    # 获取文本字符串的MD5哈希值，并截取特定部分
    text_hash = md5_hash[5:21]
    
    # 将文本哈希值添加到结果字符串
    result += text_hash
    
    # 生成最终的API签名的MD5哈希值
    api_sig = hashlib.md5()
    api_sig.update(result.encode("utf-8"))
    api_sig = api_sig.hexdigest().upper()
    
    logger.debug(f"评论签名生成: 原始字符串={result}, 签名={api_sig}")
    
    # 返回API签名
    return api_sig