package com.smartfarm.backend.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfarm.backend.common.BadRequestException;
import com.smartfarm.backend.common.ClockConfig;

/** API-004 수신. 사진을 디스크에 쓰고 crop_image에 메타데이터를 남긴다. 진단은 호출한 쪽이 커밋 뒤에 요청한다. */
@Service
public class ImageService {

	/** 저장한 사진의 메타데이터. 진단 대기열에는 이미지 바이트를 보유하지 않는다. */
	public record Received(CropImage image) {
	}

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmssSSS");

	private final CropImageRepository cropImageRepository;
	private final Path storageDir;
	private final int maxBytes;
	private final Clock clock;

	public ImageService(CropImageRepository cropImageRepository, ImageProperties properties, Clock clock) {
		this.cropImageRepository = cropImageRepository;
		this.storageDir = Path.of(properties.storageDir()).toAbsolutePath().normalize();
		this.maxBytes = properties.maxBytes();
		this.clock = clock;
	}

	@Transactional
	public Received receive(String pathFarmId, ImageRequest request) {
		if (!pathFarmId.equals(request.farmId())) {
			throw new BadRequestException("farmId mismatch");
		}
		LocalDateTime capturedAt = toKst(request.timestampUtc());
		byte[] bytes = decode(request.imageBase64());
		String format = detectFormat(bytes);

		String relativePath = pathFarmId + "/" + capturedAt.format(DAY) + "/img_" + capturedAt.format(TIME) + "_"
				+ HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt()) + "." + format;
		Path file = resolve(relativePath);
		write(file, bytes);

		try {
			CropImage image = cropImageRepository.save(new CropImage(pathFarmId, capturedAt, request,
					speciesOrNull(request.species()), relativePath, format, bytes.length, LocalDateTime.now(clock)));
			return new Received(image);
		}
		catch (RuntimeException e) {
			// DB에 남지 않은 파일은 아무도 찾을 수 없으니 지운다.
			deleteQuietly(file);
			throw e;
		}
	}

	public Optional<CropImage> find(long imageId) {
		return cropImageRepository.findById(imageId);
	}

	/** 저장된 경로가 저장 디렉터리 밖을 가리키면(경로 조작) 파일을 내주지 않는다. */
	public Optional<Path> file(CropImage image) {
		Path file = resolve(image.getFilePath());
		return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
	}

	public byte[] read(CropImage image) throws IOException {
		return Files.readAllBytes(resolve(image.getFilePath()));
	}

	private Path resolve(String relativePath) {
		Path file = storageDir.resolve(relativePath).normalize();
		if (!file.startsWith(storageDir)) {
			throw new IllegalStateException("image path escapes storage dir: " + relativePath);
		}
		return file;
	}

	private byte[] decode(String base64) {
		// base64는 원본보다 4/3배 크므로 디코딩 전에 한도를 넘는지 먼저 본다.
		if ((long) base64.length() * 3 / 4 > maxBytes + 2L) {
			throw new BadRequestException("image too large");
		}
		try {
			byte[] bytes = Base64.getMimeDecoder().decode(base64);
			if (bytes.length == 0) {
				throw new BadRequestException("empty image");
			}
			if (bytes.length > maxBytes) {
				throw new BadRequestException("image too large");
			}
			return bytes;
		}
		catch (IllegalArgumentException e) {
			throw new BadRequestException("invalid imageBase64");
		}
	}

	/** API-004 명세처럼 format 필드 대신 파일 앞 바이트로 판별한다. */
	static String detectFormat(byte[] b) {
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "jpg";
		}
		if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
			return "png";
		}
		throw new BadRequestException("unsupported image format");
	}

	private static String speciesOrNull(String species) {
		return species == null || species.isBlank() || "none".equalsIgnoreCase(species) ? null : species;
	}

	private static LocalDateTime toKst(String utc) {
		try {
			return LocalDateTime.ofInstant(Instant.parse(utc), ClockConfig.ZONE);
		}
		catch (DateTimeParseException e) {
			throw new BadRequestException("invalid timestampUtc");
		}
	}

	private static void write(Path file, byte[] bytes) {
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, bytes);
		}
		catch (IOException e) {
			throw new UncheckedIOException("사진 저장 실패: " + file, e);
		}
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ignored) {
			// 지우지 못해도 원래 예외를 덮지 않는다.
		}
	}
}
