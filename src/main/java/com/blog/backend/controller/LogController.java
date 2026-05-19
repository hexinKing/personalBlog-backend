package com.blog.backend.controller;

import com.blog.backend.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final int MAX_LINES = 1000;

    @GetMapping
    public Result<List<String>> getLogs(@RequestParam(defaultValue = "100") int lines) {
        File logFile = new File("logs/app.log");
        if (!logFile.exists()) {
            return Result.success(Collections.singletonList("Log file not found at " + logFile.getAbsolutePath()));
        }

        int safeLines = Math.max(1, Math.min(lines, MAX_LINES));
        try {
            return Result.success(readTailLines(logFile, safeLines));
        } catch (IOException e) {
            log.warn("读取日志失败", e);
            return Result.error("Error reading log file: " + e.getMessage());
        }
    }

    private List<String> readTailLines(File file, int lines) throws IOException {
        Deque<String> result = new LinkedList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long pointer = raf.length() - 1;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            boolean atFileEnd = true;

            while (pointer >= 0 && result.size() < lines) {
                raf.seek(pointer--);
                int read = raf.read();
                if (read == '\n') {
                    if (buffer.size() == 0 && atFileEnd) {
                        continue;
                    }
                    result.addFirst(decodeReversed(buffer));
                    buffer.reset();
                    atFileEnd = false;
                } else if (read != '\r') {
                    buffer.write(read);
                    atFileEnd = false;
                }
            }

            if (buffer.size() > 0) {
                result.addFirst(decodeReversed(buffer));
            }
        }
        return new ArrayList<>(result);
    }

    private String decodeReversed(ByteArrayOutputStream buffer) {
        byte[] bytes = buffer.toByteArray();
        for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
            byte temp = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = temp;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
