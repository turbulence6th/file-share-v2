package com.turbulence6th.fileshare.controller;

import com.turbulence6th.fileshare.dto.*;
import org.apache.tomcat.util.http.fileupload.FileItemIterator;
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;

@RestController
@RequestMapping("/file")
public class FileController {

    private final Map<String, FileShareWrapper> shareMap;

    private final SimpMessagingTemplate template;

    public FileController(Map<String, FileShareWrapper> shareMap, SimpMessagingTemplate template) {
        this.shareMap = shareMap;
        this.template = template;
    }

    @RequestMapping(path = "/share", method = RequestMethod.POST)
    public ShareResponseDTO share(@RequestBody ShareRequestDTO request) {
        String shareHash = UUID.randomUUID().toString();
        shareMap.put(shareHash, FileShareWrapper.builder()
                .filename(request.getFilename())
                .size(request.getSize())
                .streamMap(new HashMap<>())
            .build());
        return ShareResponseDTO.builder()
                .shareHash(shareHash)
                .build();
    }

    @RequestMapping(path = "/unshare", method = RequestMethod.POST)
    public void unshare(@RequestBody UnshareRequestDTO request) {
        FileShareWrapper fileShareWrapper = shareMap.get(request.getShareHash());
        for (FileStreamWrapper fileStreamWrapper : fileShareWrapper.getStreamMap().values()) {
            forceClose(fileStreamWrapper.getInputStream());
            forceClose(fileStreamWrapper.getOutputStream());
            fileStreamWrapper.setStatus(-1);
            if (fileStreamWrapper.getLatch() != null) {
                fileStreamWrapper.getLatch().countDown();
            }
        }
        shareMap.remove(request.getShareHash());
    }

    private void forceClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {

        }
    }

    @RequestMapping(path = "/upload/{shareHash}/{streamHash}", method = RequestMethod.POST)
    public void upload(@PathVariable String shareHash, @PathVariable String streamHash, HttpServletRequest request) throws IOException, FileUploadException, BrokenBarrierException, InterruptedException {
        FileStreamWrapper fileStreamWrapper = shareMap.get(shareHash).getStreamMap().get(streamHash);
        try {
            FileItemIterator iterator = getItemIterator(request);
            if (iterator.hasNext()) {
                FileItemStream item = iterator.next();

                if (!item.isFormField()) {
                    InputStream inputStream = item.openStream();
                    fileStreamWrapper.setInputStream(inputStream);
                    flow(inputStream, fileStreamWrapper.getOutputStream());
                    fileStreamWrapper.setStatus(1);
                }
            }
        } finally {
            Optional.ofNullable(fileStreamWrapper)
                    .map(FileStreamWrapper::getLatch)
                    .ifPresent(CountDownLatch::countDown);
        }
    }

    FileItemIterator getItemIterator(HttpServletRequest request) throws FileUploadException, IOException {
        return new ServletFileUpload().getItemIterator(request);
    }

    @RequestMapping(path = "/download/{shareHash}", method = RequestMethod.GET)
    public void download(@PathVariable String shareHash, HttpServletRequest request, HttpServletResponse response) throws IOException, InterruptedException {
        FileShareWrapper fileShareWrapper = shareMap.get(shareHash);

        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileShareWrapper.getFilename() + "\"");
        response.setHeader("Content-Length", fileShareWrapper.getSize().toString());

        String streamHash = UUID.randomUUID().toString();

        CountDownLatch latch = getLatch();
        FileStreamWrapper fileStreamWrapper = FileStreamWrapper.builder()
                .latch(latch)
                .outputStream(response.getOutputStream())
                .status(0)
                .build();
        fileShareWrapper.getStreamMap().put(streamHash, fileStreamWrapper);

        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .orElse(request.getRemoteHost());

        template.convertAndSend("/topic/" + shareHash, SocketShareDTO.builder()
                .shareHash(shareHash)
                .streamHash(streamHash)
                .ip(ip)
                .build());

        latch.await();

        fileShareWrapper.getStreamMap().remove(streamHash);
    }

    CountDownLatch getLatch() {
        return new CountDownLatch(1);
    }

    private void flow(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
    }
}
