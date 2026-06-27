package com.xiaoai.agent.voice.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音服务接口
 * 提供语音转文字和文字转语音功能
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface VoiceService {

    /**
     * 语音转文字
     * @param audioFile 音频文件
     * @param language 语言代码（如：zh-CN, en-US）
     * @return 转换后的文字
     */
    String speechToText(MultipartFile audioFile, String language);

    /**
     * 文字转语音
     * @param text 要转换的文字
     * @param language 语言代码
     * @param voice 语音类型（如：male, female）
     * @return 生成的音频文件字节数组
     */
    byte[] textToSpeech(String text, String language, String voice);

    /**
     * 检查语音服务是否可用
     */
    boolean isAvailable();

    /**
     * 获取支持的语言列表
     */
    String[] getSupportedLanguages();
}
