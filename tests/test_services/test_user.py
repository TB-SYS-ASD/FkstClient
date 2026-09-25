"""
用户服务测试
"""

import unittest
from unittest.mock import Mock, patch

from core.api_client import APIClient
from models.user import User
from services.user_service import UserService


class TestUserService(unittest.TestCase):
    
    def setUp(self):
        """测试前准备"""
        self.api_client = Mock(spec=APIClient)
        self.user_service = UserService(self.api_client)
        
    def test_get_user_info_success(self):
        """测试成功获取用户信息"""
        # 模拟API响应
        mock_response = {
            "res": 0,
            "member": {
                "uid": "12345",
                "nick_name": "test_user",
                "logo": "avatar_url",
                "phone_number": "13800138000",
                "email": "test@example.com",
                "is_vip": True,
                "vip_expire_time": "2023-12-31"
            }
        }
        self.api_client.request.return_value = mock_response
        
        # 调用被测试的方法
        user = self.user_service.get_user_info()
        
        # 验证结果
        self.assertIsNotNone(user)
        self.assertEqual(user.uid, "12345")
        self.assertEqual(user.nickname, "test_user")
        self.assertEqual(user.avatar, "avatar_url")
        self.assertEqual(user.phone, "13800138000")
        self.assertEqual(user.email, "test@example.com")
        self.assertTrue(user.is_vip)
        self.assertEqual(user.vip_expire_time, "2023-12-31")
        
    def test_get_user_info_failure(self):
        """测试获取用户信息失败"""
        # 模拟API响应失败
        mock_response = {
            "res": 1,
            "error": "User not found"
        }
        self.api_client.request.return_value = mock_response
        
        # 调用被测试的方法
        user = self.user_service.get_user_info()
        
        # 验证结果
        self.assertIsNone(user)
        
    def test_user_to_dict(self):
        """测试用户对象转字典"""
        user = User(
            uid="12345",
            nickname="test_user",
            avatar="avatar_url",
            phone="13800138000",
            email="test@example.com",
            is_vip=True,
            vip_expire_time="2023-12-31"
        )
        
        user_dict = user.to_dict()
        
        expected_dict = {
            "uid": "12345",
            "nick_name": "test_user",
            "logo": "avatar_url",
            "phone_number": "13800138000",
            "email": "test@example.com",
            "is_vip": True,
            "vip_expire_time": "2023-12-31"
        }
        
        self.assertEqual(user_dict, expected_dict)
        
    def test_user_from_dict(self):
        """测试从字典创建用户对象"""
        user_data = {
            "uid": "12345",
            "nick_name": "test_user",
            "logo": "avatar_url",
            "phone_number": "13800138000",
            "email": "test@example.com",
            "is_vip": True,
            "vip_expire_time": "2023-12-31"
        }
        
        user = User.from_dict(user_data)
        
        self.assertEqual(user.uid, "12345")
        self.assertEqual(user.nickname, "test_user")
        self.assertEqual(user.avatar, "avatar_url")
        self.assertEqual(user.phone, "13800138000")
        self.assertEqual(user.email, "test@example.com")
        self.assertTrue(user.is_vip)
        self.assertEqual(user.vip_expire_time, "2023-12-31")
        
    def test_is_authenticated(self):
        """测试用户认证状态检查"""
        # 测试已认证用户
        user_with_id = User(uid="12345", nickname="test", avatar="avatar")
        self.assertTrue(user_with_id.is_authenticated())
        
        # 测试未认证用户
        user_without_id = User(uid="", nickname="test", avatar="avatar")
        self.assertFalse(user_without_id.is_authenticated())
        
    def test_user_string_representation(self):
        """测试用户对象的字符串表示"""
        user = User(uid="12345", nickname="test_user", avatar="avatar_url")
        
        self.assertEqual(str(user), "User(uid=12345, nickname=test_user)")
        self.assertIn("User(uid='12345'", repr(user))
        self.assertIn("nickname='test_user'", repr(user))
        self.assertIn("avatar='avatar_url'", repr(user))


if __name__ == '__main__':
    unittest.main()