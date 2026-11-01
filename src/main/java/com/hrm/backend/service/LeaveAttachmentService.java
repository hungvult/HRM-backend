package com.hrm.backend.service;

import com.hrm.backend.dto.response.LeaveAttachmentResponse;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.LeaveRequestAttachment;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.LeaveRequestAttachmentRepository;
import com.hrm.backend.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
public class LeaveAttachmentService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private final LeaveRequestAttachmentRepository attachments;
    private final LeaveRequestRepository leaveRequests;
    private final AccessScopeService accessScope;

    // Dùng trước khi tạo đơn: file là part attachment tùy chọn, không phải đường dẫn trên server.
    // Đọc tối đa 5 MB + 1 byte, chuẩn hóa MIME rồi đối chiếu chữ ký và giải mã ảnh thật; lỗi trả 400/413, chưa ghi DB.
    // Cho phép tham số MIME và dữ liệu phụ sau EOI của JPEG đọc được; không đổi byte ảnh hoặc đoán loại từ tên file.
    // Trả ảnh đã kiểm tra để submission gắn FK và lưu cùng transaction với đơn/lịch sử/thông báo.
    public LeaveRequestAttachment validate(MultipartFile file) {
        if (file == null) return null;
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        byte[] data;
        try (var input = file.getInputStream()) {
            data = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException ex) {
            throw new AuthException("LEAVE_ATTACHMENT_INVALID", "Không đọc được ảnh minh chứng.", 400);
        }
        if (data.length > MAX_BYTES) throw tooLarge();
        // Multipart có thể gửi image/jpeg; charset=utf-8. Chỉ lưu MIME gốc chuẩn để khớp constraint của bảng ảnh.
        // Không suy đoán từ đuôi file; bên dưới vẫn đối chiếu chữ ký và decoder với chính MIME đã khai.
        String type = file.getContentType() == null ? ""
                : file.getContentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!hasImageSignature(data, type)) {
            throw new AuthException("LEAVE_ATTACHMENT_INVALID", "Ảnh phải là JPG, PNG hoặc WebP hợp lệ.", 400);
        }
        // Magic bytes không đủ chứng minh ảnh thật: reader phải đọc được nội dung, không chỉ header giả.
        requireDecodableImage(data, type);
        return LeaveRequestAttachment.builder().fileName(safeFileName(file.getOriginalFilename(), type))
                .contentType(type).sizeBytes((long) data.length).data(data).build();
    }

    // Caller là submission đã có transaction; ảnh và đơn rollback cùng nhau nếu lưu/notification thất bại.
    // PK leave_request_id chặn ảnh thứ hai cho cùng đơn kể cả có hai lệnh ghi đồng thời.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void save(LeaveRequest request, LeaveRequestAttachment attachment) {
        if (attachment == null) return;
        attachment.setLeaveRequest(request);
        attachments.save(attachment);
    }

    // Chỉ đọc tên/MIME/kích thước sau khi caller đã kiểm tra quyền xem đơn; URL nội bộ vẫn cần bearer.
    @Transactional(readOnly = true)
    public LeaveAttachmentResponse metadata(Long leaveRequestId) {
        return attachments.findMetadataByLeaveRequestId(leaveRequestId)
                .map(a -> new LeaveAttachmentResponse(a.getFileName(), a.getContentType(), a.getSizeBytes(),
                        "/v1/leave-requests/" + leaveRequestId + "/attachment"))
                .orElse(null);
    }

    // accountId lấy từ principal, id là leave_requests.id. Áp dụng đúng phạm vi xem đơn cũ trước khi tải bytea.
    // Khác chủ/phạm vi trả lỗi của AccessScopeService; thiếu đơn/ảnh trả 404, không mở ảnh qua URL công khai.
    @Transactional(readOnly = true)
    public LeaveRequestAttachment read(Long accountId, Long id) {
        LeaveRequest request = leaveRequests.findWithEmployeeAndLeaveTypeById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LEAVE_REQUEST_NOT_FOUND", "Không tìm thấy đơn nghỉ phép."));
        accessScope.requireCanReadLeaveRequest(accountId, request.getEmployee().getId());
        return attachments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LEAVE_ATTACHMENT_NOT_FOUND", "Đơn không có ảnh minh chứng."));
    }

    // Kiểm tra nhanh magic bytes/header trước decoder; bước này chưa đủ kết luận ảnh hợp lệ.
    private boolean hasImageSignature(byte[] data, String type) {
        if (data.length < 12) return false;
        return switch (type) {
            case "image/jpeg" -> (data[0] & 255) == 255 && (data[1] & 255) == 216 && (data[2] & 255) == 255
                    // Một số JPEG có dữ liệu phụ sau EOI; không bắt EOI phải ở hai byte cuối toàn bộ file.
                    && hasJpegEndMarker(data);
            case "image/png" -> data.length >= 45 && java.util.Arrays.equals(java.util.Arrays.copyOf(data, 8),
                    new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})
                    && ascii(data, 12, 4).equals("IHDR") && ascii(data, data.length - 8, 4).equals("IEND");
            case "image/webp" -> data.length >= 20 && ascii(data, 0, 4).equals("RIFF")
                    && ascii(data, 8, 4).equals("WEBP")
                    && java.util.Set.of("VP8 ", "VP8L", "VP8X").contains(ascii(data, 12, 4))
                    && Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(data, 4, 4)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()) == data.length - 8L;
            default -> false;
        };
    }

    // Dùng sau khi đã nhận ra SOI của JPEG. Đi theo từng segment và vùng dữ liệu nén để tìm EOI thật trước phần phụ.
    // Bỏ qua payload APP/COM theo độ dài: FF D9 trong metadata hoặc ảnh thumbnail không phải EOI của ảnh chính.
    // Đây chỉ là kiểm tra cấu trúc sơ bộ; requireDecodableImage tiếp tục giải mã để chặn ảnh giả/hỏng hoặc sai MIME.
    // Không cắt phần phụ, không sửa byte hoặc ghi DB; caller giữ nguyên dữ liệu gốc và size_bytes.
    private boolean hasJpegEndMarker(byte[] data) {
        int index = 2;
        boolean inScan = false;
        while (index < data.length) {
            int marker;
            if (inScan) {
                // Trong dữ liệu nén, FF 00 là byte được escape, còn restart marker không kết thúc scan.
                // Chỉ chuyển sang đọc segment khi gặp marker thật, tránh nhầm dữ liệu pixel thành EOI.
                do {
                    while (index < data.length && (data[index] & 255) != 255) index++;
                    while (index < data.length && (data[index] & 255) == 255) index++;
                    if (index >= data.length) return false;
                    marker = data[index++] & 255;
                } while (marker == 0 || (marker >= 0xD0 && marker <= 0xD7));
            } else {
                // Ngoài scan, segment phải bắt đầu bằng FF; file bị cắt hoặc sai ranh giới thì dừng.
                if ((data[index++] & 255) != 255) return false;
                while (index < data.length && (data[index] & 255) == 255) index++;
                if (index >= data.length) return false;
                marker = data[index++] & 255;
            }
            if (marker == 0xD9) return true;
            if (marker == 0 || marker == 0xD8) return false;
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) continue;

            // Độ dài gồm hai byte length. Bỏ qua toàn bộ segment để EOI giả trong metadata không được tính.
            if (index + 2 > data.length) return false;
            int length = ((data[index] & 255) << 8) | (data[index + 1] & 255);
            if (length < 2 || length > data.length - index) return false;
            index += length;
            // SOS mở scan; DNL chỉ bổ sung số dòng ngay trong scan đang chạy, không đóng dữ liệu nén.
            inScan = marker == 0xDA || (inScan && marker == 0xDC);
        }
        return false;
    }

    // Đọc kích thước trước khi cấp phát; giải mã preview tối đa khoảng 1 MP bằng subsampling để nhận ảnh điện thoại độ phân giải cao.
    // PNG/JPG giới hạn nguồn 200 MP và cạnh 65.535; WebP giới hạn 25 MP vì decoder vẫn có bộ đệm nguồn dù subsampling.
    // Vượt giới hạn trả mã riêng kèm giới hạn; lỗi giải mã trả 400. Không thay ảnh gốc hoặc ghi file tạm/DB.
    private void requireDecodableImage(byte[] data, String type) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Không có decoder cho ảnh.");
            var reader = readers.next();
            try {
                reader.setInput(input, false, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(type.equals("image/jpeg") && (format.equals("jpeg") || format.equals("jpg")))
                        && !(type.equals("image/png") && format.equals("png"))
                        && !(type.equals("image/webp") && format.equals("webp"))) {
                    throw new IOException("Định dạng giải mã không khớp MIME.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long maxPixels = type.equals("image/webp") ? 25_000_000L : 200_000_000L;
                if (width <= 0 || height <= 0) throw new IOException("Kích thước ảnh không hợp lệ.");
                // Nhân bằng long để header kích thước lớn không tràn số rồi vượt qua guard.
                if (width > 65535 || height > 65535 || (long) width * height > maxPixels) {
                    throw new AuthException("LEAVE_ATTACHMENT_DIMENSIONS_TOO_LARGE",
                            "Ảnh " + (type.equals("image/webp") ? "WebP tối đa 25" : "JPG/PNG tối đa 200")
                                    + " megapixel, mỗi cạnh tối đa 65.535 pixel.", 400);
                }
                var param = reader.getDefaultReadParam();
                int sampling = Math.max(1, (Math.max(width, height) + 1023) / 1024);
                param.setSourceSubsampling(sampling, sampling, 0, 0);
                var image = reader.read(0, param);
                if (image == null) throw new IOException("Không giải mã được ảnh.");
                image.flush();
            } finally {
                reader.dispose();
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new AuthException("LEAVE_ATTACHMENT_INVALID", "Nội dung ảnh bị hỏng hoặc không giải mã được.", 400);
        }
    }

    // Đọc đúng đoạn ASCII trong header đã kiểm tra chiều dài, không diễn giải toàn bộ ảnh thành chuỗi.
    private String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    // Chỉ giữ tên hiển thị: bỏ đường dẫn, control và ký tự nguy hiểm trong header; phần mở rộng theo MIME đã xác minh.
    private String safeFileName(String original, String type) {
        String name = original == null ? "attachment" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        if (name.isBlank() || name.equals(".") || name.equals("..")) name = "attachment";
        if (name.length() > 200) name = name.substring(0, 200);
        return name + switch (type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; default -> ".webp"; };
    }

    // Giữ cùng mã lỗi cho giới hạn dung lượng ở service và bộ phân tích multipart của Spring.
    private AuthException tooLarge() {
        return new AuthException("LEAVE_ATTACHMENT_TOO_LARGE", "Ảnh minh chứng không được vượt quá 5 MB.", 413);
    }
}
