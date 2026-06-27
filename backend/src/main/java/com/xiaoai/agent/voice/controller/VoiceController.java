package com.xiaoai.agent.voice.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.voice.service.VoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 语音功能控制器
 */
@RestController
@RequestMapping("/api/v1/voice")
@CrossOrigin(origins = "*")
public class VoiceController {

    private final VoiceService voiceService;

    @Autowired
    public VoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    /**
     * 语音转文字
     */
    @PostMapping("/speech-to-text")
    public ApiResponse<Map<String, Object>> speechToText(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "language", defaultValue = "zh-CN") String language) {

        if (audioFile.isEmpty()) {
            return ApiResponse.error("Audio file is required");
        }

        try {
            String text = voiceService.speechToText(audioFile, language);

            Map<String, Object> result = new HashMap<>();
            result.put("text", text);
            result.put("language", language);
            result.put("audioSize", audioFile.getSize());

            return ApiResponse.success(result);

        } catch (Exception e) {
            return ApiResponse.error("Speech to text failed: " + e.getMessage());
        }
    }

    /**
     * 文字转语音
     */
    @PostMapping("/text-to-speech")
    public ResponseEntity<byte[]> textToSpeech(
            @RequestParam("text") String text,
            @RequestParam(value = "language", defaultValue = "zh-CN") String language,
            @RequestParam(value = "voice", defaultValue = "female") String voice) {

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            byte[] audioData = voiceService.textToSpeech(text, language, voice);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/wav"));
            headers.setContentDispositionFormData("filename", "speech.wav");

            return new ResponseEntity<>(audioData, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 检查语音服务是否可用
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", voiceService.isAvailable());
        status.put("supportedLanguages", voiceService.getSupportedLanguages());

        return ApiResponse.success(status);
    }

    /**
     * 获取支持的语言列表
     */
    @GetMapping("/languages")
    public ApiResponse<String[]> getSupportedLanguages() {
        return ApiResponse.success(voiceService.getSupportedLanguages());
    }
}
