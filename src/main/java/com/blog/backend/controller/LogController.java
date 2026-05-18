package com.blog.backend.controller;

import com.blog.backend.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    @GetMapping
    public Result<List<String>> getLogs(@RequestParam(defaultValue = "100") int lines) {
        File logFile = new File("logs/app.log");
        if (!logFile.exists()) {
            return Result.success(Collections.singletonList("Log file not found at " + logFile.getAbsolutePath()));
        }

        List<String> logLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logLines.add(line);
            }
        } catch (IOException e) {
            return Result.error("Error reading log file: " + e.getMessage());
        }

        int size = logLines.size();
        if (size <= lines) {
            return Result.success(logLines);
        }
        return Result.success(logLines.subList(size - lines, size));
    }
}
