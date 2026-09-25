"""
    我们之前设计的`APIClient`和`ParamManager`会自动处理公共参数和签名。
    在`api_endpoints.py`中定义端点时，只需要指定该API特有的参数（即每个API独有的参数，比如登录时的`phone_number`和`password`，获取试卷列表时的`page`等）。
    公共参数（如`app_key`, `device_imei`, `call_id`等）会在请求前由`ParamManager`自动拼接上去，然后生成签名（`api_sig`）并添加到请求参数中。
    因此，在定义API端点时，你只需要在`required_params`中列出该API特有的参数（也就是除了公共参数之外必须传递的参数）。
    在调用`client.request`方法时，也只需传入这些特有参数和任何可选的特有参数。
    
    新增支持:
    - base_url: 可选，指定该API使用的BASE_URL，如果不指定则使用默认的settings.BASE_URL
    - skip_device_params: 可选，布尔值，如果为True则跳过设备参数
    - skip_dynamic_params: 可选，布尔值，如果为True则跳过所有动态参数
    - exclude_dynamic_params: 可选，列表，指定需要排除的特定动态参数键名（如["openid"]）
"""

API_ENDPOINTS = {
    "LOGIN": {
        "method": "POST",
        "path": "/STAccountLogin3",
        "required_params": ["phone_number", "password", "verify_type"],
        "default_params": {
            "verify_type": "2"
        }
    },
    "GET_USER_INFO": {  # 获取我的信息
        "method": "POST",
        "path": "/GetShuaTiTotal6",
        "required_params": ["modetype", "system_version", "height", "width"],
        "default_params": {
            "modetype": "normal",
            "system_version": "Android10",
            "height": "1920",
            "width": "1080",
        }
    },
    "GET_MY_PROFILE": {  # 获取我的资料
        "method": "POST",
        "path": "/GetSTMyData5",
        "required_params": ["all_black_member"],
        "default_params": {
            "all_black_member": "1"
        }
    },
    "GET_USER_DATA": {  # 获取用户资料
        "method": "POST",
        "path": "/GetSTUserData",
        "required_params": ["home_id"],
    },
    "GET_DISCOVER_NOTE": {
        "method": "POST",
        "path": "/GetDiscoverTagNotes2",
        "required_params": ["type", "start_time", "page"],
    },
    "GET_FOLLOW_USER_NOTES":{
        "method": "POST",
        "path": "/GetFollowUserNotes2",
        "required_params": ["min_time", "page"],

    },
    # 获取笔记的端点，使用settings.GET_NOTE_PARAMS中的大部分参数
    "GET_NOTE": {
        "method": "GET",
        "path": "/shuati/discoverNoteDetail-v5",
        "base_url": "https://www.yaerxing.com",
        "required_params": ["id"],
        "skip_device_params": True,   # 跳过标准设备参数
        "exclude_dynamic_params": ["openid"],  # 排除openid参数
        "default_params": {
            "adolescent_model": "0",
            "font_size": "2",
            "version": "2"
        }
    },
    "GET_COMMENT_BY_NID": {
        "method": "POST",
        "path": "/GetSTCommentByNid2",
        "required_params": ["nid", "order_type", "start_time", "ct", "page"],
        "default_params": {
            "order_type": "1",
            "page": "0"
        }

    },
    "GET_COMMENT_BY_FID": {
        "method": "POST",
        "path": "/GetSTCommentByFid2",
        "required_params": ["fid", "object_type", "start_time", "ct", "page"],
        "default_params": {
            "object_type": "2",
            "page": "0"
        }

    },
    "GET_ST_NOTICES": {
        "method": "POST",
        "path": "/GetSTNotices2",
        "required_params": ["type", "page"],
        "default_params": {
            "type": "2",
            "page": "0"
        }
    },
    "GET_USER_NOTES": {
        "method": "POST",
        "path": "/GetSTUserNotes2",
        "required_params": ["type", "home_id","page"],
        "default_params": {
            "type": "0",
            "page": "0"
        }
    },
    "SET_NOTE_COMMENT": {  # 发表笔记评论
        "method": "POST",
        "path": "/SetSTNoteComment1",
        "required_params": ["content", "fid","nid"],
        "default_params": {
            "fid": "0",
        }
    },
    
    # 示例：特殊API端点配置，使用不同的BASE_URL且跳过部分参数
    "SPECIAL_API": {
        "method": "POST",
        "path": "/special/path",
        "base_url": "https://special-api.example.com",  # 不同的BASE_URL
        "required_params": ["special_param"],
        "skip_device_params": True,   # 跳过设备参数
        "exclude_dynamic_params": ["openid"],  # 只排除特定动态参数
        "default_params": {
            "special_default": "value"
        }
    }
}