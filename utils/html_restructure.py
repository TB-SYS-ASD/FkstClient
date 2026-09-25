"""
HTML重构工具模块
用于动态重构从API获取的HTML内容，移除无用逻辑并添加自定义样式
"""

import re
from bs4 import BeautifulSoup
from typing import Optional, List
from utils.logger import setup_logger

logger = setup_logger(__name__)


class HTMLRestructurer:
    """
    HTML内容重构器
    """
    
    def __init__(self):
        # 默认样式模板
        self.default_styles = """
        <style>
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif;
                line-height: 1.6;
                color: #333;
                max-width: 800px;
                margin: 0 auto;
                padding: 20px;
                background-color: #f8f9fa;
            }
            
            .note-container {
                background: white;
                border-radius: 10px;
                box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
                padding: 25px;
                margin-bottom: 20px;
            }
            
            .note-header {
                border-bottom: 1px solid #eee;
                padding-bottom: 15px;
                margin-bottom: 20px;
            }
            
            .note-title {
                font-size: 24px;
                font-weight: bold;
                color: #2c3e50;
                margin: 0 0 10px 0;
            }
            
            .note-meta {
                display: flex;
                justify-content: space-between;
                color: #7f8c8d;
                font-size: 14px;
            }
            
            .note-content {
                margin-bottom: 25px;
                font-size: 16px;
            }
            
            .note-content img {
                max-width: 100%;
                height: auto;
                border-radius: 5px;
                margin: 10px 0;
            }
            
            .note-content p {
                margin: 0 0 15px 0;
            }
            
            .note-footer {
                background-color: #f1f2f3;
                padding: 15px;
                border-radius: 5px;
                font-size: 14px;
                color: #7f8c8d;
            }
            
            .prohibited-word {
                display: none;
            }
            
            /* 图片轮播样式 */
            .swiper-container {
                position: relative;
                margin-bottom: 20px;
                overflow: hidden;
            }
            
            .swiper-wrapper {
                display: flex;
                transition: transform 0.3s ease;
            }
            
            .swiper-slide {
                flex: 0 0 100%;
                position: relative;
            }
            
            .swiper-slide img {
                width: 100%;
                height: auto;
                display: block;
                border-radius: 5px;
            }
            
            .swiper-index {
                position: absolute;
                bottom: 10px;
                right: 10px;
                background: rgba(0, 0, 0, 0.5);
                color: white;
                padding: 2px 8px;
                border-radius: 10px;
                font-size: 12px;
                z-index: 10;
            }
            
            .swiper-pagination {
                text-align: center;
                margin-top: 10px;
            }
            
            .swiper-pagination span {
                display: inline-block;
                width: 8px;
                height: 8px;
                border-radius: 50%;
                background: #ccc;
                margin: 0 4px;
                cursor: pointer;
            }
            
            .swiper-pagination .active {
                background: #007AFF;
            }
        </style>
        """
    
    def restructure_note_html(self, html_content: str, title: str = "", 
                              publish_time: str = "", location: str = "", 
                              image_urls: List[str] = None) -> str:
        """
        重构笔记HTML内容
        
        Args:
            html_content: 原始HTML内容
            title: 笔记标题（如果为空则尝试从HTML中提取）
            publish_time: 发布时间（如果为空则尝试从HTML中提取）
            location: 发布位置（如果为空则尝试从HTML中提取）
            image_urls: 图片URL列表（用于构建图片轮播）
            
        Returns:
            重构后的HTML内容
        """
        logger.debug(f"restructure_note_html接收到的参数 - title: {title}, publish_time: {publish_time}, location: {location}")
        logger.debug(f"restructure_note_html接收到的image_urls: {image_urls}")
        
        # 使用BeautifulSoup解析HTML
        soup = BeautifulSoup(html_content, 'html.parser')
        
        # 尝试从HTML中提取标题
        if not title:
            title_elem = soup.find('div', class_='note-title')
            if title_elem:
                title = title_elem.get_text(strip=True)
            else:
                title = "笔记"
        
        # 尝试从HTML中提取时间和位置信息
        if not publish_time or not location:
            date_elem = soup.find('div', class_='rel-date')
            if date_elem:
                spans = date_elem.find_all('span')
                if spans:
                    # 第一个span通常是发布时间
                    if not publish_time and len(spans) >= 1:
                        publish_time = spans[0].get_text(strip=True).replace('发布于：', '')
                    # 第二个span通常是位置信息
                    if not location and len(spans) >= 2:
                        location_text = spans[1].get_text(strip=True)
                        if location_text.startswith('来自'):
                            location = location_text[2:]  # 去掉"来自"前缀
        
        # 提取主要内容
        content_div = soup.find('div', class_='note-content')
        if not content_div:
            content_div = soup.find('div')
            
        # 获取内容HTML，但排除标题和元数据部分
        content_html = ""
        if content_div:
            # 获取内容HTML，但排除可能的隐藏属性
            content_html = str(content_div)
        else:
            # 如果没有找到note-content，使用整个body内容
            body = soup.find('body')
            if body:
                content_html = ''.join([str(child) for child in body.children])
            else:
                content_html = str(soup)
        
        # 移除可能存在的hidden属性，确保内容可见
        content_html = content_html.replace(' hidden=', ' ')
        
        # 构建图片轮播HTML
        image_carousel_html = ""
        if image_urls:
            image_carousel_html = self._build_image_carousel(image_urls)
            logger.debug(f"构建的图片轮播HTML: {image_carousel_html}")
        else:
            logger.debug("未提供image_urls或为空")
        
        # 创建新的HTML结构
        restructured_html = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>{title}</title>
            {self.default_styles}
        </head>
        <body>
            <div class="note-container">
                <div class="note-header">
                    <h1 class="note-title">{title}</h1>
                    <div class="note-meta">
                        <span>发布于：{publish_time if publish_time else '未知时间'}</span>
                        <span>{'来自' + location if location else ''}</span>
                    </div>
                </div>
                
                {image_carousel_html}
                
                <div class="note-content">
                    {content_html}
                </div>
                
                <div class="note-footer">
                    声明: 内容版权归作者所有，并不反映任何 疯狂刷题 之意见及观点
                </div>
            </div>
            
            <script>
            // 简单的轮播图交互脚本
            document.addEventListener('DOMContentLoaded', function() {{
                const wrapper = document.querySelector('.swiper-wrapper');
                const paginationDots = document.querySelectorAll('.swiper-pagination span');
                const indexValue = document.getElementById('index-value');
                let currentIndex = 0;
                const slideCount = {len(image_urls) if image_urls else 0};
                
                // 只有当图片数量大于1时才启用轮播功能
                if (slideCount > 1 && paginationDots.length > 0 && wrapper) {{
                    // 为分页点添加点击事件
                    paginationDots.forEach((dot, index) => {{
                        dot.addEventListener('click', () => {{
                            goToSlide(index);
                        }});
                    }});
                }}
                
                function goToSlide(index) {{
                    // 更新当前索引
                    currentIndex = index;
                    
                    // 移动轮播图
                    if (wrapper) {{
                        const translateX = -currentIndex * 100;
                        wrapper.style.transform = `translateX(${{translateX}}%)`;
                    }}
                    
                    // 更新索引显示
                    if (indexValue) {{
                        indexValue.textContent = currentIndex + 1;
                    }}
                    
                    // 更新分页点激活状态
                    paginationDots.forEach((dot, i) => {{
                        if (i === currentIndex) {{
                            dot.classList.add('active');
                        }} else {{
                            dot.classList.remove('active');
                        }}
                    }});
                }}
                
                // 初始化第一个分页点为激活状态
                if (slideCount > 1 && paginationDots.length > 0) {{
                    paginationDots[0].classList.add('active');
                }}
            }});
            </script>
        </body>
        </html>
        """
        
        logger.debug(f"重构后的HTML长度: {len(restructured_html)}")
        return restructured_html
    
    def _build_image_carousel(self, image_urls: List[str]) -> str:
        """
        构建图片轮播HTML
        
        Args:
            image_urls: 图片URL列表
            
        Returns:
            图片轮播HTML字符串
        """
        logger.debug(f"_build_image_carousel接收到的image_urls: {image_urls}")
        if not image_urls or len(image_urls) == 0:
            logger.debug("_build_image_carousel: image_urls为空或长度为0")
            return ""
        
        # 如果只有一张图片，不添加轮播效果，因为图片已经在正文中存在
        if len(image_urls) == 1:
            logger.debug("_build_image_carousel: 只有一张图片，不生成轮播")
            return ""
        
        # 构建轮播项
        slides_html = ""
        for i, url in enumerate(image_urls):
            slides_html += f"""
                <div class="swiper-slide">
                    <img src="{url}" alt="笔记图片 {i+1}">
                </div>
            """
        
        # 构建分页指示器
        pagination_html = ""
        for i in range(len(image_urls)):
            pagination_html += f'<span data-index="{i}"></span>'
        
        # 构建完整的轮播HTML
        carousel_html = f"""
        <div class="swiper-container">
            <div class="swiper-wrapper">
                {slides_html}
            </div>
            <div class="swiper-index">
                <span id="index-value">1</span>
                <span>/{len(image_urls)}</span>
            </div>
            <div class="swiper-pagination">
                {pagination_html}
            </div>
        </div>
        """
        
        logger.debug(f"_build_image_carousel生成的HTML: {carousel_html}")
        return carousel_html
    
    def restructure_generic_html(self, html_content: str, title: str = "内容") -> str:
        """
        重构通用HTML内容
        
        Args:
            html_content: 原始HTML内容
            title: 页面标题
            
        Returns:
            重构后的HTML内容
        """
        # 清理原始HTML中的脚本和不必要的元素
        cleaned_html = self._clean_html(html_content)
        
        # 创建新的HTML结构
        restructured_html = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>{title}</title>
            {self.default_styles}
        </head>
        <body>
            <div class="note-container">
                <div class="note-content">
                    {cleaned_html}
                </div>
            </div>
        </body>
        </html>
        """
        
        return restructured_html
    
    def _clean_html(self, html_content: str) -> str:
        """
        清理HTML内容，移除脚本和不必要的元素
        
        Args:
            html_content: 原始HTML内容
            
        Returns:
            清理后的HTML内容
        """
        # 使用BeautifulSoup清理HTML
        soup = BeautifulSoup(html_content, 'html.parser')
        
        # 移除脚本标签
        for script in soup.find_all('script'):
            script.decompose()
            
        # 移除样式标签
        for style in soup.find_all('style'):
            style.decompose()
            
        # 移除链接标签
        for link in soup.find_all('link'):
            link.decompose()
            
        return str(soup)


# 创建全局实例
html_restructurer = HTMLRestructurer()


def restructure_html(content: str, content_type: str = "note", **kwargs) -> str:
    """
    通用HTML重构函数
    
    Args:
        content: HTML内容（已解密替换后的）
        content_type: 内容类型 ("note" 或 "generic")
        **kwargs: 其他参数，如title, publish_time, location等
        
    Returns:
        重构后的HTML内容
    """
    logger.debug(f"restructure_html接收到的参数 - content_type: {content_type}, kwargs: {kwargs}")
    if content_type == "note":
        result = html_restructurer.restructure_note_html(
            content, 
            kwargs.get('title', ''),
            kwargs.get('publish_time', ''),
            kwargs.get('location', ''),
            kwargs.get('image_urls', [])
        )
        logger.debug(f"restructure_html返回的note类型HTML长度: {len(result)}")
        return result
    else:
        result = html_restructurer.restructure_generic_html(
            content,
            kwargs.get('title', '内容')
        )
        logger.debug(f"restructure_html返回的generic类型HTML长度: {len(result)}")
        return result
