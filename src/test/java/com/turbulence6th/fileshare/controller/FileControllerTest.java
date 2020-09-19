package com.turbulence6th.fileshare.controller;

import com.turbulence6th.fileshare.dto.*;
import com.turbulence6th.fileshare.service.HashService;
import org.apache.tomcat.util.http.fileupload.FileItemIterator;
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class FileControllerTest {

    private Map<String, FileShareWrapper> shareMap;
    private SimpMessagingTemplate template;
    private FileController fileController;
    private HashService hashService;

    @BeforeEach
    public void beforeEach() {
       shareMap = new HashMap<>();
       template = mock(SimpMessagingTemplate.class);
       hashService = new HashService();
       fileController = spy(new FileController(shareMap, template, hashService));
    }

    @Test
    public void shareTest() {
        ShareResponseDTO response = fileController.share(ShareRequestDTO.builder()
                .filename("Example.txt")
                .size(29L)
                .build());

        FileShareWrapper fileShareWrapper = shareMap.get(response.getShareHash());

        assertEquals(fileShareWrapper.getFilename(), "Example.txt");
        assertEquals(fileShareWrapper.getSize(), 29L);
    }

    @Test
    public void uploadTest() throws IOException, FileUploadException, BrokenBarrierException, InterruptedException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        FileItemIterator fileItemIterator = mock(FileItemIterator.class);
        FileItemStream fileItemStream = mock(FileItemStream.class);
        CountDownLatch countDownLatch = mock(CountDownLatch.class);

        when(fileItemIterator.hasNext()).thenReturn(true, new Boolean[0]);
        when(fileItemIterator.next()).thenReturn(fileItemStream, new FileItemStream[0]);
        when(fileItemStream.isFormField()).thenReturn(false);
        when(fileItemStream.openStream()).thenReturn(new ByteArrayInputStream("This is an test example file.".getBytes()));

        doReturn(fileItemIterator).when(fileController).getItemIterator(request);

        FileShareWrapper fileShareWrapper = FileShareWrapper.builder()
                .filename("Example.txt")
                .size(29L)
                .streamMap(new HashMap<>())
                .build();

        shareMap.put("exampleShare", fileShareWrapper);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        FileStreamWrapper fileStreamWrapper = FileStreamWrapper.builder()
                .latch(countDownLatch)
                .outputStream(outputStream)
                .status(0)
                .build();

        fileShareWrapper.getStreamMap().put("exampleStream", fileStreamWrapper);

        fileController.upload("exampleShare", "exampleStream", request);
        verify(countDownLatch, times(1)).countDown();
        assertEquals("This is an test example file.", outputStream.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void downloadTest() throws InterruptedException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        CountDownLatch countDownLatch = mock(CountDownLatch.class);

        ArgumentCaptor<SocketShareDTO> socketShareCaptor = ArgumentCaptor.forClass(SocketShareDTO.class);

        when(request.getRemoteHost()).thenReturn("10.10.10.10");
        doReturn(countDownLatch).when(fileController).getLatch();

        FileShareWrapper fileShareWrapper = FileShareWrapper.builder()
                .filename("Example.txt")
                .size(29L)
                .streamMap(new HashMap<>())
                .build();

        shareMap.put("exampleShare", fileShareWrapper);
        fileController.download("exampleShare", request, response);

        verify(response, times(1)).setHeader("Content-Disposition", "attachment; filename=\"Example.txt\"");
        verify(response, times(1)).setHeader("Content-Length", "29");
        verify(template, times(1)).convertAndSend(eq("/topic/exampleShare"), socketShareCaptor.capture());
        verify(countDownLatch, times(1)).await();

        assertEquals("exampleShare", socketShareCaptor.getValue().getShareHash());
        assertEquals("10.10.10.10", socketShareCaptor.getValue().getIp());
    }
}
