package com.turbulence6th.fileshare.controller;

import com.turbulence6th.fileshare.dto.*;
import org.apache.tomcat.util.http.fileupload.FileItemIterator;
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

@RestController
@RequestMapping("/file")
public class FileController {

    private final Map<String, FileShareWrapper> shareMap = new HashMap<>();

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    private SimpMessagingTemplate template;

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
        shareMap.remove(request.getShareHash());
    }

    @RequestMapping(path = "/upload/{shareHash}/{streamHash}", method = RequestMethod.POST)
    public void upload(@PathVariable String shareHash, @PathVariable String streamHash, HttpServletRequest request) throws IOException, FileUploadException, BrokenBarrierException, InterruptedException {
        FileStreamWrapper fileStreamWrapper = shareMap.get(shareHash).getStreamMap().get(streamHash);
        try {
            ServletFileUpload upload = new ServletFileUpload();
            FileItemIterator iterator = upload.getItemIterator(request);
            if (iterator.hasNext()) {
                FileItemStream item = iterator.next();

                if (!item.isFormField()) {
                    flow(item.openStream(), fileStreamWrapper.getOutputStream());
                }
            }
        } finally {
            Optional<CyclicBarrier> cyclicBarrier = Optional.ofNullable(fileStreamWrapper)
                    .map(FileStreamWrapper::getBarrier);
            if (cyclicBarrier.isPresent()) {
                cyclicBarrier.get().await();
            }
        }
    }

    @RequestMapping(path = "/download/{shareHash}", method = RequestMethod.GET)
    public void download(@PathVariable String shareHash, HttpServletRequest request, HttpServletResponse response) throws IOException, BrokenBarrierException, InterruptedException {
        FileShareWrapper fileShareWrapper = shareMap.get(shareHash);

        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileShareWrapper.getFilename() + "\"");
        response.setHeader("Content-Length", fileShareWrapper.getSize().toString());

        String streamHash = UUID.randomUUID().toString();

        CyclicBarrier barrier = new CyclicBarrier(2);
        FileStreamWrapper fileStreamWrapper = FileStreamWrapper.builder()
                .barrier(barrier)
                .outputStream(response.getOutputStream())
                .build();
        fileShareWrapper.getStreamMap().put(streamHash, fileStreamWrapper);

        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .orElse(request.getRemoteHost());

        template.convertAndSend("/topic/" + shareHash, SocketShareDTO.builder()
                .shareHash(shareHash)
                .streamHash(streamHash)
                .ip(ip)
                .build());

        barrier.await();

        fileShareWrapper.getStreamMap().remove(streamHash);
    }

    private void flow(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
    }
}
