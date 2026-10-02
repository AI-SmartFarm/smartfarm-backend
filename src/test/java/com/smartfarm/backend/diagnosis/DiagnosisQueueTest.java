package com.smartfarm.backend.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClientException;

import com.smartfarm.backend.ai.AiDiagnosisClient;
import com.smartfarm.backend.ai.DiagnosisResponse;
import com.smartfarm.backend.image.CropImage;
import com.smartfarm.backend.image.ImageService;

import tools.jackson.databind.json.JsonMapper;

class DiagnosisQueueTest {
    @TempDir Path dir;

    private CropImage image(long id) {
        CropImage image = mock(CropImage.class);
        when(image.getImageId()).thenReturn(id);
        when(image.getFarmId()).thenReturn("farm");
        when(image.getSpecies()).thenReturn("Tomato");
        when(image.getImageFormat()).thenReturn("jpg");
        return image;
    }

    @Test
    void queuedImagesAreReadOnlyWhenRunningAndOverflowKeepsFiles() throws Exception {
        AiDiagnosisClient ai = mock(AiDiagnosisClient.class);
        ImageService images = mock(ImageService.class);
        DiagnosisRepository repository = mock(DiagnosisRepository.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> calls = new CopyOnWriteArrayList<>();
        Path first = Files.write(dir.resolve("1.jpg"), new byte[] {1});
        Path second = Files.write(dir.resolve("2.jpg"), new byte[] {2});
        Path rejected = Files.write(dir.resolve("3.jpg"), new byte[] {3});
        CropImage one = image(1), two = image(2), three = image(3);
        when(images.read(one)).thenAnswer(i -> Files.readAllBytes(first));
        when(images.read(two)).thenAnswer(i -> Files.readAllBytes(second));
        when(ai.diagnose(any(byte[].class), anyString(), eq("tomato"))).thenAnswer(i -> {
            calls.add(i.getArgument(1));
            if (calls.size() == 1) {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return new DiagnosisResponse("no_detection", "tomato", null, null, List.of(), "test");
        });
        DiagnosisService service = new DiagnosisService(ai, repository, JsonMapper.builder().build(),
                Clock.systemUTC(), new DiagnosisProperties(true, 1), images);
        try {
            service.request(new ImageService.Received(one));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            service.request(new ImageService.Received(two));
            service.request(new ImageService.Received(three));
            verify(images, never()).read(two);
            verify(images, never()).read(three);
            assertThat(calls).containsExactly("image-1.jpg");
            // Change disk content while queued: execution must use the file, not an upload array.
            Files.write(second, new byte[] {9});
        } finally {
            release.countDown();
            service.destroy();
        }
        assertThat(calls).containsExactly("image-1.jpg", "image-2.jpg");
        verify(ai).diagnose(new byte[] {9}, "image-2.jpg", "tomato");
        verify(repository, times(2)).save(any(Diagnosis.class));
        assertThat(Files.exists(first) && Files.exists(second) && Files.exists(rejected)).isTrue();
    }

    @Test
    void fileAndAiFailuresAreRecordedAndWorkerContinues() throws Exception {
        AiDiagnosisClient ai = mock(AiDiagnosisClient.class);
        ImageService images = mock(ImageService.class);
        DiagnosisRepository repository = mock(DiagnosisRepository.class);
        List<Diagnosis> saved = new CopyOnWriteArrayList<>();
        when(repository.save(any(Diagnosis.class))).thenAnswer(i -> {
            Diagnosis d = i.getArgument(0);
            saved.add(d);
            return d;
        });
        CropImage missing = image(1), failedAi = image(2), good = image(3);
        when(images.read(missing)).thenThrow(new IOException("missing file"));
        when(images.read(failedAi)).thenReturn(new byte[] {2});
        when(images.read(good)).thenReturn(new byte[] {3});
        when(ai.diagnose(any(byte[].class), eq("image-2.jpg"), eq("tomato")))
                .thenThrow(new RestClientException("AI unavailable"));
        when(ai.diagnose(any(byte[].class), eq("image-3.jpg"), eq("tomato")))
                .thenReturn(new DiagnosisResponse("no_detection", "tomato", null, null, List.of(), "test"));
        DiagnosisService service = new DiagnosisService(ai, repository, JsonMapper.builder().build(),
                Clock.systemUTC(), new DiagnosisProperties(true, 3), images);
        try {
            service.request(new ImageService.Received(missing));
            service.request(new ImageService.Received(failedAi));
            service.request(new ImageService.Received(good));
        } finally {
            service.destroy();
        }
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getDiagnosedAt()).isNull();
        assertThat(saved.get(1).getDiagnosedAt()).isNull();
        assertThat(saved.get(2).getDiagnosedAt()).isNotNull();
        verify(ai, never()).diagnose(any(byte[].class), eq("image-1.jpg"), anyString());
    }
}