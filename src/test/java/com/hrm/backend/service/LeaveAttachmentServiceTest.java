package com.hrm.backend.service;

import com.hrm.backend.entity.*;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Base64;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveAttachmentServiceTest {
    private final LeaveRequestAttachmentRepository attachments = mock(LeaveRequestAttachmentRepository.class);
    private final LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
    private final AccessScopeService scope = mock(AccessScopeService.class);
    private final LeaveAttachmentService service = new LeaveAttachmentService(attachments, requests, scope);
    public static byte[] png() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    }

    @Test void validatesRealPngAndSanitizesNameWithoutWritingAnything() {
        var image = service.validate(new MockMultipartFile("attachment", "../../photo\r\n\".exe", "image/png", png()));
        assertEquals("image/png", image.getContentType());
        assertFalse(image.getFileName().contains("/"));
        assertFalse(image.getFileName().contains("\r"));
        assertTrue(image.getFileName().endsWith(".png"));
        assertEquals(png().length, image.getSizeBytes());
        assertArrayEquals(png(), image.getData());
        verifyNoInteractions(attachments, requests, scope);
    }

    @Test void validatesJpegAndWebpSignatures() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", output);
        assertEquals("image/jpeg", service.validate(new MockMultipartFile("attachment", "a.jpg", "image/jpeg", output.toByteArray())).getContentType());
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        assertEquals("image/webp", service.validate(new MockMultipartFile("attachment", "a.webp", "image/webp", webp)).getContentType());
    }

    @Test void acceptsReadableJpegWithTrailingDataAndKeepsOriginalBytes() throws Exception {
        byte[] jpeg = realJpeg();
        byte[] trailer = "android-photo-metadata".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] data = java.util.Arrays.copyOf(jpeg, jpeg.length + trailer.length);
        System.arraycopy(trailer, 0, data, jpeg.length, trailer.length);
        assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data)));

        var image = service.validate(new MockMultipartFile("attachment", "phone.jpg", "image/jpeg", data));

        assertEquals("image/jpeg", image.getContentType());
        assertArrayEquals(data, image.getData());
        assertEquals(data.length, image.getSizeBytes());
        verifyNoInteractions(attachments, requests, scope);
    }

    @Test void normalizesMimeParametersAndCaseForEachSupportedType() throws Exception {
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        byte[][] data = {realJpeg(), png(), webp};
        String[] declared = {"IMAGE/JPEG; charset=UTF-8", "image/png; name=photo.png", "image/webp; q=1"};
        String[] expected = {"image/jpeg", "image/png", "image/webp"};
        for (int index = 0; index < data.length; index++) {
            var image = service.validate(new MockMultipartFile("attachment", "phone", declared[index], data[index]));
            assertEquals(expected[index], image.getContentType());
            assertArrayEquals(data[index], image.getData());
            assertTrue(image.getContentType().length() <= 30);
        }
        verifyNoInteractions(attachments, requests, scope);
    }

    @Test void normalizationDoesNotBypassMimeSignatureAndDecoderChecks() throws Exception {
        for (var file : java.util.List.of(
                new MockMultipartFile("attachment", "fake.jpg", "image/jpeg; charset=UTF-8", png()),
                new MockMultipartFile("attachment", "photo.jpg", "application/octet-stream", realJpeg()),
                new MockMultipartFile("attachment", "photo.jpg", null, realJpeg()),
                new MockMultipartFile("attachment", "fake.jpg", "image/jpeg; charset=UTF-8",
                        new byte[]{(byte)255, (byte)216, (byte)255, 0, 0, 0, (byte)255, (byte)217, 0, 0, 0, 0}))) {
            assertEquals("LEAVE_ATTACHMENT_INVALID", assertThrows(AuthException.class, () -> service.validate(file)).getCode());
        }
        verifyNoInteractions(attachments, requests, scope);
    }

    @Test void rejectsJpegMissingEndMarkerEvenIfDecoderCanRecover() throws Exception {
        byte[] jpeg = realJpeg();
        byte[] incomplete = java.util.Arrays.copyOf(jpeg, jpeg.length - 2);
        assertEquals("LEAVE_ATTACHMENT_INVALID", assertThrows(AuthException.class,
                () -> service.validate(new MockMultipartFile("attachment", "truncated.jpg", "image/jpeg", incomplete))).getCode());
        verifyNoInteractions(attachments, requests, scope);
    }

    @Test void metadataEndMarkerCannotReplaceMissingMainImageEndMarker() throws Exception {
        byte[] jpeg = realJpeg();
        for (int marker : new int[]{0xFE, 0xE1}) {
            var output = new java.io.ByteArrayOutputStream();
            output.write(jpeg, 0, 2);
            output.write(new byte[]{(byte)255, (byte)marker, 0, 4, (byte)255, (byte)217});
            output.write(jpeg, 2, jpeg.length - 4);
            assertEquals("LEAVE_ATTACHMENT_INVALID", assertThrows(AuthException.class,
                    () -> service.validate(new MockMultipartFile("attachment", "truncated.jpg", "image/jpeg", output.toByteArray()))).getCode());
        }
        verifyNoInteractions(attachments, requests, scope);
    }

    @Test void validMetadataAndTrailerStillAllowRealImageEndMarker() throws Exception {
        byte[] jpeg = realJpeg();
        var output = new java.io.ByteArrayOutputStream();
        output.write(jpeg, 0, 2);
        output.write(new byte[]{(byte)255, (byte)254, 0, 4, (byte)255, (byte)217});
        output.write(jpeg, 2, jpeg.length - 2);
        output.write("android-trailer".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] data = output.toByteArray();
        assertArrayEquals(data, service.validate(new MockMultipartFile("attachment", "phone.jpg", "image/jpeg; charset=utf-8", data)).getData());
    }

    @Test void progressiveJpegWithSeveralScansAndTrailerIsAccepted() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        var writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var stream = javax.imageio.ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            var options = writer.getDefaultWriteParam();
            options.setProgressiveMode(javax.imageio.ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new javax.imageio.IIOImage(new java.awt.image.BufferedImage(16, 16,
                    java.awt.image.BufferedImage.TYPE_INT_RGB), null, null), options);
        } finally { writer.dispose(); }
        byte[] jpeg = output.toByteArray();
        byte[] data = java.util.Arrays.copyOf(jpeg, jpeg.length + 4);
        assertArrayEquals(data, service.validate(new MockMultipartFile("attachment", "progressive.jpg", "image/jpeg", data)).getData());
    }

    private byte[] realJpeg() throws java.io.IOException {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", output);
        return output.toByteArray();
    }

    @Test void exactFiveMiBIsAllowedButOneExtraByteIsRejected() {
        byte[] jpg = paddedRealJpeg(LeaveAttachmentService.MAX_BYTES);
        assertEquals(5242880L, service.validate(new MockMultipartFile("attachment", "a.jpg", "image/jpeg", jpg)).getSizeBytes());
        AuthException ex = assertThrows(AuthException.class, () -> service.validate(new MockMultipartFile("attachment", "a.jpg", "image/jpeg", new byte[5242881])));
        assertEquals(413, ex.getStatus());
    }

    // Tạo JPEG đọc được rồi thêm các COM segment hợp lệ trước dữ liệu gốc, thay vì giả magic bytes trên mảng zero.
    private byte[] paddedRealJpeg(int targetSize) {
        try {
            var original = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", original);
            byte[] jpeg = original.toByteArray();
            var padded = new java.io.ByteArrayOutputStream(targetSize);
            padded.write(jpeg, 0, 2);
            int remaining = targetSize - jpeg.length;
            while (remaining > 0) {
                int segment = Math.min(remaining, 65537);
                if (remaining - segment > 0 && remaining - segment < 4) segment -= 4;
                int length = segment - 2;
                padded.write(255); padded.write(254); padded.write(length >> 8); padded.write(length & 255);
                padded.write(new byte[segment - 4]);
                remaining -= segment;
            }
            padded.write(jpeg, 2, jpeg.length - 2);
            byte[] result = padded.toByteArray();
            assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(result)));
            return result;
        } catch (java.io.IOException ex) { throw new AssertionError(ex); }
    }

    @Test void magicBytesOnlyFakeJpegAndCorruptedPixelsAreRejected() {
        byte[] fake = new byte[]{(byte) 255, (byte) 216, (byte) 255, 0, 0, 0, 0, 0, 0, 0, (byte) 255, (byte) 217};
        assertEquals("LEAVE_ATTACHMENT_INVALID", assertThrows(AuthException.class,
                () -> service.validate(new MockMultipartFile("attachment", "fake.jpg", "image/jpeg", fake))).getCode());
        byte[] fakeWebp = new byte[40];
        System.arraycopy("RIFF".getBytes(), 0, fakeWebp, 0, 4);
        fakeWebp[4] = 32;
        System.arraycopy("WEBPVP8 ".getBytes(), 0, fakeWebp, 8, 8);
        assertEquals("LEAVE_ATTACHMENT_INVALID", assertThrows(AuthException.class,
                () -> service.validate(new MockMultipartFile("attachment", "fake.webp", "image/webp", fakeWebp))).getCode());
        verifyNoInteractions(attachments);
    }

    @Test void highResolutionJpegDecodesAsBoundedPreview() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(6000, 4500, java.awt.image.BufferedImage.TYPE_BYTE_GRAY), "jpg", output);
        assertEquals("image/jpeg", service.validate(new MockMultipartFile("attachment", "phone.jpg", "image/jpeg", output.toByteArray())).getContentType());
    }

    @Test void oversizedDimensionsGetSpecificErrorBeforePixelDecode() {
        byte[] png = png();
        java.nio.ByteBuffer.wrap(png, 16, 4).putInt(65536);
        assertEquals("LEAVE_ATTACHMENT_DIMENSIONS_TOO_LARGE", assertThrows(AuthException.class,
                () -> service.validate(new MockMultipartFile("attachment", "large.png", "image/png", png))).getCode());
    }

    @Test void rejectsSpoofedTypeEmptyAndTruncatedImages() {
        assertNull(service.validate(null));
        for (var file : java.util.List.of(
                new MockMultipartFile("attachment", "a.jpg", "image/jpeg", png()),
                new MockMultipartFile("attachment", "a.svg", "image/svg+xml", png()),
                new MockMultipartFile("attachment", "a.png", "image/png", new byte[0]),
                new MockMultipartFile("attachment", "a.png", "image/png", java.util.Arrays.copyOf(png(), 20)))) {
            assertEquals("LEAVE_ATTACHMENT_INVALID", assertThrows(AuthException.class, () -> service.validate(file)).getCode());
        }
        verifyNoInteractions(attachments);
    }

    @Test void enforcesReadScopeBeforeLoadingBytesAndHandlesMissingAttachment() {
        when(requests.findWithEmployeeAndLeaveTypeById(3L)).thenReturn(Optional.of(LeaveRequest.builder()
                .id(3L).employee(Employee.builder().id(10L).build()).build()));
        doThrow(new AuthException("AUTH_FORBIDDEN_SCOPE", "Không có quyền", 403)).when(scope).requireCanReadLeaveRequest(2L, 10L);
        assertThrows(AuthException.class, () -> service.read(2L, 3L));
        verifyNoInteractions(attachments);
        assertEquals("LEAVE_ATTACHMENT_NOT_FOUND", assertThrows(ResourceNotFoundException.class, () -> service.read(1L, 3L)).getCode());
        var image = LeaveRequestAttachment.builder().data(png()).build();
        when(attachments.findById(3L)).thenReturn(Optional.of(image));
        assertSame(image, service.read(1L, 3L));
        verify(scope, times(2)).requireCanReadLeaveRequest(1L, 10L);
    }
}
