package com.beyond.teenkiri.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 3, // 3MB
        maxFileSize = 1024 * 1024 * 1024 * 3, // 3GB
        maxRequestSize = 1024 * 1024 * 1024 * 3) // 3GB
public class FileUploadServlet extends HttpServlet {

    private static final String UPLOAD_DIR = "uploads";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            for (Part part : request.getParts()) {
                String fileName = Paths.get(part.getSubmittedFileName()).getFileName().toString();
                InputStream fileContent = part.getInputStream();
                // 업로드 처리 로직
            }
            response.getWriter().println("File uploaded successfully!");
        } catch (IOException e) {
            // 파일 크기 초과 예외 처리
            response.getWriter().println("File size exceeds the allowed limit (3GB).");
        }
    }
}